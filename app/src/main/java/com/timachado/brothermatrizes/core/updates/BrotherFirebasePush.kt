package com.timachado.brothermatrizes.core.updates

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Consentimento opcional e separado para alertas de novas versões e avisos da comunidade.
 * Não envia tokens para WordPress/Supabase e não altera autenticação ou licenças.
 */
object BrotherFirebasePush {
    const val TOPIC = "brother_matrizes_updates_stable"
    const val COMMUNITY_TOPIC = "brother_matrizes_community"
    private const val PREFS = "brother_fcm_push_consent"
    private const val UPDATE_KEY = "enabled"
    private const val COMMUNITY_KEY = "community_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(UPDATE_KEY, false)

    fun isCommunityEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(COMMUNITY_KEY, false)

    fun wasAsked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("asked", false)

    fun enable(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("asked", true)
            .putBoolean(UPDATE_KEY, true)
            .apply()
        sync(context)
    }

    fun enableCommunity(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(COMMUNITY_KEY, true)
            .apply()
        sync(context)
    }

    fun disable(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("asked", true)
            .putBoolean(UPDATE_KEY, false)
            .apply()
        unsubscribe(TOPIC, context)
    }

    fun disableCommunity(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(COMMUNITY_KEY, false)
            .apply()
        unsubscribe(COMMUNITY_TOPIC, context)
    }

    fun decline(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("asked", true)
            .putBoolean(UPDATE_KEY, false)
            .apply()
        unsubscribe(TOPIC, context)
    }

    private fun unsubscribe(topic: String, context: Context) {
        runCatching {
            val messaging = FirebaseMessaging.getInstance()
            messaging.unsubscribeFromTopic(topic).addOnCompleteListener {
                if (!isEnabled(context) && !isCommunityEnabled(context)) {
                    messaging.isAutoInitEnabled = false
                }
            }
        }
    }

    fun sync(context: Context) {
        if (!BrotherUpdateNotificationWorker.notificationsAllowed(context)) return
        val updates = isEnabled(context)
        val community = isCommunityEnabled(context)
        if (!updates && !community) return
        runCatching {
            val messaging = FirebaseMessaging.getInstance()
            messaging.isAutoInitEnabled = true
            if (updates) messaging.subscribeToTopic(TOPIC)
            if (community) messaging.subscribeToTopic(COMMUNITY_TOPIC)
            // O Firebase tenta restaurar a inscrição após desconexões.
        }
    }
}
