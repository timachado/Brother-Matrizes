package com.timachado.brothermatrizes.core.project

import android.content.Context
import com.timachado.brothermatrizes.core.storage.AtomicFileWriter
import com.timachado.brothermatrizes.font.ImportedFontStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackedUpFont(val fileName: String, val bytes: ByteArray)
data class CompleteLibraryArchive(val projectsBackup: ByteArray, val fonts: List<BackedUpFont>)
data class LibraryRestoreCounts(val projects: Int, val fonts: Int)

/** Portable projects+TTF/OTF backup, without OAuth tokens or license data. */
object CompleteLibraryBackupCodec {
    private const val MANIFEST = "brother_matrizes-complete-backup.txt"
    private const val PROJECTS = "projects.brother_matrizes-backup"
    private const val MAGIC = "BROTHER_MATRIZES_COMPLETE_BACKUP"
    private const val MAX_TOTAL = 192 * 1024 * 1024
    private const val MAX_PROJECT = 128 * 1024 * 1024
    private const val MAX_FONT = 12 * 1024 * 1024
    private const val MAX_FONTS = 200
    private val fontName = Regex("[A-Za-z0-9._-]{1,100}\\.(ttf|otf)", RegexOption.IGNORE_CASE)

    fun isCompleteArchive(bytes: ByteArray): Boolean =
        ZipInputStream(ByteArrayInputStream(bytes)).use { it.nextEntry?.name == MANIFEST }

    fun validateFont(file: BackedUpFont) {
        require(fontName.matches(file.fileName) && !file.fileName.contains("..")) {
            "O backup contém um nome de fonte inválido."
        }
        require(file.bytes.size in 4..MAX_FONT) {
            "O backup contém uma fonte vazia ou maior que 12 MB."
        }
        val header = file.bytes.copyOfRange(0, 4)
        val format = when {
            header.contentEquals(byteArrayOf(0, 1, 0, 0)) -> "ttf"
            header.contentEquals("true".toByteArray(Charsets.US_ASCII)) -> "ttf"
            header.contentEquals("OTTO".toByteArray(Charsets.US_ASCII)) -> "otf"
            else -> null
        }
        require(format != null &&
            file.fileName.substringAfterLast('.').lowercase() == format) {
            "O backup contém uma fonte TTF/OTF inválida."
        }
    }

    fun encode(projectsBackup: ByteArray, fonts: List<BackedUpFont>): ByteArray {
        require(fonts.size <= MAX_FONTS) { "Há fontes demais para o backup." }
        require(projectsBackup.size <= MAX_PROJECT) { "A biblioteca de matrizes é grande demais." }
        ProjectBackupCodec.decode(projectsBackup)
        require(fonts.map { it.fileName.lowercase() }.distinct().size == fonts.size) {
            "O backup possui nomes duplicados de fontes."
        }
        fonts.forEach(::validateFont)
        val size = projectsBackup.size.toLong() + fonts.sumOf { it.bytes.size.toLong() }
        require(size <= MAX_TOTAL) { "O backup excede 192 MB." }
        return ByteArrayOutputStream().also { buffer ->
            ZipOutputStream(buffer).use { zip ->
                fun entry(name: String, bytes: ByteArray) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
                entry(MANIFEST,
                    (MAGIC + "\n1\n" + fonts.size + "\n").toByteArray(Charsets.UTF_8))
                entry(PROJECTS, projectsBackup)
                fonts.forEach { entry("fonts/" + it.fileName, it.bytes) }
            }
        }.toByteArray()
    }

