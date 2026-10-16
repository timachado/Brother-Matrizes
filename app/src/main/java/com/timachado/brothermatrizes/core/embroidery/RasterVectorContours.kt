package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.hypot

/** Vertices lie on pixel boundaries, in LOCAL coordinates of the object crop. */
data class RasterVectorVertex(val x: Float, val y: Float)

/** Closed outer contour or counter (hole); drawing data, NOT stitch commands. */
data class RasterVectorContour(
    val vertices: List<RasterVectorVertex>,
    val isHole: Boolean
)

/**
 * Vectorizes a binary component by tracing exact pixel-border edges.
 *
 * Each ink pixel contributes only edges against background. Edges are
 * consistently oriented, so inner holes wind opposite to the outer contour.
 * Diagonally-touching contours with ambiguous graph vertices are explicitly
 * refused for AUTO suggestions; the manual rail editor remains available.
 */
object RasterVectorContours {
    private data class Vertex(val x: Int, val y: Int)
    private data class Edge(val from: Vertex, val to: Vertex)

    fun trace(mask: SeparatedColorArtwork, imageWidth: Int): Result<List<RasterVectorContour>> =
        runCatching {
            val w = mask.maxX - mask.minX + 1
            val h = mask.maxY - mask.minY + 1
            require(w in 1..512 && h in 1..512 &&
                imageWidth in w..512 && mask.pixels.size % imageWidth == 0) {
                "Máscara inválida para contornos."
            }
            fun ink(x: Int, y: Int): Boolean =
                x in 0 until w && y in 0 until h &&
                    mask.pixels[(mask.minY + y) * imageWidth + mask.minX + x] == 0

            val outgoing = HashMap<Vertex, Vertex>()
            val incoming = HashMap<Vertex, Int>()
            var count = 0
            fun edge(a: Vertex, b: Vertex) {
                require(outgoing.put(a, b) == null) {
                    "Contornos com toque diagonal ambíguo exigem edição manual."
                }
                incoming[b] = (incoming[b] ?: 0) + 1
                count++
                require(count <= 4 * 512 * 512) { "Contorno complexo demais." }
            }
            for (y in 0 until h) for (x in 0 until w) {
                if (!ink(x, y)) continue
                if (!ink(x, y - 1)) edge(Vertex(x, y), Vertex(x + 1, y))
                if (!ink(x + 1, y)) edge(Vertex(x + 1, y), Vertex(x + 1, y + 1))
                if (!ink(x, y + 1)) edge(Vertex(x + 1, y + 1), Vertex(x, y + 1))
                if (!ink(x - 1, y)) edge(Vertex(x, y + 1), Vertex(x, y))
            }
            require(count >= 4) { "Área sem contorno." }
            require(incoming.size == outgoing.size &&
                outgoing.keys.all { incoming[it] == 1 }) {
                "Contorno aberto ou ambíguo; corrija a parte manualmente."
            }

            val visited = HashSet<Vertex>()
            val contours = ArrayList<RasterVectorContour>()
            for (start in outgoing.keys) {
                if (!visited.add(start)) continue
                val vertices = ArrayList<Vertex>()
                var current = start
                do {
                    vertices += current
                    val next = outgoing[current] ?: error("Contorno incompleto.")
                    current = next
                    if (current != start) require(visited.add(current)) {
                        "Contornos cruzados não são adequados à sugestão automática."
                    }
                    require(vertices.size <= count) { "Loop de contorno inválido." }
                } while (current != start)
                // Keep corners; remove only strictly collinear intermediate
                // vertices. No curve-smoothing that changes the bitmap shape.
                val corner = vertices.filterIndexed { i, at ->
                    val prev = vertices[(i + vertices.size - 1) % vertices.size]
                    val next = vertices[(i + 1) % vertices.size]
                    val dx1 = at.x - prev.x
                    val dy1 = at.y - prev.y
                    val dx2 = next.x - at.x
                    val dy2 = next.y - at.y
                    dx1 * dy2 - dy1 * dx2 != 0
                }
                require(corner.size >= 4) { "Contorno degenerado." }
                var areaTwice = 0.0
                for (i in corner.indices) {
                    val a = corner[i]
                    val b = corner[(i + 1) % corner.size]
                    areaTwice += a.x.toDouble() * b.y - b.x.toDouble() * a.y
                }
                require(abs(areaTwice) >= 2.0) { "Contorno insuficiente." }
                contours += RasterVectorContour(
                    vertices = corner.map { RasterVectorVertex(it.x.toFloat(), it.y.toFloat()) },
                    isHole = areaTwice < 0.0
                )
            }
            require(contours.size in 1..64 && contours.count { !it.isHole } == 1) {
                "A parte contém contornos desconectados; divida em partes."
            }
            contours.sortedWith(compareBy<RasterVectorContour> { it.isHole }
                .thenByDescending { c ->
                    val p = c.vertices
                    var length = 0.0
                    for (i in p.indices) {
                        val a = p[i]
                        val b = p[(i + 1) % p.size]
                        length += hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
                    }
                    length
                })
        }
}
