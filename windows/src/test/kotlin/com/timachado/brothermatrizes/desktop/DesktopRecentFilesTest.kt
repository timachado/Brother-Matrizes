package com.timachado.brothermatrizes.desktop

import java.nio.file.Files
import java.util.prefs.Preferences
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopRecentFilesTest {
    @Test
    fun `registra apenas caminhos escolhidos reordena e permite apagar recentes`() {
        val folder = Files.createTempDirectory("brother-recentes-qa-")
        val preferences = Preferences.userRoot().node(
            "bm-recent-qa-" + UUID.randomUUID().toString()
        )
        try {
            val first = DesktopMatrixService.saveNew(
                DesktopMatrixService.createName("QA", 12f),
                "DST", folder.resolve("primeiro.dst")
            )
            val second = DesktopMatrixService.saveNew(
                DesktopMatrixService.createName("AB", 12f),
                "PES", folder.resolve("segundo.pes")
            )
            val original = Files.readAllBytes(first)
            val store = DesktopRecentFiles(preferences)
            assertTrue(store.list().isEmpty())
            store.record(first)
            store.record(second)
            assertEquals(listOf(second, first), store.list())
            store.record(first)
            assertEquals(listOf(first, second), store.list())
            assertEquals(original.toList(), Files.readAllBytes(first).toList())

            val other = folder.resolve("nao-portado.txt")
            Files.writeString(other, "nao e matriz")
            assertFailsWith<IllegalArgumentException> { store.record(other) }
            Files.delete(second)
            assertEquals(listOf(first), store.list())
            store.clear()
            assertTrue(store.list().isEmpty())
        } finally {
            preferences.removeNode()
            Files.list(folder).use { stream ->
                stream.forEach { Files.deleteIfExists(it) }
            }
            Files.deleteIfExists(folder)
        }
    }
}
