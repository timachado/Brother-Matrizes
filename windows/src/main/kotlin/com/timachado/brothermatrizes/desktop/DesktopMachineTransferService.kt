package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.MachineTransferValidation
import com.timachado.brothermatrizes.core.embroidery.MachineTransferValidator
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Locale

/**
 * Exportação seletiva para destino autorizado (pendrive/pasta).
 * Não é envio direto USB/Wi-Fi nem validação física da bordadeira.
 */
internal data class DesktopTransferReceipt(
    val destination: Path,
    val format: String,
    val hoop: HoopProfile,
    val bytes: Int,
    val sha256: String
)

internal object DesktopMachineTransferService {
    const val MAX_TRANSFER_BYTES = 32L * 1024 * 1024

    fun validate(
        document: DesktopMatrixDocument,
        format: String,
        hoop: HoopProfile
    ): MachineTransferValidation =
        MachineTransferValidator.validate(document.current, format, hoop)

    fun saveNew(
        document: DesktopMatrixDocument,
        targetFormat: String,
        hoop: HoopProfile,
        destination: Path
    ): DesktopTransferReceipt {
        val validation = validate(document, targetFormat, hoop)
        require(validation.ready) {
            validation.blockingIssues.joinToString("; ") { it.message }
                .ifEmpty { "Matriz não autorizada para o formato e bastidor escolhidos." }
        }
        val format = validation.format
        require(destination.isAbsolute &&
            destination.fileName.toString().lowercase(Locale.ROOT)
                .endsWith(".${format.lowercase(Locale.ROOT)}")) {
            "Selecione destino absoluto com extensão .${format.lowercase(Locale.ROOT)}."
        }
        val parent = destination.parent
        require(parent != null &&
            Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(parent) && Files.isWritable(parent)) {
            "Pasta de destino inválida, redirecionada ou sem permissão."
        }
        require(!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            "Arquivo já existe: escolha novo nome para não sobrescrever a matriz."
        }
        // O conversor oficial já relê a matriz gerada antes da gravação.
        // PES é produzido pelo perfil Brother Machine, não pelo PC experimental.
        val exported = DesktopMatrixService.convert(
            document, format, pesPcProfile = false
        )
        require(exported.bytes.isNotEmpty() &&
            exported.bytes.size.toLong() <= MAX_TRANSFER_BYTES) {
            "Arquivo convertido ultrapassou 32 MiB."
        }
        val expectedHash = sha256(exported.bytes)
        var created = false
        try {
            Files.newOutputStream(
                destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
            ).use { stream ->
                created = true
                stream.write(exported.bytes)
                stream.flush()
            }
            val actual = Files.newInputStream(
                destination, LinkOption.NOFOLLOW_LINKS
            ).use { it.readNBytes(MAX_TRANSFER_BYTES.toInt() + 1) }
            check(actual.size == exported.bytes.size &&
                sha256(actual) == expectedHash) {
                "A cópia no destino falhou na verificação SHA-256."
            }
        } catch (failure: Exception) {
            // Apagar somente o arquivo criado por esta tentativa.
            if (created) runCatching { Files.deleteIfExists(destination) }
            throw failure
        }
        return DesktopTransferReceipt(
            destination, format, hoop, exported.bytes.size, expectedHash
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
