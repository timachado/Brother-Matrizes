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
    /** Immutable starting rails used to bound the ENTIRE editing session. */
    val baselineSections: List<ManualSatinSection> = sections,
    val maxEndpointDriftMm: Double = 0.0,
    val previousCoverage: Double? = null,
    val previousOffInkFraction: Double? = null,
    val previousStitchCount: Int? = null,
    val revision: Int = 0,
    val reviewRequired: Boolean = true,
    val exportPermitted: Boolean = false
) {
    fun summary(): String =
        sections.size.toString() + " seção(ões) Satin, " +
            preview.stitchCount + " STITCH; cobertura " +
            (coverage * 100).toInt() + "%; fora da tinta " +
            (offInkFraction * 100).toInt() + "%. " +
            "Deslocamento máximo desde o original: " +
            "%.2f".format(java.util.Locale.ROOT,maxEndpointDriftMm) +
            " mm. Trilhos editáveis, PES bloqueado."

    fun changeSummary(): String =
        if (previousCoverage == null || previousOffInkFraction == null ||
            previousStitchCount == null) {
            "Primeira prévia Satin de QA, sem comparação anterior."
        } else {
            val coverageChange = (coverage - previousCoverage) * 100
            val outsideChange = (offInkFraction - previousOffInkFraction) * 100
            val stitchChange = preview.stitchCount - previousStitchCount
            "Antes/depois (STITCH reais): cobertura " +
                "%+.1f".format(java.util.Locale.ROOT,coverageChange) +
                " p.p.; pontos fora da tinta " +
                "%+.1f".format(java.util.Locale.ROOT,outsideChange) +
                " p.p.; " + (if (stitchChange >= 0) "+" else "") +
                stitchChange + " STITCH. Comparação QA, não é PES."
        }
}

internal enum class RasterLetterRailEdge { A, B }
internal enum class RasterLetterRailAxis { X, Y }

internal object RasterLetterEditableSatinReview {
    private const val MAX_SECTIONS = 24
    private const val MAX_PAIRS = 1500
    private const val MAX_NUDGE_MM = .20f
    private const val MAX_BASELINE_DRIFT_MM = .50
    private const val MAX_TOTAL_DRIFT_MM = 8.0

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
        validate(
            original,part,sections,widthMm,hoop,diagnosis,0,
            baselineSections=sections
        )
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