    fun decode(bytes: ByteArray): CompleteLibraryArchive {
        require(bytes.size in 1..MAX_TOTAL) { "Arquivo de backup vazio ou grande demais." }
        var manifest: String? = null
        var projectData: ByteArray? = null
        val fonts = mutableListOf<BackedUpFont>()
        val seen = mutableSetOf<String>()
        var unpacked = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && seen.add(entry.name.lowercase())) {
                    "Backup com arquivo duplicado ou pasta inesperada."
                }
                val limit = when {
                    entry.name == MANIFEST -> 256
                    entry.name == PROJECTS -> MAX_PROJECT
                    entry.name.startsWith("fonts/") -> MAX_FONT
                    else -> error("Backup contém arquivo inesperado.")
                }
                val out = ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                while (true) {
                    val n = zip.read(chunk)
                    if (n < 0) break
                    unpacked += n
                    require(out.size().toLong() + n <= limit && unpacked <= MAX_TOTAL) {
                        "Arquivo do backup maior que o limite permitido."
                    }
                    out.write(chunk, 0, n)
                }
                val data = out.toByteArray()
                when (entry.name) {
                    MANIFEST -> manifest = data.toString(Charsets.UTF_8)
                    PROJECTS -> projectData = data
                    else -> {
                        require(fonts.size < MAX_FONTS) { "Backup com fontes demais." }
                        val font = BackedUpFont(entry.name.removePrefix("fonts/"), data)
                        validateFont(font)
                        fonts += font
                    }
                }
                zip.closeEntry()
            }
        }
        val metadata = manifest?.lineSequence()?.toList()
            ?: error("Manifesto do backup ausente.")
        require(metadata.size >= 3 && metadata[0] == MAGIC &&
            metadata[1] == "1" && metadata[2].toIntOrNull() == fonts.size) {
            "Manifesto de backup completo inválido."
        }
        val projects = projectData ?: error("Matrizes ausentes no backup.")
        ProjectBackupCodec.decode(projects)
        return CompleteLibraryArchive(projects, fonts)
    }
}

object CompleteLibraryBackupStore {
    fun exportBackup(context: Context): Result<ByteArray> = runCatching {
        val projects = ProjectStore.list(context).getOrThrow()
        val fonts = ImportedFontStore.list(context)
        require(projects.isNotEmpty() || fonts.isNotEmpty()) {
            "Não há projetos ou fontes salvos para backup."
        }
        val projectBytes = if (projects.isEmpty()) ProjectBackupCodec.encode(emptyList())
        else ProjectBackupStore.exportBackup(context).getOrThrow()
        val fontBytes = fonts.map { font ->
            BackedUpFont(font.fileName, File(font.absolutePath).readBytes())
        }
        CompleteLibraryBackupCodec.encode(projectBytes, fontBytes)
    }

    fun restoreBackup(context: Context, bytes: ByteArray): Result<LibraryRestoreCounts> =
        runCatching {
            if (!CompleteLibraryBackupCodec.isCompleteArchive(bytes)) {
                val projects = ProjectBackupStore.restoreBackup(context, bytes).getOrThrow()
                return@runCatching LibraryRestoreCounts(projects, 0)
            }
            val archive = CompleteLibraryBackupCodec.decode(bytes)
            val directory = File(context.filesDir, "imported_fonts")
            require((directory.isDirectory || directory.mkdirs()) && directory.isDirectory) {
                "Não foi possível preparar a pasta de fontes."
            }
            val safeDirectory = directory.canonicalFile
            var restoredFonts = 0
            archive.fonts.forEach { font ->
                CompleteLibraryBackupCodec.validateFont(font)
                val target = File(safeDirectory, font.fileName).canonicalFile
                require(target.parentFile == safeDirectory) { "Nome de fonte inseguro." }
                if (target.exists()) {
                    require(target.length() == font.bytes.size.toLong() &&
                        target.readBytes().contentEquals(font.bytes)) {
                        "Já existe uma fonte diferente com o mesmo nome. Nada foi sobrescrito."
                    }
                } else {
                    AtomicFileWriter.write(target = target) { output -> output.write(font.bytes) }
                    restoredFonts++
                }
            }
            val projects = ProjectBackupStore.restoreBackup(context, archive.projectsBackup)
                .getOrThrow()
            LibraryRestoreCounts(projects, restoredFonts)
        }
}
