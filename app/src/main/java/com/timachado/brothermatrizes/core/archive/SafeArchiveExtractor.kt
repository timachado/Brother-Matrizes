package com.timachado.brothermatrizes.core.archive

import android.content.Context
import android.net.Uri
import com.github.junrar.Archive
import com.github.junrar.ArchiveOptions
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipFile

enum class ArchiveFormat { ZIP, RAR, SEVEN_Z }
enum class ArchiveCategory { MATRIX, FONT }
data class ArchiveItem(
    val index: Int,
    val path: String,
    val name: String,
    val extension: String,
    val sizeBytes: Long,
    val category: ArchiveCategory
)
data class ArchiveInventory(
    val format: ArchiveFormat,
    val sourceFile: File,
    val entries: List<ArchiveItem>,
    val incompatibleCount: Int,
    val scannedCount: Int
)

/**
 * Single-pass bounded metadata examination and SELECTIVE extraction only.
 * Never materializes paths from an archive; data goes to a random private
 * file and is then checked by the existing PES/DST/JEF/TTF/OTF validator.
 *
 * ZIP: Android java.util.zip; 7Z: Apache Commons Compress (Apache-2.0);
 * RAR/RAR5: junrar UnRAR license (decompression only). No recursion.
 */
object SafeArchiveExtractor {
    const val MAX_ARCHIVE = 128L * 1024 * 1024
    const val MAX_ENTRY = 24L * 1024 * 1024
    const val MAX_TOTAL = 256L * 1024 * 1024
    const val MAX_ENTRIES = 300
    const val MAX_DEPTH = 8
    private const val MAX_RATIO = 1000L

    internal fun validName(path: String?): String? {
        val safe = path?.replace('\\', '/') ?: return null
        if (safe.length > 320 || safe.startsWith('/') || safe.any { it.code < 32 } ||
            safe.contains(':') || safe.split('/').any { it.isBlank() || it == "." || it == ".." } ||
            safe.count { it == '/' } >= MAX_DEPTH) return null
        return safe
    }

    internal fun describe(index: Int, path: String?, size: Long,
                          packed: Long = -1L): ArchiveItem? {
        val name = validName(path) ?: return null
        if (size < 0 || size > MAX_ENTRY) return null
        if (packed > 0 && size > packed * MAX_RATIO) return null
        val base = name.substringAfterLast('/')
        val suffix = base.substringAfterLast('.', "").uppercase()
        val type = when (suffix) {
            "PES", "DST", "JEF" -> ArchiveCategory.MATRIX
            "TTF", "OTF" -> ArchiveCategory.FONT
            else -> return null
        }
        return ArchiveItem(index, name, base, suffix, size, type)
    }

    private fun inspectSize(count: Int, size: Long, sum: Long): Long {
        require(count <= MAX_ENTRIES) { "O pacote tem arquivos demais (máximo: $MAX_ENTRIES)." }
        require(size >= 0 && size <= MAX_ENTRY) { "Entrada excede o limite de 24 MB." }
        require(sum + size <= MAX_TOTAL) { "Pacote excede 256 MB descompactados." }
        return sum + size
    }

