<?php
/**
 * Central oficial de comunidade do Brother Matrizes.
 *
 * Independente do App Commerce Core e do sistema de download/assinatura.
 */
if (!defined('ABSPATH')) { exit; }

function tim_brother_community_group_url() {
    $url = trim((string) get_option('tim_brother_community_group_url', 'https://chat.whatsapp.com/BXbLoaQXC9dFIJdEzwmwB2'));
    return preg_match('~^https://chat\.whatsapp\.com/[A-Za-z0-9]{16,32}/?$~D', $url)
        ? $url : '';
}

add_action('admin_init', function() {
    register_setting('tim_brother_community', 'tim_brother_community_group_url', array(
        'type' => 'string',
        'default' => 'https://chat.whatsapp.com/BXbLoaQXC9dFIJdEzwmwB2',
        'sanitize_callback' => function($value) {
            $url = is_string($value) ? trim($value) : '';
            if ($url === '' || preg_match('~^https://chat\.whatsapp\.com/[A-Za-z0-9]{16,32}/?$~D', $url)) {
                return $url;
            }
            add_settings_error('tim_brother_community_group_url', 'invalid_group',
                'Use apenas o link oficial de convite https://chat.whatsapp.com/... .');
            return (string) get_option('tim_brother_community_group_url', 'https://chat.whatsapp.com/BXbLoaQXC9dFIJdEzwmwB2');
        },
    ));
});

add_action('admin_menu', function() {
    add_management_page('Brother Matrizes — Comunidade', 'Brother — Comunidade',
        'manage_options', 'tim-brother-community', function() {
            if (!current_user_can('manage_options')) { return; }
            echo '<div class="wrap"><h1>Brother Matrizes — Comunidade</h1>';
            echo '<p>Configure aqui o convite oficial do grupo de feedbacks do WhatsApp. Nenhum valor, pedido ou licença é modificado.</p>';
            echo '<form method="post" action="' . esc_url(admin_url('options.php')) . '">';
            settings_fields('tim_brother_community');
            echo '<table class="form-table"><tr><th scope="row"><label for="bm_group">Link do grupo</label></th><td>';
            echo '<input type="url" id="bm_group" class="regular-text" name="tim_brother_community_group_url" value="'
                . esc_attr(tim_brother_community_group_url()) . '" placeholder="https://chat.whatsapp.com/..." />';
            echo '<p class="description">Convite do grupo WhatsApp; os participantes podem visualizar números uns dos outros. Nunca compartilhe senhas ou dados de pagamento.</p></td></tr></table>';
            submit_button('Salvar grupo oficial');
            echo '</form><p>Para mostrar a central na página do produto, adicione <code>[brother_matrizes_community]</code> uma única vez.</p>';
            echo '<p>As sugestões recebidas ficam na área <strong>Brother — Feedbacks</strong> deste painel, sem publicação automática.</p></div>';
        });
});

add_action('init', function() {
    register_post_type('tim_brother_feedback', array(
        'labels' => array('name' => 'Brother — Feedbacks', 'singular_name' => 'Feedback Brother'),
        'public' => false,
        'publicly_queryable' => false,
        'exclude_from_search' => true,
        'show_ui' => true,
        'show_in_menu' => 'tools.php',
        'show_in_rest' => false,
        'supports' => array('title', 'editor'),
        'capability_type' => 'post',
    ));
});

add_action('rest_api_init', function() {
    register_rest_route('tim-brother-sync/v1', '/community', array(
        'methods' => WP_REST_Server::READABLE,
        'permission_callback' => '__return_true',
        'callback' => function() {
            return rest_ensure_response(array(
                'groupUrl' => tim_brother_community_group_url(),
                'communityUrl' => TIM_BROTHER_SYNC_RELEASE_PAGE . '#brother-community',
                'feedbackUrl' => TIM_BROTHER_SYNC_RELEASE_PAGE . '#brother-feedback',
            ));
        },
    ));
});

