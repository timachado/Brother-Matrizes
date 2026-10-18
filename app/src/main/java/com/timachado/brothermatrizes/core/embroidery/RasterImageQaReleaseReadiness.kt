package com.timachado.brothermatrizes.core.embroidery

/**
 * A conservative acceptance checklist for Imagem para Bordado.
 *
 * Software checks passing are NOT evidence of a successful Android UI run,
 * source-specific digitizing of complex photographs, fabric/needle stitch-out
 * or a compliant, transferable PES on an actual Brother embroidery machine.
 * These gates can only be credited after evidence is obtained outside this
 * automatic software test. Never enable PES from this report.
 */
internal data class RasterImageQaReleaseReadiness(
    val verifiedComponents: Int,
    val preservedStitches: Int,
    val jumps: Int,
    val internalTrims: Int,
    val boundaryTrims: Int,
    val colorChanges: Int,
    val softwareSequencePassed: Boolean = true,
    val softwareNeedleDensityChecksPassed: Boolean = true,
    val physicalAndroidUiVerified: Boolean = false,
    val originalComplexArtReconstructedAndReviewed: Boolean = false,
    val brotherStitchOutVerified: Boolean = false,
    val transferablePesVerified: Boolean = false,
    val exportPermitted: Boolean = false
) {
    val outstandingProductionGates: Int get() =
        listOf(
            physicalAndroidUiVerified,
            originalComplexArtReconstructedAndReviewed,
            brotherStitchOutVerified,
            transferablePesVerified
        ).count { !it }

    fun summary(): String =
        "QA de software: $verifiedComponents parte(s), " +
            "$preservedStitches STITCH, $jumps JUMP, " +
            "$internalTrims cortes internos, " +
            "$boundaryTrims cortes entre partes e " +
            "$colorChanges trocas de linha conferidos. " +
            "Pendências para versão definitiva: " +
            "interface no celular, reconstrução da arte complexa, " +
            "bordado físico em máquina Brother e PES verificado. " +
            "Não pronto para publicar/exportar."
}

internal object RasterImageQaReleaseReadinessAudit {
    fun inspect(
        assembly: RasterMixedTechniqueSequenceReview
    ): Result<RasterImageQaReleaseReadiness> = runCatching {
        val design = assembly.design
        val proof = assembly.integrityReport ?: error(
            "Falta auditoria independente da sequência completa."
        )
        require(
            design.format == "QA_AUTO_STITCH" &&
                design.isModified && design.sourceBytes.isEmpty() &&
                design.endFound &&
                design.points.lastOrNull()?.command == StitchCommand.END &&
                assembly.componentOrder.size in 2..12 &&
                assembly.componentOrder.distinct().size ==
                    assembly.componentOrder.size &&
                assembly.techniqueOrder.size == assembly.componentOrder.size &&
                proof.reviewedComponents == assembly.componentOrder.size &&
                proof.originalStitches == design.stitchCount &&
                proof.originalJumps == design.jumpCount &&
                proof.totalCommands == design.points.size &&
                proof.layerBoundaryTrims ==
                    assembly.componentOrder.size - 1 &&
                proof.layerBoundaryTrims + proof.internalTrims ==
                    assembly.trims &&
                proof.layerBoundaryColorChanges == assembly.colorChanges &&
                proof.internalTravelJumps == proof.internalTrims &&
                assembly.needsPhysicalStitchOut &&
                !assembly.exportPermitted &&
                !proof.exportPermitted &&
                !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Montagem QA alterada, não é matriz de produção." }
        val sequence=RasterThreadSequenceAudit.inspect(design).getOrThrow()
        val verifiedHoop = design.hoopProfile ?: error(
            "O bastidor da composição QA não está definido."
        )
        val needle=RasterNeedleRiskAudit.inspect(
            design,verifiedHoop
        ).getOrThrow()
        val density=RasterThreadDensityPreflight.inspect(
            design
        ).getOrThrow()
        require(
            sequence.stitches == proof.originalStitches &&
                sequence.jumps == proof.originalJumps &&
                sequence.trims == assembly.trims &&
                sequence.colorChanges == assembly.colorChanges &&
                needle.stitchesExamined == sequence.stitches &&
                density.sewnSegments == sequence.stitches
        ) { "Revisão de sequência, agulha ou densidade ficou desatualizada." }
        RasterImageQaReleaseReadiness(
            verifiedComponents=proof.reviewedComponents,
            preservedStitches=sequence.stitches,
            jumps=sequence.jumps,
            internalTrims=proof.internalTrims,
            boundaryTrims=proof.layerBoundaryTrims,
            colorChanges=sequence.colorChanges
        )
    }
}
