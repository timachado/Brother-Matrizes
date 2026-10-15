package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualSatinRailsTest {
    private fun curvedSection(): ManualSatinSection {
        val rails = (0..8).map { step ->
            val v = 0.12f + step * 0.085f
            val cx = 0.30f + (sin(step / 2.5) * .028).toFloat()
            SatinRailPair(cx - .025f, v, cx + .025f, v)
        }
        return ManualSatinSection(rails)
    }

    private fun generate(
        section: ManualSatinSection = curvedSection(),
        density: Float = .5f,
        underlay: Boolean = true
    ) = ManualSatinRails.generate(
        sections = listOf(section),
        outputColorIndex = 0,
        widthMm = 65f,
        imageAspectRatio = 1.8f,
        densityMm = density,
        pullCompensationMm = .10f,
        centerUnderlay = underlay
    )

    @Test fun railsCreateStitchesWithCorrectColorAndNoSpuriousChange() {
        val r = generate()
        assertTrue(r.exceptionOrNull()?.message ?: "No stitches", r.isSuccess)
        val points = r.getOrThrow()
        assertTrue(points.count { it.command == StitchCommand.STITCH } > 30)
        assertEquals(StitchCommand.JUMP, points.first().command)
        assertTrue(points.all { it.colorIndex == 0 })
        assertEquals(0, points.count { it.command == StitchCommand.COLOR_CHANGE })
    }

    @Test fun densityAndUnderlayChangeRealStitchCount() {
        val dense = generate(density = .4f, underlay = true).getOrThrow()
        val loose = generate(density = .9f, underlay = false).getOrThrow()
        assertTrue(dense.size > loose.size)
    }

    @Test fun manualSectionsAreSeparateWithTrimAndJump() {
        val first = curvedSection()
        val second = ManualSatinSection(first.rails.map {
            it.copy(ax = it.ax + .2f, bx = it.bx + .2f)
        })
        val out = ManualSatinRails.generate(
            listOf(first, second), 1, 65f, 1.8f, .5f, .1f, true
        ).getOrThrow()
        assertEquals(1, out.count { it.command == StitchCommand.TRIM })
        assertTrue(out.count { it.command == StitchCommand.JUMP } >= 2)
        assertTrue(out.all { it.colorIndex == 1 })
    }

    @Test fun railsReversedMidwayFailInsteadOfProducingCrossedStitches() {
        val original = curvedSection()
        val rails = original.rails.toMutableList()
        val midpoint = rails[4]
        rails[4] = midpoint.copy(
            ax = midpoint.bx, ay = midpoint.by,
            bx = midpoint.ax, by = midpoint.ay
        )
        assertTrue(generate(ManualSatinSection(rails)).isFailure)
    }

    @Test fun wideRailsAndTooFewPairsFailSafely() {
        val wide = curvedSection().rails.map {
            it.copy(ax = .05f, bx = .25f)
        }
        assertTrue(generate(ManualSatinSection(wide)).isFailure)
        assertTrue(generate(ManualSatinSection(listOf(curvedSection().rails.first()))).isFailure)
    }

    @Test fun manualRailsIntegrateIntoMultiColorPesReopening() {
        val w = 104
        val h = 65
        val pixels = IntArray(w * h) { 0xFF0D0D0D.toInt() }
        for (y in 13..50) {
            for (x in 15..20) pixels[y * w + x] = 0xFFEC881D.toInt()
            for (x in 65..91) pixels[y * w + x] = 0xFFF7F7F7.toInt()
        }
        val src = RasterImagePixels(w, h, pixels, "Colunas manuais")
        val options = MultiColorRasterOptions(
            widthMm = 60f, outputFormat = "PES", maxColors = 2
        )
        val art = MultiColorRasterDigitizer.separate(src, options).getOrThrow()
        assertEquals(2, art.colors.size)
        val orangeIndex = art.pixels[20 * w + 17]
        val whiteIndex = art.pixels[20 * w + 70]
        val manual = ManualSatinSection((0..6).map { i ->
            val v = .10f + .12f * i
            SatinRailPair(.02f, v, .06f, v)
        })
        // Section is manually placed in the crop normalized coordinates,
        // independent of the original image-resolution dimensions.
        val built = MultiColorRasterDigitizer.generateSeparated(
            src, art,
            options.copy(
                layers = listOf(
                    ColorLayerEdit(
                        sourceIndex = orangeIndex,
                        stitchMode = RasterLayerStitchMode.MANUAL_SATIN,
                        manualSections = listOf(manual)
                    ),
                    ColorLayerEdit(sourceIndex = whiteIndex)
                )
            )
        )
        assertTrue(built.exceptionOrNull()?.message ?: "PES generation failure",
            built.isSuccess)
        val design = built.getOrThrow()
        assertEquals(2, design.threadColors.size)
        assertEquals(1, design.colorChanges)
        assertTrue(design.points.any { it.command == StitchCommand.COLOR_CHANGE })
        val parsed = GeneratedMatrixPipeline.canonicalize(design, "manual-rails-pes")
        assertTrue("PES must reopen", parsed is EmbroideryLoadResult.Success)
        val reopened = (parsed as EmbroideryLoadResult.Success).design
        assertEquals(2, reopened.threadColors.size)
        assertTrue(reopened.sourceBytes.isNotEmpty())
    }
}
