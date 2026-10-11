package com.timachado.brothermatrizes.core.updates

/** Evita notificações repetidas e nunca anuncia versões iguais ou inferiores. */
internal object UpdateNotificationRules {
    fun shouldNotify(
        installedCode: Long,
        publishedCode: Long,
        lastNotifiedCode: Long
    ): Boolean =
        UpdateVersionRules.isUpdateAvailable(installedCode, publishedCode) &&
            publishedCode > lastNotifiedCode
}
