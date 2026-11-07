import {
  handleProV2,
  type ProV2Dependencies,
  type ProV2RouteInput,
} from "./pro-v2-route.ts";
import type { TrustedEntitlement } from "./pro-receipt-v2.ts";

const identity = {
  authenticatedUserId: "11111111-2222-4333-8444-555555555555",
  authenticatedEmail: "verified@example.test",
};
const clientRequestId = "12345678-1234-4234-8234-123456789abc";
const key = "pro_" + clientRequestId;
const time = Date.parse("2026-11-07T18:00:00Z");
const entitlement: TrustedEntitlement = {
  authenticated: true, user: {
    id: identity.authenticatedUserId, email: identity.authenticatedEmail,
  },
  pro: true, planCode: "pro_monthly", status: "active",
  currentPeriodEnd: "2026-11-30T23:59:59Z",
};
const request: ProV2RouteInput = {
  action: "reserve_pro_v2",
  identity,
  operation: "import_matrix",
  clientRequestId,
};
function eq(actual: unknown, expected: unknown, message: string) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw Error(message + ": " + JSON.stringify(actual) +
      " !== " + JSON.stringify(expected));
  }
}

Deno.test("Pro V2 disabled by default never reaches entitlement or database", async () => {
  let calls = 0;
  const deps: ProV2Dependencies = {
    enabled: false,
    getTrustedEntitlement: async () => { calls++; throw Error("forbidden"); },
    reserve: async () => { calls++; throw Error("forbidden"); },
    finalize: async () => { calls++; throw Error("forbidden"); },
  };
  for (const action of ["reserve_pro_v2", "finalize_pro_v2"] as const) {
    const result = await handleProV2({ ...request, action, success: action ===
      "finalize_pro_v2" ? false : undefined }, deps);
    eq(result.status, 503, "flag disabled");
  }
  eq(calls, 0, "flag blocks every call");
});

Deno.test("Pro V2 active verified owner may reserve only via server RPC", async () => {
  let entitlementCalls = 0;
  let reserveCalls = 0;
  const result = await handleProV2(request, {
    enabled: true,
    nowMillis: time,
    getTrustedEntitlement: async received => {
      eq(received, identity, "verified context");
      entitlementCalls++;
      return entitlement;
    },
    reserve: async args => {
      eq(args, {
        p_user_id: identity.authenticatedUserId,
        p_operation: "import_matrix",
        p_client_request_id: clientRequestId,
        p_plan_code: "pro_monthly",
      }, "server RPC arguments");
      reserveCalls++;
      return {
        data: {
          allowed: true, operation: "import_matrix", request_key: key,
          state: "reserved", replayed: false, pro: true, remaining: null,
        },
        error: null,
      };
    },
    finalize: async () => { throw Error("reserve never finalizes"); },
  });
  eq(result.status, 200, "reserve receipt confirmed");
  eq(entitlementCalls, 1, "one server authority read");
  eq(reserveCalls, 1, "one DB reserve");
});

Deno.test("Pro V2 entitlement revocation or expiry never calls SQL", async () => {
  let writes = 0;
  for (const state of [
    { ...entitlement, pro: false },
    { ...entitlement, status: "revoked" },
    { ...entitlement, currentPeriodEnd: "2026-11-06T18:00:00Z" },
    { ...entitlement, user: {
      id: "99999999-2222-4333-8444-555555555555", email: identity.authenticatedEmail,
    } },
  ]) {
    const result = await handleProV2(request, {
      enabled: true, nowMillis: time,
      getTrustedEntitlement: async () => state,
      reserve: async () => {
        writes++; throw Error("never reserve for invalid entitlement");
      },
      finalize: async () => { throw Error("never finalize"); },
    });
    eq(result.status, 403, "invalid authority rejected");
  }
  eq(writes, 0, "no SQL on expired or impersonated Pro");
});

Deno.test("Pro V2 finalization works after trial expiry WITHOUT entitlement refetch", async () => {
  let lookupCalls = 0;
  let finalizeCalls = 0;
  const result = await handleProV2({
    ...request, action: "finalize_pro_v2", success: true,
  }, {
    enabled: true,
    getTrustedEntitlement: async () => {
      lookupCalls++;
      throw Error("the expired account must not block settling old receipt");
    },
    reserve: async () => { throw Error("finalization cannot mint receipt"); },
    finalize: async args => {
      eq(args, {
        p_user_id: identity.authenticatedUserId,
        p_operation: "import_matrix",
        p_client_request_id: clientRequestId,
        p_success: true,
      }, "owner-bound existing receipt");
      finalizeCalls++;
      return {
        data: {
          ok: true, operation: "import_matrix", request_key: key,
          state: "completed", replayed: true,
        },
        error: null,
      };
    },
  });
  eq(result.status, 200, "completed receipt");
  eq(lookupCalls, 0, "no new entitlement required for prior receipt");
  eq(finalizeCalls, 1, "one immutable finalization");
});

Deno.test("Pro V2 finalization rejects ambiguous opposite outcome", async () => {
  const result = await handleProV2({
    ...request, action: "finalize_pro_v2", success: false,
  }, {
    enabled: true,
    getTrustedEntitlement: async () => { throw Error("no lookup"); },
    reserve: async () => { throw Error("no reserve"); },
    finalize: async () => ({
      data: {
        ok: false, conflict: true,
        request_key: key, operation: "import_matrix", state: "completed",
      },
      error: null,
    }),
  });
  eq(result.status, 409, "remote immutable decision returned");
});

Deno.test("Pro V2 invalid fields never touch any authority", async () => {
  let hits = 0;
  const deps: ProV2Dependencies = {
    enabled: true,
    getTrustedEntitlement: async () => {
      hits++; throw Error("should not be called");
    },
    reserve: async () => {
      hits++; throw Error("should not be called");
    },
    finalize: async () => {
      hits++; throw Error("should not be called");
    },
  };
  const bad = [
    { ...request, action: "finalize_pro_v2" as const },
    { ...request, action: "finalize_pro_v2" as const, success: "true" as unknown as boolean },
    { ...request, success: false },
    { ...request, clientRequestId: "00000000-0000-4000-8000-invalidxxxxx" },
  ];
  for (const item of bad) {
    const result = await handleProV2(item, deps);
    eq(result.status, 400, "bad input rejected");
  }
  eq(hits, 0, "bad input never queried authority");
});

Deno.test("Pro V2 database offline fails closed", async () => {
  const result = await handleProV2(request, {
    enabled: true, nowMillis: time,
    getTrustedEntitlement: async () => entitlement,
    reserve: async () => ({ data: null, error: Error("database not deployed") }),
    finalize: async () => { throw Error("no finalize"); },
  });
  eq(result.status, 503, "cannot authorize Pro when table unavailable");
});
