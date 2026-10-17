<?php
// Unit integration test of the real WooCommerce checkout targeting, no user
// accounts, orders, products or payment gateways are touched.
define('ABSPATH', __DIR__);
define('BM_CHECKOUT_HANDOFF_KEY', base64_encode(str_repeat('A',32)));
class WP_Error {
  private $code;
  private $message;
  function __construct($code,$message){$this->code=$code;$this->message=$message;}
  function get_error_code(){return $this->code;}
  function get_error_message(){return $this->message;}
}
function add_action($name,$fn,$priority=10,$args=1){}
function is_email($s){return (bool)filter_var($s,FILTER_VALIDATE_EMAIL);}
require dirname(__DIR__).'/wordpress/brother-checkout-handoff/brother-checkout-handoff.php';

class TestProduct {
  public $name, $ready=true;
  function __construct($name){$this->name=$name;}
  function get_status(){return 'publish';}
  function get_type(){return 'simple';}
  function is_purchasable(){return $this->ready;}
  function is_in_stock(){return true;}
  function get_price(){return 199.90;}
  function get_name(){return $this->name;}
}
class TestCart {
  public $items=[], $add=[], $recalculations=0;
  function get_cart(){return $this->items;}
  function add_to_cart($id,$qty){
    $this->add[]=array($id,$qty);
    $this->items[]=array('product_id'=>$id,'quantity'=>$qty);
    return 'inserted-cart-key';
  }
  function remove_cart_item($key){if(!array_key_exists($key,$this->items))return false;unset($this->items[$key]);return true;}
  function calculate_totals(){$this->recalculations++;}
}
class TestSession {
  public $created=0;
  function set_customer_session_cookie($set){if($set)$this->created++;}
}
function WC(){return $GLOBALS['wc'];}
function wc_get_product($id){return $GLOBALS['products'][$id]??false;}
function wc_get_checkout_url(){return $GLOBALS['checkoutUrl'];}
function wp_parse_url($address){return parse_url($address);}
function home_url($path='/'){return 'https://timachado.ifree.page'.$path;}
function remove_accents($text){return strtr($text, 'íáãâéêôú', 'iaaaeeou');}
function require_result($ok,$message){if(!$ok)throw new RuntimeException($message);}
$GLOBALS['products']=[
  329=>new TestProduct('Brother Matrizes — Plano Mensal'),
  330=>new TestProduct('Brother Matrizes — Plano Anual'),
  331=>new TestProduct('Brother Matrizes — Licenca Vitalicia'),
  308=>new TestProduct('Brother Matrizes — Vitalicio Lancamento')
];
$GLOBALS['checkoutUrl']='https://timachado.ifree.page/finalizar-compra/';
function fresh_cart(){
  $GLOBALS['wc']=(object)['cart'=>new TestCart(),'session'=>new TestSession()];
}
foreach ([
  'pro_monthly'=>329,'pro_yearly'=>330,
  'pro_lifetime'=>331,'pro_lifetime_launch'=>308
] as $plan=>$productId) {
  fresh_cart();
  $checkout=bm_checkout_prepare_woo_cart($plan);
  require_result($checkout === $GLOBALS['checkoutUrl'],'Wrong checkout URL for '.$plan);
  require_result($GLOBALS['wc']->cart->add === [[$productId,1]],
    'Wrong product/quantity for '.$plan);
  require_result($GLOBALS['wc']->session->created===1,'No Woo session cookie');
  require_result($GLOBALS['wc']->cart->recalculations===1,'No cart totals');
  // If matching product is already in cart, do NOT duplicate it.
  require_result(bm_checkout_prepare_woo_cart($plan)===$GLOBALS['checkoutUrl'],
    'Same cart should remain purchasable');
  require_result(count($GLOBALS['wc']->cart->add)===1,'Repeated product addition');
}
fresh_cart();
require_result(bm_checkout_prepare_woo_cart('admin') instanceof WP_Error,
  'Unknown plan must be rejected');
