package com.timachado.brothermatrizes.font

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import com.timachado.brothermatrizes.core.storage.AtomicFileWriter
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale

data class ImportedFont(
    val id: String,
    val displayName: String,
    val fileName: String,
    val extension: String,
    val absolutePath: String
)

object ImportedFontStore {
    private const val DIRECTORY = "imported_fonts"
    private const val MAX_FONT_BYTES = 12L * 1024L * 1024L
    private val allowedExtensions = setOf("ttf", "otf")

    fun list(context: Context): List<ImportedFont> =
        fontDirectory(context)
            .listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile &&
                    file.extension
                        .lowercase(Locale.ROOT) in allowedExtensions
            }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
            .map(::toImportedFont)

    fun importFont(
        context: Context,
        uri: Uri
    ): Result<ImportedFont> =
        runCatching {
            val resolver = context.contentResolver
            val sourceName =
                queryDisplayName(context, uri)
                    ?: uri.lastPathSegment
                    ?: "fonte"

            val declaredExtension =
                sourceName
                    .substringAfterLast('.', "")
                    .lowercase(Locale.ROOT)
                    .takeIf {
                        it in allowedExtensions
                    }

            val directory =
                fontDirectory(context)

            val temp =
                File.createTempFile(
                    "brother_matrizes-font-",
                    ".font",
                    directory
                )

            try {
                val digest =
                    MessageDigest.getInstance("SHA-256")

                var total = 0L

                resolver.openInputStream(uri)
                    ?.use { input ->
                        temp.outputStream()
                            .buffered()
                            .use { output ->
                                val buffer =
                                    ByteArray(32 * 1024)

                                while (true) {
                                    val read =
                                        input.read(buffer)

                                    if (read < 0) {
                                        break
                                    }

                                    total += read

                                    require(
                                        total <= MAX_FONT_BYTES
                                    ) {
                                        "A fonte deve ter no máximo 12 MB."
                                    }

                                    digest.update(
                                        buffer,
                                        0,
                                        read
                                    )

                                    output.write(
                                        buffer,
                                        0,
                                        read
                                    )
                                }
                            }
                    }
                    ?: error(
                        "Não foi possível abrir a fonte selecionada."
                    )

                require(total > 0L) {
                    "O arquivo de fonte está vazio."
                }

                val detectedExtension =
                    detectFontExtension(
                        temp
                    )

                val extension =
                    detectedExtension
                        ?: declaredExtension

                require(
                    extension in allowedExtensions
                ) {
                    "O arquivo selecionado não é uma fonte TTF/OTF válida."
                }

                Typeface.createFromFile(temp)

                val hash =
                    digest.digest()
                        .joinToString("") {
                            "%02x".format(it)
                        }
                        .take(10)

                val baseName =
                    if (
                        declaredExtension !=
                            null
                    ) {
                        sourceName
                            .substringBeforeLast('.')
                    } else {
                        sourceName
                    }

                val safeBase =
                    sanitizeBaseName(
                        baseName
                    )

                val existing =
                    directory
                        .listFiles()
                        .orEmpty()
                        .firstOrNull {
                            it.isFile &&
                                it.nameWithoutExtension
                                    .endsWith(
                                        "-$hash",
                                        ignoreCase = true
                                    )
                        }

                if (existing != null) {
                    temp.delete()
                    return@runCatching toImportedFont(
                        existing
                    )
                }

                val destination =
                    File(
                        directory,
                        "$safeBase-$hash.$extension"
                    )

                if (!temp.renameTo(destination)) {
                    AtomicFileWriter.write(
                        target =
                            destination
                    ) {
                            output ->
                        temp.inputStream()
                            .buffered()
                            .use {
                                input ->
                                input.copyTo(
                                    output,
                                    bufferSize =
                                        32 * 1024
                                )
                            }
                    }

                    temp.delete()
                }

                toImportedFont(destination)
            } catch (error: Throwable) {
                temp.delete()
                throw error
            }
        }

    fun delete(
        context: Context,
        font: ImportedFont
    ): Result<Unit> =
        runCatching {
            val directory =
                fontDirectory(context)
                    .canonicalFile

            val target =
                File(font.absolutePath)
                    .canonicalFile

            require(
                target.parentFile ==
                    directory
            ) {
                "Fonte inválida."
            }

            if (
                target.exists() &&
                !target.delete()
            ) {
                error(
                    "Não foi possível excluir a fonte."
                )
            }
        }

    fun loadTypeface(
        font: ImportedFont
    ): Result<Typeface> =
        runCatching {
            val file =
                File(font.absolutePath)

            require(file.isFile) {
                "O arquivo da fonte não está disponível."
            }

            Typeface.createFromFile(file)
        }

    private fun detectFontExtension(
        file: File
    ): String? =
        runCatching {
            val header =
                ByteArray(4)

            FileInputStream(file)
                .use {
                    if (
                        it.read(header) !=
                            header.size
                    ) {
                        return@runCatching null
                    }
                }

            when {
                header.contentEquals(
                    byteArrayOf(
                        0x00,
                        0x01,
                        0x00,
                        0x00
                    )
                ) ->
                    "ttf"

                String(
                    header,
                    Charsets.US_ASCII
                ) == "true" ->
                    "ttf"

                String(
                    header,
                    Charsets.US_ASCII
                ) == "OTTO" ->
                    "otf"

                else ->
                    null
            }
        }.getOrNull()

    private fun fontDirectory(
        context: Context
    ): File =
        File(
            context.filesDir,
            DIRECTORY
        ).apply {
            if (
                !exists()
            ) {
                check(
                    mkdirs()
                ) {
                    "Não foi possível preparar a biblioteca de fontes."
                }
            }

            check(
                isDirectory
            ) {
                "A biblioteca de fontes está indisponível."
            }
        }

    private fun toImportedFont(
        file: File
    ): ImportedFont {
        val extension =
            file.extension
                .lowercase(Locale.ROOT)

        val rawBase =
            file.nameWithoutExtension

        val displayName =
            rawBase
                .replace(
                    Regex("-[0-9a-fA-F]{10}$"),
                    ""
                )
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
                .ifBlank {
                    "Fonte importada"
                }

        return ImportedFont(
            id = file.name,
            displayName = displayName,
            fileName = file.name,
            extension = extension,
            absolutePath =
                file.absolutePath
        )
    }

    private fun sanitizeBaseName(
        value: String
    ): String =
        value
            .trim()
            .replace(
                Regex("[^A-Za-z0-9._-]+"),
                "_"
            )
            .trim('_', '.', '-')
            .take(40)
            .ifBlank {
                "fonte"
            }

    private fun queryDisplayName(
        context: Context,
        uri: Uri
    ): String? =
        runCatching {
            context
                .contentResolver
                .query(
                    uri,
                    arrayOf(
                        OpenableColumns
                            .DISPLAY_NAME
                    ),
                    null,
                    null,
                    null
                )
                ?.use { cursor ->
                    if (
                        cursor.moveToFirst()
                    ) {
                        val index =
                            cursor.getColumnIndex(
                                OpenableColumns
                                    .DISPLAY_NAME
                            )

                        if (index >= 0) {
                            cursor.getString(index)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
        }.getOrNull()
}
