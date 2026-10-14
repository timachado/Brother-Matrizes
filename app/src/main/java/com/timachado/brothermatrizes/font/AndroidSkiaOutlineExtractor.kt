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
        rotationDegrees: Float,
        preferWholeRun: Boolean = true,
        featureTag: String? = null,
        positionedStyle: OpenTypePositionedStyle = OpenTypePositionedStyle()
    ): Result {
        require(featureTag == null || !positionedStyle.isEnabled) {
            "Escolha estilo global ou floreios individuais, não ambos."
        }
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
                // Android 26+; shaping tipográfico respeita recursos GSUB
                // declarados somente quando selecionados pelo usuário.
                fontFeatureSettings = OpenTypeStyleChoices.fontSettings(featureTag)
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

        val rawGlyphs = mutableListOf<RawGlyph>()

        val codePoints = text.codePoints().toArray()
        codePoints.forEach { codePoint ->
            val glyphText = String(Character.toChars(codePoint))
            if (Character.isWhitespace(codePoint)) return@forEach
            require(paint.hasGlyph(glyphText)) {
                "A fonte " + font.displayName + " não possui o caractere U+" +
                    codePoint.toString(16).uppercase() + "."
            }
        }

        // Skia substitui glifos/ligaturas e alterna desenhos dependendo da
        // sequência inteira. Pintar cada letra isolada (antigo comportamento)
        // perdia essas substituições apesar de posicionar corretamente pelo
        // kerning. Para palavras curtas sem espaçamento manual, extrair a
        // curva da mesma sequência completa exibida no preview Android.
        //
        // Limitar a extensão geométrica protege o digitizador Satin de
        // consumo excessivo em frases grandes: para estas permanece a
        // estratégia de glifos individuais já usada na versão comercial.
        val useCompleteRun = preferWholeRun && spacingUnits == 0f &&
            OpenTypeRunSafety.allowsShaping(codePoints.size) &&
            (positionedStyle.isEnabled ||
                OpenTypeRunSafety.allowsAdvance(
                    paint.measureText(text), targetCapHeightUnits
                ))

        // Para alternativas contextuais, extrair letra a letra descartaria o
        // estilo escolhido; informar a limitação em vez de gerar um PES falso.
        require((featureTag == null && !positionedStyle.isEnabled) || useCompleteRun) {
            "Este estilo exige texto curto e espaçamento zero no modo Satin."
        }

        if (useCompleteRun) {
            val whole = if (positionedStyle.isEnabled) {
                OpenTypePositionedPath.compose(paint, text, positionedStyle).path
            } else Path().apply {
                paint.getTextPath(text, 0, text.length, 0f, 0f, this)
            }
            if (!whole.isEmpty) {
                val bounds = RectF()
                whole.computeBounds(bounds, true)
                val contours = sampleContours(whole)
                if (contours.isNotEmpty() && bounds.width() > 0f) {
                    rawGlyphs += RawGlyph(
                        contours = contours,
                        left = bounds.left,
                        top = bounds.top,
                        right = bounds.right,
                        bottom = bounds.bottom
                    )
                }
            }
        }

        require((featureTag == null && !positionedStyle.isEnabled) ||
            rawGlyphs.isNotEmpty()) {
            "Não foi possível digitalizar o contorno inteiro com o estilo escolhido."
        }

        if (rawGlyphs.isEmpty()) {
            var textOffset = 0
            codePoints.forEachIndexed { index, codePoint ->
                val glyphText = String(Character.toChars(codePoint))
                val shapedX = paint.getRunAdvance(
                    text, 0, text.length, 0, text.length, false, textOffset
                )
                val glyphX = shapedX + index * spacingUnits
                val path = Path()
                paint.getTextPath(glyphText, 0, glyphText.length, glyphX, 0f, path)
                if (!path.isEmpty) {
                    val bounds = RectF()
                    path.computeBounds(bounds, true)
                    val contours = sampleContours(path)
                    if (contours.isNotEmpty()) {
                        rawGlyphs += RawGlyph(
                            contours = contours,
                            left = bounds.left,
                            top = bounds.top,
                            right = bounds.right,
                            bottom = bounds.bottom
                        )
                    }
                }
                textOffset += glyphText.length
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
                // Refinar SOMENTE o contorno TTF/OTF de entrada:
                // o Stitcher, as faixas Satin, os saltos e a simulação
                // continuam exatamente no percurso original.
                val sampleCount =
                    ImportedGlyphContourSampling.sampleCount(length)

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
