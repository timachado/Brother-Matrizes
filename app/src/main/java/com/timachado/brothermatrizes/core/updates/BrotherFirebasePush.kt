package com.timachado.brothermatrizes.core.updates

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Inscrição em notificações FCM sobre novas versões.
 * Nunca modifica login Google/Supabase, pagamentos nem licenças.
 * O consentimento é independente da permissão de avisos locais da 1.0.5.
 */
object BrotherFirebasePush {
    const val TOPIC = "brother_matrizes_updates_stable"
    private const val PREFS = "brother_fcm_push_consent"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("enabled", false)

    fun wasAsked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("asked", false)

    fun enable(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("asked", true)
            .putBoolean("enabled", true)
            .apply()
        sync(context)
    }

    fun disable(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("asked", true)
            .putBoolean("enabled", false)
            .apply()
        // Best effort: Firebase persiste cancelamento até reconectar.
        runCatching {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC)
                .addOnCompleteListener { FirebaseMessaging.getInstance().isAutoInitEnabled = false }
        }
    }

    fun decline(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("asked", true)
            .putBoolean("enabled", false)
            .apply()
        runCatching { FirebaseMessaging.getInstance().isAutoInitEnabled = false }
    }

    fun sync(context: Context) {
        if (!isEnabled(context) ||
            !BrotherUpdateNotificationWorker.notificationsAllowed(context)) return
        runCatching {
            val messaging = FirebaseMessaging.getInstance()
            messaging.isAutoInitEnabled = true
            messaging.subscribeToTopic(TOPIC)
            // O SDK tenta novamente se a rede estiver indisponível.
        }
    }
}
