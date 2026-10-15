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

        val codePoints = text.codePoints().toArray()
        // A simulação e a exportação devem receber a mesma silhueta que
        // Paint desenha na prévia: UM run OpenType, sem particionar anéis
        // (inclusive conexões entre letras e contraformas) por avanço X.
        // O caminho individual original continua disponível quando existe
        // espaçamento manual ou a fonte não fornece um path completo.
        val preparedGlyphs = (
            if (spacingUnits == 0f && codePoints.size in 2..24) {
                extractCompleteRun(paint, text)
            } else null
        )?.takeIf { it.isNotEmpty() } ?: run {
            val rawGlyphs = mutableListOf<RawGlyph>()
            var textOffset = 0
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

                val shapedX = paint.getRunAdvance(
                    text,
                    0,
                    text.length,
                    0,
                    text.length,
                    false,
                    textOffset
                )
                val glyphX = shapedX + index * spacingUnits

                val path =
                    Path()

                paint.getTextPath(
                    glyphText,
                    0,
                    glyphText.length,
                    glyphX,
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

                textOffset += glyphText.length
            }

            rawGlyphs
        }

        require(
            preparedGlyphs.isNotEmpty()
        ) {
            "A fonte não gerou glifos vetoriais bordáveis."
        }

        val minX =
            preparedGlyphs.minOf {
                it.left
            }

        val maxX =
            preparedGlyphs.maxOf {
                it.right
            }

        val minY =
            preparedGlyphs.minOf {
                it.top
            }

        val maxY =
            preparedGlyphs.maxOf {
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
            preparedGlyphs.map {
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

    /**
     * A topologia da fonte é definida pelo conjunto de todos os contornos
     * do texto renderizado. Um anel pode cruzar a área de várias letras;
     * atribuí-lo ao centro da letra mais próxima altera o winding e causa
     * sobreposição/vazados espúrios no raster antes do Satin.
     *
     * Entregar um único grupo de contornos preserva a geometria exata do
     * run. O motor e a ordem do algoritmo de pontadas NÃO são modificados.
     */
    private fun extractCompleteRun(
        paint: Paint,
        text: String
    ): List<RawGlyph>? {
        val path = Path()
        paint.getTextPath(text, 0, text.length, 0f, 0f, path)
        if (path.isEmpty) return null
        val contours = sampleContours(path)
        val groups = ImportedOpenTypeContourGroups.completeRun(contours)
        if (groups.isEmpty()) return null

        return groups.map { rings ->
            val points = rings.flatten()
            RawGlyph(
                contours = rings,
                left = points.minOf { it.x },
                top = points.minOf { it.y },
                right = points.maxOf { it.x },
                bottom = points.maxOf { it.y }
            )
        }
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
                // Contornos importados: refinar curvas sem alterar o motor Satin.
                // Limite impede alocação descontrolada em fontes ornamentais.
                val sampleCount =
                    max(
                        8,
                        ceil(length / 0.85f).toInt().coerceAtMost(12_000)
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
