package com.timachado.brothermatrizes.core.updates

import android.content.Context

/** Registro local de eventos entregues para não alertar duas vezes sobre a mesma notícia. */
internal object BrotherCommunityNotificationGate {
    private val lock = Any()
    private const val PREFS = "brother_community_notifications"
    private const val SEEN = "seen_event_ids"

    fun postOnce(context: Context, eventId: String, post: () -> Boolean): Boolean = synchronized(lock) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getString(SEEN, "").orEmpty().split('|').filter { it.isNotEmpty() }
        if (eventId in seen) return@synchronized false
        val published = post()
        if (published) {
            val recent = (seen + eventId).takeLast(50).joinToString("|")
            prefs.edit().putString(SEEN, recent).commit()
        }
        published
    }
}
