package com.timachado.brothermatrizes.core.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrotherFcmDiagnosticTest {
    @Test fun onlyDiagnosticTestIdCanTriggerDiagnostic() {
        assertTrue(BrotherPushPayloadRules.isDiagnostic(mapOf(
            "type" to "diagnostic", "testId" to "brother-push-qa-v1"
        )))
        assertFalse(BrotherPushPayloadRules.isDiagnostic(mapOf(
            "type" to "diagnostic", "testId" to "different-test"
        )))
        assertFalse(BrotherPushPayloadRules.isDiagnostic(mapOf(
            "type" to "update", "versionCode" to "9999"
        )))
        assertFalse(BrotherPushPayloadRules.isDiagnostic(emptyMap()))
    }

    @Test fun diagnosticIsNotTreatedAsNewRelease() {
        val data=mapOf("type" to "diagnostic", "testId" to "brother-push-qa-v1")
        assertFalse(BrotherPushPayloadRules.shouldPost(data, 173L, 0L))
    }
}
