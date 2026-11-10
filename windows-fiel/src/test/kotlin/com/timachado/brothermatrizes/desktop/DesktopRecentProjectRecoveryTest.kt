package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.desktop.platform.DesktopFlpProjectFiles
import com.timachado.brothermatrizes.desktop.platform.DesktopLibraryActivityStore
import com.timachado.brothermatrizes.desktop.platform.DesktopProjectCatalog
import com.timachado.brothermatrizes.desktop.platform.DesktopRecentProjectRecovery
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Synthetic FLP only; never accesses the actual Windows user library. */
class DesktopRecentProjectRecoveryTest {
    private fun makeName(name: String) = TextLayoutGenerator.generate(
        TextLayoutOptions(textOptions = TextMatrixOptions(
            text = name, heightMm = 12f, style = TextStitchStyle.RUNNING
        ))
    ).getOrThrow()

    @Test
    fun recoversLastOpenedFlpAfterRestartWithoutChangingOriginalBytes() {
        val folder = Files.createTempDirectory("bm-windows-recent-restart-")
        val older = folder.resolve("Maria.flp")
        val newer = folder.resolve("Ana.flp")
        val settings = folder.resolve("settings.tsv")
        try {
            val maria = makeName("Maria")
            val ana = makeName("Ana")
            DesktopFlpProjectFiles.saveNew(maria, older)
            DesktopFlpProjectFiles.saveNew(ana, newer)
            val oldBytes = Files.readAllBytes(older)
            val newBytes = Files.readAllBytes(newer)
            val store = DesktopLibraryActivityStore(settings)
            store.save(DesktopLibraryActivityStore.State(
                recents = listOf(newer.toString(), older.toString()),
                selectedFolder = folder.toString()
            ))
            val loaded = DesktopLibraryActivityStore(settings).load()
            val catalog = DesktopProjectCatalog.scan(folder)
            val restored = requireNotNull(DesktopRecentProjectRecovery.mostRecentValid(
                catalog, loaded.recents
            ))
            assertEquals(newer.toString(), restored.projectId)
            assertEquals(ana.points, restored.design.points)
            assertContentEquals(oldBytes, Files.readAllBytes(older))
            assertContentEquals(newBytes, Files.readAllBytes(newer))
        } finally {
            Files.list(folder).use { it.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun skipsCorruptOrMissingRecentsAndNeverOpensUnlistedPaths() {
        val folder = Files.createTempDirectory("bm-windows-recent-safe-")
        val foreign = Files.createTempDirectory("bm-windows-recent-foreign-")
        val lastGood = folder.resolve("good.flp")
        val former = folder.resolve("corrupt.flp")
        val outside = foreign.resolve("outside.flp")
        try {
            val design = makeName("Maria")
            DesktopFlpProjectFiles.saveNew(design, lastGood)
            DesktopFlpProjectFiles.saveNew(design, former)
            DesktopFlpProjectFiles.saveNew(design, outside)
            val catalog = DesktopProjectCatalog.scan(folder)
            val original = Files.readAllBytes(lastGood)
            val externalOriginal = Files.readAllBytes(outside)
            // Simulate a file changed between catalog scan and recent open.
            Files.write(former, byteArrayOf(0, 1, 2, 3, 4))
            val restored = requireNotNull(DesktopRecentProjectRecovery.mostRecentValid(
                catalog, listOf(outside.toString(), former.toString(), lastGood.toString())
            ))
            assertEquals(lastGood.toString(), restored.projectId)
            assertEquals(design.points, restored.design.points)
            assertContentEquals(original, Files.readAllBytes(lastGood))
            assertContentEquals(externalOriginal, Files.readAllBytes(outside))
            assertNull(DesktopRecentProjectRecovery.mostRecentValid(
                catalog, listOf(outside.toString(), folder.resolve("missing.flp").toString())
            ))
        } finally {
            Files.list(folder).use { it.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(folder)
            Files.list(foreign).use { it.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(foreign)
        }
    }
}
