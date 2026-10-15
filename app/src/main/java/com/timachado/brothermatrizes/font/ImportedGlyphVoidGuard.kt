package com.timachado.brothermatrizes.font

import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Validação da matriz produzida pelas fontes TTF/OTF, ANTES da simulação.
 *
 * O Satin e o simulador continuam inalterados. Somente um STITCH que costura
 * através de um vazio efetivo do contorno original pode virar JUMP.
 * Não insere pontos, não altera posições nem reconstrói preenchimentos.
 */
internal object ImportedGlyphVoidGuard {
    private const val MAX_CELLS = 360_000
    private const val MAX_SAMPLES_PER_SEGMENT = 500

    internal fun preventVoidConnections(
        points: MutableList<EmbroideryPoint>,
        fromIndex: Int,
        contours: List<List<CleanRoomSatinEngine.V>>
    ): Int {
        if (contours.isEmpty() || fromIndex < 0 || fromIndex >= points.size) return 0
        val mask = originalFilledMask(contours) ?: return 0
        var corrected = 0
        for (i in max(1, fromIndex) until points.size) {
            val next = points[i]
            if (next.command != StitchCommand.STITCH) continue
            val prev = points[i - 1]
            if (prev.command == StitchCommand.END || prev.command == StitchCommand.TRIM) continue
            if (mask.crossesVoid(prev.xUnits.toFloat(), prev.yUnits.toFloat(),
                    next.xUnits.toFloat(), next.yUnits.toFloat())) {
                points[i] = next.copy(command = StitchCommand.JUMP)
                corrected++
            }
        }
        return corrected
    }

    private class FilledMask(
        val width: Int, val height: Int, val step: Float,
        val left: Float, val top: Float, val inside: BooleanArray
    ) {
        fun occupied(x: Float, y: Float): Boolean {
            val ix = ((x - left) / step).roundToInt()
            val iy = ((y - top) / step).roundToInt()
            return ix in 0 until width && iy in 0 until height &&
                inside[iy * width + ix]
        }

        private fun nearOriginalInk(x: Float, y: Float): Boolean {
            if (occupied(x, y)) return true
            // Antialias, hastes subpixel e compensação de repuxo não podem
            // transformar uma costura genuína em salto por erro de raster.
            for (dy in -2..2) for (dx in -2..2) {
                if (occupied(x + dx * step, y + dy * step)) return true
            }
            return false
        }

        fun crossesVoid(ax: Float, ay: Float, bx: Float, by: Float): Boolean {
            val distance = hypot((bx - ax).toDouble(), (by - ay).toDouble()).toFloat()
            // A tolerância preserva repuxo, bordas e transições pequenas do
            // Satin existente; só agir em vazados fisicamente relevantes.
            if (distance < 13f) return false
            // Só classificar como ligação indevida se os DOIS lados estão
            // associados a áreas bordáveis da fonte. Evita perder hastes
            // finas cuja máscara caiu abaixo da resolução da grade.
            if (!nearOriginalInk(ax, ay) || !nearOriginalInk(bx, by)) return false
            val start = 3f
            val end = distance - 3f
            val count = ceil((end - start) / step).toInt()
                .coerceIn(1, MAX_SAMPLES_PER_SEGMENT)
            var emptyRun = 0
            val needed = ceil(6f / step).toInt()
            for (k in 0..count) {
                val d = start + (end - start) * k / count
                val t = d / distance
                if (occupied(ax + (bx - ax) * t, ay + (by - ay) * t)) {
                    emptyRun = 0
                } else {
                    emptyRun++
                    if (emptyRun >= needed) return true
                }
            }
            return false
        }
    }

    /**
     * Raster somente de segurança, a partir do winding non-zero da fonte.
     * Ao contrário do raster para esqueletização, não une amostras acima e
     * abaixo da linha: isso poderia fechar os recortes decorativos.
     */
    private fun originalFilledMask(
        contours: List<List<CleanRoomSatinEngine.V>>
    ): FilledMask? {
        val paths = contours.filter { it.size >= 3 }
        if (paths.isEmpty()) return null
        val all = paths.flatten()
        val minX = all.minOf { it.x }
        val maxX = all.maxOf { it.x }
        val minY = all.minOf { it.y }
        val maxY = all.maxOf { it.y }
        if (!minX.isFinite() || !maxX.isFinite() ||
            !minY.isFinite() || !maxY.isFinite()) return null
        val area = (maxX - minX + 6f) * (maxY - minY + 6f)
        if (!area.isFinite() || area <= 0f) return null
        val step = max(0.85f, kotlin.math.sqrt(area / MAX_CELLS))
        val width = ceil((maxX - minX) / step).toInt() + 7
        val height = ceil((maxY - minY) / step).toInt() + 7
        if (width.toLong() * height > MAX_CELLS * 2L) return null
        val left = minX - 3f * step
        val top = minY - 3f * step
        val mask = BooleanArray(width * height)
        for (y in 1 until height - 1) {
            val yy = top + y * step
            val intersections = mutableListOf<Pair<Float, Int>>()
            for (path in paths) {
                for (i in path.indices) {
                    val a = path[i]
                    val b = path[(i + 1) % path.size]
                    if ((a.y <= yy && b.y > yy) || (b.y <= yy && a.y > yy)) {
                        intersections += (a.x + (yy - a.y) * (b.x - a.x) / (b.y - a.y)) to
                            (if (b.y > a.y) 1 else -1)
                    }
                }
            }
            intersections.sortBy { it.first }
            var winding = 0
            for (i in 0 until intersections.lastIndex) {
                winding += intersections[i].second
                if (winding == 0) continue
                val x0 = intersections[i].first
                val x1 = intersections[i + 1].first
                val startX = max(0, ceil((x0 - left) / step).toInt())
                val endX = minOf(width - 1, floor((x1 - left) / step).toInt())
                for (x in startX..endX) mask[y * width + x] = true
            }
        }
        return FilledMask(width, height, step, left, top, mask)
    }
}
