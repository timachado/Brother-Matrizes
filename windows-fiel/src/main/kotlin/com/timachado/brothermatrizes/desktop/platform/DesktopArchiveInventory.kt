package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.archive.ArchiveCategory
import com.timachado.brothermatrizes.core.archive.ArchiveFormat
import com.timachado.brothermatrizes.core.archive.SafeArchiveExtractor
import com.github.junrar.Archive
import com.github.junrar.ArchiveOptions
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Read-only Windows inventory of user-selected local ZIP, 7Z or RAR/RAR5.
 *
 * Limits and compatible types mirror Android SafeArchiveExtractor. This class
 * deliberately does not extract, import, persist, charge quotas, open files
 * inside the archive, or invent authorization for commercial tools.
 * Uses the same third-party metadata readers already in the Android app.
 * Optional RAR/7Z password mirrors SafeArchiveExtractor Android; never persisted.
 */
internal object DesktopArchiveInventory {
    private const val MAX_ARCHIVE = 128L * 1024L * 1024L
    private const val MAX_ENTRY = 24L * 1024L * 1024L
    private const val MAX_TOTAL = 256L * 1024L * 1024L
    private const val MAX_ENTRIES = 300
    private const val MAX_DEPTH = 8
    private const val MAX_RATIO = 1000L

    enum class Category { MATRIX, FONT }
    enum class Format { ZIP, RAR, SEVEN_Z }

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
        val uncompressedBytes: Long,
        val format: Format = Format.ZIP,
        // Binding for a user-selected archive: a same-name or same-size
        // replacement cannot silently be imported in a later transaction.
        // Legacy synthetic screen QA previews intentionally have no hash.
        val archiveSha256: String? = null
    ) {
        val matrixCount get() = entries.count { it.category == Category.MATRIX }
        val fontCount get() = entries.count { it.category == Category.FONT }
    }

    fun inspect(chosenArchive: Path, password: CharArray? = null): Preview {
        val archive = chosenArchive.toAbsolutePath().normalize()
        val extension = archive.fileName.toString()
            .substringAfterLast('.', "").lowercase(Locale.ROOT)
        require(extension in setOf("zip", "rar", "7z")) {
            "Selecione um arquivo .zip, .rar ou .7z."
        }
        require(!Files.isSymbolicLink(archive) &&
            !Files.isSymbolicLink(archive.parent) &&
            Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS)) {
            "Selecione um pacote local regular, sem link simbólico."
        }
        require(Files.size(archive) in 1..MAX_ARCHIVE) {
            "O pacote precisa ter até 128 MB."
        }

        // Match the original Android magic-byte checks. An extension alone
        // is never evidence that a package uses the asserted format.
        val header = Files.newInputStream(
            archive, LinkOption.NOFOLLOW_LINKS
        ).use { it.readNBytes(8) }
        val format = identify(header)
        val sourceSha = sha256(archive)
        require(
            (extension == "zip" && format == Format.ZIP) ||
                (extension == "rar" && format == Format.RAR) ||
                (extension == "7z" && format == Format.SEVEN_Z)
        ) { "Extensão e assinatura do pacote não coincidem." }

        val names = HashSet<String>()
        val items = ArrayList<Item>()
        var scanned = 0
        var incompatible = 0
        var total = 0L

        fun review(
            entryName: String?,
            sizeBytes: Long,
            packedSize: Long,
            isDirectory: Boolean
        ) {
            val index = scanned++
            require(scanned <= MAX_ENTRIES) {
                "O pacote excede o máximo de 300 entradas."
            }
            if (isDirectory) return
            require(sizeBytes in 0..MAX_ENTRY) {
                "Entrada sem tamanho válido ou maior que 24 MB."
            }
            require(total <= MAX_TOTAL - sizeBytes) {
                "O pacote excede 256 MB descompactados."
            }
            total += sizeBytes

            val candidate = candidate(
                index, entryName.orEmpty(), sizeBytes, packedSize
            )
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

        when (format) {
            Format.ZIP -> ZipFile(archive.toFile()).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    review(entry.name, entry.size, entry.compressedSize, entry.isDirectory)
                }
            }
            Format.SEVEN_Z -> SevenZFile.builder()
                .setFile(archive.toFile())
                .setMaxMemoryLimitKiB(32768)
                .apply {
                    if (password != null && password.isNotEmpty()) setPassword(password)
                }
                .get().use { seven ->
                    // Metadata-only; never call getInputStream, read or extract.
                    for (entry in seven.entries) {
                        review(entry.name, entry.size, -1L, entry.isDirectory)
                    }
                }
            Format.RAR -> Archive(
                archive.toFile(),
                ArchiveOptions.builder()
                    .maxDictionarySize(32L * 1024L * 1024L)
                    .apply {
                        if (password != null && password.isNotEmpty()) password(password)
                    }.build()
            ).use { rar ->
                // Metadata-only; never call extractFile or uncompress.
                for (entry in rar.fileHeaders) {
                    review(
                        entry.fileName, entry.fullUnpackSize, -1L,
                        entry.isDirectory
                    )
                }
            }
        }
        require(scanned > 0) { "O pacote não contém entradas." }
        return Preview(
            archiveName = archive.fileName.toString(),
            entries = items,
            incompatibleCount = incompatible,
            scannedCount = scanned,
            uncompressedBytes = total,
            format = format,
            archiveSha256 = sourceSha
        )
    }

    internal fun sha256(file: Path): String {
        require(!Files.isSymbolicLink(file) &&
            Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) &&
            Files.size(file) in 1..MAX_ARCHIVE) {
            "Pacote original alterado ou acima de 128 MB."
        }
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS).use { input ->
            val buffer = ByteArray(16384)
            var total = 0L
            while (true) {
                val received = input.read(buffer)
                if (received < 0) break
                if (received == 0) continue
                total += received
                check(total <= MAX_ARCHIVE) { "O pacote mudou durante a leitura." }
                digest.update(buffer, 0, received)
            }
            require(total > 0) { "Pacote vazio." }
        }
        return digest.digest().joinToString("") { b ->
            "%02x".format(b.toInt() and 0xff)
        }
    }

    private fun identify(header: ByteArray): Format = when (
        SafeArchiveExtractor.identify(header) // Android original
    ) {
        ArchiveFormat.ZIP -> Format.ZIP
        ArchiveFormat.RAR -> Format.RAR
        ArchiveFormat.SEVEN_Z -> Format.SEVEN_Z
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
        // Core Android decides which embroidery/font files are compatible.
        // The Windows-specific checks below only reject unsafe filenames.
        val original = SafeArchiveExtractor.describe(index, path, size, packed)
            ?: return null
        val base = path.substringAfterLast('/')
        if (base.startsWith(' ') || base.endsWith(' ') || base.endsWith('.') ||
            base.any { it in "<>\"|?*" }
        ) return null
        val stem = base.substringBefore('.').uppercase(Locale.ROOT)
        if (stem in setOf("CON", "PRN", "AUX", "NUL") ||
            stem.matches(Regex("COM[1-9]|LPT[1-9]"))
        ) return null

        val category = when (original.category) {
            ArchiveCategory.MATRIX -> Category.MATRIX
            ArchiveCategory.FONT -> Category.FONT
        }
        return Item(index, path, base, original.extension, size, category)
    }
}
