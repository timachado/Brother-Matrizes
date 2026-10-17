<?php
/**
 * Plugin Name: Brother Matrizes — Login Integrado de Checkout
 * Description: Handoff autenticado Google/Supabase → WooCommerce. Não altera preços, pedidos, licenças ou Efí.
 * Version: 0.1.2-rc3
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
function bm_checkout_prepare_woo_cart($plan, $replaceForeignCart=false) {
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

  // Switching between Brother plans is safe and automatic. A stale monthly,
  // yearly, lifetime or launch option must not block the chosen new plan.
  // Never silently discard unrelated applications' products.
  $brotherProductIds = array(308, 329, 330, 331);
  $already = false;
  $brotherOtherKeys = array();
  $foreignKeys = array();
  foreach ((array) $cart->get_cart() as $cartKey => $item) {
    $lineId = (int)($item['product_id'] ?? 0);
    if ($lineId === (int)$productId) {
      $already = true;
    } elseif (in_array($lineId, $brotherProductIds, true)) {
      $brotherOtherKeys[] = $cartKey;
    } else {
      $foreignKeys[] = $cartKey;
    }
  }
  if ($foreignKeys && !$replaceForeignCart) {
    return new WP_Error('other_cart_items',
      'O carrinho tem outros produtos. Confirme a troca para comprar somente o plano escolhido.');
  }
  // Preflight completed. Remove other Brother plans, and ONLY upon explicit
  // consent remove unrelated cart items.
  foreach (array_merge($brotherOtherKeys, $replaceForeignCart ? $foreignKeys : array()) as $cartKey) {
    if (!method_exists($cart, 'remove_cart_item') || !$cart->remove_cart_item($cartKey)) {
      return new WP_Error('cart_replace_failed',
        'Não foi possível preparar o carrinho para este plano.');
    }
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

/**
 * A second, explicitly confirmed action is required only when the existing
 * cart contains non-Brother products. The selected plan lives in the private
 * Woo session, never in a URL or a forged hidden POST field.
 */
function bm_checkout_pending_plan() {
  if (!function_exists('WC') || !WC()->session) return '';
  $pending=WC()->session->get('bm_checkout_pending_plan');
  if (!is_array($pending) ||
      !isset($pending['plan'],$pending['created']) ||
      !bm_checkout_official_product($pending['plan']) ||
      (int)$pending['created'] < time()-300 ||
      (int)$pending['created'] > time()+10) {
    WC()->session->__unset('bm_checkout_pending_plan');
    return '';
  }
  return $pending['plan'];
}

function bm_checkout_review_page($plan) {
  status_header(200);
  nocache_headers();
  header('Content-Type: text/html; charset=UTF-8');
  header('Referrer-Policy: no-referrer');
  header('X-Robots-Tag: noindex, nofollow');
  $labels=array(
    'pro_monthly'=>'PRO Mensal',
    'pro_yearly'=>'PRO Anual',
    'pro_lifetime'=>'PRO Vitalício',
    'pro_lifetime_launch'=>'Vitalício de Lançamento'
  );
  $title=esc_html($labels[$plan]);
  $action=esc_url(admin_url('admin-post.php'));
  $nonce=wp_nonce_field('bm_checkout_replace_'.$plan,'bm_checkout_nonce',true,false);
  $cartUrl=esc_url(function_exists('wc_get_cart_url')
    ? wc_get_cart_url() : home_url('/carrinho/'));
  echo '<!doctype html><html lang="pt-BR"><head><meta charset="UTF-8">'
     .'<meta name="viewport" content="width=device-width,initial-scale=1">'
     .'<title>Confirmar compra — Brother Matrizes</title>'
     .'<style>body{font-family:system-ui,sans-serif;background:#05152a;color:#edf6ff;'
     .'min-height:100vh;display:grid;place-items:center;margin:0;padding:20px}'
     .'main{box-sizing:border-box;width:100%;max-width:490px;border:1px solid #245678;'
     .'background:#0a2039;border-radius:20px;padding:25px}'
     .'h1{font-size:1.5rem;margin:0 0 12px}p{line-height:1.6;color:#d4e6ef}'
     .'button,a{display:block;box-sizing:border-box;width:100%;text-align:center;'
     .'border-radius:11px;padding:14px;font:inherit;font-weight:650}'
     .'button{background:#12a4f6;color:#042038;border:0;cursor:pointer}'
     .'a{color:#bce7ff;margin-top:12px}</style></head><body><main>'
     .'<h1>Confirmar plano '.$title.'</h1>'
     .'<p>Há outro produto no seu carrinho. Para continuar com '
     .'somente este plano do Brother Matrizes, precisamos substituí-lo.'
     .' Isso não cancela pedidos já realizados.</p>'
     .'<form method="post" action="'.$action.'">'
     .'<input type="hidden" name="action" value="bm_checkout_replace_cart">'
     .$nonce
     .'<button type="submit">Continuar com '.$title.' e ir ao pagamento</button>'
     .'</form><a href="'.$cartUrl.'">Manter e revisar meu carrinho</a>'
     .'</main></body></html>';
  exit;
}

// The confirmation GET must happen after the WordPress auth cookie becomes
// available to the browser, so the WP nonce is tied to a real logged-in session.
add_action('template_redirect',function() {
  if (!isset($_GET['bm_checkout_review']) || !is_page('minha-conta')) return;
  if (!is_user_logged_in()) {
    wp_die('Faça login novamente pelo aplicativo.','Checkout seguro',array('response'=>403));
  }
  $plan=bm_checkout_pending_plan();
  if (!$plan) {
    wp_die('Solicitação expirada. Escolha o plano novamente no Brother Matrizes.',
      'Checkout seguro',array('response'=>410));
  }
  bm_checkout_review_page($plan);
},2);

// Explicit user-clicked form. Only current logged-in customer's private Woo
// session can authorize substitution; request is CSRF-protected.
add_action('admin_post_bm_checkout_replace_cart',function() {
  if (!is_user_logged_in()) {
    wp_die('Entre pelo aplicativo antes de continuar.','Checkout seguro',array('response'=>403));
  }
  $plan=bm_checkout_pending_plan();
  if (!$plan) {
    wp_die('Confirmação expirada. Escolha o plano novamente.',
      'Checkout seguro',array('response'=>410));
  }
  check_admin_referer('bm_checkout_replace_'.$plan,'bm_checkout_nonce');
  $checkout=bm_checkout_prepare_woo_cart($plan,true);
  if (is_wp_error($checkout)) {
    wp_die(esc_html($checkout->get_error_message()),
      'Não foi possível abrir o pagamento',array('response'=>409));
  }
  WC()->session->__unset('bm_checkout_pending_plan');
  wp_safe_redirect($checkout,303);
  exit;
});

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
    if ($checkout->get_error_code() === 'other_cart_items' &&
        function_exists('WC') && WC()->session) {
      WC()->session->set('bm_checkout_pending_plan',
        array('plan'=>$claim['plan'],'created'=>time()));
      if (method_exists(WC()->session,'set_customer_session_cookie')) {
        WC()->session->set_customer_session_cookie(true);
      }
      wp_safe_redirect(home_url('/minha-conta/?bm_checkout_review=1'),303);
      exit;
    }
    wp_die(esc_html($checkout->get_error_message()),
      'Não foi possível abrir o pagamento',
      array('response'=>409));
  }
  $dest = $checkout;
  wp_safe_redirect($dest,303);
  exit;
},1);
