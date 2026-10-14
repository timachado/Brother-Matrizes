package com.timachado.brothermatrizes.core.updates

/** Mensagens FCM devem ser específicas da versão e sem repetição. */
internal object BrotherPushPayloadRules {
    fun shouldPost(data: Map<String, String>, installedCode: Long, lastNotifiedCode: Long): Boolean {
        if (data["type"] != "update") return false
        val published = data["versionCode"]?.toLongOrNull() ?: return false
        return UpdateNotificationRules.shouldNotify(installedCode, published, lastNotifiedCode)
    }

    fun notificationTitle(data: Map<String, String>): String =
        data["title"].orEmpty().trim().take(90)
            .ifBlank { "Brother Matrizes: uma nova versão chegou!" }

    fun notificationBody(data: Map<String, String>): String =
        data["body"].orEmpty().trim().take(240)
            .ifBlank {
                "Novidades e melhorias já estão disponíveis. Toque para conferir e atualizar."
            }
}
