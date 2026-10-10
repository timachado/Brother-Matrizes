import { createClient } from "npm:@supabase/supabase-js@2.116.0";

// Brother Matrizes: auth + WooCommerce-reconciled entitlement.
// Billing evidence is mirrored by brother-commerce; no end user can write it.
const cors = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "authorization,apikey,content-type",
  "access-control-allow-methods": "GET,OPTIONS",
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
};
const json = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status, headers: cors });
const sha256 = async (value: string) => {
  const buffer = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(buffer), c => c.toString(16).padStart(2, "0")).join("");
};
const PLANS = new Set(["pro_monthly", "pro_yearly", "pro_lifetime", "pro_lifetime_launch"]);
const ranks: Record<string, number> = {
  pro_monthly: 1, pro_yearly: 2, pro_lifetime: 3, pro_lifetime_launch: 3
};
const catalog = [
  { code: "free", name: "Gratuito", billing_type: "free", is_paid: false, is_lifetime: false,
    is_promotional: false, price_cents: 0, active: true, currency: "BRL", display_order: 0 },
  { code: "pro_monthly", name: "Pro Mensal", billing_type: "monthly", is_paid: true,
    is_lifetime: false, is_promotional: false, price_cents: null, active: false, currency: "BRL", display_order: 1 },
  { code: "pro_yearly", name: "Pro Anual", billing_type: "yearly", is_paid: true,
    is_lifetime: false, is_promotional: false, price_cents: null, active: false, currency: "BRL", display_order: 2 },
  { code: "pro_lifetime", name: "Pro Vitalício", billing_type: "one_time", is_paid: true,
    is_lifetime: true, is_promotional: false, price_cents: null, active: false, currency: "BRL", display_order: 3 },
  { code: "pro_lifetime_launch", name: "Vitalício Lançamento", billing_type: "one_time", is_paid: true,
    is_lifetime: true, is_promotional: true, price_cents: null, active: false, currency: "BRL", display_order: 4 }
];

Deno.serve(async req => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
  if (req.method !== "GET") return json({ message: "Método não permitido." }, 405);
  const authorization = req.headers.get("authorization") ?? "";
  const token = authorization.toLowerCase().startsWith("bearer ") ?
    authorization.slice(7).trim() : "";
  if (token.length < 30 || token.length > 12_000)
    return json({ authenticated: false, message: "Entre com Google no Brother Matrizes." }, 401);
  const url = Deno.env.get("SUPABASE_URL") || "";
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
  if (!url || !service) return json({ message: "Serviço indisponível." }, 503);

  const admin = createClient(url, service, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data: userData, error: authError } = await admin.auth.getUser(token);
  const user = userData?.user;
  if (authError || !user) return json({ authenticated: false, message: "Sessão inválida ou expirada." }, 401);

  const email = (user.email || "").trim().toLowerCase();
  const verified = Boolean(user.email_confirmed_at && email && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email));
  const account = {
    authenticated: true,
    user: { id: user.id, email: user.email || "" },
    planCode: "free",
    status: "active",
    source: "no_paid_woocommerce_order",
    pro: false,
    currentPeriodEnd: null as string | null,
    purchasedAt: null as string | null,
    purchasePriceCents: null as number | null,
    provider: null as string | null,
    commercialConfigured: true,
    availablePlans: catalog,
    manageUrl: "https://timachado.ifree.page/minha-conta/meus-aplicativos/",
    quotaRemaining: {} as Record<string, number>
  };
  if (!verified) return json({ ...account, source: "email_unverified", needsVerifiedEmail: true });

  const emailHash = await sha256(email);
  const { data: events, error: eventsError } = await admin.from("brother_matrizes_billing_events")
    .select("site_id,order_ref,event_key,plan_code,status,provider,amount_cents,occurred_at,received_at,current_period_end")
    .eq("email_hash", emailHash)
    .order("occurred_at", { ascending: false })
    .order("received_at", { ascending: false })
    .limit(500);
  if (eventsError) return json({ message: "Não foi possível conferir seus pagamentos.", commercialConfigured: false }, 503);

  // Source of truth is the latest recorded event for each individual Woo order.
  // A refunded/failed order must not be revived by an older paid webhook.
  const seenOrders = new Set<string>();
  const eligible: typeof events = [];
  const now = Date.now();
  for (const event of events || []) {
    const key = event.site_id + ":" + event.order_ref;
    if (seenOrders.has(key)) continue;
    seenOrders.add(key);
    if (event.status !== "active" || !PLANS.has(event.plan_code)) continue;
    const lifetime = event.plan_code === "pro_lifetime" || event.plan_code === "pro_lifetime_launch";
    const expiry = event.current_period_end ? Date.parse(event.current_period_end) : 0;
    if (!lifetime && (!expiry || expiry <= now)) continue;
    if (lifetime && event.current_period_end) continue;
    eligible.push(event);
  }
  if (eligible.length === 0) return json(account);

  eligible.sort((a,b) => (ranks[b.plan_code] - ranks[a.plan_code]) ||
    (Date.parse(b.occurred_at) - Date.parse(a.occurred_at)));
  const best = eligible[0];
  return json({
    ...account, planCode: best.plan_code, status: "active", source: "woocommerce_efi",
    pro: true, currentPeriodEnd: best.current_period_end || null,
    purchasedAt: best.occurred_at, purchasePriceCents: best.amount_cents,
    provider: best.provider, billingOrderRef: best.order_ref
  });
});