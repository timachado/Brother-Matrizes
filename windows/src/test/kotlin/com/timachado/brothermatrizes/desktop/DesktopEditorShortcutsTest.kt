package com.timachado.brothermatrizes.desktop

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopEditorShortcutsTest {
    private fun map(
        key: Key,
        control: Boolean = true,
        shift: Boolean = false,
        alt: Boolean = false
    ) = DesktopEditorKeymap.resolve(key, control, shift, alt)

    @Test
    fun `atalhos de desfazer e refazer seguem convencoes Windows`() {
        assertEquals(DesktopEditorShortcut.UNDO, map(Key.Z))
        assertEquals(DesktopEditorShortcut.REDO, map(Key.Y))
        assertEquals(DesktopEditorShortcut.REDO, map(Key.Z, shift = true))
        assertNull(map(Key.Z, control = false))
        assertNull(map(Key.Z, alt = true))
    }

    @Test
    fun `zoom por teclado nao mapeia teclas sem controle`() {
        assertEquals(DesktopEditorShortcut.FIT, map(Key.Zero))
        assertEquals(DesktopEditorShortcut.ZOOM_IN, map(Key.Equals))
        assertEquals(DesktopEditorShortcut.ZOOM_IN, map(Key.Plus, shift = true))
        assertEquals(DesktopEditorShortcut.ZOOM_OUT, map(Key.Minus))
        assertNull(map(Key.Minus, control = false))
        assertNull(map(Key.A))
    }
}
