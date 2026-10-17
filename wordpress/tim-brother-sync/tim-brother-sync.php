<?php
/**
 * Plugin Name: Brother Matrizes — Atualizações Automáticas
 * Description: Espelha somente os metadados oficiais de atualização do Brother Matrizes no WordPress. Não altera WooCommerce, licenças, pedidos ou pagamentos.
 * Version: 1.1.1
 * Author: T.I. Machado
 * Text Domain: tim-brother-sync
 */

if (!defined('ABSPATH')) { exit; }

define('TIM_BROTHER_SYNC_MANIFEST_URL', 'https://raw.githubusercontent.com/timachado/Brother-Matrizes/main/updates/stable.json');
define('TIM_BROTHER_SYNC_RELEASE_PAGE', 'https://timachado.ifree.page/aplicativos/brother-matrizes/');
// ID confirmado no cadastro do App Commerce Core. Nao afeta outros aplicativos.
define('TIM_BROTHER_SYNC_APP_POST_ID', 307);

/**
 * Verificar metadados no GitHub a cada 3 minutos; nunca seguir redirects nem
 * aceitar um pacote/host diferente. Nada é atualizado no WooCommerce.
 *
 * @return array<string,mixed>|null
 */
function tim_brother_sync_latest() {
    $cache = get_transient('tim_brother_sync_latest_v2');
    if (is_array($cache) && !empty($cache['versionCode'])) {
        return $cache;
    }

    $response = wp_remote_get(TIM_BROTHER_SYNC_MANIFEST_URL, array(
        'timeout' => 9,
        'redirection' => 0,
        'headers' => array('Accept' => 'application/json', 'Cache-Control' => 'no-cache'),
        'limit_response_size' => 65536,
    ));
    if (is_wp_error($response) || wp_remote_retrieve_response_code($response) !== 200) {
        return null;
    }

    $data = json_decode(wp_remote_retrieve_body($response), true);
    if (!is_array($data)) { return null; }
    if (($data['applicationId'] ?? '') !== 'com.timachado.brothermatrizes'
        || ($data['channel'] ?? '') !== 'stable') { return null; }

    $version = (string) ($data['versionName'] ?? '');
    $code = $data['versionCode'] ?? null;
    $hash = strtolower((string) ($data['sha256'] ?? ''));
    if (!preg_match('/^[0-9]+\.[0-9]+\.[0-9]+$/D', $version)
        || !is_numeric($code) || (int) $code <= 0
        || !preg_match('/^[0-9a-f]{64}$/D', $hash)) { return null; }
    // Aceita ambos contratos do manifesto: legado (downloadUrl = pagina WordPress)
    // e OTA comercial direta (downloadUrl = APK assinado no GitHub).
    // Nunca aceitar host inesperado nem reencaminhar o Android para pagina HTML.
    $download = (string) ($data['downloadUrl'] ?? '');
    $apk = (string) ($data['downloadApkUrl'] ?? '');
    if ($apk === '' && $download !== TIM_BROTHER_SYNC_RELEASE_PAGE) {
        $apk = $download;
    }
    if ($download !== TIM_BROTHER_SYNC_RELEASE_PAGE && $download !== $apk) {
        return null;
    }
    if ($apk !== '') {
        $expected_prefix = 'https://github.com/timachado/Brother-Matrizes/releases/download/v' . $version . '/';
        $valid_prefix = strncmp($apk, $expected_prefix, strlen($expected_prefix)) === 0;
        $valid_file = preg_match('/\/[A-Za-z0-9._-]+\.apk$/D', $apk);
        if (!$valid_prefix || !$valid_file || strlen($apk) > 512) { return null; }
    }

    $result = array(
        'applicationId' => 'com.timachado.brothermatrizes',
        'channel' => 'stable',
        'versionName' => $version,
        'versionCode' => (int) $code,
        'minSupportedVersionCode' => max(0, min((int) ($data['minSupportedVersionCode'] ?? 0), (int) $code)),
        'downloadUrl' => $apk !== '' ? $apk : TIM_BROTHER_SYNC_RELEASE_PAGE,
        'sha256' => $hash,
        'releaseNotes' => function_exists('mb_substr') ? mb_substr((string) ($data['releaseNotes'] ?? ''), 0, 3500) : substr((string) ($data['releaseNotes'] ?? ''), 0, 3500),
        'publishedAt' => (string) ($data['publishedAt'] ?? ''),
    );
    if ($apk !== '') { $result['downloadApkUrl'] = $apk; }
    set_transient('tim_brother_sync_latest_v2', $result, 3 * MINUTE_IN_SECONDS);
    return $result;
}

