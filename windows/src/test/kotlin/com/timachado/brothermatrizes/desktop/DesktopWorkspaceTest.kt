package com.timachado.brothermatrizes.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopWorkspaceTest {
    @Test
    fun `navegacao agrupa sete modulos reais sem anunciar paridade total`() {
        assertEquals(
            listOf("Início", "Visualizar", "Criar nome", "Monogramas",
                "Fontes", "Editor", "Biblioteca"),
            DesktopWorkspace.entries.map { it.label }
        )
        assertEquals(DesktopWorkspace.entries.size,
            DesktopWorkspace.entries.map { it.label }.distinct().size)
        assertTrue(DesktopWorkspace.entries.none { it.label.contains("Completo") })
    }
}
