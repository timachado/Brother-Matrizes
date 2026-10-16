package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * Experimental fallback for a single elongated solid stroke whose left and
 * right contours taper smoothly by more than the single-Satin-column limit.
 *
 * Retains every rail from the image-aware generator; splits into overlapping
 * sections of modest, locally consistent width. Each SECTION must be
 * independently audited and the FINAL sewn result must pass original-image
 * fidelity/needle-risk checks. We deliberately do not omit a difficult tip,
 * jump across a counter, or claim machine-ready PES.
 *
 * Not a solution for full calligraphy, thick O/B/P counters, photos, mixed
 * stitch directions, underlay or pull compensation by fabric.
 */
internal object RasterVariableWidthSatinSections {
    private const val MAX_SECTIONS = 6
    private const val MAX_GLOBAL_RATIO = 3.5
    private const val MAX_LOCAL_RATIO = 2.25
    private const val MAX_NEIGHBOR_WIDTH_CHANGE_MM = 1.6

    fun propose(
        image: RasterImagePixels,
        part: RasterSatinObject,
        widthMm: Float
    ): Result<List<ManualSatinSection>> = runCatching {
        require(image.width in 8..512 &&
            image.height in 8..512 &&
            image.argb.size == part.mask.pixels.size &&
            widthMm.isFinite() && widthMm in 10f..200f
        ) { "Imagem ou bastidor inválido para divisão Satin." }

        // Normal propose() is intentionally unchanged and rejects more
        // than 2.35x variation. The wider proposal is only consumed here.
        val rails = RasterAutoSatinRails.propose(
            image, part, widthMm,
            maxWidthRatio = MAX_GLOBAL_RATIO
        ).getOrThrow().rails
        val total = rails.lastIndex
        require(total >= 14) {
            "O traço é curto demais para dividir em colunas Satin."
        }
        val localW = part.mask.maxX - part.mask.minX + 1
        val localH = part.mask.maxY - part.mask.minY + 1
        val mmPx = widthMm / image.width.toDouble()
        fun center(r: SatinRailPair): Pair<Double, Double> = (
            (r.ax+r.bx)*localW/2.0 to
                (r.ay+r.by)*localH/2.0
        )
        fun railWidth(r: SatinRailPair): Double =
            hypot(
                (r.bx-r.ax)*localW.toDouble(),
                (r.by-r.ay)*localH.toDouble()
            ) * mmPx
        val widths = rails.map(::railWidth)
        val overallRatio=widths.maxOrNull()!! /
            widths.minOrNull()!!.coerceAtLeast(.001)
        require(overallRatio > 2.35 && overallRatio <= MAX_GLOBAL_RATIO) {
            "Esta peça não precisa da divisão por variação de largura, " +
                "ou possui mudança excessiva de espessura."
        }
        require(widths.all { it in .8..6.2 }) {
            "Largura local Satin incompatível com a revisão."
        }
        for (i in 1 until widths.size) {
            require(abs(widths[i]-widths[i-1]) <=
                MAX_NEIGHBOR_WIDTH_CHANGE_MM) {
                "Mudança abrupta na espessura da letra exige revisão."
            }
        }

        // Overlap only the single boundary rail so the mask has no skipped
        // strip. Sections do NOT stitch-connect by default: TRIM + JUMP is
        // safer than a hidden cross-seam stitch at a variable-width joint.
        val count = maxOf(2, ceil(total/16.0).toInt())
        require(count <= MAX_SECTIONS && total / count >= 6) {
            "Muitas seções ou pontas curtas demais para Satin."
        }
        val sections = ArrayList<ManualSatinSection>(count)
        for (k in 0 until count) {
            val from = total*k/count
            val to = total*(k+1)/count
            val fragment = rails.subList(from,to+1).toList()
            require(fragment.size in 7..100) {
                "Seção Satin ficou curta demais."
            }
            val physicalWidths = widths.subList(from,to+1)
            require(physicalWidths.maxOrNull()!! /
                physicalWidths.minOrNull()!!.coerceAtLeast(.001)
                <= MAX_LOCAL_RATIO
            ) {
                "Variação excessiva dentro da própria seção Satin."
            }
            var lengthMm=0.0
            for (i in from+1..to) {
                val a = center(rails[i-1])
                val b = center(rails[i])
                lengthMm += hypot(a.first-b.first,a.second-b.second)*mmPx
            }
            require(lengthMm >= 3.2 && lengthMm <= 70.0) {
                "Seção com eixo fora da faixa conservadora de QA."
            }
            sections += ManualSatinSection(
                rails=fragment, connectToPrevious=false
            )
        }
        require(sections.size>=2 && sections.all {
            !it.connectToPrevious
        }) { "Seções não separadas com segurança." }
        sections
    }
}
