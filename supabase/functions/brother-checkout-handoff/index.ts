// Brother Matrizes: optional proof-of-identity bridge ONLY. No payment, license,
// order, or price data is stored or authorized here.
// Deploy only after the matching WordPress plugin and shared key are configured.
// The signed-in app sends its Supabase bearer token in an HTTPS HEADER, never URL.
import { createClient } from "npm:@supabase/supabase-js@2.116.0";

const ORIGIN = "https://timachado.ifree.page";
const AUDIENCE = "ti-machado-brother-checkout-v1";
const VALID_PLANS = new Set([
  "pro_monthly", "pro_yearly", "pro_lifetime", "pro_lifetime_launch",
]);
const TTL_SECONDS = 45;

function response(data: object, status: number): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store, max-age=0",
      "Pragma": "no-cache",
      "Referrer-Policy": "no-referrer",
    },
  });
}
function fromBase64(input: string): Uint8Array {
  const raw = atob(input);
  return Uint8Array.from(raw, (ch) => ch.charCodeAt(0));
}
function base64url(bytes: Uint8Array): string {
  let raw = "";
  bytes.forEach((b) => (raw += String.fromCharCode(b)));
  return btoa(raw).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}
async function encryptTicket(payload: object, configuredKey: string): Promise<string> {
  const secretBytes = fromBase64(configuredKey);
  if (secretBytes.length !== 32) throw Error("invalid server key length");
  const key = await crypto.subtle.importKey(
    "raw", secretBytes, { name: "AES-GCM" }, false, ["encrypt"],
  );
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const plain = new TextEncoder().encode(JSON.stringify(payload));
  const encrypted = new Uint8Array(await crypto.subtle.encrypt(
    { name: "AES-GCM", iv }, key, plain,
  ));
  const ticket = new Uint8Array(iv.length + encrypted.length);
  ticket.set(iv);
  ticket.set(encrypted, iv.length);
  return base64url(ticket);
}

Deno.serve(async (req: Request) => {
  // Non-sensitive diagnostics: confirms that the private handoff key exists
  // and is correctly encoded. Does not reveal the key or grant a session.
  if (req.method === "GET" &&
      new URL(req.url).pathname.endsWith("/health")) {
    let configured = false;
    try {
      const value = Deno.env.get("BROTHER_CHECKOUT_HANDOFF_KEY") || "";
      configured = value.length > 0 && fromBase64(value).length === 32;
    } catch {
      configured = false;
    }
    return response({ service: "brother-checkout-handoff", ready: configured },
      configured ? 200 : 503);
  }
  if (req.method !== "POST") return response({ error: "method_not_allowed" }, 405);
  const authorization = req.headers.get("Authorization") || "";
  if (!/^Bearer [^\s]+$/.test(authorization)) {
    return response({ error: "login_required" }, 401);
  }
  if (Number(req.headers.get("content-length") || 0) > 1024) {
    return response({ error: "invalid_request" }, 413);
  }
  try {
    const { plan_code: plan } = await req.json();
    if (typeof plan !== "string" || !VALID_PLANS.has(plan)) {
      return response({ error: "invalid_plan" }, 400);
    }
    const url = Deno.env.get("SUPABASE_URL") || "";
    // Projects created in 2026 may expose only named publishable keys;
    // never use a service_role or secret key for user authentication.
    const namedKeys = Deno.env.get("SUPABASE_PUBLISHABLE_KEYS");
    let defaultPublishable = "";
    if (namedKeys) {
      try {
        const values = JSON.parse(namedKeys);
        defaultPublishable = typeof values?.default === "string" ? values.default : "";
      } catch {
        defaultPublishable = "";
      }
    }
    const publicKey = Deno.env.get("SUPABASE_ANON_KEY") ||
      Deno.env.get("SUPABASE_PUBLISHABLE_KEY") || defaultPublishable;
    const handoffKey = Deno.env.get("BROTHER_CHECKOUT_HANDOFF_KEY") || "";
    if (!url || !publicKey || !handoffKey) {
      return response({ error: "checkout_unavailable" }, 503);
    }
    const client = createClient(url, publicKey, {
      auth: { autoRefreshToken: false, persistSession: false },
    });
    const token = authorization.slice(7);
    // Auth server confirmation, NOT an unverified client-side JWT decode.
    const { data, error } = await client.auth.getUser(token);
    if (error || !data?.user) return response({ error: "login_expired" }, 401);
    const user = data.user;
    const providers = user.app_metadata?.providers || [];
    if (!Array.isArray(providers) || !providers.includes("google")
        || !user.email || !user.email_confirmed_at) {
      return response({ error: "google_login_required" }, 403);
    }
    const now = Math.floor(Date.now() / 1000);
    const opaque = await encryptTicket({
      v: 1,
      iss: "brother-matrizes-supabase",
      aud: AUDIENCE,
      sub: user.id,
      email: user.email,
      email_verified: true,
      plan,
      iat: now,
      exp: now + TTL_SECONDS,
      jti: crypto.randomUUID(),
    }, handoffKey);
    // Authenticated identity encrypted (AES-256-GCM). No Google or Supabase
    // access / refresh token is sent to WordPress or exposed in this URL.
    return response({
      checkout_url: ORIGIN + "/minha-conta/?bm_checkout=" + opaque,
      expires_in_seconds: TTL_SECONDS,
    }, 200);
  } catch {
    return response({ error: "checkout_unavailable" }, 503);
  }
});
