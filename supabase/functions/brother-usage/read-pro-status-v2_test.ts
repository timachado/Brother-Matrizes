import { readProReceiptStatusV2 } from "./read-pro-status-v2.ts";

const user = "11111111-2222-4333-8444-555555555555";
const requestId = "12345678-1234-4234-8234-123456789abc";
const requestKey = "pro_" + requestId;
function equal(actual: unknown, expected: unknown, label: string): void {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw Error(label + " expected " + JSON.stringify(expected) +
      ", got " + JSON.stringify(actual));
  }
}

for (const operation of ["import_font", "import_matrix"] as const) {
  for (const state of ["reserved", "completed", "failed"] as const) {
    Deno.test("Pro V2 read-only owner-scoped " + operation + " " + state, async () => {
      let calls = 0;
      const result = await readProReceiptStatusV2({
        authenticatedUserId: user, operation, requestKey,
      }, async (id, owner, op) => {
        equal(id, requestId, "UUID");
        equal(owner, user, "verified user");
        equal(op, operation, "operation");
        calls++;
        return { data: { state }, error: null };
      });
      equal(result, { status: 200, body: {
        found: true, operation, request_key: requestKey, state,
      } }, "read-only status");
      equal(calls, 1, "single SELECT only");
    });
  }
}

Deno.test("Pro V2 foreign or missing receipt is NOT a confirmed refund", async () => {
  const result = await readProReceiptStatusV2({
    authenticatedUserId: user, operation: "import_matrix", requestKey,
  }, async () => ({ data: null, error: null }));
  equal(result, { status: 200, body: {
    found: false, operation: "import_matrix", request_key: requestKey,
  } }, "absent record does not authorize import");
});

Deno.test("Pro V2 rejects legacy, Free, malformed and injection keys before SQL", async () => {
  let calls = 0;
  const ids = [
    "pro_12345678-1234-1234-1234-123456789abc", // legacy Pro V1 UUID
    "usage_" + requestId, "",
    "pro_00000000-0000-4000-8000-000000000000" + "\r\nInjected: true",
    requestKey.toUpperCase(),
    requestKey + "/../", "pro_../etc/passwd",
  ];
  for (const key of ids) {
    const result = await readProReceiptStatusV2({
      authenticatedUserId: user, operation: "import_matrix", requestKey: key,
    }, async () => { calls++; throw Error("SQL must not be called"); });
    equal(result.status, 400, "malformed receipt blocked");
  }
  equal(calls, 0, "invalid key never queried database");
});

Deno.test("Pro V2 identity and operation are mandatory before SQL", async () => {
  let calls = 0;
  for (const input of [
    { authenticatedUserId: "invalid", operation: "import_matrix" as const, requestKey },
    { authenticatedUserId: user, operation: "admin" as "import_font", requestKey },
  ]) {
    const result = await readProReceiptStatusV2(input, async () => {
      calls++; throw Error("Must not query DB");
    });
    equal(result.status, 400, "unverified input");
  }
  equal(calls, 0, "no DB reads");
});

Deno.test("Pro V2 SQL error or unknown state remains blocked", async () => {
  for (const lookup of [
    async () => ({ data: null, error: Error("database unavailable") }),
    async () => { throw Error("timeout"); },
    async () => ({ data: { state: "expired but unknown" }, error: null }),
    async () => ({ data: { state: "paid" }, error: null }),
  ]) {
    const result = await readProReceiptStatusV2({
      authenticatedUserId: user, operation: "import_matrix", requestKey,
    }, lookup);
    equal(result.status, 503, "never trust missing or ambiguous state");
  }
});
