package com.timachado.brothermatrizes.core.telegramvision

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class LocalVisionClues(
    val textFound: Boolean,
    val ocrTerms: List<String>,
    val visualTerms: List<String>
) {
    val searchTerms: List<String> get() = VisionTextClues.combine(ocrTerms, visualTerms)
}

/**
 * OCR and ordinary image-label recognition of ONLY the photo chosen by the user.
 * No chat photos are passed to an ML model. The group visual search still uses
 * a deterministic local image fingerprint.
 */
object LocalVisionAnalyzer {
    suspend fun analyze(context: Context, uri: Uri): LocalVisionClues =
        withContext(Dispatchers.Default) {
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val labeler = ImageLabeling.getClient(
                ImageLabelerOptions.Builder().setConfidenceThreshold(0.74f).build()
            )
            try {
                val ocr = try {
                    suspendCancellableCoroutine<String> { continuation ->
                        recognizer.process(image)
                            .addOnSuccessListener { result ->
                                if (continuation.isActive) continuation.resume(result.text)
                            }
                            .addOnFailureListener { error ->
                                if (continuation.isActive) continuation.resumeWithException(error)
                            }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) { "" }
                val labels = try {
                    suspendCancellableCoroutine<List<Pair<String, Float>>> { continuation ->
                        labeler.process(image)
                            .addOnSuccessListener { result ->
                                if (continuation.isActive) continuation.resume(
                                    result.map { it.text to it.confidence }
                                )
                            }
                            .addOnFailureListener { error ->
                                if (continuation.isActive) continuation.resumeWithException(error)
                            }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) { emptyList() }
                LocalVisionClues(
                    textFound = ocr.isNotBlank(),
                    ocrTerms = VisionTextClues.fromOcr(ocr),
                    visualTerms = VisionTextClues.fromVisualLabels(labels)
                )
            } finally {
                recognizer.close()
                labeler.close()
            }
        }
}
