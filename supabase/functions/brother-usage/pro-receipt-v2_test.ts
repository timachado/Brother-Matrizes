import {
  eligibleProPlan, finalizeProReceiptV2, reserveProReceiptV2,
  type ProReceiptRequest, type TrustedEntitlement, type ProReceiptRpc,
  type ProFinalizeRpc,
} from "./pro-receipt-v2.ts";

const person = {
  authenticatedUserId: "11111111-2222-4333-8444-555555555555",
  authenticatedEmail: "verified@example.test",
};
const requestId = "12345678-1234-4234-8234-123456789abc";
const key = "pro_" + requestId;
const now = Date.parse("2026-11-07T18:00:00Z");
const initial: ProReceiptRequest = {
  ...person, operation: "import_matrix", clientRequestId: requestId,
};
const recurring: TrustedEntitlement = {
  authenticated: true,
  user: { id: person.authenticatedUserId, email: "VERIFIED@example.test" },
  pro: true, status: "active", planCode: "pro_monthly",
  currentPeriodEnd: "2026-11-30T23:59:59Z",
};

function eq<T>(actual: T, expected: T, context: string) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw Error(context + ": expected " + JSON.stringify(expected) +
      "; actual " + JSON.stringify(actual));
  }
}
const approved = (replayed: boolean) => ({
  allowed: true, operation: initial.operation, request_key: key,
  state: "reserved", replayed, pro: true, remaining: null,
});

Deno.test("trusted active Pro is required before first database write", async () => {
  let fetches = 0;
  let writes = 0;
  const result = await reserveProReceiptV2(initial, async id => {
    eq(id, person, "trusted entitlement owner and email");
    fetches++;
    return recurring;
  }, async args => {
    writes++;
    eq(args, {
      p_user_id: person.authenticatedUserId,
      p_operation: "import_matrix",
      p_client_request_id: requestId,
      p_plan_code: "pro_monthly",
    }, "server-only SQL arguments");
    return { data: approved(false), error: null };
  }, now);
  eq(result, { status: 200, body: approved(false) }, "first Pro receipt");
  eq(fetches, 1, "one trusted authority call");
  eq(writes, 1, "one DB operation only after authority");
});

Deno.test("one row for lost Pro response and repeated same UUID", async () => {
  const records = new Map<string, Record<string, unknown>>();
  let calls = 0;
  let lostFirst = true;
  const rpc: ProReceiptRpc = async args => {
    calls++;
    const k = args.p_user_id + "/" + args.p_operation + "/" + args.p_client_request_id;
    const prior = records.get(k);
    if (prior) return { data: { ...prior, replayed: true }, error: null };
    records.set(k, approved(false));
    if (lostFirst) {
      lostFirst = false;
      throw Error("Server committed receipt, response lost");
    }
    return { data: approved(false), error: null };
  };
  const first = await reserveProReceiptV2(initial, async () => recurring, rpc, now);
  const again = await reserveProReceiptV2(initial, async () => recurring, rpc, now);
  eq(first.status, 503, "first response unavailable");
  eq(again, { status: 200, body: approved(true) }, "replayed same UUID");
  eq(records.size, 1, "same server receipt only once");
  eq(calls, 2, "two HTTP requests, one DB insert");
});

Deno.test("no Pro for inactive, impersonated, revoked or expired authority", async () => {
  const denied: TrustedEntitlement[] = [
    { ...recurring, authenticated: false },
    { ...recurring, user: { id: "99999999-2222-4333-8444-555555555555", email: person.authenticatedEmail } },
    { ...recurring, user: { id: person.authenticatedUserId, email: "other@example.test" } },
    { ...recurring, pro: false },
    { ...recurring, status: "cancelled" },
    { ...recurring, status: "expired" },
    { ...recurring, planCode: "free" },
    { ...recurring, planCode: "owner_grant_unverified" },
    { ...recurring, currentPeriodEnd: "2026-11-06T18:00:00Z" },
    { ...recurring, currentPeriodEnd: "not-a-date" },
    { ...recurring, planCode: "trial", trialRemainingSeconds: 0 },
    { ...recurring, planCode: "trial", trialRemainingSeconds: 30,
      currentPeriodEnd: "2026-11-06T18:00:00Z" },
  ];
  let writes = 0;
  for (const bad of denied) {
    eq(eligibleProPlan(person, bad, now), null, "reject unsafe plan");
    const response = await reserveProReceiptV2(initial, async () => bad,
      async () => { writes++; throw Error("NO SQL expected"); }, now);
    eq(response.status, 403, "denied entitlement");
  }
  eq(writes, 0, "no unauthorized Pro receipt inserted");
});

Deno.test("trial and lifetime require server authority, never client assertions", async () => {
  const trial: TrustedEntitlement = {
    ...recurring, planCode: "trial", trialRemainingSeconds: 3600,
    currentPeriodEnd: "2026-11-07T19:00:00Z",
  };
  eq(eligibleProPlan(person, trial, now), "trial", "valid trial");
  for (const plan of ["pro_lifetime", "pro_lifetime_launch"]) {
    eq(eligibleProPlan(person, {
      ...recurring, planCode: plan, currentPeriodEnd: null,
    }, now), plan, "server-issued lifetime entitlement");
  }
});

Deno.test("failed entitlement service or DB stays closed", async () => {
  let writes = 0;
  const offline = await reserveProReceiptV2(initial, async () => {
    throw Error("Entitlement unavailable");
  }, async () => { writes++; throw Error("must not write"); }, now);
  eq(offline.status, 503, "authority timeout");
  eq(writes, 0, "zero writes after authority failure");
  for (const sql of [
    async () => ({ data: null, error: Error("DB unavailable") }),
    async () => { throw Error("commit reply lost"); },
    async () => ({ data: [], error: null }),
  ]) {
    const result = await reserveProReceiptV2(initial, async () => recurring, sql, now);
    eq(result.status, 503, "unknown SQL must not grant permission");
  }
});

