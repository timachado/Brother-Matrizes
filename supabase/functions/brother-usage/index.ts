import { createClient } from "npm:@supabase/supabase-js@2.116.0";

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
  "access-control-allow-headers": "authorization,apikey,content-type,x-brother-app-version-code",
  "access-control-allow-methods": "POST,OPTIONS",
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
};
const reply = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers });
const imports = new Set(["import_font", "import_matrix"]);
const dailyActions = new Set([
  "open_matrix", "rar_receive", "telegram_search", "telegram_import",
  "dafont_search", "dafont_import",
]);
const operations = new Set([...imports, ...dailyActions]);
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
    const validKeys = parsed.action === "status" ? ["action"] :
      parsed.action === "reserve" ? ["action", "operation"] :
      ["action", "operation", "request_key", "success", "fingerprint"];
    const mandatoryKeys = parsed.action === "status" ? ["action"] :
      parsed.action === "reserve" ? ["action", "operation"] :
      ["action", "operation", "request_key", "success"];
    if (!["reserve", "finalize", "status"].includes(parsed.action) ||
      Object.keys(parsed).some(k => !validKeys.includes(k)) ||
      !mandatoryKeys.every(k => Object.hasOwn(parsed, k)) ||
      (parsed.action !== "status" && !operations.has(parsed.operation)) ||
      (parsed.action === "finalize" &&
        (typeof parsed.success !== "boolean" ||
          typeof parsed.request_key !== "string" || !requestKey.test(parsed.request_key) ||
          (parsed.fingerprint !== undefined &&
            (typeof parsed.fingerprint !== "string" ||
            !/^[0-9a-f]{64}$/.test(parsed.fingerprint))))))
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
  // Provider is read from server-owned auth.app_metadata, never user_metadata.
  const providers: unknown = user.app_metadata?.providers;
  const isGoogle = user.app_metadata?.provider === "google" ||
    (Array.isArray(providers) && providers.includes("google"));
  if (!isGoogle)
    return reply({ message: "Entre com uma conta Google para registrar suas cotas de importação." }, 403);

  // Staged version gate: enable BROTHER_MINIMUM_CLIENT_VERSION_CODE in
  // Supabase ONLY after the required signed APK is publicly available.
  // Older authenticated API clients without this header are rejected.
  // This cannot remotely change APKs that never call our API, and the
  // header is client-reported (not cryptographic attestation).
  const minimum = Number(Deno.env.get("BROTHER_MINIMUM_CLIENT_VERSION_CODE") || "0");
  if (Number.isSafeInteger(minimum) && minimum > 0) {
    const supplied = req.headers.get("x-brother-app-version-code") || "";
    const installed = /^\\d{1,9}$/.test(supplied) ? Number(supplied) : 0;
    if (!Number.isSafeInteger(installed) || installed < minimum) {
      return reply({
        message: "Atualização obrigatória do Brother Matrizes. Atualize pelo site oficial para continuar importando.",
        update_required: true,
        minimum_version_code: minimum,
        website: "https://timachado.ifree.page/aplicativos/brother-matrizes/"
      }, 426);
    }
  }


  // Reuse the current entitlement authority: never duplicate or fabricate
  // WooCommerce/Efí payment checks inside this non-financial service.
  let isPro = false;
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
    isPro = body.pro === true && body.status === "active" &&
      ["trial", "pro_monthly", "pro_yearly", "pro_lifetime", "pro_lifetime_launch"]
        .includes(body.planCode);
  } catch {
    return reply({ message: "Não foi possível verificar sua licença. Confira a internet e tente novamente." }, 503);
  }

  if (payload.action === "status") {
    const { data, error } = await admin.rpc("brother_matrizes_action_status", {
      p_user_id: user.id,
    });
    if (error || !data) return reply({ message: "Não foi possível consultar o consumo." }, 503);
    return reply({ ...data, pro: isPro, plan: isPro ? "pro" : "free" });
  }

  if (payload.action === "reserve" && isPro) {
    return reply({ allowed: true, request_key: "pro_" + crypto.randomUUID(),
      operation: payload.operation, remaining: null, pro: true });
  }
  if (payload.action === "finalize" && (payload.request_key as string).startsWith("pro_")) {
    if (!isPro) return reply({ message: "Seu acesso Pro não está mais ativo." }, 403);
    return reply({ ok: true, pro: true });
  }
  if (dailyActions.has(payload.operation as string)) {
    const operation = payload.operation as string;
    if (payload.action === "reserve") {
      const { data, error } = await admin.rpc("brother_matrizes_reserve_action", {
        p_user_id: user.id, p_operation: operation,
      });
      if (error) return reply({ message: "Não foi possível consultar a cota diária." }, 503);
      if (data?.allowed !== true)
        return reply({ message: "Cota diária atingida. Ela renova à meia-noite de Brasília. Consulte os planos PRO.",
          ...data }, 409);
      return reply(data);
    }
    const { data, error } = await admin.rpc("brother_matrizes_finalize_action", {
      p_user_id: user.id, p_operation: operation,
      p_request_key: payload.request_key, p_success: payload.success,
      p_fingerprint: payload.fingerprint || null,
    });
    if (error) return reply({ message: "Não foi possível confirmar a operação." }, 503);
    return reply({ ok: true, counted: data === true });
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
