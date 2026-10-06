package com.timachado.brothermatrizes.font

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Extração clean-room do contorno TTF/OTF usando o backend Skia nativo
 * exposto pelo Android (android.graphics).
 *
 * Não usa Skija/HumbleUI: essas classes são voltadas ao runtime JVM e podem
 * não existir corretamente dentro do APK Android.
 *
 * O contrato entregue ao motor Satin permanece o mesmo:
 * cap-height -> glyph path/advance -> bounds -> transformação global ->
 * amostragem de cada contorno.
 */
internal object AndroidSkiaOutlineExtractor {

    data class Point(
        val x: Float,
        val y: Float
    )

    data class Result(
        val glyphPolygons: List<List<List<Point>>>,
        val sourceWidth: Float,
        val sourceHeight: Float
    )

    private data class RawGlyph(
        val contours: List<List<Point>>,
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
        val fontFile =
            File(
                font.absolutePath
            )

        require(
            fontFile.isFile
        ) {
            "O arquivo da fonte não está disponível."
        }

        val typeface =
            runCatching {
                Typeface.createFromFile(
                    fontFile
                )
            }.getOrElse {
                throw IllegalArgumentException(
                    "O Android não conseguiu abrir a fonte " +
                        font.displayName +
                        ".",
                    it
                )
            }

        val paint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                this.typeface =
                    typeface
                isSubpixelText =
                    true
                hinting =
                    Paint.HINTING_OFF
            }

        paint.textSize =
            100f

        val capHeight =
            measuredCapHeight(
                paint
            ).coerceAtLeast(
                1f
            )

        val resolvedSize =
            100f *
                targetCapHeightUnits /
                capHeight

        paint.textSize =
            resolvedSize

        val rawGlyphs =
            mutableListOf<RawGlyph>()

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
                val glyphText =
                    String(
                        Character.toChars(
                            codePoint
                        )
                    )

                require(
                    paint.hasGlyph(
                        glyphText
                    )
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

                val path =
                    Path()

                paint.getTextPath(
                    glyphText,
                    0,
                    glyphText.length,
                    penX,
                    0f,
                    path
                )

                if (
                    !path.isEmpty
                ) {
                    val tight =
                        RectF()

                    path.computeBounds(
                        tight,
                        true
                    )

                    val contours =
                        sampleContours(
                            path
                        )

                    if (
                        contours.isNotEmpty()
                    ) {
                        rawGlyphs +=
                            RawGlyph(
                                contours =
                                    contours,
                                left =
                                    tight.left,
                                top =
                                    tight.top,
                                right =
                                    tight.right,
                                bottom =
                                    tight.bottom
                            )
                    }
                }

                penX +=
                    paint.measureText(
                        glyphText
                    )

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
    }

    private fun measuredCapHeight(
        paint: Paint
    ): Float {
        val capPath =
            Path()

        paint.getTextPath(
            "H",
            0,
            1,
            0f,
            0f,
            capPath
        )

        if (
            !capPath.isEmpty
        ) {
            val bounds =
                RectF()

            capPath.computeBounds(
                bounds,
                true
            )

            if (
                bounds.height() >
                    0.001f
            ) {
                return bounds.height()
            }
        }

        return abs(
            paint.fontMetrics.ascent
        ) *
            0.72f
    }

    private fun sampleContours(
        path: Path
    ): List<List<Point>> {
        val measure =
            PathMeasure(
                path,
                true
            )

        val contours =
            mutableListOf<List<Point>>()

        do {
            val length =
                measure.length

            if (
                length >
                    0f &&
                length.isFinite()
            ) {
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

                val position =
                    FloatArray(
                        2
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

                    if (
                        measure.getPosTan(
                            distance,
                            position,
                            null
                        )
                    ) {
                        points +=
                            Point(
                                x =
                                    position[0],
                                y =
                                    position[1]
                            )
                    }
                }

                if (
                    points.size >=
                        3
                ) {
                    contours +=
                        points
                }
            }
        } while (
            measure.nextContour()
        )

        return contours
    }
}
