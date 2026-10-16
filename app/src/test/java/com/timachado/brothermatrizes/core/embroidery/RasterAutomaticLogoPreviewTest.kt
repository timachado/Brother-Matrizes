package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class RasterAutomaticLogoPreviewTest {
    private fun image(
        width: Int = 160,
        height: Int = 100,
        pixels: (Int,Int) -> Int
    ): RasterImagePixels {
        val argb = IntArray(width*height)
        for (y in 0 until height) for (x in 0 until width) {
            argb[y*width+x] = pixels(x,y)
        }
        return RasterImagePixels(width,height,argb,"teste composto sem PES")
    }

    @Test fun separatedSameColorStrokesNeverSewTheEmptySpace() {
        val navy = 0xff143550.toInt()
        val art = image { x,y ->
            when {
                x in 15..95 && y in 23..31 -> navy
                x in 20..100 && y in 64..72 -> navy
                else -> 0xffffffff.toInt()
            }
        }
        val result = RasterAutomaticLogoPreview.create(
            art, 90f, HoopProfile.H130X180
        ).getOrThrow()
        assertEquals("QA_AUTO_STITCH",result.format)
        assertEquals(0,result.colorChanges)
        assertTrue(result.stitchCount > 20)
        assertTrue(result.jumpCount >= 2)
        assertTrue(result.points.count { it.command == StitchCommand.TRIM } >= 1)
        assertEquals(1,result.points.count { it.command == StitchCommand.END })
        assertTrue(result.sourceBytes.isEmpty())
        assertTrue(result.isModified)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
        assertEquals(1, result.threadColors.size)
    }

    @Test fun distinctColorLettersEmitTrueChangesWithNewColorIndex() {
        val navy = 0xff143550.toInt()
        val gold = 0xffc78c28.toInt()
        val art = image { x,y ->
            when {
                x in 15..95 && y in 23..31 -> navy
                x in 24..102 && y in 64..72 -> gold
                else -> 0xffffffff.toInt()
            }
        }
        val design = RasterAutomaticLogoPreview.create(
            art,90f,HoopProfile.H130X180
        ).getOrThrow()
        assertEquals(1,design.colorChanges)
        assertEquals(2,design.threadColors.size)
        assertEquals(1,design.points.count {
            it.command == StitchCommand.COLOR_CHANGE
        })
        assertTrue(design.points.any {
            it.command == StitchCommand.STITCH && it.colorIndex == 0
        })
        assertTrue(design.points.any {
            it.command == StitchCommand.STITCH && it.colorIndex == 1
        })
        assertTrue(design.points.count {
            it.command == StitchCommand.TRIM
        } >= 1)
        val end = design.points.last()
        assertEquals(StitchCommand.END,end.command)
        assertEquals(1,end.colorIndex)
        assertTrue(design.sourceBytes.isEmpty())
        val maxStep = design.points.zipWithNext().filter { (_, b) ->
            b.command == StitchCommand.STITCH
        }.maxOf { (a,b) ->
            hypot((a.xUnits-b.xUnits).toDouble(),
                (a.yUnits-b.yUnits).toDouble())
        }
        assertTrue(maxStep <= 78.0)
    }

    @Test fun unsupportedThirdShapeRefusesEntireLogoWithoutPartialDesign() {
        val navy = 0xff143550.toInt()
        val art = image { x,y ->
            when {
                x in 15..95 && y in 23..31 -> navy
                // Irregular wide block with a counter is not safely
                // supported by constrained QA Tatami or Satin.
                x in 111..145 && y in 43..77 &&
                    !(x in 120..134 && y in 52..67) -> navy
                else -> 0xffffffff.toInt()
            }
        }
        val result = RasterAutomaticLogoPreview.create(
            art,90f,HoopProfile.H130X180
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Elemento"))
    }

    @Test fun hoopDimensionsCannotBeOverriddenByLogo() {
        val navy = 0xff143550.toInt()
        val image = image { x,y ->
            if (x in 15..95 && y in 23..31) navy else 0xffffffff.toInt()
        }
        assertTrue(
            RasterAutomaticLogoPreview.create(image, 180f, HoopProfile.H100X100)
                .isFailure
        )
    }
}
