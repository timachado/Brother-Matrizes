<?php
/** Smoke test autossuficiente, sem WordPress nem rede, para o shortcode isolado. */
define('ABSPATH', __DIR__);
$GLOBALS['bm_test_stock'] = 49;
$GLOBALS['bm_test_prices'] = array(329 => 19.90, 330 => 159.90, 331 => 399.90, 308 => 249.90);

class BrotherTestProduct {
    private $id;
    function __construct($id) { $this->id = $id; }
    function get_id() { return $this->id; }
    function get_status() { return 'publish'; }
    function is_purchasable() { return $this->id !== 308 || $GLOBALS['bm_test_stock'] > 0; }
    function is_in_stock() { return $this->id !== 308 || $GLOBALS['bm_test_stock'] > 0; }
    function get_price() { return (string) $GLOBALS['bm_test_prices'][$this->id]; }
    function managing_stock() { return $this->id === 308; }
    function get_stock_quantity() { return $GLOBALS['bm_test_stock']; }
}
function add_shortcode($tag, $cb) { $GLOBALS['bm_registered_shortcode'] = array($tag, $cb); }
function wc_get_product($id) {
    return isset($GLOBALS['bm_test_prices'][$id]) ? new BrotherTestProduct($id) : null;
}
function wc_price($n) { return 'R$ ' . number_format((float)$n, 2, ',', '.'); }
function home_url($p) { return 'https://timachado.ifree.page' . $p; }
function get_permalink($id) { return 'https://timachado.ifree.page/produto/' . $id . '/'; }
function esc_html($s) { return htmlspecialchars((string)$s, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8'); }
function esc_attr($s) { return esc_html($s); }
function esc_url($s) { return esc_html($s); }
function wp_kses_post($s) { return $s; }
function number_format_i18n($n) { return number_format($n, 0, ',', '.'); }

require __DIR__ . '/brother-planos-vitrine.php';
function verify($condition, $message) { if (!$condition) throw new RuntimeException($message); }
verify($GLOBALS['bm_registered_shortcode'][0] === 'brother_matrizes_planos', 'Shortcode incorreto');
$html = bm_planos_display_shortcode();
foreach (array('19,90','159,90','399,90','249,90') as $price) {
    verify(strpos($html, $price) !== false, 'Preço ausente: ' . $price);
}
verify(strpos($html, '49 licenças disponíveis') !== false, 'Estoque verdadeiro não exibido');
verify(strpos($html, '78,90') !== false, 'Economia anual incorreta');
verify(strpos($html, 'tiac_resume_plan=monthly') !== false, 'Fluxo mensal alterado');
verify(strpos($html, 'tiac_resume_plan=yearly') !== false, 'Fluxo anual alterado');
verify(strpos($html, 'tiac_resume_plan=lifetime') !== false, 'Fluxo vitalício alterado');
verify(strpos($html, '/produto/308/') !== false, 'Produto promocional incorreto');
verify(strpos($html, 'Brother Matrizes') !== false, 'Escopo da vitrine incorreto');

$GLOBALS['bm_test_stock'] = 0;
$htmlSoldOut = bm_planos_display_shortcode();
verify(strpos($htmlSoldOut, 'Indisponível') !== false, 'Sem estoque deve bloquear compra');
verify(strpos($htmlSoldOut, '/produto/308/') === false, 'Produto esgotado ainda pode ser comprado');
echo "QA shortcode WooCommerce: preco, economia, link, estoque e isolamento aprovados.\n";
