package com.timachado.brothermatrizes.core.embroidery

/**
 * A material-specific REVIEW context, not a machine-approved recipe.
 * An unknown material or backing must never silently default to cotton.
 */
internal enum class RasterFabricClass {
    UNKNOWN, STABLE_WOVEN, STRETCH_KNIT, TERRY_PILE
}

internal enum class RasterStabilizerClass {
    UNKNOWN, CUT_AWAY, TEAR_AWAY, WASH_AWAY
}

internal data class RasterUnderlayReviewContext(
    val fabric: RasterFabricClass = RasterFabricClass.UNKNOWN,
    val stabilizer: RasterStabilizerClass = RasterStabilizerClass.UNKNOWN,
    val machineModel: String? = null,
    /** Explicit approval to preview a QA experiment; NOT physical approval. */
    val qaExperimentAcknowledged: Boolean = false
)

internal data class RasterFabricUnderlayReport(
    val baseStitches: Int,
    val underlayStitches: Int,
    val addedStitches: Int,
    val baseLineMm: Double,
    val reviewedLineMm: Double,
    val peakMmPerSquareMm: Double,
    val offInkFraction: Double,
    val coverage: Double,
    val exportPermitted: Boolean,
    val requiresPhysicalStitchOut: Boolean
)

internal data class RasterFabricUnderlayResult(
    val preview: EmbroideryDesign,
    val report: RasterFabricUnderlayReport
)

/**
 * Opt-in Satin underlay proposal on the SAME rail geometry as the approved
 * no-underlay preview. This does not choose settings for a real fabric: the
 * existing generator's 0.5 mm Satin pitch and 0.15 mm pull compensation are
 * experimental QA placeholders. More advanced profiles (knits, pile/towel,
 * wash-away backing) deliberately fail closed until machine-specific data
 * and actual stitch-outs are available.
 *
 * The checker independently validates final quantized needle locations,
 * thread-length concentration and original mask fidelity. Its result is a
 * non-exportable QA_SATIN_REVIEW design only.
 */
internal object RasterFabricUnderlayReview {
    fun compare(
        source: RasterImagePixels,
        part: RasterSatinObject,
        sections: List<ManualSatinSection>,
        fullWidthMm: Float,
        hoop: HoopProfile,
        context: RasterUnderlayReviewContext
    ): Result<RasterFabricUnderlayResult> = runCatching {
        require(context.qaExperimentAcknowledged) {
            "Confirme que esta é uma prévia experimental, não um PES para máquina."
        }
        require(!context.machineModel.isNullOrBlank() &&
            context.machineModel.trim().length in 2..100
        ) {
            "Informe o modelo da máquina para registrar o contexto do teste."
        }
        require(context.fabric == RasterFabricClass.STABLE_WOVEN &&
            context.stabilizer in setOf(
                RasterStabilizerClass.TEAR_AWAY,
                RasterStabilizerClass.CUT_AWAY
            )
        ) {
            "Underlay central de QA ainda não calibrado para esse tecido ou " +
                "entretela. Malha, atoalhado e suporte desconhecido exigem revisão."
        }
        require(sections.isNotEmpty() && sections.all {
            !it.connectToPrevious && it.rails.size in 2..100
        }) {
            "Underlay só pode ser avaliado em seções Satin independentes."
        }
        require(!RasterEmbroideryApproval.canExportFromImageTool()) {
            "Underlay experimental nunca autoriza exportar PES."
        }

        val original=RasterSatinObjectEditor.preview(
            source, part, sections, fullWidthMm, hoop,
            minimumCoverage=.90f, qaCenterUnderlay=false
        ).getOrThrow()
        val withUnderlay=RasterSatinObjectEditor.preview(
            source, part, sections, fullWidthMm, hoop,
            minimumCoverage=.90f, qaCenterUnderlay=true
        ).getOrThrow()

        require(original.format == "QA_SATIN_REVIEW" &&
            withUnderlay.format == "QA_SATIN_REVIEW" &&
            original.sourceBytes.isEmpty() &&
            withUnderlay.sourceBytes.isEmpty() &&
            withUnderlay.isModified
        ) {
            "Comparação de underlay limitada a prévias Satin locais em QA."
        }
        require(withUnderlay.stitchCount > original.stitchCount &&
            withUnderlay.jumpCount > original.jumpCount
        ) {
            "Underlay deve acrescentar pontos reais e deslocamento explícito."
        }
        val originalNeedle=RasterNeedleRiskAudit.inspect(
            original,hoop
        ).getOrThrow()
        val checkedNeedle=RasterNeedleRiskAudit.inspect(
            withUnderlay,hoop
        ).getOrThrow()
        require(checkedNeedle.stitchesExamined>
            originalNeedle.stitchesExamined
        ) {
            "Underlay não acrescentou pontos válidos."
        }
        val baseDensity=RasterThreadDensityPreflight.inspect(
            original
        ).getOrThrow()
        val reviewedDensity=RasterThreadDensityPreflight.inspect(
            withUnderlay
        ).getOrThrow()
        require(reviewedDensity.sewnThreadLengthMm >
            baseDensity.sewnThreadLengthMm
        ) {
            "Underlay deve acrescentar linha realmente costurada."
        }
        // No underlay is allowed to cross a letter's enclosed counter.
        val maskW=part.mask.maxX-part.mask.minX+1
        val maskH=part.mask.maxY-part.mask.minY+1
        if (maskW>=8 && maskH>=8) {
            val holes=RasterCounterSatinGate.count(
                source,part
            ).getOrThrow()
            if(holes>0) RasterCounterSatinGate.inspect(
                source,part,withUnderlay,fullWidthMm
            ).getOrThrow()
        }
        val fidelity=RasterStitchFidelityAudit.compare(
            source,part,withUnderlay,fullWidthMm
        ).getOrThrow()
        require(fidelity.coverage>=.78 &&
            fidelity.offInkFraction<=.10
        ) {
            "Underlay não preservou a geometria original; " +
                "revisar os trilhos e o deslocamento da linha."
        }
        RasterFabricUnderlayResult(
            preview=withUnderlay,
            report=RasterFabricUnderlayReport(
                baseStitches=original.stitchCount,
                underlayStitches=withUnderlay.stitchCount,
                addedStitches=withUnderlay.stitchCount-original.stitchCount,
                baseLineMm=baseDensity.sewnThreadLengthMm,
                reviewedLineMm=reviewedDensity.sewnThreadLengthMm,
                peakMmPerSquareMm=reviewedDensity.peakThreadLengthMmPerCell,
                offInkFraction=fidelity.offInkFraction,
                coverage=fidelity.coverage,
                exportPermitted=false,
                requiresPhysicalStitchOut=true
            )
        )
    }
}
