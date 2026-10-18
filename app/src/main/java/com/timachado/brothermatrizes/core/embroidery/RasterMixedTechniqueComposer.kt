package com.timachado.brothermatrizes.core.embroidery

/** Non-exportable, explicit sewing order of individually reviewed techniques. */
internal enum class RasterTechniqueKind { SATIN, TATAMI, RUNNING }

internal data class RasterReviewedTechniqueLayer(
    val component: RasterSatinObject,
    val design: EmbroideryDesign,
    val technique: RasterTechniqueKind,
    val sewingOrder: Int
)

internal data class RasterMixedTechniqueSequenceReview(
    val design: EmbroideryDesign,
    val componentOrder: List<Int>,
    val techniqueOrder: List<RasterTechniqueKind>,
    val trims: Int,
    val colorChanges: Int,
    val integrityReport: RasterMixedSequenceIntegrityReport? = null,
    val needsPhysicalStitchOut: Boolean = true,
    val exportPermitted: Boolean = false
) {
    fun message(): String =
        "QA: " + componentOrder.size + " partes revisadas, " +
            trims + " cortes e " + colorChanges + " trocas de linha. " +
            (integrityReport?.summary() ?: "Integridade pendente.") +
            " Sequência experimental, não matriz PES homologada."
}

internal object RasterMixedTechniqueComposer {
    private const val MAX_LAYERS = 12
    private const val MAX_COMMANDS = 90_000

