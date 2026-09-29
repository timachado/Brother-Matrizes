package com.timachado.brothermatrizes.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {
    @Test
    fun detectsNewPatchVersion() {
        assertTrue(
            AppUpdateChecker
                .isNewer(
                    candidate =
                        "0.45.1",
                    current =
                        "0.45.0"
                )
        )
    }

    @Test
    fun acceptsVPrefix() {
        assertTrue(
            AppUpdateChecker
                .isNewer(
                    candidate =
                        "v1.0.0",
                    current =
                        "0.45.0"
                )
        )
    }

    @Test
    fun equalVersionIsNotNewer() {
        assertFalse(
            AppUpdateChecker
                .isNewer(
                    candidate =
                        "0.45.0",
                    current =
                        "0.45.0"
                )
        )
    }

    @Test
    fun olderVersionIsNotNewer() {
        assertFalse(
            AppUpdateChecker
                .isNewer(
                    candidate =
                        "0.44.9",
                    current =
                        "0.45.0"
                )
        )
    }
}
