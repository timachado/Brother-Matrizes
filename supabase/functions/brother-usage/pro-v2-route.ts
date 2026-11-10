/**
 * Pro V2 Edge Function routing (source-only, not deployed).
 *
 * Dependency injection makes the enabled/disabled transitions testable with
 * no server, no secrets and no customer database. A deployment defaults to
 * DISABLED unless BROTHER_PRO_V2_ENABLED is exactly "true".
 * Pro grant decisions come only from brother-entitlement on the server.
 */
import {
  finalizeProReceiptV2,
  reserveProReceiptV2,
  type ProFinalizeRpc,
  type ProImportOperation,
  type ProReceiptRpc,
  type TrustedEntitlement,
  type VerifiedProIdentity,
} from "./pro-receipt-v2.ts";

export type ProV2Action = "reserve_pro_v2" | "finalize_pro_v2";

export type ProV2RouteInput = {
  action: ProV2Action;
  identity: VerifiedProIdentity;
  operation: ProImportOperation;
  clientRequestId: string;
  success?: boolean;
};

export type ProV2Dependencies = {
  enabled: boolean;
  getTrustedEntitlement: (
    identity: VerifiedProIdentity
  ) => Promise<TrustedEntitlement>;
  reserve: ProReceiptRpc;
  finalize: ProFinalizeRpc;
  nowMillis?: number;
};

export async function handleProV2(
  request: ProV2RouteInput,
  deps: ProV2Dependencies,
): Promise<{ status: number; body: unknown }> {
  // Fail closed BEFORE any external service/RPC call.
  if (deps.enabled !== true) {
    return {
      status: 503,
      body: { message: "Recibos Pro V2 ainda não habilitados para homologação." },
    };
  }

  if (request.action === "reserve_pro_v2") {
    if (request.success !== undefined) {
      return { status: 400, body: { message: "Reserva Pro V2 inválida." } };
    }
    return reserveProReceiptV2({
      authenticatedUserId: request.identity.authenticatedUserId,
      authenticatedEmail: request.identity.authenticatedEmail,
      operation: request.operation,
      clientRequestId: request.clientRequestId,
    }, deps.getTrustedEntitlement, deps.reserve, deps.nowMillis ?? Date.now());
  }
  if (request.action === "finalize_pro_v2") {
    // An already persisted receipt must remain finalizable after a
    // subscription expires. The server MUST still verify the user identity
    // and the RPC checks ownership, operation and immutable outcome.
    if (typeof request.success !== "boolean") {
      return { status: 400, body: { message: "Finalização Pro V2 inválida." } };
    }
    return finalizeProReceiptV2({
      authenticatedUserId: request.identity.authenticatedUserId,
      authenticatedEmail: request.identity.authenticatedEmail,
      operation: request.operation,
      clientRequestId: request.clientRequestId,
      success: request.success,
    }, deps.finalize);
  }
  return { status: 400, body: { message: "Ação Pro V2 inválida." } };
}
