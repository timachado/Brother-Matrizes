package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object MatrixEditor {

    fun apply(
        design: EmbroideryDesign,
        transform: EditTransform
    ): EmbroideryDesign {
        require(transform.scale in 0.1f..5f) {
            "Escala fora do intervalo permitido."
        }

        if (design.points.isEmpty()) {
            return design.copy(
                threadColors =
                    transform.threadColors
                        ?: design.threadColors,
                isModified = true
            )
        }

        val sourceCenterX =
            (
                design.bounds.minXUnits +
                    design.bounds.maxXUnits
                ) / 2f

        val sourceCenterY =
            (
                design.bounds.minYUnits +
                    design.bounds.maxYUnits
                ) / 2f

        val targetCenterX =
            if (transform.centerAtOrigin) {
                transform.offsetXUnits.toFloat()
            } else {
                sourceCenterX +
                    transform.offsetXUnits
            }

        val targetCenterY =
            if (transform.centerAtOrigin) {
                transform.offsetYUnits.toFloat()
            } else {
                sourceCenterY +
                    transform.offsetYUnits
            }

        val radians =
            Math.toRadians(
                transform.rotationDegrees.toDouble()
            )

        val cosine = cos(radians)
        val sine = sin(radians)

        val editedPoints =
            design.points.map { point ->
                var x =
                    point.xUnits -
                        sourceCenterX

                var y =
                    point.yUnits -
                        sourceCenterY

                if (
                    transform.mirrorHorizontal
                ) {
                    x = -x
                }

                if (
                    transform.mirrorVertical
                ) {
                    y = -y
                }

                x *= transform.scale
                y *= transform.scale

                val rotatedX =
                    x * cosine -
                        y * sine

                val rotatedY =
                    x * sine +
                        y * cosine

                point.copy(
                    xUnits =
                        (
                            rotatedX +
                                targetCenterX
                            ).roundToInt(),
                    yUnits =
                        (
                            rotatedY +
                                targetCenterY
                            ).roundToInt()
                )
            }

        val coordinatePoints =
            editedPoints.filter {
                it.command != StitchCommand.END
            }.ifEmpty {
                editedPoints
            }

        val minX =
            coordinatePoints.minOf {
                it.xUnits
            }

        val maxX =
            coordinatePoints.maxOf {
                it.xUnits
            }

        val minY =
            coordinatePoints.minOf {
                it.yUnits
            }

        val maxY =
            coordinatePoints.maxOf {
                it.yUnits
            }

        return design.copy(
            points = editedPoints,
            bounds =
                EmbroideryBounds(
                    minXUnits = minX,
                    maxXUnits = maxX,
                    minYUnits = minY,
                    maxYUnits = maxY
                ),
            threadColors =
                transform.threadColors
                    ?: design.threadColors,
            isModified = true
        )
    }
}
