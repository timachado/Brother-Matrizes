package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.desktop.platform.DesktopEmbroideryFiles
import com.timachado.brothermatrizes.desktop.platform.DesktopFlpProjectFiles
import com.timachado.brothermatrizes.desktop.platform.DesktopLaunchDocument
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Simulates Windows Explorer arguments, without registry, installer or user files. */
class DesktopLaunchDocumentTest {
    @Test
    fun onlyOneRegularSupportedDocumentIsInterpretedAsShellOpenRequest() {
        for (ext in listOf("flp", "DST", "Pes", "jEf")) {
            val file = Path.of("pasta de projeto", "Matriz teste.$ext")
                .toAbsolutePath().normalize()
            assertEquals(
                file,
                DesktopLaunchDocument.requestedPath(
                    arrayOf(file.toString())
                )
            )
        }
        for (args in listOf(
            emptyArray(),
            arrayOf("--capture-home-qa"),
            arrayOf("--untrusted-switch"),
            arrayOf("not-an-image.exe"),
            arrayOf("image.png"),
            arrayOf("file.dst", "other.dst"),
            arrayOf(""),
            arrayOf("bad\r\nname.pes")
        )) {
            assertNull(DesktopLaunchDocument.requestedPath(args))
        }
    }

    @Test
    fun opensOriginalFlpFromExplorerPathWithoutEditingAnyBytes() {
        val folder = Files.createTempDirectory("bm-arg-open-test-")
        val path = folder.resolve("Projeto Original Brother Matrizes.FLP")
        try {
            val original = TextLayoutGenerator.generate(
                TextLayoutOptions(
                    textOptions = TextMatrixOptions(
                        text = "Maria",
                        heightMm = 12f,
                        style = TextStitchStyle.RUNNING
                    )
                )
            ).getOrThrow()
            DesktopFlpProjectFiles.saveNew(original, path)
            val originalBytes = Files.readAllBytes(path)
            val candidate = DesktopLaunchDocument.requestedPath(
                arrayOf(path.toString())
            )
            assertEquals(path, candidate)
            val restored = DesktopEmbroideryFiles.open(requireNotNull(candidate))
            assertTrue(restored.points.isNotEmpty())
            assertEquals(original.points, restored.points)
            assertContentEquals(originalBytes, Files.readAllBytes(path))
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun invalidOrAbsentTargetNeverOpensExecutableOrUnknownData() {
        val folder = Files.createTempDirectory("bm-arg-deny-test-")
        val fake = folder.resolve("fake.pes")
        val executable = folder.resolve("suspicious.exe")
        try {
            Files.write(fake, byteArrayOf(1, 2, 3))
            Files.write(executable, byteArrayOf(1, 2, 3))
            assertNull(DesktopLaunchDocument.requestedPath(arrayOf(executable.toString())))
            val requested = DesktopLaunchDocument.requestedPath(arrayOf(fake.toString()))
            assertEquals(fake, requested)
            assertFailsWith<IllegalArgumentException> {
                DesktopEmbroideryFiles.open(requireNotNull(requested))
            }
            assertFailsWith<IllegalArgumentException> {
                DesktopEmbroideryFiles.open(folder.resolve("missing.flp"))
            }
        } finally {
            Files.deleteIfExists(fake)
            Files.deleteIfExists(executable)
            Files.deleteIfExists(folder)
        }
    }
}