$GLOBALS['wc']->cart->items=[['product_id'=>113,'quantity'=>1]];
$r=bm_checkout_prepare_woo_cart('pro_monthly');
require_result($r instanceof WP_Error && $r->get_error_code()==='other_cart_items',
  'Existing unrelated-app cart must be protected');
require_result($GLOBALS['wc']->cart->add===[],'Should never mix other app in cart');
fresh_cart();
// Another Brother plan is stale in cart: replace without requiring consent.
fresh_cart();
$GLOBALS['wc']->cart->items=[['product_id'=>329,'quantity'=>1]];
$result=bm_checkout_prepare_woo_cart('pro_yearly');
require_result($result===$GLOBALS['checkoutUrl'],'Switching Brother plans should open checkout');
require_result(count($GLOBALS['wc']->cart->add)===1 &&
  $GLOBALS['wc']->cart->add[0]===[330,1],'Expected yearly plan after replacing monthly');
require_result(count($GLOBALS['wc']->cart->items)===1 &&
  (int)array_values($GLOBALS['wc']->cart->items)[0]['product_id']===330,
  'Old Brother plan must be removed');

// Keep selected plan, remove another stale Brother plan, no duplicates.
fresh_cart();
$GLOBALS['wc']->cart->items=[
  ['product_id'=>331,'quantity'=>1], ['product_id'=>308,'quantity'=>1]];
$result=bm_checkout_prepare_woo_cart('pro_lifetime');
require_result($result===$GLOBALS['checkoutUrl'],'Mixed Brother plan must be fixed');
require_result(count($GLOBALS['wc']->cart->items)===1 &&
  (int)array_values($GLOBALS['wc']->cart->items)[0]['product_id']===331 &&
  $GLOBALS['wc']->cart->add===[], 'Already selected plan must not duplicate');

// Third-party product requires explicit confirmation, no silent deletion.
fresh_cart();
$GLOBALS['wc']->cart->items=[['product_id'=>113,'quantity'=>1]];
$result=bm_checkout_prepare_woo_cart('pro_monthly');
require_result($result instanceof WP_Error &&
  $result->get_error_code()==='other_cart_items',
  'Third-party product should require explicit confirmation');
require_result(count($GLOBALS['wc']->cart->items)===1,'Unrelated cart was modified');
// Simulate the separate, CSRF-validated confirmation action from the user.
$result=bm_checkout_prepare_woo_cart('pro_monthly',true);
require_result($result===$GLOBALS['checkoutUrl'],'Consent must lead to checkout');
require_result(count($GLOBALS['wc']->cart->items)===1 &&
  (int)array_values($GLOBALS['wc']->cart->items)[0]['product_id']===329,
  'Only authorized Brother product should remain after consent');

$GLOBALS['products'][329]->name='Bíblia EBD Mensal';
$r=bm_checkout_prepare_woo_cart('pro_monthly');
require_result($r instanceof WP_Error && $r->get_error_code()==='wrong_product',
  'Mismatched product identity must not be purchased');
$GLOBALS['products'][329]->name='Brother Matrizes — Plano Mensal';
$GLOBALS['products'][329]->ready=false;
$r=bm_checkout_prepare_woo_cart('pro_monthly');
require_result($r instanceof WP_Error && $r->get_error_code()==='offer_unavailable',
  'Unavailable offer must not be purchased');
$GLOBALS['products'][329]->ready=true;
$GLOBALS['checkoutUrl']='https://evil.example/checkout/';
$r=bm_checkout_prepare_woo_cart('pro_monthly');
require_result($r instanceof WP_Error && $r->get_error_code()==='checkout_invalid',
  'Cross-origin checkout redirect must be rejected');
echo "Checkout Google SSO: 4 planos, troca de planos Brother, consentimento para itens externos, sem duplicacao e sem redirecionamento externo. OK\n";
