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

/**
 * DB readiness is an independent, authenticated, read-only server probe.
 * A feature flag alone MUST NEVER claim a capability is deployable.
 * Malformed responses or a missing SQL probe always fail closed.
 */
export function confirmV2DatabaseCapability(
  configured: ReturnType<typeof describeV2Capability>,
  databaseSnapshot: unknown,
): ReturnType<typeof describeV2Capability> {
  const snapshot = databaseSnapshot !== null &&
      typeof databaseSnapshot === "object" &&
      !Array.isArray(databaseSnapshot)
    ? databaseSnapshot as Record<string, unknown>
    : null;
  const installed = snapshot?.[configured.protocol] === true;
  return {
    ...configured,
    ready: configured.ready === true && installed,
  };
}
