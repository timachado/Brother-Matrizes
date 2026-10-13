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

        $annual_savings = null;
        $monthly_product = $catalog['monthly']['product'];
        $yearly_product = $catalog['yearly']['product'];
        if ($catalog['monthly']['valid'] && $catalog['yearly']['valid']
            && $monthly_product && $yearly_product) {
            $monthly_amount = (float) $monthly_product->get_price();
            $annual_amount = (float) $yearly_product->get_price();
            $difference = ($monthly_amount * 12) - $annual_amount;
            if ($monthly_amount > 0 && $annual_amount > 0 && $difference > 0) {
                $annual_savings = wc_price($difference);
            }
        }

        // Resumos da vitrine: detalhes comerciais ficam no FAQ, sem repetir longas notas nos cards.
        $highlights = array(
            'free' => array('Crie, edite e salve projetos', '3 fontes e 5 matrizes por mês', '7 dias PRO para contas elegíveis'),
            'monthly' => array('Importações sem a cota do Gratuito', 'Recursos PRO incluídos'),
            'yearly' => array('Mesmos recursos do PRO Mensal', 'Importações sem a cota do Gratuito'),
            'lifetime' => array('Recursos PRO cobertos pela licença', 'Uma compra, sem mensalidades'),
            'launch' => array('Mesmos direitos do PRO Vitalício', 'Preço especial conforme estoque')
        );

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
            .bm-plans-vitrine .bm-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,280px),1fr));gap:12px;margin:18px 0}
            .bm-plans-vitrine .bm-card{padding:16px;border:1px solid #355069;background:var(--surface);border-radius:18px;display:flex;flex-direction:column}
            .bm-plans-vitrine .bm-featured{border-color:var(--gold);box-shadow:0 0 0 1px #bd9755}
            .bm-plans-vitrine .bm-price{color:#fff;font-size:1.6rem;font-weight:800;margin:8px 0}
            .bm-plans-vitrine .bm-period{font-size:.83rem;color:var(--muted);font-weight:400}
            .bm-plans-vitrine .bm-card ul{padding:0;margin:8px 0 18px;list-style:none;flex:1}
            .bm-plans-vitrine .bm-card li{font-size:.87rem;margin:5px 0;padding-left:21px;position:relative}
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
            .bm-plans-vitrine .bm-card ul{margin:8px 0 12px}
            .bm-plans-vitrine .bm-plan-variants{margin-top:auto;border-top:1px solid #345068}
            .bm-plans-vitrine .bm-plan-variant{display:flex;justify-content:space-between;
                align-items:center;gap:12px;border-bottom:1px solid #345068;padding:11px 0;font-size:.9rem}
            .bm-plans-vitrine .bm-plan-variant span strong{display:block;color:white;font-size:1rem}
            .bm-plans-vitrine .bm-plan-variant a{color:#071826;background:var(--gold);
                padding:7px 10px;border-radius:9px;text-decoration:none;font-weight:700;white-space:nowrap}
            .bm-plans-vitrine .bm-plan-variant>span:last-child{color:var(--muted);font-size:.75rem}
            .bm-plans-vitrine details.bm-section{border:1px solid #294156;background:var(--surface);
                border-radius:16px;padding:14px 16px;margin-top:12px}
            .bm-plans-vitrine details.bm-section>summary{list-style:none;display:flex;justify-content:space-between;
                align-items:center;font-size:1rem;gap:12px}
            .bm-plans-vitrine details.bm-section>summary::-webkit-details-marker{display:none}
            .bm-plans-vitrine details.bm-section>summary::after{content:"+";color:var(--gold);font-size:1.25rem}
            .bm-plans-vitrine details.bm-section[open]>summary::after{content:"−"}
            .bm-plans-vitrine details.bm-section[open]>summary{margin-bottom:12px}
            .bm-plans-vitrine details.bm-section details{margin-top:4px}
            .bm-plans-vitrine .bm-summary{color:var(--muted);font-size:.84rem;margin-bottom:2px}
            @media(max-width:680px){.bm-plans-vitrine{margin:20px 0;padding:17px;border-radius:20px}
                .bm-plans-vitrine h2{font-size:1.6rem}.bm-plans-vitrine .bm-card{padding:16px}}
            </style>
            <p class="bm-eyebrow">Escolha seu jeito de criar</p>
            <h2>Planos Brother Matrizes</h2>
            <p>Crie, edite e gerencie matrizes no celular. Escolha a opção ideal para você.</p>
            <div class="bm-grid">
                <article class="bm-card">
                    <p class="bm-eyebrow">Comece a criar</p>
                    <h3>Gratuito</h3>
                    <p class="bm-price">Grátis</p>
                    <p>Crie, edite e salve seus projetos.</p>
                    <ul>
                        <li>3 fontes e 5 matrizes por mês</li>
                        <li>Teste PRO de 7 dias para contas elegíveis</li>
                    </ul>
                    <a class="bm-action" href="<?php echo esc_url(home_url('/aplicativos/brother-matrizes/')); ?>">Baixar grátis</a>
                </article>
                <article class="bm-card">
                    <p class="bm-eyebrow">Mais produtividade</p>
                    <h3>PRO</h3>
                    <p>Importações sem a cota mensal do Gratuito.</p>
                    <ul>
                        <li>Recursos PRO incluídos</li>
                        <li>Escolha mensal ou anual</li>
                    </ul>
                    <div class="bm-plan-variants" aria-label="Contratação PRO">
                        <?php foreach (array('monthly' => 'Mensal', 'yearly' => 'Anual') as $code => $label):
                            $offer = $catalog[$code]; ?>
                            <div class="bm-plan-variant">
                                <span><?php echo esc_html($label); ?>
                                    <strong><?php echo $offer['product'] ? wp_kses_post($offer['price']) : 'Consultar site'; ?></strong>
                                </span>
                                <?php if ($offer['valid']): ?>
                                    <a rel="nofollow" href="<?php echo esc_url($offer['url']); ?>">Selecionar ›</a>
                                <?php else: ?>
                                    <span>Indisponível</span>
                                <?php endif; ?>
                            </div>
                        <?php endforeach; ?>
                    </div>
                    <?php if ($annual_savings !== null): ?>
                        <p class="bm-stock">Economize <?php echo wp_kses_post($annual_savings); ?> frente a 12 mensalidades*.</p>
                    <?php endif; ?>
                </article>
                <article class="bm-card bm-featured">
                    <p class="bm-eyebrow">Pagamento único</p>
                    <h3>PRO Vitalício</h3>
                    <p>Recursos PRO cobertos pela licença, sem mensalidades.</p>
                    <ul>
                        <li>Mesma categoria de acesso PRO</li>
                        <li>Escolha o preço convencional ou a promoção vigente</li>
                    </ul>
                    <div class="bm-plan-variants" aria-label="Compra de licença vitalícia">
                        <?php foreach (array('lifetime' => 'Vitalício', 'launch' => 'Lançamento') as $code => $label):
                            $offer = $catalog[$code];
                            $is_launch = $code === 'launch'; ?>
                            <div class="bm-plan-variant">
                                <span><?php echo esc_html($label); ?>
                                    <strong><?php echo $offer['product'] ? wp_kses_post($offer['price']) : 'Consultar site'; ?></strong>
                                </span>
                                <?php if ($offer['valid']): ?>
                                    <a rel="nofollow" href="<?php echo esc_url($offer['url']); ?>">Selecionar ›</a>
                                <?php else: ?>
                                    <span>Indisponível</span>
                                <?php endif; ?>
                            </div>
                            <?php if ($is_launch && $offer['valid'] && is_int($offer['stock']) && $offer['stock'] > 0): ?>
                                <p class="bm-stock"><?php echo esc_html(number_format_i18n($offer['stock'])); ?> licenças disponíveis no estoque consultado</p>
                            <?php endif; ?>
                        <?php endforeach; ?>
                    </div>
                </article>
            </div>

            <details class="bm-section">
                <summary>Comparar recursos</summary>
                <p>Gratuito x PRO: a principal diferença é o limite mensal de importações.</p>
                <div class="bm-table-scroll" role="region" aria-label="Comparativo de recursos" tabindex="0">
                    <table><thead><tr><th>Recurso</th><th>Gratuito</th><th>PRO (Mensal, Anual e Vitalício)</th></tr></thead><tbody>
                        <tr><td>Criação e edição de trabalhos</td><td>Disponível</td><td>Disponível</td></tr>
                        <tr><td>Texto e personalização de bordados</td><td>Disponível</td><td>Disponível</td></tr>
                        <tr><td>Biblioteca local de projetos</td><td>Disponível</td><td>Disponível</td></tr>
                        <tr><td>Fontes TTF/OTF e arquivos ZIP</td><td>Até 3 fontes por mês</td><td>Sem a cota mensal do Gratuito</td></tr>
                        <tr><td>Importações de matrizes</td><td>Até 5 por mês</td><td>Sem a cota mensal do Gratuito</td></tr>
                        <tr><td>Arquivos de bordado aceitos pelo aplicativo</td><td>Conforme formato</td><td>Conforme formato</td></tr>
                        <tr><td>Editor, visualização e simulação</td><td>Disponível</td><td>Disponível</td></tr>
                        <tr><td>Exportar e compartilhar trabalhos</td><td>Conforme formato</td><td>Conforme formato</td></tr>
                        <tr><td>Atualizações oficiais</td><td>Disponíveis</td><td>Disponíveis</td></tr>
                        <tr><td>Testes Beta antecipados</td><td colspan="2">Programa futuro, sem direito contratual garantido</td></tr>
                        <tr><td>Conversor EMB/PES</td><td>Em desenvolvimento</td><td>Em desenvolvimento</td></tr>
                    </tbody></table>
                </div>
            </details>
            <details class="bm-section">
                <summary>Por que escolher o Vitalício?</summary>
                <p>É uma alternativa para trabalhar com os recursos PRO incluídos na licença sem mensalidades recorrentes. A modalidade promocional oferece a mesma categoria de acesso, enquanto houver estoque.</p>
                <h4>Tenha prioridade nas próximas novidades</h4>
                <p>Está em planejamento oferecer participação prioritária em experiências Beta e oportunidades de conhecer recursos antecipadamente. A participação será voluntária e dependerá de critérios técnicos, vagas e regras divulgadas quando o programa existir. Não é benefício garantido na compra atual.</p>
            </details>
            <details class="bm-section">
                <summary>Perguntas frequentes</summary>
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
            </details>
            <p class="bm-summary">*Preços e disponibilidade confirmados no checkout do WooCommerce. Mensal e anual sujeitos às condições de contratação.</p>
            <p class="bm-actions-bottom">Já comprou? <a href="<?php echo esc_url(home_url('/minha-conta/meus-aplicativos/')); ?>">Consulte suas licenças</a>. Após pagar, volte ao aplicativo e toque em <strong>Verificar licença</strong>. Nenhuma funcionalidade PRO é liberada apenas pelo retorno do navegador.</p>
        </section>
        <?php
        return ob_get_clean();
    }

    add_shortcode('brother_matrizes_planos', 'bm_planos_display_shortcode');
}
