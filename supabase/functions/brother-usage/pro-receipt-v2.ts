/**
 * Brother Matrizes Pro V2 — OFFLINE CONTRACT, NOT WIRED OR DEPLOYED.
 *
 * Trusted Edge Function MUST authenticate getUser and call the existing
 * brother-entitlement authority server-side. The Windows client must NOT
 * supply an entitlement or a plan code. All database effects are injected
 * here to allow tests without customer data or production credentials.
 *
 * A unique UUIDv4, persisted locally before the first request, identifies
 * exactly one operational receipt; it is NOT a licence or payment event.
 */
export type ProImportOperation = "import_font" | "import_matrix";
export type ProPlanCode =
  | "trial"
  | "pro_monthly"
  | "pro_yearly"
  | "pro_lifetime"
  | "pro_lifetime_launch";

export type VerifiedProIdentity = {
  authenticatedUserId: string;
  authenticatedEmail: string;
};

export type TrustedEntitlement = {
  authenticated: boolean;
  user?: { id?: string; email?: string };
  pro?: boolean;
  status?: string;
  planCode?: string;
  currentPeriodEnd?: string | null;
  trialRemainingSeconds?: number;
};

export type ProReceiptRequest = VerifiedProIdentity & {
  operation: ProImportOperation;
  clientRequestId: string;
};

export type ProReceiptRpc = (args: {
  p_user_id: string;
  p_operation: ProImportOperation;
  p_client_request_id: string;
  p_plan_code: ProPlanCode;
}) => Promise<{ data: unknown; error: unknown }>;

export type ProFinalizeRequest = ProReceiptRequest & { success: boolean };
export type ProFinalizeRpc = (args: {
  p_user_id: string;
  p_operation: ProImportOperation;
  p_client_request_id: string;
  p_success: boolean;
}) => Promise<{ data: unknown; error: unknown }>;

export type ProReceiptReply =
  | { allowed: true; operation: ProImportOperation; request_key: string;
      state: "reserved"; replayed: boolean; pro: true; remaining: null }
  | { allowed: false; operation: ProImportOperation; request_key?: string;
      state?: "reserved" | "completed" | "failed"; replayed?: boolean };

export type ProFinalizeReply =
  | { ok: true; operation: ProImportOperation; request_key: string;
      state: "completed" | "failed"; replayed: boolean }
  | { ok: false; operation: ProImportOperation; request_key: string;
      state: "completed" | "failed"; conflict: true };

export type ProResult<T> = {
  status: number;
  body: T | { message: string };
};

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const uuidV4 =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const planCodes: readonly string[] = [
  "trial", "pro_monthly", "pro_yearly", "pro_lifetime", "pro_lifetime_launch",
];

function validIdentity(input: ProReceiptRequest): boolean {
  return uuid.test(input.authenticatedUserId) &&
    typeof input.authenticatedEmail === "string" &&
    input.authenticatedEmail.trim().length > 3 &&
    input.authenticatedEmail.length <= 254 &&
    uuidV4.test(input.clientRequestId) &&
    (input.operation === "import_font" || input.operation === "import_matrix");
}

function validPeriodEnd(value: unknown, nowMillis: number): boolean {
  if (typeof value !== "string" ||
      !/^\d{4}-\d\d-\d\dT/.test(value) ||
      !/(?:Z|[+-]\d\d:\d\d)$/.test(value)) return false;
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) && parsed > nowMillis;
}

/**
 * A trusted, server-fetched entitlement must match the authenticated user.
 * Trial and recurring plans require a positive server-computed expiry.
 * Lifetime requires active server status but no date.
 */
export function eligibleProPlan(
  identity: VerifiedProIdentity,
  entitlement: TrustedEntitlement,
  nowMillis: number,
): ProPlanCode | null {
  if (!uuid.test(identity.authenticatedUserId) ||
      !Number.isFinite(nowMillis) ||
      entitlement.authenticated !== true ||
      entitlement.user?.id !== identity.authenticatedUserId ||
      entitlement.user?.email?.toLowerCase() !== identity.authenticatedEmail.toLowerCase() ||
      entitlement.pro !== true ||
      entitlement.status !== "active" ||
      typeof entitlement.planCode !== "string" ||
      !planCodes.includes(entitlement.planCode)) return null;

  const plan = entitlement.planCode as ProPlanCode;
  if (plan === "pro_lifetime" || plan === "pro_lifetime_launch") return plan;
  if (!validPeriodEnd(entitlement.currentPeriodEnd, nowMillis)) return null;
  if (plan === "trial" &&
      (!Number.isFinite(entitlement.trialRemainingSeconds) ||
        (entitlement.trialRemainingSeconds ?? 0) <= 0)) return null;
  return plan;
}

