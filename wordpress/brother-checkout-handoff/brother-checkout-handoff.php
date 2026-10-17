<?php
/**
 * Plugin Name: Brother Matrizes — Login Integrado de Checkout
 * Description: Handoff autenticado Google/Supabase → WooCommerce. Não altera preços, pedidos, licenças ou Efí.
 * Version: 0.1.1-rc2
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
/**
 * Authoritative product mapping for Brother Matrizes, app post #307.
 * Check WooCommerce's real product at runtime; prices, stock and recurrence
 * are never copied from the app or inferred from a URL parameter.
 */
function bm_checkout_official_product($plan) {
  $products = array(
    'pro_monthly' => array(329, 'mensal'),
    'pro_yearly' => array(330, 'anual'),
    'pro_lifetime' => array(331, 'vital'),
    'pro_lifetime_launch' => array(308, 'vital'),
  );
  return isset($products[$plan]) ? $products[$plan] : null;
}

/** @return string|WP_Error Official HTTPS Woo checkout URL or a safe error. */
function bm_checkout_prepare_woo_cart($plan) {
  $mapping = bm_checkout_official_product($plan);
  if (!$mapping) return new WP_Error('invalid_plan','Plano não autorizado.');
  if (!function_exists('WC') || !function_exists('wc_get_product') ||
      !function_exists('wc_get_checkout_url')) {
    return new WP_Error('woocommerce_offline','Pagamento indisponível no momento.');
  }

  // template_redirect normally runs after WooCommerce initializes its cart.
  // Load the real Woo cart if a different plugin deferred its creation.
  if (!WC()->cart && function_exists('wc_load_cart')) {
    wc_load_cart();
  }
  $cart = WC()->cart;
  if (!$cart) return new WP_Error('cart_offline','Carrinho temporariamente indisponível.');

  $productId = $mapping[0];
  $product = wc_get_product($productId);
  if (!$product || $product->get_status() !== 'publish' ||
      $product->get_type() !== 'simple' ||
      !$product->is_purchasable() || !$product->is_in_stock() ||
      (float) $product->get_price() <= 0) {
    return new WP_Error('offer_unavailable','Plano indisponível. Confira a oferta no site.');
  }
  // Prevent attaching another product accidentally if a Woo product ID
  // changes in production. No product of another application is allowed.
  $name = function_exists('remove_accents')
    ? strtolower(remove_accents((string)$product->get_name()))
    : strtolower((string)$product->get_name());
  if (strpos($name,'brother matrizes') !== 0 ||
      strpos($name,$mapping[1]) === false) {
    return new WP_Error('wrong_product','Produto comercial não corresponde ao plano escolhido.');
  }

  $already = false;
  foreach ((array) $cart->get_cart() as $item) {
    if ((int)($item['product_id'] ?? 0) !== (int)$productId) {
      // Do not remove items, join unrelated purchases or change a cart
      // that belongs to another application on the shared WordPress site.
      return new WP_Error('other_cart_items',
        'Seu carrinho contém outro produto. Conclua ou limpe esse carrinho no site antes de contratar este plano.');
    }
    $already = true;
  }
  if (!$already && !$cart->add_to_cart($productId, 1)) {
    return new WP_Error('cart_add_failed','Não foi possível adicionar o plano ao carrinho.');
  }
  if (method_exists($cart,'calculate_totals')) $cart->calculate_totals();
  if (WC()->session && method_exists(WC()->session,'set_customer_session_cookie')) {
    WC()->session->set_customer_session_cookie(true);
  }
  $checkout = wc_get_checkout_url();
  $parts = is_string($checkout) ? wp_parse_url($checkout) : false;
  $site = wp_parse_url(home_url('/'));
  if (!is_array($parts) || !is_array($site) ||
      ($parts['scheme'] ?? '') !== 'https' ||
      strcasecmp($parts['host'] ?? '',$site['host'] ?? '') !== 0) {
    return new WP_Error('checkout_invalid','Endereço de pagamento inválido.');
  }
  return $checkout;
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
  // All four official Brother Matrizes plans MUST go directly through the
  // WooCommerce cart/checkout. The old 'tiac_auth=checkout' URL only opened
  // Minha Conta and could trigger an extra email confirmation screen.
  $checkout = bm_checkout_prepare_woo_cart($claim['plan']);
  if (is_wp_error($checkout)) {
    wp_die(esc_html($checkout->get_error_message()),
      'Não foi possível abrir o pagamento',
      array('response'=>409));
  }
  $dest = $checkout;
  wp_safe_redirect($dest,303);
  exit;
},1);
