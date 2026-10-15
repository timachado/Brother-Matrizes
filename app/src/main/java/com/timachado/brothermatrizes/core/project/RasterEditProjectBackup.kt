package com.timachado.brothermatrizes.core.project

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup EXCLUSIVO dos arquivos .bmrail; não altera ProjectBackupCodec (.flp).
 * Toda entrada é validada ANTES de gravar qualquer projeto.
 */
object RasterEditProjectBackupCodec {
    private const val MANIFEST = "brother-matrizes-satin-backup.txt"
    private const val MAGIC = "BROTHER_MATRIZES_SATIN_BACKUP_V1"
    private const val MAX_PROJECTS = 100
    const val MAX_ARCHIVE_BYTES = 64 * 1024 * 1024
    private const val MAX_ENTRY_BYTES = RasterEditProjectCodec.MAX_BYTES
    private const val MAX_TOTAL_BYTES = 64 * 1024 * 1024

    fun encode(projects: List<RasterEditProject>): ByteArray {
        require(projects.size in 1..MAX_PROJECTS) {
            "É necessário salvar de 1 a 100 projetos para exportar."
        }
        val payloads = projects.map { RasterEditProjectCodec.encode(it) }
        require(payloads.sumOf { it.size.toLong() } <= MAX_TOTAL_BYTES.toLong()) {
            "O backup ultrapassaria o limite de segurança de 64 MB."
        }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(
                (MAGIC + "\n" + payloads.size + "\n")
                    .toByteArray(StandardCharsets.UTF_8)
            )
            zip.closeEntry()
            payloads.forEachIndexed { index, bytes ->
                zip.putNextEntry(
                    ZipEntry("projects/" + index.toString().padStart(4, '0') + ".bmrail")
                )
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        val result = out.toByteArray()
        require(result.size <= MAX_ARCHIVE_BYTES) {
            "Arquivo ZIP de backup excedeu 64 MB."
        }
        return result
    }

    fun decode(bytes: ByteArray): List<RasterEditProject> {
        require(bytes.isNotEmpty() && bytes.size <= MAX_ARCHIVE_BYTES) {
            "O backup ZIP está vazio ou é muito grande."
        }
        var count: Int? = null
        val projects = linkedMapOf<String, RasterEditProject>()
        var totalRead = 0L
        val seenEntries = HashSet<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                require(!entry.isDirectory &&
                    !name.contains("..") && !name.startsWith("/") &&
                    !name.startsWith("\\") && !name.contains('\\')) {
                    "Backup contém pasta ou caminho inseguro."
                }
                require(seenEntries.add(name)) {
                    "Backup contém arquivo duplicado."
                }
                val limit = if (name == MANIFEST) 4096 else MAX_ENTRY_BYTES
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                var itemSize = 0
                while (true) {
                    val n = zip.read(chunk)
                    if (n < 0) break
                    if (n == 0) continue
                    itemSize += n
                    totalRead += n.toLong()
                    require(itemSize <= limit && totalRead <= MAX_TOTAL_BYTES.toLong()) {
                        "Backup descompactado excede limite seguro."
                    }
                    buffer.write(chunk, 0, n)
                }
                val contents = buffer.toByteArray()
                when {
                    name == MANIFEST -> {
                        val parts = contents.toString(StandardCharsets.UTF_8)
                            .lines().filter { it.isNotBlank() }
                        require(parts.size == 2 && parts[0] == MAGIC) {
                            "Não é backup de projetos Satin do Brother Matrizes."
                        }
                        count = parts[1].toIntOrNull()
                        require(count in 1..MAX_PROJECTS) {
                            "Quantidade de projetos no backup inválida."
                        }
                    }
                    name.matches(Regex("projects/[0-9]{4}\\.bmrail")) -> {
                        require(projects.size < MAX_PROJECTS) {
                            "Backup contém projetos demais."
                        }
                        projects[name] = RasterEditProjectCodec.decode(contents)
                    }
                    else -> throw IllegalArgumentException(
                        "Arquivo desconhecido dentro do backup."
                    )
                }
                zip.closeEntry()
            }
        }
        require(count != null && count == projects.size) {
            "Backup incompleto: a quantidade de projetos não confere."
        }
        require((0 until projects.size).all {
            projects.containsKey("projects/" + it.toString().padStart(4, '0') + ".bmrail")
        }) {
            "Backup contém entradas ausentes ou fora de sequência."
        }
        return projects.values.toList()
    }
}

/** Grava apenas novos IDs, sem tocar na biblioteca comercial nem sobrescrever. */
object RasterEditProjectBackupStore {
    fun exportAll(folder: File): Result<ByteArray> = runCatching {
        val summaries = RasterEditFileStore.list(folder).getOrThrow()
        require(summaries.isNotEmpty()) {
            "Ainda não existe projeto Satin salvo para backup."
        }
        val projects = summaries.map {
            RasterEditFileStore.load(folder, it.id).getOrThrow()
        }
        RasterEditProjectBackupCodec.encode(projects)
    }

    fun restoreAll(folder: File, bytes: ByteArray): Result<Int> = runCatching {
        // Validar integralmente antes de qualquer gravação.
        val projects = RasterEditProjectBackupCodec.decode(bytes)
        val existing = RasterEditFileStore.list(folder).getOrThrow().size
        require(existing + projects.size <= 100) {
            "Não há espaço para restaurar todos os projetos (limite: 100)."
        }
        val created = ArrayList<String>()
        try {
            for (project in projects) {
                created += RasterEditFileStore.save(folder, project).getOrThrow()
            }
        } catch (error: Exception) {
            // Nunca apagar o que já existia; desfazer só arquivos recém-importados.
            for (id in created) File(folder, "$id.bmrail").delete()
            throw error
        }
        created.size
    }

    fun exportAll(context: Context): Result<ByteArray> =
        exportAll(File(context.filesDir, "brother_matrizes_editable_satin_v1"))

    fun restoreAll(context: Context, bytes: ByteArray): Result<Int> =
        restoreAll(File(context.filesDir, "brother_matrizes_editable_satin_v1"), bytes)
}
