<?php
/**
 * Plugin Name: Brother Matrizes — Login Integrado de Checkout
 * Description: Handoff autenticado Google/Supabase → WooCommerce. Não altera preços, pedidos, licenças ou Efí.
 * Version: 0.1.0-rc1
 * Author: T.I. Machado
 */
if (!defined('ABSPATH')) exit;
const BM_CHECKOUT_AUDIENCE = 'ti-machado-brother-checkout-v1';

/** Shared server-side key. Supports wp-config.php or a private non-autoloaded option. */
function bm_checkout_private_key() {
  if (defined('BM_CHECKOUT_HANDOFF_KEY')) return (string) BM_CHECKOUT_HANDOFF_KEY;
  return function_exists('get_option')
    ? (string) get_option('bm_checkout_handoff_key_v1', '') : '';
}
if (function_exists('register_activation_hook')) {
  register_activation_hook(__FILE__, function() {
    if (defined('BM_CHECKOUT_HANDOFF_KEY')) return;
    $key = get_option('bm_checkout_handoff_key_v1', '');
    if (!$key) {
      // One-time generation on site only; never expose in code or APK.
      add_option('bm_checkout_handoff_key_v1',
        base64_encode(random_bytes(32)), '', false);
    }
  });
}
require_once __DIR__ . '/includes/admin-setup.php';

/** AES-256-GCM; secret never exposed through public REST or frontend. */
function bm_checkout_decode($input) {
  if (bm_checkout_private_key() === '') return new WP_Error('setup', 'Checkout integrado indisponível.');
  if (!is_string($input) || strlen($input)>2500 || strlen($input)<70 ||
      !preg_match('/^[A-Za-z0-9_-]+$/D', $input)) return new WP_Error('invalid', 'Acesso inválido.');
  $bytes=base64_decode(strtr($input, '-_', '+/').str_repeat('=', (4-strlen($input)%4)%4), true);
  $key=base64_decode(bm_checkout_private_key(), true);
  if ($bytes===false || strlen($bytes)<60 || $key===false || strlen($key)!==32)
    return new WP_Error('invalid', 'Acesso inválido ou configuração incompleta.');
  $nonce=substr($bytes, 0, 12); $data=substr($bytes, 12);
  $body=openssl_decrypt(substr($data,0,-16), 'aes-256-gcm', $key,
    OPENSSL_RAW_DATA, $nonce, substr($data,-16));
  if (!is_string($body) || strlen($body)>1500) return new WP_Error('auth', 'Acesso inválido.');
  $p=json_decode($body,true); $now=time();
  if (!is_array($p) || ($p['v']??null)!==1 ||
      ($p['iss']??'')!=='brother-matrizes-supabase' ||
      ($p['aud']??'')!==BM_CHECKOUT_AUDIENCE ||
      ($p['email_verified']??false)!==true ||
      !in_array($p['plan']??null, array('pro_monthly','pro_yearly','pro_lifetime','pro_lifetime_launch'),true) ||
      !is_string($p['sub']??null) || !preg_match('/^[a-f0-9-]{36}$/D',$p['sub']) ||
      !is_string($p['email']??null) || !is_email($p['email']) ||
      !is_int($p['iat']??null) || !is_int($p['exp']??null) ||
      $p['iat']>$now+10 || $p['iat']<$now-65 || $p['exp']<=$now ||
      $p['exp']>$p['iat']+60 ||
      !is_string($p['jti']??null) || !preg_match('/^[a-f0-9-]{36}$/D',$p['jti']))
    return new WP_Error('expired', 'Acesso inválido ou expirado. Volte ao aplicativo.');
  return $p;
}

function bm_checkout_resolve_user($p) {
  $users=get_users(array('meta_key'=>'_bm_checkout_google_sub',
    'meta_value'=>$p['sub'],'number'=>2,'fields'=>'ids'));
  if (count($users)>1) return new WP_Error('duplicate','Conta duplicada.');
  if ($users) {
    $user=get_user_by('id', $users[0]);
    if (!$user || strcasecmp($user->user_email,$p['email'])!==0)
      return new WP_Error('conflict','Confirme sua identidade no suporte.');
  } else {
    $user=get_user_by('email',$p['email']);
    if ($user) {
      // Never adopt administrator/editor/shop_manager using matching email.
      if (array_diff((array)$user->roles,array('customer','subscriber')) || empty($user->roles))
        return new WP_Error('privileged','Entre no site para vincular esta conta.');
      $linked=get_user_meta($user->ID,'_bm_checkout_google_sub',true);
      if ($linked!=='' && $linked!==$p['sub'])
        return new WP_Error('linked','Conta vinculada a outra identidade.');
    } else {
      $username='bm_'.substr(hash('sha256',$p['sub']),0,24);
      $id=wp_create_user($username,wp_generate_password(40,true,true),$p['email']);
      if (is_wp_error($id)) return new WP_Error('create','Cadastro automático indisponível.');
      $user=get_user_by('id',$id);
      if (!$user) return new WP_Error('create','Cadastro automático indisponível.');
      $user->set_role(get_role('customer')?'customer':'subscriber');
    }
    update_user_meta($user->ID,'_bm_checkout_google_sub',$p['sub']);
  }
  if (!array_intersect((array)$user->roles,array('customer','subscriber')))
    return new WP_Error('forbidden','Conta não autorizada.');
  return $user;
}
add_action('bm_checkout_cleanup',function($name) {
  if (is_string($name) && preg_match('/^bm_checkout_used_[a-f0-9]{64}$/D',$name))
    delete_option($name);
});
add_action('template_redirect',function() {
  if (!isset($_GET['bm_checkout']) || !is_page('minha-conta')) return;
  if (!is_ssl() || ($_SERVER['REQUEST_METHOD']??'')!=='GET')
    wp_die('Utilize HTTPS para continuar.','Checkout seguro',array('response'=>403));
  nocache_headers();
  header('Referrer-Policy: no-referrer');
  header('X-Robots-Tag: noindex, nofollow');
  $claim=bm_checkout_decode(is_string($_GET['bm_checkout'])?wp_unslash($_GET['bm_checkout']):'');
  if (is_wp_error($claim)) wp_die(esc_html($claim->get_error_message()),'Checkout seguro',array('response'=>403));
  $used='bm_checkout_used_'.hash('sha256',$claim['jti']);
  // WP option_name is unique; add_option fails closed on concurrent replays.
  if (!add_option($used,time(),'',false))
    wp_die('Este acesso já foi usado. Volte ao aplicativo.','Checkout seguro',array('response'=>403));
  wp_schedule_single_event(time()+600,'bm_checkout_cleanup',array($used));
  $user=bm_checkout_resolve_user($claim);
  if (is_wp_error($user)) wp_die(esc_html($user->get_error_message()),'Checkout seguro',array('response'=>403));
  wp_set_current_user($user->ID);
  wp_set_auth_cookie($user->ID,false,true);
  do_action('wp_login',$user->user_login,$user);
  if ($claim['plan']==='pro_lifetime_launch') {
    $dest=home_url('/?post_type=product&p=308');
  } else {
    $lookup=array('pro_monthly'=>'monthly','pro_yearly'=>'yearly','pro_lifetime'=>'lifetime');
    $dest=add_query_arg(array('tiac_auth'=>'checkout','tiac_resume_app'=>307,
      'tiac_resume_plan'=>$lookup[$claim['plan']]),home_url('/minha-conta/'));
  }
  wp_safe_redirect($dest,303);
  exit;
},1);
