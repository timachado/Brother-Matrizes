package com.timachado.brothermatrizes.font

import com.timachado.brothermatrizes.core.embroidery.SatinUnderlayMode
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class CleanRoomSatinEngineTest {
    private fun rectangle(
        left: Float, top: Float, width: Float, height: Float
    ) = listOf(
        CleanRoomSatinEngine.V(left, top),
        CleanRoomSatinEngine.V(left + width, top),
        CleanRoomSatinEngine.V(left + width, top + height),
        CleanRoomSatinEngine.V(left, top + height)
    )

    @Test
    fun firstLegOfMUsesOneTransverseSatinCoverageWithoutReturningToSameRail() {
        val plan = CleanRoomSatinEngine.planContours(
            listOf(rectangle(0f, 0f, 24f, 132f)), densityMm = 0.4f
        )
        val rails = plan.rails.flatten()
        assertTrue("A perna deve ter linhas Satin reais.", rails.size >= 15)
        val centers = rails.map { r ->
            r.middle.y.toInt() to r.middle.x.toInt()
        }
        assertEquals(
            "O eixo central da perna nao pode ser atravessado novamente.",
            centers.size,
            centers.distinct().size
        )
        val physical = CleanRoomSatinEngine.stitchPlannedGlyphForTest(plan)
        assertTrue("A perna deve ter cobertura, nao apenas deslocamentos.",
            physical.count { it.command == StitchCommand.STITCH } > 25)
        assertEquals("Sem underlay, nao se pode costurar um segundo passe.",
            0, physical.count { it.command == StitchCommand.TRIM })
    }

    @Test
    fun separateLegsRemainCoveredWithoutReusingTheFirstStroke() {
        val glyph = CleanRoomSatinEngine.planContours(
            listOf(
                rectangle(0f, 0f, 24f, 132f),
                rectangle(64f, 0f, 24f, 132f)
            ), densityMm = 0.4f
        )
        val all = glyph.rails.flatten()
        assertTrue("A primeira perna existe.", all.any { it.middle.x < 30f })
        assertTrue("A segunda perna existe.", all.any { it.middle.x > 60f })
        val stream = CleanRoomSatinEngine.stitchPlannedGlyphForTest(glyph)
        assertTrue(
            "A proxima perna deve ser alcancada sem bordar linha solta de viagem.",
            stream.count { it.command == StitchCommand.JUMP } >= 2
        )
    }

    @Test
    fun overlappingTrueTypeOutlinesDoNotEraseCursiveStrokes() {
        // Contornos com mesma orientação se sobrepõem em letras ornamentais.
        // Pela regra non-zero winding, a área comum permanece preenchida.
        // Pelo antigo even-odd, dois contornos iguais se anulavam inteiros.
        val stem = rectangle(0f, 0f, 24f, 132f)
        val plan = CleanRoomSatinEngine.planContours(
            listOf(stem, stem), densityMm = 0.4f
        )
        assertTrue(
            "Hastes com sobreposição de contornos não podem desaparecer.",
            plan.rails.flatten().size >= 15
        )
        val stitched = CleanRoomSatinEngine.stitchPlannedGlyphForTest(plan)
        assertTrue(
            "A geometria sobreposta deve produzir cobertura Satin.",
            stitched.any { it.command == StitchCommand.STITCH }
        )
    }

    @Test
    fun isolatedDotOfLetterIIsNotOmitted() {
        val glyph = CleanRoomSatinEngine.planContours(
            listOf(
                rectangle(0f, 0f, 20f, 75f),
                rectangle(5f, -33f, 10f, 10f)
            ), densityMm = 0.4f
        )
        val rails = glyph.rails.flatten()
        assertTrue("Haste do i mantida.", rails.any { it.middle.y > 0f })
        assertTrue("Pingo isolado do i precisa ser preenchido.",
            rails.any { it.middle.y < -22f })
    }

    @Test
    fun smallDetachedDotKeepsMultipleSatinRowsInsteadOfOneDash() {
        // Em alturas pequenas, a esqueletização pode reduzir um pingo a
        // um pixel; a forma original precisa de várias barras transversais.
        val plan = CleanRoomSatinEngine.planContours(
            listOf(
                rectangle(0f, 0f, 18f, 92f),
                rectangle(5f, -26f, 9f, 10f)
            ),
            densityMm = 0.4f
        )
        val dot = plan.rails.flatten().filter { it.middle.y < -15f }
        assertTrue("O pingo precisa de ao menos 3 barras Satin.", dot.size >= 3)
        assertTrue("As barras precisam preencher alturas distintas.",
            dot.map { it.middle.y.roundToInt() }.distinct().size >= 3)
        val stitches = CleanRoomSatinEngine.stitchPlannedGlyphForTest(plan)
        assertTrue("Pingo deve aparecer na sequência física de pontadas.",
            stitches.count { it.command == StitchCommand.STITCH && it.yUnits < -15 } >= 5)
    }

    @Test
    fun branchesContinueForwardBeforeVisitingOtherPartsOfTheLetter() {
        val ordered = CleanRoomSatinEngine.orderSkeletonRoutes(
            listOf(
                listOf(CleanRoomSatinEngine.V(0f, 0f), CleanRoomSatinEngine.V(30f, 0f)),
                listOf(CleanRoomSatinEngine.V(30f, 0f), CleanRoomSatinEngine.V(60f, 0f)),
                listOf(CleanRoomSatinEngine.V(60f, 0f), CleanRoomSatinEngine.V(90f, 0f)),
                listOf(CleanRoomSatinEngine.V(30f, 0f), CleanRoomSatinEngine.V(30f, -30f))
            ),
            gridStep = 1.2f
        )
        assertEquals(4, ordered.size)
        assertEquals(0f, ordered.first().first().x)
        // Continue no mesmo segmento do traço (x=30 -> x=60), em vez
        // de subir pelo ramo lateral logo após alcançar o entroncamento.
        assertEquals(60f, ordered[1].last().x)
        assertEquals(90f, ordered[2].last().x)
    }

    @Test
    fun disconnectedBranchesUseJumpWithoutSewingLooseConnectors() {
        val railA = CleanRoomSatinEngine.Rail(
            CleanRoomSatinEngine.V(0f, 0f), CleanRoomSatinEngine.V(6f, 0f)
        )
        val railB = CleanRoomSatinEngine.Rail(
            CleanRoomSatinEngine.V(17f, 0f), CleanRoomSatinEngine.V(23f, 0f)
        )
        val design = CleanRoomSatinEngine.stitchPlannedGlyphForTest(
            CleanRoomSatinEngine.PlannedGlyph(
                rails = listOf(listOf(railA), listOf(railB)),
                sampledEdges = 2,
                duplicateRailsDiscarded = 0
            )
        )
        assertTrue("O motor deve saltar até o segundo ramo sem costurar ligação.",
            design.any { it.command == StitchCommand.JUMP && it.xUnits == 17 })
    }

    @Test
    fun subpixelThinStemPreservesSatinCoverageAtSmallHoopSize() {
        // A letra cursiva reduzida pode ter regiões abaixo de 0,2 mm.
        // O contorno é real, mas não intercepta todos os centros do raster.
        val planned = CleanRoomSatinEngine.planContours(
            listOf(rectangle(3.15f, 0f, 0.85f, 82f)),
            densityMm = 0.4f,
            pullMm = 0.2f
        )
        val rails = planned.rails.flatten()
        assertTrue("Não descartar haste fina da TTF/OTF.", rails.size >= 10)
        assertTrue("A haste fina precisa atingir a região inferior.",
            rails.any { it.middle.y > 65f })
        assertTrue("A haste fina precisa atingir a região superior.",
            rails.any { it.middle.y < 16f })
    }

    @Test
    fun diagonalHairlineRetainsBothEndsWithoutGaps() {
        // Faixa diagonal mais fina que um pixel de raster, semelhante aos
        // arremates de M, a e r em fontes ornamentais reduzidas.
        val diagonal = listOf(
            CleanRoomSatinEngine.V(0f, 0f),
            CleanRoomSatinEngine.V(1.2f, 0f),
            CleanRoomSatinEngine.V(61.2f, 60f),
            CleanRoomSatinEngine.V(60f, 60f)
        )
        val planned = CleanRoomSatinEngine.planContours(
            listOf(diagonal), densityMm = 0.4f, pullMm = 0.2f
        )
        val rails = planned.rails.flatten()
        assertTrue("A faixa diagonal fina deve produzir pontadas Satin.",
            rails.size >= 8)
        assertTrue("Preservar o início da diagonal.",
            rails.any { it.middle.y < 12f })
        assertTrue("Preservar o fim da diagonal.",
            rails.any { it.middle.y > 48f })
    }

    @Test
    fun smallFontFidelityDoesNotReorderApprovedStrokeTraversal() {
        val planned = CleanRoomSatinEngine.planContours(
            listOf(rectangle(0f, 0f, 20f, 100f)),
            densityMm = 0.4f
        )
        val stitched = CleanRoomSatinEngine.stitchPlannedGlyphForTest(planned)
        assertTrue("O preenchimento Satin continua presente.",
            stitched.count { it.command == StitchCommand.STITCH } >= 15)
        assertEquals("A preservação da fonte não altera o tipo de comandos.",
            0, stitched.count { it.command == StitchCommand.TRIM })
    }

    @Test
    fun underlayAddsOneRunNotMultipleFullWidthLayers() {
        val glyph = CleanRoomSatinEngine.planContours(
            listOf(rectangle(0f, 0f, 20f, 160f)), densityMm = 0.4f
        )
        val without = CleanRoomSatinEngine.stitchPlannedGlyphForTest(
            glyph, SatinUnderlayMode.NONE
        )
        val with = CleanRoomSatinEngine.stitchPlannedGlyphForTest(
            glyph, SatinUnderlayMode.CENTER
        )
        val plainStitches = without.count { it.command == StitchCommand.STITCH }
        val underlayStitches = with.count { it.command == StitchCommand.STITCH }
        assertTrue("O underlay e uma passada central curta.", underlayStitches > plainStitches)
        assertTrue(
            "O underlay nao pode duplicar o preenchimento completo.",
            underlayStitches < plainStitches * 1.5f
        )
    }
}
