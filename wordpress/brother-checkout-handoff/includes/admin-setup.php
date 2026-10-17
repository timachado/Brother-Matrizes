<?php
if (!defined('ABSPATH')) exit;

/**
 * Minimal administrator-only setup page.
 * No user, payment, license or subscription records are modified.
 * Key generated once by plugin activation and stored autoload=false.
 */
add_action('admin_menu', function() {
    add_management_page(
        'Brother Matrizes — Checkout Google',
        'Brother — Checkout Google',
        'manage_options',
        'bm-checkout-google',
        function() {
            if (!current_user_can('manage_options')) return;
            $configured = bm_checkout_private_key();
            $keyBytes = $configured ? base64_decode($configured, true) : false;
            $valid = is_string($keyBytes) && strlen($keyBytes) === 32;
            echo '<div class="wrap"><h1>Brother Matrizes — Login Google e Checkout</h1>';
            echo '<p>Esta integração permite usar a identidade Google do aplicativo para autenticar o comprador no WooCommerce, sem segundo cadastro.</p>';
            echo '<p>O acesso PRO só é liberado após confirmação real de pagamento. WooCommerce, Efí, licenças, planos e pedidos permanecem intactos.</p>';
            echo '<p><strong>Chave privada no WordPress:</strong> '
                . ($valid ? 'configurada' : 'ausente ou inválida') . '</p>';
            if ($valid) {
                echo '<p><strong>Próxima configuração:</strong> copie a chave e cadastre-a em <code>BROTHER_CHECKOUT_HANDOFF_KEY</code> nas Edge Function Secrets do projeto Supabase do Brother Matrizes.</p>';
                echo '<p><label for="bm-checkout-key">Chave Base64 — exibida somente para administradores</label></p>';
                echo '<input class="regular-text code" type="password" id="bm-checkout-key" readonly autocomplete="off" spellcheck="false" value="'
                    . esc_attr($configured) . '" aria-label="Chave privada do checkout" />';
                echo ' <button type="button" class="button" id="bm-checkout-show">Mostrar ou ocultar</button>';
                echo '<p class="description">Não envie este valor em conversas, prints, GitHub ou APK. Salve apenas nas configurações privadas do Supabase.</p>';
                echo '<script>(function(){var b=document.getElementById("bm-checkout-show");var t=document.getElementById("bm-checkout-key");if(b&&t){b.addEventListener("click",function(){t.type=t.type==="password"?"text":"password";});}})();</script>';
            } else {
                echo '<p>O plugin ainda não tem uma chave válida. Reative apenas este plugin após verificar o banco e o wp-config.php.</p>';
            }
            echo '<p><a href="https://supabase.com/dashboard/project/dwpcddiramxlhavdmmyn/settings/functions" target="_blank" rel="noopener noreferrer">Abrir Supabase — Edge Function Secrets</a></p>';
            echo '<p><strong>Importante:</strong> após salvar no Supabase, teste o checkout usando uma conta de cliente não administrativa. Não habilite a build comercial antes dos testes.</p>';
            echo '</div>';
        }
    );
});
