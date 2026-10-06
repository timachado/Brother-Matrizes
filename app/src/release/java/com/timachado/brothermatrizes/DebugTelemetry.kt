package com.timachado.brothermatrizes

import android.content.Context
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign

/**
 * Release intentionally contains no diagnostic analytics.
 */
internal object DebugTelemetry {
    fun setup(
        context: Context
    ) = Unit

    fun captureSimulationOpened(
        design: EmbroideryDesign
    ) = Unit
}
