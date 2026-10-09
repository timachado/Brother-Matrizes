package com.timachado.brothermatrizes.core.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class UiPreferencesStoreTest {
    @Test
    fun colorPreferenceDefaultsToApprovedDarkThemeAndRecognizesAllModes() {
        assertEquals(AppColorMode.DARK, UiPreferencesStore.parseColorMode(null))
        assertEquals(AppColorMode.DARK, UiPreferencesStore.parseColorMode("unsupported"))
        assertEquals(AppColorMode.DARK, UiPreferencesStore.parseColorMode("DARK"))
        assertEquals(AppColorMode.LIGHT, UiPreferencesStore.parseColorMode("LIGHT"))
        assertEquals(AppColorMode.SYSTEM, UiPreferencesStore.parseColorMode("SYSTEM"))
    }

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
