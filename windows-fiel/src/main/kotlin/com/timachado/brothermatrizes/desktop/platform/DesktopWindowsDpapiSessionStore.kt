package com.timachado.brothermatrizes.desktop.platform

import com.sun.jna.platform.win32.Crypt32Util
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.time.Instant
import java.util.Arrays
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * CryptProtectData / CryptUnprotectData using CURRENT Windows user only.
 * Does not use CRYPTPROTECT_LOCAL_MACHINE or an application-wide static key.
 * Windows DPAPI also provides integrity protection for the encrypted payload.
 */
internal object DesktopCurrentUserDpapi {
    private const val CRYPTPROTECT_UI_FORBIDDEN = 0x1

    fun protect(plain: ByteArray): ByteArray {
        requireWindows()
        return Crypt32Util.cryptProtectData(
            plain, null, CRYPTPROTECT_UI_FORBIDDEN,
            "Brother Matrizes Windows - sessão autenticada", null
        )
    }

    fun unprotect(ciphertext: ByteArray): ByteArray {
        requireWindows()
        return Crypt32Util.cryptUnprotectData(
            ciphertext, null, CRYPTPROTECT_UI_FORBIDDEN, null
        )
    }

    private fun requireWindows() {
        check(System.getProperty("os.name").startsWith("Windows", true)) {
            "DPAPI só é compatível com Windows."
        }
    }
}

internal class DesktopProtectedSessionSnapshot(
    val session: DesktopTransientAuthSession,
    val expiresAt: Instant
) {
    override fun toString(): String = "DesktopProtectedSessionSnapshot(redacted)"
}

/**
 * File containing ONLY a user-bound DPAPI ciphertext. Intended for a future
 * opt-in desktop account login, not yet connected to the Windows UI.
 *
 * Security restrictions: no plaintext token file, no silent fallback to
 * unencrypted storage, no global/machine DPAPI scope, no symlink reads/writes,
 * bounded JSON and ciphertext, atomic replacement within LocalAppData.
 *
 * DPAPI does not protect against other processes running as the same user.
 * Application licensing still requires server verification independently.
 */
/**
 * Protected session persistence contract for real DPAPI and offline fakes.
 * Never back this interface with a plaintext vault in a distributed build.
 */
internal interface DesktopProtectedSessionVault {
    fun save(session: DesktopTransientAuthSession)
    fun load(): DesktopProtectedSessionSnapshot?
    fun clear()
}

