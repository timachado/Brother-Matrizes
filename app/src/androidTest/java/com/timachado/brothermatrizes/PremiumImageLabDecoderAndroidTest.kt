package com.timachado.brothermatrizes

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.PremiumImagePhysicalLab
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android regression: decoding a valid PNG must NOT fail because
 * BitmapFactory.decodeStream(inJustDecodeBounds=true) returns null.
 */
@RunWith(AndroidJUnit4::class)
class PremiumImageLabDecoderAndroidTest {
    @Test fun actualPngImportsAndReachesStitchGenerator() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val w = 64
        val h = 64
        val pixels = IntArray(w*h) { index ->
            val x = index % w
            val y = index / w
            if (x in 10..54 && y in 10..54 &&
                !(x in 26..38 && y in 26..38)
            ) 0xffa96728.toInt() else 0
        }
        val bmp = Bitmap.createBitmap(
            pixels, w, h, Bitmap.Config.ARGB_8888
        )
        val file = File(app.cacheDir, "premium-import-alpha-regression.png")
        try {
            file.outputStream().use { stream ->
                assertTrue(bmp.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
            val decoded = PremiumImageLabDecoder.decode(
                app.contentResolver, Uri.fromFile(file)
            ).getOrThrow()
            assertEquals(w, decoded.width)
            assertEquals(h, decoded.height)
            assertTrue(decoded.argb.any { (it ushr 24) == 0 })
            assertTrue(decoded.argb.any { (it ushr 24) == 255 })
            val candidate = PremiumImagePhysicalLab.propose(
                decoded.width, decoded.height, decoded.argb,
                60f, HoopProfile.H100X100
            ).getOrThrow()
            assertTrue(candidate.stitchCount > 10)
        } finally {
            bmp.recycle()
            file.delete()
        }
    }

    @Test fun opaquePngIsRejectedWithUsefulMessage() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val bmp = Bitmap.createBitmap(
            24, 24, Bitmap.Config.ARGB_8888
        )
        bmp.eraseColor(0xff437ac4.toInt())
        val file = File(app.cacheDir, "premium-opaque-regression.png")
        try {
            file.outputStream().use {
                assertTrue(bmp.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            val decoded = PremiumImageLabDecoder.decode(
                app.contentResolver, Uri.fromFile(file)
            )
            assertTrue(decoded.isFailure)
            assertTrue(decoded.exceptionOrNull()?.message.orEmpty()
                .contains("transparente"))
        } finally {
            bmp.recycle()
            file.delete()
        }
    }
}
