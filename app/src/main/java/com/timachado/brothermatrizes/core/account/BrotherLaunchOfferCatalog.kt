package com.timachado.brothermatrizes.core.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Leitura pública, sem cookies, token, senha, sessão, ID de usuário ou pagamento.
 * Não substitui o checkout, que continua autoridade para estoque e preço final.
 *
 * InfinityFree pode bloquear clientes Android. Nesses casos o estado é Unknown
 * e a interface NÃO anuncia que há unidades disponíveis.
 */
internal object BrotherLaunchOfferCatalog {
    private const val URL_PUBLIC = "https://timachado.ifree.page/wp-json/wc/store/v1/products/308"

    sealed class Status {
        data class Available(val priceCents: Int) : Status()
        data object Unavailable : Status()
        data object Unknown : Status()
    }

    suspend fun fetch(): Status = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(URL_PUBLIC).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4500
                readTimeout = 4500
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode != 200 ||
                !connection.contentType.orEmpty().contains("json", ignoreCase = true)
            ) return@withContext Status.Unknown
            val raw = connection.inputStream.bufferedReader().use { it.readText() }
            if (raw.length > 60000) return@withContext Status.Unknown
            val product = JSONObject(raw)
            if (product.optInt("id") != 308 ||
                !product.has("is_in_stock") || !product.has("is_purchasable")
            ) return@withContext Status.Unknown

            if (!product.optBoolean("is_in_stock") || !product.optBoolean("is_purchasable"))
                return@withContext Status.Unavailable

            val cents = product.optJSONObject("prices")?.optString("price")
                ?.toIntOrNull()
            if (cents == null || cents <= 0 || cents > 1000000)
                return@withContext Status.Unknown
            Status.Available(cents)
        } catch (_: Exception) {
            Status.Unknown
        } finally {
            connection?.disconnect()
        }
    }
}