internal class DesktopWindowsDpapiSessionStore(
    private val folder: Path = defaultUserDataFolder(),
    private val clock: Clock = Clock.systemUTC()
) : DesktopProtectedSessionVault {
    private val file = folder.resolve("session.v1.dpapi")
    private val maxCiphertext = 65_536
    private val maxPlaintext = 32_768

    init {
        check(System.getProperty("os.name").startsWith("Windows", true)) {
            "Credenciais locais indisponíveis fora do Windows."
        }
        require(folder.isAbsolute && folder.normalize() == folder) {
            "Local de credenciais inválido."
        }
    }

    @Synchronized
    override fun save(session: DesktopTransientAuthSession) {
        require(session.expiresInSeconds in 1..86_400 &&
            session.accessToken.validSessionToken() &&
            session.refreshToken.validRefreshToken()) {
            "Sessão não pode ser persistida."
        }
        validateStorageDirectory(create = true)
        check(!Files.exists(file, LinkOption.NOFOLLOW_LINKS) ||
            Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            "Arquivo de sessão inesperado."
        }

        val expires = clock.instant().plusSeconds(session.expiresInSeconds.toLong())
        val json = JsonObject(mapOf(
            "version" to JsonPrimitive(1),
            "access_token" to JsonPrimitive(session.accessToken),
            "refresh_token" to JsonPrimitive(session.refreshToken),
            "expires_at" to JsonPrimitive(expires.epochSecond)
        )).toString()
        val plain = json.toByteArray(Charsets.UTF_8)
        require(plain.size <= maxPlaintext) { "Sessão grande demais." }

        var ciphertext: ByteArray? = null
        var temp: Path? = null
        try {
            ciphertext = DesktopCurrentUserDpapi.protect(plain)
            check(ciphertext.size in 1..maxCiphertext) {
                "Sessão cifrada fora do limite."
            }
            temp = Files.createTempFile(folder, ".bm-session-", ".tmp")
            Files.write(temp, ciphertext, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
            try {
                Files.move(
                    temp, file, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
                temp = null
            } catch (_: AtomicMoveNotSupportedException) {
                error("Armazenamento não oferece substituição atômica.")
            }
        } finally {
            Arrays.fill(plain, 0)
            ciphertext?.let { Arrays.fill(it, 0) }
            temp?.let { Files.deleteIfExists(it) }
        }
    }

    @Synchronized
    override fun load(): DesktopProtectedSessionSnapshot? {
        validateStorageDirectory(create = false)
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return null
        check(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            "Arquivo de sessão inválido."
        }
        val size = Files.size(file)
        check(size in 1..maxCiphertext.toLong()) {
            "Arquivo de sessão fora do limite."
        }
        val ciphertext = Files.readAllBytes(file)
        var plain: ByteArray? = null
        try {
            plain = DesktopCurrentUserDpapi.unprotect(ciphertext)
            check(plain.size in 1..maxPlaintext) {
                "Sessão recuperada fora do limite."
            }
            return runCatching {
                val json = Json.parseToJsonElement(plain.toString(Charsets.UTF_8))
                require(json is JsonObject)
                require((json["version"] as? JsonPrimitive)?.intOrNull == 1)
                val access = (json["access_token"] as? JsonPrimitive)
                    ?.takeIf { it.isString }?.content.orEmpty()
                val refresh = (json["refresh_token"] as? JsonPrimitive)
                    ?.takeIf { it.isString }?.content.orEmpty()
                val expiry = (json["expires_at"] as? JsonPrimitive)?.longOrNull
                require(access.validSessionToken() && refresh.validRefreshToken())
                require(expiry != null && expiry in 1L..4_102_444_800L)
                val validUntil = Instant.ofEpochSecond(expiry)
                require(!validUntil.isAfter(clock.instant().plusSeconds(86_400)))
                val secondsLeft = (validUntil.epochSecond -
                    clock.instant().epochSecond).coerceIn(1L, 86_400L).toInt()
                DesktopProtectedSessionSnapshot(
                    DesktopTransientAuthSession(access, refresh, secondsLeft),
                    validUntil
                )
            }.getOrElse {
                // Never expose parser input, token bytes or DPAPI error internals.
                error("Sessão protegida inválida; novo login necessário.")
            }
        } finally {
            Arrays.fill(ciphertext, 0)
            plain?.let { Arrays.fill(it, 0) }
        }
    }

    @Synchronized
    override fun clear() {
        validateStorageDirectory(create = false)
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            check(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                "Arquivo de sessão inválido."
            }
            Files.delete(file)
        }
    }

    private fun validateStorageDirectory(create: Boolean) {
        if (create) Files.createDirectories(folder)
        if (!Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) return
        check(Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
            "Diretório de credenciais inválido."
        }
    }

    private fun String.validSessionToken(): Boolean =
        length in 16..8192 &&
            all { it.code in 0x21..0x7e && it != '"' && it != '\\' }

    // Legacy Supabase refresh tokens are exactly 12 lowercase a-z/0-9.
    // Modern refresh tokens remain subject to the existing bounded checks.
    private fun String.validRefreshToken(): Boolean =
        if (length == 12) matches(Regex("[a-z0-9]{12}")) else validSessionToken()

    override fun toString(): String = "DesktopWindowsDpapiSessionStore(redacted)"

    companion object {
        internal fun defaultUserDataFolder(): Path {
            val local = System.getenv("LOCALAPPDATA")
                ?.takeIf { it.isNotBlank() }
                ?: error("Perfil local Windows indisponível.")
            val root = Path.of(local).toAbsolutePath().normalize()
            return root.resolve("T.I. Machado").resolve("Brother Matrizes").normalize()
        }
    }
}
