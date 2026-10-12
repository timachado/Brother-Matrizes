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

    @Test fun releaseNotesAreBoundedForHomeCard() {
        assertEquals("Novidades", UpdateInAppNoticeRules.notes("   Novidades   "))
        assertEquals(1400, UpdateInAppNoticeRules.notes("A".repeat(5000)).length)
    }
}