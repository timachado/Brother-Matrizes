package com.timachado.brothermatrizes.font

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Color
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
        val sourceHeight: Float,
        val contextualShapingPreserved: Boolean = false
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
        val independentlyShaped = Path()

        // O deslocamento deve considerar kerning e shaping do texto completo,
        // em vez de somar larguras isoladas (que separa fontes cursivas).
        var textOffset = 0

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

                // Uso apenas para comparação: o texto unido aplica formas
                // iniciais/mediais/finais e ligaduras que não existem se
                // chamarmos getTextPath para cada letra isoladamente.
                val isolated = Path()
                paint.getTextPath(
                    glyphText, 0, glyphText.length,
                    shapedX, 0f, isolated
                )
                independentlyShaped.addPath(isolated)

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

        require(
            rawGlyphs.isNotEmpty()
        ) {
            "A fonte não gerou glifos vetoriais bordáveis."
        }

        val completeTextPath = Path()
        paint.getTextPath(text, 0, text.length, 0f, 0f, completeTextPath)
        val meaningfulOverlap = if (rawGlyphs.size < 2) false else {
            // Algumas cursivas são conectadas por sobreposição de
            // contornos, mesmo sem substituições OpenType. Costurar cada
            // glifo isoladamente pode duplicar a junção e empelotar.
            val overlaps = rawGlyphs.zipWithNext().count { (a, b) ->
                (a.right - b.left) >= targetCapHeightUnits * 0.03f
            }
            overlaps >= max(1, (rawGlyphs.size - 1) / 3)
        }
        val contextualShaping = codePoints.size > 1 &&
            !completeTextPath.isEmpty &&
            (glyphShapeDiffers(completeTextPath, independentlyShaped) ||
                meaningfulOverlap)

        // Fontes manuscritas (ex.: Bertilda) mudam o glifo conforme o
        // contexto. Dividir a palavra em caracteres isolados gera
        // curvas equivocadas, letras coladas e Satin ilegível.
        // Em modo contextual, o espaço é distribuído por escala leve
        // de tracking, SEM separar fisicamente as ligações cursivas.
        val glyphsForStitch = if (contextualShaping) {
            val wholeBounds = RectF()
            completeTextPath.computeBounds(wholeBounds, true)
            val originalWidth = wholeBounds.width().coerceAtLeast(1f)
            val extraWidth = spacingUnits * (codePoints.size - 1)
            val scaleX = ((originalWidth + extraWidth) / originalWidth)
                .coerceIn(0.7f, 2.0f)
            val transform = Matrix().apply {
                postScale(scaleX, 1f, wholeBounds.left, wholeBounds.centerY())
            }
            val shaped = Path().also { completeTextPath.transform(transform, it) }
            val bounds = RectF().also { shaped.computeBounds(it, true) }
            listOf(
                RawGlyph(
                    contours = sampleContours(shaped),
                    left = bounds.left, right = bounds.right,
                    top = bounds.top, bottom = bounds.bottom
                )
            )
        } else {
            rawGlyphs
        }

        val minX =
            glyphsForStitch.minOf {
                it.left
            }

        val maxX =
            glyphsForStitch.maxOf {
                it.right
            }

        val minY =
            glyphsForStitch.minOf {
                it.top
            }

        val maxY =
            glyphsForStitch.maxOf {
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
            glyphsForStitch.map {
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
                    minY,
            contextualShapingPreserved = contextualShaping
        )
    }

    /**
     * Compara a SILHUETA do texto formado como palavra com a soma das
     * letras isoladas. Pequenas diferenças por hinting não importam; uma
     * diferença de >=4% da área indica shaping contextual/ligadura.
     * Amostragem limitada a 240×96 pixels; fonte nunca é serializada.
     */
    internal fun glyphShapeDiffers(whole: Path, isolated: Path): Boolean {
        val wb = RectF().also { whole.computeBounds(it, true) }
        val ib = RectF().also { isolated.computeBounds(it, true) }
        val bounds = RectF(wb).also { it.union(ib) }
        if (bounds.width() <= .01f || bounds.height() <= .01f) return false
        val width = 240
        val height = 96
        val matrix = Matrix().apply {
            postTranslate(-bounds.left, -bounds.top)
            postScale(
                (width - 4f) / bounds.width(),
                (height - 4f) / bounds.height()
            )
            postTranslate(2f, 2f)
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        try {
            fun pixels(path: Path): ByteArray {
                bitmap.eraseColor(Color.TRANSPARENT)
                val shaped = Path().also { path.transform(matrix, it) }
                Canvas(bitmap).drawPath(shaped, Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                    isAntiAlias = false
                })
                val buffer = java.nio.ByteBuffer.allocate(width * height)
                bitmap.copyPixelsToBuffer(buffer)
                return buffer.array()
            }
            val full = pixels(whole)
            val parts = pixels(isolated)
            var different = 0
            var covered = 0
            for (i in full.indices) {
                val f = full[i].toInt() and 0xff
                val p = parts[i].toInt() and 0xff
                if (f != 0 || p != 0) covered++
                if ((f != 0) != (p != 0)) different++
            }
            return covered > 0 && different.toFloat() / covered >= .04f
        } finally {
            bitmap.recycle()
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
