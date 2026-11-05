package com.timachado.brothermatrizes.desktop

import androidx.compose.ui.input.key.Key

/**
 * Atalhos disponíveis somente quando a área de prévia do Editor
 * recebe foco por clique. Não interferem em campos de texto.
 *
 * A interpretação de teclas é pura e testável, independente de I/O.
 */
internal enum class DesktopEditorShortcut {
    UNDO, REDO, FIT, ZOOM_IN, ZOOM_OUT
}

internal object DesktopEditorKeymap {
    fun resolve(
        key: Key,
        control: Boolean,
        shift: Boolean,
        alt: Boolean
    ): DesktopEditorShortcut? {
        if (!control || alt) return null
        return when {
            key == Key.Z && shift -> DesktopEditorShortcut.REDO
            key == Key.Z -> DesktopEditorShortcut.UNDO
            key == Key.Y -> DesktopEditorShortcut.REDO
            key == Key.Zero -> DesktopEditorShortcut.FIT
            key == Key.Equals || key == Key.Plus -> DesktopEditorShortcut.ZOOM_IN
            key == Key.Minus -> DesktopEditorShortcut.ZOOM_OUT
            else -> null
        }
    }
}
