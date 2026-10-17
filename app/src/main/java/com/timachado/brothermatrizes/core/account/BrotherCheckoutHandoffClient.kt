package com.timachado.brothermatrizes.core.account

import com.timachado.brothermatrizes.BuildConfig
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Temporary identity exchange only. Plan prices/orders remain WooCommerce's
 * responsibility. A Supabase access token goes in the HTTPS Authorization
 * header, never inside an URL, log, intent or web cookie.
 */
internal object BrotherCheckoutHandoffClient {
    fun validateCheckoutUrl(value: String): String {
        val uri = URI(value)
        require(
            uri.scheme == "https" &&
            uri.host.equals("timachado.ifree.page", ignoreCase = true) &&
            uri.port == -1 && uri.rawUserInfo == null &&
            uri.rawPath == "/minha-conta/" && uri.rawFragment == null &&
            (uri.rawQuery ?: "").matches(Regex("bm_checkout=[A-Za-z0-9_-]{70,2500}"))
        ) { "Endereço seguro de checkout inválido." }
        return value
    }

    suspend fun open(planCode: String, accessToken: String): String =
        withContext(Dispatchers.IO) {
            require(
                planCode in setOf("pro_monthly", "pro_yearly",
                    "pro_lifetime", "pro_lifetime_launch")
            ) { "Plano inválido." }
            require(accessToken.isNotBlank()) { "Entre com Google antes de comprar." }

            val base = URI(BuildConfig.SUPABASE_URL)
            require(
                base.scheme == "https" && base.port == -1 &&
                base.rawUserInfo == null && base.rawQuery == null &&
                base.rawFragment == null && base.rawPath.trim('/').isEmpty() &&
                base.host.endsWith(".supabase.co")
            ) { "Provedor de identidade indisponível." }
            val url = URL(base.toString().trimEnd('/') +
                "/functions/v1/brother-checkout-handoff")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 9000
                readTimeout = 9000
                doOutput = true
                instanceFollowRedirects = false
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-store")
                setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            try {
                val data = JSONObject().put("plan_code", planCode)
                    .toString().toByteArray(StandardCharsets.UTF_8)
                conn.outputStream.use { it.write(data) }
                check(conn.responseCode == 200) {
                    "Login integrado indisponível. Utilize a loja normalmente."
                }
                val payload = conn.inputStream.bufferedReader().use { it.readText() }
                val address = JSONObject(payload).getString("checkout_url")
                validateCheckoutUrl(address)
            } finally {
                conn.disconnect()
            }
        }
}
