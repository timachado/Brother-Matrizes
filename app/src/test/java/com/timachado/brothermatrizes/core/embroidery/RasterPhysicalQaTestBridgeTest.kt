package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** DEBUG-only physical QA handoff: these bytes are NOT production approval. */
class RasterPhysicalQaTestBridgeTest {
    private fun ring(): RasterImagePixels {
        val w = 12
        val h = 12
        return RasterImagePixels(w,h,IntArray(w*h) { i ->
            val x = i % w
            val y = i / w
            if (x in 2..9 && y in 2..9 &&
                !(x in 5..6 && y in 5..6)
            ) 0xffb35624.toInt() else 0
        },"ring real raster QA")
    }

    @Test fun physicalBenchSampleHasParsablePesButProductionQaRemainsBlocked() {
        val source=ring()
        val untouched=source.argb.copyOf()
        val qa=RasterQaRunningStitchDraft.create(
            source,80f,HoopProfile.H130X180
        ).getOrThrow().design
        assertEquals("QA_RUNNING_REVIEW",qa.format)
        assertTrue(qa.isExperimentalImageQa)
        assertTrue(MatrixConverter.convert(qa,"PES").isFailure)
        assertTrue(MatrixConverter.convert(
            qa.copy(format="PES"),"PES"
        ).isFailure)

        val sample=RasterPhysicalQaTestBridge.createOutlinePes(
            source,80f,HoopProfile.H130X180
        ).getOrThrow()
        assertEquals("PES",sample.format)
        assertTrue(sample.fileName.endsWith(".pes"))
        assertTrue(sample.bytes.size>=512)
        assertTrue(sample.bytes.copyOfRange(0,8)
            .toString(Charsets.US_ASCII).startsWith("#PES"))
        assertTrue(source.argb.contentEquals(untouched))
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun physicalBenchExportIsLimitedToTransparentInkAndSmallSize() {
        val image=ring()
        assertTrue(RasterPhysicalQaTestBridge.createOutlinePes(
            image,120f,HoopProfile.H130X180
        ).isFailure)
        val opaque=image.copy(argb=IntArray(12*12) {
            0xff6d723a.toInt()
        })
        assertTrue(RasterPhysicalQaTestBridge.createOutlinePes(
            opaque,80f,HoopProfile.H130X180
        ).isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }
}
