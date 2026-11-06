package com.timachado.brothermatrizes.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopWorkspaceTest {
    @Test
    fun `navegacao agrupa doze areas sem anunciar paridade total`() {
        assertEquals(
            listOf("Início", "Visualizar", "Simulador", "Criar nome", "Desenhar",
                "Monogramas", "Fontes", "Editor", "Biblioteca", "Transferir",
                "Comunidade", "Conta"),
            DesktopWorkspace.entries.map { it.label }
        )
        assertEquals(DesktopWorkspace.entries.size,
            DesktopWorkspace.entries.map { it.label }.distinct().size)
        assertTrue(DesktopWorkspace.entries.none { it.label.contains("Completo") })
    }
}
