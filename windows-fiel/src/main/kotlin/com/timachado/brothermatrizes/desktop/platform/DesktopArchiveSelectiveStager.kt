package com.timachado.brothermatrizes.desktop.platform

import com.github.junrar.Archive
import com.github.junrar.ArchiveOptions
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.CRC32
import java.util.zip.ZipFile

/**
 * Single-item, bounded, private STAGING for ZIP, 7Z, RAR4 and RAR5.
 *
 * Reuses Android SafeArchiveExtractor's algorithm and memory caps.
 * An archive entry's path is never used as an output path: all bytes go to
 * one unpredictably named temp file, deleted by Staged.close().
 *
 * Not an importer: it does NOT validate embroidery/font semantics, publish
 * projects, charge a quota, or connect to a network. Only invoke through an
 * approved, post-reservation transaction factory. Not wired to Windows UI.
 */
internal object DesktopArchiveSelectiveStager {
    private const val MAX_ENTRY = 24L * 1024L * 1024L

    class Staged internal constructor(
        val file: Path,
        val bytes: Long
    ) : AutoCloseable {
        override fun close() {
            // Never delete a user-selected original archive or existing file.
            // Both paths were created by this invocation with unpredictable names.
            Files.deleteIfExists(file)
            Files.deleteIfExists(checkNotNull(file.parent))
        }
    }