    fun compose(
        source: RasterImagePixels,
        widthMm: Float,
        hoop: HoopProfile,
        reviewed: List<RasterReviewedTechniqueLayer>
    ): Result<RasterMixedTechniqueSequenceReview> = runCatching {
        require(source.width in 8..512 && source.height in 8..512 &&
            source.argb.size == source.width * source.height &&
            widthMm.isFinite() && widthMm in 10f..200f &&
            widthMm <= hoop.usableWidthMm &&
            widthMm * source.height / source.width <= hoop.usableHeightMm &&
            reviewed.size in 2..MAX_LAYERS &&
            reviewed.map { it.sewingOrder }.distinct().size == reviewed.size &&
            reviewed.map { it.component.id }.distinct().size == reviewed.size &&
            !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "QA exige partes distintas, bastidor e ordem explícita." }

        val layers = reviewed.sortedBy { it.sewingOrder }
        val output = ArrayList<EmbroideryPoint>()
        val colors = ArrayList<Int>()
        var last: EmbroideryPoint? = null
        var lastColor: Int? = null
        var changes = 0
        for ((index, layer) in layers.withIndex()) {
            val part = layer.component
            val input = layer.design
            val requiredFormat = when(layer.technique) {
                RasterTechniqueKind.SATIN -> "QA_SATIN_REVIEW"
                RasterTechniqueKind.TATAMI -> "QA_TATAMI_REVIEW"
                RasterTechniqueKind.RUNNING -> "QA_RUNNING_REVIEW"
            }
            require(input.format == requiredFormat &&
                input.isModified && input.sourceBytes.isEmpty() &&
                input.hoopProfile == hoop && input.endFound &&
                input.points.size in 3..MAX_COMMANDS &&
                input.points.first().command == StitchCommand.JUMP &&
                input.points.last().command == StitchCommand.END &&
                input.points.count { it.command == StitchCommand.END } == 1 &&
                input.points[input.points.lastIndex - 1].command ==
                    StitchCommand.STITCH &&
                input.threadColors == listOf(part.color) &&
                input.colorChanges == 0 && input.stitchCount > 0 &&
                input.jumpCount > 0 && input.points.all {
                    it.colorIndex == 0 &&
                        it.command !in setOf(
                            StitchCommand.COLOR_CHANGE,
                            StitchCommand.STOP, StitchCommand.SEQUIN
                        )
                }
            ) { "Parte " + (index + 1) + ": técnica ou comandos inválidos." }

            require(part.mask.pixels.size == source.argb.size &&
                part.pixelCount > 0 &&
                part.mask.minX in 0 until source.width &&
                part.mask.maxX in part.mask.minX until source.width &&
                part.mask.minY in 0 until source.height &&
                part.mask.maxY in part.mask.minY until source.height
            ) { "Máscara desconectada da foto original." }
            // Fidelity of STITCH to a mask cannot by itself prove that the
            // MASK still matches the import. Solid-color QA parts must be
            // checked against the ORIGINAL source before composing layers.
            var inkCount = 0
            for (i in part.mask.pixels.indices) {
                if (part.mask.pixels[i] != 0) continue
                require(source.argb[i] == part.color) {
                    "Parte " + part.id +
                        ": a máscara/cor divergiu da imagem original."
                }
                inkCount++
            }
            require(inkCount == part.pixelCount) {
                "Contagem de tinta da máscara foi alterada."
            }
            val fidelity = RasterStitchFidelityAudit.compare(
                source,part,input,widthMm
            ).getOrThrow()
            val required = when(layer.technique) {
                RasterTechniqueKind.RUNNING -> .72
                RasterTechniqueKind.TATAMI -> .76
                RasterTechniqueKind.SATIN -> .78
            }
            require(fidelity.coverage >= required &&
                fidelity.offInkFraction <= if(
                    layer.technique==RasterTechniqueKind.RUNNING
                ) .15 else .10
            ) { "Parte " + part.id + ": pontos não correspondem à tinta original." }
            RasterNeedleRiskAudit.inspect(input,hoop).getOrThrow()
            RasterThreadDensityPreflight.inspect(input).getOrThrow()
            if(layer.technique==RasterTechniqueKind.SATIN) {
                val holes = RasterCounterSatinGate.count(source,part).getOrThrow()
                if(holes>0) RasterCounterSatinGate.inspect(
                    source,part,input,widthMm
                ).getOrThrow()
            }
            val change = part.color != lastColor
            if(change) {
                require(colors.size < 25)
                colors += part.color
                lastColor = part.color
            }
            val colorIndex = colors.lastIndex
            last?.let { prev ->
                output += EmbroideryPoint(
                    prev.xUnits,prev.yUnits,StitchCommand.TRIM,
                    if(change) colorIndex-1 else colorIndex
                )
                if(change) {
                    output += EmbroideryPoint(
                        prev.xUnits,prev.yUnits,
                        StitchCommand.COLOR_CHANGE,colorIndex
                    )
                    changes++
                }
            }
            output.addAll(input.points.dropLast(1).map {
                it.copy(colorIndex=colorIndex)
            })
            last = output.lastOrNull()
            require(last?.command==StitchCommand.STITCH &&
                output.size < MAX_COMMANDS
            ) { "A sequência excedeu os limites QA." }
        }
        val finish = last ?: error("Sem pontadas verificadas.")
        output += EmbroideryPoint(
            finish.xUnits,finish.yUnits,StitchCommand.END,colors.lastIndex
        )
        val located = output.filter {
            it.command==StitchCommand.STITCH ||
                it.command==StitchCommand.JUMP
        }
        val assembled = EmbroideryDesign(
            fileName="CAMADAS-MISTAS-REVISAO.qa",
            format="QA_AUTO_STITCH",
            label="Composição em QA, NÃO enviar à máquina",
            points=output,
            bounds=EmbroideryBounds(
                located.minOf { it.xUnits },located.maxOf { it.xUnits },
                located.minOf { it.yUnits },located.maxOf { it.yUnits }
            ),
            stitchCount=output.count { it.command==StitchCommand.STITCH },
            jumpCount=output.count { it.command==StitchCommand.JUMP },
            colorChanges=changes,endFound=true,
            sourceBytes=ByteArray(0),threadColors=colors,
            isModified=true,hoopProfile=hoop
        )
        val integrity = RasterMixedSequenceIntegrityAudit.inspect(
            assembled,layers
        ).getOrThrow()
        val sequence=RasterThreadSequenceAudit.inspect(assembled).getOrThrow()
        val needle=RasterNeedleRiskAudit.inspect(assembled,hoop).getOrThrow()
        val density=RasterThreadDensityPreflight.inspect(assembled).getOrThrow()
        val overlap=RasterColorLayerOverlapAudit.inspect(assembled).getOrThrow()
        require(sequence.trims >= layers.size-1 &&
            sequence.colorChanges==changes &&
            needle.stitchesExamined==sequence.stitches &&
            density.sewnSegments==sequence.stitches &&
            overlap.sewnSegments==sequence.stitches &&
            !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Revisão da sequência, cortes ou densidade recusada." }
        RasterMixedTechniqueSequenceReview(
            design=assembled,
            componentOrder=layers.map { it.component.id },
            techniqueOrder=layers.map { it.technique },
            trims=sequence.trims,colorChanges=changes,
            integrityReport=integrity
        )
    }
}
