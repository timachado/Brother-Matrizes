package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumImageQaDiagnosticsTest {
    private fun design(
        points:List<EmbroideryPoint>,
        stitches:Int=points.count { it.command==StitchCommand.STITCH },
        jumps:Int=points.count { it.command==StitchCommand.JUMP },
        changes:Int=points.count { it.command==StitchCommand.COLOR_CHANGE }
    )=EmbroideryDesign(
        fileName="TESTE_SEM_PES.qa",
        format="QA_IMAGE_VISUAL",
        label="QA visual e não físico",
        points=points,
        bounds=EmbroideryBounds(-400,400,-400,400),
        stitchCount=stitches,
        jumpCount=jumps,
        colorChanges=changes,
        endFound=true,
        sourceBytes=ByteArray(0),
        threadColors=listOf(0xe6be70)
    )
    private fun point(
        x:Int,y:Int,command:StitchCommand
    )=EmbroideryPoint(x,y,command,0)

    @Test fun catchesZeroLengthShortLongAndUntrimmedTravel() {
        val d=design(listOf(
            point(0,0,StitchCommand.JUMP),
            point(0,0,StitchCommand.STITCH),
            point(1,0,StitchCommand.STITCH),
            point(101,0,StitchCommand.STITCH),
            point(250,0,StitchCommand.JUMP),
            point(252,0,StitchCommand.STITCH),
            point(252,0,StitchCommand.END)
        ))
        val r=PremiumImageQaDiagnostics.inspect(d)
        assertTrue(r.countConsistent)
        assertEquals(1,r.zeroLengthStitches)
        assertEquals(2,r.veryShortStitches)
        assertEquals(1,r.longStitches)
        assertEquals(1,r.longUntrimmedJumps)
        assertTrue(r.warnings.any { it.contains("SEM liberação") })
        assertTrue(MatrixConverter.convert(d,"PES").isFailure)
    }

    @Test fun detectsSewnTravelThroughOriginalTransparentCounter() {
        val width=80
        val raster=IntArray(width*width) {i->
            val x=i%width
            val y=i/width
            if(y in 10..65 &&
                (x in 10..18 || x in 40..50))0xffe6be70.toInt() else 0
        }
        // 80 mm design, 80 pixels => 10 embroidery units per pixel.
        val preview=design(listOf(
            point(-250,0,StitchCommand.JUMP),
            point(50,0,StitchCommand.STITCH),
            point(50,0,StitchCommand.END)
        ))
        val r=PremiumImageQaDiagnostics.inspect(
            preview,width,width,raster,80f
        )
        assertEquals(1,r.transparentCrossings)
        assertEquals(1,r.longStitches)
        assertTrue(r.warnings.any { it.contains("vazados") })
    }

    @Test fun trimSeparatesLongJumpFromDangerousUntrimmedTravel() {
        val preview=design(listOf(
            point(-250,0,StitchCommand.JUMP),
            point(-230,0,StitchCommand.STITCH),
            point(-230,0,StitchCommand.TRIM),
            point(230,0,StitchCommand.JUMP),
            point(235,0,StitchCommand.STITCH),
            point(235,0,StitchCommand.END)
        ))
        val r=PremiumImageQaDiagnostics.inspect(preview)
        assertEquals(0,r.longUntrimmedJumps)
        assertEquals(1,r.trims)
        assertTrue(r.countConsistent)
        assertTrue(r.warnings.any { it.contains("SEM liberação") })
    }

    @Test fun renamingQaFormatToPesCannotBypassPhysicalExportLock() {
        val preview=design(listOf(
            point(0,0,StitchCommand.JUMP),
            point(10,0,StitchCommand.STITCH),
            point(10,0,StitchCommand.END)
        ))
        assertTrue(MatrixConverter.convert(preview,"PES").isFailure)
        assertTrue(MatrixConverter.convert(
            preview.copy(format="PES"),"PES"
        ).isFailure)
        assertTrue(MatrixConverter.convert(
            preview.copy(format="DST"),"DST"
        ).isFailure)
        assertTrue(MatrixConverter.convert(
            preview.copy(format="JEF"),"JEF"
        ).isFailure)
    }

    @Test fun sourceFreePreviewDoesNotClaimOpaqueHoleAudit() {
        val preview=design(listOf(
            point(0,0,StitchCommand.JUMP),
            point(10,0,StitchCommand.STITCH),
            point(10,0,StitchCommand.END)
        ),stitches=7)
        val r=PremiumImageQaDiagnostics.inspect(preview)
        assertFalse(r.countConsistent)
        assertEquals(0,r.transparentCrossings)
        assertTrue(r.warnings.any { it.contains("inconsistente") })
        assertTrue(MatrixConverter.convert(preview,"DST").isFailure)
    }
}
