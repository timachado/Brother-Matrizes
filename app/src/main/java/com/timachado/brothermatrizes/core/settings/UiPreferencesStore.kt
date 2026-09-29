package com.timachado.brothermatrizes.core.settings

import android.content.Context
import kotlin.math.abs

object UiPreferencesStore {
    private const val PREFS =
        "brother_matrizes_ui_preferences"

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
