package com.timachado.brothermatrizes.core.embroidery

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.InflaterInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real user image regression ("Vasos de Bênção").
 *
 * Reference is a 96x96, 12-color miniature derived from the supplied
 * 1536x1536 JPG, NOT a PES and NOT a fidelity benchmark for lettering.
 * Miniature is checked for provenance using the decompressed-byte SHA256.
 * Full-resolution visual fidelity and stitch-out remain separate gates.
 */
class VasosDeBencaoArtworkRegressionTest {
    private fun imageFromUploadedArtwork(): RasterImagePixels {
        val resource = javaClass.getResourceAsStream(
            "/qa/vasos_de_bencao_96px_palette_zlib.b64"
        ) ?: error("Missing Vasos de Bênção QA reference")
        val encoded = resource.bufferedReader().use { it.readText().trim() }
        val data = InflaterInputStream(
            ByteArrayInputStream(Base64.getDecoder().decode(encoded))
        ).use { it.readBytes() }
        assertEquals(36 + 96 * 96, data.size)
        val sha = MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it) }
        assertEquals(
            "4efbfcf10197f75d92f2cdff05a5c5fa935886c209a0a32bc60323f42ecaa44f",
            sha
        )
        val pixels = IntArray(96 * 96) { i ->
            val paletteIndex = data[36 + i].toInt() and 0xff
            require(paletteIndex in 0..11)
            val r = data[paletteIndex * 3].toInt() and 0xff
            val g = data[paletteIndex * 3 + 1].toInt() and 0xff
            val b = data[paletteIndex * 3 + 2].toInt() and 0xff
            (0xff shl 24) or (r shl 16) or (g shl 8) or b
        }
        return RasterImagePixels(96, 96, pixels, "Vasos de Bênção • QA")
    }

    @Test fun referenceHasLightBackgroundDarkBlueAndGoldRegions() {
        val pixels = imageFromUploadedArtwork()
        val audit = RasterArtworkComplexityAudit.examine(pixels)
        assertTrue(audit.backgroundPixels > pixels.argb.size / 2)
        assertTrue(audit.darkBluePixels > 300)
        assertTrue(audit.warmGoldPixels > 200)
        assertTrue(audit.tonalBuckets >= 4)
        assertTrue(audit.requiresMultiTechniqueReview)
    }

    @Test fun wholeImageCannotMasqueradeAsSafeSingleModeSatinPES() {
        val actual = imageFromUploadedArtwork()
        val result = RasterGeometryAwareSatinPreview.create(
            actual, 85f, HoopProfile.H130X180
        )
        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message.orEmpty()
                .contains("múltiplas técnicas")
        )
        // The new full-logo compositor must ALSO fail closed on the exact
        // user-supplied complex reference, not just the older Satin route.
        val automatic = RasterAutomaticLogoPreview.create(
            actual, 85f, HoopProfile.H130X180
        )
        assertTrue(automatic.isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun realVasosReferenceRejectsAVisualOverlayMasqueradingAsStitches() {
        val realArtwork = imageFromUploadedArtwork()
        // Intentionally tiny legitimate sequence: unlike a screenshot of
        // the complete JPG over a hoop, this exposes the actual missing ink.
        val points = listOf(
            EmbroideryPoint(-50,0,StitchCommand.JUMP,0),
            EmbroideryPoint(0,0,StitchCommand.STITCH,0),
            EmbroideryPoint(50,0,StitchCommand.STITCH,0),
            EmbroideryPoint(50,0,StitchCommand.END,0)
        )
        val fakeWholeImage = EmbroideryDesign(
            fileName = "não-é-PES.qa",
            format = "QA_AUTO_STITCH",
            label = "Trajetória parcial versus fotografia real",
            points = points,
            bounds = EmbroideryBounds(-50,50,0,0),
            stitchCount = 2, jumpCount = 1, colorChanges = 0,
            endFound = true, sourceBytes = ByteArray(0),
            threadColors = listOf(0xff173c5c.toInt()),
            isModified = true,
            hoopProfile = HoopProfile.H130X180
        )
        val comparison = RasterWholeArtworkFidelity.inspect(
            realArtwork, fakeWholeImage, 85f
        ).getOrThrow()
        assertTrue(
            "Uma linha de pontos não pode representar todo o logo",
            comparison.coverage < .15
        )
        assertTrue(comparison.significantColorMinimumCoverage < .15)
        // Use the new PR #137 report with a REAL (reduced-resolution)
        // reference, not just synthetic rectangles. A sparse needle path
        // must be a documented QA FAIL, regardless of parser metadata.
        val dossier=RasterQaReadinessReportBuilder.collect(
            fakeWholeImage,HoopProfile.H130X180,
            realArtwork,85f
        ).getOrThrow()
        val fidelity=dossier.gates.first {
            it.label.contains("Fidelidade global")
        }
        assertEquals(RasterQaGateStatus.FAIL,fidelity.status)
        assertTrue(dossier.hasStaticFailures)
        assertTrue(dossier.missingEvidence)
        assertTrue(dossier.toMarkdown().contains("BLOQUEADO EM QA"))
        assertFalse(dossier.exportPermitted)
        assertFalse(dossier.machineCertified)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun plainOneColorBandIsNotMistakenForThisComplexArtwork() {
        val w = 96
        val h = 96
        val bitmap = IntArray(w*h) { index ->
            val x = index % w
            val y = index / w
            if (x in 15..75 && y in 42..50) 0xff102c48.toInt()
            else 0xffffffff.toInt()
        }
        val plain = RasterImagePixels(w, h, bitmap, "faixa simples")
        assertFalse(RasterArtworkComplexityAudit.examine(plain)
            .requiresMultiTechniqueReview)
    }
}
