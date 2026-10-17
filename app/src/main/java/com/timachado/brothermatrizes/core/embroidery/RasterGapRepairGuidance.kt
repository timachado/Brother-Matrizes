package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.max
import kotlin.math.min

/**
 * Prioritizes the edits a human might make to Satin rails or other stitch
 * techniques. Distances to the nearest *sewn* ink are measured only along
 * original-ink pixels: never through transparent counters or photo background.
 *
 * This is an advisory report. It does NOT edit Satin rails, fabricate Tatami
 * or Running needle commands, or enable PES export.
 */
internal enum class RasterGapRepairAction {
    REVIEW_SATIN_RAILS,
    REVIEW_RUNNING_STROKE,
    REVIEW_TATAMI_FILL,
    RECONSTRUCT_CONTOUR
}

internal data class RasterGapRepairItem(
    val regionId: Int,
    val pixelsMissing: Int,
    val action: RasterGapRepairAction,
    val nearestCoveredX: Int?,
    val nearestCoveredY: Int?,
    val nearestCoveredDistanceMm: Double?,
    val priority: Int,
    val requiresManualReview: Boolean = true,
    val exportPermitted: Boolean = false
) {
    fun guidance(): String {
        val technique = when (action) {
            RasterGapRepairAction.REVIEW_SATIN_RAILS ->
                "rever o eixo, a largura e as extremidades dos trilhos Satin"
            RasterGapRepairAction.REVIEW_RUNNING_STROKE ->
                "avaliar um contorno em ponto corrido"
            RasterGapRepairAction.REVIEW_TATAMI_FILL ->
                "avaliar preenchimento Tatami separado do Satin"
            RasterGapRepairAction.RECONSTRUCT_CONTOUR ->
                "reconstruir manualmente a forma e suas ramificações"
        }
        val relation = if (nearestCoveredDistanceMm == null) {
            "Sem ligação segura até tinta já costurada; não criar atalho."
        } else if (nearestCoveredDistanceMm > 4.0) {
            "Lacuna distante da área costurada: não prolongar trilhos sem revisar a forma."
        } else {
            "Conferir o trecho próximo da cobertura existente, sem atravessar vazados."
        }
        return "Região " + regionId + " • prioridade " + priority +
            " • " + pixelsMissing + " px: " + technique + ". " +
            relation + " Nenhum ponto novo foi gerado."
    }
}

internal data class RasterGapRepairPlan(
    val advice: List<RasterGapRepairItem>,
    val totalGapRegions: Int,
    val unlistedRegions: Int,
    val unlistedGapPixels: Int,
    val originalUncoveredPixels: Int,
    val exportPermitted: Boolean = false
) {
    fun summary(): String =
        advice.size.toString() + " regiões priorizadas para revisão de " +
            totalGapRegions + " regiões encontradas; " +
            originalUncoveredPixels + " px continuam sem cobertura. " +
            if (unlistedRegions > 0) {
                unlistedRegions.toString() + " regiões adicionais (" +
                    unlistedGapPixels + " px) permanecem pendentes. " +
                    "Nenhuma correção ou PES foi gerada."
            } else "Revisão orientativa; nenhuma correção ou PES foi gerada."
}

internal object RasterGapRepairGuidance {
    private const val MAX_IMAGE_SIDE = 512
    private const val ALPHA_LIMIT = 180
    private const val UNREACHABLE = Int.MAX_VALUE

