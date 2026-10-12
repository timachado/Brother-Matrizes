package com.timachado.brothermatrizes.core.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class BrotherPushPayloadRulesTest {
    private fun msg(code: String, type: String = "update") = mapOf(
        "type" to type, "versionCode" to code,
        "versionName" to "1.0.7"
    )

    @Test fun onlyAnnouncesHigherVersions() {
        assertTrue(BrotherPushPayloadRules.shouldPost(msg("173"), 172, 0))
        assertFalse(BrotherPushPayloadRules.shouldPost(msg("172"), 172, 0))
        assertFalse(BrotherPushPayloadRules.shouldPost(msg("171"), 172, 0))
    }

    @Test fun ignoresDuplicatesOrUnknownFormats() {
        assertFalse(BrotherPushPayloadRules.shouldPost(msg("173"), 172, 173))
        assertFalse(BrotherPushPayloadRules.shouldPost(msg("invalid"), 172, 0))
        assertFalse(BrotherPushPayloadRules.shouldPost(msg("173", "sale"), 172, 0))
        assertFalse(BrotherPushPayloadRules.shouldPost(emptyMap(), 172, 0))
    }

    @Test fun trimsUntrustedMessageText() {
        assertEquals("Brother Matrizes: uma nova versão chegou!",
            BrotherPushPayloadRules.notificationTitle(emptyMap()))
        assertEquals(90, BrotherPushPayloadRules.notificationTitle(mapOf("title" to "A".repeat(300))).length)
        assertEquals(240, BrotherPushPayloadRules.notificationBody(mapOf("body" to "A".repeat(300))).length)
    }
}
