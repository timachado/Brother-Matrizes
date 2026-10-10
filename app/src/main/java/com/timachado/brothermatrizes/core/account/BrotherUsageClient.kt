package com.timachado.brothermatrizes.core.account

import com.timachado.brothermatrizes.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Authenticated monthly import quota for production.
 * Financial entitlements remain WooCommerce/Efí, verified by Brother's
 * isolated server service. No secret/service-role key is shipped in Android.
 */
internal object BrotherUsageClient {
    private val reservationPattern =
        Regex("^(usage|pro)_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    internal fun isValidReservationKey(value: String): Boolean =
        reservationPattern.matches(value)

    suspend fun reserve(accessToken: String, isFont: Boolean): String {
        val operation = if (isFont) "import_font" else "import_matrix"
        val body = post(accessToken, JSONObject()
            .put("action", "reserve")
            .put("operation", operation))
        check(body.optBoolean("allowed") && body.optString("operation") == operation) {
            "Não foi possível reservar a importação."
        }
        val requestKey = body.optString("request_key")
        check(isValidReservationKey(requestKey)) {
            "O servidor retornou uma reserva inválida."
        }
        return requestKey
    }

    suspend fun finalize(
        accessToken: String, isFont: Boolean,
        requestKey: String, success: Boolean
    ) {
        require(isValidReservationKey(requestKey)) {
            "Reserva de importação inválida."
        }
        val operation = if (isFont) "import_font" else "import_matrix"
        val body = post(accessToken, JSONObject()
            .put("action", "finalize")
            .put("operation", operation)
            .put("request_key", requestKey)
            .put("success", success))
        check(body.optBoolean("ok")) {
            "Não foi possível registrar o resultado da importação."
        }
    }

    private suspend fun post(accessToken: String, payload: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val url = URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/brother-usage")
            require(url.protocol == "https" &&
                url.host == "dwpcddiramxlhavdmmyn.supabase.co" &&
                url.port == -1) { "Servidor de cotas inesperado." }
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 20000
                readTimeout = 20000
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            try {
                conn.outputStream.use {
                    it.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
                val code = conn.responseCode
                val mime = conn.contentType.orEmpty().substringBefore(';')
                    .lowercase().trim()
                check(mime == "application/json" || mime.endsWith("+json")) {
                    "O servidor não retornou uma resposta válida."
                }
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    val result = StringBuilder()
                    val chars = CharArray(4096)
                    while (true) {
                        val count = reader.read(chars)
                        if (count < 0) break
                        check(result.length + count <= 65536) {
                            "Resposta do servidor muito grande."
                        }
                        result.append(chars, 0, count)
                    }
                    result.toString()
                }.orEmpty()
                val data = JSONObject(raw)
                check(code == 200) {
                    data.optString("message").takeIf(String::isNotBlank)
                        ?: "Não foi possível verificar a cota (HTTP $code)."
                }
                data
            } finally {
                conn.disconnect()
            }
        }
}
