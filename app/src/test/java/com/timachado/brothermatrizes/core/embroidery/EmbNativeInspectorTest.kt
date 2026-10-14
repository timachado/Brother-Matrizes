package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.*
import org.junit.Test

class EmbNativeInspectorTest {
    @Test fun fileNameAcceptsTrueEmbAndAndroidRenamedDoc() {
        assertTrue(EmbNativeInspector.looksLikeEmbName("quiltin puma.EMB"))
        assertTrue(EmbNativeInspector.looksLikeEmbName("coração. EMB .doc"))
        assertFalse(EmbNativeInspector.looksLikeEmbName("relatorio.doc"))
        assertFalse(EmbNativeInspector.looksLikeEmbName("matriz.pes"))
    }

    @Test fun extensionByItselfDoesNotCreateFabricatedStitches() {
        assertNull(EmbNativeInspector.inspect("falso.emb", ByteArray(512) { 7 }))
    }

    @Test fun cfbSignatureIsDetectedButCorruptedDataIsRejected() {
        val validPrefix = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()
        )
        assertTrue(EmbNativeInspector.hasCfbSignature(validPrefix))
        val forged = validPrefix + ByteArray(512)
        assertTrue(runCatching {
            EmbNativeInspector.inspect("falso.emb", forged)
        }.isFailure)
    }

    @Test fun emptyAndOversizedFilesAreRejected() {
        assertTrue(runCatching {
            EmbNativeInspector.inspect("vazio.emb", byteArrayOf())
        }.isFailure)
        assertTrue(runCatching {
            EmbNativeInspector.inspect("grande.emb",
                ByteArray(EmbNativeInspector.MAX_EMB_BYTES + 1))
        }.isFailure)
    }

    @Test fun previewMetadataIsNotAStitchSequence() {
        val document = EmbNativePreview(
            fileName = "amostra.emb",
            sha256 = "a".repeat(64),
            declaredStitches = 39343,
            declaredColors = 4,
            declaredObjects = 28,
            declaredTrims = 27,
            declaredColorChanges = 4,
            declaredStops = 5,
            declaredThreadSteps = emptyList(),
            imageJpeg = null
        )
        assertFalse(document.isStitchDataDecoded)
        assertEquals(39343, document.declaredStitches)
    }
}
