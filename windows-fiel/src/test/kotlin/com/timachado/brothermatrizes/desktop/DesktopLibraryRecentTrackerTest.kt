package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.desktop.platform.DesktopFlpProjectFiles
import com.timachado.brothermatrizes.desktop.platform.DesktopLibraryRecentTracker
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every file is synthetic and isolated; no personal library or AppData. */
class DesktopLibraryRecentTrackerTest {
    private fun sample() = TextLayoutGenerator.generate(TextLayoutOptions(
        textOptions = TextMatrixOptions(
            text = "Maria", heightMm = 12f, style = TextStitchStyle.RUNNING
        )
    )).getOrThrow()

    @Test
    fun savedFlpBecomesVisibleInSelectedLibraryAndRecentWithoutMutation() {
        val folder = Files.createTempDirectory("bm-recent-tracking-")
        val saved = folder.resolve("novo-projeto.flp")
        try {
            DesktopFlpProjectFiles.saveNew(sample(), saved)
            val bytes = Files.readAllBytes(saved)
            val tracked = requireNotNull(
                DesktopLibraryRecentTracker.refreshIfInLibrary(
                    folder.toString(), saved
                )
            )
            assertEquals(saved.toString(), tracked.id)
            assertTrue(tracked.catalog.projects.any { it.id == saved.toString() })
            assertEquals(
                listOf(saved.toString(), "older", "another"),
                DesktopLibraryRecentTracker.putFirst(
                    saved.toString(), listOf("older", saved.toString(), "another")
                )
            )
            assertContentEquals(bytes, Files.readAllBytes(saved))
        } finally {
            Files.deleteIfExists(saved)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun outsideFolderInvalidAndMissingFilesDoNotRegister() {
        val chosen = Files.createTempDirectory("bm-recent-chosen-")
        val outside = Files.createTempDirectory("bm-recent-outside-")
        val foreignFile = outside.resolve("outside.flp")
        val fake = chosen.resolve("not-real.flp")
        try {
            DesktopFlpProjectFiles.saveNew(sample(), foreignFile)
            assertNull(DesktopLibraryRecentTracker.refreshIfInLibrary(
                chosen.toString(), foreignFile
            ))
            assertNull(DesktopLibraryRecentTracker.refreshIfInLibrary(
                chosen.toString(), fake
            ))
            assertNull(DesktopLibraryRecentTracker.refreshIfInLibrary(
                null, foreignFile
            ))
            Files.write(fake, byteArrayOf(0, 1, 2, 3))
            assertNull(DesktopLibraryRecentTracker.refreshIfInLibrary(
                chosen.toString(), fake
            ))
        } finally {
            Files.deleteIfExists(fake)
            Files.deleteIfExists(foreignFile)
            Files.deleteIfExists(chosen)
            Files.deleteIfExists(outside)
        }
    }

    @Test
    fun repeatedOpenKeepsOnlyOneEntryAndCapsRecents() {
        val current = "/synthetic/current.flp"
        val prior = (1..60).map { "/synthetic/$it.flp" }
        val first = DesktopLibraryRecentTracker.putFirst(current, prior)
        assertEquals(30, first.size)
        assertEquals(current, first.first())
        val second = DesktopLibraryRecentTracker.putFirst(current, first)
        assertEquals(first, second)
    }
}
