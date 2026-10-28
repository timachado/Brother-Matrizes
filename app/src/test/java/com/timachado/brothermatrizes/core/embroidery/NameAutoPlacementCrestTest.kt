package com.timachado.brothermatrizes.core.embroidery

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressão geométrica, sem copiar a matriz PES privada do usuário:
 * moldura de brasão larga com região útil acima do centro da caixa,
 * além de um ornamento inferior que desloca o bounding box.
 */
class NameAutoPlacementCrestTest {
    private fun ornamentedCrest(): EmbroideryDesign {
        val points = listOf(
            EmbroideryPoint(-430, -400, StitchCommand.JUMP, 0),
            EmbroideryPoint(430, -400, StitchCommand.STITCH, 0),
            EmbroideryPoint(430, 100, StitchCommand.STITCH, 0),
            EmbroideryPoint(-430, 100, StitchCommand.STITCH, 0),
            EmbroideryPoint(-430, -400, StitchCommand.STITCH, 0),
            // Ornamento central: texto em y=0 deve ser rejeitado.
            EmbroideryPoint(-430, 0, StitchCommand.JUMP, 0),
            EmbroideryPoint(430, 0, StitchCommand.STITCH, 0),
            // Extensões inferiores à esquerda e direita do brasão.
            EmbroideryPoint(-965, 540, StitchCommand.JUMP, 0),
            EmbroideryPoint(-955, 550, StitchCommand.STITCH, 0),
            EmbroideryPoint(955, 540, StitchCommand.JUMP, 0),
            EmbroideryPoint(965, 550, StitchCommand.STITCH, 0),
            EmbroideryPoint(0, -550, StitchCommand.JUMP, 0),
            EmbroideryPoint(10, -540, StitchCommand.STITCH, 0),
            EmbroideryPoint(10, -540, StitchCommand.END, 0)
        )
        return EmbroideryIntegrity.normalize(
            EmbroideryDesign(
                fileName = "brasao-luxo-sintetico.pes",
                format = "PES",
                label = "Brasão geométrico",
                points = points,
                bounds = EmbroideryBounds(-965, 965, -550, 550),
                stitchCount = 9,
                jumpCount = 4,
                colorChanges = 0,
                endFound = true,
                sourceBytes = byteArrayOf(),
                threadColors = listOf(0x999999),
                hoopProfile = HoopProfile.H200X300
            )
        ).getOrThrow().design
    }

    @Test
    fun landscapeCrestScansInsideAreaAboveBoundingBoxCenter() {
        val offsets = NameAutoPlacement.candidateOffsets(ornamentedCrest())
        assertEquals(0 to 0, offsets.first())
        assertTrue("Buscar primeiro acima do centro do brasão",
            offsets.any { it.first == 0 && it.second <= -140 })
        assertTrue("Permitir correção horizontal sem mover ornamentos",
            offsets.any { it.first > 0 && it.second == 0 })
    }

    @Test
    fun importedFontKeepsItsIdentityAndAspectInDisplacedCrest() = runBlocking {
        val source = ornamentedCrest()
        val savedPoints = source.points.toList()
        val requested = TextMatrixOptions(
            text = "Maria",
            font = EmbroideryFontPreset.ELEGANT,
            importedFontId = "adamyia-qa",
            heightMm = 30f,
            style = TextStitchStyle.RUNNING,
            outputFormat = "PES",
            hoopProfile = HoopProfile.H200X300
        )
        val generator: (TextMatrixOptions) -> Result<EmbroideryDesign> = {
            options -> TextMatrixGenerator.generate(options.copy(importedFontId = null))
        }
        val fit = NameAutoPlacement.find(
            source, requested, importedGenerator = generator
        ).getOrThrow()
        assertEquals("adamyia-qa", fit.options.importedFontId)
        assertEquals(1f, fit.options.widthScale, 0f)
        assertEquals(1f, fit.options.heightScale, 0f)
        assertTrue("O nome deve mover-se do ornamento central para área livre",
            fit.centerYUnits < -50)
        assertTrue(NamePersonalization.canPlace(
            source, fit.options, fit.centerXUnits, fit.centerYUnits, generator
        ).isSuccess)
        assertEquals(savedPoints, source.points)
        assertEquals(savedPoints.dropLast(1),
            fit.design.points.take(savedPoints.size - 1))
    }
}
