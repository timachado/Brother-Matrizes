package com.timachado.brothermatrizes.font

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Planejador experimental para preenchimento de regiões largas de fontes TTF/OTF.
 * Entrada: contornos preservando a orientação de seus anéis (non-zero winding).
 * Saída: comandos geométricos, sem acabamento de máquina ou mudança de cor.
 * NÃO deve ser usado em produção sem validação na máquina e nos exportadores.
 */
internal object ImportedGlyphTatamiPlanner {
    data class P(val x: Float, val y: Float)
    enum class Move { JUMP, STITCH }
    data class Needle(val point: P, val move: Move)
    private data class Crossing(val x: Float, val windingDelta: Int)
    private data class Strip(val id: Int, val row: Int, val left: P, val right: P)

    private fun length(a: P, b: P): Float =
        hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()

    /** Eventos de contorno com winding não-zero; preserva vazados reais. */
    internal fun scanlineSpans(contours: List<List<P>>, y: Float): List<Pair<Float, Float>> {
        val crossings = ArrayList<Crossing>()
        for (ring in contours) {
            if (ring.size < 3) continue
            for (i in ring.indices) {
                val a = ring[i]
                val b = ring[(i + 1) % ring.size]
                if ((a.y <= y && b.y > y) || (b.y <= y && a.y > y)) {
                    val x = a.x + (y - a.y) * (b.x - a.x) / (b.y - a.y)
                    if (x.isFinite()) crossings += Crossing(x, if (b.y > a.y) 1 else -1)
                }
            }
        }
        crossings.sortBy { it.x }
        val spans = ArrayList<Pair<Float, Float>>()
        var winding = 0
        var start: Float? = null
        var i = 0
        while (i < crossings.size) {
            val x = crossings[i].x
            var delta = 0
            while (i < crossings.size && crossings[i].x == x) {
                delta += crossings[i].windingDelta
                i++
            }
            val before = winding
            winding += delta
            if (before == 0 && winding != 0) start = x
            if (before != 0 && winding == 0) {
                val begin = start
                if (begin != null && x - begin > 1.2f) spans += begin to x
                start = null
            }
        }
        return spans
    }

    private fun inside(contours: List<List<P>>, p: P): Boolean {
        var winding = 0
        for (ring in contours) {
            for (i in ring.indices) {
                val a = ring[i]
                val b = ring[(i + 1) % ring.size]
                val above = a.y <= p.y && b.y > p.y
                val below = b.y <= p.y && a.y > p.y
                if (!above && !below) continue
                val crossingX = a.x + (p.y - a.y) * (b.x - a.x) / (b.y - a.y)
                if (crossingX > p.x) winding += if (above) 1 else -1
            }
        }
        return winding != 0
    }

    /** Nunca costurar conectores que atravessam vazios ou excedem a distância. */
    private fun safeConnector(contours: List<List<P>>, a: P, b: P, maxUnits: Float): Boolean {
        val distance = length(a, b)
        if (distance > maxUnits) return false
        val steps = max(2, ceil(distance / 0.75f).toInt())
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            if (!inside(contours, P(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))) return false
        }
        return true
    }

    /**
     * Apenas protótipo de geometria. Parâmetros em unidades de 0,1 mm.
     * Não gera underlay, tie-in, tie-off, trim nem END.
     */
    fun plan(
        contours: List<List<P>>,
        rowPitchUnits: Float = 3.75f,
        maxStitchUnits: Float = 27f
    ): List<Needle> {
        require(rowPitchUnits in 2.5f..12f)
        require(maxStitchUnits in 10f..40f)
        val rings = contours.filter { it.size >= 3 }
        if (rings.isEmpty()) return emptyList()
        require(rings.sumOf { it.size } <= 50_000) { "Glifo muito complexo" }
        val vertices = rings.flatten()
        require(vertices.all { it.x.isFinite() && it.y.isFinite() })
        val minY = vertices.minOf { it.y }
        val maxY = vertices.maxOf { it.y }
        val rows = ceil((maxY - minY) / rowPitchUnits).toInt()
        require(rows <= 450) { "Glifo excedeu o limite de linhas para preenchimento" }
        val connectorLimit = min(28f, maxStitchUnits)
        val segments = ArrayList<Strip>()
        val byRow = HashMap<Int, MutableList<Int>>()
        for (row in 0 until rows) {
            val y = minY + (row + 0.5f) * rowPitchUnits
            for ((x0, x1) in scanlineSpans(rings, y)) {
                val inset = min(0.55f, (x1 - x0) * 0.15f)
                val from = x0 + inset
                val to = x1 - inset
                if (to - from < 1.0f) continue
                val id = segments.size
                segments += Strip(id, row, P(from, y), P(to, y))
                byRow.getOrPut(row) { mutableListOf() } += id
            }
        }
        require(segments.size <= 4_000) { "Excesso de trajetórias para fonte importada" }
        val available = BooleanArray(segments.size) { true }
        var remaining = segments.size
        var current: P? = null
        var currentRow = 0
        val output = ArrayList<Needle>()
        while (remaining > 0) {
            var bestId = -1
            var bestReverse = false
            var bestDistance = Float.POSITIVE_INFINITY
            var connected = false
            if (current != null) {
                for (row in intArrayOf(currentRow - 1, currentRow + 1, currentRow - 2, currentRow + 2)) {
                    for (sid in byRow[row].orEmpty()) {
                        if (!available[sid]) continue
                        val strip = segments[sid]
                        for (reverse in booleanArrayOf(false, true)) {
                            val start = if (reverse) strip.right else strip.left
                            val delta = length(current, start)
                            if (delta < bestDistance && delta <= connectorLimit &&
                                safeConnector(rings, current, start, connectorLimit)) {
                                bestId = sid
                                bestReverse = reverse
                                bestDistance = delta
                                connected = true
                            }
                        }
                    }
                }
            }
            if (bestId == -1) {
                for (sid in segments.indices) {
                    if (!available[sid]) continue
                    val strip = segments[sid]
                    val reverse = current != null &&
                        length(current, strip.right) < length(current, strip.left)
                    val start = if (reverse) strip.right else strip.left
                    val delta = if (current == null) sid.toFloat() else length(current, start)
                    if (delta < bestDistance) {
                        bestId = sid
                        bestReverse = reverse
                        bestDistance = delta
                    }
                }
            }
            check(bestId >= 0)
            available[bestId] = false
            remaining--
            val strip = segments[bestId]
            val start = if (bestReverse) strip.right else strip.left
            val end = if (bestReverse) strip.left else strip.right
            output += Needle(start, if (connected) Move.STITCH else Move.JUMP)
            val steps = max(1, ceil(length(start, end) / maxStitchUnits).toInt())
            for (i in 1..steps) {
                val t = i.toFloat() / steps
                output += Needle(P(start.x + (end.x - start.x) * t, start.y), Move.STITCH)
            }
            current = end
            currentRow = strip.row
        }
        return output
    }
}
