package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Diagnostico (nao destrutivo) das pontadas GERADAS de fontes Satin.
 *
 * Unidades EmbroideryPoint: 1 unidade = 0,1 mm. Uma distancia curta
 * isolada nao e defeito: pontadas de travamento e de underlay sao legitimas.
 * Em particular NAO remover segmentos de 0,3 a 0,4 mm automaticamente,
 * porque essa distancia pode ser o passo correto do preenchimento Satin.
 */
object SatinDensityAnalyzer {
    enum class Status { NORMAL, ATENCAO, SOBREPOSICAO_ELEVADA }

    data class Report(
        val status: Status,
        val originalPointCount: Int,
        val preservedPointCount: Int,
        val stitchCount: Int,
        val analyzedSegments: Int,
        val meanStitchLengthMm: Float,
        val shortSegmentCount: Int,
        val repeatedSegmentCount: Int,
        val longSegmentCount: Int,
        val maxRepeatedInFiveMmCell: Int
    ) {
        val repeatedPercent: Float
            get() = if (analyzedSegments == 0) 0f else
                100f * repeatedSegmentCount / analyzedSegments
    }

    private data class PointKey(val x: Int, val y: Int)
    private data class SegmentKey(
        val colorIndex: Int,
        val from: PointKey,
        val to: PointKey
    )

    fun analyze(points: List<EmbroideryPoint>): Report {
        val observedSegments = HashSet<SegmentKey>()
        val overlapCells = HashMap<Pair<Int, Int>, Int>()
        var previous: EmbroideryPoint? = null
        var stitches = 0
        var segments = 0
        var shortSegments = 0
        var repeated = 0
        var longSegments = 0
        var lengthsUnits = 0.0
        var maxRepeatedInCell = 0

        points.forEach { point ->
            when (point.command) {
                StitchCommand.STITCH -> {
                    stitches++
                    val from = previous
                    if (from != null) {
                        val dx = (point.xUnits.toLong() - from.xUnits).toDouble()
                        val dy = (point.yUnits.toLong() - from.yUnits).toDouble()
                        val lengthUnits = hypot(dx, dy)
                        segments++
                        lengthsUnits += lengthUnits
                        if (lengthUnits < 3.5) shortSegments++
                        if (lengthUnits > 80.0) longSegments++

                        // Somente percursos geometricamente quase iguais
                        // sao comparados, com tolerancia de 0,1 a 0,2 mm.
                        // Colunas Satin validas com passo de 0,4 mm continuam
                        // distintas. Direcao invertida conta como repeticao.
                        if (lengthUnits >= 4.0) {
                            val a = PointKey(
                                (from.xUnits / 2.0).roundToInt(),
                                (from.yUnits / 2.0).roundToInt()
                            )
                            val b = PointKey(
                                (point.xUnits / 2.0).roundToInt(),
                                (point.yUnits / 2.0).roundToInt()
                            )
                            val forward = a.x < b.x || (a.x == b.x && a.y <= b.y)
                            val key = if (forward) SegmentKey(
                                point.colorIndex, a, b
                            ) else SegmentKey(point.colorIndex, b, a)
                            if (!observedSegments.add(key)) {
                                repeated++
                                val cell = (
                                    ((from.xUnits.toLong() + point.xUnits) / 2L) / 50L
                                ).toInt() to (
                                    ((from.yUnits.toLong() + point.yUnits) / 2L) / 50L
                                ).toInt()
                                val count = (overlapCells[cell] ?: 0) + 1
                                overlapCells[cell] = count
                                if (count > maxRepeatedInCell) {
                                    maxRepeatedInCell = count
                                }
                            }
                        }
                    }
                    previous = point
                }
                StitchCommand.END -> previous = null
                else -> {
                    // O destino de JUMP / TRIM / troca de cor e
                    // a posicao conhecida da agulha para o proximo STITCH.
                    // Nao considerar o deslocamento sem costura na media.
                    previous = point
                }
            }
        }
        val percent = if (segments == 0) 0f else repeated.toFloat() / segments
        val status = when {
            repeated >= 30 && (percent >= 0.12f || maxRepeatedInCell >= 18) ->
                Status.SOBREPOSICAO_ELEVADA
            repeated >= 8 && (percent >= 0.05f || maxRepeatedInCell >= 6) ->
                Status.ATENCAO
            // Indicador de revisao, NAO criterio para descartar pontos.
            shortSegments >= 100 && shortSegments >= segments * 0.35f ->
                Status.ATENCAO
            else -> Status.NORMAL
        }
        return Report(
            status = status,
            originalPointCount = points.size,
            preservedPointCount = points.size,
            stitchCount = stitches,
            analyzedSegments = segments,
            meanStitchLengthMm =
                if (segments == 0) 0f else (lengthsUnits / segments / 10.0).toFloat(),
            shortSegmentCount = shortSegments,
            repeatedSegmentCount = repeated,
            longSegmentCount = longSegments,
            maxRepeatedInFiveMmCell = maxRepeatedInCell
        )
    }
}
