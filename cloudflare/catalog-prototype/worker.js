/**
 * Brother Matrizes: read-only catalog relay for Cloudflare Workers + D1.
 * Proof of concept only: no purchases, licenses or quota grants here.
 * WooCommerce on WordPress remains the catalog authority.
 */
const ENCODER = new TextEncoder();
const MAX_BODY_BYTES = 64 * 1024;
const MAX_CLOCK_SKEW_SECONDS = 300;
const MAX_CATALOG_AGE_SECONDS = 600;

function json(payload, status = 200) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store',
      'x-content-type-options': 'nosniff',
      'content-security-policy': "default-src 'none'",
    },
  });
}

function hexToBytes(value) {
  if (!/^[0-9a-f]{64}$/i.test(value)) return null;
  const out = new Uint8Array(32);
  for (let i = 0; i < out.length; i++) out[i] = parseInt(value.slice(i * 2, i * 2 + 2), 16);
  return out;
}

async function signedByWordPress(request, secret, body, now) {
  const timestamp = request.headers.get('x-bm-timestamp');
  const signature = request.headers.get('x-bm-signature') || '';
  if (!/^\d{10}$/.test(timestamp || '') || !signature.startsWith('sha256=')) return false;
  const timestampNumber = Number(timestamp);
  if (Math.abs(now - timestampNumber) > MAX_CLOCK_SKEW_SECONDS) return false;
  if (typeof secret !== 'string' || ENCODER.encode(secret).length < 32) return false;
  const sig = hexToBytes(signature.slice(7));
  if (!sig) return false;
  const key = await crypto.subtle.importKey(
    'raw', ENCODER.encode(secret), {name: 'HMAC', hash: 'SHA-256'}, false, ['verify']
  );
  return crypto.subtle.verify('HMAC', key, sig, ENCODER.encode(`${timestamp}.${body}`));
}

function normalizePayload(body) {
  const input = JSON.parse(body);
  if (!Number.isSafeInteger(input.revision) || input.revision < 1) {
    throw new Error('revision must be positive integer');
  }
  if (!Array.isArray(input.plans) || input.plans.length > 10) {
    throw new Error('plans must be an array with at most 10 entries');
  }
  const seen = new Set();
  const plans = input.plans.map(plan => {
    if (!plan || typeof plan !== 'object' || Array.isArray(plan)) throw new Error('invalid plan');
    const {code, name, price_cents: price, currency, active} = plan;
    if (typeof code !== 'string' || !/^[a-z][a-z0-9_]{1,63}$/.test(code) || seen.has(code)) {
      throw new Error('invalid or duplicate plan code');
    }
    seen.add(code);
    if (typeof name !== 'string' || !name.trim() || name.length > 128) throw new Error('invalid plan name');
    if (price !== null && (!Number.isSafeInteger(price) || price < 0 || price > 100000000)) {
      throw new Error('invalid price_cents');
    }
    if (currency !== 'BRL' || typeof active !== 'boolean') throw new Error('invalid plan currency or status');
    // Prevent promo publication without an actual mapped WooCommerce price.
    if (active && code !== 'free' && price === null) throw new Error('active plan requires price');
    return {code, name: name.trim(), price_cents: price, currency, active};
  });
  return {revision: input.revision, plans};
}

export default {
  async fetch(request, env) {
    const pathname = new URL(request.url).pathname;
    const method = request.method.toUpperCase();
    if (pathname === '/health' && method === 'GET') {
      return json({service: 'brother-matrizes-catalog-relay', status: 'prototype', ready_for_sales: false});
    }
    if (pathname === '/internal/catalog-sync') {
      if (method !== 'POST') return json({code: 'method_not_allowed'}, 405);
      if (!env.DB || !env.CATALOG_SYNC_SECRET) return json({code: 'not_configured'}, 503);
      if (Number(request.headers.get('content-length') || 0) > MAX_BODY_BYTES) {
        return json({code: 'payload_too_large'}, 413);
      }
      const body = await request.text();
      if (ENCODER.encode(body).length > MAX_BODY_BYTES) return json({code: 'payload_too_large'}, 413);
      const now = Math.floor(Date.now() / 1000);
      if (!await signedByWordPress(request, env.CATALOG_SYNC_SECRET, body, now)) {
        return json({code: 'unauthorized'}, 401);
      }
      let catalog;
      try { catalog = normalizePayload(body); }
      catch { return json({code: 'invalid_catalog'}, 422); }
      try {
        const value = JSON.stringify({plans: catalog.plans});
        const result = await env.DB.prepare(`INSERT INTO catalog_state(id, revision, payload, synced_at)
          VALUES (1, ?, ?, ?)
          ON CONFLICT(id) DO UPDATE SET revision=excluded.revision,
          payload=excluded.payload, synced_at=excluded.synced_at
          WHERE excluded.revision > catalog_state.revision`)
          .bind(catalog.revision, value, now).run();
        if ((result.meta?.changes || 0) !== 1) return json({code: 'stale_revision'}, 409);
        return json({ok: true, revision: catalog.revision});
      } catch { return json({code: 'storage_unavailable'}, 503); }
    }
    if (pathname === '/wp-json/brother-matrizes/v1/plans') {
      if (method !== 'GET') return json({code: 'method_not_allowed'}, 405);
      if (!env.DB) return json({code: 'not_configured'}, 503);
      try {
        const row = await env.DB.prepare('SELECT payload, synced_at FROM catalog_state WHERE id = 1').first();
        if (!row || (Date.now() / 1000 - row.synced_at) > MAX_CATALOG_AGE_SECONDS) {
          return json({code: 'catalog_not_synchronized'}, 503);
        }
        return json(JSON.parse(row.payload));
      } catch { return json({code: 'storage_unavailable'}, 503); }
    }
    // The commercial endpoints are intentionally disabled until full WP/Efi
    // synchronization and authentication are tested end-to-end.
    if (pathname.startsWith('/wp-json/brother-matrizes/v1/')) {
      return json({code: 'commercial_api_not_enabled', message: 'Licensing validation is not yet available.'}, 503);
    }
    return json({code: 'not_found'}, 404);
  },
};
