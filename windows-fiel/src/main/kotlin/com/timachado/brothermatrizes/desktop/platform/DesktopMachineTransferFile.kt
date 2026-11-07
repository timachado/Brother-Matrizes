package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.MachineTransferValidator
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Locale

/**
 * Filesystem handoff for the ORIGINAL Android MachineTransferScreen on Windows.
 *
 * Does not contact or discover a physical embroidery machine, USB port,
 * Bluetooth, Wi-Fi app or cloud account. The user chooses a destination
 * via a native Windows save dialog. Never overwrites existing files.
 */
internal object DesktopMachineTransferFile {
    private const val MAX_EXPORT_BYTES = 64L * 1024L * 1024L

    data class Prepared(
        val format: String,
        val suggestedName: String,
        val bytes: ByteArray
    )

    fun prepare(
        design: EmbroideryDesign,
        selectedFormat: String,
        selectedHoop: HoopProfile
    ): Prepared {
        val validation = MachineTransferValidator.validate(
            design, selectedFormat, selectedHoop
        )
        require(validation.ready) {
            "Envio recusado: " + validation.blockingIssues.joinToString("; ") {
                it.message
            }
        }
        val converted = MatrixConverter.convert(
            design = design,
            targetFormat = validation.format,
            outputSuffix = "maquina"
        ).getOrThrow()
        require(converted.bytes.isNotEmpty() &&
            converted.bytes.size.toLong() <= MAX_EXPORT_BYTES) {
            "O arquivo convertido está vazio ou excede 64 MB."
        }
        val extension = "." + validation.format.lowercase(Locale.ROOT)
        require(converted.format == validation.format &&
            converted.fileName.lowercase(Locale.ROOT).endsWith(extension)) {
            "O conversor não produziu o formato selecionado."
        }
        return Prepared(
            format = validation.format,
            suggestedName = converted.fileName,
            bytes = converted.bytes
        )
    }

    fun saveNew(prepared: Prepared, chosenPath: Path): Path {
        val file = chosenPath.toAbsolutePath().normalize()
        val parent = requireNotNull(file.parent)
        val extension = "." + prepared.format.lowercase(Locale.ROOT)
        require(prepared.format in MatrixConverter.supportedFormats &&
            file.fileName.toString().lowercase(Locale.ROOT).endsWith(extension)) {
            "O destino deve terminar com $extension."
        }
        require(prepared.bytes.isNotEmpty() &&
            prepared.bytes.size.toLong() <= MAX_EXPORT_BYTES) {
            "Arquivo inválido ou acima do limite de 64 MB."
        }
        require(!Files.isSymbolicLink(parent) &&
            Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(file)) {
            "Selecione um destino local existente, sem links simbólicos."
        }
        // CREATE_NEW refuses to replace a client's previous machine design.
        // Do not create parent folders, mount volumes or run external apps.
        val stream = Files.newOutputStream(
            file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
        )
        try {
            stream.use { it.write(prepared.bytes) }
        } catch (failure: Exception) {
            try {
                Files.deleteIfExists(file)
            } catch (cleanup: Exception) {
                failure.addSuppressed(cleanup)
            }
            throw failure
        }
        return file
    }
}
