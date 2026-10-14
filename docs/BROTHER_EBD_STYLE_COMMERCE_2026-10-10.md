# Brother Matrizes — EBD-style subscription bridge for WordPress + Efí + Supabase

2026-10-10. Owner explicitly authorized adopting the EBD model **only for Brother Matrizes**.

## Commercial responsibilities
- **WooCommerce + Efí Bank on timachado.ifree.page** continue as the ONLY sources of payment/renewal/refund and customer order records.
- **Supabase Auth** authenticates Google accounts; new **Brother-specific** Edge Functions mirror signed-site commerce events to expose entitlement to the APK, like the existing Bible EBD architecture.
- **No commerce migrations or edits to Bíblia EBD.** Existing `biblia_*` tables and functions remain untouched.
- Cloudflare is not used.

## Implemented in Supabase
- `brother-commerce`: POST-only event receiver protected by a unique 48-byte WordPress site token (only SHA-256 stored in `brother_matrizes_commerce_sites`, never APK or code).
- `brother-entitlement`: GET-only API that checks a real Supabase user access token using auth.getUser and binds the **confirmed** Google email hash to the WooCommerce buyer email hash.
- `brother_matrizes_commerce_sites` and `brother_matrizes_billing_events`: append-only Brother commerce mirror, RLS enabled with no client policies, anon/authenticated privileges revoked, service_role only.
- Events are keyed by Woo order, site and event ID; refund, failed, cancelled and expired orders do not yield Pro; recurring access requires period expiration.
- Never grant based on a request from the APK, URL of a checkout, user_metadata or a self-supplied subscription.

## Android changes
- Account lookups through `BrotherEntitlementClient`, not InfinityFree REST.
- Google sign-in remains the same. When the buyer returns to the app, AccountHostScreen refreshes entitlement via ON_RESUME or 'Verificar licença'.
- Paid Pro access is derived from `brother-entitlement` responses; future expiry checked locally.
- Beta local import limits remain for free users, while recently verified paid Pro bypasses only those free import reservations.
- The WordPress browser account is still `/minha-conta/meus-aplicativos/`; site and Google login sessions are separate.
- No private Efí, service_role or site tokens in the APK.

## App Commerce Core 2.6.12-rc1 STAGING
The package in this conversation is **not automatically installed**. It preserves all Core integrations; it only adds
`includes/brother-matrizes/class-tiac-brother-commerce-sync.php` guarded by:
```php
define('TIAC_BROTHER_COMMERCE_SITE_TOKEN', '<PRIVATE_TOKEN_FROM_SEPARATE_LOCAL_FILE>');
define('TIAC_BROTHER_COMMERCE_SYNC_ENABLED', true);
```
Never share the token in chat, repository or screenshots. A private setup file is generated outside the ZIP, and only the token's hash is registered in Supabase.
The WordPress menu `WooCommerce > Brother Matrizes Sync` provides:
- credential-authenticated connection test without charging/creating events;
- administrator re-send of an existing Woo order to retry sync (no new payment).

The event sync hooks use priority 90 **after** the existing Core license issuance, check paid orders and issued license ownership, and reject products outside app #307. Product #308 maps to `pro_lifetime_launch`.
For recurring licenses, a verified paid timestamp and bounded renewal period are mandatory; test actual Efí renewal callbacks before claiming automatic renewals.
Refunds/cancellations also prevent replay and revoke matching Brother WP licenses without modifying Bíblia EBD customers.

### Installation checklist
1. Backup WordPress/files/database first. Save a copy of the live App Commerce Core plugin ZIP or source for rollback.
2. Upload 2.6.12-rc1 over the existing Core plugin **without deleting the existing plugin or database tables**.
3. Confirm `wp-config.php` still has `BM_CATALOG_SYNC_ENABLED = false`. Add the exact two Brother-only constants from the private setup file (one copy of each).
4. Open `WooCommerce > Brother Matrizes Sync`, click **Verificar conexão com Supabase**. Must say `Conexão autenticada OK`. If blocked 401/403/503 or transport error, **do not publish paid products**; inspect outgoing HTTPS hosting limitations.
5. Test sandbox Efí payment and a real Woo order, linked to app #307 and product #308. In Woo order note the order number, status and verified license. Admin resync that order. Supabase `brother_matrizes_billing_events` should receive exactly a verified payment event.
6. Confirm with an actual Google session having the exact **same verified email as the Woo order billing email**. Open Brother Matrizes > Minha Conta > Verificar licença. Only a paid, active and non-expired plan unlocks Pro.
7. Test cancelled/refunded plan, failed payment, no billing email match, renewal and expiration. Ensure they **remove** the right to Pro.
8. Only after end-to-end tests, publish allowed Woo products and the Brother app listing on the site. Monthly/yearly auto-recurring requires a separate observed Efí renewal test.
9. Sign production APK with preapproved Brother signing certificate and test upgrade without uninstalling so projects/fonts are preserved.

## Current go/no-go
- Edge Functions and Supabase schema: deployed.
- WordPress integration: staging package generated, unit-tested, **not installed in production**.
- Real paid event and checkout -> Pro: **not yet tested**.
- Paid plans in WordPress: still inactive/draft at last public check.
- Existing EBD subscriptions: untouched.

No claim of fully working paid subscriptions until items 4-7 pass.