/**
 * Mantém o endpoint já utilizado pelo aplicativo, sem substituir plugin ou
 * modificar tabelas/usuários do WordPress. Se GitHub não responder, libera o
 * endpoint antigo para atender normalmente.
 */
add_filter('rest_pre_dispatch', function($result, $server, $request) {
    if (null !== $result || !($request instanceof WP_REST_Request)) { return $result; }
    if ($request->get_route() !== '/tim-brother-updates/v1/latest') { return $result; }
    if ($request->get_method() !== 'GET' || $request->get_param('channel') !== 'stable') {
        return $result;
    }
    $release = tim_brother_sync_latest();
    if (!$release) { return $result; }
    return new WP_REST_Response($release, 200);
}, 9, 3);

/** Endpoint de diagnóstico não intrusivo para a integração, somente leitura. */
add_action('rest_api_init', function() {
    register_rest_route('tim-brother-sync/v1', '/latest', array(
        'methods' => WP_REST_Server::READABLE,
        'permission_callback' => '__return_true',
        'callback' => function() {
            $release = tim_brother_sync_latest();
            return $release
                ? new WP_REST_Response($release, 200)
                : new WP_Error('tim_brother_sync_offline', 'Atualização indisponível temporariamente.', array('status' => 503));
        },
    ));
});

/** Ligação isolada do App Commerce Core, sem modificar o plugin comercial. */
require_once __DIR__ . '/includes/app-commerce-core.php';

/**
 * Bloco opcional para a página do aplicativo.
 * Adicionar UMA vez [brother_matrizes_release] ao conteúdo da página.
 * O JavaScript lê o GitHub pelo navegador e se mantém atualizado mesmo que
 * a hospedagem bloqueie chamadas PHP externas.
 *
 * Por padrão o link do botão leva à página comercial já existente; apenas
 * [brother_matrizes_release direct="yes"] usa o link direto público.
 */
