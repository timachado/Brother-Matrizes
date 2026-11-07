package com.timachado.brothermatrizes.desktop.platform

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Fail-closed, local-only audit journal for per-item archive quota operations.
 *
 * A durable marker is written BEFORE the first remote reserve request.
 * The response may be lost after the remote service already reserved quota:
 * NEVER reissue reserve/finalize, infer account entitlement, or delete an
 * imported file automatically when a marker survives a crash.
 *
 * The journal contains an opaque UUID request key (not a bearer credential),
 * a SHA-256 entry identifier, stage, category and timestamp. NO token, source
 * path, matrix content, customer name, private key, or access profile.
 *
 * This class needs a caller-provisioned per-user PRIVATE EXISTING directory.
 * Do not instantiate from the Windows UI or with production credentials.
 * Cross-process FileLock ensures only one active journalled import at a time.
 * No method auto-resolves an outstanding server transaction.
 */
internal class DesktopArchiveQuotaJournal(private val location: Path) {
    enum class Stage { RESERVE_UNKNOWN, RESERVED, FINALIZE_UNKNOWN, CLEANUP_UNKNOWN }

    /**
     * Persisted BEFORE the first HTTP attempt. Legacy V1 has no reliable
     * protocol evidence, especially for ephemeral Pro receipt UUIDs.
     */
    enum class ReservationProtocol { LEGACY_UNKNOWN, FREE_V2, PRO_V2 }

    /** A file's safe relative identity, never an absolute customer path. */
    data class OutputEvidence(
        val fileNameSha256: String,
        val sha256: String,
        val verifiedNew: Boolean = false
    )

    data class Pending(
        val localId: String,
        val category: DesktopArchiveInventory.Category,
        val entrySha256: String,
        val reservationKey: String?,
        val stage: Stage,
        val createdAtMillis: Long,
        val protocol: ReservationProtocol = ReservationProtocol.LEGACY_UNKNOWN,
        val output: OutputEvidence? = null
    )

    internal class Lease(private val channel: FileChannel, private val lock: FileLock) :
        AutoCloseable {
        override fun close() {
            try { lock.release() } finally { channel.close() }
        }
    }

