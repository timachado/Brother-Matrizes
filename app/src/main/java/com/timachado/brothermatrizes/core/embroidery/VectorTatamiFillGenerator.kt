package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Rasteriza contornos SVG fechados em passadas Tatami horizontais.
 *
 * QA experimental: mesma cor para todos os polígonos, regra de preenchimento
 * non-zero (padrão SVG) e sem underlay automático. Preserve o contorno original.
 * As limitações e restrições aqui são intencionais para não gerar pontos inseguros.
 */
internal object VectorTatamiFillGenerator {
    private data class Edge(
        val minY: Int,
        val maxY: Int,
        val xAtMinY: Double,
        val dxPerY: Double,
        val winding: Int
    ) {
        fun crosses(y: Double): Boolean = y >= minY && y < maxY
        fun xAt(y: Double): Double = xAtMinY + dxPerY * (y - minY)
    }

    private data class Crossing(val x: Double, val winding: Int)
    private data class Span(val fromX: Int, val toX: Int)

    fun generate(
        contours: List<List<Pair<Int, Int>>>,
        rowSpacingUnits: Int,
        maxStitchLengthUnits: Int
    ): Result<List<EmbroideryPoint>> = runCatching {
        require(rowSpacingUnits in 3..9) {
            "Espaçamento Tatami fora do intervalo seguro de 0,3 a 0,9 mm."
        }
        require(maxStitchLengthUnits in 15..40) {
            "Comprimento Tatami fora do intervalo seguro de 1,5 a 4,0 mm."
        }
        require(contours.isNotEmpty() && contours.size <= 500) {
            "O preenchimento exige áreas fechadas com complexidade controlada."
        }
        require(contours.sumOf { it.size } <= 12_000) {
            "SVG complexo demais para preenchimento Tatami experimental."
        }

        val edges = mutableListOf<Edge>()
        val all = mutableListOf<Pair<Int, Int>>()
        contours.forEach { original ->
            val loop = original.dropLast(if (original.firstOrNull() == original.lastOrNull()) 1 else 0)
                .fold(mutableListOf<Pair<Int, Int>>()) { acc, point ->
                    if (acc.lastOrNull() != point) acc.add(point)
                    acc
                }
            require(loop.size >= 3) { "Área fechada com menos de três vértices." }

            // Área quase nula não é preenchível com segurança.
            val twiceArea = loop.indices.sumOf { i ->
                val a = loop[i]
                val b = loop[(i + 1) % loop.size]
                a.first.toLong() * b.second - b.first.toLong() * a.second
            }
            require(abs(twiceArea) >= 16L) {
                "Um caminho fechado possui área insuficiente para Tatami."
            }
            all.addAll(loop)

            loop.indices.forEach { i ->
                val from = loop[i]
                val to = loop[(i + 1) % loop.size]
                if (from.second == to.second) return@forEach
                val rising = from.second < to.second
                val lower = if (rising) from else to
                val upper = if (rising) to else from
                edges.add(
                    Edge(
                        minY = lower.second,
                        maxY = upper.second,
                        xAtMinY = lower.first.toDouble(),
                        dxPerY = (upper.first - lower.first).toDouble() /
                            (upper.second - lower.second),
                        winding = if (rising) 1 else -1
                    )
                )
            }
        }
        val minY = all.minOf { it.second }
        val maxY = all.maxOf { it.second }
        val minX = all.minOf { it.first }
        val maxX = all.maxOf { it.first }
        require(maxY - minY in 4..3_000 && maxX - minX in 4..3_000) {
            "Área Tatami fora dos limites de dimensão."
        }
        val rows = ceil((maxY - minY).toDouble() / rowSpacingUnits).toInt()
        require(rows in 1..1_000) { "Preenchimento Tatami com linhas demais." }
        require(edges.isNotEmpty()) { "Não há bordas utilizáveis para preenchimento." }

        // Entrada/saída dos polígonos por winding não nulo (regra SVG padrão).
        fun spansFor(y: Double): List<Span> {
            val crossings = edges.asSequence().filter { it.crosses(y) }
                .map { Crossing(it.xAt(y), it.winding) }
                .sortedBy { it.x }
                .toList()
            val spans = mutableListOf<Span>()
            var winding = 0
            var fromX = 0.0
            var cursor = 0
            while (cursor < crossings.size) {
                val x = crossings[cursor].x
                var delta = 0
                while (cursor < crossings.size && abs(crossings[cursor].x - x) < 1e-6) {
                    delta += crossings[cursor].winding
                    cursor++
                }
                val before = winding != 0
                winding += delta
                val after = winding != 0
                if (!before && after) fromX = x
                if (before && !after) {
                    // Margem interna de 0,1 mm evita ligar linhas exatamente
                    // sobre a borda, onde o teste de interior é ambíguo.
                    val left = ceil(fromX).toInt() + 1
                    val right = floor(x).toInt() - 1
                    if (right - left >= 2) spans.add(Span(left, right))
                }
            }
            require(winding == 0) { "Geometria SVG com preenchimento inconsistente." }
            return spans
        }

        fun inside(x: Double, y: Double): Boolean {
            var winding = 0
            edges.forEach { edge ->
                if (edge.crosses(y) && edge.xAt(y) > x) winding += edge.winding
            }
            return winding != 0
        }

        val stitches = mutableListOf<EmbroideryPoint>()
        var current: Pair<Int, Int>? = null
        var totalSegments = 0

        // Pontos intermediários no comprimento máximo; deslocamentos curtos
        // de uma linha para a próxima só costuram se permanecerem na área.
        fun appendSegment(from: Pair<Int, Int>, to: Pair<Int, Int>) {
            val distance = hypot(
                (to.first - from.first).toDouble(),
                (to.second - from.second).toDouble()
            )
            val steps = max(1, ceil(distance / maxStitchLengthUnits).toInt())
            for (part in 1..steps) {
                val ratio = part.toDouble() / steps
                val x = (from.first + (to.first - from.first) * ratio).roundToInt()
                val y = (from.second + (to.second - from.second) * ratio).roundToInt()
                if (stitches.lastOrNull()?.let {
                    it.xUnits == x && it.yUnits == y && it.command == StitchCommand.STITCH
                } == true) continue
                stitches.add(EmbroideryPoint(x, y, StitchCommand.STITCH, 0))
            }
            totalSegments += steps
            require(stitches.size <= 65_000) {
                "Preenchimento Tatami excedeu o limite de 65 mil comandos."
            }
        }

        for (row in 0 until rows) {
            val y = minY + (row + 0.5) * rowSpacingUnits
            if (y >= maxY) break
            val spans = spansFor(y)
            val scanY = y.roundToInt()
            val ordered = if (row % 2 == 0) spans else spans.asReversed()
            for (span in ordered) {
                val start = if (row % 2 == 0) span.fromX else span.toX
                val finish = if (row % 2 == 0) span.toX else span.fromX
                val first = start to scanY
                val last = finish to scanY
                if (first == last) continue

                if (current == null) {
                    stitches.add(EmbroideryPoint(first.first, first.second, StitchCommand.JUMP, 0))
                } else {
                    val previous = current
                    val distance = hypot(
                        (first.first - previous.first).toDouble(),
                        (first.second - previous.second).toDouble()
                    )
                    val connectable = distance <= max(12.0, rowSpacingUnits * 2.0) &&
                        (1..3).all { step ->
                            val t = step / 4.0
                            inside(
                                previous.first + (first.first - previous.first) * t,
                                previous.second + (first.second - previous.second) * t
                            )
                        }
                    if (connectable) {
                        appendSegment(previous, first)
                    } else {
                        // Jamais atravessar vazios ou furos com ponto de costura.
                        if (distance > 40.0) {
                            stitches.add(
                                EmbroideryPoint(previous.first, previous.second, StitchCommand.TRIM, 0)
                            )
                        }
                        stitches.add(
                            EmbroideryPoint(first.first, first.second, StitchCommand.JUMP, 0)
                        )
                    }
                }
                appendSegment(first, last)
                current = last
            }
        }
        require(stitches.count { it.command == StitchCommand.STITCH } >= 3) {
            "A área fechada é estreita demais para o Tatami configurado."
        }
        stitches
    }
}
