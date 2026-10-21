package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * User-facing priority: seeing a useful stitch SEQUENCE in the official
 * SimulatorScreen must not require successful machine PES digitization.
 */
class PremiumImageVisualStitchPreviewTest {
    private fun textLikeImage(): IntArray {
        val w=156
        val h=156
        return IntArray(w*h) { index ->
            val x=index%w
            val y=index/w
            when {
                // White title-like strokes with eight-plus islands.
                y in 85..94 && x in 10..146 &&
                    (x%14 in 2..10) -> 0xffeeeeee.toInt()
                // Orange segmented brand icon, with a transparent counter.
                y in 25..76 && x in 51..104 &&
                    !(y in 40..62 && x in 65..90) ->
                        0xffff9a23.toInt()
                else -> 0
            }
        }
    }

    @Test fun complexTwoColorLogoGetsSimulatorCommandsAndNeverPes() {
        val source=textLikeImage()
        val before=source.copyOf()
        val result=PremiumImageVisualStitchPreview.create(
            156,156,source,60f,HoopProfile.H130X180
        ).getOrThrow()
        assertEquals("QA_IMAGE_VISUAL",result.format)
        assertTrue(result.stitchCount>50)
        assertTrue(result.jumpCount>10)
        assertTrue(result.threadColors.size>=2)
        assertTrue(result.colorChanges>=1)
        assertTrue(result.points.any { it.command==StitchCommand.JUMP })
        assertTrue(result.points.any { it.command==StitchCommand.STITCH })
        assertEquals(StitchCommand.END,result.points.last().command)
        assertEquals(result.stitchCount,result.points.count {
            it.command==StitchCommand.STITCH
        })
        assertTrue(HoopValidator.validate(result,HoopProfile.H130X180).fits)
        assertTrue(source.contentEquals(before))
        assertTrue(MatrixConverter.convert(result,"PES").isFailure)
        assertTrue(MatrixConverter.convert(result,"DST").isFailure)
        assertTrue(MatrixConverter.convert(result,"JEF").isFailure)
        assertTrue(PremiumImagePhysicalLab.pesForBench(result).isFailure)
    }

    @Test fun diagonalOnlyTouchDoesNotBecomeContinuousSewnBridge() {
        val w=64
        val h=64
        val image=IntArray(w*h) { index ->
            val x=index%w
            val y=index/w
            if ((x in 8..20 && y in 8..20) ||
                (x in 21..34 && y in 21..34))
                    0xffd3a753.toInt() else 0
        }
        val preview=PremiumImageVisualStitchPreview.create(
            w,h,image,55f,HoopProfile.H100X100
        ).getOrThrow()
        // Two squares touching at one diagonal pixel vertex must remain
        // two independent stitched outlines, never a bridge.
        assertEquals(2,preview.jumpCount)
        assertTrue(preview.points.any { it.command==StitchCommand.TRIM })
        assertTrue(preview.stitchCount>10)
        assertTrue(preview.points.any { it.command==StitchCommand.JUMP })
        assertTrue(MatrixConverter.convert(preview,"PES").isFailure)
    }

    @Test fun opaquePhotoOrHoopOverflowStillClearlyRejected() {
        val opaque=IntArray(64*64) { 0xffeabc18.toInt() }
        val photo=PremiumImageVisualStitchPreview.create(
            64,64,opaque,60f,HoopProfile.H100X100
        )
        assertTrue(photo.isFailure)
        assertTrue(photo.exceptionOrNull()?.message.orEmpty()
            .contains("transparente"))
        assertTrue(PremiumImageVisualStitchPreview.create(
            156,156,textLikeImage(),100f,HoopProfile.H100X100
        ).isFailure)
        assertFalse(MatrixConverter.supportedFormats.contains("QA_IMAGE_VISUAL"))
    }
    @Test fun transparentCounterIsNotFilledByHorizontalStitchScan() {
        val w=80
        val h=80
        val source=IntArray(w*h) { i ->
            val x=i%w
            val y=i/w
            if(x in 10..70 && y in 10..70 &&
                !(x in 26..54 && y in 26..54)
            ) 0xffffa12c.toInt() else 0
        }
        val draft=PremiumImageVisualStitchPreview.create(
            w,h,source,60f,HoopProfile.H100X100
        ).getOrThrow()
        assertEquals(2,draft.jumpCount)
        assertTrue(draft.points.any { it.command==StitchCommand.TRIM })
        // The open middle of the logo must remain transparent.
        assertFalse(draft.points.any { p ->
            p.command==StitchCommand.STITCH &&
                p.xUnits in -75..75 && p.yUnits in -75..75
        })
        // No simulated raster draft, even one that looks plausible, gets PES.
        assertTrue(MatrixConverter.convert(draft,"PES").isFailure)
    }


}
