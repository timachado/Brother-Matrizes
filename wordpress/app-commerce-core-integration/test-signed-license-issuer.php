<?php
/** Pure PHP tests: no WordPress boot, remote API, payment or production DB. */
define('ABSPATH', __DIR__ . '/');
require_once __DIR__ . '/bm-signed-license-issuer.php';

function issuer_assert($valid, $why) {
    if (!$valid) throw new RuntimeException('FAIL: ' . $why);
}
function issuer_rejected($call, $why) {
    try {
        $call();
    } catch (InvalidArgumentException|RuntimeException $e) {
        return;
    }
    throw new RuntimeException('FAIL: expected rejection for ' . $why);
}

$key = openssl_pkey_new(array(
    'private_key_type' => OPENSSL_KEYTYPE_EC,
    'curve_name' => 'prime256v1',
));
issuer_assert($key !== false, 'Generate ephemeral P-256 test key');
openssl_pkey_export($key, $pem);
$public = openssl_pkey_get_details($key)['key'];
$now = 1791626400;
$verified = array(
    'sub' => '12345678-1234-4123-8123-123456789abc',
    'device_id' => '0123456789abcdef0123456789abcdef',
    'license_id' => 'license_12345678',
    'plan_code' => 'pro_lifetime_launch',
    'status' => 'active',
    'order_id' => 3088, // TEST order ID only, not a production order.
    'wp_user_id' => 70,
    'app_id' => 307,
    'entitlement_expires_at' => null,
);

$lease = TIAC_Brother_Signed_Issuer::issue_verified($verified, $pem, $now);
$sections = explode('.', $lease);
issuer_assert(count($sections) === 3 && $sections[0] === 'bm1', 'Protocol prefix');
$decode = static fn($input) => base64_decode(strtr($input, '-_', '+/'), true);
$claims = json_decode($decode($sections[1]), true);
issuer_assert($claims['exp'] === $now + 3600, 'Default 1h lease');
issuer_assert($claims['plan'] === 'pro_lifetime_launch', 'Approved plan');
issuer_assert($claims['aud'] === 'brother-matrizes-android', 'Restricted audience');
issuer_assert(openssl_verify('bm1.' . $sections[1], $decode($sections[2]),
    $public, OPENSSL_ALGO_SHA256) === 1, 'ECDSA P-256 verification');
issuer_assert(openssl_verify('bm1.' . $sections[1] . 'changed', $decode($sections[2]),
    $public, OPENSSL_ALGO_SHA256) !== 1, 'Reject tampering');

foreach (array(
    array('status','pending'),
    array('status','refunded'),
    array('plan_code','free'),
    array('plan_code','invalid_plan'),
    array('device_id','not-a-device'),
    array('sub','fake-sub'),
    array('order_id',0),
    array('app_id',0),
) as $change) {
    $bad = $verified;
    $bad[$change[0]] = $change[1];
    issuer_rejected(static fn()=>TIAC_Brother_Signed_Issuer::issue_verified(
        $bad, $pem, $now), implode('=', $change));
}
$bad = $verified;
unset($bad['wp_user_id']);
issuer_rejected(static fn()=>TIAC_Brother_Signed_Issuer::issue_verified(
    $bad, $pem, $now), 'missing WooCommerce owner');
$bad = $verified;
$bad['plan_code'] = 'pro_monthly';
issuer_rejected(static fn()=>TIAC_Brother_Signed_Issuer::issue_verified(
    $bad, $pem, $now), 'recurring without expiry');
$bad['entitlement_expires_at'] = $now + 200;
$short = TIAC_Brother_Signed_Issuer::issue_verified($bad, $pem, $now);
$shortClaims = json_decode($decode(explode('.', $short)[1]), true);
issuer_assert($shortClaims['exp'] === $now + 200, 'Clamp lease to paid expiry');
issuer_rejected(static fn()=>TIAC_Brother_Signed_Issuer::issue_verified(
    $verified, $pem, $now, 604801), 'long lease');
issuer_rejected(static fn()=>TIAC_Brother_Signed_Issuer::issue_verified(
    $verified, 'invalid-key', $now), 'missing signing key');
$rsa = openssl_pkey_new(array(
    'private_key_type' => OPENSSL_KEYTYPE_RSA,
    'private_key_bits' => 2048,
));
openssl_pkey_export($rsa, $rsaPem);
issuer_rejected(static fn()=>TIAC_Brother_Signed_Issuer::issue_verified(
    $verified, $rsaPem, $now), 'reject RSA key');
echo "PASS: Brother Matrizes WordPress signed-lease issuer QA.\n";
