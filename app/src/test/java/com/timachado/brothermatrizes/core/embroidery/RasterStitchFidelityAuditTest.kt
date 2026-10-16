package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class RasterStitchFidelityAuditTest {
    private fun art(): RasterImagePixels {
        val w = 150
        val h = 100
        val argb = IntArray(w*h) { i ->
            val x = i % w
            val y = i / w
            if (x in 20..118 && y in 32..42) 0xff143550.toInt()
            else 0xffffffff.toInt()
        }
        return RasterImagePixels(w, h, argb, "fidelidade de pontos")
    }

    @Test fun validSatinPathCoversReferenceBand() {
        val image = art()
        val component = RasterSatinObjectEditor.detect(image).getOrThrow().single()
        val rails = RasterAutoSatinRails.propose(image, component, 85f).getOrThrow()
        val design = RasterSatinObjectEditor.preview(
            image, component, listOf(rails), 85f, HoopProfile.H130X180,
            minimumCoverage = .90f
        ).getOrThrow()
        val measured = RasterStitchFidelityAudit.compare(
            image, component, design, 85f
        ).getOrThrow()
        assertTrue("coverage=${measured.coverage}", measured.coverage >= .78)
        assertTrue("outside=${measured.offInkFraction}",
            measured.offInkFraction <= .10)
    }

    @Test fun displacedStitchesAreDetectedAgainstOriginalImage() {
        val image = art()
        val component = RasterSatinObjectEditor.detect(image).getOrThrow().single()
        val rails = RasterAutoSatinRails.propose(image, component, 85f).getOrThrow()
        val correct = RasterSatinObjectEditor.preview(
            image, component, listOf(rails), 85f, HoopProfile.H130X180
        ).getOrThrow()
        // Shift points by more than the bar thickness: the design still has
        // valid STITCH commands but no longer resembles the input bitmap.
        val bad = correct.copy(points = correct.points.map { p ->
            p.copy(yUnits = p.yUnits + 170)
        })
        val measured = RasterStitchFidelityAudit.compare(
            image, component, bad, 85f
        ).getOrThrow()
        assertTrue(measured.coverage < .30)
        assertTrue(measured.offInkFraction > .75)
    }

    @Test fun jpegIsNotSilentlyReinterpretedAsPES() {
        val image = art()
        val component = RasterSatinObjectEditor.detect(image).getOrThrow().single()
        val bogus = EmbroideryDesign(
            fileName="bogus.pes", format="PES", label=null,
            points = listOf(
                EmbroideryPoint(0,0,StitchCommand.JUMP,0),
                EmbroideryPoint(20,20,StitchCommand.STITCH,0)
            ),
            bounds=EmbroideryBounds(0,20,0,20),
            stitchCount=1,jumpCount=1,colorChanges=0,
            endFound=true,sourceBytes=byteArrayOf(1)
        )
        assertTrue(RasterStitchFidelityAudit.compare(
            image,component,bogus,85f
        ).isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }
}
