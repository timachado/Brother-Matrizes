package com.timachado.brothermatrizes.core.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateInAppNoticeRulesTest {
    @Test fun onlyNewerReleasesAppear() {
        assertTrue(UpdateInAppNoticeRules.shouldShow(175, 176, 0))
        assertFalse(UpdateInAppNoticeRules.shouldShow(175, 175, 0))
        assertFalse(UpdateInAppNoticeRules.shouldShow(175, 174, 0))
        assertFalse(UpdateInAppNoticeRules.shouldShow(0, 176, 0))
    }

    @Test fun dismissalIsScopedToEachVersion() {
        assertFalse(UpdateInAppNoticeRules.shouldShow(175, 176, 176))
        assertTrue(UpdateInAppNoticeRules.shouldShow(175, 177, 176))
        assertTrue(UpdateInAppNoticeRules.shouldShow(175, 176, 175))
    }

    @Test fun releaseNotesAreCuratedWithoutTechnicalJargon() {
        val notes = """
            Novidades desta versão:
            - Melhorias de estabilidade e desempenho.
            - Correções no sistema de matrizes e fontes.
            - Atualização via APK e Firebase / FCM.
            - Endpoint WordPress e SHA256 sincronizados.
        """.trimIndent()
        val highlights = UpdateInAppNoticeRules.highlights(notes)
        assertEquals(2, highlights.size)
        assertTrue(highlights[0].contains("estabilidade"))
        assertFalse(highlights.any { it.contains("APK") || it.contains("FCM") })
    }

    @Test fun defaultHighlightsWhenReleaseTextIsTechnical() {
        assertEquals(3, UpdateInAppNoticeRules.highlights("APK / SHA256 / WordPress").size)
        assertEquals(1400, "A".repeat(1400).length)
    }
}
