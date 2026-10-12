package com.timachado.brothermatrizes.core.updates

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.timachado.brothermatrizes.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

/**
 * Atualização externa, mediante toque do usuário.
 *
 * Resolve o botão de download da página oficial T.I. Machado, baixa o APK
 * para cache privado, confere SHA-256 no manifesto GitHub Raw, pacote,
 * versionCode e certificado original; só então abre o instalador Android.
 * Não instala silenciosamente nem interfere no Play Protect.
 */
object BrotherApkUpdater {
    private const val MIRROR = "https://raw.githubusercontent.com/timachado/Brother-Matrizes/main/updates/stable.json"
    private const val OFFICIAL_HOST = "timachado.ifree.page"
    private const val SITE_PAGE = "https://timachado.ifree.page/aplicativos/brother-matrizes/"
    private const val EXPECTED_CERT = "fac3e71b641805e1183e34a636e50e4890e8fe24873f53e85d863b934815de4a"
    private const val MAX_APK_BYTES = 150L * 1024L * 1024L

    suspend fun download(
        context: Context,
        release: BrotherUpdateClient.Release
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val trusted = readTrustedManifest()
            require(trusted.first == release.versionCode && trusted.third == release.versionName) {
                "A versão do site ainda não está sincronizada com o canal oficial. Tente novamente."
            }
            require(trusted.first > BuildConfig.VERSION_CODE.toLong()) {
                "Esta versão já está instalada."
            }
            val url = resolveDownloadLink()
            val dir = File(context.cacheDir, "verified_apk_updates").apply { mkdirs() }
            val target = File(dir, "Brother-Matrizes-" + release.versionCode + ".apk")
            if (target.isFile && digestOf(target) == trusted.second) {
                validateApk(context, target, release.versionCode)
                return@runCatching target
            }
            val pending = File(dir, "download.partial")
            pending.delete()
            try {
                val connection = openOfficial(url)
                try {
                    require(connection.responseCode == 200) {
                        "O site não autorizou o download (HTTP " + connection.responseCode + ")."
                    }
                    require(connection.contentLengthLong <= MAX_APK_BYTES || connection.contentLengthLong < 0) {
                        "O arquivo excede o tamanho máximo permitido."
                    }
                    val hasher = MessageDigest.getInstance("SHA-256")
                    var total = 0L
                    connection.inputStream.use { stream ->
                        pending.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = stream.read(buffer)
                                if (count == -1) break
                                total += count
                                require(total <= MAX_APK_BYTES) { "O arquivo excede o limite permitido." }
                                hasher.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    require(total > 10_000L) { "O site não retornou o APK completo." }
                    val actual = hasher.digest().toHex()
                    require(actual.equals(trusted.second, ignoreCase = true)) {
                        "O arquivo baixado é diferente do APK oficial. Verifique o arquivo no site."
                    }
                    validateApk(context, pending, release.versionCode)
                    target.delete()
                    require(pending.renameTo(target)) { "Não foi possível salvar a atualização." }
                    target
                } finally {
                    connection.disconnect()
                }
            } finally {
                pending.delete()
            }
        }
    }

    /**
     * O endpoint WordPress pode manter um SHA antigo mesmo com a versão certa.
     * Portanto, a fonte de verdade criptográfica é o manifesto da branch main.
     */
    private fun readTrustedManifest(): Triple<Long, String, String> {
        val url = URL(MIRROR)
        val connection = (url.openConnection() as HttpsURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            require(connection.responseCode == 200) {
                "Não foi possível validar a versão oficial no GitHub."
            }
            val text = connection.inputStream.bufferedReader().use { it.readText().take(65537) }
            require(text.length < 65537) { "Manifesto excessivo." }
            val json = JSONObject(text)
            require(json.optString("applicationId") == "com.timachado.brothermatrizes" &&
                json.optString("channel") == "stable") { "Manifesto de outro aplicativo." }
            val hash = json.getString("sha256").lowercase(Locale.ROOT)
            require(Regex("[0-9a-f]{64}").matches(hash)) { "Hash oficial inválido." }
            return Triple(json.getLong("versionCode"), hash, json.getString("versionName"))
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Descobre o link de download assinado/temporário que o WordPress já cria
     * para o aplicativo. Não usa nonce fixo, não inventa URL e não faz
     * requisição a hosts de terceiros.
     */
    private fun resolveDownloadLink(): URL {
        val page = openOfficial(URL(SITE_PAGE))
        try {
            require(page.responseCode == 200) {
                "A página oficial não está disponível para o download automático."
            }
            val html = page.inputStream.bufferedReader().use { it.readText().take(1_000_001) }
            require(html.length <= 1_000_000) { "Página de download inesperadamente grande." }
            return BrotherApkSourceRules.findDownloadUrl(html)
                ?: error("O botão de download do APK não está disponível na página oficial.")

        } finally {
            page.disconnect()
        }
    }

    private fun safeOfficial(url: URL): Boolean = BrotherApkSourceRules.isOfficial(url)

    private fun openOfficial(url: URL): HttpsURLConnection {
        var current = url
        val cookies = linkedMapOf<String, String>()
        repeat(5) {
            require(safeOfficial(current)) { "Download fora do domínio oficial da T.I. Machado." }
            val connection = (current.openConnection() as HttpsURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream, text/html, */*")
                setRequestProperty("User-Agent", "BrotherMatrizes-Android-Updater/" + BuildConfig.VERSION_NAME)
                if (cookies.isNotEmpty()) {
                    setRequestProperty("Cookie", cookies.values.joinToString("; "))
                }
            }
            val status = connection.responseCode
            if (status !in 300..399) return connection
            val redirect = connection.getHeaderField("Location")
            if (redirect.isNullOrBlank()) return connection
            connection.headerFields["Set-Cookie"]?.forEach { raw ->
                val pair = raw.substringBefore(';').trim()
                val name = pair.substringBefore('=')
                if (name.isNotBlank() && pair.contains('=')) cookies[name] = pair
            }
            val next = URL(current, redirect)
            connection.disconnect()
            require(safeOfficial(next)) { "O download redirecionou para um domínio não autorizado." }
            current = next
        }
        error("O site redirecionou o download muitas vezes.")
    }

    private fun validateApk(context: Context, apk: File, code: Long) {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } ?: error("O arquivo recebido não é um APK Android válido.")
        require(info.packageName == context.packageName) { "APK pertence a outro aplicativo." }
        @Suppress("DEPRECATION")
        val version = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        require(version == code && version > BuildConfig.VERSION_CODE.toLong()) {
            "O APK não corresponde à versão disponível."
        }
        @Suppress("DEPRECATION")
        val signers = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)?.signatures
        }
        require(!signers.isNullOrEmpty()) { "O APK não possui assinatura verificável." }
        val certificate = MessageDigest.getInstance("SHA-256").digest(signers[0].toByteArray()).toHex()
        require(certificate == EXPECTED_CERT) { "A assinatura não corresponde ao Brother Matrizes." }
    }

    fun launchInstaller(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:" + context.packageName)
                }
            )
            return false
        }
        val uri = FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = ClipData.newUri(context.contentResolver, "Brother Matrizes", uri)
        }
        context.startActivity(intent)
        return true
    }

    private fun digestOf(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(8192)
            while (true) {
                val n = input.read(bytes)
                if (n == -1) break
                md.update(bytes, 0, n)
            }
        }
        return md.digest().toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
