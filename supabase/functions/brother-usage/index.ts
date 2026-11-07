import { createClient } from "npm:@supabase/supabase-js@2.116.0";
import { readKnownImportStatus } from "./read-status.ts";
import { reserveImportV2 } from "./reserve-v2.ts";
import { handleProV2 } from "./pro-v2-route.ts";
import type { TrustedEntitlement } from "./pro-receipt-v2.ts";

/**
 * Brother Matrizes — import allowance, not a commerce authority.
 * 3 successful font / 5 successful design imports per UTC calendar month
 * for a Google-verified free account. Pro comes exclusively from the existing
 * WooCommerce-mirrored entitlement or the time-bound non-financial trial.
 *
 * Service-role key lives ONLY in Supabase secrets, never in the APK.
 * All reservation timestamps, counts and user identities are server-generated.
 */
const headers = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "authorization,apikey,content-type",
  "access-control-allow-methods": "POST,OPTIONS",
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
};
const reply = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers });
const operations = new Set(["import_font", "import_matrix"]);
const requestKey = /^(usage|pro)_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers });
  if (req.method !== "POST") return reply({ message: "Método não permitido." }, 405);

  let payload: Record<string, unknown>;
  try {
    if (Number(req.headers.get("content-length") || 0) > 400)
      return reply({ message: "Requisição muito grande." }, 413);
    const raw = await req.text();
    if (raw.length > 400) return reply({ message: "Requisição muito grande." }, 413);
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw Error("invalid");
    payload = parsed;
    const validKeys = parsed.action === "reserve"
      ? ["action", "operation"]
      : ["reserve_v2", "reserve_pro_v2"].includes(parsed.action)
        ? ["action", "operation", "client_request_id"]
        : parsed.action === "status"
          ? ["action", "operation", "request_key"]
          : ["action", "operation", "request_key", "success"];
    if (!["reserve", "reserve_v2", "reserve_pro_v2", "finalize",
      "finalize_pro_v2", "status"].includes(parsed.action) ||
      Object.keys(parsed).some(k => !validKeys.includes(k)) ||
      !validKeys.every(k => Object.hasOwn(parsed, k)) ||
      !operations.has(parsed.operation) ||
      (parsed.action === "finalize" &&
        (typeof parsed.success !== "boolean" ||
          typeof parsed.request_key !== "string" || !requestKey.test(parsed.request_key))) ||
      (parsed.action === "status" &&
        (typeof parsed.request_key !== "string" ||
          !/^usage_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
            .test(parsed.request_key))) ||
      (["reserve_v2", "reserve_pro_v2"].includes(parsed.action) &&
        (typeof parsed.client_request_id !== "string" ||
          !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
            .test(parsed.client_request_id))) ||
      (parsed.action === "finalize_pro_v2" &&
        (typeof parsed.success !== "boolean" ||
          typeof parsed.request_key !== "string" ||
          !/^pro_[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
            .test(parsed.request_key))))
      throw Error("invalid");
  } catch {
    return reply({ message: "Solicitação de importação inválida." }, 400);
  }

  const authorization = req.headers.get("authorization") || "";
  const token = authorization.toLowerCase().startsWith("bearer ") ?
    authorization.slice(7).trim() : "";
  if (token.length < 30 || token.length > 12000)
    return reply({ message: "Entre com Google na aba Minha Conta." }, 401);

  const url = Deno.env.get("SUPABASE_URL") || "";
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
  const anon = Deno.env.get("SUPABASE_ANON_KEY") || "";
  if (!url || !key || !anon) return reply({ message: "Licenciamento temporariamente indisponível." }, 503);

  const admin = createClient(url, key, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data: dataUser, error: loginError } = await admin.auth.getUser(token);
  const user = dataUser?.user;
  if (loginError || !user) return reply({ message: "Sessão Google inválida ou expirada." }, 401);
  if (!user.email || !user.email_confirmed_at)
    return reply({ message: "Confirme seu e-mail Google para utilizar as importações." }, 403);

  // Separate, explicit opt-in. Defaults to disabled even in a deploy.
  // Never enable before the Pro receipt table/RPCs, entitlement checks,
  // staging, and Windows journal have been independently homologated.
  const proV2Enabled = Deno.env.get("BROTHER_PRO_V2_ENABLED") === "true";
  if (typeof payload.action === "string" &&
      ["reserve_pro_v2", "finalize_pro_v2"].includes(payload.action) &&
      !proV2Enabled) {
    return reply({ message: "Recibos Pro V2 ainda não habilitados para homologação." }, 503);
  }

  // A persisted Pro receipt may be finalized even after its subscription
  // expires. The authenticated user is already verified above, and the
  // restricted database RPC additionally matches user, operation and UUID.
  if (payload.action === "finalize_pro_v2") {
    const result = await handleProV2({
      action: "finalize_pro_v2",
      identity: { authenticatedUserId: user.id, authenticatedEmail: user.email },
      operation: payload.operation as "import_font" | "import_matrix",
      clientRequestId: (payload.request_key as string).slice(4),
      success: payload.success as boolean,
    }, {
      enabled: proV2Enabled,
      getTrustedEntitlement: async () => {
        throw Error("Finalizing an existing receipt must never fetch or create entitlement.");
      },
      reserve: async () => {
        throw Error("Finalization must never reserve another Pro receipt.");
      },
      finalize: async args => await admin.rpc(
        "brother_matrizes_finalize_pro_import_v2", args
      ),
    });
    return reply(result.body, result.status);
  }

  // Additive, read-only diagnostic for a KNOWN 'usage_' reservation.
  // No quota changes, no rows inserted, no entitlement assertions and
  // no retries/finalization. The owner and operation are filtered together:
  // a validly formatted key is NOT sufficient to read another account.
  // Pro receipts were never persisted in this table and are intentionally
  // not supported. Lost reserve responses without a key require a separate
  // future idempotent client-request protocol, not guesswork.
  if (payload.action === "status") {
    const result = await readKnownImportStatus({
      userId: user.id,
      operation: payload.operation as "import_matrix" | "import_font",
      requestKey: payload.request_key as string,
    }, async (requestId, verifiedUserId, operation) => {
      return await admin.from("brother_matrizes_import_usage")
        .select("state")
        .eq("request_id", requestId)
        .eq("user_id", verifiedUserId)
        .eq("operation", operation)
        .maybeSingle();
    });
    return reply(result.body, result.status);
  }

  // Reuse the current entitlement authority: never duplicate or fabricate
  // WooCommerce/Efí payment checks inside this non-financial service.
  let isPro = false;
  let trustedEntitlement: TrustedEntitlement | null = null;
  try {
    const ent = await fetch(url.replace(/\/$/, "") + "/functions/v1/brother-entitlement", {
      method: "GET",
      headers: { "Authorization": "Bearer " + token, "apikey": anon, "Accept": "application/json" },
      signal: AbortSignal.timeout(10000),
    });
    if (!ent.ok) throw Error("entitlement HTTP " + ent.status);
    const body = await ent.json();
    if (!body?.authenticated || body?.user?.id !== user.id ||
      !body?.user?.email?.toLowerCase || body.user.email.toLowerCase() !== user.email.toLowerCase())
      throw Error("entitlement identity mismatch");
    trustedEntitlement = body as TrustedEntitlement;
    isPro = body.pro === true && body.status === "active" &&
      ["trial", "pro_monthly", "pro_yearly", "pro_lifetime", "pro_lifetime_launch"]
        .includes(body.planCode);
  } catch {
    return reply({ message: "Não foi possível verificar sua licença. Confira a internet e tente novamente." }, 503);
  }

  // Authenticated Pro V2 is staged behind an opt-in flag. All permission
  // fields come from the trusted brother-entitlement GET above, not the
  // client body. The SQL RPC and table are intentionally NOT deployed.
  if (payload.action === "reserve_pro_v2") {
    const result = await handleProV2({
      action: "reserve_pro_v2",
      identity: { authenticatedUserId: user.id, authenticatedEmail: user.email },
      operation: payload.operation as "import_font" | "import_matrix",
      clientRequestId: payload.client_request_id as string,
    }, {
      enabled: proV2Enabled,
      getTrustedEntitlement: async () => {
        if (!trustedEntitlement) throw Error("Server entitlement unavailable.");
        return trustedEntitlement;
      },
      reserve: async args => await admin.rpc("brother_matrizes_reserve_pro_import_v2", args),
      finalize: async () => {
        throw Error("Reserve must never finalize another Pro receipt.");
      },
    });
    return reply(result.body, result.status);
  }

  // Additive Windows-only free quota V2. Authentication, active
  // entitlement verification and EMAIL checks above remain mandatory.
  // No RPC is applied or deployed by a GitHub branch commit.
  //
  // PRO V2 remains deliberately disabled: its legacy pro_UUID receipts
  // are ephemeral and cannot be safely deduplicated without new
  // server-persisted receipts. Android V1 Pro is left untouched.
  if (payload.action === "reserve_v2") {
    if (isPro) return reply({
      message: "Reserva idempotente Pro ainda não homologada.",
    }, 503);
    const response = await reserveImportV2({
      authenticatedUserId: user.id,
      operation: payload.operation as "import_font" | "import_matrix",
      clientRequestId: payload.client_request_id as string,
    }, async args => await admin.rpc("brother_matrizes_reserve_import_v2", args));
    return reply(response.body, response.status);
  }

  if (payload.action === "reserve" && isPro) {
    return reply({ allowed: true, request_key: "pro_" + crypto.randomUUID(),
      operation: payload.operation, remaining: null, pro: true });
  }
  if (payload.action === "finalize" && (payload.request_key as string).startsWith("pro_")) {
    if (!isPro) return reply({ message: "Seu acesso Pro não está mais ativo." }, 403);
    return reply({ ok: true, pro: true });
  }
  if (payload.action === "reserve") {
    const { data, error } = await admin.rpc("brother_matrizes_reserve_import", {
      p_user_id: user.id, p_operation: payload.operation,
    });
    if (error) return reply({ message: "Não foi possível reservar a importação." }, 503);
    if (data?.allowed !== true) return reply({ message:
      "Limite mensal do Gratuito atingido: 3 fontes ou 5 matrizes. Aguarde o próximo mês ou assine Pro.",
      remaining: 0 }, 409);
    return reply(data);
  }

  const { data, error } = await admin.rpc("brother_matrizes_finalize_import", {
    p_user_id: user.id, p_operation: payload.operation,
    p_request_key: payload.request_key, p_success: payload.success,
  });
  if (error) return reply({ message: "Não foi possível registrar a importação. Confira sua conexão." }, 503);
  return reply({ ok: true, counted: data === true });
});
