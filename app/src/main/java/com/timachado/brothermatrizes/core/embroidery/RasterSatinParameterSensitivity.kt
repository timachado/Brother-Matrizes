package com.timachado.brothermatrizes.core.embroidery

/**
 * Observational QA comparison of Satin needle streams at alternate stitch
 * spacing, pull compensation and optional center underlay.
 *
 * NOT a fabric recipe generator. No alternative is auto-selected, approved
 * for any Brother model or exported as PES. A "passedGeometryQa" result only
 * means the actual integer STITCH commands passed existing static checks.
 * Thread width, backing, tension, speed, fabric distortion and physical
 * stitch-out evidence are not measured by this analysis.
 */
internal data class RasterSatinTrialSettings(
    val stitchSpacingMm: Float,
    val pullCompensationMm: Float,
    val centerUnderlay: Boolean
)

internal data class RasterSatinTrialResult(
    val settings: RasterSatinTrialSettings,
    val passedGeometryQa: Boolean,
    val reason: String?,
    val stitchCount: Int?,
    val jumpCount: Int?,
    val threadLengthMm: Double?,
    val peakThreadLengthMmPerSquareMm: Double?,
    val coverage: Double?,
    val offInkFraction: Double?,
    val machineApproved: Boolean = false
)

internal data class RasterSatinSensitivityReport(
    val machineModel: String,
    val fabric: RasterFabricClass,
    val stabilizer: RasterStabilizerClass,
    val trials: List<RasterSatinTrialResult>,
    val recommendedSettings: RasterSatinTrialSettings? = null,
    val exportPermitted: Boolean = false,
    val physicalStitchOutRequired: Boolean = true
)

/**
 * Explicit user context and bounded experiment only. The original QA Satin
 * default .5 mm / .15 mm / no underlay is not changed by this class.
 */
internal object RasterSatinParameterSensitivity {
    fun compare(
        source: RasterImagePixels,
        part: RasterSatinObject,
        sections: List<ManualSatinSection>,
        widthMm: Float,
        hoop: HoopProfile,
        context: RasterUnderlayReviewContext,
        alternatives: List<RasterSatinTrialSettings>
    ): Result<RasterSatinSensitivityReport> = runCatching {
        require(context.qaExperimentAcknowledged &&
            !context.machineModel.isNullOrBlank() &&
            context.machineModel.trim().length in 2..100
        ) { "Informe o modelo da máquina e confirme o ensaio exclusivamente QA." }
        require(context.fabric==RasterFabricClass.STABLE_WOVEN &&
            context.stabilizer in setOf(
                RasterStabilizerClass.CUT_AWAY,
                RasterStabilizerClass.TEAR_AWAY
            )
        ) {
            "Sem parâmetros físicos para este tecido/entretela: " +
                "não aplicar ajuste automático."
        }
        require(alternatives.size in 2..8 &&
            alternatives.distinct().size==alternatives.size
        ) {
            "Informe de 2 a 8 ensaios distintos de QA."
        }
        require(alternatives.all {
            it.stitchSpacingMm.isFinite() &&
                it.stitchSpacingMm in .40f.. .85f &&
                it.pullCompensationMm.isFinite() &&
                it.pullCompensationMm in 0f.. .25f
        }) {
            "Valores fora da janela de comparação experimental Satin."
        }
        require(sections.isNotEmpty() && sections.all {
            !it.connectToPrevious && it.rails.size in 2..100
        }) {
            "A comparação exige seções Satin independentes."
        }
        require(!RasterEmbroideryApproval.canExportFromImageTool()) {
            "Exportação PES de Imagem para Bordado permanece bloqueada."
        }
        // No passing candidate is ever allowed to bypass a letter counter.
        val m=part.mask
        val holes = if(m.maxX-m.minX+1>=8 && m.maxY-m.minY+1>=8)
            RasterCounterSatinGate.count(source,part).getOrThrow()
        else 0

        val trialResults=alternatives.map { settings ->
            runCatching {
                val design=RasterSatinObjectEditor.preview(
                    source=source,
                    item=part,
                    sections=sections,
                    fullWidthMm=widthMm,
                    hoop=hoop,
                    minimumCoverage=.90f,
                    qaCenterUnderlay=settings.centerUnderlay,
                    qaDensityMm=settings.stitchSpacingMm,
                    qaPullCompensationMm=settings.pullCompensationMm
                ).getOrThrow()
                require(design.format=="QA_SATIN_REVIEW" &&
                    design.sourceBytes.isEmpty() && design.isModified
                ) { "Ensaio não representa uma sequência Satin QA." }
                RasterNeedleRiskAudit.inspect(design,hoop).getOrThrow()
                val density=RasterThreadDensityPreflight.inspect(
                    design
                ).getOrThrow()
                if(holes>0) RasterCounterSatinGate.inspect(
                    source,part,design,widthMm
                ).getOrThrow()
                val fidelity=RasterStitchFidelityAudit.compare(
                    source,part,design,widthMm
                ).getOrThrow()
                require(fidelity.coverage>=.78 &&
                    fidelity.offInkFraction<=.10
                ) {
                    "A alternativa perdeu tinta ou atravessou regiões vazias."
                }
                RasterSatinTrialResult(
                    settings=settings,passedGeometryQa=true,
                    reason=null,stitchCount=design.stitchCount,
                    jumpCount=design.jumpCount,
                    threadLengthMm=density.sewnThreadLengthMm,
                    peakThreadLengthMmPerSquareMm=
                        density.peakThreadLengthMmPerCell,
                    coverage=fidelity.coverage,
                    offInkFraction=fidelity.offInkFraction
                )
            }.getOrElse { failure ->
                RasterSatinTrialResult(
                    settings=settings,passedGeometryQa=false,
                    reason=failure.message ?: "Revisão geométrica necessária.",
                    stitchCount=null,jumpCount=null,
                    threadLengthMm=null,
                    peakThreadLengthMmPerSquareMm=null,
                    coverage=null,offInkFraction=null
                )
            }
        }
        RasterSatinSensitivityReport(
            machineModel=context.machineModel.trim(),
            fabric=context.fabric,
            stabilizer=context.stabilizer,
            trials=trialResults
        )
    }
}
