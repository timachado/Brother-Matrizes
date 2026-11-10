package com.timachado.brothermatrizes.desktop.platform

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64

/**
 * Preferências LOCAIS da Biblioteca Windows. Sem dados de matriz, credenciais
 * ou rede: somente caminhos da pasta selecionada, IDs de favoritos e recentes.
 * Escrita atômica; o aplicativo nunca modifica arquivos FLP do cliente.
 */
internal class DesktopLibraryActivityStore(
    private val storageFile: Path = defaultFile()
) {
    data class State(
        val favorites: Set<String> = emptySet(),
        val recents: List<String> = emptyList(),
        val selectedFolder: String? = null
    )

    private val file get() = storageFile.toAbsolutePath().normalize()

    fun load(): State {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return State()
        require(!Files.isSymbolicLink(file) &&
            Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
        ) { "Preferências locais não são um arquivo comum." }
        require(Files.size(file) in 1L..MAX_BYTES) {
            "Preferências locais estão vazias ou excessivamente grandes."
        }
        val lines = Files.readAllLines(file, StandardCharsets.UTF_8)
        require(lines.size in 1..MAX_LINES && lines.first() == HEADER) {
            "Versão ou conteúdo inválido das preferências da Biblioteca."
        }
        val favorites = linkedSetOf<String>()
        val recents = mutableListOf<String>()
        var folder: String? = null
        lines.drop(1).forEach { record ->
            val separator = record.indexOf('\t')
            require(separator == 1 && record.length > 2) {
                "Registro inválido das preferências locais."
            }
            val decoded = String(
                Base64.getUrlDecoder().decode(record.substring(2)),
                StandardCharsets.UTF_8
            )
            require(validPath(decoded)) { "Caminho inválido nas preferências." }
            when (record[0]) {
                'F' -> { require(favorites.size < MAX_FAVORITES); favorites += decoded }
                'R' -> { require(recents.size < MAX_RECENTS); recents += decoded }
                'D' -> { require(folder == null); folder = decoded }
                else -> throw IllegalArgumentException("Tipo inválido de preferência.")
            }
        }
        require(recents.size == recents.distinct().size) {
            "Histórico de recentes duplicado."
        }
        return State(favorites, recents, folder)
    }

    @Synchronized
    fun save(state: State) {
        val parent = requireNotNull(file.parent) { "Diretório de preferências inválido." }
        // A aplicação é dona somente deste arquivo de preferências.
        Files.createDirectories(parent)
        require(!Files.isSymbolicLink(parent) &&
            Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
        ) { "Diretório de preferências não confiável." }
        require(!Files.exists(file, LinkOption.NOFOLLOW_LINKS) ||
            (!Files.isSymbolicLink(file) &&
                Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
        ) { "Não é permitido substituir links ou pastas." }

        val favorites = state.favorites.toList().sorted().take(MAX_FAVORITES)
        val recents = state.recents.distinct().take(MAX_RECENTS)
        val chosen = state.selectedFolder
        require((favorites + recents + listOfNotNull(chosen)).all(::validPath)) {
            "Não salvar caminhos inválidos."
        }
        fun line(prefix: Char, value: String): String {
            val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(
                value.toByteArray(StandardCharsets.UTF_8)
            )
            return prefix.toString() + "\t" + encoded + "\n"
        }
        val contents = buildString {
            append(HEADER).append('\n')
            if (chosen != null) append(line('D', chosen))
            favorites.forEach { append(line('F', it)) }
            recents.forEach { append(line('R', it)) }
        }
        require(contents.toByteArray(StandardCharsets.UTF_8).size <= MAX_BYTES) {
            "Preferências locais excederam o limite."
        }

        val temporary = Files.createTempFile(parent, ".bm-library-", ".tmp")
        try {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8)
            try {
                Files.move(temporary, file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    companion object {
        private const val HEADER = "BROTHER_MATRIZES_LIBRARY_ACTIVITY_V1"
        private const val MAX_FAVORITES = 50
        private const val MAX_RECENTS = 30
        private const val MAX_LINES = 1 + 1 + MAX_FAVORITES + MAX_RECENTS
        private const val MAX_BYTES = 256L * 1024L

        private fun validPath(value: String): Boolean =
            value.isNotBlank() && value.length <= 2048 &&
                runCatching { Path.of(value).isAbsolute }.getOrDefault(false)

        private fun defaultFile(): Path {
            val appData = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
            val root = if (appData != null) Path.of(appData) else
                Path.of(System.getProperty("user.home"), ".local", "share")
            return root.resolve("T.I. Machado")
                .resolve("Brother Matrizes")
                .resolve("library-activity-v1.tsv")
        }
    }
}
