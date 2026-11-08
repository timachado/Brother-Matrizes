package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.project.BackedUpFont
import com.timachado.brothermatrizes.core.project.CompleteLibraryBackupCodec
import com.timachado.brothermatrizes.core.project.ProjectBackupCodec
import com.timachado.brothermatrizes.core.project.ProjectCodec
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Locale

/**
 * Windows filesystem bridge for the UNMODIFIED Android 1.11.9 backup codecs.
 * Produces the exact Android .brother_matrizes-fullbackup archive format:
 * FLP projects + TTF/OTF files, no account, token, quotas or settings.
 */
internal object DesktopAndroidCompleteBackupExporter {
    private const val MAX_PROJECTS = 200
    private const val MAX_FONTS = 200
    private const val MAX_PROJECT_BYTES = 64L * 1024L * 1024L
    private const val MAX_FONT_BYTES = 12L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 192L * 1024L * 1024L
    private val fontName = Regex("[A-Za-z0-9._-]{1,100}\\.(ttf|otf)", RegexOption.IGNORE_CASE)

    data class ExportResult(
        val destination: Path,
        val exportedProjects: Int,
        val exportedFonts: Int
    )

    fun export(
        chosenProjectsFolder: Path,
        chosenFontsFolder: Path?,
        outputFile: Path
    ): ExportResult {
        val output = outputFile.toAbsolutePath().normalize()
        require(output.fileName.toString().lowercase(Locale.ROOT)
            .endsWith(".brother_matrizes-fullbackup")) {
            "Escolha o formato completo original .brother_matrizes-fullbackup."
        }
        require(!Files.exists(output, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(output) &&
            Files.isDirectory(output.parent, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(output.parent)) {
            "O backup não pode substituir um arquivo existente."
        }

        val catalog = DesktopProjectCatalog.scan(chosenProjectsFolder)
        require(catalog.skippedInvalid == 0) {
            "A pasta contém projetos FLP inválidos. Backup completo recusado."
        }
        val projectPaths = catalog.pathById.values.sortedBy {
            it.fileName.toString().lowercase(Locale.ROOT)
        }
        require(projectPaths.size <= MAX_PROJECTS) { "Há projetos demais para exportar." }
        val projectBytes = ArrayList<ByteArray>(projectPaths.size)
        var total = 0L
        for (path in projectPaths) {
            require(path.parent == catalog.folder && !Files.isSymbolicLink(path) &&
                Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                "Projeto fora da pasta original ou substituído por link."
            }
            val size = Files.size(path)
            require(size in 1..MAX_PROJECT_BYTES && size <= MAX_TOTAL_BYTES - total) {
                "Projeto ultrapassou o limite de segurança."
            }
            val bytes = Files.readAllBytes(path)
            require(bytes.size.toLong() == size) { "Projeto alterado durante a leitura." }
            ProjectCodec.decode(bytes) // original Android project validation
            total += size
            projectBytes += bytes
        }

        val fonts = ArrayList<BackedUpFont>()
        if (chosenFontsFolder != null) {
            val directory = chosenFontsFolder.toAbsolutePath().normalize()
            require(!Files.isSymbolicLink(directory) &&
                Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                "Selecione uma pasta real de fontes TTF/OTF."
            }
            val candidates = Files.list(directory).use { entries ->
                entries.filter { file ->
                    Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) &&
                        !Files.isSymbolicLink(file) &&
                        file.fileName.toString().lowercase(Locale.ROOT)
                            .let { it.endsWith(".ttf") || it.endsWith(".otf") }
                }.limit(MAX_FONTS.toLong() + 1).toList()
            }
            require(candidates.size <= MAX_FONTS) { "A pasta contém mais de 200 fontes." }
            val names = HashSet<String>()
            for (file in candidates.sortedBy { it.fileName.toString().lowercase(Locale.ROOT) }) {
                val name = file.fileName.toString()
                require(fontName.matches(name) && !name.contains("..") &&
                    names.add(name.lowercase(Locale.ROOT))) {
                    "Nome de fonte incompatível ou duplicado."
                }
                val size = Files.size(file)
                require(size in 4..MAX_FONT_BYTES && size <= MAX_TOTAL_BYTES - total) {
                    "Fonte ultrapassou o limite de segurança."
                }
                val bytes = Files.readAllBytes(file)
                require(bytes.size.toLong() == size) { "Fonte alterada durante a leitura." }
                val font = BackedUpFont(name, bytes)
                CompleteLibraryBackupCodec.validateFont(font)
                fonts += font
                total += size
            }
        }
        require(projectBytes.isNotEmpty() || fonts.isNotEmpty()) {
            "Não há matrizes ou fontes para exportar."
        }
        val projectsBackup = ProjectBackupCodec.encode(projectBytes)
        val archive = CompleteLibraryBackupCodec.encode(projectsBackup, fonts)
        var created = false
        try {
            Files.newOutputStream(
                output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
            ).use { stream ->
                created = true
                stream.write(archive)
            }
            // Exact Android codec must decode this backup before announcing success.
            val recovered = CompleteLibraryBackupCodec.decode(Files.readAllBytes(output))
            check(ProjectBackupCodec.decode(recovered.projectsBackup).size ==
                projectBytes.size && recovered.fonts.size == fonts.size) {
                "Falha na verificação do backup original Brother Matrizes."
            }
            return ExportResult(output, projectBytes.size, fonts.size)
        } catch (error: Exception) {
            if (created) Files.deleteIfExists(output)
            throw error
        }
    }
}
