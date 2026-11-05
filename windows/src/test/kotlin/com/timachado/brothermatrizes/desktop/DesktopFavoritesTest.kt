package com.timachado.brothermatrizes.desktop

import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopFavoritesTest {
    @Test
    fun `favoritos persistem apenas referencias e nao alteram matrizes`() {
        val dir = Files.createTempDirectory("bm-favorites-")
        val node = Preferences.userRoot().node("bm-fav-qa-" + UUID.randomUUID())
        try {
            val dst = dir.resolve("flores.dst")
            val pes = dir.resolve("rosas.pes")
            Files.write(dst, byteArrayOf(1, 2, 3, 4))
            Files.write(pes, byteArrayOf(5, 6, 7))
            val original = Files.readAllBytes(dst)
            val favorites = DesktopFavorites(node)
            assertTrue(favorites.toggle(dst))
            assertTrue(favorites.toggle(pes))
            assertEquals(listOf(pes, dst), DesktopFavorites(node).list())
            assertFalse(favorites.toggle(dst))
            assertEquals(listOf(pes), favorites.list())
            assertEquals(original.toList(), Files.readAllBytes(dst).toList())
            Files.delete(pes)
            assertTrue(favorites.list().isEmpty())
            assertFailsWith<IllegalArgumentException> {
                favorites.toggle(dir.resolve("inexistente.dst"))
            }
            val txt = dir.resolve("malicioso.txt")
            Files.writeString(txt, "não é matriz")
            assertFailsWith<IllegalArgumentException> { favorites.toggle(txt) }
            assertTrue(favorites.toggle(dst))
            favorites.clear()
            assertTrue(DesktopFavorites(node).list().isEmpty())
        } finally {
            node.removeNode()
            Files.list(dir).use { files -> files.forEach { Files.deleteIfExists(it) } }
            Files.deleteIfExists(dir)
        }
    }

    @Test
    fun `limite de favoritos e de preferencias nao excede armazenamento local`() {
        val dir = Files.createTempDirectory("bm-favorites-limit-")
        val node = Preferences.userRoot().node("bm-fav-limit-" + UUID.randomUUID())
        try {
            val favorites = DesktopFavorites(node)
            repeat(20) { n ->
                val file = dir.resolve("arquivo-" + n.toString().padStart(2, '0') + ".dst")
                Files.write(file, byteArrayOf(1))
                assertTrue(favorites.toggle(file))
            }
            assertEquals(DesktopFavorites.MAX_FAVORITES, favorites.list().size)
            assertEquals("arquivo-19.dst", favorites.list().first().fileName.toString())
        } finally {
            node.removeNode()
            Files.list(dir).use { files -> files.forEach { Files.deleteIfExists(it) } }
            Files.deleteIfExists(dir)
        }
    }
}
