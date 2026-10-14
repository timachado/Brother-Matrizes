package com.timachado.brothermatrizes

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timachado.brothermatrizes.font.AndroidSkiaOutlineExtractor
import com.timachado.brothermatrizes.font.ImportedFont
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Teste Android real (Paint / Skia), sem incorporar ao repositório arquivos
 * de fontes do DaFont pertencentes a terceiros.
 */
@RunWith(AndroidJUnit4::class)
class ImportedGlyphOutlineFidelityTest {
    private fun systemFont(): ImportedFont {
        val source = listOf(
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/NotoSans-Regular.ttf"
        ).map(::File).firstOrNull { it.isFile }
            ?: error("Não há fonte TTF de sistema disponível no emulador")
        return ImportedFont(
            id = source.name,
            displayName = "TTF de sistema",
            fileName = source.name,
            extension = "ttf",
            absolutePath = source.absolutePath
        )
    }

    @Test
    fun fullTextOutlineIsNotSplitByFakeIndependentGlyphRender() {
        val font = systemFont()
        val sample = "MAMA"
        val height = 220f
        val actual = AndroidSkiaOutlineExtractor.extract(
            font, sample, height, spacingUnits = 0f, rotationDegrees = 0f
        )
        val points = actual.glyphPolygons.flatten().flatten()
        assertTrue("A palavra precisa ter contornos reais", points.size > 100)
        assertTrue("Cada glifo precisa de geometria", actual.glyphPolygons.size >= 2)
        // O valor absoluto do texto foi centralizado como conjunto.
        val centerX = (points.minOf { it.x } + points.maxOf { it.x }) * 0.5f
        val centerY = (points.minOf { it.y } + points.maxOf { it.y }) * 0.5f
        assertTrue(kotlin.math.abs(centerX) < 2.5f)
        assertTrue(kotlin.math.abs(centerY) < 2.5f)

        // Extrair o MESMO run Android, medindo o aspecto do contorno.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.createFromFile(File(font.absolutePath))
            textSize = 100f
            isSubpixelText = true
            hinting = Paint.HINTING_OFF
        }
        val h = Path().also { paint.getTextPath("H", 0, 1, 0f, 0f, it) }
        val hb = RectF().also { h.computeBounds(it, true) }
        paint.textSize = 100f * height / hb.height()
        val run = Path().also {
            paint.getTextPath(sample, 0, sample.length, 0f, 0f, it)
        }
        val bounds = RectF().also { run.computeBounds(it, true) }
        val fromPaint = bounds.width() / bounds.height()
        val fromExtractor = actual.sourceWidth / actual.sourceHeight
        assertTrue("A conversão deve receber as proporções exatas da prévia Android",
            kotlin.math.abs(fromPaint - fromExtractor) < 0.001f)
    }

    @Test
    fun manualSpacingKeepsHistoricalExtractorPath() {
        val font = systemFont()
        val single = AndroidSkiaOutlineExtractor.extract(
            font, "MA", 180f, spacingUnits = 0f, rotationDegrees = 0f
        )
        val spaced = AndroidSkiaOutlineExtractor.extract(
            font, "MA", 180f, spacingUnits = 30f, rotationDegrees = 0f
        )
        assertTrue("Espaçamento explícito deve seguir gerador aprovado",
            spaced.sourceWidth > single.sourceWidth)
    }
}
