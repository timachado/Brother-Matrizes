package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.desktop.platform.DesktopMachineTransferFile
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Local QA only: creates synthetic embroidery and file-system outputs.
 * No USB device, physical machine, Wi-Fi, app login or commercial quota.
 */
class DesktopMachineTransferFileTest {
    private fun design() = TextLayoutGenerator.generate(
        TextLayoutOptions(textOptions = TextMatrixOptions(
            text = "Maria", heightMm = 12f, style = TextStitchStyle.RUNNING
        ))
    ).getOrThrow()

    @Test
    fun writesNewPesAndDstForManuallySelectedFolderWithoutOverwriting() {
        val directory = Files.createTempDirectory("bm-transfer-windows-qa-")
        try {
            for (format in listOf("PES", "DST", "JEF")) {
                val prepared = DesktopMachineTransferFile.prepare(
                    design(), format, HoopProfile.H100X100
                )
                assertEquals(format, prepared.format)
                val filename = "maquina-qa.${format.lowercase()}"
                val target = directory.resolve(filename)
                assertFalse(Files.exists(target))
                assertEquals(target, DesktopMachineTransferFile.saveNew(prepared, target))
                val written = Files.readAllBytes(target)
                assertTrue(written.size > 100)
                assertContentEquals(prepared.bytes, written)
                assertFailsWith<java.nio.file.FileAlreadyExistsException> {
                    DesktopMachineTransferFile.saveNew(prepared, target)
                }
                assertContentEquals(written, Files.readAllBytes(target))
            }
        } finally {
            Files.list(directory).use { files ->
                files.forEach(Files::deleteIfExists)
            }
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun refusesUnsupportedFormatsWrongExtensionAndMissingDirectory() {
        val directory = Files.createTempDirectory("bm-transfer-invalid-")
        try {
            assertFailsWith<IllegalArgumentException> {
                DesktopMachineTransferFile.prepare(design(), "EXP", HoopProfile.H100X100)
            }
            val prepared = DesktopMachineTransferFile.prepare(
                design(), "PES", HoopProfile.H100X100
            )
            assertFailsWith<IllegalArgumentException> {
                DesktopMachineTransferFile.saveNew(prepared, directory.resolve("mismatch.dst"))
            }
            assertFailsWith<IllegalArgumentException> {
                DesktopMachineTransferFile.saveNew(
                    prepared, directory.resolve("missing").resolve("saved.pes")
                )
            }
            assertFalse(Files.exists(directory.resolve("mismatch.dst")))
            assertFalse(Files.exists(directory.resolve("missing")))
        } finally {
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun sourceDesignIsNotModifiedByTransferPreparation() {
        val original = design()
        val points = original.points.toList()
        val filename = original.fileName
        DesktopMachineTransferFile.prepare(original, "PES", HoopProfile.H100X100)
        assertEquals(points, original.points)
        assertEquals(filename, original.fileName)
    }
}
