package com.timachado.brothermatrizes.font

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * DaFont archives are untrusted. Never extract using ZipEntry paths.
 * Read only bounded .ttf/.otf entries into an isolated cache directory,
 * and pass each one through the existing Typeface/SHA-256 validator.
 */
object FontArchiveImporter {
    private const val MAX_ENTRIES = 128
    private const val MAX_TOTAL_BYTES = 48L * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 12L * 1024 * 1024

    internal fun permittedEntryName(name: String): String? {
        val filename = name.replace('\\', '/').substringAfterLast('/')
        if (filename.isBlank() || filename == "." || filename == "..") return null
        if (filename.any { it.code < 32 }) return null
        val extension = filename.substringAfterLast('.', "").lowercase()
        if (extension !in setOf("ttf", "otf")) return null
        return filename.take(160)
    }

    fun importZip(context: Context, uri: Uri): Result<List<ImportedFont>> = runCatching {
        val cache = File(context.cacheDir, "dafont-import-${UUID.randomUUID()}")
        require(cache.mkdirs()) { "Não foi possível preparar o download." }
        val fonts = mutableListOf<ImportedFont>()
        var scanned = 0
        var total = 0L
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: error("Não foi possível ler o arquivo ZIP.")
            ZipInputStream(stream.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    scanned++
                    require(scanned <= MAX_ENTRIES) { "ZIP com arquivos demais." }
                    val filename = if (entry.isDirectory) null else permittedEntryName(entry.name)
                    val temp = filename?.let { File(cache, it) }
                    var bytes = 0L
                    // Bound decompression of *every* entry including skipped
                    // content: closeEntry() alone could consume a ZIP bomb.
                    temp?.outputStream()?.buffered().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            bytes += read
                            total += read
                            require(total <= MAX_TOTAL_BYTES &&
                                (temp == null || bytes <= MAX_ENTRY_BYTES)) {
                                "O arquivo ZIP ultrapassou o limite seguro."
                            }
                            output?.write(buffer, 0, read)
                        }
                    }
                    if (temp != null) {
                        // The existing importer checks the font signature,
                        // Typeface parser, maximum size and SHA-256 hash.
                        val imported = ImportedFontStore.importFont(context, Uri.fromFile(temp))
                        imported.getOrNull()?.let { font ->
                            if (fonts.none { it.id == font.id }) fonts += font
                        }
                        temp.delete()
                    }
                    zip.closeEntry()
                }
            }
            require(fonts.isNotEmpty()) {
                "O ZIP não contém fontes TTF/OTF válidas."
            }
            fonts
        } finally {
            cache.deleteRecursively()
        }
    }
}
