<?php
if (!defined('ABSPATH')) { exit; }

/**
 * App Commerce Core (campos da versão 2.6.6 conferidos no código original).
 * Lê os campos de versão/novidades diretamente do manifesto. O filtro não
 * grava no banco, não mexe no checkout e nunca retorna metadados de outro app.
 *
 * No modo "free", o Core consulta _ti_app_apk_attachment_id primeiro.
 * Substituímos a leitura desse ID por 0 somente quando a URL oficial pública
 * estiver validada; então o Core usa _ti_app_download e seu fluxo de botão.
 * No modo "licensed", NUNCA redireciona nem expõe uma URL pública pelo Core.
 * A hospedagem pública de assets no GitHub é decisão independente do WordPress.
 */
function tim_brother_sync_core_metadata($value, $object_id, $meta_key, $single, $meta_type = 'post') {
    if (null !== $value || $meta_type !== 'post'
        || (int) $object_id !== TIM_BROTHER_SYNC_APP_POST_ID
        || !is_string($meta_key)
        || !in_array($meta_key, array(
            '_ti_app_version', '_ti_app_release_notes',
            '_ti_app_apk_attachment_id', '_ti_app_download',
        ), true)) {
        return $value;
    }
    if (get_post_type((int) $object_id) !== 'ti_app') { return $value; }

    // Consultar uma única fonte; se não houver manifesto válido, deixar o Core
    // trabalhar com os metadados já armazenados, sem substituir downloads.
    $release = tim_brother_sync_latest();
    if (!$release) { return $value; }
    $new_value = null;
    if ($meta_key === '_ti_app_version') {
        $new_value = $release['versionName'];
    } elseif ($meta_key === '_ti_app_release_notes') {
        $new_value = $release['releaseNotes'];
    } else {
        // Acesso ao download não é modificado por este plugin. Modo de licença
        // ativa continua estritamente gerenciado pelo App Commerce Core.
        $access = get_post_meta(TIM_BROTHER_SYNC_APP_POST_ID, '_ti_app_download_access', true);
        if ($access !== 'free' || empty($release['downloadApkUrl'])) { return $value; }
        if ($meta_key === '_ti_app_apk_attachment_id') {
            $new_value = '0'; // não servir o attachment antigo (1.10.0)
        } elseif ($meta_key === '_ti_app_download') {
            $new_value = $release['downloadApkUrl'];
        }
    }
    if ($new_value === null) { return $value; }
    // Contrato do filtro get_post_metadata: um valor escalar para single,
    // um array de valores para chamadas não-single.
    return $single ? $new_value : array($new_value);
}
add_filter('get_post_metadata', 'tim_brother_sync_core_metadata', 8, 5);