    fun extract(
        archive: Path,
        expected: DesktopArchiveInventory.Preview,
        item: DesktopArchiveInventory.Item,
        cancelled: () -> Boolean = { false },
        stagingParent: Path? = null, // QA: test cleanup inside an isolated temporary folder
        password: CharArray? = null // transient; never logged or persisted
    ): Staged {
        // Synthetic screenshot previews have no fingerprint and can NEVER
        // become inputs to selective extraction or a quota transaction.
        val fingerprint = expected.archiveSha256
        require(fingerprint != null && Regex("[0-9a-f]{64}").matches(fingerprint)) {
            "Reabra o pacote original para autorizar a importação."
        }
        require(!cancelled()) { "Importação cancelada." }

        // Detect replacement of the original archive between inspection
        // and extraction (including equal-length and equal-name tampering).
        val examined = DesktopArchiveInventory.inspect(archive, password)
        require(examined.archiveSha256 == fingerprint &&
            examined.archiveName == expected.archiveName &&
            examined.format == expected.format &&
            examined.entries == expected.entries &&
            examined.scannedCount == expected.scannedCount &&
            examined.uncompressedBytes == expected.uncompressedBytes &&
            expected.entries.any { it == item }) {
            "O conteúdo do pacote mudou. Analise-o novamente."
        }

        val stageDir = if (stagingParent == null) {
            Files.createTempDirectory("brother-archive-private-")
        } else {
            val root = stagingParent.toAbsolutePath().normalize()
            require(!Files.isSymbolicLink(root) &&
                Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                "Área de teste temporária inválida."
            }
            Files.createTempDirectory(root, "brother-archive-private-")
        }
        var file: Path? = null
        var completed = false
        try {
            require(!Files.isSymbolicLink(stageDir) &&
                Files.isDirectory(stageDir, LinkOption.NOFOLLOW_LINKS)) {
                "Área temporária privada indisponível."
            }
            // Do NOT use item.entryName or any user path as file name.
            val output = Files.createTempFile(
                stageDir, "single-item-", "." + item.extension.lowercase()
            )
            file = output
            var written = 0L
            Files.newOutputStream(
                output, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING,
                LinkOption.NOFOLLOW_LINKS
            ).use { stream ->
                BoundedOutput(stream, cancelled).use { bounded ->
                    when (expected.format) {
                        DesktopArchiveInventory.Format.ZIP -> {
                            ZipFile(archive.toFile()).use { zip ->
                                val entries = zip.entries()
                                var index = 0
                                var found = false
                                while (entries.hasMoreElements()) {
                                    val entry = entries.nextElement()
                                    if (index++ != item.index) continue
                                    check(!entry.isDirectory &&
                                        entry.name.replace('\\', '/') == item.entryName &&
                                        entry.size == item.sizeBytes) {
                                        "A entrada ZIP foi alterada."
                                    }
                                    zip.getInputStream(entry).use { input ->
                                        copyLimited(input, bounded)
                                    }
                                    require(entry.crc == bounded.crcValue) {
                                        "CRC do item ZIP inválido."
                                    }
                                    found = true
                                    break
                                }
                                check(found) { "Entrada ZIP não encontrada." }
                            }
                        }
                        DesktopArchiveInventory.Format.SEVEN_Z -> {
                            SevenZFile.builder().setFile(archive.toFile())
                                .setMaxMemoryLimitKiB(32768)
                                .apply {
                                    if (password != null && password.isNotEmpty())
                                        setPassword(password)
                                }.get().use { seven ->
                                    var index = 0
                                    var found = false
                                    while (true) {
                                        val entry = seven.nextEntry ?: break
                                        if (index++ != item.index) continue
                                        check(!entry.isDirectory &&
                                            entry.name.replace('\\', '/') == item.entryName &&
                                            entry.size == item.sizeBytes) {
                                            "A entrada 7Z foi alterada."
                                        }
                                        seven.getInputStream(entry).use { input ->
                                            copyLimited(input, bounded)
                                        }
                                        found = true
                                        break
                                    }
                                    check(found) { "Entrada 7Z não encontrada." }
                                }
                        }
                        DesktopArchiveInventory.Format.RAR -> {
                            Archive(
                                archive.toFile(), ArchiveOptions.builder()
                                    .maxDictionarySize(32L * 1024L * 1024L)
                                    .apply {
                                        if (password != null && password.isNotEmpty())
                                            password(password)
                                    }.build()
                            ).use { rar ->
                                val entry = rar.fileHeaders.getOrNull(item.index)
                                    ?: error("Entrada RAR não encontrada.")
                                check(!entry.isDirectory &&
                                    entry.fileName.replace('\\', '/') == item.entryName &&
                                    entry.fullUnpackSize == item.sizeBytes) {
                                    "A entrada RAR foi alterada."
                                }
                                rar.extractFile(entry, bounded)
                            }
                        }
                    }
                    written = bounded.bytes
                }
            }
            require(written == item.sizeBytes && written in 1..MAX_ENTRY) {
                "Conteúdo extraído diferente do tamanho declarado."
            }
            require(!cancelled()) { "Importação cancelada." }
            require(DesktopArchiveInventory.sha256(archive) == fingerprint) {
                "O pacote foi alterado durante a extração."
            }
            completed = true
            return Staged(output, written)
        } finally {
            if (!completed) {
                file?.let { Files.deleteIfExists(it) }
                Files.deleteIfExists(stageDir)
            }
        }
    }

    private fun copyLimited(input: java.io.InputStream, output: OutputStream) {
        val buffer = ByteArray(16384)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            if (size > 0) output.write(buffer, 0, size)
        }
    }

    private class BoundedOutput(
        private val delegate: OutputStream,
        private val cancelled: () -> Boolean
    ) : OutputStream() {
        private var count = 0L
        private val crc = CRC32()
        val bytes get() = count
        val crcValue get() = crc.value

        private fun checkCapacity(added: Int) {
            check(!cancelled()) { "Importação cancelada." }
            require(added >= 0 && count <= MAX_ENTRY - added) {
                "Entrada excedeu 24 MB durante extração."
            }
        }

        override fun write(b: Int) {
            checkCapacity(1)
            delegate.write(b)
            crc.update(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            checkCapacity(len)
            delegate.write(b, off, len)
            crc.update(b, off, len)
            count += len
        }

        override fun flush() = delegate.flush()
        override fun close() = delegate.close()
    }
}
