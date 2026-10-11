package com.timachado.brothermatrizes.core.updates

/** Mensagens FCM devem ser específicas da versão e sem repetição. */
internal object BrotherPushPayloadRules {
    /** Apenas para teste individual; nunca registra uma atualização publicada. */
    fun isDiagnostic(data: Map<String, String>): Boolean =
        data["type"] == "diagnostic" && data["testId"] == "brother-push-qa-v1"

    fun shouldPost(data: Map<String, String>, installedCode: Long, lastNotifiedCode: Long): Boolean {
        if (data["type"] != "update") return false
        val published = data["versionCode"]?.toLongOrNull() ?: return false
        return UpdateNotificationRules.shouldNotify(installedCode, published, lastNotifiedCode)
    }

    fun notificationTitle(data: Map<String, String>): String =
        data["title"].orEmpty().trim().take(90)
            .ifBlank { "Brother Matrizes: atualização disponível" }

    fun notificationBody(data: Map<String, String>): String =
        data["body"].orEmpty().trim().take(240)
            .ifBlank {
                "A versão " + data["versionName"].orEmpty().take(32) +
                    " está disponível. Toque para ver as novidades."
            }
}
