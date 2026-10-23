package com.timachado.brothermatrizes.core.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object TelegramImageAssistAnalyzer {
    /** Bound image decoding to avoid exhausting entry-level Android devices. */
    fun decodeThumbnail(context: Context, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val probe = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Não foi possível ler a foto.")
        probe.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth in 1..30000 && bounds.outHeight in 1..30000) {
            "Imagem inválida."
        }
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024)
            sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IllegalArgumentException("Não foi possível decodificar a foto.")
    }

    suspend fun identifyTerm(context: Context, uri: Uri): String? =
        withContext(Dispatchers.Default) {
            val bitmap = withContext(Dispatchers.IO) { decodeThumbnail(context, uri) }
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val labeler = ImageLabeling.getClient(
                ImageLabelerOptions.Builder().setConfidenceThreshold(0.80f).build()
            )
            try {
                val ocr = try {
                    suspendCancellableCoroutine<String> { cont ->
                        recognizer.process(image)
                            .addOnSuccessListener {
                                if (cont.isActive) cont.resume(it.text)
                            }
                            .addOnFailureListener {
                                if (cont.isActive) cont.resumeWithException(it)
                            }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    ""
                }
                val labels = try {
                    suspendCancellableCoroutine<List<Pair<String, Float>>> { cont ->
                        labeler.process(image)
                            .addOnSuccessListener { found ->
                                if (cont.isActive) cont.resume(
                                    found.map { it.text to it.confidence }
                                )
                            }
                            .addOnFailureListener {
                                if (cont.isActive) cont.resumeWithException(it)
                            }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
                TelegramImageQuery.choose(ocr, labels)
            } finally {
                recognizer.close()
                labeler.close()
                bitmap.recycle()
            }
        }
}
