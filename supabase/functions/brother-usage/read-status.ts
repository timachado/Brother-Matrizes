/**
 * Brother Matrizes — additive read-only lookup for an EXISTING persisted
 * import reservation, scoped to the verified user and operation.
 *
 * This module does not connect to Supabase itself: a server-only adapter
 * must supply the authenticated DB query. It performs NO inserts, updates,
 * deletes, quota retries, entitlement grants or reconciliation by itself.
 *
 * Import status is not a replacement for an idempotent client-request key:
 * a lost 'reserve' reply has no known server-generated request key to query.
 */
export type KnownImportOperation = "import_matrix" | "import_font";
export type KnownImportState = "reserved" | "completed" | "failed";

export type ImportRecord = { state: unknown };
export type ImportLookup = (
  requestId: string, authenticatedUserId: string, operation: KnownImportOperation
) => Promise<{ data: ImportRecord | null; error: unknown }>;

export interface KnownImportStatusInput {
  userId: string;
  operation: KnownImportOperation;
  requestKey: string;
}

export interface KnownImportStatusResult {
  status: number;
  body:
    | { found: false; operation: KnownImportOperation; request_key: string }
    | { found: true; operation: KnownImportOperation; request_key: string; state: KnownImportState }
    | { message: string };
}

const persistedKey =
  /^usage_([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/;
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

export async function readKnownImportStatus(
  input: KnownImportStatusInput, lookup: ImportLookup
): Promise<KnownImportStatusResult> {
  const match = persistedKey.exec(input.requestKey);
  if (!match || !uuid.test(input.userId) ||
      !["import_matrix", "import_font"].includes(input.operation)) {
    return { status: 400, body: { message: "Consulta de cota inválida." } };
  }
  try {
    const result = await lookup(match[1], input.userId, input.operation);
    if (result.error) {
      return { status: 503, body: { message: "Estado da reserva indisponível." } };
    }
    if (!result.data) {
      return { status: 200, body: {
        found: false, operation: input.operation, request_key: input.requestKey,
      } };
    }
    const state = result.data.state;
    if (state !== "reserved" && state !== "completed" && state !== "failed") {
      return { status: 503, body: { message: "Estado desconhecido: revisão manual obrigatória." } };
    }
    return { status: 200, body: {
      found: true, operation: input.operation,
      request_key: input.requestKey, state,
    } };
  } catch {
    return { status: 503, body: { message: "Estado da reserva indisponível." } };
  }
}