    private val markerName = Regex("pending-[0-9a-f-]{36}\\.json")
    private val keyFormat = Regex(
        "^(usage|pro)_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    )

    fun acquireLease(): Lease {
        val root = folder()
        // Lock file is permanent; never truncate or delete it across processes.
        val file = root.resolve(".archive-usage.lock")
        check(!Files.isSymbolicLink(file)) { "Arquivo de bloqueio inválido." }
        val channel = FileChannel.open(
            file, StandardOpenOption.CREATE, StandardOpenOption.WRITE
        )
        try {
            val lock = try {
                channel.tryLock()
            } catch (_: java.nio.channels.OverlappingFileLockException) {
                null
            }
            check(lock != null) {
                "Outra instância está importando. Finalize-a antes de continuar."
            }
            return Lease(channel, lock)
        } catch (error: Throwable) {
            channel.close()
            throw error
        }
    }

    /**
     * Intended to be called only while holding acquireLease().
     * An old or partially written marker blocks NEW server reservations.
     */
    fun begin(
        item: DesktopArchiveInventory.Item,
        protocol: ReservationProtocol = ReservationProtocol.LEGACY_UNKNOWN
    ): Pending {
        check(listPending().isEmpty()) {
            "Há uma importação anterior sem reconciliação. Nenhuma nova cota foi solicitada."
        }
        val name = item.entryName
        require(name.length in 1..320 && item.index >= 0 &&
            item.extension in setOf("PES", "DST", "JEF", "TTF", "OTF")
        ) { "Item inválido para registrar transação." }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(("${item.index}:$name:${item.sizeBytes}:${item.extension}")
                .toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val entry = Pending(
            localId = UUID.randomUUID().toString(),
            category = item.category,
            entrySha256 = digest,
            reservationKey = null,
            stage = Stage.RESERVE_UNKNOWN,
            createdAtMillis = System.currentTimeMillis(),
            protocol = protocol
        )
        write(entry, existing = false)
        return entry
    }

    fun reserved(previous: Pending, key: String): Pending {
        require(keyFormat.matches(key)) { "Reserva retornada inválida." }
        check(previous.stage == Stage.RESERVE_UNKNOWN) { "Transição de quota inválida." }
        check(when (previous.protocol) {
            ReservationProtocol.LEGACY_UNKNOWN -> true
            ReservationProtocol.FREE_V2 -> key == "usage_${previous.localId}"
            ReservationProtocol.PRO_V2 -> key == "pro_${previous.localId}"
        }) { "Resposta da reserva não corresponde ao protocolo persistido." }
        return advance(previous, previous.copy(
            reservationKey = key,
            stage = Stage.RESERVED
        ))
    }

    /**
     * Before any new library file is published, record its deterministic
     * basename and expected content hash. A crash leaves evidence, not
     * permission to delete, finalize or retry any server operation.
     */
    fun declareOutput(previous: Pending, fileName: String, sha256: String): Pending {
        check(previous.stage == Stage.RESERVED && previous.output == null) {
            "Saída não pode ser declarada neste estado."
        }
        require(sha256.matches(Regex("[0-9a-f]{64}"))) {
            "Hash de saída inválido."
        }
        val allowed = when (previous.category) {
            DesktopArchiveInventory.Category.MATRIX ->
                Regex("archive-(pes|dst|jef)-[0-9a-f]{64}\\.flp")
            DesktopArchiveInventory.Category.FONT ->
                Regex("[A-Za-z0-9._-]+-[0-9a-f]{10}\\.(ttf|otf)")
        }
        require(fileName.length <= 160 && allowed.matches(fileName) &&
            fileName != "." && fileName != "..") {
            "Identidade do destino não está autorizada."
        }
        return advance(previous, previous.copy(
            output = OutputEvidence(fileNameHash(fileName), sha256)
        ))
    }

    /** Called only after a new output's bytes and codec were verified. */
    fun verifyOutput(previous: Pending, fileName: String, sha256: String): Pending {
        val evidence = checkNotNull(previous.output) {
            "Saída sem declaração durável."
        }
        check(previous.stage == Stage.RESERVED &&
            !evidence.verifiedNew &&
            evidence.fileNameSha256 == fileNameHash(fileName) &&
            evidence.sha256 == sha256) {
            "Verificação não corresponde à saída declarada."
        }
        return advance(previous, previous.copy(
            output = evidence.copy(verifiedNew = true)
        ))
    }

    fun verifiedOutputMatches(previous: Pending, fileName: String): Boolean =
        previous.output?.verifiedNew == true &&
            previous.output.fileNameSha256 == fileNameHash(fileName)

    private fun fileNameHash(fileName: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(fileName.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun finalizing(previous: Pending): Pending {
        check(previous.stage == Stage.RESERVED) { "Quota não estava reservada." }
        return advance(previous, previous.copy(stage = Stage.FINALIZE_UNKNOWN))
    }

    fun needsCleanup(previous: Pending): Pending {
        check(previous.stage == Stage.FINALIZE_UNKNOWN) { "Quota não finalizada." }
        return advance(previous, previous.copy(stage = Stage.CLEANUP_UNKNOWN))
    }

    /**
     * Only call after server has ACKed finalize and all local rollback (if
     * needed) has completed. Crash between server ACK and deletion leaves a
     * false-positive pending marker: this is intentional and safe.
     */
    fun completed(previous: Pending) {
        check(previous.stage == Stage.FINALIZE_UNKNOWN ||
            previous.stage == Stage.CLEANUP_UNKNOWN
        ) { "Registro não confirmado." }
        check(read(previous.localId) == previous) { "Registro alterado externamente." }
        Files.delete(checkNotNull(path(previous.localId)))
    }

    fun listPending(): List<Pending> {
        val root = folder()
        val records = Files.list(root).use { stream -> stream.toList() }
        check(records.size <= 501) { "Pasta de quotas excede limite seguro." }
        check(records.none {
            val name = it.fileName.toString()
            name.startsWith("pending-") && !markerName.matches(name)
        }) {
            "Journal incompleto ou inválido. Reconciliação manual obrigatória."
        }
        return records.filter { markerName.matches(it.fileName.toString()) }
            .sortedBy { it.fileName.toString() }
            .map { file ->
                check(!Files.isSymbolicLink(file) &&
                    Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) &&
                    Files.size(file) in 1..4096
                ) { "Registro de cota inválido. Bloqueie novas reservas." }
                val parsed = parse(Files.readString(file))
                check(file.fileName.toString() == "pending-${parsed.localId}.json") {
                    "Identificador local da cota não coincide."
                }
                parsed
            }
    }

    private fun advance(old: Pending, updated: Pending): Pending {
        check(read(old.localId) == old) { "Registro de cota foi alterado externamente." }
        write(updated, existing = true)
        return updated
    }

    private fun read(localId: String): Pending =
        parse(Files.readString(path(localId)))

    private fun path(localId: String): Path {
        require(localId.matches(Regex("[0-9a-f-]{36}")) &&
            runCatching { UUID.fromString(localId).toString() == localId }
                .getOrDefault(false)) {
            "Identificador de transação inválido."
        }
        return folder().resolve("pending-$localId.json")
    }

    private fun write(entry: Pending, existing: Boolean) {
        val root = folder()
        val target = path(entry.localId)
        if (existing) {
            check(Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) &&
                !Files.isSymbolicLink(target)) { "Registro inexistente ou inválido." }
        } else {
            check(!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                "Identificador de journal já utilizado."
            }
        }
        val text = buildJsonObject {
            put("version", 3)
            put("localId", entry.localId)
            put("category", entry.category.name)
            put("entrySha256", entry.entrySha256)
            put("reservationKey", entry.reservationKey ?: "")
            put("stage", entry.stage.name)
            put("createdAtMillis", entry.createdAtMillis)
            put("protocol", entry.protocol.name)
            val output = entry.output
            if (output != null) {
                put("outputNameHash", output.fileNameSha256)
                put("outputSha256", output.sha256)
                put("outputVerifiedNew", output.verifiedNew)
            }
        }.toString().toByteArray(Charsets.UTF_8)
        check(text.size in 1..4096) { "Registro de quota acima do limite." }

        val tmp = Files.createTempFile(root, "pending-", ".tmp")
        var moved = false
        try {
            FileChannel.open(tmp, StandardOpenOption.WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(text)
                while (buffer.hasRemaining()) {
                    check(channel.write(buffer) > 0) { "Journal não foi gravado." }
                }
                channel.force(true)
            }
            val options = if (existing) arrayOf(
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            ) else arrayOf(StandardCopyOption.ATOMIC_MOVE)
            Files.move(tmp, target, *options)
            moved = true
        } finally {
            // Fail closed when an interrupted write/rename exists, including
            // after a crash. A failed live write may remove its own .tmp.
            if (!moved) Files.deleteIfExists(tmp)
        }
    }

    private fun parse(raw: String): Pending {
        val parsed = Json.parseToJsonElement(raw) as? JsonObject
            ?: error("Formato de journal inválido.")
        fun str(key: String): String =
            parsed[key]?.jsonPrimitive?.content
                ?: error("Journal sem campo obrigatório: $key")
        val version = str("version")
        check(version == "1" || version == "2" || version == "3") {
            "Versão do journal não suportada."
        }
        // The legacy schema never recorded protocol identity: do not
        // reinterpret a V1 Pro UUID as a durable Pro V2 receipt.
        val protocol = if (version == "1") ReservationProtocol.LEGACY_UNKNOWN
            else ReservationProtocol.valueOf(str("protocol"))
        val id = str("localId")
        require(id.matches(Regex("[0-9a-f-]{36}")) &&
            runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) {
            "Journal contém identificador inválido."
        }
        val digest = str("entrySha256")
        require(digest.matches(Regex("[0-9a-f]{64}"))) {
            "Journal contém hash inválido."
        }
        val key = str("reservationKey").ifBlank { null }
        require(key == null || keyFormat.matches(key)) { "Journal contém reserva inválida." }
        if (protocol != ReservationProtocol.LEGACY_UNKNOWN) {
            require(id.matches(Regex(
                "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
            ))) { "Protocolo V2 exige UUIDv4 local." }
            val prefix = if (protocol == ReservationProtocol.FREE_V2) "usage_" else "pro_"
            require(key == null || key == prefix + id) {
                "Recibo não coincide com protocolo e UUID persistidos."
            }
        }
        val output = if (version == "3") {
            val nameHash = parsed["outputNameHash"]?.jsonPrimitive?.content
            if (nameHash == null) {
                check(parsed["outputSha256"] == null &&
                    parsed["outputVerifiedNew"] == null && parsed["outputFile"] == null) {
                    "Evidência de saída incompleta."
                }
                null
            } else {
                val digestOut = str("outputSha256")
                val verified = parsed["outputVerifiedNew"]?.jsonPrimitive?.content
                require(digestOut.matches(Regex("[0-9a-f]{64}")) &&
                    verified in setOf("true", "false")) {
                    "Evidência de saída inválida."
                }
                require(nameHash.matches(Regex("[0-9a-f]{64}"))) {
                    "Identificador opaco da saída inválido."
                }
                OutputEvidence(nameHash, digestOut, verified == "true")
            }
        } else null
        val createdAt = str("createdAtMillis").toLong()
        require(createdAt > 0) { "Journal contém data inválida." }
        return Pending(
            localId = id,
            category = DesktopArchiveInventory.Category.valueOf(str("category")),
            entrySha256 = digest,
            reservationKey = key,
            stage = Stage.valueOf(str("stage")),
            createdAtMillis = createdAt,
            protocol = protocol,
            output = output
        )
    }

    private fun folder(): Path {
        val dir = location.toAbsolutePath().normalize()
        require(!Files.isSymbolicLink(dir) &&
            !Files.isSymbolicLink(dir.parent) &&
            Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            "Journal precisa de pasta privada existente, sem link simbólico."
        }
        return dir
    }
}
