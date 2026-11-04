package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.MonogramStyle
import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopMonogramAndLibrarySelectionTest {
    @Test
    fun `estilos oficiais de monograma geram matrizes locais exportaveis`() {
        for (style in MonogramStyle.entries) {
            val doc = DesktopMatrixService.createMonogram("AB", style, 20f)
            assertTrue(doc.current.stitchCount > 0)
            assertTrue(doc.current.endFound)
            assertEquals(doc.current.points, doc.original.points)
            assertTrue(doc.original.sourceBytes.isEmpty())
            val bytes = DesktopMatrixService.convert(doc, "DST").bytes
            assertTrue(bytes.size > 512)
            val reopened = DesktopMatrixService.open("mono.dst", bytes)
            assertTrue(reopened.current.stitchCount > 0)
        }
    }

    @Test
    fun `monograma rejeita iniciais e alturas invalidas`() {
        assertFailsWith<IllegalArgumentException> {
            DesktopMatrixService.createMonogram("A1", MonogramStyle.CLASSIC, 22f)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopMatrixService.createMonogram("ABCD", MonogramStyle.CLASSIC, 22f)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopMatrixService.createMonogram("AB", MonogramStyle.CLASSIC, Float.NaN)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopMatrixService.createMonogram("AB", MonogramStyle.CLASSIC, 5f)
        }
    }

    @Test
    fun `biblioteca lembra somente pasta selecionada e pode esquecer`() {
        val prefs = Preferences.userRoot().node("com/timachado/bm-qa-tests/" + UUID.randomUUID())
        val folder = Files.createTempDirectory("bm-qa-persist-")
        val content = folder.resolve("cliente.dst")
        try {
            Files.write(content, byteArrayOf(1, 2, 3))
            val selection = DesktopLibrarySelection(prefs)
            assertNull(selection.recall())
            selection.remember(folder)
            val reopened = DesktopLibrarySelection(prefs)
            assertEquals(folder.toAbsolutePath().normalize(), reopened.recall())
            // Memorizar a pasta não varre o conteúdo; o usuário chama list() depois.
            assertNotNull(content)
            reopened.forget()
            assertNull(DesktopLibrarySelection(prefs).recall())
        } finally {
            Files.deleteIfExists(content)
            Files.deleteIfExists(folder)
            prefs.removeNode()
        }
    }

    @Test
    fun `biblioteca nao memoriza arquivo comum como diretorio`() {
        val prefs = Preferences.userRoot().node("com/timachado/bm-qa-tests/" + UUID.randomUUID())
        val folder = Files.createTempDirectory("bm-qa-not-folder-")
        val file = folder.resolve("arquivo.txt")
        try {
            Files.writeString(file, "exemplo")
            val selection = DesktopLibrarySelection(prefs)
            assertFailsWith<IllegalArgumentException> { selection.remember(file) }
            assertNull(selection.recall())
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(folder)
            prefs.removeNode()
        }
    }
}
