import {
  readKnownImportStatus, type ImportLookup,
} from "./read-status.ts";

const userId = "11111111-2222-4333-8444-555555555555";
const requestKey = "usage_12345678-1234-1234-1234-123456789abc";
const requestId = "12345678-1234-1234-1234-123456789abc";

function mustEqual(actual: unknown, expected: unknown, message: string) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw Error(message + ": expected " + JSON.stringify(expected) +
      ", actual " + JSON.stringify(actual));
  }
}

for (const operation of ["import_matrix", "import_font"] as const) {
  for (const state of ["reserved", "completed", "failed"] as const) {
    Deno.test("read-only scoped state " + operation + " " + state, async () => {
      let queries = 0;
      const lookup: ImportLookup = async (id, owner, op) => {
        queries++;
        mustEqual(id, requestId, "request ID");
        mustEqual(owner, userId, "authenticated account");
        mustEqual(op, operation, "operation");
        return { data: { state }, error: null };
      };
      const result = await readKnownImportStatus(
        { userId, operation, requestKey }, lookup,
      );
      mustEqual(result, {
        status: 200, body: {
          found: true, operation, request_key: requestKey, state,
        },
      }, "read-only outcome");
      mustEqual(queries, 1, "read-only select count");
    });
  }
}

Deno.test("unknown or foreign request never claims quota is free", async () => {
  const result = await readKnownImportStatus(
    { userId, operation: "import_matrix", requestKey },
    async () => ({ data: null, error: null }),
  );
  mustEqual(result, {
    status: 200, body: {
      found: false, operation: "import_matrix", request_key: requestKey,
    },
  }, "unknown result");
});

Deno.test("invalid, pro and injected keys rejected before query", async () => {
  let count = 0;
  for (const key of [
    "", "pro_12345678-1234-1234-1234-123456789abc",
    "usage_12345678-1234-1234-1234-123456789abx",
    requestKey + " extra", "usage_../../etc/passwd",
  ]) {
    const result = await readKnownImportStatus(
      { userId, operation: "import_matrix", requestKey: key },
      async () => { count++; throw Error("should not query"); },
    );
    mustEqual(result.status, 400, "malformed status request");
  }
  mustEqual(count, 0, "invalid keys never reach backend");
});

Deno.test("invalid identity and unknown operation rejected before query", async () => {
  let count = 0;
  const lookup: ImportLookup = async () => {
    count++; throw Error("should never query");
  };
  const invalidIdentity = await readKnownImportStatus(
    { userId: "../bad", operation: "import_matrix", requestKey }, lookup,
  );
  mustEqual(invalidIdentity.status, 400, "invalid verified id");
  const invalidOperation = await readKnownImportStatus(
    { userId, operation: "user_admin" as "import_matrix", requestKey }, lookup,
  );
  mustEqual(invalidOperation.status, 400, "invalid operation");
  mustEqual(count, 0, "no query on malformed request");
});

Deno.test("database failure and unknown state fail closed", async () => {
  for (const query of [
    async () => ({ data: null, error: Error("db unavailable") }),
    async () => { throw Error("network unavailable"); },
    async () => ({ data: { state: "other" }, error: null }),
    async () => ({ data: { state: "completed_but_unverified" }, error: null }),
  ]) {
    const result = await readKnownImportStatus(
      { userId, operation: "import_font", requestKey }, query,
    );
    mustEqual(result.status, 503, "unknown state remains blocked");
  }
});