add_shortcode('brother_matrizes_community', function() {
    $url = tim_brother_community_group_url();
    $release = tim_brother_sync_latest();
    $state = isset($_GET['bm_feedback']) && is_scalar($_GET['bm_feedback'])
        ? sanitize_key(wp_unslash($_GET['bm_feedback'])) : '';
    ob_start();
    ?>
    <section id="brother-community" class="tim-brother-community" aria-label="Comunidade Brother Matrizes">
      <p class="bm-eyebrow">BROTHER MATRIZES · T.I. MACHADO</p>
      <h2>Novidades e comunidade</h2>
      <p>Acompanhe a evolução do aplicativo, participe das decisões e compartilhe sugestões para as próximas versões.</p>
      <div class="bm-panels">
        <div class="bm-panel">
          <h3>Atualizações transparentes</h3>
          <?php if ($release) : ?>
            <p><strong>Versão publicada: <?php echo esc_html($release['versionName']); ?></strong></p>
            <p><?php echo nl2br(esc_html($release['releaseNotes'] ?: 'Consulte as novidades desta versão.')); ?></p>
          <?php else : ?>
            <p>Confira as notas de versão e as melhorias publicadas do Brother Matrizes.</p>
          <?php endif; ?>
          <a href="<?php echo esc_url(TIM_BROTHER_SYNC_RELEASE_PAGE); ?>">Página do aplicativo</a>
        </div>
        <div class="bm-panel">
          <h3>Grupo oficial de feedbacks no WhatsApp</h3>
          <p>Participe das conversas, relate erros e ajude a escolher melhorias. A entrada é gratuita e sujeita à moderação.</p>
          <p>As mensagens ficam visíveis aos membros. Ao participar, seu número de telefone pode ser visto pelos demais participantes. Evite compartilhar dados pessoais, senhas e documentos.</p>
          <?php if ($url) : ?>
            <a href="<?php echo esc_url($url); ?>" rel="noopener noreferrer" target="_blank">Entrar no grupo de feedbacks</a>
          <?php else : ?>
            <p data-bm-group-pending><strong>Grupo em preparação.</strong> O acesso será liberado nesta página quando estiver disponível.</p>
            <a data-bm-group-link hidden href="#" rel="noopener noreferrer" target="_blank">Entrar no grupo de feedbacks</a>
            <script>
              (function(){
                var root=document.getElementById('brother-community');
                if(!root||!window.fetch)return;
                var action=root.querySelector('[data-bm-group-link]');
                var pending=root.querySelector('[data-bm-group-pending]');
                if(!action||!pending)return;
                fetch('https://raw.githubusercontent.com/timachado/Brother-Matrizes/main/updates/community.json',{cache:'no-store'})
                  .then(function(r){if(!r.ok)throw Error('offline');return r.json();})
                  .then(function(data){
                    if(data.product!=='brother-matrizes'||typeof data.groupUrl!=='string')return;
                    if(!/^https:\/\/chat\.whatsapp\.com\/[A-Za-z0-9]{16,32}\/?$/.test(data.groupUrl))return;
                    action.href=data.groupUrl;
                    action.hidden=false;
                    pending.hidden=true;
                  }).catch(function(){/* mantém acesso indisponível, sem link falso */});
              })();
            </script>
          <?php endif; ?>
        </div>
      </div>
      <p class="bm-investment">A compra e a assinatura remuneram o acesso aos recursos contratados e o trabalho de manutenção e evolução do software. Novos recursos serão anunciados quando estiverem realmente disponíveis.</p>
      <div id="brother-feedback" class="bm-feedback">
        <h3>Ajude a melhorar o Brother Matrizes</h3>
        <p>Envie uma ideia, relato de problema ou elogio. Não envie senhas, dados de pagamento ou arquivos pessoais.</p>
        <?php if ($state === 'ok') : ?><p role="status" class="bm-success">Feedback recebido. Obrigado por contribuir!</p><?php endif; ?>
        <?php if ($state === 'error') : ?><p role="alert" class="bm-error">Não foi possível receber o feedback. Revise os campos ou tente novamente mais tarde.</p><?php endif; ?>
        <form method="post" action="<?php echo esc_url(admin_url('admin-post.php')); ?>">
          <input type="hidden" name="action" value="tim_brother_feedback" />
          <?php wp_nonce_field('tim_brother_feedback', 'tim_brother_feedback_nonce'); ?>
          <label for="bm-kind">Tipo de feedback</label>
          <select id="bm-kind" name="bm_kind" required>
            <option value="sugestao">Sugestão de melhoria</option>
            <option value="erro">Informar um problema</option>
            <option value="elogio">Elogio / experiência de uso</option>
          </select>
          <label for="bm-message">Sua mensagem</label>
          <textarea id="bm-message" name="bm_message" minlength="10" maxlength="2500" rows="5" required placeholder="Conte o que precisa melhorar ou o que você gostaria de ver..."></textarea>
          <label for="bm-email">E-mail para resposta (opcional)</label>
          <input type="email" id="bm-email" name="bm_email" maxlength="150" autocomplete="email" />
          <label class="bm-consent"><input type="checkbox" name="bm_consent" value="1" /> Autorizo a T.I. Machado a utilizar meu e-mail para responder a este feedback.</label>
          <div class="bm-honeypot" aria-hidden="true"><label for="bm-website">Deixe vazio</label><input id="bm-website" name="bm_website" type="text" tabindex="-1" autocomplete="off" /></div>
          <button type="submit">Enviar feedback</button>
        </form>
      </div>
    </section>
    <style>
      .tim-brother-community{background:linear-gradient(140deg,#101c30,#18263d);color:#f1f5f9;border:1px solid #d5af6760;border-radius:22px;padding:clamp(18px,4vw,30px);margin:24px 0}
      .tim-brother-community h2,.tim-brother-community h3{color:#fff;line-height:1.3}
      .tim-brother-community h2{font-size:clamp(24px,3vw,30px);margin:.4em 0}
      .tim-brother-community h3{font-size:19px;margin:0 0 10px}
      .tim-brother-community p{line-height:1.6;color:#d6deea}
      .tim-brother-community .bm-eyebrow{font-size:11px;letter-spacing:1px;font-weight:800;color:#eac783}
      .tim-brother-community .bm-panels{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,255px),1fr));gap:14px;margin:22px 0}
      .tim-brother-community .bm-panel{background:#24334b;border:1px solid #ffffff20;border-radius:14px;padding:20px}
      .tim-brother-community a,.tim-brother-community button{display:inline-block;background:#e1bd79;color:#16243b!important;padding:11px 16px;border:0;border-radius:11px;font-size:14px;font-weight:750;text-decoration:none;cursor:pointer}
      .tim-brother-community .bm-investment{border-left:3px solid #e1bd79;padding-left:14px}
      .tim-brother-community .bm-feedback{margin-top:25px;border-top:1px solid #ffffff33;padding-top:22px}
      .tim-brother-community form{display:grid;gap:10px;max-width:660px}
      .tim-brother-community label{font-size:14px;font-weight:600;color:#eff2f6}
      .tim-brother-community textarea,.tim-brother-community input[type=email],.tim-brother-community select{display:block;width:100%;box-sizing:border-box;background:#f9fafb;color:#152234;border:1px solid #aebbc8;border-radius:9px;padding:12px;font-size:16px}
      .tim-brother-community .bm-consent{font-size:13px;font-weight:400;display:flex;align-items:flex-start;gap:8px}
      .tim-brother-community .bm-honeypot{position:absolute;left:-9999px}
      .tim-brother-community [hidden]{display:none!important}
      .tim-brother-community .bm-success{color:#b8f3d0}.tim-brother-community .bm-error{color:#ffb7b7}
    </style>
    <?php
    return ob_get_clean();
});

function tim_brother_receive_feedback() {
    if (strtoupper((string) ($_SERVER['REQUEST_METHOD'] ?? '')) !== 'POST') {
        wp_die('Método inválido.', '', array('response' => 405));
    }
    $nonce = isset($_POST['tim_brother_feedback_nonce']) && is_string($_POST['tim_brother_feedback_nonce'])
        ? sanitize_text_field(wp_unslash($_POST['tim_brother_feedback_nonce'])) : '';
    if (!$nonce || !wp_verify_nonce($nonce, 'tim_brother_feedback')) {
        wp_die('Formulário expirado. Recarregue a página.', '', array('response' => 403));
    }
    $target = wp_get_referer();
    if (!$target || strpos($target, home_url('/')) !== 0) { $target = TIM_BROTHER_SYNC_RELEASE_PAGE; }
    $go = function($result) use ($target) {
        wp_safe_redirect(add_query_arg('bm_feedback', $result, $target) . '#brother-feedback', 303);
        exit;
    };
    if (!empty($_POST['bm_website'])) { $go('ok'); }
    $key = 'tim_brother_feedback_rate_' . substr(hash_hmac('sha256',
        (string) ($_SERVER['REMOTE_ADDR'] ?? 'unknown'), wp_salt('nonce')), 0, 32);
    $count = (int) get_transient($key);
    if ($count >= 3) { $go('error'); }
    $kind = isset($_POST['bm_kind']) && is_string($_POST['bm_kind'])
        ? sanitize_key(wp_unslash($_POST['bm_kind'])) : '';
    $allowed = array('sugestao' => 'Sugestão', 'erro' => 'Problema', 'elogio' => 'Elogio');
    $message = isset($_POST['bm_message']) && is_string($_POST['bm_message'])
        ? sanitize_textarea_field(wp_unslash($_POST['bm_message'])) : '';
    $email = isset($_POST['bm_email']) && is_string($_POST['bm_email'])
        ? sanitize_email(wp_unslash($_POST['bm_email'])) : '';
    $consent = isset($_POST['bm_consent']) && $_POST['bm_consent'] === '1';
    if (!isset($allowed[$kind]) || strlen($message) < 10 || strlen($message) > 10000 ||
        ($email !== '' && (!is_email($email) || !$consent))) { $go('error'); }
    $post_id = wp_insert_post(array(
        'post_type' => 'tim_brother_feedback',
        'post_status' => 'private',
        'post_title' => $allowed[$kind] . ' — ' . current_time('d/m/Y H:i'),
        'post_content' => $message,
        'post_author' => 0,
    ), true);
    if (is_wp_error($post_id) || !$post_id) { $go('error'); }
    if ($email && $consent) { update_post_meta($post_id, '_tim_brother_contact_email', $email); }
    set_transient($key, $count + 1, HOUR_IN_SECONDS);
    $go('ok');
}
add_action('admin_post_tim_brother_feedback', 'tim_brother_receive_feedback');
add_action('admin_post_nopriv_tim_brother_feedback', 'tim_brother_receive_feedback');


// Permite ao administrador ler o e-mail opcional sem expô-lo publicamente.
add_action('add_meta_boxes_tim_brother_feedback', function() {
    add_meta_box('tim_brother_feedback_contact', 'Contato autorizado', function($post) {
        if (!current_user_can('edit_post', $post->ID)) { return; }
        $email = (string) get_post_meta($post->ID, '_tim_brother_contact_email', true);
        echo '<p>' . ($email !== '' ? esc_html($email) : 'Não informado.') . '</p>';
        echo '<p>Exclua feedbacks antigos periodicamente para limitar a retenção de dados pessoais.</p>';
    }, 'tim_brother_feedback', 'side', 'default');
});