export async function reserveProReceiptV2(
  input: ProReceiptRequest,
  trustedEntitlement: (identity: VerifiedProIdentity) => Promise<TrustedEntitlement>,
  rpc: ProReceiptRpc,
  nowMillis = Date.now(),
): Promise<ProResult<ProReceiptReply>> {
  if (!validIdentity(input)) {
    return { status: 400, body: { message: "Identificador de reserva Pro inválido." } };
  }

  // No DB access until the trusted server has verified entitlement.
  let plan: ProPlanCode | null;
  try {
    const entitlement = await trustedEntitlement({
      authenticatedUserId: input.authenticatedUserId,
      authenticatedEmail: input.authenticatedEmail,
    });
    plan = eligibleProPlan(input, entitlement, nowMillis);
  } catch {
    return { status: 503, body: { message: "Autoridade de licença indisponível." } };
  }
  if (plan === null) {
    return { status: 403, body: { message: "Licença Pro não confirmada pelo servidor." } };
  }
  try {
    const { data, error } = await rpc({
      p_user_id: input.authenticatedUserId,
      p_operation: input.operation,
      p_client_request_id: input.clientRequestId,
      p_plan_code: plan,
    });
    if (error || !data || typeof data !== "object" || Array.isArray(data)) {
      throw Error("No verified database result");
    }
    const result = data as Record<string, unknown>;
    const requestKey = "pro_" + input.clientRequestId;
    if (result.operation !== input.operation ||
        typeof result.allowed !== "boolean" ||
        (result.request_key !== undefined && result.request_key !== requestKey) ||
        (result.replayed !== undefined && typeof result.replayed !== "boolean")) {
      throw Error("Invalid receipt identity");
    }
    if (result.allowed === false) {
      if (result.state !== undefined &&
          !["reserved", "completed", "failed"].includes(String(result.state))) {
        throw Error("Unknown denial state");
      }
      return { status: 409, body: {
        allowed: false, operation: input.operation,
        ...(result.request_key === requestKey ? { request_key: requestKey } : {}),
        ...(typeof result.state === "string"
          ? { state: result.state as "reserved" | "completed" | "failed" } : {}),
        ...(typeof result.replayed === "boolean"
          ? { replayed: result.replayed } : {}),
      } };
    }
    if (result.request_key !== requestKey ||
        result.state !== "reserved" ||
        typeof result.replayed !== "boolean" ||
        result.pro !== true ||
        result.remaining !== null) throw Error("Unverified receipt approval");
    return { status: 200, body: {
      allowed: true, operation: input.operation,
      request_key: requestKey, state: "reserved",
      replayed: result.replayed, pro: true, remaining: null,
    } };
  } catch {
    return { status: 503, body: { message: "Reserva Pro ainda não confirmada." } };
  }
}

/**
 * Finalization only reads the server-owned receipt with a verified user.
 * It does NOT require the plan to remain active: expiring subscriptions
 * must not orphan a previously approved receipt. The RPC must enforce
 * owner, operation, immutable outcome and single transition atomically.
 */
export async function finalizeProReceiptV2(
  input: ProFinalizeRequest,
  rpc: ProFinalizeRpc,
): Promise<ProResult<ProFinalizeReply>> {
  if (!validIdentity(input) || typeof input.success !== "boolean") {
    return { status: 400, body: { message: "Finalização Pro inválida." } };
  }
  try {
    const { data, error } = await rpc({
      p_user_id: input.authenticatedUserId,
      p_operation: input.operation,
      p_client_request_id: input.clientRequestId,
      p_success: input.success,
    });
    if (error || !data || typeof data !== "object" || Array.isArray(data)) {
      throw Error("Unverified finalize outcome");
    }
    const receipt = data as Record<string, unknown>;
    const requestKey = "pro_" + input.clientRequestId;
    if (receipt.operation !== input.operation ||
        receipt.request_key !== requestKey ||
        (receipt.state !== "completed" && receipt.state !== "failed")) {
      throw Error("Wrong receipt returned");
    }
    if (receipt.ok === false && receipt.conflict === true) {
      return { status: 409, body: {
        ok: false, operation: input.operation, request_key: requestKey,
        state: receipt.state, conflict: true,
      } };
    }
    if (receipt.ok !== true || typeof receipt.replayed !== "boolean" ||
        receipt.state !== (input.success ? "completed" : "failed")) {
      throw Error("Finalize was not confirmed");
    }
    return { status: 200, body: {
      ok: true, operation: input.operation, request_key: requestKey,
      state: receipt.state, replayed: receipt.replayed,
    } };
  } catch {
    return { status: 503, body: { message: "Finalização Pro não confirmada." } };
  }
}
