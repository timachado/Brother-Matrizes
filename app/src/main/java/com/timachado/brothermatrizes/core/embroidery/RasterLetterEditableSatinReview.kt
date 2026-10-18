package com.timachado.brothermatrizes.core.embroidery

/**
 * QA-only bridge between LETTER TOPOLOGY diagnosis and EDITABLE Satin rails.
 * A successful proposal is NOT an accepted layer; every trial uses actual
 * STITCH geometry and independent counter, density and needle audits.
 * The operator must inspect and confirm the new preview manually.
 */
internal data class RasterLetterSatinDraft(
    val sections: List<ManualSatinSection>,
    val preview: EmbroideryDesign,
    val coverage: Double,
    val offInkFraction: Double,
    val diagnosis: RasterLetterTopologyReport,
    val revision: Int = 0,
    val reviewRequired: Boolean = true,
    val exportPermitted: Boolean = false
) {
    fun summary(): String =
        sections.size.toString() + " seção(ões) Satin, " +
            preview.stitchCount + " STITCH; cobertura " +
            (coverage * 100).toInt() + "%; fora da tinta " +
            (offInkFraction * 100).toInt() + "%. " +
            "Trilhos editáveis, prévia técnica QA; PES bloqueado."
}

internal enum class RasterLetterRailEdge { A, B }
internal enum class RasterLetterRailAxis { X, Y }

internal object RasterLetterEditableSatinReview {
    private const val MAX_SECTIONS = 24
    private const val MAX_PAIRS = 1500
    private const val MAX_NUDGE_MM = .20f

    fun propose(
        original: RasterImagePixels,
        part: RasterSatinObject,
        widthMm: Float,
        hoop: HoopProfile
    ): Result<RasterLetterSatinDraft> = runCatching {
        val diagnosis = RasterLetterTopologyReview.inspect(original,part)
            .getOrThrow()
        require(diagnosis.ambiguousDiagonalContacts == 0 &&
            diagnosis.vectorOutlineValid &&
            (diagnosis.enclosedCounters == 0 ||
                diagnosis.smallestCounterPixels >= 16) &&
            diagnosis.enclosedCounters <= 2
        ) {
            "Junção diagonal ou vazado estreito: reconstrua a letra " +
                "manualmente antes de criar trilhos Satin."
        }
        val sections = RasterSatinTopologyPlanner.propose(
            original,part,widthMm
        ).recoverCatching {
            RasterAutoSatinRails.propose(
                original,part,widthMm
            ).getOrThrow().let { listOf(it) }
        }.getOrThrow()
        validate(original,part,sections,widthMm,hoop,diagnosis,0)
    }

    /**
     * Moves exactly one rail endpoint on one axis, by <= 0.20 mm.
     * No partial mutation: an unsafe or ineffective proposal is returned
     * as failure, and the previous draft remains intact.
     */
    fun nudge(
        original: RasterImagePixels,
        part: RasterSatinObject,
        previous: RasterLetterSatinDraft,
        sectionIndex: Int,
        pairIndex: Int,
        edge: RasterLetterRailEdge,
        axis: RasterLetterRailAxis,
        deltaMm: Float,
        widthMm: Float,
        hoop: HoopProfile
    ): Result<RasterLetterSatinDraft> = runCatching {
        require(deltaMm.isFinite() && kotlin.math.abs(deltaMm) in
            .0001f..MAX_NUDGE_MM &&
            sectionIndex in previous.sections.indices &&
            pairIndex in previous.sections[sectionIndex].rails.indices &&
            previous.revision in 0..100 &&
            !previous.exportPermitted &&
            !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Ajuste QA limitado a uma borda Satin e 0,20 mm." }

        val box = part.mask
        val pixelsForAxis = when (axis) {
            RasterLetterRailAxis.X -> box.maxX-box.minX+1
            RasterLetterRailAxis.Y -> box.maxY-box.minY+1
        }
        val step = deltaMm * original.width /
            (widthMm * pixelsForAxis)
        val section = previous.sections[sectionIndex]
        val old = section.rails[pairIndex]
        val changed = when (edge to axis) {
            RasterLetterRailEdge.A to RasterLetterRailAxis.X ->
                old.copy(ax=old.ax+step)
            RasterLetterRailEdge.A to RasterLetterRailAxis.Y ->
                old.copy(ay=old.ay+step)
            RasterLetterRailEdge.B to RasterLetterRailAxis.X ->
                old.copy(bx=old.bx+step)
            RasterLetterRailEdge.B to RasterLetterRailAxis.Y ->
                old.copy(by=old.by+step)
            else -> error("Borda Satin inválida.")
        }
        val updated = previous.sections.mapIndexed { sectionId, selected ->
            if(sectionId != sectionIndex) selected
            else selected.copy(
                rails=selected.rails.mapIndexed { index, pair ->
                    if(index == pairIndex) changed else pair
                }
            )
        }
        val fresh = RasterLetterTopologyReview.inspect(
            original,part
        ).getOrThrow()
        require(fresh == previous.diagnosis) {
            "A imagem ou máscara foi alterada após criar os trilhos."
        }
        val tested = validate(
            original,part,updated,widthMm,hoop,fresh,
            previous.revision+1
        )
        // Quantization to tenths of a millimetre can make small moves a no-op.
        // Do not pretend that a geometric change improved the stitches.
        require(tested.preview.points != previous.preview.points) {
            "Deslocamento não alterou os STITCH reais; escolha outro trilho."
        }
        tested
    }

    private fun validate(
        source: RasterImagePixels,
        part: RasterSatinObject,
        rails: List<ManualSatinSection>,
        widthMm: Float,
        hoop: HoopProfile,
        topology: RasterLetterTopologyReport,
        revision: Int
    ): RasterLetterSatinDraft {
        require(rails.size in 1..MAX_SECTIONS &&
            rails.sumOf { it.rails.size } in 2..MAX_PAIRS &&
            widthMm.isFinite() && widthMm in 10f..200f &&
            widthMm <= hoop.usableWidthMm &&
            widthMm * source.height/source.width <= hoop.usableHeightMm &&
            !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Trilhos ou bastidor fora do limite de edição QA." }
        RasterSatinRailGeometryGate.inspect(
            source,part,rails,widthMm,topology.enclosedCounters
        ).getOrThrow()
        val preview = RasterSatinObjectEditor.preview(
            source,part,rails,widthMm,hoop,minimumCoverage=.68f
        ).getOrThrow()
        if(topology.enclosedCounters > 0) {
            RasterCounterSatinGate.inspect(
                source,part,preview,widthMm
            ).getOrThrow()
        }
        RasterNeedleRiskAudit.inspect(preview,hoop).getOrThrow()
        RasterThreadDensityPreflight.inspect(preview).getOrThrow()
        val fidelity = RasterStitchFidelityAudit.compare(
            source,part,preview,widthMm
        ).getOrThrow()
        require(fidelity.coverage >= .78 &&
            fidelity.offInkFraction <= .10 &&
            preview.sourceBytes.isEmpty() &&
            preview.format == "QA_SATIN_REVIEW" &&
            preview.points.last().command == StitchCommand.END
        ) {
            "Os pontos Satin não cobrem a letra com fidelidade " +
                "suficiente; ajuste manual e revise o mapa de cobertura."
        }
        return RasterLetterSatinDraft(
            sections=rails,preview=preview,
            coverage=fidelity.coverage,
            offInkFraction=fidelity.offInkFraction,
            diagnosis=topology,revision=revision
        )
    }
}
