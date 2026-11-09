import { describeV2Capability } from "./protocol-capability-v2.ts";

function equal(actual: unknown, expected: unknown): void {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error("capability mismatch: " + JSON.stringify(actual));
  }
}

Deno.test("V2 capability distinguishes Free and Pro without granting either", () => {
  for (const operation of ["import_font", "import_matrix"] as const) {
    for (const freeV2Enabled of [false, true]) {
      for (const proV2Enabled of [false, true]) {
        equal(describeV2Capability({
          operation, isPro: false, freeV2Enabled, proV2Enabled,
        }), { operation, protocol: "free_v2", ready: freeV2Enabled });
        equal(describeV2Capability({
          operation, isPro: true, freeV2Enabled, proV2Enabled,
        }), { operation, protocol: "pro_v2", ready: proV2Enabled });
      }
    }
  }
});

Deno.test("V2 capability rejects non-import operations", () => {
  let rejected = false;
  try {
    describeV2Capability({
      operation: "grant_pro" as "import_font",
      isPro: false, freeV2Enabled: true, proV2Enabled: true,
    });
  } catch {
    rejected = true;
  }
  equal(rejected, true);
});
