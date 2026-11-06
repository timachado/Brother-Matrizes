package com.timachado.brothermatrizes.desktop

import com.github.junrar.Archive
import com.github.junrar.ArchiveOptions
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale

/** Port de bibliotecas 7Z e RAR/RAR5 já utilizadas no Android 1.11.8.
 * Nenhum caminho do arquivo é extraído ao disco; somente matriz selecionada
 * é lida em memória e decodificada por DesktopMatrixService oficial.
 */
internal object DesktopExtendedArchiveImporter {
    private const val MAX_ITEM_BYTES = 24L * 1024 * 1024
    private const val MAX_DEPTH = 8
    private val noFollow = arrayOf(LinkOption.NOFOLLOW_LINKS)

    private enum class Kind { SEVEN_Z, RAR }

    private fun checkArchive(file: Path): Kind {
        require(Files.isRegularFile(file, *noFollow) &&
            !Files.isSymbolicLink(file)) {
            "Pacote deve ser arquivo regular, sem links simbólicos."
        }
        require(Files.size(file) in 1..DesktopArchiveImporter.MAX_ARCHIVE_BYTES) {
            "Pacote vazio ou maior que 128 MB."
        }
        val header = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS).use {
            it.readNBytes(8)
        }
        val kind = when {
            header.size >= 6 && header.copyOfRange(0, 6).contentEquals(
                byteArrayOf(0x37, 0x7a, 0xbc.toByte(),
                    0xaf.toByte(), 0x27, 0x1c)
            ) -> Kind.SEVEN_Z
            header.size >= 7 && header.copyOfRange(0, 7).contentEquals(
                byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x00)
            ) -> Kind.RAR
            header.size >= 8 && header.copyOfRange(0, 8).contentEquals(
                byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x01, 0x00)
            ) -> Kind.RAR
            else -> throw IllegalArgumentException(
                "Assinatura 7Z/RAR/RAR5 inválida."
            )
        }
        val ext = file.fileName.toString()
            .substringAfterLast('.', "").lowercase(Locale.ROOT)
        require((kind == Kind.SEVEN_Z && ext == "7z") ||
            (kind == Kind.RAR && ext == "rar")) {
            "Extensão não corresponde ao formato interno do arquivo."
        }
        return kind
    }

    private fun safeName(raw: String?): String? {
        val normalized = raw?.replace('\\', '/') ?: return null
        if (normalized.isEmpty() || normalized.length > 320 ||
            normalized.startsWith('/') || normalized.contains(':') ||
            normalized.any { it.code < 32 } ||
            normalized.split('/').any { it.isBlank() || it == "." || it == ".." } ||
            normalized.count { it == '/' } >= MAX_DEPTH) return null
        return normalized
    }

    private class Inspection {
        val items = mutableListOf<DesktopArchiveMatrix>()
        private val seen = HashSet<String>()
        private var index = 0
        private var total = 0L

        fun add(name: String?, size: Long, directory: Boolean) {
            val current = index++
            require(index <= DesktopArchiveImporter.MAX_ENTRIES) {
                "Pacote ultrapassa o limite de 300 entradas."
            }
            if (directory) return
            require(size in 0..MAX_ITEM_BYTES) {
                "Entrada sem tamanho confiável ou maior que 24 MB."
            }
            require(total <= DesktopArchiveImporter.MAX_TOTAL_UNPACKED_BYTES - size) {
                "Pacote excede 256 MB descompactados."
            }
            total += size
            val safe = safeName(name) ?: return
            require(seen.add(safe.lowercase(Locale.ROOT))) {
                "Pacote contém caminhos duplicados."
            }
            val fileName = safe.substringAfterLast('/')
            val format = fileName.substringAfterLast('.', "").uppercase(Locale.ROOT)
            if (format !in DesktopMatrixService.inputFormats ||
                size == 0L || size > DesktopMatrixService.MAX_INPUT_BYTES) return
            items += DesktopArchiveMatrix(current, safe, fileName, format, size)
        }
    }

    fun inspect(file: Path): List<DesktopArchiveMatrix> {
        val inspection = Inspection()
        when (checkArchive(file)) {
            Kind.SEVEN_Z -> SevenZFile.builder().setFile(file.toFile())
                .setMaxMemoryLimitKiB(32768).get().use { archive ->
                    for (entry in archive.entries) {
                        inspection.add(entry.name, entry.size, entry.isDirectory)
                    }
                }
            Kind.RAR -> Archive(file.toFile(), ArchiveOptions.builder()
                .maxDictionarySize(32L * 1024 * 1024).build()).use { archive ->
                    for (entry in archive.fileHeaders) {
                        inspection.add(entry.fileName, entry.fullUnpackSize,
                            entry.isDirectory)
                    }
                }
        }
        return inspection.items
    }

    fun openMatrix(file: Path, selected: DesktopArchiveMatrix): DesktopMatrixDocument {
        require(inspect(file).contains(selected)) {
            "A entrada não pertence ao pacote selecionado."
        }
        val bytes = when (checkArchive(file)) {
            Kind.SEVEN_Z -> SevenZFile.builder().setFile(file.toFile())
                .setMaxMemoryLimitKiB(32768).get().use { archive ->
                    var index = 0
                    var selectedBytes: ByteArray? = null
                    while (true) {
                        val entry = archive.nextEntry ?: break
                        if (index++ != selected.index) continue
                        require(safeName(entry.name) == selected.relativePath &&
                            entry.size == selected.bytes && !entry.isDirectory) {
                            "Entrada 7Z alterada desde o inventário."
                        }
                        selectedBytes = archive.getInputStream(entry).use {
                            it.readNBytes(DesktopMatrixService.MAX_INPUT_BYTES.toInt() + 1)
                        }
                        break
                    }
                    selectedBytes ?: error("Matriz não encontrada no 7Z.")
                }
            Kind.RAR -> Archive(file.toFile(), ArchiveOptions.builder()
                .maxDictionarySize(32L * 1024 * 1024).build()).use { archive ->
                    val entry = archive.fileHeaders.getOrNull(selected.index)
                        ?: error("Matriz não encontrada no RAR.")
                    require(safeName(entry.fileName) == selected.relativePath &&
                        entry.fullUnpackSize == selected.bytes && !entry.isDirectory) {
                        "Entrada RAR alterada desde o inventário."
                    }
                    val bounded = BoundedBuffer(DesktopMatrixService.MAX_INPUT_BYTES)
                    archive.extractFile(entry, bounded)
                    bounded.toByteArray()
                }
        }
        require(bytes.size.toLong() == selected.bytes) {
            "O tamanho real da matriz não corresponde ao inventário."
        }
        return DesktopMatrixService.open(selected.fileName, bytes)
    }

    private class BoundedBuffer(private val max: Long): ByteArrayOutputStream() {
        override fun write(b: Int) {
            require(size().toLong() < max) { "RAR excedeu o limite de 8 MB." }
            super.write(b)
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            require(len >= 0 && size().toLong() + len <= max) {
                "RAR excedeu o limite de 8 MB."
            }
            super.write(b, off, len)
        }
    }
}
