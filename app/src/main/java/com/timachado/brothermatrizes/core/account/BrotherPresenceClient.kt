package com.timachado.brothermatrizes.core.account

import com.timachado.brothermatrizes.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Registra presença do aplicativo sem misturar atividade com licença/pagamento.
 *
 * A função Supabase valida a sessão Google no servidor e faz o upsert do
 * dispositivo com horário de servidor. Nenhuma credencial privada é enviada
 * pelo APK; apenas a publishable key pública e o JWT normal da sessão.
 */
internal object BrotherPresenceClient {

    suspend fun heartbeat(
        accessToken: String,
        device: LocalDeviceIdentity
    ): Unit = withContext(Dispatchers.IO) {
        require(accessToken.isNotBlank()) { "Sessão indisponível." }
        require(device.deviceId.matches(Regex("^[0-9a-f]{32}$"))) {
            "Identificador de dispositivo inválido."
        }

        val endpoint = URL(
            BuildConfig.SUPABASE_URL.trimEnd('/') +
                "/functions/v1/brother-presence"
        )
        require(
            endpoint.protocol == "https" &&
                endpoint.host == "dwpcddiramxlhavdmmyn.supabase.co" &&
                endpoint.port == -1
        ) { "Servidor de presença inesperado." }

        val payload = JSONObject()
            .put("device_id", device.deviceId)
            .put("device_name", device.deviceName.take(80))
            .put("platform", "android")
            .put("app_version", BuildConfig.VERSION_NAME)

        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty(
                "X-Brother-App-Version-Code",
                BuildConfig.VERSION_CODE.toString()
            )
        }

        try {
            conn.outputStream.use { stream ->
                stream.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode
            val mime = conn.contentType.orEmpty()
                .substringBefore(';')
                .lowercase()
                .trim()

            check(
                code == 200 &&
                    (mime == "application/json" || mime.endsWith("+json"))
            ) {
                "Presença indisponível (HTTP $code)."
            }

            // Consumir e limitar a resposta evita deixar a conexão pendurada,
            // mas o aplicativo não depende do conteúdo para continuar.
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val chars = CharArray(1024)
                var total = 0
                while (true) {
                    val count = reader.read(chars)
                    if (count < 0) break
                    total += count
                    check(total <= 16_384) {
                        "Resposta de presença muito grande."
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}
