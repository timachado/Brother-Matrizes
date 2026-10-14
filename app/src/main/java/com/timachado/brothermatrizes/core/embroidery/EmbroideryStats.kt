package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.hypot

fun estimateThreadMeters(design: EmbroideryDesign): Double {
    var totalMm = 0.0
    var previous: EmbroideryPoint? = null
    for (point in design.points) {
        if (point.command == StitchCommand.END) break
        val before = previous
        if (before != null && point.command == StitchCommand.STITCH) {
            val dx = (point.xUnits - before.xUnits) / 10.0
            val dy = (point.yUnits - before.yUnits) / 10.0
            totalMm += hypot(dx, dy)
        }
        previous = point
    }
    return totalMm * 2.2 / 1000.0
}
