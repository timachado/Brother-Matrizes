package com.timachado.brothermatrizes.font

import io.github.humbleui.skija.Font
import io.github.humbleui.skija.FontHinting
import io.github.humbleui.skija.FontMgr
import io.github.humbleui.skija.PathMeasure
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Extração clean-room do contorno TTF/OTF usando o mesmo backend gráfico
 * (Skia) observado no MãoDesign.
 *
 * Este módulo NÃO contém código do app de referência. Ele reproduz apenas
 * o contrato comportamental observado: CapHeight -> SKFont -> glyph width ->
 * glyph path -> TightBounds -> transformação global -> PathMeasure.
 */
internal object MaoSkiaOutlineExtractor {

    data class Point(
        val x: Float,
        val y: Float
    )

    data class Result(
        val glyphPolygons:
            List<List<List<Point>>>,
        val sourceWidth: Float,
        val sourceHeight: Float
    )

    private data class RawGlyph(
        val contours:
            List<List<Point>>,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    fun extract(
        font: ImportedFont,
        text: String,
        targetCapHeightUnits: Float,
        spacingUnits: Float,
        rotationDegrees: Float
    ): Result {
        val typeface =
            FontMgr
                .getDefault()
                .makeFromFile(
                    font.absolutePath
                )
                ?: error(
                    "O motor Skia não conseguiu abrir a fonte " +
                        font.displayName +
                        "."
                )

        try {
            val probe =
                Font(
                    typeface,
                    100f,
                    1f,
                    0f
                )
                    .setSubpixel(
                        true
                    )
                    .setMetricsLinear(
                        true
                    )
                    .setHinting(
                        FontHinting.NONE
                    )

            val capHeight =
                try {
                    val metrics =
                        probe.metrics

                    val reported =
                        metrics.capHeight

                    if (
                        reported >
                            0.001f
                    ) {
                        reported
                    } else {
                        abs(
                            metrics.ascent
                        ) *
                            0.72f
                    }
                } finally {
                    probe.close()
                }
                    .coerceAtLeast(
                        1f
                    )

            val resolvedSize =
                100f *
                    targetCapHeightUnits /
                    capHeight

            val skFont =
                Font(
                    typeface,
                    resolvedSize,
                    1f,
                    0f
                )
                    .setSubpixel(
                        true
                    )
                    .setMetricsLinear(
                        true
                    )
                    .setHinting(
                        FontHinting.NONE
                    )

            try {
                val rawGlyphs =
                    mutableListOf<
                        RawGlyph
                    >()

                var penX =
                    0f

                val codePoints =
                    text
                        .codePoints()
                        .toArray()

                codePoints
                    .forEachIndexed {
                            index,
                            codePoint ->
                        val glyph =
                            skFont
                                .getUTF32Glyph(
                                    codePoint
                                )

                        require(
                            glyph.toInt() !=
                                0
                        ) {
                            "A fonte " +
                                font.displayName +
                                " não possui o caractere U+" +
                                codePoint
                                    .toString(
                                        16
                                    )
                                    .uppercase() +
                                "."
                        }

                        val width =
                            skFont
                                .getWidths(
                                    shortArrayOf(
                                        glyph
                                    )
                                )
                                .firstOrNull()
                                ?: 0f

                        val path =
                            skFont
                                .getPath(
                                    glyph
                                )

                        if (
                            path !=
                                null
                        ) {
                            try {
                                val tight =
                                    path
                                        .computeTightBounds()

                                val contours =
                                    sampleContours(
                                        path =
                                            path,
                                        offsetX =
                                            penX
                                    )

                                if (
                                    contours.isNotEmpty()
                                ) {
                                    rawGlyphs +=
                                        RawGlyph(
                                            contours =
                                                contours,
                                            left =
                                                tight.left +
                                                    penX,
                                            top =
                                                tight.top,
                                            right =
                                                tight.right +
                                                    penX,
                                            bottom =
                                                tight.bottom
                                        )
                                }
                            } finally {
                                path.close()
                            }
                        }

                        penX +=
                            width

                        if (
                            index <
                                codePoints.lastIndex
                        ) {
                            penX +=
                                spacingUnits
                        }
                    }

                require(
                    rawGlyphs.isNotEmpty()
                ) {
                    "A fonte não gerou glifos vetoriais bordáveis."
                }

                val minX =
                    rawGlyphs.minOf {
                        it.left
                    }

                val maxX =
                    rawGlyphs.maxOf {
                        it.right
                    }

                val minY =
                    rawGlyphs.minOf {
                        it.top
                    }

                val maxY =
                    rawGlyphs.maxOf {
                        it.bottom
                    }

                val centerX =
                    (
                        minX +
                            maxX
                        ) /
                        2f

                val centerY =
                    (
                        minY +
                            maxY
                        ) /
                        2f

                val radians =
                    Math.toRadians(
                        rotationDegrees
                            .toDouble()
                    )

                val rotationCos =
                    cos(
                        radians
                    )
                        .toFloat()

                val rotationSin =
                    sin(
                        radians
                    )
                        .toFloat()

                val transformed =
                    rawGlyphs.map {
                            glyph ->
                        glyph.contours.map {
                                contour ->
                            contour.map {
                                point ->
                                val centeredX =
                                    point.x -
                                        centerX

                                val centeredY =
                                    point.y -
                                        centerY

                                Point(
                                    x =
                                        centeredX *
                                            rotationCos -
                                            centeredY *
                                                rotationSin,
                                    y =
                                        centeredX *
                                            rotationSin +
                                            centeredY *
                                                rotationCos
                                )
                            }
                        }
                    }

                return Result(
                    glyphPolygons =
                        transformed,
                    sourceWidth =
                        maxX -
                            minX,
                    sourceHeight =
                        maxY -
                            minY
                )
            } finally {
                skFont.close()
            }
        } finally {
            typeface.close()
        }
    }

    private fun sampleContours(
        path: io.github.humbleui.skija.Path,
        offsetX: Float
    ): List<List<Point>> {
        val measure =
            PathMeasure(
                path,
                true,
                1f
            )

        try {
            val contours =
                mutableListOf<
                    List<Point>
                >()

            do {
                val length =
                    measure.length

                if (
                    length <=
                        0f ||
                    !length.isFinite()
                ) {
                    continue
                }

                val sampleCount =
                    max(
                        8,
                        ceil(
                            length /
                                2f
                        ).toInt()
                    )

                val points =
                    ArrayList<Point>(
                        sampleCount
                    )

                for (
                    index in
                        0 until
                            sampleCount
                ) {
                    val distance =
                        length *
                            index /
                            sampleCount

                    val point =
                        measure.getPosition(
                            distance
                        )
                            ?: continue

                    points +=
                        Point(
                            x =
                                point.x +
                                    offsetX,
                            y =
                                point.y
                        )
                }

                if (
                    points.size >=
                        3
                ) {
                    contours +=
                        points
                }
            } while (
                measure.nextContour()
            )

            return contours
        } finally {
            measure.close()
        }
    }
}
