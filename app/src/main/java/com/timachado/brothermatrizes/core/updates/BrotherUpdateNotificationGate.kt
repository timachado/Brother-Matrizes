package com.timachado.brothermatrizes.core.updates

import android.content.Context

/**
 * Evita que o aviso periódico e o Firebase emitam duas notificações para a
 * mesma versão quando ambos chegam quase ao mesmo tempo.
 *
 * Usa o mesmo registro persistente já empregado pela versão 1.10.2.
 * A exclusão mútua é local ao processo Android, sem tokens e sem servidor.
 */
internal object BrotherUpdateNotificationGate {
    private val lock = Any()

    fun postOnce(
        context: Context,
        installedCode: Long,
        publishedCode: Long,
        post: () -> Boolean
    ): Boolean = synchronized(lock) {
        val prefs = context.getSharedPreferences(
            BrotherUpdateNotificationWorker.PREFS, Context.MODE_PRIVATE
        )
        val notified = prefs.getLong("last_notified_version_code", 0L)
        if (!UpdateNotificationRules.shouldNotify(
                installedCode = installedCode,
                publishedCode = publishedCode,
                lastNotifiedCode = notified
            )
        ) return@synchronized false

        val didPost = post()
        if (didPost) {
            // Commit síncrono evita reemitir o mesmo aviso após reinicialização.
            prefs.edit().putLong("last_notified_version_code", publishedCode).commit()
        }
        didPost
    }
}
