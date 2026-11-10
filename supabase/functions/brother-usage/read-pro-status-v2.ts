/**
 * Read-only status of a previously issued Pro V2 operational receipt.
 * Owner and operation are taken from Supabase auth.getUser(), never client
 * supplied. No entitlement lookup is needed after a receipt was created:
 * expired subscriptions may still require transaction reconciliation.
 *
 * This helper never creates a Pro licence, charges, finalizes, retries,
 * deletes or clears the Windows journal. The table/RPC remain undeployed.
 */
export type ProStatusOperation = "import_font" | "import_matrix";
export type ProStatusState = "reserved" | "completed" | "failed";
export type ProStatusLookup = (
  requestId: string,
  userId: string,
  operation: ProStatusOperation,
) => Promise<{ data: { state: unknown } | null; error: unknown }>;
export type ProStatusInput = {
  authenticatedUserId: string;
  operation: ProStatusOperation;
  requestKey: string;
};
export type ProStatusResult = {
  status: number;
  body:
    | { found: false; operation: ProStatusOperation; request_key: string }
    | { found: true; operation: ProStatusOperation; request_key: string; state: ProStatusState }
    | { message: string };
};

const userUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const proV2Key =
  /^pro_([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})$/;

export async function readProReceiptStatusV2(
  input: ProStatusInput,
  lookup: ProStatusLookup,
): Promise<ProStatusResult> {
  const match = proV2Key.exec(input.requestKey);
  if (!match ||
      !userUuid.test(input.authenticatedUserId) ||
      (input.operation !== "import_font" && input.operation !== "import_matrix")) {
    return { status: 400, body: { message: "Consulta de recibo Pro inválida." } };
  }
  try {
    const { data, error } = await lookup(
      match[1], input.authenticatedUserId, input.operation,
    );
    if (error) throw Error("Database status unavailable");
    if (!data) {
      // NOT_FOUND is NOT permission to re-reserve or delete local content.
      return { status: 200, body: {
        found: false, operation: input.operation, request_key: input.requestKey,
      } };
    }
    if (data.state !== "reserved" && data.state !== "completed" &&
        data.state !== "failed") {
      throw Error("Unrecognized database receipt state");
    }
    return { status: 200, body: {
      found: true, operation: input.operation,
      request_key: input.requestKey, state: data.state,
    } };
  } catch {
    return { status: 503, body: { message: "Estado do recibo Pro indisponível." } };
  }
}
