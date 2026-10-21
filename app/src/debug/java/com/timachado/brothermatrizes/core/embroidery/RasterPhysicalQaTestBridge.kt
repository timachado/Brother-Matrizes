package com.timachado.brothermatrizes.core.embroidery

/**
 * DEBUG-ONLY handoff for a physical Brother test on scrap fabric.
 *
 * This deliberately does NOT enable RasterEmbroideryApproval or production
 * MatrixConverter export of QA designs. It creates a narrowly restricted
 * throwaway PES fixture from the real checked running-stitch raster pipeline.
 * Never call it without the two-step explicit operator confirmation in UI.
 *
 * Same-package release stub always rejects this operation.
 */
internal object RasterPhysicalQaTestBridge {
    fun createOutlinePes(
        source: RasterImagePixels,
        widthMm: Float,
        hoop: HoopProfile
    ): Result<ConvertedMatrix> = runCatching {
        require(widthMm.isFinite() && widthMm in 10f..100f) {
            "Homologação limitada a um contorno de 10–100 mm."
        }
        require(!RasterEmbroideryApproval.canExportFromImageTool()) {
            "O bloqueio comercial de exportação precisa permanecer ativo."
        }
        val qa = RasterQaRunningStitchDraft.create(
            source, widthMm, hoop
        ).getOrThrow()
        val design = qa.design
        require(qa.needsMachineStitchOut && qa.needsOperatorReview &&
            !qa.exportPermitted && design.isExperimentalImageQa &&
            design.format == "QA_RUNNING_REVIEW" &&
            design.stitchCount in 10..3500 &&
            qa.outlineCount in 1..8 &&
            qa.threadDensity.sewnSegments == design.stitchCount &&
            qa.needleRisk.stitchesExamined == design.stitchCount &&
            design.colorChanges == 0 &&
            design.threadColors.size == 1 &&
            design.points.last().command == StitchCommand.END
        ) { "A amostra excede o escopo seguro de avaliação inicial." }
        // Trust no stale/modified stitch metadata. Software-only proof;
        // physical compatibility is precisely what this sample will test.
        RasterThreadSequenceAudit.inspect(design).getOrThrow()
        RasterNeedleRiskAudit.inspect(design,hoop).getOrThrow()
        RasterThreadDensityPreflight.inspect(design).getOrThrow()
        // App-level QA exports remain denied even in the DEBUG build.
        require(MatrixConverter.convert(design, "PES").isFailure) {
            "A proteção comercial de QA deixou de funcionar."
        }

        // A transient codec source ONLY in this build variant. The flag
        // override is impossible from release (stub) or regular UI export.
        val probe = design.copy(
            fileName="TESTE_CONTORNO_BROTHER_NAO_HOMOLOGADO.pes",
            format="PES",
            label="AMOSTRA QA CONTORNO - NAO COMERCIAL",
            isExperimentalImageQa=false
        )
        val encoded = MatrixConverter.convert(
            probe, "PES", "TESTE_FISICO_NAO_HOMOLOGADO"
        ).getOrThrow()
        val readback = PesReadbackEvidence.inspect(
            probe,encoded.fileName,encoded.bytes
        ).getOrThrow()
        require(readback.geometryPreserved &&
            readback.stitchOrderPreserved &&
            !readback.externalDecoderVerified &&
            !readback.machineCertified &&
            !readback.authorizesImagePesExport
        ) { "Falha na releitura da amostra PES." }
        require(!RasterEmbroideryApproval.canExportFromImageTool()) {
            "Exportação comercial não pode ser ativada pelo teste."
        }
        encoded
    }
}
