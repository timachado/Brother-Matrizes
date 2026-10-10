import { createClient } from "npm:@supabase/supabase-js@2.116.0";

// Brother Matrizes WooCommerce -> Supabase receipt mirror.
// Distinct from Bíblia EBD. Never directly charges customers or grants Pro.
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" }
  });
const clean = (v: unknown, max: number): string => typeof v === "string" ? v.trim().slice(0, max) : "";
const b64Sha = async (value: string) => {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), n => n.toString(16).padStart(2, "0")).join("");
};
const VALID_PLANS = new Set(["pro_monthly","pro_yearly","pro_lifetime","pro_lifetime_launch"]);
const VALID_STATUSES = new Set(["active","pending","on_hold","cancelled","refunded","expired","failed"]);
const UUID_RE = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i;

Deno.serve(async req => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  // The secret is installed only in WordPress wp-config.php, never in the APK.
  const credential = clean(req.headers.get("x-brother-site-token"), 240);
  if (credential.length < 40) return json({ error: "unauthorized" }, 401);
  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !key) return json({ error: "service_unavailable" }, 503);
  let input: Record<string, unknown>;
  try {
    if (Number(req.headers.get("content-length") || 0) > 10_000) return json({ error: "too_large" }, 413);
    const raw = await req.text();
    if (raw.length > 10_000) return json({ error: "too_large" }, 413);
    input = JSON.parse(raw);
    if (!input || Array.isArray(input) || typeof input !== "object") throw Error("invalid");
  } catch { return json({ error: "invalid_json" }, 400); }

  const admin = createClient(url, key, { auth: { persistSession: false, autoRefreshToken: false } });
  const tokenHash = await b64Sha(credential);
  const { data: site, error: siteError } = await admin.from("brother_matrizes_commerce_sites")
    .select("id,active,site_url").eq("token_hash", tokenHash).eq("active", true).maybeSingle();
  if (siteError) return json({ error: "site_lookup_unavailable" }, 503);
  if (!site) return json({ error: "unauthorized" }, 401);
  // Admin-only connection probe via the same high-entropy site credential.
  // Does not create a purchase, subscription or any billing event.
  if (clean(input.action, 40) === "ping")
    return json({ ok: true, service: "brother-commerce", siteConfigured: true });

  if (clean(input.action, 40) !== "order_event" || clean(input.appSlug, 80) !== "brother-matrizes")
    return json({ error: "invalid_app" }, 400);

  const eventKey = clean(input.eventKey, 220);
  const orderRef = clean(input.orderRef, 80);
  const email = clean(input.email, 320).toLowerCase();
  const plan = clean(input.planCode, 70);
  const status = clean(input.status, 30);
  const provider = clean(input.provider, 49) || "efi";
  const currency = clean(input.currency, 3) || "BRL";
  const eventTime = new Date(clean(input.occurredAt, 64));
  const endRaw = input.currentPeriodEnd;
  const endTime = endRaw ? new Date(clean(endRaw, 64)) : null;
  const amount = input.amountCents === null ? null : Number(input.amountCents);
  const now = Date.now();

  if (!/^[A-Za-z0-9:_-]{12,220}$/.test(eventKey) ||
    !/^[0-9]{1,16}$/.test(orderRef) ||
    !email.includes("@") || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ||
    !VALID_PLANS.has(plan) || !VALID_STATUSES.has(status) ||
    !/^[a-z0-9_.-]{1,49}$/.test(provider) || currency !== "BRL" ||
    !Number.isFinite(eventTime.getTime()) ||
    Math.abs(eventTime.getTime() - now) > 30 * 24 * 3600_000 ||
    (endTime && !Number.isFinite(endTime.getTime())) ||
    !Number.isSafeInteger(amount) || amount < 0 || amount > 100_000_000)
    return json({ error: "invalid_event" }, 400);

  // Recurring Pro always expires. No purchase receipt can create an endless subscription.
  const recurring = plan === "pro_monthly" || plan === "pro_yearly";
  if (status === "active" && recurring &&
    (!endTime || endTime.getTime() <= now ||
     endTime.getTime() > now + 370 * 24 * 3600_000))
    return json({ error: "missing_recurring_expiration" }, 422);
  if (status === "active" && !recurring && endTime !== null)
    return json({ error: "invalid_lifetime_expiration" }, 422);

  const existing = await admin.from("brother_matrizes_billing_events")
    .select("event_key,order_ref,status,plan_code").eq("event_key", eventKey).maybeSingle();
  if (existing.error) return json({ error: "event_lookup_unavailable" }, 503);
  if (existing.data) {
    if (existing.data.order_ref !== orderRef || existing.data.status !== status ||
        existing.data.plan_code !== plan) return json({ error: "event_key_conflict" }, 409);
    return json({ ok: true, duplicate: true, eventKey });
  }

  // Older webhook deliveries must not revert a newer order state.
  const latest = await admin.from("brother_matrizes_billing_events")
    .select("occurred_at").eq("site_id", site.id).eq("order_ref", orderRef)
    .order("occurred_at", { ascending: false }).limit(1);
  if (latest.error) return json({ error: "order_lookup_unavailable" }, 503);
  if (latest.data?.length && new Date(latest.data[0].occurred_at).getTime() > eventTime.getTime())
    return json({ ok: true, stale: true, eventKey });

  const emailHash = await b64Sha(email);
  const event = {
    event_key: eventKey, site_id: site.id, order_ref: orderRef,
    email_hash: emailHash, app_slug: "brother-matrizes", plan_code: plan,
    status, provider, amount_cents: amount, currency,
    current_period_end: recurring ? endTime?.toISOString() || null : null,
    occurred_at: eventTime.toISOString()
  };
  const result = await admin.from("brother_matrizes_billing_events").insert(event);
  if (result.error) return json({ error: "event_persist_failed" }, 503);
  return json({ ok: true, eventKey, status, planCode: plan, orderRef }, 200);
});