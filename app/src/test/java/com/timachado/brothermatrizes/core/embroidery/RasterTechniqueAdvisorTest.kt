package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterTechniqueAdvisorTest {
    private fun art(
        paint: (Int, Int) -> Int, w: Int = 160, h: Int = 100
    ): RasterImagePixels {
        val pixels = IntArray(w * h) { pos ->
            paint(pos % w, pos / w)
        }
        return RasterImagePixels(w, h, pixels, "qa escolha de técnica")
    }
    private val navy = 0xff183a5a.toInt()
    private val gold = 0xffc9953b.toInt()
    private val white = 0xffffffff.toInt()

    @Test fun simpleSolidStrokeCanBeSatinButNeverMachinePES() {
        val image = art({ x, y ->
            if (x in 20..119 && y in 30..40) navy else white
        })
        val result = RasterTechniqueAdvisor.assess(
            image, 85f, HoopProfile.H130X180
        ).getOrThrow()
        assertEquals(1, result.candidates.size)
        assertEquals(RasterTechnique.SATIN_QA,
            result.candidates.single().technique)
        assertFalse(result.needsDigitizing)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun largeAreaNeedsFillInsteadOfFakeSatin() {
        val image = art({ x, y ->
            if (x in 25..100 && y in 15..80) gold else white
        })
        val result = RasterTechniqueAdvisor.assess(
            image, 90f, HoopProfile.H130X180
        ).getOrThrow()
        assertEquals(RasterTechnique.FILL_NEEDS_DIGITIZING,
            result.candidates.single().technique)
        assertTrue(result.needsDigitizing)
        assertTrue(result.brief().contains("preenchimento"))
    }

    @Test fun narrowHairlineIsAContourCandidateNotSatin() {
        val image = art({ x, y ->
            if (x in 20..119 && y in 43..45) navy else white
        })
        val result = RasterTechniqueAdvisor.assess(
            image, 25f, HoopProfile.H130X180
        ).getOrThrow()
        assertTrue(result.candidates.any {
            it.technique == RasterTechnique.RUNNING_NEEDS_DIGITIZING
        })
        assertTrue(result.needsDigitizing)
    }

    @Test fun misfittingHoopCannotReceiveTechniques() {
        val image = art({ x, y ->
            if (x in 20..119 && y in 30..40) navy else white
        })
        assertTrue(
            RasterTechniqueAdvisor.assess(image, 180f, HoopProfile.H100X100)
                .isFailure
        )
    }

    @Test fun combinedArtDoesNotClaimEveryObjectIsAutomaticallyReady() {
        val image = art({ x, y ->
            when {
                x in 10..110 && y in 20..29 -> navy
                x in 115..150 && y in 50..88 -> gold
                else -> white
            }
        })
        val report = RasterTechniqueAdvisor.assess(
            image, 90f, HoopProfile.H130X180
        ).getOrThrow()
        assertEquals(2, report.candidates.size)
        assertTrue(report.candidates.any {
            it.technique == RasterTechnique.FILL_NEEDS_DIGITIZING
        })
        assertTrue(report.needsDigitizing)
    }
}
