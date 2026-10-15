package com.timachado.brothermatrizes.core.project

import com.timachado.brothermatrizes.core.embroidery.ColorLayerEdit
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.ManualSatinSection
import com.timachado.brothermatrizes.core.embroidery.RasterImagePixels
import com.timachado.brothermatrizes.core.embroidery.RasterLayerStitchMode
import com.timachado.brothermatrizes.core.embroidery.SatinRailPair
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterEditProjectStoreTest {
    private fun sample(): RasterEditProject {
        val w = 32
        val h = 26
        val pixels = IntArray(w * h) { 0xFF000000.toInt() }
        for (y in 5..20) {
            for (x in 6..10) pixels[y * w + x] = 0xFFEE8F24.toInt()
            for (x in 17..25) pixels[y * w + x] = 0xFFF9F9F9.toInt()
        }
        val first = ManualSatinSection(
            (0..5).map { n ->
                val y = .2f + n * .1f
                SatinRailPair(.19f, y, .26f, y)
            }
        )
        val next = ManualSatinSection(
            (0..5).map { n ->
                val y = .2f + n * .1f
                SatinRailPair(.6f, y, .68f, y)
            }
        )
        return RasterEditProject(
            title = "Sabrina Freitas • edição",
            image = RasterImagePixels(w, h, pixels, "roupão frente e costas"),
            widthMm = 85f,
            maxColors = 3,
            outputFormat = "PES",
            hoopProfile = HoopProfile.H100X100,
            layers = listOf(
                ColorLayerEdit(
                    sourceIndex = 1,
                    enabled = true,
                    threadColor = 0xE67D20,
                    rowSpacingMm = .6f,
                    stitchMode = RasterLayerStitchMode.MANUAL_SATIN,
                    satinPullCompensationMm = .18f,
                    satinCenterUnderlay = false,
                    manualSections = listOf(first, next)
                ),
                ColorLayerEdit(sourceIndex = 0, enabled = false)
            )
        )
    }

    @Test fun codecRoundtripPreservesSourcePaletteOrderAndAllSections() {
        val input = sample()
        val bytes = RasterEditProjectCodec.encode(input)
        val parsed = RasterEditProjectCodec.decode(bytes)
        assertEquals(input.title, parsed.title)
        assertEquals(input.image.label, parsed.image.label)
        assertTrue(input.image.argb.contentEquals(parsed.image.argb))
        assertEquals(input.widthMm, parsed.widthMm)
        assertEquals(input.maxColors, parsed.maxColors)
        assertEquals(input.outputFormat, parsed.outputFormat)
        assertEquals(input.hoopProfile, parsed.hoopProfile)
        assertEquals(input.layers, parsed.layers)
        assertEquals(2, parsed.layers.first().manualSections.size)
    }

    @Test fun badChecksumAndTruncationDoNotYieldProject() {
        val raw = RasterEditProjectCodec.encode(sample())
        val corrupt = raw.copyOf()
        corrupt[42] = (corrupt[42].toInt() xor 0x40).toByte()
        assertTrue(runCatching { RasterEditProjectCodec.decode(corrupt) }.isFailure)
        assertTrue(runCatching {
            RasterEditProjectCodec.decode(raw.copyOf(raw.size - 3))
        }.isFailure)
    }

    @Test fun projectSaveLoadListAndUpdatePreserveOneIdentifier() {
        val root = Files.createTempDirectory("bm-rails-test").toFile()
        try {
            val initial = sample()
            val id = RasterEditFileStore.save(root, initial).getOrThrow()
            val summaries = RasterEditFileStore.list(root).getOrThrow()
            assertEquals(1, summaries.size)
            assertEquals(initial.title, summaries.first().title)
            val loaded = RasterEditFileStore.load(root, id).getOrThrow()
            assertEquals(initial.layers, loaded.layers)
            assertTrue(loaded.image.argb.contentEquals(initial.image.argb))

            val updated = initial.copy(
                title = "Nova revisão",
                layers = initial.layers.map {
                    if (it.sourceIndex == 1) it.copy(threadColor = 0xFFFFFF) else it
                }
            )
            val current = RasterEditFileStore.save(root, updated, id).getOrThrow()
            assertEquals(id, current)
            assertEquals("Nova revisão", RasterEditFileStore.load(root, id)
                .getOrThrow().title)
            assertEquals(1, RasterEditFileStore.list(root).getOrThrow().size)
            val clone = RasterEditFileStore.save(root, updated).getOrThrow()
            assertNotEquals(id, clone)
            assertEquals(2, RasterEditFileStore.list(root).getOrThrow().size)
        } finally {
            root.listFiles()?.forEach { it.delete() }
            root.delete()
        }
    }

    @Test fun failsClosedForTraversalAndOutOfBoundsRails() {
        val root = Files.createTempDirectory("bm-safe-id").toFile()
        try {
            assertTrue(RasterEditFileStore.load(root, "../backup").isFailure)
            assertTrue(RasterEditFileStore.save(root, sample(), "../lost").isFailure)
            val bad = sample().copy(
                layers = listOf(
                    sample().layers.first().copy(
                        manualSections = listOf(
                            ManualSatinSection(listOf(
                                SatinRailPair(-.2f, 0f, .4f, .6f),
                                SatinRailPair(.2f, .2f, .6f, .7f)
                            ))
                        )
                    )
                )
            )
            assertTrue(runCatching { RasterEditProjectCodec.encode(bad) }.isFailure)
        } finally {
            root.listFiles()?.forEach { it.delete() }
            root.delete()
        }
    }
}
