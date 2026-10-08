package com.timachado.brothermatrizes

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryLoadResult
import com.timachado.brothermatrizes.core.embroidery.EmbroiderySequenceAudit
import com.timachado.brothermatrizes.core.embroidery.GeneratedMatrixPipeline
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Gera diagnostico apenas quando o usuario solicitar explicitamente.
 * Usa a matriz da PREVIA e a mesma canonicalizacao PES que alimenta
 * o simulador; nao copia nem publica o arquivo TTF/OTF do usuario.
 */
internal object MatrixSequenceDiagnosticExport {
    // API 29+ is checked BEFORE referencing MediaStore.Downloads.
    // This beta action is unavailable on devices running Android 9 or older.
    @SuppressLint("NewApi")
    fun export(context: Context, created: EmbroideryDesign): String {
        require(Build.VERSION.SDK_INT >= 29) {
            "Exportação CSV requer Android 10 ou superior."
        }

        val reopened = GeneratedMatrixPipeline.canonicalize(
            design = created,
            outputSuffix = "sequencia-diagnostico"
        )
        require(reopened is EmbroideryLoadResult.Success) {
            "A matriz PES não pôde ser reaberta para comparação."
        }
        val reopenedDesign = (reopened as EmbroideryLoadResult.Success).design
        val comparison = EmbroiderySequenceAudit.compare(created, reopenedDesign)

        val fileName = "Brother-Matrizes-sequencia-${System.currentTimeMillis()}.zip"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Não foi possível criar o diagnóstico em Downloads.")

        try {
            val stream = resolver.openOutputStream(uri)
                ?: error("Não foi possível abrir o arquivo de diagnóstico.")
            stream.use { output ->
                ZipOutputStream(output).use { archive ->
                    fun add(name: String, content: String) {
                        archive.putNextEntry(ZipEntry(name))
                        archive.write(content.toByteArray(Charsets.UTF_8))
                        archive.closeEntry()
                    }
                    add(
                        "01-pontos-gerados.csv",
                        EmbroiderySequenceAudit.fullCsv("generated", created)
                    )
                    add(
                        "02-pontos-reabertos-PES.csv",
                        EmbroiderySequenceAudit.fullCsv("reopened-PES", reopenedDesign)
                    )
                    add(
                        "03-comparacao.txt",
                        comparison.summary() + "\n\n" +
                            comparison.before.csv() + "\n" +
                            comparison.after.csv()
                    )
                }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return fileName
    }
}
