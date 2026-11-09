/** No database or network calls: describes the configured V2 capability only. */
export type V2ImportOperation = "import_font" | "import_matrix";
export type V2ImportProtocol = "free_v2" | "pro_v2";

export function describeV2Capability(input: {
  operation: V2ImportOperation;
  isPro: boolean;
  freeV2Enabled: boolean;
  proV2Enabled: boolean;
}): { operation: V2ImportOperation; protocol: V2ImportProtocol; ready: boolean } {
  if (input.operation !== "import_font" && input.operation !== "import_matrix") {
    throw new Error("Invalid V2 import operation");
  }
  // Pro has an independent idempotent receipt protocol; never downshift to Free.
  if (input.isPro) {
    return { operation: input.operation, protocol: "pro_v2", ready: input.proV2Enabled === true };
  }
  return { operation: input.operation, protocol: "free_v2", ready: input.freeV2Enabled === true };
}
