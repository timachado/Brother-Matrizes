package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Acabamento experimental dos contornos de áreas fechadas, sobre o Tatami.
 *
 * O modo é opt-in e não altera o gerador de preenchimento.
 * Os caminhos são percorridos com pontos corridos de até 2 mm e JUMP/TRIM
 * entre regiões; nenhum deslocamento entre áreas vira ponto de costura.
 */
internal object VectorContourFinishGenerator {
    fun generate(
        contours: List<List<Pair<Int, Int>>>,
        maxStitchLengthUnits: Int = 20
    ): Result<List<EmbroideryPoint>> = runCatching {
        require(maxStitchLengthUnits in 10..25) {
            "Comprimento dos pontos de acabamento fora do intervalo seguro."
        }
        require(contours.isNotEmpty() && contours.size <= 500) {
            "O acabamento exige contornos com complexidade controlada."
        }
        require(contours.sumOf { it.size } <= 12_000) {
            "SVG possui vértices demais para acabamento automático."
        }

        val points = mutableListOf<EmbroideryPoint>()
        val seenPaths = HashSet<String>()
        var previous: Pair<Int, Int>? = null

        contours.forEach { source ->
            val loop = source
                .dropLast(if (source.firstOrNull() == source.lastOrNull()) 1 else 0)
                .fold(mutableListOf<Pair<Int, Int>>()) { acc, next ->
                    if (acc.lastOrNull() != next) acc.add(next)
                    acc
                }
            // Um contorno tão pequeno não permite o acabamento em ponto corrido.
            if (loop.size < 3) return@forEach
            val minX = loop.minOf { it.first }
            val maxX = loop.maxOf { it.first }
            val minY = loop.minOf { it.second }
            val maxY = loop.maxOf { it.second }
            if (maxX - minX <= 5 && maxY - minY <= 5) return@forEach

            // Evita repetir um mesmo caminho (inclusive com início e sentido
            // diferentes), reduzindo sobrecostura nos SVGs com duplicatas.
            val canonical = canonicalPath(loop)
            if (!seenPaths.add(canonical)) return@forEach

            var at = loop.first()
            if (previous != null) {
                points.add(
                    EmbroideryPoint(previous!!.first, previous!!.second, StitchCommand.TRIM, 0)
                )
            }
            points.add(EmbroideryPoint(at.first, at.second, StitchCommand.JUMP, 0))
            for (next in loop.drop(1) + loop.first()) {
                val distance = hypot(
                    (next.first - at.first).toDouble(),
                    (next.second - at.second).toDouble()
                )
                val steps = max(1, ceil(distance / maxStitchLengthUnits).toInt())
                for (step in 1..steps) {
                    val fraction = step.toDouble() / steps
                    val x = (at.first + (next.first - at.first) * fraction).roundToInt()
                    val y = (at.second + (next.second - at.second) * fraction).roundToInt()
                    if (points.lastOrNull()?.let {
                        it.xUnits == x && it.yUnits == y
                    } == true) continue
                    points.add(EmbroideryPoint(x, y, StitchCommand.STITCH, 0))
                }
                at = next
                require(points.size <= 20_000) {
                    "Acabamento com pontos demais; simplifique o SVG."
                }
            }
            previous = at
        }

        require(points.count { it.command == StitchCommand.STITCH } > 0) {
            "Nenhum contorno tem dimensão suficiente para acabamento."
        }
        points
    }

    private fun canonicalPath(path: List<Pair<Int, Int>>): String {
        // A string é pequena após o limite de 12 mil vértices; o menor vértice
        // é o ponto de partida determinístico, em ambos os sentidos.
        val minIndex = path.indices.minWithOrNull(
            compareBy<Int>({ path[it].first }, { path[it].second })
        ) ?: 0
        fun signature(reverse: Boolean): String = buildString {
            for (step in path.indices) {
                val index = if (reverse) {
                    (minIndex - step + path.size) % path.size
                } else {
                    (minIndex + step) % path.size
                }
                val p = path[index]
                append(p.first).append(',').append(p.second).append(';')
            }
        }
        val forward = signature(false)
        val backward = signature(true)
        return minOf(forward, backward)
    }
}
