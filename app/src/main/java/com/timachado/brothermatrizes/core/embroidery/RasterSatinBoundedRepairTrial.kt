package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.hypot
import kotlin.math.min

/**
 * QA ONLY. Tries nudging exactly ONE Satin rail endpoint toward an uncovered
 * ink region, at most 0.35 mm. Each attempt re-generates REAL STITCH commands
 * and passes original-photo fidelity, counter, rail geometry, needle and thread
 * density gates before being offered as an EDITABLE draft.
 *
 * No automatic stitching, no PES bytes, no certification of physical safety.
 */
internal data class RasterSatinBoundedRepairResult(
    val candidateSections: List<ManualSatinSection>,
    val regionId: Int,
    val sectionIndex: Int,
    val railIndex: Int,
    val movedFirstEdge: Boolean,
    val displacementMm: Double,
    val originalRegionMissingPixels: Int,
    val candidateRegionMissingPixels: Int,
    val originalTotalMissingPixels: Int,
    val candidateTotalMissingPixels: Int,
    val originalOffInkFraction: Double,
    val candidateOffInkFraction: Double,
    val requiresHumanReview: Boolean = true,
    val exportPermitted: Boolean = false
) {
    fun message(): String =
        "Região " + regionId + ": " + originalRegionMissingPixels +
            " → " + candidateRegionMissingPixels + " px descobertos. " +
            "Total " + originalTotalMissingPixels + " → " +
            candidateTotalMissingPixels + ". Deslocamento de borda Satin até " +
            "%.2f".format(java.util.Locale.ROOT, displacementMm) +
            " mm; proposta para editar e revisar, não para exportar PES."
}

internal object RasterSatinBoundedRepairTrial {
    private const val MAX_MOVE_MM = .35
    private const val MAX_DISTANCE_TO_INK_MM = 1.8
    private const val MAX_TESTS = 12
    private const val MAX_RAIL_PAIRS = 60

