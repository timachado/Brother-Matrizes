package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Conservative automatic proposal for *multiple editable Satin sections*.
 *
 * A Zhang-Suen medial-axis skeleton supplies local stitch direction instead of
 * assuming all letters are horizontal/vertical. Branch intersections are cut:
 * sections use independent TRIM/JUMP, never a sewn shortcut across a counter.
 * Closed loops are split into four arcs, NOT bridged across their hole.
 *
 * The output is only a suggested manual draft: callers must require a human
 * touch and RasterSatinObjectEditor.preview validation before simulation.
 * Neither this class nor its output creates machine/exportable PES.
 */
object RasterSatinTopologyPlanner {
    private const val MAX_AREA = 90_000
    private const val MAX_SECTIONS = 24
    private const val MAX_ITERATIONS = 180
    private const val MAX_POINTS_PER_SECTION = 96

    fun propose(
        source: RasterImagePixels,
        item: RasterSatinObject,
        widthMm: Float
    ): Result<List<ManualSatinSection>> = runCatching {
        val m = item.mask
        val w = m.maxX - m.minX + 1
        val h = m.maxY - m.minY + 1
        require(source.width in 8..512 && source.height in 8..512 &&
            source.argb.size == item.mask.pixels.size &&
            w >= 7 && h >= 7 && w * h <= MAX_AREA &&
            widthMm.isFinite() && widthMm in 10f..200f) {
            "Imagem grande ou pequena demais para decomposição automática."
        }
        // Refuse ambiguous diagonal edge contacts. Never silently invent topology.
        RasterVectorContours.trace(m, source.width).getOrThrow()
        val original = BooleanArray(w * h) { i ->
            m.pixels[(m.minY + i / w) * source.width + m.minX + i % w] == 0
        }
        require(original.count { it } == item.pixelCount && item.pixelCount >= 24) {
            "Máscara de bordado inconsistente."
        }
        val thin = original.copyOf()
        fun has(x: Int, y: Int) =
            x in 0 until w && y in 0 until h && thin[y * w + x]
        fun ring(x: Int, y: Int) = booleanArrayOf(
            has(x, y - 1), has(x + 1, y - 1), has(x + 1, y),
            has(x + 1, y + 1), has(x, y + 1), has(x - 1, y + 1),
            has(x - 1, y), has(x - 1, y - 1)
        )
        // Two-pass Zhang-Suen thinning; original remains immutable for rails.
        var rounds = 0
        var changed: Boolean
        do {
            changed = false
            for (pass in 0..1) {
                val drop = ArrayList<Int>()
                for (y in 1 until h - 1) for (x in 1 until w - 1) {
                    if (!has(x, y)) continue
                    val n = ring(x, y)
                    val neighbors = n.count { it }
                    if (neighbors !in 2..6) continue
                    var transitions = 0
                    for (k in 0..7) if (!n[k] && n[(k + 1) % 8]) transitions++
                    if (transitions != 1) continue
                    val ok = if (pass == 0) {
                        !(n[0] && n[2] && n[4]) && !(n[2] && n[4] && n[6])
                    } else {
                        !(n[0] && n[2] && n[6]) && !(n[0] && n[4] && n[6])
                    }
                    if (ok) drop += y * w + x
                }
                for (index in drop) thin[index] = false
                if (drop.isNotEmpty()) changed = true
            }
            rounds++
        } while (changed && rounds < MAX_ITERATIONS)
        require(!changed) {
            "Traço excessivamente espesso para análise automática."
        }
        require(thin.count { it } >= 8) { "Não foi encontrado eixo de bordado." }

        // Remove diagonal graph links that form redundant triangles at corners.
        // This limits false junctions in a stair-step digital medial axis.
        fun neighbors(index: Int): List<Int> {
            val x = index % w
            val y = index / w
            val out = ArrayList<Int>(8)
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx; val ny = y + dy
                if (!has(nx, ny)) continue
                if (dx != 0 && dy != 0 &&
                    (has(x + dx, y) || has(x, y + dy))) continue
                out += ny * w + nx
            }
            return out.sorted()
        }
        val sk = thin.indices.filter { thin[it] }
        val degree = IntArray(w * h)
        for (at in sk) degree[at] = neighbors(at).size
        val junction = BooleanArray(w * h) { thin[it] && degree[it] >= 3 }
        val keep = BooleanArray(w * h) { thin[it] && !junction[it] }
        fun usableN(index: Int): List<Int> = neighbors(index).filter { keep[it] }
        val seen = HashSet<Long>()
        fun edgeKey(a: Int, b: Int): Long {
            val low = min(a,b); val high = max(a,b)
            return (low.toLong() shl 19) or high.toLong()
        }
        fun seenEdge(a: Int,b: Int) = edgeKey(a,b) in seen
        fun follow(start: Int, next: Int): List<Int> {
            val path = ArrayList<Int>()
            path += start
            var previous = start
            var at = next
            var counter = 0
            while (true) {
                seen += edgeKey(previous, at)
                if (at == start) break // Closed cycle.
                path += at
                if (++counter > sk.size) error("Ciclo inválido no esqueleto.")
                val choices = usableN(at).filter { it != previous && !seenEdge(at,it) }
                if (choices.size != 1) break
                previous = at
                at = choices.first()
            }
            return path
        }
        val paths = ArrayList<Pair<List<Int>, Boolean>>()
        // All open endpoints first; remaining unvisited edges form cycles.
        for (at in sk) if (keep[at] && usableN(at).size == 1) {
            val next = usableN(at).first()
            if (!seenEdge(at,next)) paths += follow(at,next) to false
        }
        for (at in sk) if (keep[at]) {
            for (next in usableN(at)) {
                if (seenEdge(at,next)) continue
                val path = follow(at,next)
                val closed = path.size >= 4 && at in usableN(path.last())
                paths += path to closed
            }
        }
        require(paths.isNotEmpty() && paths.size <= MAX_SECTIONS) {
            "Muitas bifurcações; divida a arte em partes menores."
        }
        // An unvisited isolated skeleton pixel is a missing branch, not a
        // reason to pretend the path covers the full source.
        require(sk.filter { keep[it] && usableN(it).isEmpty() }.size <= 2) {
            "A letra contém pequenos traços isolados: correção manual necessária."
        }

