package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterAssistedElementPlannerTest {
    private val orange = 0xffdb771c.toInt()
    private val white = 0xffeeeeee.toInt()
    private val blue = 0xff2681b9.toInt()

    private fun image(
        width: Int = 90, height: Int = 90,
        pixel: (Int, Int) -> Int
    ): RasterImagePixels = RasterImagePixels(
        width, height,
        IntArray(width * height) { i -> pixel(i % width, i / width) },
        "desenho original QA"
    )

    @Test fun texturedSymbolAndWhiteLettersRemainDistinctWithHoles() {
        val original = image { x,y ->
            when {
                x in 12..39 && y in 10..44 &&
                    !(x in 20..31 && y in 18..36) ->
                    if ((x+y) % 2 == 0) orange else 0xffad4a10.toInt()
                x in 17..44 && y in 58..65 -> white
                x in 62..80 && y in 58..65 -> white
                else -> 0
            }
        }
        val study = RasterThreadTextureStudy.inspect(original).getOrThrow()
        val plan = RasterAssistedElementPlanner.propose(original,study).getOrThrow()
        assertEquals(3,plan.totalComponents)
        assertEquals(3,plan.elements.size)
        assertTrue(plan.originalAlphaPreserved)
        assertEquals(0,plan.undisplayedForegroundPixels)
        val emblem = plan.elements.first { it.enclosedHoles == 1 }
        assertTrue(emblem.pixelCount > 100)
        val isolated = plan.isolate(emblem.id).getOrThrow()
        assertEquals(0,isolated.argb[25*90+25])
        assertEquals(0,isolated.argb[60*90+30])
        assertTrue(isolated.argb[10*90+12] ushr 24 >= 180)
        assertEquals(emblem.pixelCount,isolated.argb.count { it ushr 24 >= 180 })
        assertFalse(plan.exportPermitted)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun twoTouchingFamiliesMustNotMergeIntoOneNeedleObject() {
        val original = image { x,y ->
            when {
                x in 12..36 && y in 18..46 -> orange
                x in 37..55 && y in 18..46 -> blue
                else -> 0
            }
        }
        val study = RasterThreadTextureStudy.inspect(original).getOrThrow()
        val plan = RasterAssistedElementPlanner.propose(original,study).getOrThrow()
        assertEquals(2,plan.totalComponents)
        for (part in plan.elements) {
            assertEquals(part.pixelCount,
                plan.isolate(part.id).getOrThrow().argb.count { it ushr 24 >= 180 })
        }
    }

    @Test fun singlePixelAccentsAreNotSilentlyDeleted() {
        val original = image { x,y ->
            when {
                x in 10..35 && y in 20..45 -> orange
                x == 70 && y == 71 -> white
                else -> 0
            }
        }
        val plan = RasterAssistedElementPlanner.propose(
            original,RasterThreadTextureStudy.inspect(original).getOrThrow()
        ).getOrThrow()
        assertEquals(2,plan.totalComponents)
        assertTrue(plan.elements.any { it.pixelCount == 1 })
        assertEquals(0,plan.undisplayedForegroundPixels)
        assertTrue(plan.summary().contains("Ainda não há pontos"))
    }

    @Test fun manySmallComponentsAreReportedWithoutClaimingFullReview() {
        val original = image(96,96) { x,y ->
            if (x in 8..87 && y in 8..87 && x % 4 == 0 && y % 4 == 0)
                white else 0
        }
        val plan = RasterAssistedElementPlanner.propose(
            original,RasterThreadTextureStudy.inspect(original).getOrThrow()
        ).getOrThrow()
        assertTrue(plan.totalComponents > 48)
        assertEquals(48,plan.elements.size)
        assertTrue(plan.undisplayedForegroundPixels > 0)
        assertEquals(plan.sourceForegroundPixels,
            plan.displayedForegroundPixels + plan.undisplayedForegroundPixels)
        assertTrue(plan.summary().contains("permanecem na imagem"))
        assertFalse(plan.exportPermitted)
    }

    @Test fun inconsistentFlatMaskMustFailClosed() {
        val original = image { x,y ->
            if (x in 10..40 && y in 16..45) orange else 0
        }
        val study = RasterThreadTextureStudy.inspect(original).getOrThrow()
        val counterfeit = study.flatPreview.argb.copyOf()
        counterfeit[23*90+20] = 0 // Erased original ink.
        val tampered = study.copy(
            flatPreview = RasterImagePixels(
                original.width, original.height, counterfeit,
                "tentativa de apagar letra"
            )
        )
        assertTrue(RasterAssistedElementPlanner.propose(original,tampered).isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun unsupportedOpaquePhotographCannotBecomeProposedThreadObjects() {
        val opaque = image { x,y ->
            if (x in 10..40 && y in 15..45) orange else 0xffffffff.toInt()
        }
        assertTrue(RasterThreadTextureStudy.inspect(opaque).isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun unknownElementCannotCreateSubstitutePixelMask() {
        val original = image { x,y ->
            if (x in 10..40 && y in 15..45) orange else 0
        }
        val plan = RasterAssistedElementPlanner.propose(original,
            RasterThreadTextureStudy.inspect(original).getOrThrow()
        ).getOrThrow()
        assertTrue(plan.isolate(-1).isFailure)
        assertTrue(plan.isolate(9999).isFailure)
        assertFalse(plan.exportPermitted)
    }
}