    fun suggest(
        original: RasterImagePixels,
        item: RasterSatinObject,
        sections: List<ManualSatinSection>,
        gapReport: RasterUncoveredRegionReport,
        selectedRegionId: Int,
        fullWidthMm: Float,
        hoop: HoopProfile,
        enclosedHoles: Int
    ): Result<RasterSatinBoundedRepairResult> = runCatching {
        require(original.width in 8..512 && original.height in 8..512 &&
            original.argb.size == original.width * original.height &&
            sections.isNotEmpty() && sections.size <= 12 &&
            sections.sumOf { it.rails.size } in 2..MAX_RAIL_PAIRS &&
            gapReport.totalRegions > 0 &&
            !gapReport.exportPermitted &&
            !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Ajuste limitado exige elemento QA e trilhos editáveis." }
        val region = gapReport.regions.firstOrNull {
            it.id == selectedRegionId
        } ?: error("Região fora da lista de revisão.")
        require(region.suggestedReview == RasterGapReviewRoute.SATIN_REVIEW) {
            "Esta região requer outra técnica ou reconstrução manual, não Satin."
        }
        val advice = RasterGapRepairGuidance.propose(
            original, item, gapReport, fullWidthMm
        ).getOrThrow().advice.firstOrNull { it.regionId == selectedRegionId }
            ?: error("Região não consta na orientação de correção.")
        require(advice.action == RasterGapRepairAction.REVIEW_SATIN_RAILS &&
            advice.nearestCoveredDistanceMm != null
        ) { "Não aproximar partes desconectadas nem alterar outra técnica." }

        data class Metrics(
            val gaps: RasterUncoveredRegionReport,
            val fidelity: RasterStitchFidelity,
            val needle: RasterNeedleRiskReport,
            val density: RasterThreadDensityReport,
            val design: EmbroideryDesign
        )
        fun evaluate(draft: List<ManualSatinSection>): Metrics {
            RasterSatinRailGeometryGate.inspect(
                original,item,draft,fullWidthMm,enclosedHoles
            ).getOrThrow()
            val design = RasterSatinObjectEditor.preview(
                original,item,draft,fullWidthMm,hoop
            ).getOrThrow()
            RasterAssistedSatinDraftPlanner.requireSafeCounterPath(
                original,item,design,fullWidthMm
            ).getOrThrow()
            val needle = RasterNeedleRiskAudit.inspect(design,hoop).getOrThrow()
            val density = RasterThreadDensityPreflight.inspect(design).getOrThrow()
            val fidelity = RasterStitchFidelityAudit.compare(
                original,item,design,fullWidthMm
            ).getOrThrow()
            val gaps = RasterUncoveredRegionAudit.inspect(
                original,item,fidelity,fullWidthMm
            ).getOrThrow()
            require(design.sourceBytes.isEmpty() &&
                design.format == "QA_SATIN_REVIEW" &&
                design.colorChanges == 0 &&
                design.threadColors.size == 1 &&
                design.points.last().command == StitchCommand.END &&
                design.endFound
            ) { "A revisão não pode converter a imagem em PES." }
            return Metrics(gaps,fidelity,needle,density,design)
        }
        val before = evaluate(sections)
        require(before.gaps.uncoveredPixels == gapReport.uncoveredPixels &&
            before.gaps.coveredPixels == gapReport.coveredPixels &&
            before.gaps.regionIds.contentEquals(gapReport.regionIds)
        ) { "A edição mudou desde a última análise de lacunas." }

        // Find the ink centroid of precisely the selected uncovered region.
        var sumX = 0.0
        var sumY = 0.0
        var pixels = 0
        val localWidth = gapReport.localWidth
        for (i in gapReport.regionIds.indices) {
            if (gapReport.regionIds[i] != selectedRegionId) continue
            sumX += gapReport.originX + i%localWidth + .5
            sumY += gapReport.originY + i/localWidth + .5
            pixels++
        }
        require(pixels == region.pixelCount && pixels > 0) {
            "A região mudou ou perdeu pontos sem cobertura."
        }
        val targetX = sumX/pixels
        val targetY = sumY/pixels
        val mmPerPx = fullWidthMm.toDouble()/original.width
        val mask = item.mask
        val maskW = mask.maxX - mask.minX + 1
        val maskH = mask.maxY - mask.minY + 1
        data class Endpoint(
            val section: Int, val rail: Int, val first: Boolean,
            val distanceMm: Double
        )
        val endpoints = ArrayList<Endpoint>()
        for ((sectionIndex,section) in sections.withIndex()) {
            for ((railIndex,rail) in section.rails.withIndex()) {
                for (first in listOf(true,false)) {
                    val u = if(first) rail.ax else rail.bx
                    val v = if(first) rail.ay else rail.by
                    val x = mask.minX + u * maskW
                    val y = mask.minY + v * maskH
                    val d = hypot((x-targetX)*mmPerPx,(y-targetY)*mmPerPx)
                    if(d <= MAX_DISTANCE_TO_INK_MM && d > .03) {
                        endpoints += Endpoint(sectionIndex,railIndex,first,d)
                    }
                }
            }
        }
        require(endpoints.isNotEmpty()) {
            "Lacuna longe dos trilhos: edite as seções manualmente, " +
                "sem estender Satin por um atalho."
        }

        // Trial candidates are ephemeral; no mutation of the user's editor
        // until an explicit click to adopt a candidate as a NEW draft.
        for (candidate in endpoints.sortedBy { it.distanceMm }.take(MAX_TESTS)) {
            val section = sections[candidate.section]
            val originalRail = section.rails[candidate.rail]
            val x = mask.minX +
                (if(candidate.first) originalRail.ax else originalRail.bx)*maskW
            val y = mask.minY +
                (if(candidate.first) originalRail.ay else originalRail.by)*maskH
            val dx = targetX-x
            val dy = targetY-y
            val length = hypot(dx,dy)
            if(length <= 1e-6) continue
            val movementMm = min(MAX_MOVE_MM,candidate.distanceMm)
            val step = movementMm/mmPerPx/length
            val u = ((x+dx*step-mask.minX)/maskW).toFloat()
            val v = ((y+dy*step-mask.minY)/maskH).toFloat()
            if (u !in 0f..1f || v !in 0f..1f) continue
            val editedRail = if(candidate.first) {
                originalRail.copy(ax=u,ay=v)
            } else originalRail.copy(bx=u,by=v)
            val edited = sections.mapIndexed { sectionId,s ->
                if(sectionId != candidate.section) s
                else s.copy(rails=s.rails.mapIndexed { railId,rail ->
                    if(railId == candidate.rail) editedRail else rail
                })
            }
            val after = runCatching { evaluate(edited) }.getOrNull()
                ?: continue
            if (after.gaps.uncoveredPixels >= before.gaps.uncoveredPixels ||
                after.fidelity.coverage < before.fidelity.coverage ||
                after.fidelity.offInkFraction >
                    before.fidelity.offInkFraction + .005 ||
                after.density.peakThreadLengthMmPerCell >
                    before.density.peakThreadLengthMmPerCell + 1.0 ||
                after.needle.peakNeedlePenetrationsPerCell >
                    before.needle.peakNeedlePenetrationsPerCell ||
                after.design.jumpCount != before.design.jumpCount ||
                after.design.colorChanges != before.design.colorChanges ||
                after.design.points.count { it.command == StitchCommand.TRIM } !=
                    before.design.points.count { it.command == StitchCommand.TRIM }
            ) continue

            // Match original region ID rather than trusting new IDs (which
            // may shift when another region becomes fully covered).
            var stillMissing = 0
            for (i in gapReport.regionIds.indices) {
                if (gapReport.regionIds[i] == selectedRegionId &&
                    after.gaps.regionIds[i] != 0) stillMissing++
            }
            if(stillMissing >= pixels) continue
            return@runCatching RasterSatinBoundedRepairResult(
                candidateSections=edited,
                regionId=selectedRegionId,
                sectionIndex=candidate.section,
                railIndex=candidate.rail,
                movedFirstEdge=candidate.first,
                displacementMm=movementMm,
                originalRegionMissingPixels=pixels,
                candidateRegionMissingPixels=stillMissing,
                originalTotalMissingPixels=before.gaps.uncoveredPixels,
                candidateTotalMissingPixels=after.gaps.uncoveredPixels,
                originalOffInkFraction=before.fidelity.offInkFraction,
                candidateOffInkFraction=after.fidelity.offInkFraction
            )
        }
        error(
            "Nenhum deslocamento Satin de até 0,35 mm melhorou a " +
                "cobertura com densidade, vazados, saltos e agulha válidos. " +
                "Revise os trilhos manualmente ou escolha outra técnica."
        )
    }
}
