package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterWholeArtworkFidelityTest {
    private val blue=0xff153853.toInt()
    private val gold=0xffc99737.toInt()
    private val white=0xffffffff.toInt()

    private fun artwork(): RasterImagePixels {
        val w=160;val h=100
        val pixels=IntArray(w*h) { index ->
            val x=index%w;val y=index/w
            when {
                x in 14..95 && y in 24..32 -> blue
                x in 19..103 && y in 63..71 -> gold
                else -> white
            }
        }
        return RasterImagePixels(w,h,pixels,"qa full-art lettering")
    }

    @Test fun validTwoColorDesignCoversBothActualInkRegions() {
        val source=artwork()
        val design=RasterAutomaticLogoPreview.create(
            source,90f,HoopProfile.H130X180
        ).getOrThrow()
        val fidelity=RasterWholeArtworkFidelity.inspect(
            source,design,90f
        ).getOrThrow()
        assertTrue("coverage=${fidelity.coverage}",fidelity.coverage>.65)
        assertTrue(
            "min-color=${fidelity.significantColorMinimumCoverage}",
            fidelity.significantColorMinimumCoverage>.55
        )
        assertTrue("outside=${fidelity.offInkFraction}",
            fidelity.offInkFraction<.18)
        assertTrue("wrongColor=${fidelity.wrongColorFraction}",
            fidelity.wrongColorFraction<.12)
        assertEquals(2,design.threadColors.size)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun swappingThreadPaletteIsDetectedDespiteSameGeometry() {
        val source=artwork()
        val original=RasterAutomaticLogoPreview.create(
            source,90f,HoopProfile.H130X180
        ).getOrThrow()
        val wrong=original.copy(
            threadColors=original.threadColors.reversed()
        )
        val fidelity=RasterWholeArtworkFidelity.inspect(
            source,wrong,90f
        ).getOrThrow()
        assertTrue("wrongColor=${fidelity.wrongColorFraction}",
            fidelity.wrongColorFraction>.50)
        assertTrue("coverage=${fidelity.coverage}",fidelity.coverage<.20)
    }

    @Test fun aMissingSecondColorIsVisibleInFullArtworkCoverage() {
        val source=artwork()
        val original=RasterAutomaticLogoPreview.create(
            source,90f,HoopProfile.H130X180
        ).getOrThrow()
        val firstOnly=original.points.filter {
            it.colorIndex==0 && it.command != StitchCommand.END
        }
        val last=firstOnly.last()
        val incomplete=original.copy(
            points=firstOnly+last.copy(command=StitchCommand.END),
            stitchCount=firstOnly.count { it.command==StitchCommand.STITCH },
            jumpCount=firstOnly.count { it.command==StitchCommand.JUMP },
            colorChanges=0
        )
        val fidelity=RasterWholeArtworkFidelity.inspect(
            source,incomplete,90f
        ).getOrThrow()
        assertTrue(
            "missing color should reduce min coverage",
            fidelity.significantColorMinimumCoverage<.15
        )
    }

    @Test fun importedPESDoesNotPassTheQAOnlyArtworkGate() {
        val source=artwork()
        val design=RasterAutomaticLogoPreview.create(
            source,90f,HoopProfile.H130X180
        ).getOrThrow()
        assertTrue(RasterWholeArtworkFidelity.inspect(
            source,design.copy(format="PES"),90f
        ).isFailure)
    }
}
