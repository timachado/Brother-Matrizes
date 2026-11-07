package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.project.ProjectCodec
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Locale

/**
 * Transactional validated output for ONE item extracted after a server quota
 * reservation. Never wired to Windows UI or production accounts.
 *
 * MATRICES: validate using the official Android PES/DST/JEF loader, encode
 * through the official FLP v11 ProjectCodec, verify round-trip and persist
 * with CREATE_NEW under an explicitly designated local library folder.
 *
 * FONTS: validate actual TTF/OTF contents and glyphs through Skia, then
 * delegate deduplication/persistence to the existing Android-parity desktop
 * font library. No source archive item or existing project is overwritten.
 *
 * Process-only rollback; crash recovery/journal is still a release blocker.
 */
internal class DesktopArchiveValidatedLibrarySinkFactory(
    private val projectFolder: Path,
    private val fontFolder: Path
) : DesktopArchiveValidatedSinkFactory {
    override suspend fun begin(item: DesktopArchiveInventory.Item): DesktopArchiveValidatedSink {
        require(item.extension in when (item.category) {
            DesktopArchiveInventory.Category.MATRIX -> setOf("PES", "DST", "JEF")
            DesktopArchiveInventory.Category.FONT -> setOf("TTF", "OTF")
        }) { "Formato não permitido nesta categoria." }
        return LibraryTransaction(item)
    }

    private inner class LibraryTransaction(
        private val item: DesktopArchiveInventory.Item
    ) : DesktopArchiveValidatedSink {
        private var owned: Path? = null
        private var expectedDigest: String? = null
        private var complete = false

        override suspend fun validateAndStore(
            privateExtractedFile: Path
        ): DesktopArchiveImportCommit {
            check(owned == null) { "Transação não pode ser repetida." }
            val file = privateExtractedFile.toAbsolutePath().normalize()
            require(!Files.isSymbolicLink(file) &&
                Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) &&
                file.fileName.toString().lowercase(Locale.ROOT)
                    .endsWith("." + item.extension.lowercase(Locale.ROOT)) &&
                Files.size(file) == item.sizeBytes &&
                Files.size(file) in 1..(24L * 1024L * 1024L)
            ) { "Arquivo extraído inválido ou diferente da entrada autorizada." }

            return when (item.category) {
                DesktopArchiveInventory.Category.MATRIX -> persistMatrix(file)
                DesktopArchiveInventory.Category.FONT -> persistFont(file)
            }
        }

        private fun persistMatrix(source: Path): DesktopArchiveImportCommit {
            val destination = requireLocalDirectory(projectFolder)
            val design = DesktopEmbroideryFiles.open(source)
            val stitches = design.points.count { it.command == StitchCommand.STITCH }
            require(stitches > 0 && design.endFound &&
                design.bounds.widthMm > 0f && design.bounds.heightMm > 0f &&
                design.format.equals(item.extension, ignoreCase = true)
            ) { "A matriz não contém dados de bordado válidos." }

            val sourceHash = sha256(Files.readAllBytes(source))
            val fileName = "archive-" +
                item.extension.lowercase(Locale.ROOT) + "-" + sourceHash + ".flp"
            val target = destination.resolve(fileName)
            require(target.parent == destination && !Files.isSymbolicLink(target)) {
                "Destino de projeto FLP inseguro."
            }

            // Deterministic filename gives content-addressed deduplication.
            // The original import format is retained in the filename, and
            // points are stored as FLP via the official Android codec.
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                return duplicateMatrix(target, design)
            }

            val bytes = ProjectCodec.encode(design)
            val decoded = ProjectCodec.decode(bytes)
            require(decoded.points == design.points &&
                decoded.format.equals(design.format, ignoreCase = true)) {
                "O FLP não preservou as pontadas ou o formato original."
            }
            val digest = sha256(bytes)

            // Take ownership ONLY after CREATE_NEW succeeds. Immediately
            // marking ownership lets rollback remove a partially written file.
            Files.newByteChannel(
                target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
            ).use { channel ->
                owned = target
                expectedDigest = digest
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) {
                    check(channel.write(buffer) > 0) {
                        "Gravação FLP interrompida antes do final."
                    }
                }
            }
            require(Files.readAllBytes(target).contentEquals(bytes) &&
                DesktopFlpProjectFiles.open(target).points == design.points) {
                "Não foi possível verificar a matriz gravada na Biblioteca."
            }
            complete = true
            return DesktopArchiveImportCommit(true, fileName)
        }

        private fun duplicateMatrix(
            target: Path,
            expectedDesign: EmbroideryDesign
        ): DesktopArchiveImportCommit {
            require(!Files.isSymbolicLink(target) &&
                Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                "Colisão em arquivo de projeto existente."
            }
            val previous = DesktopFlpProjectFiles.open(target)
            require(previous.points == expectedDesign.points &&
                previous.format.equals(expectedDesign.format, ignoreCase = true)) {
                "O destino existente contém outra matriz. Nada foi substituído."
            }
            return DesktopArchiveImportCommit(false, null)
        }

        private fun persistFont(source: Path): DesktopArchiveImportCommit {
            val destination = requireLocalDirectory(fontFolder)
            val report = DesktopReadOnlyFontAnalyzer.inspect(source)
            require(report.format == item.extension &&
                report.testedGlyphs > 0) { "Fonte rejeitada pelo motor de contornos." }

            val library = DesktopImportedFontLibrary(destination)
            val known = library.list().mapTo(HashSet()) { it.id }
            val result = library.importFile(source, item.displayName)
            if (result.id in known) {
                return DesktopArchiveImportCommit(false, null)
            }
            val newPath = Path.of(result.absolutePath)
            require(newPath.parent == destination &&
                Files.isRegularFile(newPath, LinkOption.NOFOLLOW_LINKS) &&
                !Files.isSymbolicLink(newPath)
            ) { "Fonte foi armazenada fora da biblioteca autorizada." }
            owned = newPath
            expectedDigest = sha256(Files.readAllBytes(newPath))
            complete = true
            return DesktopArchiveImportCommit(true, result.id)
        }

        override suspend fun rollback() {
            val file = owned ?: return
            val expected = expectedDigest
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                owned = null
                return
            }
            require(!Files.isSymbolicLink(file) &&
                Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) &&
                file.parent == requireLocalDirectory(
                    if (item.category == DesktopArchiveInventory.Category.MATRIX)
                        projectFolder else fontFolder
                )
            ) { "Rollback recusado: destino foi alterado ou substituído." }
            // After a completed commit, never delete a different version
            // potentially modified by another process after creation.
            if (complete) {
                require(expected != null &&
                    sha256(Files.readAllBytes(file)) == expected) {
                    "Rollback recusado: arquivo foi modificado por outra operação."
                }
            }
            Files.delete(file)
            owned = null
            complete = false
        }
    }

    private fun requireLocalDirectory(folder: Path): Path {
        val target = folder.toAbsolutePath().normalize()
        require(!Files.isSymbolicLink(target) &&
            !Files.isSymbolicLink(target.parent) &&
            Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)
        ) { "Escolha uma pasta local existente, sem links simbólicos." }
        return target
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
