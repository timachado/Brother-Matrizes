/**
 * Additive, NOT DEPLOYED Brother Matrizes idempotent free import reservation.
 *
 * The client persists a random UUIDv4 BEFORE the first send. The proposed
 * transactional Postgres RPC uses that UUID as public.import_usage.request_id;
 * therefore an identical request never creates another reserved quota row,
 * even if the first HTTP response was lost.
 *
 * This pure adapter is testable without the database, network, secrets,
 * payment system or production entitlement. It MUST only be called after
 * the authenticated server has verified the current Supabase user and that
 * the account is FREE. Pro receipts still require a separate durable path.
 */
export type ImportOperationV2 = "import_matrix" | "import_font";

export type IdempotentReserveInput = {
  authenticatedUserId: string;
  operation: ImportOperationV2;
  clientRequestId: string;
};

export type IdempotentReserveResponse =
  | {
      allowed: true;
      operation: ImportOperationV2;
      request_key: string;
      state: "reserved";
      replayed: boolean;
      remaining: number | null;
    }
  | {
      allowed: false;
      operation: ImportOperationV2;
      request_key?: string;
      state?: "reserved" | "failed" | "completed";
      replayed?: boolean;
      remaining?: number;
    };

export type ReserveV2Rpc = (args: {
  p_user_id: string;
  p_operation: ImportOperationV2;
  p_client_request_id: string;
}) => Promise<{ data: unknown; error: unknown }>;

const canonicalUuid =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const randomUuidV4 =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

export async function reserveImportV2(
  input: IdempotentReserveInput, rpc: ReserveV2Rpc,
): Promise<{ status: number; body: IdempotentReserveResponse | { message: string } }> {
  if (!canonicalUuid.test(input.authenticatedUserId) ||
      !randomUuidV4.test(input.clientRequestId) ||
      !["import_matrix", "import_font"].includes(input.operation)) {
    return { status: 400, body: { message: "Identificador de importação inválido." } };
  }
  try {
    const result = await rpc({
      p_user_id: input.authenticatedUserId,
      p_operation: input.operation,
      p_client_request_id: input.clientRequestId,
    });
    if (result.error || !result.data || typeof result.data !== "object" ||
        Array.isArray(result.data)) {
      return { status: 503, body: { message: "Reserva idempotente indisponível." } };
    }
    const data = result.data as Record<string, unknown>;
    if (data.operation !== input.operation ||
        (data.allowed !== true && data.allowed !== false)) {
      return { status: 503, body: { message: "Contrato da reserva inválido." } };
    }
    if (data.allowed === false) {
      // A quota rejection MUST NOT mint any new key. A replayed, known
      // expired/finalized reservation may include the ORIGINAL key only.
      const key = data.request_key;
      if (key !== undefined &&
          key !== "usage_" + input.clientRequestId) {
        return { status: 503, body: { message: "Reserva retornada divergente." } };
      }
      if (data.state !== undefined &&
          !["reserved", "completed", "failed"].includes(String(data.state))) {
        return { status: 503, body: { message: "Estado remoto desconhecido." } };
      }
      if (data.replayed !== undefined && typeof data.replayed !== "boolean") {
        return { status: 503, body: { message: "Replay remoto inválido." } };
      }
      if (data.remaining !== undefined &&
          (!Number.isInteger(data.remaining) ||
            (data.remaining as number) < 0 ||
            (data.remaining as number) > 5)) {
        return { status: 503, body: { message: "Saldo de quota remoto inválido." } };
      }
      return { status: 409, body: {
        allowed: false,
        operation: input.operation,
        ...(typeof key === "string" ? { request_key: key } : {}),
        ...(typeof data.state === "string"
          ? { state: data.state as "reserved" | "failed" | "completed" } : {}),
        ...(typeof data.replayed === "boolean" ? { replayed: data.replayed } : {}),
        ...(typeof data.remaining === "number" ? { remaining: data.remaining } : {}),
      } };
    }
    if (data.request_key !== "usage_" + input.clientRequestId ||
        data.state !== "reserved" ||
        typeof data.replayed !== "boolean" ||
        (data.remaining !== null &&
          (!Number.isInteger(data.remaining) ||
            (data.remaining as number) < 0 ||
            (data.remaining as number) > 5))) {
      return { status: 503, body: { message: "Identidade da reserva não confirmada." } };
    }
    return { status: 200, body: {
      allowed: true,
      operation: input.operation,
      request_key: "usage_" + input.clientRequestId,
      state: "reserved",
      replayed: data.replayed as boolean,
      remaining: data.remaining as number | null,
    } };
  } catch {
    return { status: 503, body: { message: "Reserva idempotente indisponível." } };
  }
}
