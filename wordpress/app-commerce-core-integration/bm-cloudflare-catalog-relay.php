<?php
/**
 * Brother Matrizes — Cloudflare catalog relay (INTEGRATION SOURCE ONLY).
 *
 * Install only after reviewing the exact current App Commerce Core version.
 * The module does not touch orders, licensing, checkout, Efí or other apps.
 * Add to the existing Core via require_once; NEVER install as a second plugin.
 */
defined('ABSPATH') || exit;

if (!class_exists('BM_Cloudflare_Catalog_Relay', false)) {
final class BM_Cloudflare_Catalog_Relay {
    private const HOOK = 'bm_cloudflare_catalog_sync';
    private const REVISION_KEY = 'bm_cloudflare_catalog_revision';
    private const LOCK_KEY = 'bm_cloudflare_catalog_lock';
    private const STATUS_KEY = 'bm_cloudflare_catalog_sync_status';
    private const ENDPOINT = 'https://brother-matrizes-api.servicospremiummachadoti.workers.dev/internal/catalog-sync';
    private const CODES = array('free', 'pro_monthly', 'pro_yearly', 'pro_lifetime', 'pro_lifetime_launch');

    public static function enabled(): bool {
        return defined('BM_CATALOG_SYNC_SECRET')
            && is_string(BM_CATALOG_SYNC_SECRET)
            && strlen(BM_CATALOG_SYNC_SECRET) >= 32
            && defined('BM_CATALOG_SYNC_ENABLED')
            && BM_CATALOG_SYNC_ENABLED === true;
    }

    public static function boot(): void {
        if (!self::enabled()) {
            return; // Never send anything until the owner has set the secret.
        }
        add_filter('cron_schedules', array(__CLASS__, 'schedule'));
        add_action('init', array(__CLASS__, 'maybe_schedule'));
        add_action(self::HOOK, array(__CLASS__, 'sync'));
    }

    public static function schedule(array $schedules): array {
        $schedules['bm_cloudflare_3min'] = array(
            'interval' => 180,
            'display' => 'Brother Matrizes Cloudflare catalog every 3 minutes',
        );
        return $schedules;
    }

    public static function maybe_schedule(): void {
        if (!wp_next_scheduled(self::HOOK)) {
            wp_schedule_event(time() + 30, 'bm_cloudflare_3min', self::HOOK);
        }
    }

    /**
     * Read existing Brother-scoped REST data INSIDE WordPress, avoiding
     * outbound HTTP loops to the InfinityFree REST endpoint.
     */
    public static function catalog() {
        if (!function_exists('rest_do_request')) {
            return new WP_Error('rest_unavailable', 'WordPress REST server is not available');
        }
        $request = new WP_REST_Request('GET', '/brother-matrizes/v1/plans');
        $response = rest_do_request($request);
        if (is_wp_error($response)) {
            return $response;
        }
        if (!($response instanceof WP_REST_Response) || $response->get_status() !== 200) {
            return new WP_Error('plans_unavailable', 'Brother Matrizes plans endpoint is unavailable');
        }
        $data = $response->get_data();
        if (!is_array($data) || !isset($data['plans']) || !is_array($data['plans'])) {
            return new WP_Error('invalid_catalog', 'The Brother Matrizes catalog response is invalid');
        }

        $plans = array();
        $seen = array();
        foreach ($data['plans'] as $plan) {
            if (!is_array($plan)) {
                return new WP_Error('invalid_plan', 'Invalid Brother Matrizes plan');
            }
            $code = $plan['code'] ?? null;
            if (!is_string($code) || !in_array($code, self::CODES, true) || isset($seen[$code])) {
                return new WP_Error('invalid_plan_code', 'Unexpected or duplicate Brother Matrizes plan');
            }
            $seen[$code] = true;
            $name = $plan['name'] ?? null;
            if (!is_string($name) || trim($name) === '' || strlen($name) > 128) {
                return new WP_Error('invalid_plan_name', 'Missing Brother Matrizes plan name');
            }
            $active = $plan['active'] ?? null;
            if (!is_bool($active)) {
                return new WP_Error('invalid_plan_status', 'The plan status must be boolean');
            }
            $price = $plan['price_cents'] ?? null;
            if ($price !== null && (!is_int($price) || $price < 0 || $price > 100000000)) {
                return new WP_Error('invalid_plan_price', 'The WooCommerce price must be an integer in cents');
            }
            if ($active && $code !== 'free' && $price === null) {
                return new WP_Error('missing_plan_price', 'A paid plan lacks an authoritative price');
            }
            if (isset($plan['currency']) && $plan['currency'] !== 'BRL') {
                return new WP_Error('invalid_currency', 'Currency must be BRL');
            }
            $plans[] = array(
                'code' => $code,
                'name' => trim($name),
                'price_cents' => $price,
                'currency' => 'BRL',
                'active' => $active,
            );
        }
        if (count($plans) > 10) {
            return new WP_Error('too_many_plans', 'Unexpected plan catalog size');
        }
        return $plans;
    }

    private static function status(string $code, int $revision = 0): void {
        // No customer data, API token, signature or prices in diagnostics.
        update_option(self::STATUS_KEY, array(
            'code' => $code,
            'at' => time(),
            'revision' => $revision,
        ), false);
    }

    /**
     * Run only by WP-Cron or a privileged internal call (never public REST).
     * Saves revision before HTTP, so an uncertain outcome is never replayed.
     */
    public static function sync() {
        if (!self::enabled()) {
            return new WP_Error('relay_disabled', 'The optional relay is disabled');
        }
        $lock_time = (int) get_option(self::LOCK_KEY, 0);
        if ($lock_time > 0 && $lock_time > time() - 90) {
            return new WP_Error('relay_locked', 'Another sync is running');
        }
        if ($lock_time > 0) {
            delete_option(self::LOCK_KEY); // Discard abandoned lock after 90s.
        }
        // add_option uses a unique option name in WordPress/MySQL to lock.
        if (!add_option(self::LOCK_KEY, time(), '', false)) {
            return new WP_Error('relay_locked', 'Another sync is running');
        }
        try {
            $plans = self::catalog();
            if (is_wp_error($plans)) {
                self::status($plans->get_error_code());
                return $plans;
            }
            $last_revision = (int) get_option(self::REVISION_KEY, 0);
            $revision = max((int) floor(microtime(true) * 1000), $last_revision + 1);
            update_option(self::REVISION_KEY, $revision, false);
            $body = wp_json_encode(array('revision' => $revision, 'plans' => $plans));
            if (!is_string($body) || strlen($body) > 65536) {
                self::status('payload_too_large', $revision);
                return new WP_Error('payload_too_large', 'Catalog is too large');
            }
            $timestamp = (string) time();
            $signature = hash_hmac('sha256', $timestamp . '.' . $body, BM_CATALOG_SYNC_SECRET);
            $response = wp_remote_post(self::ENDPOINT, array(
                'timeout' => 15,
                'redirection' => 0,
                'sslverify' => true,
                'headers' => array(
                    'Content-Type' => 'application/json; charset=utf-8',
                    'Accept' => 'application/json',
                    'X-BM-Timestamp' => $timestamp,
                    'X-BM-Signature' => 'sha256=' . $signature,
                ),
                'body' => $body,
            ));
            if (is_wp_error($response)) {
                self::status('network_error', $revision);
                return $response;
            }
            $code = (int) wp_remote_retrieve_response_code($response);
            $ctype = (string) wp_remote_retrieve_header($response, 'content-type');
            $result = json_decode((string) wp_remote_retrieve_body($response), true);
            if ($code !== 200 || stripos($ctype, 'application/json') === false
                || !is_array($result) || ($result['ok'] ?? false) !== true
                || ($result['revision'] ?? null) !== $revision) {
                self::status('relay_rejected_' . $code, $revision);
                return new WP_Error('relay_rejected', 'The Cloudflare catalog relay rejected the update');
            }
            self::status('ok', $revision);
            return true;
        } finally {
            delete_option(self::LOCK_KEY);
        }
    }
}
BM_Cloudflare_Catalog_Relay::boot();
}
