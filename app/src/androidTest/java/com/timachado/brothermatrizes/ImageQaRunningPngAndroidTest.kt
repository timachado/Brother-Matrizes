package com.timachado.brothermatrizes

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.RasterEmbroideryApproval
import com.timachado.brothermatrizes.core.embroidery.RasterImageDecoder
import com.timachado.brothermatrizes.core.embroidery.RasterQaRunningStitchDraft
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end Android PNG import -> true internal needle-path commands.
 *
 * Runs only on GitHub's emulator. Never writes or shares PES/DST/JEF,
 * never claims a physical embroidery-machine stitch-out.
 */
@RunWith(AndroidJUnit4::class)
class ImageQaRunningPngAndroidTest {
    @Test fun pngWithRealAlphaCounterReachesGuardedRunningSimulatorCommands() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val w = 12
        val h = 12
        val argb = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            if (x in 2..9 && y in 2..9 &&
                !(x in 5..6 && y in 5..6)
            ) 0xffb35624.toInt() else 0
        }
        val bitmap = Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888)
        val file = File(context.cacheDir, "qa-ring-transparency-no-pes.png")
        try {
            file.outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            val decoded = RasterImageDecoder.decodeWithSamplingEvidence(
                context.contentResolver, Uri.fromFile(file)
            ).getOrThrow()
            assertEquals(w, decoded.image.width)
            assertEquals(h, decoded.image.height)
            assertFalse(decoded.evidence.needsDetailReview)
            assertEquals(0, decoded.evidence.potentiallyLostPixels)

            val draft = RasterQaRunningStitchDraft.create(
                decoded.image, 120f, HoopProfile.H130X180,
                maxWaypointSpacingMm = 2f
            ).getOrThrow()
            assertEquals("QA_RUNNING_REVIEW", draft.design.format)
            assertEquals(1, draft.counterCount)
            assertEquals(2, draft.outlineCount)
            assertTrue(draft.design.points.any {
                it.command == StitchCommand.TRIM
            })
            assertTrue(draft.needleRisk.stitchesExamined > 0)
            assertEquals(draft.needleRisk.stitchesExamined,
                draft.threadDensity.sewnSegments)
            assertTrue(draft.design.sourceBytes.isEmpty())
            assertFalse(draft.exportPermitted)
            assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
            assertTrue(MatrixConverter.convert(
                draft.design,"PES","android-test-blocked"
            ).isFailure)
        } finally {
            bitmap.recycle()
            file.delete()
        }
    }
}