        val mmPerPixel = widthMm / source.width
        fun ink(x: Double, y: Double): Boolean {
            val px = floor(x).toInt()
            val py = floor(y).toInt()
            return x >= 0.0 && y >= 0.0 && px in 0 until w && py in 0 until h &&
                original[py * w + px]
        }
        // Probe only the connected ink width around the skeleton; never jump
        // over a transparent hole to reach another portion of the letter.
        fun reach(x: Double, y: Double, nx: Double, ny: Double): Double {
            var distance = 0.0
            val limit = min(35.0, 6.7 / mmPerPixel)
            while (distance + .22 <= limit &&
                ink(x + nx * (distance + .22), y + ny * (distance + .22))) {
                distance += .22
            }
            return distance
        }

        val parts = ArrayList<ManualSatinSection>()
        fun build(chain: List<Int>) {
            require(parts.size < MAX_SECTIONS) { "Muitas seções Satin." }
            // Trim tiny skeleton tails touching an intersection. The editing
            // UI permits moving endpoints and adding sections at junctions.
            if (chain.size < 8) return
            val stride = max(1, ceil(chain.size.toDouble() / (MAX_POINTS_PER_SECTION-1)).toInt())
            val samples = chain.indices.filter { it % stride == 0 }.toMutableList()
            if (samples.last() != chain.lastIndex) samples += chain.lastIndex
            val rails = ArrayList<SatinRailPair>()
            for (k in samples) {
                val at = chain[k]
                val first = chain[max(0,k-2)]
                val last = chain[min(chain.lastIndex,k+2)]
                val dx = ((last % w) - (first % w)).toDouble()
                val dy = ((last / w) - (first / w)).toDouble()
                val magnitude = hypot(dx,dy)
                require(magnitude > .001) { "A direção local se perdeu." }
                val nx = -dy / magnitude
                val ny = dx / magnitude
                val x = at % w + .5
                val y = at / w + .5
                require(ink(x,y)) { "Eixo fora da arte." }
                val forward = reach(x,y,nx,ny)
                val backward = reach(x,y,-nx,-ny)
                val spanMm = (forward+backward) * mmPerPixel
                require(spanMm in .8..7.0) {
                    "Largura Satin de ${"\$"}spanMm mm fora do limite nesta junção; " +
                        "refine os contornos manualmente."
                }
                val ax = ((x - nx * backward) / w).toFloat().coerceIn(0f,1f)
                val ay = ((y - ny * backward) / h).toFloat().coerceIn(0f,1f)
                val bx = ((x + nx * forward) / w).toFloat().coerceIn(0f,1f)
                val by = ((y + ny * forward) / h).toFloat().coerceIn(0f,1f)
                rails += SatinRailPair(ax,ay,bx,by)
            }
            require(rails.size in 4..100) { "Seção curta demais para Satin." }
            parts += ManualSatinSection(rails, connectToPrevious=false)
        }
        for ((path, closed) in paths) {
            if (closed) {
                // A loop around a counter may be divided at four arc-length
                // intervals, avoiding a cross-hole "shortcut".
                require(path.size >= 32) {
                    "Furo pequeno exige definição manual dos cantos."
                }
                val step = path.size / 4
                require(step >= 8)
                for (quarter in 0..3) {
                    val a = quarter * step
                    val b = if (quarter == 3) path.lastIndex else (quarter+1)*step
                    build(path.subList(a,b+1))
                }
            } else {
                // Cut skeleton tails adjacent to a branch cluster. Otherwise
                // the rail normal may include the T intersection's entire
                // perpendicular arm as if it were one wide Satin column.
                val trimStart = if (neighbors(path.first()).any { junction[it] }) 4 else 0
                val trimEnd = if (neighbors(path.last()).any { junction[it] }) 4 else 0
                if (path.size > trimStart + trimEnd + 7) {
                    build(path.drop(trimStart).dropLast(trimEnd))
                }
            }
        }
        require(parts.isNotEmpty()) {
            "Nenhuma seção Satin com comprimento suficiente foi reconhecida."
        }
        require(parts.size <= MAX_SECTIONS)
        // Verify their stitch topology using the existing engine; never
        // return 'ready' geometry just because the skeleton exists.
        val objectWidth = w * mmPerPixel
        val objectHeight = h * mmPerPixel
        ManualSatinRails.generate(
            sections=parts, outputColorIndex=0, widthMm=objectWidth,
            imageAspectRatio=objectWidth/objectHeight,
            densityMm=.5f, pullCompensationMm=.15f,
            centerUnderlay=false, qaSmallObjectPreview=true
        ).getOrThrow()
        parts
    }
}
