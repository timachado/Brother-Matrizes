package com.timachado.brothermatrizes.core.account

import android.content.Context
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Explicitly free BETA quota for local ZIP/TTF/OTF/matrix imports.
 *
 * No Pro entitlement, paid checkout, Supabase license or WooCommerce quota is
 * ever inferred here. This is only enabled on the separate BETA preview build.
 * Stored on this device, not a secure commercial usage counter.
 */
internal object LocalBetaImportQuota {
    private const val PREFS = "brother_matrizes_local_beta_quota_v1"
    private const val RESERVE_PREFIX = "reservation_"
    private const val RESERVATION_MAX_AGE_SECONDS = 30L * 60L
    private val utcPeriod = DateTimeFormatter.ofPattern("yyyy-MM")
        .withZone(ZoneOffset.UTC)
    private val tokenShape = Regex("beta_[0-9a-f-]{36}")

    fun limit(isFont: Boolean): Int = if (isFont) 3 else 5
    private fun operation(isFont: Boolean): String =
        if (isFont) "import_font" else "import_matrix"

    /** Reserves a local free-beta slot. No Android bearer token is needed. */
    @Synchronized
    fun authorize(context: Context, isFont: Boolean): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val currentPeriod = utcPeriod.format(Instant.now())
        val previousPeriod = prefs.getString("period", null)
        check(previousPeriod == null || currentPeriod >= previousPeriod) {
            "Data do aparelho anterior ao período já registrado. Confira a data automática."
        }
        if (previousPeriod != currentPeriod) {
            check(prefs.edit().clear().putString("period", currentPeriod).commit()) {
                "Não foi possível preparar a cota local da versão beta."
            }
        }

        val now = Instant.now().epochSecond
        val editor = prefs.edit()
        var cleaned = false
        var pending = 0
        val op = operation(isFont)
        for ((key, value) in prefs.all) {
            if (!key.startsWith(RESERVE_PREFIX)) continue
            val data = (value as? String)?.split('|')
            val created = data?.getOrNull(2)?.toLongOrNull()
            if (data?.size != 3 || created == null ||
                created > now + 60 || now - created > RESERVATION_MAX_AGE_SECONDS
            ) {
                editor.remove(key)
                cleaned = true
            } else if (data[0] == op && data[1] == currentPeriod) {
                pending++
            }
        }
        if (cleaned) check(editor.commit()) { "Não foi possível liberar reservas expiradas." }

        val used = prefs.getInt("used_$op", 0)
        check(used + pending < limit(isFont)) {
            "Limite beta de ${limit(isFont)} ${if (isFont) "fontes" else "matrizes"} por mês atingido neste aparelho."
        }
        val token = "beta_" + UUID.randomUUID().toString()
        check(prefs.edit().putString(
            RESERVE_PREFIX + token, "$op|$currentPeriod|$now"
        ).commit()) { "Não foi possível reservar uma importação local." }
        return token
    }

    /** Count successful operations only. A failed import releases its slot. */
    @Synchronized
    fun finalize(context: Context, isFont: Boolean, requestKey: String, success: Boolean) {
        require(tokenShape.matches(requestKey)) { "Reserva beta inválida." }
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = RESERVE_PREFIX + requestKey
        val details = prefs.getString(key, null)?.split('|')
            ?: error("Reserva beta inexistente ou já finalizada.")
        val op = operation(isFont)
        check(details.size == 3 && details[0] == op) {
            "A reserva não corresponde à operação solicitada."
        }
        val period = details[1]
        val currentPeriod = prefs.getString("period", null)
        check(period == currentPeriod) { "Período da reserva expirado." }
        val editor = prefs.edit().remove(key)
        if (success) {
            val used = prefs.getInt("used_$op", 0)
            check(used < limit(isFont)) { "Cota local esgotada." }
            editor.putInt("used_$op", used + 1)
        }
        check(editor.commit()) { "Não foi possível atualizar a cota local." }
    }
}
