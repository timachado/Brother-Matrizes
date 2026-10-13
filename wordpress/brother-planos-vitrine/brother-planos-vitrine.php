<?php
/**
 * Plugin Name: Brother Matrizes — Vitrine de Planos
 * Description: Comparativo e apresentação comercial exclusivos do Brother Matrizes, usando WooCommerce como fonte de preço e disponibilidade.
 * Version: 0.9.0
 * Requires PHP: 7.4
 * Text Domain: brother-planos-vitrine
 *
 * Instalar separadamente e inserir [brother_matrizes_planos] SOMENTE
 * na página oficial do Brother Matrizes. Não altera checkout/licenças.
 */
defined('ABSPATH') || exit;

if (!function_exists('bm_planos_display_shortcode')) {
    function bm_planos_display_shortcode($atts = array()) {
        // Não renderizar esta vitrine em outros produtos sem decisão explícita.
        // O shortcode precisa ser inserido manualmente na página específica.
        if (!function_exists('wc_get_product') || !function_exists('wc_price')) {
            return '<p class="bm-planos-erro">Planos temporariamente indisponíveis. Consulte a loja oficial.</p>';
        }

        $rows = array(
            'free' => array('title' => 'Gratuito', 'tag' => 'Comece a criar', 'product' => 0, 'type' => 'free',
                'description' => 'Conheça o aplicativo e realize trabalhos ocasionais.',
                'bullets' => array('Criação e edição de trabalhos', 'Biblioteca local de projetos', 'Até 3 importações de fontes e 5 de matrizes por mês', 'Teste PRO gratuito de sete dias, uma única vez quando elegível'),
                'note' => 'O período PRO de teste não altera o caráter gratuito do aplicativo. Após o prazo, permanece o acesso gratuito.' ),
            'monthly' => array('title' => 'PRO Mensal', 'tag' => 'Potencialize seu trabalho', 'product' => 329, 'type' => 'monthly',
                'description' => 'Para quem precisa de produtividade com contratação mensal.',
                'bullets' => array('Todos os recursos de criação e edição já disponíveis no aplicativo', 'Importações sem as cotas mensais do Gratuito', 'Biblioteca local de projetos', 'Acesso PRO enquanto a assinatura estiver válida no servidor'),
                'note' => 'Periodicidade mensal. Consulte as condições de renovação e cancelamento no checkout.' ),
            'yearly' => array('title' => 'PRO Anual', 'tag' => 'Mais economia e produtividade', 'product' => 330, 'type' => 'yearly',
                'description' => 'Para quem usa o aplicativo regularmente ao longo do ano.',
                'bullets' => array('Mesma categoria de recursos PRO do plano mensal', 'Importações sem as cotas mensais do Gratuito', 'Pagamento em modalidade anual', 'Atualizações oficiais disponíveis'),
                'note' => 'Confira a periodicidade, renovação e as condições do contrato antes de pagar.' ),
            'lifetime' => array('title' => 'PRO Vitalício', 'tag' => 'Liberdade sem mensalidades', 'product' => 331, 'type' => 'lifetime',
                'description' => 'Para quem prefere obter a licença PRO coberta por um pagamento único.',
                'bullets' => array('Recursos PRO incluídos na licença', 'Importações sem as cotas mensais do Gratuito', 'Pagamento único, sem mensalidade recorrente desta licença', 'Atualizações oficiais do aplicativo'),
                'note' => 'Não inclui automaticamente outros aplicativos ou futuros produtos/serviços separados.' ),
            'launch' => array('title' => 'Vitalício de Lançamento', 'tag' => 'Condição especial', 'product' => 308, 'type' => 'launch',
                'description' => 'Mesma categoria de acesso PRO Vitalício, com preço promocional limitado.',
                'bullets' => array('Mesmos direitos PRO do Vitalício convencional', 'Pagamento único, sem mensalidade recorrente', 'Importações sem as cotas mensais do Gratuito', 'Condição promocional sujeita ao estoque real'),
                'note' => 'Edição limitada originalmente a 50 licenças. Disponibilidade confirmada ao comprar.' ),
        );

        // Checkout original da T.I. Machado: não mudar nem simular a autenticação.
        $checkout = array(
            'monthly' => home_url('/minha-conta/?tiac_auth=checkout&tiac_resume_app=307&tiac_resume_plan=monthly'),
            'yearly' => home_url('/minha-conta/?tiac_auth=checkout&tiac_resume_app=307&tiac_resume_plan=yearly'),
            'lifetime' => home_url('/minha-conta/?tiac_auth=checkout&tiac_resume_app=307&tiac_resume_plan=lifetime')
        );

        $catalog = array();
        foreach ($rows as $code => $row) {
            $product = $row['product'] > 0 ? wc_get_product($row['product']) : null;
            $is_valid = $product && $product->get_status() === 'publish'
                && $product->is_purchasable() && $product->is_in_stock();
            $catalog[$code] = array(
                'product' => $product,
                'valid' => (bool) $is_valid,
                'price' => $product ? wc_price((float) $product->get_price()) : '',
                'url' => isset($checkout[$code]) ? $checkout[$code]
                    : ($product ? get_permalink($product->get_id()) : ''),
                'stock' => $product && $product->managing_stock()
                    ? $product->get_stock_quantity() : null
            );
        }

        ob_start();
        ?>
        <section class="bm-plans-vitrine" id="brother-planos" aria-label="Planos exclusivos do Brother Matrizes">
            <style>
            .bm-plans-vitrine{--navy:#071826;--surface:#122535;--gold:#e5bc68;--muted:#bac5d2;
                background:var(--navy);color:#f8fafc;border:1px solid #294156;border-radius:26px;padding:clamp(18px,3vw,34px);
                font-family:inherit;line-height:1.5;margin:32px auto;max-width:1220px;box-sizing:border-box}
            .bm-plans-vitrine *{box-sizing:border-box}
            .bm-plans-vitrine h2,.bm-plans-vitrine h3,.bm-plans-vitrine h4{color:#fff;margin:0 0 10px;line-height:1.2}
            .bm-plans-vitrine p{color:var(--muted);margin:5px 0 13px}
            .bm-plans-vitrine .bm-eyebrow{color:var(--gold);font-size:.82rem;font-weight:700;letter-spacing:.06em;text-transform:uppercase}
            .bm-plans-vitrine .bm-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,235px),1fr));gap:14px;margin:24px 0}
            .bm-plans-vitrine .bm-card{padding:18px;border:1px solid #355069;background:var(--surface);border-radius:20px;display:flex;flex-direction:column}
            .bm-plans-vitrine .bm-featured{border-color:var(--gold);box-shadow:0 0 0 1px #bd9755}
            .bm-plans-vitrine .bm-price{color:#fff;font-size:1.6rem;font-weight:800;margin:8px 0}
            .bm-plans-vitrine .bm-period{font-size:.83rem;color:var(--muted);font-weight:400}
            .bm-plans-vitrine .bm-card ul{padding:0;margin:8px 0 18px;list-style:none;flex:1}
            .bm-plans-vitrine .bm-card li{font-size:.87rem;margin:9px 0;padding-left:21px;position:relative}
            .bm-plans-vitrine .bm-card li::before{content:"✓";color:var(--gold);position:absolute;left:0}
            .bm-plans-vitrine .bm-card small{color:var(--muted);display:block;margin-bottom:12px;line-height:1.5}
            .bm-plans-vitrine .bm-action{display:block;text-align:center;background:var(--gold);color:#071826!important;
                padding:11px 12px;border-radius:12px;text-decoration:none!important;font-weight:700;margin-top:auto}
            .bm-plans-vitrine .bm-action[aria-disabled="true"]{background:#354657;color:#eef1f5!important;cursor:not-allowed}
            .bm-plans-vitrine .bm-stock{color:var(--gold);font-size:.86rem;font-weight:700}
            .bm-plans-vitrine .bm-section{border-top:1px solid #294156;padding-top:22px;margin-top:22px}
            .bm-plans-vitrine .bm-table-scroll{overflow-x:auto;max-width:100%;border-radius:14px}
            .bm-plans-vitrine table{width:100%;border-collapse:collapse;min-width:520px}
            .bm-plans-vitrine th,.bm-plans-vitrine td{padding:12px;vertical-align:top;text-align:left;border-bottom:1px solid #30455b}
            .bm-plans-vitrine th{color:var(--gold);background:#172d40}
            .bm-plans-vitrine td{font-size:.88rem}
            .bm-plans-vitrine details{border-bottom:1px solid #294156;padding:12px 2px}
            .bm-plans-vitrine summary{font-weight:700;cursor:pointer;color:#fff}
            .bm-plans-vitrine .bm-actions-bottom a{color:var(--gold)}
            @media(max-width:680px){.bm-plans-vitrine{margin:20px 0;padding:17px;border-radius:20px}
                .bm-plans-vitrine h2{font-size:1.6rem}.bm-plans-vitrine .bm-card{padding:16px}}
            </style>
            <p class="bm-eyebrow">Escolha seu jeito de criar</p>
            <h2>Planos Brother Matrizes</h2>
            <p>Crie, personalize e gerencie matrizes no celular. O aplicativo possui um plano Gratuito e opções PRO com condições de contratação diferentes. Consulte seus direitos na conta e finalize compras somente pelo checkout oficial.</p>
            <div class="bm-grid">
                <?php foreach ($rows as $code => $row) :
                    $live = $catalog[$code];
                    $is_free = $code === 'free';
                    $available = $is_free || $live['valid'];
                    $price = $is_free ? 'Grátis' : ($live['product'] ? $live['price'] : 'Indisponível');
                    $term = $code === 'monthly' ? '/ mês' : ($code === 'yearly' ? '/ ano' : ($is_free ? '' : 'pagamento único'));
                    ?>
                    <article class="bm-card <?php echo esc_attr(in_array($code, array('lifetime','launch'), true) ? 'bm-featured' : ''); ?>">
                        <p class="bm-eyebrow"><?php echo esc_html($row['tag']); ?></p>
                        <h3><?php echo esc_html($row['title']); ?></h3>
                        <p class="bm-price"><?php echo wp_kses_post($price); ?>
                            <span class="bm-period"><?php echo esc_html($term); ?></span>
                        </p>
                        <p><?php echo esc_html($row['description']); ?></p>
                        <ul><?php foreach ($row['bullets'] as $benefit) : ?>
                            <li><?php echo esc_html($benefit); ?></li>
                        <?php endforeach; ?></ul>
                        <?php if ($code === 'launch' && is_int($live['stock']) && $live['stock'] > 0): ?>
                            <p class="bm-stock"><?php echo esc_html(number_format_i18n($live['stock'])); ?> licenças disponíveis no estoque consultado</p>
                        <?php endif; ?>
                        <small><?php echo esc_html($row['note']); ?></small>
                        <?php if ($available && !$is_free): ?>
                            <a class="bm-action" rel="nofollow" href="<?php echo esc_url($live['url']); ?>">
                                <?php echo $code === 'launch' ? 'Conferir oferta promocional' : 'Contratar no site oficial'; ?>
                            </a>
                        <?php elseif ($is_free): ?>
                            <a class="bm-action" href="<?php echo esc_url(home_url('/aplicativos/brother-matrizes/')); ?>">Baixar e começar</a>
                        <?php else: ?>
                            <span class="bm-action" aria-disabled="true">Oferta indisponível no momento</span>
                        <?php endif; ?>
                    </article>
                <?php endforeach; ?>
            </div>

            <div class="bm-section">
                <h3>Compare os recursos</h3>
                <p>Os planos PRO Mensal, Anual e Vitalício têm a mesma categoria de acesso PRO validada pelo servidor. O diferencial é a contratação; o lançamento é uma condição promocional da mesma licença vitalícia.</p>
                <div class="bm-table-scroll" role="region" aria-label="Comparativo de recursos" tabindex="0">
                    <table><thead><tr><th>Recurso</th><th>Gratuito</th><th>PRO (Mensal, Anual e Vitalício)</th></tr></thead><tbody>
                        <tr><td>Criação e edição de trabalhos</td><td>Disponível</td><td>Disponível</td></tr>
                        <tr><td>Biblioteca local de projetos</td><td>Disponível</td><td>Disponível</td></tr>
                        <tr><td>Importações de fontes</td><td>Até 3 por mês</td><td>Sem a cota mensal do Gratuito</td></tr>
                        <tr><td>Importações de matrizes</td><td>Até 5 por mês</td><td>Sem a cota mensal do Gratuito</td></tr>
                        <tr><td>Atualizações oficiais</td><td>Disponíveis</td><td>Disponíveis</td></tr>
                        <tr><td>Conversor EMB/PES</td><td>Em desenvolvimento</td><td>Em desenvolvimento</td></tr>
                        <tr><td>Testes Beta antecipados</td><td colspan="2">Programa futuro, sem direito contratual garantido</td></tr>
                    </tbody></table>
                </div>
            </div>
            <div class="bm-section">
                <h3>Por que escolher o Brother Matrizes Vitalício?</h3>
                <p>É uma alternativa para trabalhar com os recursos PRO incluídos na licença sem mensalidades recorrentes. A modalidade promocional oferece a mesma categoria de acesso, enquanto houver estoque.</p>
                <h4>Tenha prioridade nas próximas novidades</h4>
                <p>Está em planejamento oferecer participação prioritária em experiências Beta e oportunidades de conhecer recursos antecipadamente. A participação será voluntária e dependerá de critérios técnicos, vagas e regras divulgadas quando o programa existir. Não é benefício garantido na compra atual.</p>
            </div>
            <div class="bm-section">
                <h3>Perguntas frequentes</h3>
                <?php
                $faqs = array(
                    'O aplicativo é gratuito?' => 'Sim. O download e o plano Gratuito não têm cobrança; importações gratuitas possuem limites mensais.',
                    'Como funcionam os sete dias de teste?' => 'A conta elegível pode ativar uma única vez sete dias de recursos PRO, sem cartão e sem cobrança automática. O período é confirmado pelo servidor.',
                    'O que acontece depois do teste?' => 'A conta retorna ao acesso Gratuito; os projetos salvos localmente permanecem no aparelho.',
                    'Qual a diferença entre mensal e anual?' => 'Os direitos PRO são da mesma categoria. A diferença está no período e nas condições de contratação informadas no checkout.',
                    'O vitalício possui mensalidades?' => 'Não. A licença vitalícia coberta pelo produto tem pagamento único e não inclui automaticamente serviços ou outros aplicativos separados.',
                    'Quem participa dos testes Beta?' => 'Programas Beta futuros terão regras próprias e participação voluntária; ainda não constituem um benefício garantido.',
                    'Como cancelar ou recuperar minha licença?' => 'Acesse Minha conta e Minhas compras no site da T.I. Machado, utilizando a conta vinculada à contratação. A liberação no app depende de confirmação segura do servidor.',
                    'Quais meios de pagamento e condições de renovação?' => 'Consulte o checkout oficial; as formas de pagamento e a renovação variam conforme a modalidade escolhida.',
                );
                foreach ($faqs as $question => $answer) : ?>
                    <details><summary><?php echo esc_html($question); ?></summary>
                        <p><?php echo esc_html($answer); ?></p></details>
                <?php endforeach; ?>
            </div>
            <p class="bm-actions-bottom">Já comprou? <a href="<?php echo esc_url(home_url('/minha-conta/meus-aplicativos/')); ?>">Consulte suas licenças</a>. Após pagar, volte ao aplicativo e toque em <strong>Verificar licença</strong>. Nenhuma funcionalidade PRO é liberada apenas pelo retorno do navegador.</p>
        </section>
        <?php
        return ob_get_clean();
    }

    add_shortcode('brother_matrizes_planos', 'bm_planos_display_shortcode');
}
