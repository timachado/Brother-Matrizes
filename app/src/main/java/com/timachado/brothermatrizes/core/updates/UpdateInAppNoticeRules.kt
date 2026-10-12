package com.timachado.brothermatrizes.core.updates

/**
 * Decide quando a Central de Novidades pode aparecer na Home.
 *
 * Os avisos push/WorkManager continuam independentes deste cartão.
 * O usuário pode dispensar a versão atual, mas uma versão nova reaparece.
 */
internal object UpdateInAppNoticeRules {
    fun shouldShow(
        installedCode: Long,
        publishedCode: Long,
        dismissedVersionCode: Long
    ): Boolean =
        installedCode > 0L &&
            publishedCode > installedCode &&
            dismissedVersionCode != publishedCode

    fun notes(text: String): String =
        text.trim().take(1400)
}
