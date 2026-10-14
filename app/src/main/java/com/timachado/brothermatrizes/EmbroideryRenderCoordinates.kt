package com.timachado.brothermatrizes

internal fun renderScreenY(
    centerScreenY: Float,
    centerYUnits: Float,
    pointYUnits: Float,
    scale: Float,
    sourceYAxisDown: Boolean,
    userOffsetY: Float = 0f
): Float {
    val direction =
        if (
            sourceYAxisDown
        ) {
            1f
        } else {
            -1f
        }

    return centerScreenY +
        (
            pointYUnits -
                centerYUnits
            ) *
            scale *
            direction +
        userOffsetY
}
