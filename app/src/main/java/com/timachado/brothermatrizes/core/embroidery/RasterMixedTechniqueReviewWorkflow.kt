package com.timachado.brothermatrizes.core.embroidery

/**
 * QA UI workflow: offer REAL non-exportable stitch previews for one selected
 * component. Nothing is silently confirmed or sewn.
 */
internal object RasterMixedTechniqueReviewWorkflow {
    private const val MAX_PARTS = 12

    fun prepare(
        source: RasterImagePixels,
        part: RasterSatinObject,
        technique: RasterTechniqueKind,
        widthMm: Float,
        hoop: HoopProfile
    ): Result<EmbroideryDesign> = runCatching {
        require(source.width in 8..512 && source.height in 8..512 &&
            source.argb.size == source.width * source.height &&
            widthMm.isFinite() && widthMm in 10f..200f &&
            widthMm <= hoop.usableWidthMm &&
            widthMm * source.height / source.width <= hoop.usableHeightMm &&
            part.mask.pixels.size == source.argb.size &&
            !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Componente QA ou bastidor incompatível com a prévia." }

        var sourcePixels = 0
        for (i in part.mask.pixels.indices) {
            if (part.mask.pixels[i] != 0) continue
            require(source.argb[i] == part.color) {
                "A família de cores planas não corresponde à foto original. " +
                    "Reconstrua a forma e os detalhes antes de gerar pontos."
            }
            sourcePixels++
        }
        require(sourcePixels == part.pixelCount && sourcePixels >= 15) {
            "O elemento perdeu pixels ou é pequeno demais para digitalização."
        }

        val draft = when (technique) {
            RasterTechniqueKind.TATAMI ->
                RasterTatamiRectanglePreview.create(
                    source,part,widthMm,hoop
                ).recoverCatching {
                    RasterTatamiShapePreview.create(
                        source,part,widthMm,hoop
                    ).getOrThrow()
                }.getOrThrow()
            RasterTechniqueKind.RUNNING ->
                RasterRunningOutlinePreview.create(
                    source,part,widthMm,hoop
                ).recoverCatching {
                    RasterCurvedRunningPreview.create(
                        source,part,widthMm,hoop
                    ).getOrThrow()
                }.recoverCatching {
                    RasterBranchedRunningPreview.create(
                        source,part,widthMm,hoop
                    ).getOrThrow()
                }.recoverCatching {
                    RasterClosedRunningPreview.create(
                        source,part,widthMm,hoop
                    ).getOrThrow()
                }.getOrThrow()
            RasterTechniqueKind.SATIN -> {
                val rails = RasterAutoSatinRails.propose(
                    source,part,widthMm
                ).map { listOf(it) }.recoverCatching {
                    RasterVariableWidthSatinSections.propose(
                        source,part,widthMm
                    ).getOrThrow()
                }.recoverCatching {
                    RasterSatinTopologyPlanner.propose(
                        source,part,widthMm
                    ).getOrThrow()
                }.getOrThrow()
                val holes = RasterCounterSatinGate.count(
                    source,part
                ).getOrThrow()
                RasterSatinRailGeometryGate.inspect(
                    source,part,rails,widthMm,holes
                ).getOrThrow()
                val stitched = RasterSatinObjectEditor.preview(
                    source,part,rails,widthMm,hoop,minimumCoverage=.90f
                ).getOrThrow()
                if (holes > 0) RasterCounterSatinGate.inspect(
                    source,part,stitched,widthMm
                ).getOrThrow()
                stitched
            }
        }
        require(draft.sourceBytes.isEmpty() && draft.endFound &&
            draft.isModified && draft.stitchCount > 0 &&
            draft.points.last().command == StitchCommand.END
        ) { "A prévia desta técnica não produziu pontadas reais de QA." }
        RasterNeedleRiskAudit.inspect(draft,hoop).getOrThrow()
        RasterThreadDensityPreflight.inspect(draft).getOrThrow()
        val fidelity = RasterStitchFidelityAudit.compare(
            source,part,draft,widthMm
        ).getOrThrow()
        val requiredCoverage = when(technique) {
            RasterTechniqueKind.SATIN -> .78
            RasterTechniqueKind.TATAMI -> .76
            RasterTechniqueKind.RUNNING -> .72
        }
        require(fidelity.coverage >= requiredCoverage &&
            fidelity.offInkFraction <=
                if(technique == RasterTechniqueKind.RUNNING) .15 else .10
        ) { "Esta técnica não cobriu o componente original. " +
            "Revise Satin, Tatami ou corrido manualmente; " +
            "nenhuma parte incompleta foi confirmada." }
        draft
    }

    /**
     * A complete set, never just the subset that happened to succeed.
     * Each reviewed layer must reference an unchanged detected component.
     */
    fun composeReviewed(
        source: RasterImagePixels,
        detected: List<RasterSatinObject>,
        confirmed: List<RasterReviewedTechniqueLayer>,
        widthMm: Float,
        hoop: HoopProfile
    ): Result<RasterMixedTechniqueSequenceReview> = runCatching {
        require(detected.size in 2..MAX_PARTS &&
            detected.map { it.id }.distinct().size == detected.size &&
            confirmed.size == detected.size &&
            confirmed.map { it.component.id }.toSet() ==
                detected.map { it.id }.toSet() &&
            !RasterEmbroideryApproval.canExportFromImageTool()
        ) {
            "A sequência precisa de todas as partes detectadas e " +
                "confirmadas. Nenhuma letra poderá ser omitida."
        }
        val byId = detected.associateBy { it.id }
        for (layer in confirmed) {
            val expected = byId[layer.component.id] ?:
                error("Parte fora da revisão da imagem original.")
            require(layer.component.color == expected.color &&
                layer.component.pixelCount == expected.pixelCount &&
                layer.component.mask.minX == expected.mask.minX &&
                layer.component.mask.minY == expected.mask.minY &&
                layer.component.mask.maxX == expected.mask.maxX &&
                layer.component.mask.maxY == expected.mask.maxY &&
                layer.component.mask.pixels.contentEquals(expected.mask.pixels)
            ) { "A máscara revisada foi substituída após a confirmação." }
        }
        RasterMixedTechniqueComposer.compose(
            source,widthMm,hoop,confirmed
        ).getOrThrow()
    }
}
