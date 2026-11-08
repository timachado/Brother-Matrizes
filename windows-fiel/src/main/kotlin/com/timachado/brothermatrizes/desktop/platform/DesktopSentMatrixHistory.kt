package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.library.SentMatrixRecord
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.UUID

/**
 * Windows equivalent of Android LibraryActivityStore.sentMatrices.
 *
 * One record is added ONLY AFTER DesktopMachineTransferFile.saveNew succeeds.
 * This means the selected file was prepared/saved for USB or an app, not that
 * any physical embroidery machine acknowledged its receipt.
 *
 * Local metadata only: never stores stitch data, file bytes, account tokens,
 * customer identifiers or absolute paths; does not touch source projects.
 */
internal class DesktopSentMatrixHistory(
    private val storage: Path = defaultFile()
) {
    private val file: Path = storage.toAbsolutePath().normalize()

    @Synchronized
    fun list(): List<SentMatrixRecord> {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return emptyList()
        require(!Files.isSymbolicLink(file) &&
            Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) &&
            Files.size(file) in 1L..MAX_BYTES) {
            "Histórico de envios inválido ou fora do limite seguro."
        }
        val lines = Files.readAllLines(file, StandardCharsets.UTF_8)
        require(lines.size in 1..(MAX_ITEMS + 1) && lines[0] == HEADER) {
            "Versão do histórico de envios inválida."
        }
        val records = lines.drop(1).map { line ->
            val parts = line.split('\t')
            require(parts.size == 5) { "Registro de envio inválido." }
            val uuid = UUID.fromString(parts[0]).toString()
            val fileName = decode(parts[1])
            val format = decode(parts[2])
            val method = decode(parts[3])
            val at = parts[4].toLong()
            require(valid(fileName, format, method) && at > 0L) {
                "Registro de envio fora dos limites."
            }
            SentMatrixRecord(uuid, fileName, format, method, at)
        }
        require(records.distinctBy { it.id }.size == records.size) {
            "Histórico de envios duplicado."
        }
        return records.sortedByDescending { it.sentAtMillis }
    }

    @Synchronized
    fun recordExport(
        fileName: String,
        format: String,
        method: String,
        recordedAt: Long = System.currentTimeMillis()
    ): List<SentMatrixRecord> {
        require(valid(fileName, format, method) && recordedAt > 0L) {
            "Dados inválidos para registrar arquivo preparado."
        }
        val newRecord = SentMatrixRecord(
            UUID.randomUUID().toString(), fileName, format, method, recordedAt
        )
        return (listOf(newRecord) + list())
            .take(MAX_ITEMS)
            .also(::persist)
    }

    @Synchronized
    fun remove(recordId: String): List<SentMatrixRecord> {
        require(runCatching { UUID.fromString(recordId) }.isSuccess) {
            "Identificador do registro inválido."
        }
        val remaining = list().filterNot { it.id == recordId }
        persist(remaining)
        return remaining
    }

    @Synchronized
    fun clear(): List<SentMatrixRecord> {
        persist(emptyList())
        return emptyList()
    }

    private fun valid(fileName: String, format: String, method: String): Boolean =
        fileName.isNotBlank() && fileName.length <= 220 &&
            '/' !in fileName && '\\' !in fileName &&
            fileName.none { Character.isISOControl(it) } &&
            format in MatrixConverter.supportedFormats &&
            method in setOf("USB / OTG", "Wi-Fi / app")

    private fun decode(value: String): String {
        require(value.length in 1..1200)
        return String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    }

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(
            value.toByteArray(StandardCharsets.UTF_8)
        )

    private fun persist(records: List<SentMatrixRecord>) {
        require(records.size <= MAX_ITEMS)
        val parent = requireNotNull(file.parent)
        require(!Files.isSymbolicLink(parent) &&
            (!Files.exists(parent, LinkOption.NOFOLLOW_LINKS) ||
                Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS))) {
            "Diretório do histórico de envios não confiável."
        }
        Files.createDirectories(parent)
        require(!Files.isSymbolicLink(parent) &&
            !Files.isSymbolicLink(file) &&
            (!Files.exists(file, LinkOption.NOFOLLOW_LINKS) ||
                Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))) {
            "O arquivo de histórico não pode ser um link ou diretório."
        }
        val body = buildString {
            append(HEADER).append('\n')
            for (record in records) {
                require(valid(record.fileName, record.format, record.method))
                append(UUID.fromString(record.id)).append('\t')
                append(encode(record.fileName)).append('\t')
                append(encode(record.format)).append('\t')
                append(encode(record.method)).append('\t')
                append(record.sentAtMillis).append('\n')
            }
        }
        require(body.toByteArray(StandardCharsets.UTF_8).size <= MAX_BYTES)
        val temp = Files.createTempFile(parent, ".bm-sent-", ".tmp")
        try {
            Files.writeString(temp, body, StandardCharsets.UTF_8)
            try {
                Files.move(temp, file,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    companion object {
        private const val HEADER = "BROTHER_MATRIZES_SENT_HISTORY_V1"
        private const val MAX_ITEMS = 50
        private const val MAX_BYTES = 128L * 1024L

        private fun defaultFile(): Path {
            val appData = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
            val root = if (appData != null) Path.of(appData)
                else Path.of(System.getProperty("user.home"), ".local", "share")
            return root.resolve("T.I. Machado")
                .resolve("Brother Matrizes")
                .resolve("sent-history-v1.tsv")
        }
    }
}