add_shortcode('brother_matrizes_release', function($atts) {
    $atts = shortcode_atts(array('direct' => 'no'), $atts, 'brother_matrizes_release');
    $direct = ($atts['direct'] === 'yes') ? '1' : '0';
    $id = 'tim-brother-release-' . wp_rand(100000, 999999);
    $page = esc_url(TIM_BROTHER_SYNC_RELEASE_PAGE);
    ob_start();
    ?>
    <section id="<?php echo esc_attr($id); ?>" class="tim-brother-release" data-direct="<?php echo esc_attr($direct); ?>">
      <div class="tim-brother-release-head">
        <span class="tim-brother-release-label">BROTHER MATRIZES · T.I. MACHADO</span>
        <span class="tim-brother-release-version" data-version>Atualizações</span>
      </div>
      <h3>Novidades do Brother Matrizes</h3>
      <p class="tim-brother-release-desc" data-summary>Confira a versão mais recente e as melhorias.</p>
      <a class="tim-brother-release-action" data-link href="<?php echo $page; ?>">Ver aplicativo</a>
    </section>
    <style>
      .tim-brother-release{background:linear-gradient(125deg,#112039,#17273a 74%);color:#f5f6f7;border:1px solid #9f864b66;border-radius:20px;padding:22px 23px;margin:20px 0;box-shadow:0 14px 35px #08102018}
      .tim-brother-release-head{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}
      .tim-brother-release-label{font-size:11px;font-weight:800;letter-spacing:1px;color:#dfbd79}
      .tim-brother-release-version{font-size:12px;font-weight:700;color:#dfbd79;background:#d3af6b16;border:1px solid #dfbd7950;border-radius:50px;padding:5px 11px}
      .tim-brother-release h3{font-size:21px;line-height:1.2;font-weight:750;color:#fff;margin:17px 0 8px}
      .tim-brother-release-desc{color:#d4dce5;font-size:14px;white-space:pre-line;line-height:1.55;margin-bottom:15px}
      .tim-brother-release-action{display:inline-block;background:#e2be7f;color:#16233a!important;border-radius:12px;font-size:13px;font-weight:750;text-decoration:none;padding:11px 18px}
    </style>
    <script>
      (function() {
        var root=document.getElementById(<?php echo wp_json_encode($id); ?>);
        if (!root || !window.fetch) return;
        fetch(<?php echo wp_json_encode(TIM_BROTHER_SYNC_MANIFEST_URL); ?>,{cache:'no-store'})
          .then(function(r){if(!r.ok)throw Error('offline');return r.json();})
          .then(function(d){
            if(d.applicationId!=='com.timachado.brothermatrizes'||d.channel!=='stable'
              ||!(/^[0-9]+\.[0-9]+\.[0-9]+$/).test(d.versionName)||!Number.isInteger(d.versionCode))return;
            var v=root.querySelector('[data-version]');
            var sum=root.querySelector('[data-summary]');
            var link=root.querySelector('[data-link]');
            v.textContent='v'+d.versionName;
            if(typeof d.releaseNotes==='string'){
              sum.textContent=d.releaseNotes.slice(0,650);
            }
            if(root.getAttribute('data-direct')==='1' && typeof d.downloadApkUrl==='string'
              && /^https:\/\/github\.com\/timachado\/Brother-Matrizes\/releases\/download\/v[0-9.]+\/[\w.-]+\.apk$/.test(d.downloadApkUrl)){
              link.href=d.downloadApkUrl;
              link.textContent='Baixar APK oficial';
            }else{
              link.textContent='Ver aplicativo';
            }
          }).catch(function(){/* mantém link comercial confiável, sem erro técnico */});
      })();
    </script>
    <?php
    return ob_get_clean();
});


/**
 * Fallback APENAS no frontend da página comercial do app ID 307.
 * O navegador lê o GitHub diretamente, mesmo quando o PHP da hospedagem
 * não alcança raw.githubusercontent.com. Não altera metas, planos, preços,
 * checkout, licenças ou o botão existente de download.
 * Aplicado uma única vez; releases futuras mudam só no GitHub.
 */
add_action('wp_footer', function() {
    if (!is_singular('ti_app') || (int) get_queried_object_id() !== TIM_BROTHER_SYNC_APP_POST_ID) {
        return;
    }
    ?>
    <script id="tim-brother-sync-site-v111">
    (function() {
      'use strict';
      if (!window.fetch) return;
      var url='https://raw.githubusercontent.com/timachado/Brother-Matrizes/main/updates/stable.json';
      fetch(url, {cache:'no-store'})
        .then(function(r) { if (!r.ok) throw Error('offline'); return r.json(); })
        .then(function(d) {
          if (!d || d.applicationId !== 'com.timachado.brothermatrizes'
              || d.channel !== 'stable'
              || typeof d.versionName !== 'string'
              || !/^[0-9]+\.[0-9]+\.[0-9]+$/.test(d.versionName)
              || !Number.isSafeInteger(d.versionCode) || d.versionCode <= 0
              || typeof d.sha256 !== 'string' || !/^[0-9a-f]{64}$/i.test(d.sha256)) return;
          var apk = d.downloadApkUrl || d.downloadUrl;
          var prefix = 'https://github.com/timachado/Brother-Matrizes/releases/download/v' + d.versionName + '/';
          if (typeof apk !== 'string' || apk.indexOf(prefix) !== 0
              || !/\/[A-Za-z0-9._-]+\.apk$/.test(apk) || apk.length > 512) return;
          var version = d.versionName;
          var date = '';
          if (typeof d.publishedAt === 'string' && d.publishedAt) {
            var parsed = new Date(d.publishedAt);
            if (!isNaN(parsed.getTime())) {
              date = new Intl.DateTimeFormat('pt-BR', {timeZone:'America/Sao_Paulo'}).format(parsed);
            }
          }
          document.querySelectorAll('.ti-app-meta .ti-tag').forEach(function(el) {
            var label = el.textContent.trim();
            if (/^Versão\s/i.test(label)) el.textContent = 'Versão ' + version;
            if (date && /^Atualizado\s/i.test(label)) el.textContent = 'Atualizado ' + date;
          });
          document.querySelectorAll('.ti-app-info-grid > div').forEach(function(el) {
            var field = el.querySelector('span');
            var value = el.querySelector('b');
            if (!field || !value) return;
            var label = field.textContent.trim();
            if (label === 'Versão atual') value.textContent = version;
            if (date && label === 'Última atualização') value.textContent = date;
          });
          var changelog = document.querySelector('.ti-app-changelog');
          if (changelog) {
            var title = changelog.querySelector('.ti-kicker');
            if (title) title.textContent = 'Novidades da versão ' + version;
            var copy = Array.prototype.filter.call(changelog.children, function(el) {
              return el.tagName === 'DIV' && !el.classList.contains('ti-kicker');
            })[0];
            if (copy && typeof d.releaseNotes === 'string' && d.releaseNotes.trim()) {
              copy.textContent = d.releaseNotes.slice(0, 3500);
              copy.style.whiteSpace = 'pre-line';
            }
          }
          // O botão de download comercial permanece sob controle do App Commerce Core.
          // Quando o PHP consegue consultar o GitHub, a integração em modo free
          // já disponibiliza o APK oficial pelo filtro de metadados do plugin.
        })
        .catch(function() {
          // Sem GitHub, preservar dados do site e compras sem falsa atualização.
        });
    })();
    </script>
    <?php
}, 20);

/**
 * Página de administração apenas informativa. Não modifica páginas, produtos,
 * licenças, configurações de pagamento ou valores do WooCommerce.
 */
add_action('admin_menu', function() {
    add_management_page('Brother Matrizes — Atualizações', 'Brother Matrizes', 'manage_options',
      'tim-brother-sync', function() {
        if (!current_user_can('manage_options')) { return; }
        $release = tim_brother_sync_latest();
        echo '<div class="wrap"><h1>Brother Matrizes — Sincronização automática</h1>';
        echo '<p>O WordPress consulta o manifesto estável do GitHub a cada três minutos e expõe as novidades no endpoint de atualizações existente.</p>';
        if ($release) {
            echo '<p><strong>Versão sincronizada:</strong> ' . esc_html($release['versionName'])
               . ' (código ' . esc_html((string) $release['versionCode']) . ')</p>';
        } else {
            echo '<p><strong>Conectividade:</strong> Não foi possível consultar o GitHub pelo servidor. O endpoint anterior foi preservado.</p>';
        }
        $core_app = get_post(TIM_BROTHER_SYNC_APP_POST_ID);
        if ($core_app instanceof WP_Post && $core_app->post_type === 'ti_app') {
            echo '<h2>App Commerce Core — integração automática</h2>';
            echo '<p>Cadastro identificado: Brother Matrizes (ID ' . esc_html((string) TIM_BROTHER_SYNC_APP_POST_ID) . '). Versão e notas sincronizadas sem alterar o banco de dados.</p>';
            $download_access = get_post_meta(TIM_BROTHER_SYNC_APP_POST_ID, '_ti_app_download_access', true);
            if ($release && $download_access === 'free' && !empty($release['downloadApkUrl'])) {
                echo '<p><strong>APK:</strong> link oficial direto do GitHub ativo no Core (modo gratuito/público). O APK antigo continua armazenado para reversão.</p>';
            } else {
                echo '<p><strong>APK:</strong> Core mantém o download e as restrições originais. Para acesso licenciado o plugin nunca substitui o arquivo.</p>';
            }
        } else {
            echo '<p><strong>App Commerce Core:</strong> cadastro 307 não encontrado. Integração de metadados desativada automaticamente.</p>';
        }
        echo '<p>Para exibir automaticamente as novidades na página do aplicativo, insira uma vez o shortcode: <code>[brother_matrizes_release]</code>.</p>';
        echo '<p>Esta integração não modifica produtos, licenças, pagamentos nem a página sem o shortcode.</p>';
        echo '</div>';
      });
});