    fun propose(
        original: RasterImagePixels,
        component: RasterSatinObject,
        report: RasterUncoveredRegionReport,
        artworkWidthMm: Float
    ): Result<RasterGapRepairPlan> = runCatching {
        val mask = component.mask
        val w = report.localWidth
        val h = report.localHeight
        require(
            original.width in 8..MAX_IMAGE_SIDE &&
                original.height in 8..MAX_IMAGE_SIDE &&
                original.argb.size == original.width * original.height &&
                mask.pixels.size == original.argb.size &&
                w in 1..MAX_IMAGE_SIDE && h in 1..MAX_IMAGE_SIDE &&
                w * h == report.regionIds.size &&
                report.originX == mask.minX && report.originY == mask.minY &&
                report.originX + w == mask.maxX + 1 &&
                report.originY + h == mask.maxY + 1 &&
                mask.minX >= 0 && mask.minY >= 0 &&
                mask.maxX < original.width && mask.maxY < original.height &&
                artworkWidthMm.isFinite() &&
                artworkWidthMm in 10f..200f &&
                report.totalRegions >= 0 && report.totalRegions <= 4096 &&
                report.regions.size <= 24 &&
                !report.exportPermitted &&
                !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Plano de correção incompatível com as lacunas da arte original." }

        val regionCount = IntArray(report.totalRegions + 1)
        val distance = IntArray(w * h) { UNREACHABLE }
        val anchor = IntArray(w * h) { -1 }
        val queue = IntArray(w * h)
        var end = 0
        var inkCount = 0
        var covered = 0
        for (y in 0 until h) for (x in 0 until w) {
            val at = y*w+x
            val full = (report.originY+y)*original.width+report.originX+x
            val ink = mask.pixels[full] == 0
            val region = report.regionIds[at]
            require(region in 0..report.totalRegions) {
                "Identificação de região fora da referência QA."
            }
            if (!ink) {
                require(region == 0) {
                    "Não atravessar contraforma transparente."
                }
                continue
            }
            require(original.argb[full] ushr 24 >= ALPHA_LIMIT) {
                "O desenho original não contém tinta nesta posição."
            }
            inkCount++
            if (region == 0) {
                covered++
                distance[at] = 0
                anchor[at] = at
                queue[end++] = at
            } else regionCount[region]++
        }
        require(inkCount == component.pixelCount &&
            inkCount == report.sourcePixels &&
            covered == report.coveredPixels &&
            inkCount-covered == report.uncoveredPixels &&
            regionCount.drop(1).count { it > 0 } == report.totalRegions &&
            report.regions.all { it.id in 1..report.totalRegions &&
                it.pixelCount == regionCount[it.id] } &&
            report.regions.sumOf { it.pixelCount } +
                report.hiddenRegionPixels == report.uncoveredPixels
        ) { "Contagem de regiões não corresponde à tinta original." }

        // Multi-source shortest path on the original component ink only.
        // No transparent counter can become a shortcut for a Satin rail.
        var front = 0
        fun tryVisit(from: Int, next: Int) {
            if (next < 0 || next >= distance.size ||
                distance[next] != UNREACHABLE) return
            val x = next % w
            val y = next / w
            val full = (report.originY + y)*original.width +
                report.originX + x
            if (mask.pixels[full] != 0) return
            distance[next] = distance[from] + 1
            anchor[next] = anchor[from]
            queue[end++] = next
        }
        while (front < end) {
            val index = queue[front++]
            val x = index % w
            val y = index / w
            if (x > 0) tryVisit(index,index-1)
            if (x+1 < w) tryVisit(index,index+1)
            if (y > 0) tryVisit(index,index-w)
            if (y+1 < h) tryVisit(index,index+w)
        }

        val result = ArrayList<RasterGapRepairItem>()
        val mm = artworkWidthMm.toDouble()/original.width
        for (region in report.regions) {
            var best = UNREACHABLE
            var point = -1
            for (i in report.regionIds.indices) {
                if (report.regionIds[i] != region.id) continue
                if (distance[i] < best) {
                    best = distance[i]
                    point = anchor[i]
                }
            }
            val action = when (region.suggestedReview) {
                RasterGapReviewRoute.SATIN_REVIEW ->
                    RasterGapRepairAction.REVIEW_SATIN_RAILS
                RasterGapReviewRoute.RUNNING_REVIEW ->
                    RasterGapRepairAction.REVIEW_RUNNING_STROKE
                RasterGapReviewRoute.TATAMI_REVIEW ->
                    RasterGapRepairAction.REVIEW_TATAMI_FILL
                RasterGapReviewRoute.MANUAL_REVIEW ->
                    RasterGapRepairAction.RECONSTRUCT_CONTOUR
            }
            val distanceMm =
                if (best == UNREACHABLE) null else best*mm
            // Larger missing areas and disconnected portions take precedence.
            val priority = when {
                distanceMm == null -> 1
                region.pixelCount >= 200 || distanceMm >= 4.0 -> 1
                region.pixelCount >= 30 || distanceMm >= 1.0 -> 2
                else -> 3
            }
            result += RasterGapRepairItem(
                regionId=region.id,
                pixelsMissing=region.pixelCount,
                action=action,
                nearestCoveredX=if (point == -1) null
                    else report.originX + point%w,
                nearestCoveredY=if (point == -1) null
                    else report.originY + point/w,
                nearestCoveredDistanceMm=distanceMm,
                priority=priority
            )
        }
        val missingRegions=report.totalRegions-report.regions.size
        require(missingRegions >= 0 &&
            result.size+missingRegions == report.totalRegions
        ) { "Uma região saiu indevidamente da revisão assistida." }
        RasterGapRepairPlan(
            advice=result.sortedWith(compareBy<RasterGapRepairItem> { it.priority }
                .thenByDescending { it.pixelsMissing }.thenBy { it.regionId }),
            totalGapRegions=report.totalRegions,
            unlistedRegions=missingRegions,
            unlistedGapPixels=report.hiddenRegionPixels,
            originalUncoveredPixels=report.uncoveredPixels
        )
    }
}
