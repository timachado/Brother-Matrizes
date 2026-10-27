package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Verifica a proximidade entre pontadas REAIS do nome e os traçados existentes
 * na matriz, sem considerar saltos/cortes como costura.
 *
 * A geometria é indexada espacialmente para preservar a resposta interativa.
 * Margem padrão: 1,5 mm (15 unidades de 0,1 mm).
 */
internal object NamePlacementSafety {
    private const val CELL_UNITS = 40

    private data class Segment(
        val ax: Int, val ay: Int,
        val bx: Int, val by: Int
    ) {
        val minX = min(ax, bx)
        val maxX = max(ax, bx)
        val minY = min(ay, by)
        val maxY = max(ay, by)
    }

    // Somente a última moldura. Compara IDENTIDADE de objeto, evitando
    // hashCode/equals de listas com milhares de pontadas a cada gesto.
    private data class Indexed(
        val design: EmbroideryDesign,
        val segments: List<Segment>,
        val cells: Map<Long, List<Int>>
    )

    @Volatile private var cached: Indexed? = null

    private fun indexed(background: EmbroideryDesign): Indexed {
        cached?.let { if (it.design === background) return it }
        synchronized(this) {
            cached?.let { if (it.design === background) return it }
            val segments = sewnSegments(background.points)
            val cells = HashMap<Long, MutableList<Int>>()
            // Máxima margem suportada pela API (100 unidades). Assim o cache
            // atende a qualquer validação de 0 a 10 mm.
            segments.forEachIndexed { index, segment ->
                val left = Math.floorDiv(segment.minX - 100, CELL_UNITS)
                val right = Math.floorDiv(segment.maxX + 100, CELL_UNITS)
                val top = Math.floorDiv(segment.minY - 100, CELL_UNITS)
                val bottom = Math.floorDiv(segment.maxY + 100, CELL_UNITS)
                require(right.toLong() - left < 1024L &&
                    bottom.toLong() - top < 1024L) {
                    "Segmento muito longo para validação."
                }
                for (gx in left..right) for (gy in top..bottom) {
                    cells.getOrPut(cellKey(gx, gy)) { ArrayList() }.add(index)
                }
            }
            return Indexed(background, segments, cells).also { cached = it }
        }
    }

    fun validate(
        background: EmbroideryDesign,
        name: EmbroideryDesign,
        clearanceUnits: Int = 15
    ): Result<Unit> = runCatching {
        require(clearanceUnits in 0..100) { "Margem inválida." }
        val index = indexed(background)
        val original = index.segments
        val grid = index.cells
        val typed = sewnSegments(name.points)
        require(original.isNotEmpty() && typed.isNotEmpty()) {
            "Não foi possível verificar o traçado físico do nome e da moldura."
        }
        val clearanceSquared = clearanceUnits.toDouble() * clearanceUnits
        for (seg in typed) {
            val left = Math.floorDiv(seg.minX, CELL_UNITS)
            val right = Math.floorDiv(seg.maxX, CELL_UNITS)
            val top = Math.floorDiv(seg.minY, CELL_UNITS)
            val bottom = Math.floorDiv(seg.maxY, CELL_UNITS)
            require(right.toLong() - left.toLong() < 1024L &&
                bottom.toLong() - top.toLong() < 1024L) {
                "Nome possui um traçado muito longo para validação automática."
            }
            val visited = HashSet<Int>()
            for (gx in left..right) {
                for (gy in top..bottom) {
                    for (id in grid[cellKey(gx, gy)].orEmpty()) {
                        if (visited.add(id) &&
                            segmentDistanceSquared(seg, original[id]) <
                                clearanceSquared
                        ) {
                            throw IllegalArgumentException(
                                "Nome muito próximo das flores ou borboletas. " +
                                    "Reduza a altura, escolha outra fonte ou mova o nome " +
                                    "para manter pelo menos 1,5 mm de separação."
                            )
                        }
                    }
                }
            }
        }
    }

    private fun sewnSegments(points: List<EmbroideryPoint>): List<Segment> {
        val sewn = ArrayList<Segment>()
        var needle: EmbroideryPoint? = null
        for (point in points) {
            if (point.command == StitchCommand.END) break
            if (point.command == StitchCommand.STITCH && needle != null &&
                (point.xUnits != needle.xUnits || point.yUnits != needle.yUnits)
            ) {
                sewn += Segment(
                    needle.xUnits, needle.yUnits,
                    point.xUnits, point.yUnits
                )
            }
            needle = point
        }
        return sewn
    }

    private fun cellKey(x: Int, y: Int): Long =
        (x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)

    private fun segmentDistanceSquared(a: Segment, b: Segment): Double {
        if (intersects(a, b)) return 0.0
        return min(
            min(pointToSegmentSquared(a.ax, a.ay, b),
                pointToSegmentSquared(a.bx, a.by, b)),
            min(pointToSegmentSquared(b.ax, b.ay, a),
                pointToSegmentSquared(b.bx, b.by, a))
        )
    }

    private fun intersects(a: Segment, b: Segment): Boolean {
        fun cross(ax: Int, ay: Int, bx: Int, by: Int, cx: Int, cy: Int): Double =
            (bx.toDouble() - ax) * (cy.toDouble() - ay) -
                (by.toDouble() - ay) * (cx.toDouble() - ax)
        val c1 = cross(a.ax, a.ay, a.bx, a.by, b.ax, b.ay)
        val c2 = cross(a.ax, a.ay, a.bx, a.by, b.bx, b.by)
        val c3 = cross(b.ax, b.ay, b.bx, b.by, a.ax, a.ay)
        val c4 = cross(b.ax, b.ay, b.bx, b.by, a.bx, a.by)
        if (c1 * c2 > 0 || c3 * c4 > 0) return false
        return a.maxX >= b.minX && b.maxX >= a.minX &&
            a.maxY >= b.minY && b.maxY >= a.minY
    }

    private fun pointToSegmentSquared(x: Int, y: Int, seg: Segment): Double {
        val dx = seg.bx.toDouble() - seg.ax
        val dy = seg.by.toDouble() - seg.ay
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) {
            val vx = x.toDouble() - seg.ax
            val vy = y.toDouble() - seg.ay
            return vx * vx + vy * vy
        }
        val projection =
            ((x.toDouble() - seg.ax) * dx +
                (y.toDouble() - seg.ay) * dy) / len2
        val t = projection.coerceIn(0.0, 1.0)
        val ex = seg.ax + t * dx - x
        val ey = seg.ay + t * dy - y
        return ex * ex + ey * ey
    }
}
