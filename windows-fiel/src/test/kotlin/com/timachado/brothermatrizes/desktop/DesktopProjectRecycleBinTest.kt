package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.desktop.platform.DesktopFlpProjectFiles
import com.timachado.brothermatrizes.desktop.platform.DesktopProjectCatalog
import com.timachado.brothermatrizes.desktop.platform.DesktopProjectRecycleBin
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** All recycle operations are fakes; no system trash or production data used. */
class DesktopProjectRecycleBinTest {
    private fun createFlp(folder: Path, name: String = "Maria.flp"): Path {
        val design = TextLayoutGenerator.generate(
            TextLayoutOptions(textOptions = TextMatrixOptions(
                text = "Maria", heightMm = 12f, style = TextStitchStyle.RUNNING
            ))
        ).getOrThrow()
        val file = folder.resolve(name)
        DesktopFlpProjectFiles.saveNew(design, file)
        return file
    }

    private fun deleteTree(folder: Path) {
        Files.walk(folder).use { walk ->
            walk.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    @Test
    fun acceptsOnlyMatchingLocalFlpAndDelegatesToRecycleBinOnce() {
        val folder = Files.createTempDirectory("bm-recycle-qa-")
        try {
            val file = createFlp(folder)
            val original = Files.readAllBytes(file)
            val summary = DesktopProjectCatalog.scan(folder).projects.single()
            var called = 0
            val result = DesktopProjectRecycleBin.moveConfirmed(
                folder, summary, osName = "Windows 11",
                recycle = {
                    called++
                    assertEquals(file, it)
                    true // Fake, never actually deletes or moves customer files.
                }
            )
            assertEquals(file, result)
            assertEquals(1, called)
            assertTrue(original.contentEquals(Files.readAllBytes(file)))
        } finally { deleteTree(folder) }
    }

    @Test
    fun refusesStaleUnknownOrUnsupportedPlatformBeforeAnyRecycleAttempt() {
        val folder = Files.createTempDirectory("bm-recycle-stale-")
        val other = Files.createTempDirectory("bm-recycle-other-")
        try {
            val file = createFlp(folder)
            val summary = DesktopProjectCatalog.scan(folder).projects.single()
            var called = 0
            val noRecycle: (Path) -> Boolean = { called++; true }
            assertFailsWith<IllegalStateException> {
                DesktopProjectRecycleBin.moveConfirmed(
                    folder, summary, osName = "Linux", recycle = noRecycle
                )
            }
            assertFailsWith<IllegalStateException> {
                DesktopProjectRecycleBin.moveConfirmed(
                    other, summary, osName = "Windows 11", recycle = noRecycle
                )
            }
            // A changed but still valid FLP must not be silently removed.
            Files.setLastModifiedTime(
                file, FileTime.fromMillis(summary.updatedAtMillis + 120_000L)
            )
            assertFailsWith<IllegalStateException> {
                DesktopProjectRecycleBin.moveConfirmed(
                    folder, summary, osName = "Windows 11", recycle = noRecycle
                )
            }
            assertEquals(0, called)
            assertTrue(Files.isRegularFile(file))
        } finally { deleteTree(folder); deleteTree(other) }
    }

    @Test
    fun rejectsFailedTrashAndNeverDeletesTheFileAsFallback() {
        val folder = Files.createTempDirectory("bm-recycle-failure-")
        try {
            val file = createFlp(folder)
            val summary = DesktopProjectCatalog.scan(folder).projects.single()
            val original = Files.readAllBytes(file)
            assertFailsWith<IllegalStateException> {
                DesktopProjectRecycleBin.moveConfirmed(
                    folder, summary, osName = "Windows 11", recycle = { false }
                )
            }
            assertTrue(Files.exists(file))
            assertTrue(original.contentEquals(Files.readAllBytes(file)))
        } finally { deleteTree(folder) }
    }
}
