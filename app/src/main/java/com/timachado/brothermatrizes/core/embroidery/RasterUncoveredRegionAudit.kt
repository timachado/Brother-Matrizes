package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.max
import kotlin.math.min

/**
 * QA-only explanation of original component pixels NOT covered by real sewn
 * STITCH segments. JUMP/TRIM never add coverage; letter counters stay empty.
 * This routes HUMAN review, not sewing instructions or PES.
 */
internal enum class RasterGapReviewRoute {
    SATIN_REVIEW, RUNNING_REVIEW, TATAMI_REVIEW, MANUAL_REVIEW
}

internal data class RasterUncoveredRegion(
    val id: Int, val pixelCount: Int,
    val minX: Int, val minY: Int, val maxX: Int, val maxY: Int,
    val suggestedReview: RasterGapReviewRoute
) {
    val widthPixels: Int get() = maxX - minX + 1
    val heightPixels: Int get() = maxY - minY + 1
}

internal data class RasterUncoveredRegionReport(
    val sourcePixels: Int,
    val coveredPixels: Int,
    val uncoveredPixels: Int,
    val totalRegions: Int,
    val regions: List<RasterUncoveredRegion>,
    val hiddenRegionPixels: Int,
    val outsideInkFraction: Double,
    val exportPermitted: Boolean = false
) {
    val coverage: Double get() =
        if (sourcePixels == 0) 0.0 else coveredPixels.toDouble() / sourcePixels

    fun summary(): String {
        val ambiguous = regions.count {
            it.suggestedReview == RasterGapReviewRoute.MANUAL_REVIEW
        }
        return "$uncoveredPixels pixels sem cobertura em $totalRegions " +
            "regiões; cobertura " + (coverage * 100).toInt() + "%. " +
            "$ambiguous região(ões) ambíguas na lista. " +
            if (hiddenRegionPixels > 0) {
                "$hiddenRegionPixels pixels continuam pendentes " +
                    "fora da lista, sem descarte. Não é PES."
            } else "Sugestões de revisão; não gera pontos ou PES."
    }
}

internal object RasterUncoveredRegionAudit {
    private const val MAX_REGIONS = 4096
    private const val MAX_LISTED = 24

    fun inspect(
        source: RasterImagePixels,
        component: RasterSatinObject,
        fidelity: RasterStitchFidelity,
        fullWidthMm: Float
    ): Result<RasterUncoveredRegionReport> = runCatching {
        val b = component.mask
        val w = b.maxX - b.minX + 1
        val h = b.maxY - b.minY + 1
        require(
            source.width in 8..512 && source.height in 8..512 &&
                source.argb.size == source.width * source.height &&
                b.pixels.size == source.argb.size &&
                b.minX >= 0 && b.minY >= 0 &&
                b.maxX < source.width && b.maxY < source.height &&
                w > 0 && h > 0 && w * h <= 262_144 &&
                fullWidthMm.isFinite() && fullWidthMm in 10f..200f &&
                fidelity.maskWidth == w && fidelity.maskHeight == h &&
                fidelity.coveredMask.size == w * h &&
                fidelity.stitchSamples > 0 &&
                fidelity.offInkSamples in 0..fidelity.stitchSamples &&
                !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Auditoria de lacunas exige cobertura das pontadas reais QA." }

        val missing = BooleanArray(w * h)
        var sourceInk = 0
        var covered = 0
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val p = (b.minY + y) * source.width + b.minX + x
            val isInk = b.pixels[p] == 0
            if (!isInk) {
                require(!fidelity.coveredMask[i]) {
                    "Cobertura atribuída a espaço vazio da letra."
                }
                continue
            }
            require(source.argb[p] ushr 24 >= 180) {
                "A máscara divergiu da imagem original."
            }
            sourceInk++
            if (fidelity.coveredMask[i]) covered++ else missing[i] = true
        }
        require(sourceInk > 0 && sourceInk == component.pixelCount &&
            sourceInk == fidelity.inkPixels &&
            covered == fidelity.coveredInkPixels
        ) { "Contagens de pontos e imagem inconsistentes." }

        val visited = BooleanArray(missing.size)
        val queue = IntArray(missing.size)
        val regions = ArrayList<RasterUncoveredRegion>()
        var allRegions = 0
        var hiddenPixels = 0
        val mmPerPixel = fullWidthMm.toDouble() / source.width

        fun route(count: Int, boxWidth: Int, boxHeight: Int):
            RasterGapReviewRoute {
            val small = min(boxWidth, boxHeight) * mmPerPixel
            val large = max(boxWidth, boxHeight) * mmPerPixel
            val fill = count.toDouble() / (boxWidth * boxHeight)
            // Bounding boxes only prioritize HUMAN review, not sewn routes.
            return when {
                small <= .8 -> RasterGapReviewRoute.RUNNING_REVIEW
                large / small >= 2.5 && small <= 7.0 &&
                    fill >= .72 -> RasterGapReviewRoute.SATIN_REVIEW
                small > 7.0 && fill >= .72 ->
                    RasterGapReviewRoute.TATAMI_REVIEW
                else -> RasterGapReviewRoute.MANUAL_REVIEW
            }
        }
        for (root in missing.indices) {
            if (!missing[root] || visited[root]) continue
            require(allRegions < MAX_REGIONS) {
                "Muitos fragmentos; recorte e revise manualmente."
            }
            allRegions++
            visited[root] = true
            queue[0] = root
            var first = 0
            var tail = 1
            var minX = w
            var minY = h
            var maxX = 0
            var maxY = 0
            while (first < tail) {
                val p = queue[first++]
                val x = p % w
                val y = p / w
                minX = min(minX, x); minY = min(minY, y)
                maxX = max(maxX, x); maxY = max(maxY, y)
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val xx = x + dx
                    val yy = y + dy
                    if (xx !in 0 until w || yy !in 0 until h) continue
                    val next = yy * w + xx
                    if (missing[next] && !visited[next]) {
                        visited[next] = true
                        queue[tail++] = next
                    }
                }
            }
            if (regions.size < MAX_LISTED) {
                regions += RasterUncoveredRegion(
                    id = allRegions, pixelCount = tail,
                    minX = b.minX + minX, minY = b.minY + minY,
                    maxX = b.minX + maxX, maxY = b.minY + maxY,
                    suggestedReview = route(tail, maxX-minX+1, maxY-minY+1)
                )
            } else hiddenPixels += tail
        }
        val unsewn = missing.count { it }
        require(covered + unsewn == sourceInk &&
            regions.sumOf { it.pixelCount } + hiddenPixels == unsewn
        ) { "Parte da arte sem costura desapareceu do inventário." }
        RasterUncoveredRegionReport(
            sourcePixels = sourceInk, coveredPixels = covered,
            uncoveredPixels = unsewn, totalRegions = allRegions,
            regions = regions, hiddenRegionPixels = hiddenPixels,
            outsideInkFraction = fidelity.offInkFraction
        )
    }
}
