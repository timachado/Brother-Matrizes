package com.timachado.brothermatrizes

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.timachado.brothermatrizes.core.embroidery.FabricProfile
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.SatinUnderlayMode
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.font.ImportedFont
import com.timachado.brothermatrizes.font.ImportedFontMatrixGenerator
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Inspect actual production dispatch and emitted stitch order for
 * Maria / Great Vibes. A preview-only test will not catch bad ordering
 * of the serialized embroidery points.
 */
@RunWith(AndroidJUnit4::class)
class MariaSatinPathDiagnosticTest {
    @Test
    fun writeCompleteActualStitchSequence() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = File(context.cacheDir, "GreatVibes-Regular.ttf")
        instrumentation.context.assets.open("GreatVibes-Regular.ttf").use {
            input -> fixture.outputStream().use { input.copyTo(it) }
        }
        val font = ImportedFont(
            id = fixture.name,
            displayName = "Great Vibes",
            fileName = fixture.name,
            extension = "ttf",
            absolutePath = fixture.absolutePath
        )
        val config = TextMatrixOptions(
            text = "Maria",
            heightMm = 40f,
            spacingMm = 0f,
            stitchLengthMm = 2.5f,
            style = TextStitchStyle.SATIN,
            satinWidthMm = 2.4f,
            satinDensityMm = 0.4f,
            satinPullCompensationMm = 0.2f,
            satinShortStitches = true,
            satinUnderlayMode = SatinUnderlayMode.CENTER,
            specialStitchMode = null,
            color = 0xE63946,
            outputFormat = "PES",
            hoopProfile = HoopProfile.H200X300,
            fabricProfile = FabricProfile.COTTON,
            enforceHoop = false,
            rotationDegrees = 0f
        )
        val matrix = ImportedFontMatrixGenerator.generateText(
            font = font, text = "Maria", options = config
        ).getOrThrow()
        val points = matrix.points
        assertTrue("A matriz deve conter pontos", points.isNotEmpty())
        val minimumX = points.minOf { it.xUnits }
        val maximumX = points.maxOf { it.xUnits }
        val minimumY = points.minOf { it.yUnits }
        val maximumY = points.maxOf { it.yUnits }
        val width = (maximumX - minimumX).coerceAtLeast(1)
        val height = (maximumY - minimumY).coerceAtLeast(1)
        val csv = buildString {
            appendLine("index,command,xUnits,yUnits,xFraction,yFraction")
            points.forEachIndexed { index, point ->
                appendLine("$"+"{index},$"+"{point.command.name},$"+"{point.xUnits},$"+"{point.yUnits},$"+"{(point.xUnits - minimumX).toFloat() / width},$"+"{(point.yUnits - minimumY).toFloat() / height}")
            }
        }
        File(context.filesDir, "maria-actual-sequence.csv").writeText(csv)
        Log.i("MariaSequence", "TOTAL=$"+"{points.size} BOUNDS=$"+"{minimumX},$"+"{minimumY} TO $"+"{maximumX},$"+"{maximumY}")
        points.take(35).forEachIndexed { i,p ->
            Log.i("MariaSequence","FIRST_POINT=$"+"{i} $"+"{p.command.name} $"+"{p.xUnits},$"+"{p.yUnits}")
        }
        for (fraction in listOf(0.05,0.10,0.20,0.30)) {
            val prefix = points.take((points.size * fraction).toInt().coerceAtLeast(1))
            val avgX = prefix.map { (it.xUnits - minimumX).toFloat()/width }.average()
            val minX = prefix.minOf { it.xUnits }
            val maxX = prefix.maxOf { it.xUnits }
            val jumpCount= prefix.count { it.command == StitchCommand.JUMP }
            Log.i("MariaSequence","PREFIX=$"+"{fraction} avgX=$"+"{avgX} minX=$"+"{minX} maxX=$"+"{maxX} jumps=$"+"{jumpCount}")
        }
    }
}
