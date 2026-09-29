package com.timachado.brothermatrizes.core.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class UiPreferencesStoreTest {
    @Test
    fun normalizesTextScaleToSupportedChoices() {
        assertEquals(
            0.90f,
            UiPreferencesStore
                .normalizeTextScale(
                    0.86f
                )
        )

        assertEquals(
            1.00f,
            UiPreferencesStore
                .normalizeTextScale(
                    1.04f
                )
        )

        assertEquals(
            1.15f,
            UiPreferencesStore
                .normalizeTextScale(
                    1.18f
                )
        )

        assertEquals(
            1.30f,
            UiPreferencesStore
                .normalizeTextScale(
                    1.50f
                )
        )
    }
}
