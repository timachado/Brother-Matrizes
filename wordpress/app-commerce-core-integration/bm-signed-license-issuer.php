<?php
/**
 * Brother Matrizes -- offline lease signing engine (staging only).
 * NO routes, checkout hooks, admin forms, or entitlement lookups.
 * A future authenticated controller must verify Google identity, WooCommerce
 * paid entitlement, device proof of possession and revocation before calling.
 * This class alone cannot grant Pro access to an APK.
 */
if (!defined('ABSPATH')) { exit; }

final class TIAC_Brother_Signed_Issuer {
    private const ISSUER = 'ti-machado-app-commerce';
    private const AUDIENCE = 'brother-matrizes-android';
    private const MAX_LEASE_SECONDS = 604800; // 7 days, also for lifetime.
    private const PLANS = array('pro_monthly', 'pro_yearly', 'pro_lifetime', 'pro_lifetime_launch');

    /**
     * Only accept a server-derived, already authenticated and authorized context.
     * The array MUST NEVER be built from unsigned GET/POST input or a Woo order
     * confirmation URL. See docs/SIGNED_WORDPRESS_LICENSE_PROTOCOL.md.
     *
     * @param array $verified Server-authenticated: sub, device_id, license_id,
     *                        plan_code, status, order_id, wp_user_id, app_id,
     *                        entitlement_expires_at (Unix time/null for lifetime).
     * @param string $private_pem A P-256 private key loaded by the server from a
     *                            secure, non-public storage location; never APK.
     * @param int|null $now Trusted server epoch time, override for local QA only.
     * @param int $ttl Desired short-lived lease, 60s..7 days.
     * @return string The 'bm1' compact signed lease, not a Woo purchase receipt.
     * @throws InvalidArgumentException When verified facts are not complete.
     * @throws RuntimeException On cryptographic failures.
     */
    public static function issue_verified(array $verified, string $private_pem,
        ?int $now = null, int $ttl = 3600): string {
        $issued = $now ?? time();
        if ($issued < 1700000000 || $ttl < 60 || $ttl > self::MAX_LEASE_SECONDS) {
            throw new InvalidArgumentException('Invalid issuance window.');
        }
        $sub = $verified['sub'] ?? null;
        $device = $verified['device_id'] ?? null;
        $license = $verified['license_id'] ?? null;
        $plan = $verified['plan_code'] ?? null;
        $status = $verified['status'] ?? null;
        $order_id = $verified['order_id'] ?? null;
        $customer = $verified['wp_user_id'] ?? null;
        $app = $verified['app_id'] ?? null;
        if (!is_string($sub) || !preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/iD', $sub) ||
            !is_string($device) || !preg_match('/^[a-f0-9]{32}$/D', $device) ||
            !is_string($license) || !preg_match('/^[A-Za-z0-9_-]{8,128}$/D', $license) ||
            !is_string($plan) || !in_array($plan, self::PLANS, true) ||
            $status !== 'active' || !is_int($order_id) || $order_id < 1 ||
            !is_int($customer) || $customer < 1 || !is_int($app) || $app < 1) {
            throw new InvalidArgumentException('Missing verified entitlement, user, order, or device binding.');
        }
        $entitlement_exp = $verified['entitlement_expires_at'] ?? null;
        if ($entitlement_exp !== null && (!is_int($entitlement_exp) || $entitlement_exp <= $issued)) {
            throw new InvalidArgumentException('Commercial entitlement expired or invalid.');
        }
        // Recurring plans always need a verified commercial expiration.
        if (in_array($plan, array('pro_monthly', 'pro_yearly'), true) && $entitlement_exp === null) {
            throw new InvalidArgumentException('Recurring plan has no verified expiry.');
        }
        $expires = $issued + $ttl;
        if ($entitlement_exp !== null) $expires = min($expires, $entitlement_exp);
        if ($expires <= $issued) throw new InvalidArgumentException('Lease expired.');

        $key = openssl_pkey_get_private($private_pem);
        if (!$key) throw new RuntimeException('License signing key unavailable.');
        $details = openssl_pkey_get_details($key);
        if (!is_array($details) || ($details['type'] ?? null) !== OPENSSL_KEYTYPE_EC ||
            ($details['ec']['curve_name'] ?? null) !== 'prime256v1') {
            throw new RuntimeException('License signing requires an EC P-256 key.');
        }

        $claims = array(
            'v' => 1, 'iss' => self::ISSUER, 'aud' => self::AUDIENCE,
            'sub' => strtolower($sub), 'dev' => $device, 'lic' => $license,
            'plan' => $plan, 'status' => 'active',
            'iat' => $issued, 'nbf' => $issued, 'exp' => $expires,
        );
        $json = json_encode($claims, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
        $data = self::b64url($json);
        $to_sign = 'bm1.' . $data;
        $signature = '';
        if (openssl_sign($to_sign, $signature, $key, OPENSSL_ALGO_SHA256) !== true) {
            throw new RuntimeException('Failed to sign license.');
        }
        $token = $to_sign . '.' . self::b64url($signature);
        if (strlen($token) > 4096) throw new RuntimeException('Signed license exceeded limit.');
        return $token;
    }

    private static function b64url(string $data): string {
        return rtrim(strtr(base64_encode($data), '+/', '-_'), '=');
    }
}
