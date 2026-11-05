package com.timachado.brothermatrizes.desktop

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Primeira etapa do importador de pacotes Android no Desktop.
 * Faz inventário sem extrair caminhos; lê somente a entrada escolhida
 * em memória e a valida pelo MESMO motor Android do DesktopMatrixService.
 *
 * ZIP nesta etapa. RAR / RAR5 / 7Z do Android exigem porte e testes separados.
 */
internal data class DesktopArchiveMatrix(
    val index: Int,
    val relativePath: String,
    val fileName: String,
    val format: String,
    val bytes: Long
) {
    override fun toString(): String = "$relativePath · $format · $bytes bytes"
}

internal object DesktopArchiveImporter {
    const val MAX_ARCHIVE_BYTES = 128L * 1024 * 1024
    const val MAX_TOTAL_UNPACKED_BYTES = 256L * 1024 * 1024
    const val MAX_ENTRIES = 300
    private const val MAX_DEPTH = 8
    private const val MAX_RATIO = 1000L
    private val noFollow = arrayOf(LinkOption.NOFOLLOW_LINKS)

    private fun validatePath(archive: Path) {
        require(Files.isRegularFile(archive, *noFollow) &&
            !Files.isSymbolicLink(archive)) {
            "Selecione um ZIP local sem atalhos simbólicos."
        }
        require(Files.size(archive) in 1..MAX_ARCHIVE_BYTES) {
            "Pacote ZIP inválido ou maior que 128 MB."
        }
        val signature = Files.newInputStream(archive, LinkOption.NOFOLLOW_LINKS)
            .use { it.readNBytes(4) }
        require(signature.contentEquals(byteArrayOf(0x50, 0x4b, 0x03, 0x04))) {
            "Arquivo não possui assinatura de ZIP válida."
        }
    }

    private fun safeEntryName(raw: String): String? {
        val name = raw.replace('\\', '/')
        if (name.length > 320 || name.startsWith('/') ||
            name.any { it.code < 32 } || name.contains(':') ||
            name.split('/').any { it.isBlank() || it == "." || it == ".." } ||
            name.count { it == '/' } >= MAX_DEPTH) return null
        return name
    }

    /**
     * O inventário é limitado como no app Android 1.11.8.
     * Nenhum arquivo do pacote é escrito no disco durante o exame.
     */
    fun inspect(archive: Path): List<DesktopArchiveMatrix> {
        validatePath(archive)
        val accepted = mutableListOf<DesktopArchiveMatrix>()
        val seenPaths = HashSet<String>()
        var scanned = 0
        var total = 0L
        ZipFile(archive.toFile()).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val item = entries.nextElement()
                val index = scanned++
                require(scanned <= MAX_ENTRIES) {
                    "ZIP com mais de 300 entradas: importação recusada."
                }
                if (item.isDirectory) continue
                val size = item.size
                require(size in 0..(24L * 1024 * 1024)) {
                    "Entrada desconhecida ou maior que 24 MB."
                }
                require(total <= MAX_TOTAL_UNPACKED_BYTES - size) {
                    "ZIP com mais de 256 MB descompactados."
                }
                total += size
                if (item.compressedSize > 0) {
                    require(size <= item.compressedSize * MAX_RATIO) {
                        "Entrada ZIP com taxa de compressão excessiva."
                    }
                }
                val safe = safeEntryName(item.name) ?: continue
                require(seenPaths.add(safe.lowercase(Locale.ROOT))) {
                    "ZIP contém caminhos duplicados."
                }
                val fileName = safe.substringAfterLast('/')
                val extension = fileName.substringAfterLast('.', "").uppercase(Locale.ROOT)
                if (extension !in DesktopMatrixService.inputFormats ||
                    size == 0L || size > DesktopMatrixService.MAX_INPUT_BYTES) continue
                accepted += DesktopArchiveMatrix(index, safe, fileName, extension, size)
            }
        }
        return accepted
    }

    fun openMatrix(archive: Path, selected: DesktopArchiveMatrix): DesktopMatrixDocument {
        // Examinar de novo no momento da abertura evita confiar em um
        // inventário antigo ou parâmetros inventados pelo chamador.
        require(inspect(archive).contains(selected)) {
            "A entrada selecionada não pertence ao ZIP atual."
        }
        ZipFile(archive.toFile()).use { zip ->
            val entries = zip.entries()
            var index = 0
            while (entries.hasMoreElements()) {
                val item = entries.nextElement()
                if (index++ != selected.index) continue
                require(item.name.replace('\\', '/') == selected.relativePath &&
                    item.size == selected.bytes && !item.isDirectory) {
                    "A entrada ZIP mudou durante a importação."
                }
                val content = zip.getInputStream(item).use {
                    it.readNBytes(DesktopMatrixService.MAX_INPUT_BYTES.toInt() + 1)
                }
                require(content.size.toLong() == selected.bytes) {
                    "Tamanho real difere do inventário do ZIP."
                }
                return DesktopMatrixService.open(selected.fileName, content)
            }
        }
        error("Matriz não encontrada no ZIP selecionado.")
    }
}
