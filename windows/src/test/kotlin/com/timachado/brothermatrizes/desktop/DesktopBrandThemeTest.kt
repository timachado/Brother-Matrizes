package com.timachado.brothermatrizes.desktop

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Valores conferidos contra BrotherMatrizesTheme.kt da referência Android
 * comercial 1.11.8. Teste de contrato visual, não screenshot nem homologação.
 */
class DesktopBrandThemeTest {
    @Test
    fun `paleta Android modo escuro`() {
        try {
            DesktopBrandColors.dark = true
            assertEquals(Color(0xFF08131D), DesktopBrandColors.background)
            assertEquals(Color(0xFF101D28), DesktopBrandColors.surface)
            assertEquals(Color(0xFFE6BE70), DesktopBrandColors.gold)
            assertEquals(Color(0xFFF6E7C4), DesktopBrandColors.goldSoft)
            assertEquals(Color(0xFFA9B3BC), DesktopBrandColors.muted)
            assertEquals(Color(0xFFFF8A80), DesktopBrandColors.danger)
        } finally {
            DesktopBrandColors.dark = true
        }
    }

    @Test
    fun `paleta Android modo claro`() {
        try {
            DesktopBrandColors.dark = false
            assertEquals(Color(0xFFF7F5F0), DesktopBrandColors.background)
            assertEquals(Color.White, DesktopBrandColors.surface)
            assertEquals(Color(0xFF936616), DesktopBrandColors.gold)
            assertEquals(Color(0xFF675018), DesktopBrandColors.goldSoft)
            assertEquals(Color(0xFF546471), DesktopBrandColors.muted)
            assertEquals(Color(0xFFB43546), DesktopBrandColors.danger)
        } finally {
            DesktopBrandColors.dark = true
        }
    }
}
