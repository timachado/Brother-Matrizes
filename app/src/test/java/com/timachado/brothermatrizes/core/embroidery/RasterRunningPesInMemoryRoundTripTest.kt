package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Non-distributable software-only format experiment.
 *
 * A released PES must NEVER be created from a QA_* source by the app.
 * These tests deliberately make a transient, in-memory PES-format fixture
 * after first proving the production converter rejects the QA original.
 * No application API, source bytes or export permission is introduced.
 *
 * The application's own reader is not an external decoder, and a decoded
 * file is not proof of safe needle/fabric/machine behavior.
 */
class RasterRunningPesInMemoryRoundTripTest {
    private fun image(letterShape: Boolean): RasterImagePixels {
        val w = 12
        val h = 12
        val argb = IntArray(w * h)
        for (y in 2..9) for (x in 2..9) {
            val occupied = if (letterShape) {
                x in 2..4 || (y in 6..9 && x in 2..9)
            } else {
                !(x in 5..6 && y in 5..6)
            }
            if (occupied) argb[y*w+x] = 0xffb35624.toInt()
        }
        return RasterImagePixels(w,h,argb,
            if (letterShape) "synthetic L" else "synthetic O with counter")
    }

    private fun verifyOnlyInMemory(source: RasterImagePixels, loops: Int) {
        val snapshot = source.argb.copyOf()
        val draft = RasterQaRunningStitchDraft.create(
            source, 120f, HoopProfile.H130X180, maxWaypointSpacingMm=2f
        ).getOrThrow()
        val qa = draft.design
        assertEquals(loops,draft.outlineCount)
        assertFalse(draft.exportPermitted)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
        assertTrue(qa.sourceBytes.isEmpty())
        assertTrue("O conversor público deve recusar o formato experimental.",
            MatrixConverter.convert(qa,"PES","proibido").isFailure)

        // Only a disposable test-local representation: the application
        // never exposes this promotion and never receives its bytes.
        val ephemeral = qa.copy(
            format="PES",
            fileName="APENAS-JUNIT-NAO-ENVIAR.pes",
            label="PES in-memory readback; never a machine file"
        )
        val encoded = MatrixConverter.convert(
            ephemeral,"PES","in-memory-only"
        ).getOrThrow()
        assertTrue(encoded.bytes.size >= 512)
        val readback = PesReadbackEvidence.inspect(
            ephemeral,encoded.fileName,encoded.bytes
        ).getOrThrow()
        assertTrue(readback.geometryPreserved)
        assertTrue(readback.stitchOrderPreserved)
        assertEquals(qa.stitchCount,readback.originalStitches)
        assertTrue(readback.reopenedStitches >= qa.stitchCount)
        assertFalse(readback.externalDecoderVerified)
        assertFalse(readback.machineCertified)
        assertFalse(readback.authorizesImagePesExport)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
        assertTrue(source.argb.contentEquals(snapshot))
    }

    @Test fun simpleRingWithTransparentCounterHasOrderedPesCodecReadback() {
        verifyOnlyInMemory(image(false),2)
    }

    @Test fun concaveLShapeHasOrderedPesCodecReadbackWithoutRotation() {
        verifyOnlyInMemory(image(true),1)
    }
    @Test fun twoColorFlatArtRetainsSewnOrderThroughPesCodec() {
        val width=160
        val height=100
        val pixels=IntArray(width*height) { i ->
            val x=i%width
            val y=i/width
            when {
                x in 15..95 && y in 23..31 -> 0xffda761a.toInt()
                x in 20..100 && y in 64..72 -> 0xff193e6a.toInt()
                else -> 0
            }
        }
        val original=RasterImagePixels(width,height,pixels,"two-color QA strokes")
        val before=pixels.copyOf()
        val qa=RasterFlatColorStitchCandidate.create(
            original,90f,HoopProfile.H130X180
        ).getOrThrow().design
        assertEquals("QA_AUTO_STITCH",qa.format)
        assertEquals(2,qa.threadColors.size)
        assertEquals(1,qa.colorChanges)
        assertTrue(MatrixConverter.convert(qa,"PES").isFailure)
        val fixture=qa.copy(
            format="PES",
            fileName="MEMORY-ONLY-MULTICOLOR-NOT-RELEASED.pes"
        )
        val encoded=MatrixConverter.convert(
            fixture,"PES","junit-only"
        ).getOrThrow()
        val review=PesReadbackEvidence.inspect(
            fixture,encoded.fileName,encoded.bytes
        ).getOrThrow()
        assertTrue(review.geometryPreserved)
        assertTrue(review.stitchOrderPreserved)
        assertFalse(review.machineCertified)
        assertFalse(review.authorizesImagePesExport)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
        assertTrue(pixels.contentEquals(before))
    }


    @Test fun completeMixedSatinTatamiArtworkCodecRoundTripIsOnlyInMemory() {
        val w=180
        val h=130
        val gold=0xffcb943c.toInt()
        val navy=0xff1c405e.toInt()
        val white=0xffffffff.toInt()
        val source=RasterImagePixels(w,h,IntArray(w*h) { i ->
            val x=i%w
            val y=i/w
            when {
                x in 40..129 && y in 30..97 &&
                    !(x in 70..99 && y in 52..75) -> gold
                x in 20..111 && y in 10..19 -> navy
                else -> white
            }
        },"mixed artwork source")
        // This artifact uses the actual per-object shape-aware generator,
        // including a transparent COUNTER in the gold letter-shaped ring.
        val qa=RasterAutomaticLogoPreview.create(
            source,90f,HoopProfile.H130X180
        ).getOrThrow()
        assertEquals("QA_AUTO_STITCH",qa.format)
        assertTrue(qa.stitchCount>100)
        assertTrue(qa.jumpCount>1)
        assertEquals(1,qa.colorChanges)
        assertTrue(qa.points.any { it.command==StitchCommand.TRIM })
        RasterThreadSequenceAudit.inspect(qa).getOrThrow()
        RasterThreadDensityPreflight.inspect(qa).getOrThrow()
        assertTrue(MatrixConverter.convert(qa,"PES").isFailure)
        // Never exposed as an app workflow: codec investigation in JUnit.
        val ephemeral=qa.copy(
            fileName="MEMORY-ONLY-MIXED-DESIGN.pes",format="PES"
        )
        val bytes=MatrixConverter.convert(ephemeral,"PES","junit-review")
            .getOrThrow()
        val report=PesReadbackEvidence.inspect(
            ephemeral,bytes.fileName,bytes.bytes
        ).getOrThrow()
        assertEquals(qa.stitchCount,report.originalStitches)
        assertTrue(report.geometryPreserved)
        assertTrue(report.stitchOrderPreserved)
        assertFalse(report.externalDecoderVerified)
        assertFalse(report.machineCertified)
        assertFalse(report.authorizesImagePesExport)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }


}
