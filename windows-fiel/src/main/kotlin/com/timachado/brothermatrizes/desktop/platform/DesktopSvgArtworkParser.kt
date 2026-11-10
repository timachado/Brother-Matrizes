package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.embroidery.SimpleSvgParser
import com.timachado.brothermatrizes.core.embroidery.VectorArtwork
import com.timachado.brothermatrizes.core.embroidery.VectorPoint
import com.timachado.brothermatrizes.core.embroidery.VectorPath
import java.io.ByteArrayInputStream
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Windows-only SVG transformations. The Android parser and matrix generator
 * remain unchanged. Applies nested SVG affine matrices BEFORE stitch generation.
 * Source bytes are read-only; external XML resources are forbidden.
 */
internal object DesktopSvgArtworkParser {
    private const val MAX_BYTES = 2_000_000
    private val shapes = setOf(
        "path", "line", "polyline", "polygon", "rect", "circle", "ellipse"
    )
    private val hiddenTrees = setOf(
        "defs", "clippath", "mask", "symbol", "pattern",
        "lineargradient", "radialgradient", "filter", "metadata"
    )
    private val numberRe = Regex("""[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?""")
    private val opRe = Regex("""([A-Za-z]+)\s*\(([^()]*)\)""")

    private data class Matrix(
        val a: Double = 1.0, val b: Double = 0.0,
        val c: Double = 0.0, val d: Double = 1.0,
        val e: Double = 0.0, val f: Double = 0.0
    ) {
        // SVG standard matrix multiplication. Parent * child.
        fun times(v: Matrix): Matrix = Matrix(
            a * v.a + c * v.b, b * v.a + d * v.b,
            a * v.c + c * v.d, b * v.c + d * v.d,
            a * v.e + c * v.f + e, b * v.e + d * v.f + f
        )
        fun point(p: VectorPoint): VectorPoint {
            val x = a * p.x + c * p.y + e
            val y = b * p.x + d * p.y + f
            require(x.isFinite() && y.isFinite() &&
                kotlin.math.abs(x) < 1e9 && kotlin.math.abs(y) < 1e9) {
                "Transformação SVG ultrapassou o limite de coordenadas."
            }
            return VectorPoint(x.toFloat(), y.toFloat())
        }
    }

    fun parse(filename: String, bytes: ByteArray): Result<VectorArtwork> = runCatching {
        require(bytes.size in 1..MAX_BYTES) { "SVG fora do limite de tamanho." }
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        factory.isExpandEntityReferences = false
        factory.isXIncludeAware = false
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        val root = factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(bytes)).documentElement
        require(tag(root) == "svg") { "O arquivo não é SVG." }
        val collected = mutableListOf<VectorPath>()
        var count = 0
        var points = 0

        fun visit(node: Node, inherited: Matrix) {
            if (node.nodeType != Node.ELEMENT_NODE) return
            val element = node as Element
            val type = tag(element)
            if (type in hiddenTrees || element.getAttribute("display") == "none") return
            val transformed = inherited.times(parseTransforms(element.getAttribute("transform")))
            if (type in shapes) {
                count++
                require(count <= 3000) { "SVG excede o limite de formas." }
                val one = SimpleSvgParser.parse(filename, shapeOnly(element, type))
                    .getOrThrow()
                for (path in one.paths) {
                    points += path.points.size
                    require(points <= 200_000) { "SVG tem pontos vetoriais demais." }
                    collected += path.copy(points = path.points.map(transformed::point))
                }
            }
            val children = element.childNodes
            for (i in 0 until children.length) visit(children.item(i), transformed)
        }
        visit(root, Matrix())
        require(collected.isNotEmpty()) { "Nenhuma geometria SVG suportada." }
        VectorArtwork(collected, filename.substringBeforeLast('.').ifBlank { "Desenho SVG" })
    }

    private fun tag(e: Element) = e.tagName.substringAfterLast(':')
        .lowercase(Locale.ROOT)

    // Single-element SVG reuses the original Android path/curve interpreter.
    private fun shapeOnly(e: Element, type: String): ByteArray {
        val names = when (type) {
            "path" -> listOf("d")
            "line" -> listOf("x1", "y1", "x2", "y2")
            "polyline", "polygon" -> listOf("points")
            "rect" -> listOf("x", "y", "width", "height")
            "circle" -> listOf("cx", "cy", "r")
            "ellipse" -> listOf("cx", "cy", "rx", "ry")
            else -> error("Elemento SVG não suportado.")
        }
        val attrs = names.filter(e::hasAttribute).joinToString(" ") { key ->
            val value = e.getAttribute(key).replace("&", "&amp;")
                .replace("\"", "&quot;").replace("<", "&lt;")
            key + "=\"" + value + "\""
        }
        return ("<svg><" + type + " " + attrs + " /></svg>")
            .toByteArray(Charsets.UTF_8)
    }

    private fun parseTransforms(raw: String): Matrix {
        if (raw.isBlank()) return Matrix()
        var end = 0
        var combined = Matrix()
        var n = 0
        for (op in opRe.findAll(raw)) {
            require(raw.substring(end, op.range.first).all {
                it.isWhitespace() || it == ','
            }) { "Transformação SVG não suportada." }
            end = op.range.last + 1
            n++
            require(n <= 64) { "Muitas transformações SVG." }
            val v = parseNumbers(op.groupValues[2])
            val transform = when (op.groupValues[1].lowercase(Locale.ROOT)) {
                "matrix" -> {
                    require(v.size == 6) { "matrix SVG requer 6 valores." }
                    Matrix(v[0], v[1], v[2], v[3], v[4], v[5])
                }
                "translate" -> {
                    require(v.size in 1..2) { "translate SVG inválido." }
                    Matrix(e = v[0], f = v.getOrElse(1) { 0.0 })
                }
                "scale" -> {
                    require(v.size in 1..2) { "scale SVG inválido." }
                    Matrix(a = v[0], d = v.getOrElse(1) { v[0] })
                }
                "rotate" -> {
                    require(v.size == 1 || v.size == 3) { "rotate SVG inválido." }
                    val angle = Math.toRadians(v[0])
                    val rotation = Matrix(
                        a = cos(angle), b = sin(angle),
                        c = -sin(angle), d = cos(angle)
                    )
                    if (v.size == 1) rotation else
                        Matrix(e = v[1], f = v[2]).times(rotation)
                            .times(Matrix(e = -v[1], f = -v[2]))
                }
                "skewx", "skewy" -> {
                    require(v.size == 1) { "skew SVG inválido." }
                    val slope = tan(Math.toRadians(v[0]))
                    if (op.groupValues[1].equals("skewx", true))
                        Matrix(c = slope) else Matrix(b = slope)
                }
                else -> throw IllegalArgumentException(
                    "Transformação SVG desconhecida: " + op.groupValues[1]
                )
            }
            combined = combined.times(transform)
        }
        require(n > 0 && raw.substring(end).all {
            it.isWhitespace() || it == ','
        }) { "Transformação SVG inválida." }
        return combined
    }

    private fun parseNumbers(raw: String): List<Double> {
        var pos = 0
        val out = mutableListOf<Double>()
        for (match in numberRe.findAll(raw)) {
            require(raw.substring(pos, match.range.first).all {
                it.isWhitespace() || it == ','
            }) { "Valor numérico SVG inválido." }
            pos = match.range.last + 1
            out += match.value.toDouble().also { require(it.isFinite()) }
        }
        require(raw.substring(pos).all { it.isWhitespace() || it == ',' }) {
            "Parâmetro de transformação SVG inválido."
        }
        return out
    }
}
