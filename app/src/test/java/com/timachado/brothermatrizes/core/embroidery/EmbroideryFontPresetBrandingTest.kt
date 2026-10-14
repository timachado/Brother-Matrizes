package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * O rename Fio -> BM e somente visual: nao renomear os enum IDs
 * porque as preferencias de Criar Nome persistem pelo enum.name.
 */
class EmbroideryFontPresetBrandingTest {

    @Test fun nativeFontsUseBrotherMatrizesBrand() {
        val expectedNames = mapOf(
            EmbroideryFontPreset.LINE to "BM Linha",
            EmbroideryFontPreset.COMPACT to "BM Compacta",
            EmbroideryFontPreset.WIDE to "BM Larga",
            EmbroideryFontPreset.MODERN to "BM Moderna",
            EmbroideryFontPreset.ITALIC to "BM Itálica",
            EmbroideryFontPreset.ELEGANT to "BM Elegante",
            EmbroideryFontPreset.ORNAMENTAL to "BM Ornamental"
        )
        assertEquals(EmbroideryFontPreset.entries.size, expectedNames.size)
        expectedNames.forEach { (font, label) ->
            assertEquals(label, font.displayName)
            assertFalse(font.displayName.startsWith("Fio "))
        }
    }

    @Test fun storedPresetIdentifiersAreBackwardCompatible() {
        assertEquals("LINE", EmbroideryFontPreset.LINE.name)
        assertEquals("COMPACT", EmbroideryFontPreset.COMPACT.name)
        assertEquals("WIDE", EmbroideryFontPreset.WIDE.name)
        assertEquals("MODERN", EmbroideryFontPreset.MODERN.name)
        assertEquals("ITALIC", EmbroideryFontPreset.ITALIC.name)
        assertEquals("ELEGANT", EmbroideryFontPreset.ELEGANT.name)
        assertEquals("ORNAMENTAL", EmbroideryFontPreset.ORNAMENTAL.name)
    }
}
