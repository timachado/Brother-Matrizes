package com.timachado.brothermatrizes.desktop

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Base64
import java.util.Locale
import java.util.prefs.Preferences

/**
 * Favoritos EXCLUSIVAMENTE locais: referências a arquivos escolhidos pelo usuário.
 * Não move, copia, modifica, indexa o disco ou envia dados para servidores.
 * As preferências não armazenam conteúdo de matrizes nem credenciais.
 */
internal class DesktopFavorites(
    private val preferences: Preferences =
        Preferences.userNodeForPackage(DesktopFavorites::class.java)
) {
    companion object {
        const val MAX_FAVORITES = 16
        private const val KEY = "localFavoriteFilesV1"
        private const val MAX_PATH_LENGTH = 320
        private const val PREFERENCES_VALUE_LIMIT = 8_000
    }

    fun list(): List<Path> {
        val stored = preferences.get(KEY, "")
        if (stored.isBlank()) return emptyList()
        return stored.split(';').take(MAX_FAVORITES).mapNotNull { part ->
            runCatching {
                val decoded = String(Base64.getUrlDecoder().decode(part),
                    StandardCharsets.UTF_8)
                Path.of(decoded).toAbsolutePath().normalize()
            }.getOrNull()?.takeIf(::valid)
        }.distinct().take(MAX_FAVORITES)
    }

    fun toggle(file: Path): Boolean {
        val absolute = file.toAbsolutePath().normalize()
        require(valid(absolute)) {
            "Favoritos aceitam apenas arquivos locais DST, PES, JEF ou FLP válidos."
        }
        val current = list()
        val add = absolute !in current
        val updated = if (add) listOf(absolute) + current
            else current.filterNot { it == absolute }
        save(updated.take(MAX_FAVORITES))
        return add
    }

    fun clear() {
        preferences.remove(KEY)
        preferences.flush()
    }

    private fun save(paths: List<Path>) {
        val encoded = paths.map { file ->
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                file.toString().toByteArray(StandardCharsets.UTF_8)
            )
        }.toMutableList()
        while (encoded.joinToString(";").length > PREFERENCES_VALUE_LIMIT &&
            encoded.size > 1) {
            encoded.removeAt(encoded.lastIndex)
        }
        preferences.put(KEY, encoded.joinToString(";"))
        preferences.flush()
    }

    private fun valid(file: Path): Boolean {
        val value = file.toString()
        if (value.length > MAX_PATH_LENGTH || value.any { it.code < 32 } ||
            !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ||
            Files.isSymbolicLink(file)) return false
        val extension = file.fileName.toString()
            .substringAfterLast('.', "").uppercase(Locale.ROOT)
        val sizeLimit = when (extension) {
            "FLP" -> DesktopProjectService.MAX_PROJECT_BYTES
            in DesktopMatrixService.inputFormats -> DesktopMatrixService.MAX_INPUT_BYTES
            else -> return false
        }
        return runCatching { Files.size(file) in 1..sizeLimit }.getOrDefault(false)
    }
}
