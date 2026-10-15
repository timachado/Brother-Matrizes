package com.timachado.brothermatrizes.font

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Evita múltiplas coberturas Satin sobre a MESMA parte dos glifos decorativos
 * importados. Não cria faixas, não altera suas coordenadas, largura, direção
 * ou densidade; somente descarta uma barra comprovadamente redundante.
 *
 * A máquina e o simulador continuam recebendo a sequência normal STITCH/JUMP
 * do Stitcher oficial. Uma barra descartada divide sua rota para evitar
 * conectar duas áreas anteriormente separadas.
 */
internal object ImportedGlyphRailCoverage {
    private const val MAX_CELLS = 350_000
    private const val OVERLAP_TO_SKIP = 0.82f

    fun suppressRedundant(
        paths: List<List<CleanRoomSatinEngine.Rail>>,
        densityMm: Float
    ): List<List<CleanRoomSatinEngine.Rail>> {
        // Letras simples com uma sequência Satin não devem mudar.
        if (paths.size < 4 || paths.sumOf { it.size } < 20) return paths
        val all = paths.flatten()
        val xs = all.flatMap { listOf(it.a.x, it.b.x) }
        val ys = all.flatMap { listOf(it.a.y, it.b.y) }
        val minX = xs.minOrNull() ?: return paths
        val maxX = xs.maxOrNull() ?: return paths
        val minY = ys.minOrNull() ?: return paths
        val maxY = ys.maxOrNull() ?: return paths
        if (!minX.isFinite() || !maxX.isFinite() ||
            !minY.isFinite() || !maxY.isFinite()) return paths
        val area = (maxX - minX + 6f) * (maxY - minY + 6f)
        if (!area.isFinite() || area <= 0f) return paths
        val step = max(0.8f, sqrt(area / MAX_CELLS))
        val width = ceil((maxX - minX) / step).toInt() + 7
        val height = ceil((maxY - minY) / step).toInt() + 7
        if (width.toLong() * height.toLong() > MAX_CELLS * 2L) return paths
        val left = minX - 3f * step
        val top = minY - 3f * step
        val covered = BooleanArray(width * height)
        val radius = (densityMm * 10f * 0.45f).coerceIn(1.2f, 3.0f)
        val pixels = ceil(radius / step).toInt()
        val offsets = mutableListOf<Pair<Int, Int>>()
        for (dy in -pixels..pixels) for (dx in -pixels..pixels) {
            if ((dx * dx + dy * dy) * step * step <= radius * radius) {
                offsets += dx to dy
            }
        }

        fun samples(rail: CleanRoomSatinEngine.Rail): List<Pair<Int, Int>> {
            val length = hypot(
                (rail.b.x - rail.a.x).toDouble(),
                (rail.b.y - rail.a.y).toDouble()
            ).toFloat()
            val count = ceil(length / (step * 0.8f)).toInt().coerceIn(2, 400)
            return (0..count).map { k ->
                val t = k.toFloat() / count
                val x = rail.a.x + (rail.b.x - rail.a.x) * t
                val y = rail.a.y + (rail.b.y - rail.a.y) * t
                ((x - left) / step).roundToInt() to ((y - top) / step).roundToInt()
            }.distinct()
        }

        val output = mutableListOf<List<CleanRoomSatinEngine.Rail>>()
        for (path in paths) {
            var active = mutableListOf<CleanRoomSatinEngine.Rail>()
            fun flush() {
                if (active.isNotEmpty()) output += active
                active = mutableListOf()
            }
            for (rail in path) {
                val points = samples(rail)
                val hit = points.count { (x, y) ->
                    x in 0 until width && y in 0 until height &&
                        covered[y * width + x]
                }
                val fraction = if (points.isEmpty()) 0f else hit.toFloat() / points.size
                val length = hypot(
                    (rail.b.x - rail.a.x).toDouble(),
                    (rail.b.y - rail.a.y).toDouble()
                ).toFloat()
                // Não descartar pinguinhos ou hastes finas/curtas.
                if (length >= 8f && fraction >= OVERLAP_TO_SKIP) {
                    flush()
                    continue
                }
                active += rail
                for ((x, y) in points) {
                    for ((dx, dy) in offsets) {
                        val ix = x + dx
                        val iy = y + dy
                        if (ix in 0 until width && iy in 0 until height) {
                            covered[iy * width + ix] = true
                        }
                    }
                }
            }
            flush()
        }
        return if (output.isEmpty()) paths else output
    }
}
