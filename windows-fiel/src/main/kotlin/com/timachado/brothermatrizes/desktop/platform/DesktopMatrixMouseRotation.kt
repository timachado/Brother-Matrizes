package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.embroidery.EmbroideryBounds
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.HoopLayerPositioning
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.MatrixNameRotation
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Permanent, undoable-by-reloading source matrix rotation with a Windows mouse.
 * Rotates embroidery commands and guides around ONE pivot. Composite designs
 * retain the editable name, its source design, and its absolute anchor.
 * Does not write a PES/DST/SVG source file or award any commercial permission.
 */
internal object DesktopMatrixMouseRotation {
    fun rotate(
        original: EmbroideryDesign,
        degrees: Float,
        hoop: HoopProfile
    ): Result<EmbroideryDesign> = runCatching {
        require(degrees.isFinite() && degrees in -360f..360f) {
            "Ângulo de rotação inválido."
        }
        require(original.points.any { it.command == StitchCommand.STITCH }) {
            "A matriz não contém pontadas para girar."
        }
        if (degrees == 0f) return@runCatching original
        val cx = (original.bounds.minXUnits.toLong() +
            original.bounds.maxXUnits.toLong()) / 2.0
        val cy = (original.bounds.minYUnits.toLong() +
            original.bounds.maxYUnits.toLong()) / 2.0
        val theta = Math.toRadians(degrees.toDouble())
        val cosine = cos(theta)
        val sine = sin(theta)
        fun rotatePoint(point: EmbroideryPoint): EmbroideryPoint {
            val x = point.xUnits - cx
            val y = point.yUnits - cy
            val rx = cx + x * cosine - y * sine
            val ry = cy + x * sine + y * cosine
            require(rx.isFinite() && ry.isFinite() &&
                rx in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() &&
                ry in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
                "A rotação ultrapassa o limite das coordenadas."
            }
            return point.copy(xUnits = rx.roundToInt(), yUnits = ry.roundToInt())
        }
        fun reorient(d: EmbroideryDesign): EmbroideryDesign {
            val points = d.points.map(::rotatePoint)
            val drawable = points.filter { it.command != StitchCommand.END }
                .ifEmpty { points }
            return d.copy(
                points = points,
                guidePoints = d.guidePoints.map(::rotatePoint),
                bounds = EmbroideryBounds(
                    drawable.minOf { it.xUnits }, drawable.maxOf { it.xUnits },
                    drawable.minOf { it.yUnits }, drawable.maxOf { it.yUnits }
                ),
                sourceBytes = byteArrayOf(),
                isModified = true
            )
        }
        val rotated = reorient(original)
        val layer = original.editableName
        val result = if (layer == null) rotated else {
            val anchor = rotatePoint(
                EmbroideryPoint(
                    layer.centerXUnits, layer.centerYUnits,
                    StitchCommand.STITCH, 0
                )
            )
            rotated.copy(
                editableName = layer.copy(
                    original = reorient(layer.original),
                    centerXUnits = anchor.xUnits,
                    centerYUnits = anchor.yUnits,
                    options = layer.options.copy(
                        rotationDegrees = MatrixNameRotation.normalizeDegrees(
                            layer.options.rotationDegrees + degrees
                        )
                    )
                )
            )
        }
        if (result.hoopPositionLocked) {
            HoopLayerPositioning.validateHoop(result, hoop)
        }
        result
    }

    /** Screen moves to the right mean clockwise motion, independent of Y axis. */
    fun mouseAngle(screenDragPixels: Float, sourceYAxisDown: Boolean): Float {
        require(screenDragPixels.isFinite()) { "Movimento de mouse inválido." }
        val clockwise = (screenDragPixels * 0.5f).coerceIn(-180f, 180f)
        return if (sourceYAxisDown) clockwise else -clockwise
    }
}
