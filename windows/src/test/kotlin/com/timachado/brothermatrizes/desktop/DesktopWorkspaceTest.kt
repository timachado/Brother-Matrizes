package com.timachado.brothermatrizes.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopWorkspaceTest {
    @Test
    fun `navegacao agrupa nove areas sem anunciar paridade total`() {
        assertEquals(
            listOf("Início", "Visualizar", "Criar nome", "Desenhar",
                "Monogramas", "Fontes", "Editor", "Biblioteca", "Transferir"),
            DesktopWorkspace.entries.map { it.label }
        )
        assertEquals(DesktopWorkspace.entries.size,
            DesktopWorkspace.entries.map { it.label }.distinct().size)
        assertTrue(DesktopWorkspace.entries.none { it.label.contains("Completo") })
    }
}
