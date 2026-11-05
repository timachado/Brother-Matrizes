package com.timachado.brothermatrizes.desktop

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopLibraryBrowserTest {
    private fun entry(name: String, format: String = "DST"): LocalMatrixEntry =
        LocalMatrixEntry(
            path = Path.of("C:/synthetic/$name"),
            name = name,
            format = format,
            bytes = 1024
        )

    @Test
    fun `pesquisa por nome ignora maiusculas e nao acessa disco`() {
        val entries = listOf(
            entry("Flores A.DST"),
            entry("Rosas.dst"),
            entry("Flor MEIO.JEF", "JEF"),
            entry("coração.PES", "PES")
        )
        val p = DesktopLibraryBrowser.page(entries, " fLoR ", "TODOS", 0)
        assertEquals(2, p.matching)
        assertEquals(listOf("Flores A.DST", "Flor MEIO.JEF"), p.items.map { it.name })
    }

    @Test
    fun `filtro por formato afeta somente lista ja selecionada`() {
        val entries = listOf(
            entry("a.dst"), entry("b.pes", "PES"), entry("c.jef", "JEF"),
            entry("d.flp", "FLP")
        )
        assertEquals(1, DesktopLibraryBrowser.page(entries, "", "FLP", 0).matching)
        assertEquals("d.flp", DesktopLibraryBrowser.page(entries, "", "FLP", 0).items.single().name)
        assertEquals(4, DesktopLibraryBrowser.page(entries, "", "TODOS", 0).matching)
    }

    @Test
    fun `paginas consecutivas alcançam todos os 500 arquivos`() {
        val entries = (1..500).map { entry("arquivo-${it.toString().padStart(3, '0')}.dst") }
        val selected = (0 until 13).flatMap { index ->
            val p = DesktopLibraryBrowser.page(entries, "", "TODOS", index)
            if (index == 12) assertEquals(20, p.items.size)
            assertEquals(13, p.totalPages)
            p.items
        }
        assertEquals(500, selected.size)
        assertEquals(entries, selected)
    }

    @Test
    fun `pagina muito alta é limitada ao fim sem erro e nao retorna registros antigos`() {
        val entries = (0..44).map { entry("a$it.dst") }
        val p = DesktopLibraryBrowser.page(entries, "", "TODOS", 99)
        assertEquals(2, p.totalPages)
        assertEquals(1, p.currentPage)
        assertEquals(5, p.items.size)
    }

    @Test
    fun `pesquisa sem resultados nao mostra paginas fantasmas`() {
        val p = DesktopLibraryBrowser.page(listOf(entry("flor.dst")), "inexistente", "TODOS", 0)
        assertEquals(0, p.matching)
        assertEquals(0, p.totalPages)
        assertTrue(p.items.isEmpty())
    }

    @Test
    fun `filtro favoritos combina com nome e nao acessa arquivo`() {
        val entries = listOf(
            entry("uma.dst"),
            entry("flores.pes", "PES"),
            entry("flores.jef", "JEF")
        )
        val selected = setOf(entries[0].path, entries[2].path)
        val matching = DesktopLibraryBrowser.page(
            entries, "flores", "FAVORITOS", 0, selected
        )
        assertEquals(1, matching.matching)
        assertEquals("flores.jef", matching.items.single().name)
        val noMatches = DesktopLibraryBrowser.page(
            entries, "", "FAVORITOS", 0
        )
        assertTrue(noMatches.items.isEmpty())
    }

    @Test
    fun `rejeita filtro e pagina invalidos`() {
        assertFailsWith<IllegalArgumentException> {
            DesktopLibraryBrowser.page(emptyList(), "", "EXE", 0)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopLibraryBrowser.page(emptyList(), "", "TODOS", -1)
        }
    }
}
