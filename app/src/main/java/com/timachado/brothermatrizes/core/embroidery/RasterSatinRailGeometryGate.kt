package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * QA-only inspection of EDITABLE Satin rails before generating needle paths.
 * For curved, tapered and branched glyphs, checks width measured in mm,
 * centerline direction and whether the A/B rails twist through one another.
 *
 * Complements, never replaces, the post-quantization STITCH/counter audits.
 * NOT a machine, fabric or density certification. Export remains disabled.
 */
internal data class RasterSatinRailGeometryEvidence(
    val sectionCount: Int,
    val inspectedPairs: Int,
    val minWidthMm: Double,
    val maxWidthMm: Double,
    val maxAdjacentWidthRatio: Double,
    val maxCenterTurnDegrees: Double,
    val exportPermitted: Boolean = false
)

internal object RasterSatinRailGeometryGate {
    private const val MIN_WIDTH_MM = .78
    private const val MAX_WIDTH_MM = 7.0
    private const val MAX_WIDTH_RATIO = 3.0
    private const val MIN_CENTER_STEP_MM = .10
    private const val MAX_CENTER_STEP_MM = 40.0
    private const val MAX_TURN_COSINE_NEGATIVE = -.60

    private data class Pt(val x: Double, val y: Double) {
        operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
        operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
        operator fun div(k: Double) = Pt(x / k, y / k)
        fun norm(): Double = hypot(x, y)
        fun dot(o: Pt) = x * o.x + y * o.y
        fun cross(o: Pt) = x * o.y - y * o.x
    }

    fun inspect(
        source: RasterImagePixels,
        item: RasterSatinObject,
        sections: List<ManualSatinSection>,
        fullWidthMm: Float,
        enclosedHoles: Int
    ): Result<RasterSatinRailGeometryEvidence> = runCatching {
        val mask = item.mask
        require(
            source.width in 8..512 && source.height in 8..512 &&
                source.argb.size == source.width * source.height &&
                mask.pixels.size == source.argb.size &&
                mask.minX in 0 until source.width &&
                mask.maxX in mask.minX until source.width &&
                mask.minY in 0 until source.height &&
                mask.maxY in mask.minY until source.height &&
                fullWidthMm.isFinite() && fullWidthMm in 10f..200f &&
                sections.size in 1..40 && enclosedHoles in 0..2 &&
                !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Geometria Satin fora do tamanho ou modo QA permitido." }

        val scale = fullWidthMm.toDouble() / source.width
        val cropW = (mask.maxX - mask.minX + 1) * scale
        val cropH = (mask.maxY - mask.minY + 1) * scale
        fun a(p: SatinRailPair) = Pt(p.ax.toDouble() * cropW,
            p.ay.toDouble() * cropH)
        fun b(p: SatinRailPair) = Pt(p.bx.toDouble() * cropW,
            p.by.toDouble() * cropH)

        var pairs = 0
        var minWidth = Double.POSITIVE_INFINITY
        var maxWidth = 0.0
        var maxRatio = 1.0
        var maxTurn = 0.0
        for ((sectionIndex, section) in sections.withIndex()) {
            require(section.rails.size in 2..100) {
                "Cada seção Satin requer de 2 a 100 pares de trilhos."
            }
            require(sectionIndex != 0 || !section.connectToPrevious) {
                "Primeira seção não pode ligar-se à anterior."
            }
            require(enclosedHoles == 0 || !section.connectToPrevious) {
                "Contraforma: divida em seções com TRIM/JUMP, sem costurar " +
                    "um atalho através do vazado."
            }
            val railA = ArrayList<Pt>(section.rails.size)
            val railB = ArrayList<Pt>(section.rails.size)
            val center = ArrayList<Pt>(section.rails.size)
            val widths = ArrayList<Double>(section.rails.size)
            for (r in section.rails) {
                require(listOf(r.ax, r.ay, r.bx, r.by).all {
                    it.isFinite() && it in 0f..1f
                }) { "Trilhos Satin fora da área do componente." }
                val left = a(r)
                val right = b(r)
                val span = right - left
                val w = span.norm()
                require(w.isFinite() && w in MIN_WIDTH_MM..MAX_WIDTH_MM) {
                    "Satin com largura " + "%.2f".format(java.util.Locale.ROOT,w) +
                        " mm fora de 0,78 a 7 mm. Divida ou ajuste a coluna."
                }
                minWidth = min(minWidth, w)
                maxWidth = max(maxWidth, w)
                railA += left
                railB += right
                center += (left + right) / 2.0
                widths += w
                pairs++
            }
            val segments = ArrayList<Pt>()
            for (i in 0 until center.lastIndex) {
                val step = center[i + 1] - center[i]
                val length = step.norm()
                require(length.isFinite() &&
                    length in MIN_CENTER_STEP_MM..MAX_CENTER_STEP_MM
                ) { "Seção Satin contém salto entre pares ou eixo parado." }
                segments += step
                val ratio = max(widths[i], widths[i+1]) /
                    min(widths[i], widths[i+1])
                require(ratio <= MAX_WIDTH_RATIO) {
                    "A largura Satin muda abruptamente entre trilhos. " +
                        "Ajuste a curva e a densidade da seção."
                }
                maxRatio = max(maxRatio, ratio)
                // Do not accept a twisted quadrilateral. For each step,
                // an A rail cannot cross its opposing B rail.
                val d1 = railA[i+1] - railA[i]
                val d2 = railB[i+1] - railB[i]
                val side = railB[i] - railA[i]
                val denom1 = d1.cross(side)
                val denom2 = d2.cross(side)
                // Check orientation against the next centerline. The main
                // Satin generator independently checks local cross signs.
                val across = (railB[i] - railA[i]).cross(step)
                require(abs(across) >= .20 * widths[i] * length) {
                    "Trilhos quase paralelos ao caminho Satin ou torcidos."
                }
                val nextAcross = (railB[i+1] - railA[i+1]).cross(step)
                require(across * nextAcross > 0.0) {
                    "Os dois lados da coluna Satin inverteram entre pares."
                }
                // Crossing of the A/B edge segments is a separate explicit
                // rejection, including intersections despite same endpoint
                // winding (rare highly skewed user edits).
                val det = d1.cross(d2)
                if (abs(det) > 1e-9) {
                    val diff = railB[i] - railA[i]
                    val t = diff.cross(d2) / det
                    val u = diff.cross(d1) / det
                    require(t <= .0001 || t >= .9999 ||
                        u <= .0001 || u >= .9999) {
                        "Lados A/B do Satin se cruzam dentro da seção."
                    }
                }
                @Suppress("UNUSED_VARIABLE")
                val geometricScreen = denom1 + denom2
            }
            for (i in 0 until segments.lastIndex) {
                val cos = (segments[i].dot(segments[i+1]) /
                    (segments[i].norm() * segments[i+1].norm()))
                    .coerceIn(-1.0,1.0)
                require(cos > MAX_TURN_COSINE_NEGATIVE) {
                    "Curva Satin faz retorno muito brusco. " +
                        "Divida a letra em seções independentes."
                }
                val turnDegrees = Math.toDegrees(kotlin.math.acos(cos))
                maxTurn = max(maxTurn, turnDegrees)
            }
        }
        RasterSatinRailGeometryEvidence(
            sectionCount = sections.size,
            inspectedPairs = pairs,
            minWidthMm = minWidth,
            maxWidthMm = maxWidth,
            maxAdjacentWidthRatio = maxRatio,
            maxCenterTurnDegrees = maxTurn
        )
    }
}
