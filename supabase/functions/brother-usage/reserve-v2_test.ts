import {
  reserveImportV2, type ReserveV2Rpc,
} from "./reserve-v2.ts";

const user = "11111111-2222-4333-8444-555555555555";
const request = "12345678-1234-4234-8234-123456789abc";
const key = "usage_" + request;
const operation = "import_matrix" as const;

function expect(value: unknown, expected: unknown, message: string) {
  if (JSON.stringify(value) !== JSON.stringify(expected)) {
    throw Error(message + ": expected " + JSON.stringify(expected) +
      " actual " + JSON.stringify(value));
  }
}
const okData = {
  allowed: true, operation, request_key: key,
  state: "reserved", replayed: false, remaining: 4,
};
const confirmed = {
  allowed: true, operation, request_key: key,
  state: "reserved", replayed: true, remaining: 4,
};

Deno.test("same client UUID replays exactly the same reservation, never two", async () => {
  let calls = 0;
  const storage = new Map<string, Record<string, unknown>>();
  const rpc: ReserveV2Rpc = async ({ p_user_id, p_operation, p_client_request_id }) => {
    expect(p_user_id, user, "server supplied authenticated owner");
    expect(p_operation, operation, "operation");
    expect(p_client_request_id, request, "UUIDv4");
    calls++;
    const k = [p_user_id, p_operation, p_client_request_id].join("/");
    const existing = storage.get(k);
    if (existing) return { data: { ...existing, replayed: true }, error: null };
    storage.set(k, okData);
    return { data: okData, error: null };
  };
  const input = { authenticatedUserId: user, operation, clientRequestId: request };
  const first = await reserveImportV2(input, rpc);
  const replay = await reserveImportV2(input, rpc);
  expect(first, { status: 200, body: okData }, "new request");
  expect(replay, { status: 200, body: confirmed }, "idempotent retry");
  expect(calls, 2, "both simulated HTTP calls reached RPC");
  expect(storage.size, 1, "exactly one quota reservation created");
});

Deno.test("lost first HTTP response can replay same persisted client ID", async () => {
  let writes = 0;
  let tries = 0;
  const rpc: ReserveV2Rpc = async () => {
    tries++;
    if (tries === 1) {
      writes++;
      throw Error("response lost AFTER server stored the reservation");
    }
    return { data: confirmed, error: null };
  };
  const input = { authenticatedUserId: user, operation, clientRequestId: request };
  const lost = await reserveImportV2(input, rpc);
  const replay = await reserveImportV2(input, rpc);
  expect(lost.status, 503, "unknown network status");
  expect(replay, { status: 200, body: confirmed }, "original idempotent reservation");
  expect(writes, 1, "no new quota row on replay");
});

Deno.test("denied free quota and old finalized attempt never mint another reservation", async () => {
  const input = { authenticatedUserId: user, operation, clientRequestId: request };
  for (const data of [
    { allowed: false, operation, remaining: 0 },
    { allowed: false, operation, request_key: key, state: "failed", replayed: true },
    { allowed: false, operation, request_key: key, state: "completed", replayed: true },
    { allowed: false, operation, request_key: key, state: "reserved", replayed: true },
  ]) {
    const result = await reserveImportV2(input, async () => ({ data, error: null }));
    expect(result.status, 409, "no permission to import");
  }
});

Deno.test("a cross-account or cross-operation key mismatch is rejected", async () => {
  const input = { authenticatedUserId: user, operation, clientRequestId: request };
  for (const data of [
    { ...okData, request_key: "usage_00000000-0000-4000-8000-000000000000" },
    { ...okData, operation: "import_font" },
    { ...okData, state: "completed" },
    { ...okData, replayed: "true" },
    { ...okData, remaining: -3 },
    { ...okData, remaining: 6 },
    { allowed: true, operation },
    { allowed: false, operation, request_key: "usage_00000000-0000-4000-8000-000000000000" },
    { allowed: false, operation, state: "unverified" },
  ]) {
    const result = await reserveImportV2(input, async () => ({ data, error: null }));
    expect(result.status, 503, "invalid server response must fail closed");
  }
});

Deno.test("UUIDv4, account UUID and operation validation precede any RPC", async () => {
  let calls = 0;
  const rpc: ReserveV2Rpc = async () => {
    calls++;
    throw Error("No SQL should execute for malformed identifiers");
  };
  const badInputs = [
    { authenticatedUserId: user, operation, clientRequestId: "" },
    { authenticatedUserId: user, operation, clientRequestId: "12345678-1234-1234-8234-123456789abc" },
    { authenticatedUserId: user, operation, clientRequestId: request.toUpperCase() },
    { authenticatedUserId: user, operation, clientRequestId: request + "\n" },
    { authenticatedUserId: "not-a-user", operation, clientRequestId: request },
    { authenticatedUserId: user, operation: "admin" as typeof operation, clientRequestId: request },
  ];
  for (const input of badInputs) {
    const r = await reserveImportV2(input, rpc);
    expect(r.status, 400, "bad input rejected before RPC");
  }
  expect(calls, 0, "no RPC for bad input");
});

Deno.test("RPC unavailable or unexpected response always fails closed", async () => {
  const input = { authenticatedUserId: user, operation, clientRequestId: request };
  for (const rpc of [
    async () => ({ data: null, error: Error("database unavailable") }),
    async () => ({ data: [], error: null }),
    async () => ({ data: { allowed: "true", operation }, error: null }),
    async () => { throw Error("transaction aborted"); },
  ]) {
    const result = await reserveImportV2(input, rpc);
    expect(result.status, 503, "unverified server state blocks import");
  }
});
