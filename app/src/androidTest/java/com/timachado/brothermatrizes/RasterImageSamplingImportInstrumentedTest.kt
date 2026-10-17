package com.timachado.brothermatrizes

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.timachado.brothermatrizes.core.embroidery.RasterImageDecoder
import com.timachado.brothermatrizes.core.embroidery.RasterEmbroideryApproval
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reproduces the gallery import regression on Android's real BitmapFactory.
 * With inJustDecodeBounds, decodeStream returns null EVEN ON SUCCESS:
 * the original PR #143 incorrectly propagated that null as an import error.
 *
 * No image leaves the emulator and no PES/JEF/DST is generated.
 */
@RunWith(AndroidJUnit4::class)
class RasterImageSamplingImportInstrumentedTest {
    private fun withGeneratedPhoto(
        width: Int,
        height: Int,
        format: Bitmap.CompressFormat,
        block: (Uri) -> Unit
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xffffffff.toInt())
        val pixels = IntArray(width * height) { 0xffffffff.toInt() }
        for (y in height / 4 until height * 3 / 4)
            for (x in width / 4 until width * 3 / 4)
                pixels[y * width + x] = 0xff153c58.toInt()
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        val suffix = if (format == Bitmap.CompressFormat.PNG) ".png" else ".jpg"
        val file = File(context.cacheDir, "qa-image-import-regression$suffix")
        try {
            file.outputStream().use { stream ->
                assertTrue(bitmap.compress(format, 95, stream))
            }
            block(Uri.fromFile(file))
        } finally {
            bitmap.recycle()
            file.delete()
        }
    }

    @Test fun importedPngPreservesDimensionsAfterBoundsOnlyDecode() {
        withGeneratedPhoto(128, 80, Bitmap.CompressFormat.PNG) { uri ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val decoded = RasterImageDecoder.decodeWithSamplingEvidence(
                context.contentResolver, uri
            ).getOrThrow()
            assertEquals(128, decoded.image.width)
            assertEquals(80, decoded.image.height)
            assertEquals(128, decoded.evidence.sourceWidth)
            assertEquals(80, decoded.evidence.sourceHeight)
            assertTrue(decoded.evidence.referenceIsFullResolution)
            assertEquals(64, decoded.evidence.sha256.length)
            assertFalse(decoded.evidence.isFullSourceProven)
            assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
        }
    }

    @Test fun importedHighResolutionJpegRetainsReferenceAndDownsampledPreview() {
        withGeneratedPhoto(800, 600, Bitmap.CompressFormat.JPEG) { uri ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val decoded = RasterImageDecoder.decodeWithSamplingEvidence(
                context.contentResolver, uri
            ).getOrThrow()
            assertEquals(800, decoded.evidence.sourceWidth)
            assertEquals(600, decoded.evidence.sourceHeight)
            assertTrue(decoded.image.width <= 512)
            assertTrue(decoded.image.height <= 512)
            assertEquals(800, decoded.evidence.referenceWidth)
            assertEquals(600, decoded.evidence.referenceHeight)
            assertFalse(decoded.evidence.workingIsFullResolution)
            assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
        }
    }
}
