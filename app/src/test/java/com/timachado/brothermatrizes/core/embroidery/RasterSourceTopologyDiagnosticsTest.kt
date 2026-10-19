package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic QA source pixels, NOT a substitute for the original PANOBIANCO. */
class RasterSourceTopologyDiagnosticsTest {
    private val w=96
    private val h=80
    private val orange=0xffce7423.toInt()
    private val white=0xffeeeeee.toInt()

    private fun artwork() = IntArray(w*h).also { pixels ->
        for (y in 20..53) for (x in 12..32) pixels[y*w+x]=orange
        for (y in 20..53) for (x in 50..70) pixels[y*w+x]=white
    }

    private fun image(pixels:IntArray) =
        RasterImagePixels(w,h,pixels,"duas letras reais simuladas em QA")

    @Test fun twoIndependentOpaqueLettersHaveTwoCoreIslands() {
        val source=image(artwork())
        val untouched=source.argb.copyOf()
        val report=RasterSourceTopologyDiagnostics.inspect(source)
            .getOrThrow()
        assertEquals(2,report.opaqueCores)
        assertEquals(2,report.thresholdIslands)
        assertEquals(0,report.highAlphaBridges)
        assertEquals(0,report.unanchoredThresholdIslands)
        assertEquals(0,report.strongAlpha)
        assertTrue(report.highAlphaTopologySafe)
        assertEquals(w*h,report.transparent+report.weakAlpha+
            report.strongAlpha+report.fullyOpaque)
        assertTrue(source.argb.contentEquals(untouched))
        assertTrue(report.summary().contains("fingerprint="))
        assertFalse(report.exportPermitted)
    }

    @Test fun highAlphaBridgeBetweenTwoOpaqueLettersIsRejected() {
        val pixels=artwork()
        for (x in 33..49) pixels[35*w+x]=0xdcce7423.toInt()
        val source=image(pixels)
        val unchanged=source.argb.copyOf()
        val report=RasterSourceTopologyDiagnostics.inspect(source)
            .getOrThrow()
        assertEquals(2,report.opaqueCores)
        assertEquals(1,report.thresholdIslands)
        assertEquals(1,report.highAlphaBridges)
        assertEquals(17,report.strongAlpha)
        assertFalse(report.highAlphaTopologySafe)
        val study=RasterThreadTextureStudy.inspect(source).getOrThrow()
        val checked=RasterTextureReconstructionGate.prepare(source,study)
        assertTrue(checked.isFailure)
        assertTrue(checked.exceptionOrNull()?.message.orEmpty()
            .contains("unir letras"))
        assertTrue(source.argb.contentEquals(unchanged))
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun tinyStrongAlphaIslandWithoutOpaqueAnchorCannotDisappear() {
        val pixels=artwork()
        for (y in 66..69) for (x in 82..85) {
            pixels[y*w+x]=0xdcce7423.toInt()
        }
        val source=image(pixels)
        val report=RasterSourceTopologyDiagnostics.inspect(source)
            .getOrThrow()
        assertEquals(2,report.opaqueCores)
        assertEquals(3,report.thresholdIslands)
        assertEquals(1,report.unanchoredThresholdIslands)
        assertFalse(report.highAlphaTopologySafe)
        assertTrue(RasterTextureReconstructionGate.prepare(
            source,RasterThreadTextureStudy.inspect(source).getOrThrow()
        ).isFailure)
    }

    @Test fun ordinaryAntialiasAroundOneOpaqueCoreIsNotAFalseBridge() {
        val pixels=artwork()
        for (y in 19..54) for(x in 11..33) {
            val i=y*w+x
            if(pixels[i]==0) pixels[i]=0xdcb46b23.toInt()
        }
        val source=image(pixels)
        val report=RasterSourceTopologyDiagnostics.inspect(source)
            .getOrThrow()
        assertEquals(2,report.opaqueCores)
        assertEquals(2,report.thresholdIslands)
        assertEquals(0,report.highAlphaBridges)
        assertTrue(report.strongAlpha>0)
        assertTrue(report.highAlphaTopologySafe)
        val study=RasterThreadTextureStudy.inspect(source).getOrThrow()
        assertTrue(RasterTextureReconstructionGate.prepare(source,study).isSuccess)
    }

    @Test fun noRawImagePixelsOrStitchesAppearInDiagnosticReport() {
        val source=image(artwork())
        val diagnostic=RasterSourceTopologyDiagnostics.inspect(source)
            .getOrThrow()
        val summary=diagnostic.summary()
        assertTrue(summary.contains("NÃO é matriz nem PES"))
        assertTrue(diagnostic.reviewRequired)
        assertFalse(diagnostic.exportPermitted)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }
}
