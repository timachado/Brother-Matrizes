package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterTextureReconstructionGateTest {
    private val w = 100
    private val h = 80

    private fun texturedLogo(): RasterImagePixels =
        RasterImagePixels(w,h,IntArray(w*h) { i ->
            val x=i%w
            val y=i/w
            when {
                x in 10..36 && y in 15..52 ->
                    0xff000000.toInt() or
                        (0xbc shl 16) or
                        ((80+(x+y)%12) shl 8) or 0x17
                x in 58..85 && y in 17..54 &&
                    !(x in 68..77 && y in 29..42) ->
                    valWhite((x+y)%10)
                else -> 0
            }
        },"Logo com textura em laranja e branco")

    private fun valWhite(variation:Int):Int {
        val c=230+variation
        return 0xff000000.toInt() or (c shl 16) or (c shl 8) or c
    }

    @Test fun texturedTwoColorLogoBecomesReviewableQaSourceWithoutChangingPhoto() {
        val source=texturedLogo()
        val original=source.argb.copyOf()
        val study=RasterThreadTextureStudy.inspect(source).getOrThrow()
        val candidate=RasterTextureReconstructionGate.prepare(
            source,study
        ).getOrThrow()
        assertTrue(candidate.reviewRequired)
        assertFalse(candidate.exportPermitted)
        assertEquals(2,candidate.threadColorFamilies)
        assertEquals(2,candidate.disconnectedComponents)
        assertEquals(
            source.argb.count {it ushr 24 >= 180},
            candidate.sourceOpaquePixels
        )
        assertEquals(
            candidate.sourceOpaquePixels,
            candidate.flatReference.argb.count {it ushr 24 >= 180}
        )
        assertTrue(source.argb.contentEquals(original))
        assertTrue(candidate.flatReference.argb
            .contentEquals(study.flatPreview.argb))
        assertTrue(candidate.message().contains("Nenhuma agulhada"))
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun allOriginalLetterCountersAndBackgroundRemainTransparent() {
        val source=texturedLogo()
        val candidate=RasterTextureReconstructionGate.prepare(
            source,RasterThreadTextureStudy.inspect(source).getOrThrow()
        ).getOrThrow()
        val flat=candidate.flatReference
        assertEquals(0,flat.argb[35*w+72]) // hole in the white character
        assertEquals(0,flat.argb[5*w+5]) // background
        for(i in flat.argb.indices) {
            assertEquals(
                source.argb[i] ushr 24 >= 180,
                flat.argb[i] ushr 24 >= 180
            )
        }
    }

    @Test fun staleOrForgedTextureStudyIsRejected() {
        val source=texturedLogo()
        val study=RasterThreadTextureStudy.inspect(source).getOrThrow()
        val altered=source.argb.copyOf()
        // Same broad orange family and silhouette; different SOURCE PIXEL.
        altered[25*w+20] = altered[25*w+20] xor 0x00000100
        val newSource=RasterImagePixels(w,h,altered,"foto modificada")
        assertTrue(RasterTextureReconstructionGate.prepare(
            newSource,study
        ).isFailure)
        assertTrue(RasterTextureReconstructionGate.prepare(
            source,study.copy(sourceArgbFingerprint=0L)
        ).isFailure)
    }

    @Test fun tinyDisconnectedAccentMustNotDisappearWhenFlattening() {
        val original=texturedLogo()
        val changed=original.argb.copyOf()
        changed[64*w+43]=0xffbf5917.toInt()
        val source=RasterImagePixels(w,h,changed,"acento isolado")
        val study=RasterThreadTextureStudy.inspect(source).getOrThrow()
        val trial=RasterTextureReconstructionGate.prepare(source,study)
        assertTrue(trial.isFailure)
        assertTrue(trial.exceptionOrNull()?.message.orEmpty().isNotEmpty())
    }

    @Test fun ambiguousSemiTransparentOutlinesCannotBecomeApprovedStitches() {
        val original=texturedLogo()
        val changed=original.argb.copyOf()
        for(y in 57..61) for(x in 10..70) {
            changed[y*w+x] = 0x77000000 or 0x00d28a19
        }
        val source=RasterImagePixels(w,h,changed,"bordas com alfa ambíguo")
        val report=RasterThreadTextureStudy.inspect(source).getOrThrow()
        assertTrue(RasterTextureReconstructionGate.prepare(
            source,report
        ).isFailure)
    }

    @Test fun photoWithoutTransparencyStillRequiresSeparateBackgroundRemoval() {
        val source=RasterImagePixels(w,h,IntArray(w*h) {0xffb67b25.toInt()},
            "foto opaca")
        val study=RasterThreadTextureStudy.inspect(source)
        assertTrue(study.isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }
}
