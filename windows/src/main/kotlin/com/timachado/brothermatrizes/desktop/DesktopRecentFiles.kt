package com.timachado.brothermatrizes.desktop

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Base64
import java.util.Locale
import java.util.prefs.Preferences

/**
 * Histórico de caminhos selecionados explicitamente no computador.
 * Salva apenas caminhos em preferências do usuário, nunca matrizes, hashes
 * dos desenhos, credenciais ou arquivos em serviços remotos.
 * Não lê conteúdo nem percorre diretórios ao listar recentes.
 */
internal class DesktopRecentFiles(
    private val preferences: Preferences =
        Preferences.userNodeForPackage(DesktopRecentFiles::class.java)
) {
    companion object {
        const val MAX_RECENT = 12
        private const val KEY = "recentFilesV1"
        private const val MAX_PATH_LENGTH = 320
    }

    fun record(path: Path) {
        val absolute = path.toAbsolutePath().normalize()
        require(valid(absolute)) { "Somente arquivos DST/PES/JEF/FLP locais podem ser recentes." }
        val paths = (listOf(absolute) + list()).distinct().take(MAX_RECENT)
        val encoded = paths.joinToString(";") { file ->
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                file.toString().toByteArray(StandardCharsets.UTF_8)
            )
        }
        preferences.put(KEY, encoded)
        preferences.flush()
    }

    fun list(): List<Path> {
        val stored = preferences.get(KEY, "")
        if (stored.isBlank()) return emptyList()
        return stored.split(';').take(MAX_RECENT).mapNotNull { encoded ->
            runCatching {
                val decoded = String(Base64.getUrlDecoder().decode(encoded),
                    StandardCharsets.UTF_8)
                Path.of(decoded).toAbsolutePath().normalize()
            }.getOrNull()?.takeIf(::valid)
        }.distinct().take(MAX_RECENT)
    }

    fun clear() {
        preferences.remove(KEY)
        preferences.flush()
    }

    private fun valid(path: Path): Boolean {
        val raw = path.toString()
        if (raw.length > MAX_PATH_LENGTH || raw.any { it.code < 32 } ||
            !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ||
            Files.isSymbolicLink(path)) return false
        val extension = path.fileName.toString()
            .substringAfterLast('.', "").uppercase(Locale.ROOT)
        val limit = when (extension) {
            "FLP" -> DesktopProjectService.MAX_PROJECT_BYTES
            in DesktopMatrixService.inputFormats -> DesktopMatrixService.MAX_INPUT_BYTES
            else -> return false
        }
        return runCatching { Files.size(path) in 1..limit }.getOrDefault(false)
    }
}
