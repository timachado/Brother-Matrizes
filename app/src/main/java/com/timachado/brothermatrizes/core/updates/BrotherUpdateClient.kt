package com.timachado.brothermatrizes.core.updates

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Consulta o manifesto publico de atualizacoes Brother Matrizes.
 * O espelho GitHub oficial tem prioridade sobre o WordPress para evitar cache
 * desatualizado e paginas HTML de bloqueio, sem mexer nos dados comerciais.
 * A instalacao e validacao do APK sao realizadas por BrotherApkUpdater.
 */
class BrotherUpdateClient(private val endpoint: String) {
    companion object {
        private const val WORDPRESS_ENDPOINT_PREFIX =
            "https://timachado.ifree.page/wp-json/tim-brother-updates/v1/latest"
        private const val OFFICIAL_MIRROR =
            "https://raw.githubusercontent.com/timachado/Brother-Matrizes/main/updates/stable.json"
    }

    data class Release(
        val versionName: String,
        val versionCode: Long,
        val minSupportedVersionCode: Long,
        val downloadUrl: String,
        val sha256: String,
        val releaseNotes: String,
        val publishedAt: String
    )

    sealed interface Result {
        data class Available(val release: Release, val critical: Boolean) : Result
        data object UpToDate : Result
        data object NotPublished : Result
        data class Error(val message: String) : Result
    }

    suspend fun check(context: Context): Result = withContext(Dispatchers.IO) {
        // GitHub é a fonte oficial da versão/hash e tem link APK direto. A
        // hospedagem WordPress pode devolver HTML anti-bot ou cache antigo.
        // Consultar primeiro o espelho oficial impede falso "atualizado"
        // e evita sugerir um APK diferente do que o assinador publicou.
        if (endpoint.startsWith(WORDPRESS_ENDPOINT_PREFIX)) {
            when (val mirrorResult = BrotherUpdateClient(OFFICIAL_MIRROR).check(context)) {
                is Result.Available, Result.UpToDate -> return@withContext mirrorResult
                else -> Unit // Se GitHub cair, manter tentativa na API WordPress.
            }
        }
        var connection: HttpsURLConnection? = null
        try {
            val url = URL(endpoint)
            if (url.protocol != "https") {
                return@withContext Result.Error("Servidor de atualizações sem HTTPS.")
            }
            connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = false
            }
            val status = connection.responseCode
            if (status == 404) {
                // 404 do WordPress pode ser ausência de publicação ou endpoint inexistente.
                // Aceitar somente o código explícito produzido pelo nosso plugin.
                val errorType = connection.contentType.orEmpty()
                    .substringBefore(';').trim().lowercase()
                val response = connection.errorStream?.use { stream ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(512)
                    while (true) {
                        val n = stream.read(buffer)
                        if (n == -1) break
                        if (out.size() + n > 4096) break
                        out.write(buffer, 0, n)
                    }
                    out.toString(Charsets.UTF_8.name())
                }.orEmpty()
                val code = if (errorType == "application/json") {
                    runCatching { JSONObject(response).optString("code") }.getOrNull()
                } else null
                return@withContext if (code == "tim_no_release") Result.NotPublished
                    else Result.Error("Servidor de atualizações não encontrado (HTTP 404).")
            }
            if (status == 403 && endpoint.startsWith("https://timachado.ifree.page/")) {
                // Hospedagem InfinityFree bloqueia consultas Android à API REST,
                // mesmo quando o plugin responde corretamente no navegador.
                // O espelho publico (somente metadados) fica fora desse firewall.
                connection.disconnect()
                return@withContext BrotherUpdateClient(
                    "https://raw.githubusercontent.com/timachado/Brother-Matrizes/main/updates/stable.json"
                ).check(context)
            }
            if (status != 200) {
                return@withContext Result.Error("Não foi possível consultar as atualizações (HTTP $status).")
            }
            val mediaType = connection.contentType.orEmpty().substringBefore(';').trim().lowercase()
            // GitHub Raw pode servir .json como text/plain, embora o conteúdo
            // seja JSON válido. Só permitir no endereço exato do espelho oficial.
            val trustedMirror = url.host == "raw.githubusercontent.com" &&
                url.path == "/timachado/Brother-Matrizes/main/updates/stable.json"
            val acceptedType = mediaType == "application/json" ||
                (trustedMirror && mediaType in setOf("text/plain", "application/octet-stream"))
            if (!acceptedType) {
                return@withContext Result.Error("O servidor não retornou os dados de atualização esperados.")
            }
            val bytes = connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                while (true) {
                    val n = input.read(buf)
                    if (n == -1) break
                    require(out.size() + n <= 65536) { "Resposta excessiva" }
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
            val manifest = JSONObject(bytes.toString(Charsets.UTF_8))
            if (manifest.optString("applicationId") != context.packageName) {
                return@withContext Result.Error("Esta atualização pertence a outro aplicativo.")
            }
            val code = manifest.getLong("versionCode")
            val minCode = manifest.optLong("minSupportedVersionCode", 0)
            if (code <= 0 || minCode < 0 || minCode > code) {
                return@withContext Result.Error("Versão publicada inválida.")
            }
            val installedCode = installedVersionCode(context)
            if (!UpdateVersionRules.isUpdateAvailable(installedCode, code)) {
                // A loja WordPress ja ficou atrasada do manifesto GitHub em
                // lancamentos anteriores. Nunca dizer "atualizado" apenas com
                // os metadados antigos do WordPress; consultar o espelho oficial.
                if (endpoint.startsWith("https://timachado.ifree.page/")) {
                    val mirror = BrotherUpdateClient(
                        "https://raw.githubusercontent.com/timachado/Brother-Matrizes/main/updates/stable.json"
                    ).check(context)
                    if (mirror is Result.Available) return@withContext mirror
                }
                return@withContext Result.UpToDate
            }

            val download = manifest.getString("downloadUrl")
            val target = URL(download)
            if (target.protocol != "https" || target.userInfo != null) {
                return@withContext Result.Error("Link de atualização inseguro.")
            }
            val hash = manifest.getString("sha256")
            if (!Regex("[a-fA-F0-9]{64}").matches(hash)) {
                return@withContext Result.Error("Hash do APK inválido.")
            }
            val name = manifest.getString("versionName").trim()
            if (name.isBlank() || name.length > 64) {
                return@withContext Result.Error("Nome da versão inválido.")
            }
            Result.Available(
                release = Release(
                    versionName = name,
                    versionCode = code,
                    minSupportedVersionCode = minCode,
                    downloadUrl = download,
                    sha256 = hash.lowercase(),
                    releaseNotes = manifest.optString("releaseNotes").take(4000),
                    publishedAt = manifest.optString("publishedAt")
                ),
                critical = UpdateVersionRules.isCritical(installedCode, minCode)
            )
        } catch (_: Exception) {
            Result.Error("Não foi possível verificar agora. Verifique a conexão e tente novamente.")
        } finally {
            connection?.disconnect()
        }
    }

    private fun installedVersionCode(context: Context): Long {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 28) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
    }
}