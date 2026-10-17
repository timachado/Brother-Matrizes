<?php
// Isolated PHP integration checks: no WordPress database, WooCommerce, or network.
define('ABSPATH', __DIR__);
define('BM_CHECKOUT_HANDOFF_KEY', base64_encode(str_repeat('A', 32)));
class WP_Error {
    private $code;
    private $message;
    public function __construct($code, $message) {
        $this->code=$code;
        $this->message=$message;
    }
    public function get_error_code(){return $this->code;}
}
function is_email($mail) {return filter_var($mail,FILTER_VALIDATE_EMAIL)!==false;}
function add_action($name,$callback,$priority=10,$args=1) {}
require dirname(__DIR__).'/wordpress/brother-checkout-handoff/brother-checkout-handoff.php';
function ticket($claims) {
    $iv=random_bytes(12);$tag='';
    $cipher=openssl_encrypt(json_encode($claims), 'aes-256-gcm',
        base64_decode(BM_CHECKOUT_HANDOFF_KEY), OPENSSL_RAW_DATA,$iv,$tag);
    return rtrim(strtr(base64_encode($iv.$cipher.$tag),'+/','-_'),'=');
}
function check($condition, $message) {
    if (!$condition) throw new RuntimeException($message);
}
$now=time();
$claim=array(
  'v'=>1,'iss'=>'brother-matrizes-supabase',
  'aud'=>'ti-machado-brother-checkout-v1',
  'sub'=>'7e8a7798-d3b9-407d-93eb-e3ef4e44dc05',
  'email'=>'cliente@example.org','email_verified'=>true,'plan'=>'pro_monthly',
  'iat'=>$now,'exp'=>$now+45,
  'jti'=>'ecf2bbd7-219a-4a63-8dde-54197d6c90b8');
$encoded=ticket($claim);
$decoded=bm_checkout_decode($encoded);
check(!($decoded instanceof WP_Error),'Valid AES ticket rejected');
check($decoded['sub']===$claim['sub'],'Supabase subject not preserved');
$flipped=$encoded;
$flipped[30]=($flipped[30]==='A'?'B':'A');
check(bm_checkout_decode($flipped) instanceof WP_Error,'Tampered ticket accepted');
$expired=$claim; $expired['iat']=$now-200; $expired['exp']=$now-150;
check(bm_checkout_decode(ticket($expired)) instanceof WP_Error,'Expired ticket accepted');
$wrong=$claim; $wrong['aud']='malicious-website';
check(bm_checkout_decode(ticket($wrong)) instanceof WP_Error,'Wrong audience accepted');
$other=$claim; $other['plan']='pro_admin_override';
check(bm_checkout_decode(ticket($other)) instanceof WP_Error,'Unauthorized plan accepted');
$unverified=$claim; $unverified['email_verified']=false;
check(bm_checkout_decode(ticket($unverified)) instanceof WP_Error,'Unverified email accepted');
check(bm_checkout_decode('broken') instanceof WP_Error,'Bad format accepted');
echo "Brother SSO: valid, tampered, expired, audience, plan and identity checks OK\n";
