package com.timachado.brothermatrizes.desktop.platform

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.text.Normalizer
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Read-only Windows inventory of a user-selected local ZIP.
 *
 * Limits and compatible types mirror Android SafeArchiveExtractor. This class
 * deliberately does not extract, import, persist, charge quotas, open files
 * inside the archive, or invent authorization for commercial tools.
 * RAR and 7Z require separate, licensed and authenticated adapters.
 */
internal object DesktopArchiveInventory {
    private const val MAX_ARCHIVE = 128L * 1024L * 1024L
    private const val MAX_ENTRY = 24L * 1024L * 1024L
    private const val MAX_TOTAL = 256L * 1024L * 1024L
    private const val MAX_ENTRIES = 300
    private const val MAX_DEPTH = 8
    private const val MAX_RATIO = 1000L

    enum class Category { MATRIX, FONT }

    data class Item(
        val index: Int,
        val entryName: String,
        val displayName: String,
        val extension: String,
        val sizeBytes: Long,
        val category: Category
    )

    data class Preview(
        val archiveName: String,
        val entries: List<Item>,
        val incompatibleCount: Int,
        val scannedCount: Int,
        val uncompressedBytes: Long
    ) {
        val matrixCount get() = entries.count { it.category == Category.MATRIX }
        val fontCount get() = entries.count { it.category == Category.FONT }
    }

    fun inspect(chosenArchive: Path): Preview {
        val archive = chosenArchive.toAbsolutePath().normalize()
        require(archive.fileName.toString().lowercase(Locale.ROOT).endsWith(".zip")) {
            "Esta etapa Windows aceita somente arquivos .zip."
        }
        require(!Files.isSymbolicLink(archive) &&
            !Files.isSymbolicLink(archive.parent) &&
            Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS)) {
            "Selecione um ZIP local regular, sem link simbólico."
        }
        require(Files.size(archive) in 1..MAX_ARCHIVE) {
            "O ZIP precisa ter até 128 MB."
        }

        val names = HashSet<String>()
        val items = ArrayList<Item>()
        var scanned = 0
        var incompatible = 0
        var total = 0L
        ZipFile(archive.toFile()).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val index = scanned++
                require(scanned <= MAX_ENTRIES) {
                    "O ZIP excede o máximo de 300 entradas."
                }
                if (entry.isDirectory) continue

                val size = entry.size
                require(size in 0..MAX_ENTRY) {
                    "Entrada do ZIP sem tamanho válido ou maior que 24 MB."
                }
                require(total <= MAX_TOTAL - size) {
                    "O ZIP excede 256 MB descompactados."
                }
                total += size

                val candidate = candidate(index, entry.name, size, entry.compressedSize)
                val folded = candidate?.let {
                    Normalizer.normalize(it.entryName, Normalizer.Form.NFC)
                        .lowercase(Locale.ROOT)
                }
                if (candidate == null || !names.add(checkNotNull(folded))) {
                    incompatible++
                } else {
                    items += candidate
                }
            }
        }
        require(scanned > 0) { "O ZIP não contém entradas." }
        return Preview(
            archiveName = archive.fileName.toString(),
            entries = items,
            incompatibleCount = incompatible,
            scannedCount = scanned,
            uncompressedBytes = total
        )
    }

    private fun candidate(
        index: Int,
        original: String,
        size: Long,
        packed: Long
    ): Item? {
        val path = original.replace('\\', '/')
        if (path.length !in 1..320 || path.startsWith('/') ||
            path.contains(':') || path.count { it == '/' } >= MAX_DEPTH ||
            path.split('/').any { it.isBlank() || it == "." || it == ".." } ||
            path.any { it.isISOControl() ||
                it in "\u202A\u202B\u202C\u202D\u202E\u2066\u2067\u2068\u2069" }
        ) return null
        if (packed > 0 && size > packed * MAX_RATIO) return null
        if (packed < 0) return null

        val base = path.substringAfterLast('/')
        if (base.startsWith(' ') || base.endsWith(' ') || base.endsWith('.') ||
            base.any { it in "<>\"|?*" }
        ) return null
        val stem = base.substringBefore('.').uppercase(Locale.ROOT)
        if (stem in setOf("CON", "PRN", "AUX", "NUL") ||
            stem.matches(Regex("COM[1-9]|LPT[1-9]"))
        ) return null

        val ext = base.substringAfterLast('.', "").uppercase(Locale.ROOT)
        val category = when (ext) {
            "PES", "DST", "JEF" -> Category.MATRIX
            "TTF", "OTF" -> Category.FONT
            else -> return null
        }
        return Item(index, path, base, ext, size, category)
    }
}
