package com.timachado.brothermatrizes.core.embroidery

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.timachado.brothermatrizes.core.storage.AtomicFileWriter
import java.io.File
import java.io.FileOutputStream

object MatrixExporter {
    fun saveCopy(
        contentResolver: ContentResolver,
        destination: Uri,
        design: EmbroideryDesign
    ): Result<Unit> =
        saveBytes(
            contentResolver = contentResolver,
            destination = destination,
            bytes = design.sourceBytes
        )

    fun saveBytes(
        contentResolver: ContentResolver,
        destination: Uri,
        bytes: ByteArray
    ): Result<Unit> = runCatching {
        require(
            bytes.isNotEmpty()
        ) {
            "O arquivo preparado está vazio."
        }

        val descriptor =
            contentResolver
                .openFileDescriptor(
                    destination,
                    "w"
                )

        if (
            descriptor !=
                null
        ) {
            descriptor.use {
                    parcel ->
                FileOutputStream(
                    parcel.fileDescriptor
                ).use {
                        output ->
                    output.write(
                        bytes
                    )

                    output.flush()

                    // Alguns provedores SAF (ex.: nuvem) não implementam fsync.
                    // A escrita/flush continuam obrigatórios; fsync é reforço
                    // quando o destino expõe um descritor de arquivo real.
                    runCatching {
                        output.fd.sync()
                    }
                }
            }
        } else {
            val output =
                contentResolver
                    .openOutputStream(
                        destination,
                        "w"
                    )
                    ?: error(
                        "Não foi possível abrir o destino selecionado."
                    )

            output.use {
                it.write(
                    bytes
                )

                it.flush()
            }
        }
    }

    fun createShareIntent(
        context: Context,
        design: EmbroideryDesign
    ): Result<Intent> =
        createShareIntent(
            context = context,
            fileName = design.fileName,
            bytes = design.sourceBytes
        )

    fun createShareIntent(
        context: Context,
        fileName: String,
        bytes: ByteArray
    ): Result<Intent> = runCatching {
        val sharedDir =
            File(context.cacheDir, "shared").apply {
                if (!exists() && !mkdirs()) {
                    error("Não foi possível preparar a pasta temporária.")
                }
            }

        require(
            bytes.isNotEmpty()
        ) {
            "O arquivo preparado está vazio."
        }

        val safeName = safeFileName(fileName)
        val file = File(sharedDir, safeName)

        AtomicFileWriter.write(
            target =
                file,
            bytes =
                bytes
        )

        val contentUri =
            FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )

        Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, safeName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun safeFileName(input: String): String {
        val cleaned = input
            .replace(
                Regex("[\\/:*?\"<>|\\p{Cntrl}]"),
                "_"
            )
            .trim()
            .trim('.')
            .take(120)

        return cleaned.ifBlank { "matriz.dst" }
    }
}
