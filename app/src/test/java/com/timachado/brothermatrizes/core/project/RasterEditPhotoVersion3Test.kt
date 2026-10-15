package com.timachado.brothermatrizes.core.project

import com.timachado.brothermatrizes.core.embroidery.ColorLayerEdit
import com.timachado.brothermatrizes.core.embroidery.MultiColorRasterDigitizer
import com.timachado.brothermatrizes.core.embroidery.MultiColorRasterOptions
import com.timachado.brothermatrizes.core.embroidery.RasterCropBounds
import com.timachado.brothermatrizes.core.embroidery.RasterImagePixels
import com.timachado.brothermatrizes.core.embroidery.RasterPhotoPreparation
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.util.zip.CRC32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterEditPhotoVersion3Test {
    private fun original(): RasterImagePixels {
        val w = 68
        val h = 52
        val pixels = IntArray(w * h) { 0xFF171717.toInt() }
        for (y in 12..35) for (x in 16..30) {
            pixels[y * w + x] = 0xFFF18B25.toInt()
        }
        for (y in 15..39) for (x in 40..53) {
            pixels[y * w + x] = 0xFFF7F7F7.toInt()
        }
        return RasterImagePixels(w, h, pixels, "roupão original.jpeg")
    }

    private fun prepared(): RasterEditProject {
        val input = original()
        val bounds = RasterCropBounds(.08f, .08f, .92f, .92f)
        val photoResult = RasterPhotoPreparation.prepare(
            input, bounds, removeEdgeBackground = true, tolerance = 55
        ).getOrThrow()
        val artwork = MultiColorRasterDigitizer.separate(
            photoResult.image, MultiColorRasterOptions(widthMm = 60f, maxColors = 2)
        ).getOrThrow()
        return RasterEditProject(
            title = "Marca laranja e branca",
            image = photoResult.image,
            widthMm = 60f,
            maxColors = 2,
            outputFormat = "PES",
            hoopProfile = null,
            layers = artwork.colors.indices.map { ColorLayerEdit(it) },
            photoSource = RasterEditPhotoSource(
                original = input, crop = bounds,
                removeEdgeBackground = true, backgroundTolerance = 55
            )
        )
    }

    @Test fun roundTripV3RetainsOriginalAndEditedPixelsAndCrop() {
        val project = prepared()
        val restored = RasterEditProjectCodec.decode(
            RasterEditProjectCodec.encode(project)
        )
        assertEquals(project.title, restored.title)
        assertTrue(project.image.argb.contentEquals(restored.image.argb))
        assertTrue(project.photoSource!!.original.argb.contentEquals(
            restored.photoSource!!.original.argb
        ))
        assertEquals(project.photoSource!!.crop, restored.photoSource!!.crop)
        assertTrue(restored.photoSource!!.removeEdgeBackground)
        assertEquals(55, restored.photoSource!!.backgroundTolerance)
        assertEquals(project.layers, restored.layers)

        val reconstructed = RasterPhotoPreparation.prepare(
            restored.photoSource!!.original,
            restored.photoSource!!.crop,
            restored.photoSource!!.removeEdgeBackground,
            restored.photoSource!!.backgroundTolerance
        ).getOrThrow()
        assertTrue(reconstructed.image.argb.contentEquals(restored.image.argb))
    }

    @Test fun zipBackupKeepsOriginalPhotoForEditingAfterReopen() {
        val first = prepared()
        val backup = RasterEditProjectBackupCodec.encode(listOf(first))
        val reopened = RasterEditProjectBackupCodec.decode(backup).single()
        assertEquals(first.photoSource!!.original.label, reopened.photoSource!!.original.label)
        assertTrue(first.photoSource!!.original.argb.contentEquals(
            reopened.photoSource!!.original.argb
        ))
        val folder = Files.createTempDirectory("bm-v3-source").toFile()
        try {
            assertEquals(
                1, RasterEditProjectBackupStore.restoreAll(folder, backup).getOrThrow()
            )
            val id = RasterEditFileStore.list(folder).getOrThrow().single().id
            val loaded = RasterEditFileStore.load(folder, id).getOrThrow()
            assertTrue(loaded.photoSource != null)
            assertEquals(first.photoSource!!.crop, loaded.photoSource!!.crop)
        } finally {
            folder.listFiles()?.forEach { it.delete() }
            folder.delete()
        }
    }

    @Test fun v2DocumentsStillOpenWithoutInventingTheOriginalPhoto() {
        val modern = prepared().copy(photoSource = null)
        val bytes = RasterEditProjectCodec.encode(modern)
        // V2 layout is identical to V3 up to end of layers.
        // Strip the final V3-only false marker and recompute CRC.
        val legacyPayload = bytes.copyOfRange(0, bytes.size - 9)
        legacyPayload[4] = 0
        legacyPayload[5] = 0
        legacyPayload[6] = 0
        legacyPayload[7] = 2
        val checksum = CRC32().apply { update(legacyPayload) }.value
        val legacy = ByteArrayOutputStream().apply {
            write(legacyPayload)
            DataOutputStream(this).use { it.writeLong(checksum) }
        }.toByteArray()
        val restored = RasterEditProjectCodec.decode(legacy)
        assertEquals(modern.title, restored.title)
        assertNull(restored.photoSource)
        assertTrue(modern.image.argb.contentEquals(restored.image.argb))
        assertEquals(modern.layers, restored.layers)
        assertFalse(modern.image.argb.contentEquals(original().argb))
    }

    @Test fun tamperingWithOriginalOrCropFailsIntegrityAndValidation() {
        val input = prepared()
        val bytes = RasterEditProjectCodec.encode(input)
        val modified = bytes.copyOf()
        modified[modified.size - 24] = (modified[modified.size - 24].toInt() xor 0x2F).toByte()
        assertTrue(runCatching { RasterEditProjectCodec.decode(modified) }.isFailure)

        assertTrue(runCatching {
            RasterEditProjectCodec.encode(
                input.copy(photoSource = input.photoSource!!.copy(
                    crop = RasterCropBounds(.8f, .1f, .3f, .9f)
                ))
            )
        }.isFailure)
        assertTrue(runCatching {
            RasterEditProjectCodec.encode(
                input.copy(photoSource = input.photoSource!!.copy(backgroundTolerance = 1000))
            )
        }.isFailure)
    }
}
