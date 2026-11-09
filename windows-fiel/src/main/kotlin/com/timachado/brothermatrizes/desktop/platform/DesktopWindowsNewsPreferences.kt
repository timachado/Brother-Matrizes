package com.timachado.brothermatrizes.desktop.platform

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Private, local and intentionally small consent record for announcements
 * about ANDROID releases in the existing Windows app.
 *
 * Default is always OFF. No credential, machine ID, personal details, account
 * data or client files are read or saved. Writes happen only on an explicit
 * preference change or after a newly published notice is acknowledged.
 */
internal class DesktopWindowsNewsPreferences(
    private val storageFile: Path = defaultFile()
) {
    data class State(
        val enabled: Boolean = false,
        val lastNotifiedCode: Int = 0
    )

    private val file = storageFile.toAbsolutePath().normalize()

    init {
        require(storageFile.isAbsolute && storageFile.normalize() == storageFile) {
            "Caminho de preferências de avisos inválido."
        }
    }

    @Synchronized
    fun load(): State {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return State()
        check(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(file) &&
            Files.size(file) in 30L..128L) {
            "Registro de avisos inesperado; avisos automáticos desabilitados."
        }
        val parts = Files.readString(file, StandardCharsets.US_ASCII).lines()
        check(parts.size in 3..4 &&
            parts[0] == HEADER &&
            parts[1] in listOf("enabled=0", "enabled=1") &&
            parts[2].matches(Regex("lastNotifiedCode=(0|[1-9][0-9]{0,8})")) &&
            parts.drop(3).all { it.isEmpty() }) {
            "Preferências de avisos inválidas; revisão manual obrigatória."
        }
        val code = parts[2].substringAfter('=').toIntOrNull()
            ?: error("Código da última versão inválido.")
        return State(enabled = parts[1] == "enabled=1", lastNotifiedCode = code)
    }

    @Synchronized
    fun save(state: State) {
        require(state.lastNotifiedCode in 0..999_999_999) {
            "Código inválido para preferência de avisos."
        }
        val parent = requireNotNull(file.parent)
        require(!Files.isSymbolicLink(parent) &&
            (!Files.exists(parent, LinkOption.NOFOLLOW_LINKS) ||
                Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS))) {
            "Pasta de preferências não confiável."
        }
        // This is the app's own LocalAppData folder, never a client library.
        Files.createDirectories(parent)
        check(!Files.isSymbolicLink(parent) &&
            Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            "Pasta de preferências não confiável."
        }
        check(!Files.exists(file, LinkOption.NOFOLLOW_LINKS) ||
            Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            "Registro de avisos não é arquivo regular."
        }
        val payload = "$HEADER\nenabled=${if (state.enabled) 1 else 0}\n" +
            "lastNotifiedCode=${state.lastNotifiedCode}\n"
        val temp = Files.createTempFile(parent, ".bm-notice-", ".tmp")
        try {
            Files.writeString(temp, payload, StandardCharsets.US_ASCII)
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    companion object {
        private const val HEADER = "BROTHER_MATRIZES_WINDOWS_NEWS_V1"

        /**
         * Windows release alerts NEVER inherit Android-news consent,
         * stored version or defaults. Same atomic encoding, distinct file.
         * Does not create the file until the user explicitly opts in.
         */
        fun forWindowsReleases(): DesktopWindowsNewsPreferences =
            DesktopWindowsNewsPreferences(
                defaultFolder().resolve("windows-official-release-notices-v1.txt")
            )

        private fun defaultFolder(): Path {
            val root = System.getenv("LOCALAPPDATA")
                ?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
                ?: Path.of(System.getProperty("user.home"), ".local", "share")
            return root.toAbsolutePath().normalize()
                .resolve("T.I. Machado")
                .resolve("Brother Matrizes")
        }

        private fun defaultFile(): Path =
            defaultFolder().resolve("windows-news-v1.txt")
    }
}