        // Recheck that the original snapshot is still the SAME set of
        // physically valid stitches. A stale or forged in-memory draft cannot
        // serve as a baseline for further needle movement.
        val oldChecked = validate(
            original,part,previous.sections,widthMm,hoop,
            previous.diagnosis,previous.revision,
            baselineSections=previous.baselineSections
        )
        require(oldChecked.preview.points == previous.preview.points &&
            oldChecked.coverage == previous.coverage &&
            oldChecked.offInkFraction == previous.offInkFraction
        ) { "Prévia alterada ou desatualizada: recalcule o Satin." }

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
            previous.revision+1,
            baselineSections=previous.baselineSections
        )
        // Quantization to tenths of a millimetre can make small moves a no-op.
        // Do not pretend that a geometric change improved the stitches.
        require(tested.preview.points != previous.preview.points) {
            "Deslocamento não alterou os STITCH reais; escolha outro trilho."
        }
        tested.copy(
            previousCoverage=previous.coverage,
            previousOffInkFraction=previous.offInkFraction,
            previousStitchCount=previous.preview.stitchCount
        )
    }

    /**
     * Split an existing Satin curve into TWO independent sew sections.
     * Overlap exactly the original seam rail; the generator must insert
     * TRIM + JUMP and all post-quantized audits must pass.
     *
     * The split returns a NEW section topology but keeps the original
     * coordinate baseline by applying the SAME split to the baseline rails.
     * This prevents bypassing cumulative endpoint drift limits through
     * repeated splitting. UI must obtain explicit operator consent.
     * Existing previews/history are NOT modified by a rejected split.
     */
    fun proposeIndependentSplit(
        original: RasterImagePixels,
        part: RasterSatinObject,
        previous: RasterLetterSatinDraft,
        sectionIndex: Int,
        splitPairIndex: Int,
        widthMm: Float,
        hoop: HoopProfile
    ): Result<RasterLetterSatinDraft> = runCatching {
        require(
            previous.sections.size in 1 until MAX_SECTIONS &&
                sectionIndex in previous.sections.indices &&
                previous.revision in 0..100 &&
                !previous.exportPermitted &&
                !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Seção Satin fora dos limites da divisão QA." }

        val earlier = validate(
            original,part,previous.sections,widthMm,hoop,
            previous.diagnosis,previous.revision,
            baselineSections=previous.baselineSections
        )
        require(
            earlier.preview.points==previous.preview.points &&
                earlier.preview.threadColors==previous.preview.threadColors &&
                earlier.coverage==previous.coverage &&
                earlier.offInkFraction==previous.offInkFraction
        ) { "Os trilhos foram alterados após a última conferência de STITCH." }

        val selected = previous.sections[sectionIndex]
        val railCount = selected.rails.size
        require(railCount >= 12 && splitPairIndex in 5..railCount-6) {
            "É preciso pelo menos seis pares de trilhos seguros " +
                "em cada trecho de uma junção Satin."
        }
        // The seam rail belongs to both physical sections so the edge is
        // not dropped from the source shape. No new coordinates invented.
        val first = ManualSatinSection(
            selected.rails.subList(0,splitPairIndex+1).toList(),
            connectToPrevious=selected.connectToPrevious
        )
        val second = ManualSatinSection(
            selected.rails.subList(splitPairIndex,railCount).toList(),
            connectToPrevious=false
        )
        val updated = previous.sections.take(sectionIndex) + listOf(
            first,second
        ) + previous.sections.drop(sectionIndex+1)
        val baselineSource=previous.baselineSections
        require(baselineSource.size==previous.sections.size &&
            baselineSource[sectionIndex].rails.size==railCount
        ) { "O limite acumulado Satin não acompanha a topologia anterior." }
        val base=baselineSource[sectionIndex]
        val baselineAfterSplit=baselineSource.take(sectionIndex) + listOf(
            ManualSatinSection(
                base.rails.subList(0,splitPairIndex+1).toList(),
                connectToPrevious=base.connectToPrevious
            ),
            ManualSatinSection(
                base.rails.subList(splitPairIndex,railCount).toList(),
                connectToPrevious=false
            )
        ) + baselineSource.drop(sectionIndex+1)
        require(updated.sumOf { it.rails.size } ==
            previous.sections.sumOf { it.rails.size }+1 &&
            updated[sectionIndex+1].rails.first() ==
                updated[sectionIndex].rails.last()
        ) { "Divisão Satin perdeu pares ou gerou junção falsa." }

        val fresh=RasterLetterTopologyReview.inspect(original,part)
            .getOrThrow()
        require(fresh==previous.diagnosis) {
            "Imagem de referência foi alterada antes da divisão."
        }
        val result = validate(
            original,part,updated,widthMm,hoop,fresh,
            0,baselineSections=baselineAfterSplit
        )
        val beforeTrims=previous.preview.points.count {
            it.command==StitchCommand.TRIM
        }
        val afterTrims=result.preview.points.count {
            it.command==StitchCommand.TRIM
        }
        val beforeJumps=previous.preview.jumpCount
        require(afterTrims==beforeTrims+1 &&
            result.preview.jumpCount>=beforeJumps+1 &&
            result.preview.points!=previous.preview.points &&
            result.preview.colorChanges==0 &&
            result.preview.sourceBytes.isEmpty() &&
            result.preview.points.last().command==StitchCommand.END
        ) {
            "A divisão não introduziu um TRIM/JUMP real e seguro " +
                "entre os dois trechos Satin. Não foi aplicada."
        }
        // Do not silently accept a regression as an improvement. A new
        // section is only offered when the full raster coverage remains
        // inside the independently preflighted range.
        require(result.coverage>=previous.coverage-.02 &&
            result.offInkFraction<=previous.offInkFraction+.01
        ) { "A divisão reduziu a cobertura ou aumentou pontos fora da tinta." }
        result.copy(
            previousCoverage=previous.coverage,
            previousOffInkFraction=previous.offInkFraction,
            previousStitchCount=previous.preview.stitchCount
        )
    }

    /**
     * One-level undo target is ALWAYS independently re-audited; merely
     * reading the UI history is never treated as permission to stitch.
     */
    fun restorePrevious(
        original: RasterImagePixels,
        part: RasterSatinObject,
        current: RasterLetterSatinDraft,
        previous: RasterLetterSatinDraft,
        widthMm: Float,
        hoop: HoopProfile
    ): Result<RasterLetterSatinDraft> = runCatching {
        require(current.revision == previous.revision + 1 &&
            current.baselineSections == previous.baselineSections &&
            !current.exportPermitted && !previous.exportPermitted &&
            !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Histórico Satin incompatível; refaça a proposta QA." }
        val fresh=RasterLetterTopologyReview.inspect(original,part)
            .getOrThrow()
        require(current.diagnosis == fresh && previous.diagnosis == fresh) {
            "A origem foi alterada após o ajuste Satin."
        }
        val checked=validate(
            original,part,previous.sections,widthMm,hoop,
            fresh,previous.revision,previous.baselineSections
        )
        require(checked.preview.points == previous.preview.points) {
            "O histórico perdeu os pontos reais originais."
        }
        checked
    }

    private fun validate(
        source: RasterImagePixels,
        part: RasterSatinObject,
        rails: List<ManualSatinSection>,
        widthMm: Float,
        hoop: HoopProfile,
        topology: RasterLetterTopologyReport,
        revision: Int,
        baselineSections: List<ManualSatinSection>
    ): RasterLetterSatinDraft {
        require(rails.size in 1..MAX_SECTIONS &&
            rails.sumOf { it.rails.size } in 2..MAX_PAIRS &&
            widthMm.isFinite() && widthMm in 10f..200f &&
            widthMm <= hoop.usableWidthMm &&
            widthMm * source.height/source.width <= hoop.usableHeightMm &&
            !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Trilhos ou bastidor fora do limite de edição QA." }
        val maxDrift = inspectBaselineDrift(
            source,part,rails,baselineSections,widthMm
        )
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
            diagnosis=topology,revision=revision,
            baselineSections=baselineSections,
            maxEndpointDriftMm=maxDrift
        )
    }

    private fun inspectBaselineDrift(
        source: RasterImagePixels,
        part: RasterSatinObject,
        current: List<ManualSatinSection>,
        baseline: List<ManualSatinSection>,
        widthMm: Float
    ): Double {
        require(current.size == baseline.size) {
            "Seções Satin mudaram após a proposta inicial."
        }
        val mmPerPixel=widthMm/source.width.toDouble()
        val width=(part.mask.maxX-part.mask.minX+1)*mmPerPixel
        val height=(part.mask.maxY-part.mask.minY+1)*mmPerPixel
        var greatest=0.0
        var summed=0.0
        for (sectionIndex in current.indices) {
            val a=current[sectionIndex]
            val b=baseline[sectionIndex]
            require(a.rails.size == b.rails.size &&
                a.connectToPrevious == b.connectToPrevious
            ) { "Trilhos Satin perderam pares ou conexões originais." }
            for (index in a.rails.indices) {
                val p=a.rails[index]
                val q=b.rails[index]
                val edges=listOf(
                    kotlin.math.hypot(
                        (p.ax-q.ax).toDouble()*width,(p.ay-q.ay).toDouble()*height
                    ),
                    kotlin.math.hypot(
                        (p.bx-q.bx).toDouble()*width,(p.by-q.by).toDouble()*height
                    )
                )
                for (drift in edges) {
                    require(drift.isFinite())
                    greatest=maxOf(greatest,drift)
                    summed+=drift
                }
            }
        }
        require(greatest <= MAX_BASELINE_DRIFT_MM+1e-6 &&
            summed <= MAX_TOTAL_DRIFT_MM+1e-6
        ) {
            "A soma dos ajustes ultrapassou o limite acumulado " +
                "de segurança dos trilhos Satin. Reinicie a revisão."
        }
        return greatest
    }
}
