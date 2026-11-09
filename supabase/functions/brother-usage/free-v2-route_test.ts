import { handleFreeV2Reserve } from "./free-v2-route.ts";
import type { ReserveV2Rpc } from "./reserve-v2.ts";

const account = "11111111-2222-4333-8444-555555555555";
const requestId = "12345678-1234-4234-8234-123456789abc";
const input = {
  authenticatedUserId: account,
  operation: "import_matrix" as const,
  clientRequestId: requestId,
};

function assertEq(actual: unknown, expected: unknown, name: string): void {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(name + " expected " + JSON.stringify(expected) +
      " but received " + JSON.stringify(actual));
  }
}

Deno.test("Free V2 defaults to denied and never calls the RPC", async () => {
  let calls = 0;
  const reserve: ReserveV2Rpc = async () => {
    calls++;
    throw Error("Not allowed to touch any database before operator rollout");
  };
  for (const enabled of [false, undefined, null, "true", 1]) {
    const output = await handleFreeV2Reserve(input, {
      enabled: enabled as boolean,
      reserve,
    });
    assertEq(output.status, 503, "disabled Free V2");
  }
  assertEq(calls, 0, "no SQL call");
});

Deno.test("Free V2 approved route returns exact idempotent receipt", async () => {
  let calls = 0;
  const reserve: ReserveV2Rpc = async args => {
    calls++;
    assertEq(args.p_user_id, account, "verified server principal");
    assertEq(args.p_client_request_id, requestId, "persisted client UUID");
    assertEq(args.p_operation, "import_matrix", "operation");
    return {
      data: {
        allowed: true,
        operation: "import_matrix",
        request_key: "usage_" + requestId,
        state: "reserved",
        replayed: calls > 1,
        remaining: 4,
      },
      error: null,
    };
  };
  const settings = { enabled: true, reserve };
  const first = await handleFreeV2Reserve(input, settings);
  const retry = await handleFreeV2Reserve(input, settings);
  assertEq(first.status, 200, "first success");
  assertEq(retry.status, 200, "same UUID replay success");
  assertEq((first.body as Record<string, unknown>).replayed, false, "initial");
  assertEq((retry.body as Record<string, unknown>).replayed, true, "replayed");
  assertEq(calls, 2, "both requests use same UUID");
});

Deno.test("Free V2 never trusts invalid RPC responses even when enabled", async () => {
  let calls = 0;
  const reserve: ReserveV2Rpc = async () => {
    calls++;
    return {
      data: {
        allowed: true,
        operation: "import_matrix",
        request_key: "pro_" + requestId,
        state: "reserved",
        replayed: false,
        remaining: 4,
      },
      error: null,
    };
  };
  const answer = await handleFreeV2Reserve(input, { enabled: true, reserve });
  assertEq(answer.status, 503, "Pro receipt cannot pass Free V2");
  assertEq(calls, 1, "no fallback");
});
