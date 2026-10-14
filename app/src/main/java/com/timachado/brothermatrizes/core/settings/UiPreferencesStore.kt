package com.timachado.brothermatrizes.core.settings

import android.content.Context
import kotlin.math.abs

enum class AppColorMode(val label: String) {
    DARK("Escuro"),
    LIGHT("Claro"),
    SYSTEM("Sistema")
}

object UiPreferencesStore {
    private const val PREFS =
        "brother_matrizes_ui_preferences"

    private const val KEY_COLOR_MODE = "color_mode"

    const val DEFAULT_COLOR_MODE = "DARK"

    fun colorMode(context: Context): AppColorMode {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_COLOR_MODE, DEFAULT_COLOR_MODE)
        return parseColorMode(stored)
    }

    fun parseColorMode(stored: String?): AppColorMode =
        AppColorMode.entries.firstOrNull { it.name == stored } ?: AppColorMode.DARK

    fun setColorMode(context: Context, mode: AppColorMode): AppColorMode {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_COLOR_MODE, mode.name).apply()
        return mode
    }

    private const val KEY_TEXT_SCALE =
        "text_scale"

    const val DEFAULT_TEXT_SCALE =
        1.0f

    val textScaleOptions =
        listOf(
            0.90f,
            1.00f,
            1.15f,
            1.30f
        )

    fun normalizeTextScale(
        value: Float
    ): Float =
        textScaleOptions
            .minBy {
                abs(
                    it -
                        value
                )
            }

    fun textScale(
        context: Context
    ): Float =
        normalizeTextScale(
            context
                .getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
                .getFloat(
                    KEY_TEXT_SCALE,
                    DEFAULT_TEXT_SCALE
                )
        )

    fun setTextScale(
        context: Context,
        value: Float
    ): Float {
        val normalized =
            normalizeTextScale(
                value
            )

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putFloat(
                KEY_TEXT_SCALE,
                normalized
            )
            .apply()

        return normalized
    }

    fun textScaleLabel(
        value: Float
    ): String =
        when (
            normalizeTextScale(
                value
            )
        ) {
            0.90f ->
                "Compacto"

            1.15f ->
                "Grande"

            1.30f ->
                "Extra grande"

            else ->
                "Padrão"
        }
}
