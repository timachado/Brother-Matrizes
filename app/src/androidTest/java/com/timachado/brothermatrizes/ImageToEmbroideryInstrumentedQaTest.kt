package com.timachado.brothermatrizes

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.testTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.timachado.brothermatrizes.core.embroidery.EmbroideryLoadResult
import com.timachado.brothermatrizes.core.embroidery.GeneratedMatrixPipeline
import com.timachado.brothermatrizes.core.embroidery.MultiColorRasterDigitizer
import com.timachado.brothermatrizes.core.embroidery.MultiColorRasterOptions
import com.timachado.brothermatrizes.core.embroidery.RasterCropBounds
import com.timachado.brothermatrizes.core.embroidery.RasterImagePixels
import com.timachado.brothermatrizes.core.embroidery.RasterImageDecoder
import com.timachado.brothermatrizes.core.embroidery.RasterPhotoPreparation
import com.timachado.brothermatrizes.ui.theme.BrotherMatrizesTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests actual Android Compose rendering, BitmapFactory import and PES readback.
 * NO real Brother machine test is claimed by these tests.
 */
@RunWith(AndroidJUnit4::class)
class ImageToEmbroideryInstrumentedQaTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun homeToolsCardIsVisibleAndOpensDedicatedRoute() {
        var invoked = 0
        composeRule.setContent {
            BrotherMatrizesTheme {
                HomeScreen(
                    recent = null,
                    onCreateName = {},
                    onImageToEmbroidery = { invoked++ },
                    onFonts = {},
                    onTransfer = {},
                    onOpen = {},
                    onRecent = {},
                    onSimulate = {},
                    onEdit = {},
                    onConvert = {},
                    onClearRecent = {},
                    onUnavailable = {},
                    onCommunity = {}
                )
            }
        }
        composeRule.onNodeWithText("Ferramentas").assertExists()
        composeRule.onNodeWithText("Imagem para bordado")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertEquals(1, invoked) }
    }

    @Test
    fun dedicatedToolOpensImageModeWithoutSvgOrFreehandSelection() {
        composeRule.setContent {
            BrotherMatrizesTheme {
                CreateDrawingScreen(
                    onBack = {},
                    onCreate = {},
                    onSimulate = {},
                    imageToEmbroideryTool = true
                )
            }
        }
        composeRule.onNodeWithText("Imagem para Bordado").assertExists()
        composeRule.onNodeWithText("Da imagem para a matriz").assertExists()
        composeRule.onNodeWithText("Importar PNG, JPG ou WebP").assertExists()
        composeRule.onNodeWithText("Origem").assertDoesNotExist()
        composeRule.onNodeWithText("Gerar matriz").assertExists()
    }

    @Test
    fun cropCanvasDragUpdatesSelectedAreaOnAndroid() {
        val w = 90
        val h = 60
        val source = RasterImagePixels(
            w, h, IntArray(w * h) { 0xFF191919.toInt() }, "Gesto de recorte"
        )
        var latest = RasterCropBounds()
        composeRule.setContent {
            var crop by remember { mutableStateOf(RasterCropBounds()) }
            RasterPhotoCropCanvas(
                photo = source,
                selection = crop,
                onSelection = { selected ->
                    crop = selected
                    latest = selected
                },
                modifier = Modifier.testTag("photo-crop-canvas")
            )
        }
        composeRule.onNodeWithTag("photo-crop-canvas").performTouchInput {
            swipe(
                start = Offset(size.width * .34f, size.height * .25f),
                end = Offset(size.width * .68f, size.height * .75f),
                durationMillis = 800
            )
        }
        composeRule.runOnIdle {
            assertTrue(latest.left in 0f..0.5f)
            assertTrue(latest.right in 0.5f..1f)
            assertTrue(latest.right - latest.left > .15f)
            assertTrue(latest.bottom - latest.top > .35f)
        }
    }

    @Test
    fun androidPngDecoderBackgroundRemovalAndPesReadback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val w = 100
        val h = 68
        val pixels = IntArray(w * h) { 0xFF191919.toInt() }
        for (y in 15..49) {
            for (x in 16..40) pixels[y * w + x] = 0xFFED8A20.toInt()
            for (x in 57..84) pixels[y * w + x] = 0xFFF8F8F8.toInt()
        }
        val bitmap = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        val imageFile = File(context.cacheDir, "image-embroidery-qa.png")
        try {
            imageFile.outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            val decoded = RasterImageDecoder.decode(
                context.contentResolver, Uri.fromFile(imageFile)
            ).getOrThrow()
            assertEquals(w, decoded.width)
            assertEquals(h, decoded.height)
            val prepared = RasterPhotoPreparation.prepare(
                decoded, RasterCropBounds(0f, 0f, 1f, 1f),
                removeEdgeBackground = true,
                tolerance = 55
            ).getOrThrow()
            assertTrue(prepared.removedBackgroundPixels > 1500)
            val generated = MultiColorRasterDigitizer.generate(
                prepared.image,
                MultiColorRasterOptions(
                    widthMm = 68f,
                    maxColors = 2,
                    outputFormat = "PES"
                )
            ).getOrThrow()
            assertEquals("PES", generated.format)
            assertEquals(2, generated.threadColors.size)
            val reopened = GeneratedMatrixPipeline.canonicalize(
                generated, outputSuffix = "qa-android-photo"
            )
            assertTrue(
                "PES exportado precisa ser reaberto pelo aplicativo",
                reopened is EmbroideryLoadResult.Success
            )
            assertTrue(
                (reopened as EmbroideryLoadResult.Success).design.sourceBytes.isNotEmpty()
            )
        } finally {
            bitmap.recycle()
            imageFile.delete()
        }
    }
}
