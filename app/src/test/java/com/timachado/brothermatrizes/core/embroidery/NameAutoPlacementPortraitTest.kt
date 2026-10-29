package com.timachado.brothermatrizes.core.embroidery

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Regressão sintética: corredor floral vertical de 60 x 160 mm. */
class NameAutoPlacementPortraitTest {
    private fun floralCorridor(): EmbroideryDesign {
        val points = listOf(
            EmbroideryPoint(-300, -800, StitchCommand.JUMP, 0),
            EmbroideryPoint(-300, 800, StitchCommand.STITCH, 0),
            EmbroideryPoint(300, 800, StitchCommand.STITCH, 0),
            EmbroideryPoint(300, -800, StitchCommand.STITCH, 0),
            EmbroideryPoint(-300, -800, StitchCommand.STITCH, 0),
            EmbroideryPoint(-300, -800, StitchCommand.END, 0)
        )
        return EmbroideryIntegrity.normalize(
            EmbroideryDesign(
                fileName = "corredor-floral-13x18.pes",
                format = "PES",
                label = "Moldura sintética",
                points = points,
                bounds = EmbroideryBounds(-300, 300, -800, 800),
                stitchCount = 4,
                jumpCount = 1,
                colorChanges = 0,
                endFound = true,
                sourceBytes = byteArrayOf(),
                threadColors = listOf(0xAAAAAA),
                hoopProfile = HoopProfile.H130X180
            )
        ).getOrThrow().design
    }

    @Test
    fun portraitNameExpandsAlongLongAxisWithoutCoveringBorder() = runBlocking {
        val base = floralCorridor()
        val before = base.points.toList()
        val requested = TextMatrixOptions(
            text = "Maria",
            font = EmbroideryFontPreset.ELEGANT,
            heightMm = 12f,
            style = TextStitchStyle.SATIN,
            outputFormat = "PES",
            hoopProfile = HoopProfile.H130X180
        )
        val fit = NameAutoPlacement.find(base, requested).getOrThrow()
        assertEquals(90f, abs(fit.options.rotationDegrees), 0.1f)
        assertTrue("Nome deve crescer além do limite anterior de 17mm",
            fit.options.heightMm > 17f)
        assertTrue(NamePersonalization.canPlace(
            base, fit.options, fit.centerXUnits, fit.centerYUnits
        ).isSuccess)
        assertEquals(before, base.points)
        assertEquals(before.dropLast(1),
            fit.design.points.take(before.size - 1))
        assertEquals("Maria", fit.design.editableName?.options?.text)
        assertTrue(fit.design.stitchCount > base.stitchCount)
    }
    @Test
    fun importedShortNameExpandsAlongEmptyFloralCorridorAndKeepsSafeMargins() = runBlocking {
        val base = floralCorridor()
        val background = base.points.toList()
        val requested = TextMatrixOptions(
            text = "II",
            font = EmbroideryFontPreset.ELEGANT,
            importedFontId = "test-font-vector",
            heightMm = 30f,
            style = TextStitchStyle.RUNNING,
            outputFormat = "PES",
            hoopProfile = HoopProfile.H130X180
        )
        val generator: (TextMatrixOptions) -> Result<EmbroideryDesign> = { options ->
            TextMatrixGenerator.generate(options.copy(importedFontId = null))
        }
        val fit = NameAutoPlacement.find(base, requested,
            importedGenerator = generator).getOrThrow()
        assertEquals("O nome deve manter a rotação vertical",
            90f, kotlin.math.abs(fit.options.rotationDegrees), 0.1f)
        assertTrue("Usar comprimento disponível no corredor floral, se seguro",
            fit.options.widthScale > 1f)
        assertTrue("Não distorcer demasiadamente as letras",
            fit.options.widthScale <= 1.65f)
        assertEquals(1f, fit.options.heightScale, 0f)
        assertTrue(NamePersonalization.canPlace(
            base, fit.options, fit.centerXUnits, fit.centerYUnits,
            generator
        ).isSuccess)
        assertEquals(background, base.points)
        assertEquals(background.dropLast(1),
            fit.design.points.take(background.size - 1))
    }

}
