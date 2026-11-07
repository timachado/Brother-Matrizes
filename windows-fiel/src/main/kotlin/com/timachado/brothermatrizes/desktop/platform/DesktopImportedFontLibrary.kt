package com.timachado.brothermatrizes.desktop.platform

import org.jetbrains.skia.FontMgr
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Locale

/**
 * Biblioteca local TTF/OTF para o port Windows fiel do Brother Matrizes.
 *
 * Mesmos limites, extensões, detecção de magic e id SHA-256 da
 * ImportedFontStore Android. Os bytes ficam num diretório QA isolado.
 * Nenhuma fonte é publicada ao CreateNameScreen enquanto o motor de
 * pontadas ImportedFontMatrixGenerator Android não estiver portado.
 */
internal data class DesktopFontRecord(
    val id: String,
    val displayName: String,
    val fileName: String,
    val extension: String,
    val absolutePath: String
)

internal class DesktopImportedFontLibrary(private val location: Path) {
    private val maxSize = 12L * 1024L * 1024L
    private val validExtensions = setOf("ttf", "otf")
    private val tenHex = Regex("-[0-9a-f]{10}$")

    fun importFile(sourceFile: Path, displayName: String? = null): DesktopFontRecord {
        val source = sourceFile.toAbsolutePath().normalize()
        require(!Files.isSymbolicLink(source) &&
            Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            "A fonte precisa ser um arquivo regular local, sem link simbólico."
        }
        val name = displayName?.takeIf { it.isNotBlank() }
            ?: source.fileName.toString()
        val extension = name.substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
        require(extension in validExtensions) {
            "Importe somente fontes TTF ou OTF."
        }
        val size = Files.size(source)
        require(size in 4..maxSize) {
            "A fonte deve conter dados válidos e ter até 12 MB."
        }
        val bytes = Files.readAllBytes(source)
        require(bytes.size.toLong() == size) {
            "Arquivo de fonte mudou durante a leitura."
        }
        require(detectFormat(bytes) == extension) {
            "Assinatura do arquivo não corresponde à extensão TTF/OTF."
        }

        val root = directory()
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(bytes).take(5)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        val rawBase = name.substringBeforeLast(".")
        val base = sanitizeBase(rawBase)
        // Android deduplica fontes pelo mesmo conteúdo, mesmo quando
        // importadas de outro nome ou extensão declarada.
        Files.list(root).use { files ->
            val existing = files.filter { entry ->
                val fileName = entry.fileName.toString()
                !Files.isSymbolicLink(entry) &&
                    Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS) &&
                    isSafeId(fileName) &&
                    fileName.substringBeforeLast(".")
                        .endsWith("-$hash", ignoreCase = true)
            }.findFirst().orElse(null)
            if (existing != null) {
                require(Files.readAllBytes(existing).contentEquals(bytes)) {
                    "Identificador de fonte duplicado com conteúdo diferente."
                }
                return record(existing)
            }
        }
        val target = root.resolve("$base-$hash.$extension")
        require(!Files.isSymbolicLink(target)) {
            "Uma entrada simbólica não pode substituir uma fonte."
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            require(Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) &&
                Files.readAllBytes(target).contentEquals(bytes)) {
                "Identificador de fonte duplicado com conteúdo diferente."
            }
            return record(target)
        }

        // Validar a fonte no Skia, o mesmo motor de renderização do
        // Compose Desktop, ANTES de adicioná-la à biblioteca.
        val temp = Files.createTempFile(root, "bm-font-validation-", ".tmp")
        try {
            Files.write(temp, bytes, StandardOpenOption.TRUNCATE_EXISTING)
            require(validSkiaFont(temp)) {
                "Skia recusou a fonte TTF/OTF: dados inválidos ou não suportados."
            }
            try {
                // A cópia fica no mesmo volume e move sem REPLACE_EXISTING.
                Files.move(temp, target)
            } catch (_: FileAlreadyExistsException) {
                require(Files.readAllBytes(target).contentEquals(bytes)) {
                    "Arquivo de fonte apareceu com conteúdo divergente."
                }
            }
            return record(target)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    fun list(): List<DesktopFontRecord> {
        val root = directory()
        return Files.list(root).use { files ->
            files.filter {
                !Files.isSymbolicLink(it) &&
                    Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) &&
                    isSafeId(it.fileName.toString()) &&
                    it.fileName.toString()
                        .substringAfterLast('.', "")
                        .lowercase(Locale.ROOT) in validExtensions
            }.map { record(it) }
                .sorted(compareBy(String.CASE_INSENSITIVE_ORDER) { it.id })
                .toList()
        }
    }

    fun delete(fontId: String) {
        require(isSafeId(fontId)) { "Identificador de fonte inválido." }
        val root = directory()
        val target = root.resolve(fontId).normalize()
        require(target.parent == root) {
            "Arquivo fora da biblioteca de fontes."
        }
        require(!Files.isSymbolicLink(target)) {
            "Links simbólicos não podem ser removidos por esta função."
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            require(Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                "Entrada inválida na biblioteca de fontes."
            }
            Files.delete(target)
        }
    }

    private fun directory(): Path {
        val folder = location.toAbsolutePath().normalize()
        require(!Files.isSymbolicLink(folder)) {
            "Diretório de fontes não pode ser link simbólico."
        }
        Files.createDirectories(folder)
        require(Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(folder)) {
            "Biblioteca de fontes indisponível."
        }
        return folder
    }

    private fun validSkiaFont(path: Path): Boolean =
        runCatching {
            val typeface = FontMgr.default.makeFromFile(path.toString())
                ?: return@runCatching false
            try {
                typeface.glyphsCount > 0 && typeface.familyName.isNotBlank()
            } finally {
                typeface.close()
            }
        }.getOrDefault(false)

    private fun detectFormat(bytes: ByteArray): String? {
        if (bytes.size < 4) return null
        return when {
            bytes[0] == 0.toByte() && bytes[1] == 1.toByte() &&
                bytes[2] == 0.toByte() && bytes[3] == 0.toByte() -> "ttf"
            String(bytes, 0, 4, Charsets.US_ASCII) == "true" -> "ttf"
            String(bytes, 0, 4, Charsets.US_ASCII) == "OTTO" -> "otf"
            else -> null
        }
    }

    private fun record(file: Path): DesktopFontRecord {
        val name = file.fileName.toString()
        require(isSafeId(name)) { "Nome da fonte inválido." }
        val stem = name.substringBeforeLast(".")
        val displayName = stem.replace(tenHex, "")
            .replace('_', ' ').replace('-', ' ').trim()
            .ifBlank { "Fonte importada" }
        return DesktopFontRecord(
            id = name, displayName = displayName, fileName = name,
            extension = name.substringAfterLast(".").lowercase(Locale.ROOT),
            absolutePath = file.toAbsolutePath().normalize().toString()
        )
    }

    private fun sanitizeBase(name: String): String = name
        .trim().replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_', '.', '-').take(40)
        .ifBlank { "fonte" }

    private fun isSafeId(value: String): Boolean =
        value.length <= 80 &&
            Regex("[A-Za-z0-9._-]+-[0-9a-f]{10}\\.(ttf|otf)")
                .matches(value)
}