Deno.test("invalid client UUID, owner and operation never fetch authority", async () => {
  let calls = 0;
  const invalids: ProReceiptRequest[] = [
    { ...initial, clientRequestId: "" },
    { ...initial, clientRequestId: "12345678-1234-1234-8234-123456789abc" },
    { ...initial, clientRequestId: requestId + "\nX" },
    { ...initial, clientRequestId: requestId.toUpperCase() },
    { ...initial, authenticatedUserId: "not-verified" },
    { ...initial, authenticatedEmail: "" },
    { ...initial, operation: "billing_upgrade" as "import_matrix" },
  ];
  for (const invalid of invalids) {
    const result = await reserveProReceiptV2(invalid, async () => {
      calls++; throw Error("No entitlement lookup");
    }, async () => { throw Error("No SQL"); }, now);
    eq(result.status, 400, "input validation");
  }
  eq(calls, 0, "no calls on invalid inputs");
});

Deno.test("fake, foreign, expired, and changed receipts cannot grant Pro import", async () => {
  const corrupt: Record<string, unknown>[] = [
    { ...approved(false), request_key: "pro_00000000-0000-4000-8000-000000000000" },
    { ...approved(false), operation: "import_font" },
    { ...approved(false), state: "completed" },
    { ...approved(false), replayed: "false" },
    { ...approved(false), pro: "true" },
    { ...approved(false), remaining: 3 },
    { ...approved(false), allowed: "true" },
    { allowed: true, operation: "import_matrix", request_key: key },
    { allowed: false, operation: "import_matrix", request_key: "pro_different" },
    { allowed: false, operation: "import_matrix", state: "unknown" },
  ];
  for (const data of corrupt) {
    const result = await reserveProReceiptV2(
      initial, async () => recurring,
      async () => ({ data, error: null }), now,
    );
    eq(result.status, 503, "reject unverified receipt");
  }
  for (const state of ["completed", "failed", "reserved"] as const) {
    const result = await reserveProReceiptV2(initial, async () => recurring,
      async () => ({ data: {
        allowed: false, operation: initial.operation, request_key: key,
        state, replayed: true,
      }, error: null }), now);
    eq(result.status, 409, "no reuse of closed or stale Pro receipt");
    const b = result.body as { allowed?: boolean };
    eq(b.allowed, false, "no authorization after replay denial");
  }
});

Deno.test("Pro finalize result is immutable across repeated and conflicting calls", async () => {
  const storage = new Map<string, { success: boolean }>();
  let writes = 0;
  const rpc: ProFinalizeRpc = async args => {
    const prev = storage.get(args.p_client_request_id);
    const state = (prev?.success ?? args.p_success) ? "completed" : "failed";
    if (prev && prev.success !== args.p_success) {
      return { data: {
        ok: false, conflict: true, operation: args.p_operation,
        request_key: key, state,
      }, error: null };
    }
    if (!prev) {
      writes++; storage.set(args.p_client_request_id, { success: args.p_success });
    }
    return { data: {
      ok: true, operation: args.p_operation,
      request_key: key, state, replayed: !!prev,
    }, error: null };
  };
  const first = await finalizeProReceiptV2({ ...initial, success: true }, rpc);
  const replay = await finalizeProReceiptV2({ ...initial, success: true }, rpc);
  const opposite = await finalizeProReceiptV2({ ...initial, success: false }, rpc);
  eq(first, { status: 200, body: {
    ok: true, operation: "import_matrix", request_key: key,
    state: "completed", replayed: false,
  } }, "first finalized");
  eq(replay, { status: 200, body: {
    ok: true, operation: "import_matrix", request_key: key,
    state: "completed", replayed: true,
  } }, "idempotent confirm");
  eq(opposite.status, 409, "opposite result conflict");
  eq(writes, 1, "finalize changed database exactly once");
});

Deno.test("lost finalize reply does not flip the previously persisted decision", async () => {
  let stored: boolean | undefined;
  const rpc: ProFinalizeRpc = async input => {
    if (stored === undefined) {
      stored = input.p_success;
      throw Error("ACK lost AFTER committed finalize");
    }
    return { data: {
      ok: true, operation: input.p_operation,
      request_key: key, state: stored ? "completed" : "failed",
      replayed: true,
    }, error: null };
  };
  const request = { ...initial, success: false };
  eq((await finalizeProReceiptV2(request, rpc)).status, 503, "unknown ACK");
  eq((await finalizeProReceiptV2(request, rpc)).status, 200, "recovered ACK");
  eq(stored, false, "original result unchanged");
});

Deno.test("finalization cannot accept another receipt, forged outcome or wrong type", async () => {
  for (const data of [
    { ok: true, operation: "import_font", request_key: key, state: "completed", replayed: false },
    { ok: true, operation: "import_matrix", request_key: "pro_other", state: "completed", replayed: false },
    { ok: true, operation: "import_matrix", request_key: key, state: "failed", replayed: false },
    { ok: true, operation: "import_matrix", request_key: key, state: "completed", replayed: "true" },
    { ok: false, operation: "import_matrix", request_key: key, state: "completed" },
    { ok: true, operation: "import_matrix", request_key: key, state: "reserved", replayed: false },
  ]) {
    const result = await finalizeProReceiptV2({ ...initial, success: true },
      async () => ({ data, error: null }));
    eq(result.status, 503, "invalid receipt outcome");
  }
  eq((await finalizeProReceiptV2({
    ...initial, success: "yes" as unknown as boolean,
  }, async () => { throw Error("No RPC"); })).status, 400, "boolean required");
});