    fun identify(header: ByteArray): ArchiveFormat {
        if (header.size >= 4 &&
            header[0] == 0x50.toByte() && header[1] == 0x4b.toByte() &&
            header[2] == 0x03.toByte() && header[3] == 0x04.toByte())
            return ArchiveFormat.ZIP
        if (header.size >= 6 && header.sliceArray(0..5).contentEquals(
                byteArrayOf(0x37, 0x7a, 0xbc.toByte(), 0xaf.toByte(), 0x27, 0x1c)))
            return ArchiveFormat.SEVEN_Z
        if (header.size >= 7 && header.sliceArray(0..6).contentEquals(
                byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x00)))
            return ArchiveFormat.RAR
        if (header.size >= 8 && header.sliceArray(0..7).contentEquals(
                byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x01, 0x00)))
            return ArchiveFormat.RAR
        error("Formato de arquivo não reconhecido. Use ZIP, RAR, RAR5 ou 7Z.")
    }

    fun isArchive(context: Context, uri: Uri): Boolean {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(
                android.provider.OpenableColumns.DISPLAY_NAME
            ), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
        return listOf(".zip", ".rar", ".7z").any { name.lowercase().endsWith(it) }
    }

    fun stage(context: Context, uri: Uri, password: CharArray? = null): ArchiveInventory {
        val file = File(context.cacheDir, "archive-${UUID.randomUUID()}.bin")
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: error("Não foi possível abrir o pacote selecionado.")
            stream.use { input ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(16384)
                    var count = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        count += read
                        require(count <= MAX_ARCHIVE) {
                            "Pacote maior que 128 MB; utilize outro aplicativo."
                        }
                        out.write(buffer, 0, read)
                    }
                }
            }
            return inspect(file, password)
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    /** Metadata-only inspector for files staged in private storage. */
    internal fun inspect(file: File, password: CharArray? = null): ArchiveInventory {
        require(file.length() <= MAX_ARCHIVE) { "Pacote maior que 128 MB." }
            val header = file.inputStream().use { stream ->
                val bytes = ByteArray(8)
                val read = stream.read(bytes)
                if (read < 0) ByteArray(0) else bytes.copyOf(read)
            }
            val format = identify(header)
            val items = mutableListOf<ArchiveItem>()
            var incompatible = 0
            var seen = 0
            var sizeSum = 0L
            when (format) {
                ArchiveFormat.ZIP -> ZipFile(file).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val index = seen++
                        require(seen <= MAX_ENTRIES) { "ZIP contém arquivos demais." }
                        if (entry.isDirectory) continue
                        val size = entry.size
                        sizeSum = inspectSize(seen, size, sizeSum)
                        val candidate = describe(index, entry.name, size, entry.compressedSize)
                        if (candidate == null) incompatible++ else items += candidate
                    }
                }
                ArchiveFormat.SEVEN_Z -> SevenZFile.builder()
                    .setFile(file).setMaxMemoryLimitKiB(32768)
                    .apply {
                        if (password != null && password.isNotEmpty()) setPassword(password)
                    }.get().use { seven ->
                        for (entry in seven.entries) {
                            val index = seen++
                            require(seen <= MAX_ENTRIES) { "7Z contém arquivos demais." }
                            if (entry.isDirectory) continue
                            sizeSum = inspectSize(seen, entry.size, sizeSum)
                            val candidate = describe(index, entry.name, entry.size)
                            if (candidate == null) incompatible++ else items += candidate
                        }
                    }
                ArchiveFormat.RAR -> Archive(
                    file, ArchiveOptions.builder()
                        .maxDictionarySize(32L * 1024 * 1024)
                        .apply {
                            if (password != null && password.isNotEmpty()) password(password)
                        }.build()
                ).use { rar ->
                    for (entry in rar.fileHeaders) {
                        val index = seen++
                        require(seen <= MAX_ENTRIES) { "RAR contém arquivos demais." }
                        if (entry.isDirectory) continue
                        val size = entry.fullUnpackSize
                        sizeSum = inspectSize(seen, size, sizeSum)
                        val candidate = describe(index, entry.fileName, size)
                        if (candidate == null) incompatible++ else items += candidate
                    }
                }
            }
            return ArchiveInventory(format, file, items, incompatible, seen)
    }

    private class LimitedOutput(
        private val delegate: OutputStream,
        private val cap: Long,
        private val cancelled: () -> Boolean
    ) : OutputStream() {
        private var count = 0L
        override fun write(b: Int) {
            check(!cancelled()) { "Operação cancelada." }
            if (++count > cap) error("Extração excedeu limite seguro.")
            delegate.write(b)
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            check(!cancelled()) { "Operação cancelada." }
            count += len
            require(count <= cap) { "Extração excedeu 24 MB." }
            delegate.write(b, off, len)
        }
        override fun flush() = delegate.flush()
    }

    private fun copyLimited(input: InputStream, output: OutputStream,
                            cancelled: () -> Boolean) {
        val buffer = ByteArray(16384)
        while (true) {
            check(!cancelled()) { "Operação cancelada." }
            val len = input.read(buffer)
            if (len < 0) break
            output.write(buffer, 0, len)
        }
    }

    /**
     * Only one *selected* item is written, to our private flat cache name.
     * Caller must delete the returned file in finally after validation/import.
     */
    fun extract(context: Context, inventory: ArchiveInventory,
                item: ArchiveItem, password: CharArray? = null,
                cancelled: () -> Boolean = { false }): File {
        require(inventory.entries.any { it.index == item.index && it.path == item.path }) {
            "Entrada não pertence a este pacote."
        }
        check(inventory.sourceFile.isFile) {
            "O pacote temporário não está mais disponível. Abra o ZIP novamente."
        }
        val destination = File(context.cacheDir,
            "brother-matrix-${UUID.randomUUID()}.${item.extension.lowercase()}")
        try {
            destination.outputStream().use { stream ->
                LimitedOutput(stream, MAX_ENTRY, cancelled).use { limited ->
                    when (inventory.format) {
                        ArchiveFormat.ZIP -> ZipFile(inventory.sourceFile).use { zip ->
                            val entries = zip.entries()
                            var idx = 0
                            var found = false
                            while (entries.hasMoreElements()) {
                                val z = entries.nextElement()
                                if (idx++ == item.index) {
                                    require(z.name == item.path)
                                    zip.getInputStream(z).use { copyLimited(it, limited, cancelled) }
                                    found = true
                                    break
                                }
                            }
                            check(found) { "Entrada não encontrada no ZIP." }
                        }
                        ArchiveFormat.SEVEN_Z -> {
                            val builder = SevenZFile.builder()
                                .setFile(inventory.sourceFile)
                                .setMaxMemoryLimitKiB(32768)
                            if (password != null && password.isNotEmpty())
                                builder.setPassword(password)
                            builder.get().use { seven ->
                                var idx = 0
                                var found = false
                                while (true) {
                                    val entry = seven.nextEntry ?: break
                                    if (idx++ == item.index) {
                                        check(entry.name == item.path)
                                        seven.getInputStream(entry).use {
                                            copyLimited(it, limited, cancelled)
                                        }
                                        found = true
                                        break
                                    }
                                }
                                check(found) { "Entrada não encontrada no 7Z." }
                            }
                        }
                        ArchiveFormat.RAR -> {
                            val options = ArchiveOptions.builder()
                                .maxDictionarySize(32L * 1024 * 1024)
                            if (password != null && password.isNotEmpty()) {
                                options.password(password)
                            }
                            val archive = Archive(inventory.sourceFile, options.build())
                            archive.use { rar ->
                                val entry = rar.fileHeaders.getOrNull(item.index)
                                    ?: error("Entrada RAR não encontrada.")
                                check(entry.fileName == item.path)
                                rar.extractFile(entry, limited)
                            }
                        }
                    }
                }
            }
            require(destination.length() <= MAX_ENTRY) { "Limite de extração excedido." }
            return destination
        } catch (e: Exception) {
            destination.delete()
            throw e
        }
    }
}
