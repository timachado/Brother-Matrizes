package com.timachado.brothermatrizes.font

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Teste para emulador/dispositivo REAL: compara o Path integral do Android
 * com o contorno vetorial entregue ao planejador QA. Não usa fontes privadas.
 */
@RunWith(AndroidJUnit4::class)
class ImportedFontFullRunSkiaInstrumentedTest {
    private val fontFile: File by lazy {
        listOf(
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/RobotoStatic-Regular.ttf",
            "/system/fonts/NotoSans-Regular.ttf"
        ).map(::File).firstOrNull(File::isFile)
            ?: error("Fonte TTF do sistema indisponível para o teste de Skia.")
    }

    private fun reference(text: String, capUnits: Float): Path {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.createFromFile(fontFile)
            hinting = Paint.HINTING_OFF
            isSubpixelText = true
            textSize = 100f
        }
        val capPath = Path()
        paint.getTextPath("H", 0, 1, 0f, 0f, capPath)
        val capBounds = RectF()
        capPath.computeBounds(capBounds, true)
        val cap = if (!capPath.isEmpty && capBounds.height() > 0.001f)
            capBounds.height() else abs(paint.fontMetrics.ascent) * 0.72f
        paint.textSize = 100f * capUnits / cap

        val original = Path()
        paint.getTextPath(text, 0, text.length, 0f, 0f, original)
        val bounds = RectF()
        original.computeBounds(bounds, true)
        original.offset(-bounds.centerX(), -bounds.centerY())
        return original
    }

    private fun reconstructed(
        result: AndroidSkiaOutlineExtractor.Result
    ): Path = Path().apply {
        fillType = Path.FillType.WINDING
        result.glyphPolygons.single().forEach { ring ->
            if (ring.size >= 3) {
                moveTo(ring.first().x, ring.first().y)
                ring.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
        }
    }

    private fun rasterize(path: Path): Bitmap {
        val result = Bitmap.createBitmap(1400, 680, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        canvas.translate(700f, 340f)
        val fill = Paint().apply { color = Color.BLACK; isAntiAlias = false }
        canvas.drawPath(path, fill)
        return result
    }

    @Test
    fun fullRunFeedsTatamiAndKeepsBothStemsOfM() {
        val font = ImportedFont(
            id = "system-m-qa",
            displayName = "Android system font QA",
            fileName = fontFile.name,
            extension = "ttf",
            absolutePath = fontFile.absolutePath
        )
        val source = ImportedFontFullRunExtractor.extract(
            font = font, text = "M",
            targetCapHeightUnits = 170f, rotationDegrees = 0f
        )
        val commands = ImportedGlyphTatamiAdapter.digitize(
            source.glyphPolygons.single().map { ring ->
                ring.map { CleanRoomSatinEngine.V(it.x, it.y) }
            }
        )
        val sewn = commands.filter {
            it.command == com.timachado.brothermatrizes.core.embroidery.StitchCommand.STITCH
        }
        assertTrue("Tatami não produziu costuras para M", sewn.size > 30)
        val minX = sewn.minOf { it.xUnits }
        val maxX = sewn.maxOf { it.xUnits }
        val minY = sewn.minOf { it.yUnits }
        val maxY = sewn.maxOf { it.yUnits }
        val width = maxX - minX
        val height = maxY - minY
        assertTrue("A base da primeira haste desapareceu",
            sewn.any { it.xUnits < minX + width * 0.22f &&
                it.yUnits > minY + height * 0.7f })
        assertTrue("A base da segunda haste desapareceu",
            sewn.any { it.xUnits > minX + width * 0.78f &&
                it.yUnits > minY + height * 0.7f })
        assertTrue("O topo da segunda haste desapareceu",
            sewn.any { it.xUnits > minX + width * 0.78f &&
                it.yUnits < minY + height * 0.3f })
    }

    @Test
    fun qaFullGeneratorProducesFinishedPesDesign() {
        assertTrue(
            "Este teste só executa com -PbrotherTatamiQa=true",
            com.timachado.brothermatrizes.BuildConfig.ENABLE_IMPORTED_TATAMI_QA
        )
        val font = ImportedFont(
            id = "system-full-pipeline-qa",
            displayName = "Android system font QA",
            fileName = fontFile.name,
            extension = "ttf",
            absolutePath = fontFile.absolutePath
        )
        val settings = com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions(
            text = "MARIA",
            heightMm = 17f,
            spacingMm = 0f,
            style = com.timachado.brothermatrizes.core.embroidery.TextStitchStyle.SATIN,
            satinDensityMm = 0.4f,
            outputFormat = "PES"
        )
        val result = CleanRoomSatinEngine.generate(
            font, "MARIA", settings, "qa")
            .getOrThrow()
        assertTrue("Não houve pontadas no pipeline QA",
            result.points.any {
                it.command == com.timachado.brothermatrizes.core.embroidery.StitchCommand.STITCH
            })
        assertTrue("O acabamento nativo não foi aplicado ao planejador QA",
            result.machineFinishing?.autoTrimLongJumps == true)
        val sewn = result.points.filter {
            it.command == com.timachado.brothermatrizes.core.embroidery.StitchCommand.STITCH
        }
        assertTrue("Poucas pontadas para uma palavra inteira", sewn.size > 200)
        assertEquals(
            com.timachado.brothermatrizes.core.embroidery.StitchCommand.END,
            result.points.last().command
        )
        var previous = result.points.firstOrNull()
        for (next in result.points.drop(1)) {
            val prev = previous ?: break
            val distance = kotlin.math.hypot(
                (next.xUnits - prev.xUnits).toDouble(),
                (next.yUnits - prev.yUnits).toDouble()
            ) / 10.0
            if (next.command ==
                com.timachado.brothermatrizes.core.embroidery.StitchCommand.JUMP &&
                distance >= 6.0) {
                assertEquals("Um salto longo ficou sem corte",
                    com.timachado.brothermatrizes.core.embroidery.StitchCommand.TRIM,
                    prev.command)
            }
            previous = next
        }
    }

    @Test
    fun wholeShapedWordsMatchNativeAndroidTextPaths() {
        val imported = ImportedFont(
            id = "system-font-qa",
            displayName = "Android system font QA",
            fileName = fontFile.name,
            extension = "ttf",
            absolutePath = fontFile.absolutePath
        )
        for (word in listOf("M", "Maria", "BROTHER")) {
            val result = ImportedFontFullRunExtractor.extract(
                font = imported, text = word,
                targetCapHeightUnits = 170f, rotationDegrees = 0f
            )
            assertEquals("Texto completo deve ser um grupo topológico", 1, result.glyphPolygons.size)
            val native = rasterize(reference(word, 170f))
            val obtained = rasterize(reconstructed(result))
            try {
                var nativeInk = 0
                var difference = 0
                val a = IntArray(native.width * native.height)
                val b = IntArray(obtained.width * obtained.height)
                native.getPixels(a, 0, native.width, 0, 0, native.width, native.height)
                obtained.getPixels(b, 0, obtained.width, 0, 0, obtained.width, obtained.height)
                for (i in a.indices) {
                    if (a[i] == Color.BLACK) nativeInk++
                    if (a[i] != b[i]) difference++
                }
                assertTrue("Skia não produziu pixels de glifo para $word", nativeInk > 500)
                assertTrue("Geometria do texto '$word' divergiu da fonte do Android: " +
                    "$difference/$nativeInk pixels",
                    difference.toFloat() / nativeInk <= 0.09f)
            } finally {
                native.recycle()
                obtained.recycle()
            }
        }
    }
}
