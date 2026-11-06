package com.timachado.brothermatrizes.desktop

import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopThemePreferencesTest {
    @Test
    fun `tema persiste entre instancias sem afetar outras preferencias`() {
        val node = Preferences.userRoot().node("bm-theme-qa-" + UUID.randomUUID())
        try {
            node.put("localFavoriteFilesV1", "valor-de-teste")
            val theme = DesktopThemePreferences(node)
            assertTrue(theme.loadDark(), "Tema inicial precisa respeitar o Android oficial.")
            theme.saveDark(false)
            assertFalse(DesktopThemePreferences(node).loadDark())
            theme.saveDark(true)
            assertTrue(DesktopThemePreferences(node).loadDark())
            assertEquals("valor-de-teste", node.get("localFavoriteFilesV1", ""))
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun `preferencia ausente ou adulterada retorna ao modo escuro sem regravar`() {
        val node = Preferences.userRoot().node("bm-theme-invalid-qa-" + UUID.randomUUID())
        try {
            val theme = DesktopThemePreferences(node)
            assertTrue(theme.loadDark())
            assertEquals("", node.get(DesktopThemePreferences.KEY, ""))
            node.put(DesktopThemePreferences.KEY, "invalido")
            assertTrue(theme.loadDark())
            assertEquals("invalido", node.get(DesktopThemePreferences.KEY, ""))
        } finally {
            node.removeNode()
        }
    }
}
