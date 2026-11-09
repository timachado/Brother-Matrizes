package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/**
 * Windows export of an Android-generated embroidery design.
 *
 * Never modifies PES/DST/JEF bytes or machine compatibility profiles.
 * The write is CREATE_NEW; a subsequent local read verifies that security
 * software or storage did not block/quarantine the freshly written file.
 * This does not certify the file as malware-free.
 */
internal object DesktopVerifiedMatrixExport {
    fun saveNew(format: String, convertedBytes: ByteArray, selected: Path): Path {
        val normalized = format.uppercase(Locale.ROOT)
        require(normalized in MatrixConverter.supportedFormats) {
            "A exportação suporta somente PES, DST ou JEF."
        }
        require(convertedBytes.isNotEmpty() &&
            convertedBytes.size <= 64 * 1024 * 1024) {
            "Arquivo de bordado vazio ou maior que o limite seguro."
        }
        if (normalized == "PES") {
            val header = if (convertedBytes.size >= 8) {
                convertedBytes.copyOfRange(0, 8).toString(Charsets.US_ASCII)
            } else ""
            require(header == "#PES0001" || header == "#PES0060") {
                "O conversor não produziu um PES Brother/PE-DESIGN reconhecível."
            }
        }

        val file = DesktopMachineTransferFile.saveNew(
            DesktopMachineTransferFile.Prepared(
                normalized,
                selected.fileName.toString(),
                convertedBytes
            ),
            selected
        )
        try {
            check(Files.readAllBytes(file).contentEquals(convertedBytes)) {
                "Os bytes da matriz não correspondem à conversão original."
            }
            val parsed = DesktopEmbroideryFiles.open(file)
            check(parsed.stitchCount > 0 && parsed.endFound) {
                "O arquivo de bordado gravado não reabriu corretamente."
            }
        } catch (failure: Exception) {
            // This newly created file is not a pre-existing customer design.
            // Never disable Defender, change permissions or whitelist a path.
            try {
                Files.deleteIfExists(file)
            } catch (cleanup: Exception) {
                failure.addSuppressed(cleanup)
            }
            throw IOException(
                "O arquivo não passou na conferência após salvar. " +
                    "O Windows pode ter bloqueado a leitura. Consulte " +
                    "Segurança do Windows > Histórico de proteção e " +
                    "não crie exclusões de antivírus.",
                failure
            )
        }
        return file
    }
}
