package com.timachado.brothermatrizes.core.embroidery

/**
 * QA-only independent proof that the full Simular Bordado command stream
 * conserves every reviewed STITCH and JUMP in the requested sewing order.
 * TRIM is compulsory between components, COLOR_CHANGE when thread changes.
 * This does not encode PES, generate new needle paths or certify a machine.
 */
internal data class RasterMixedSequenceIntegrityReport(
    val reviewedComponents: Int,
    val originalStitches: Int,
    val originalJumps: Int,
    val totalCommands: Int,
    val layerBoundaryTrims: Int,
    val layerBoundaryColorChanges: Int,
    val internalTrims: Int = 0,
    val internalTravelJumps: Int = 0,
    val exportPermitted: Boolean = false
) {
    fun summary(): String =
        reviewedComponents.toString() + " componentes, " + originalStitches +
            " STITCH originais preservados, " + layerBoundaryTrims +
            " cortes entre partes, " + internalTrims +
            " cortes internos, " + internalTravelJumps +
            " deslocamentos após cortes internos, " + layerBoundaryColorChanges +
            " trocas de linha. Sem atalhos costurados. PES bloqueado."
}

internal object RasterMixedSequenceIntegrityAudit {
    fun inspect(
        composed: EmbroideryDesign,
        layers: List<RasterReviewedTechniqueLayer>
    ): Result<RasterMixedSequenceIntegrityReport> = runCatching {
        require(
            composed.format == "QA_AUTO_STITCH" &&
                composed.isModified && composed.sourceBytes.isEmpty() &&
                composed.endFound &&
                composed.points.size in 3..90_000 &&
                layers.size in 2..12 &&
                layers.map { it.component.id }.distinct().size == layers.size &&
                layers.map { it.sewingOrder }.distinct().size == layers.size &&
                !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Prova de sequência exige camadas QA revisadas, não PES." }

        val ordered = layers.sortedBy { it.sewingOrder }
        val actual = composed.points
        val colors = ArrayList<Int>()
        var cursor = 0
        var lastSewn: EmbroideryPoint? = null
        var stitches = 0
        var jumps = 0
        var boundaryTrims = 0
        var internalTrims = 0
        var internalTravelJumps = 0
        var changes = 0

        fun consume(expected: EmbroideryPoint, reason: String) {
            require(cursor < actual.size) {
                "Comando ausente na montagem: " + reason
            }
            val p = actual[cursor++]
            require(
                p.command == expected.command &&
                    p.xUnits == expected.xUnits &&
                    p.yUnits == expected.yUnits &&
                    p.colorIndex == expected.colorIndex
            ) {
                "Comando alterado no índice " + cursor + ": " + reason
            }
        }
        for ((index, layer) in ordered.withIndex()) {
            val part = layer.component
            val input = layer.design
            val required = when (layer.technique) {
                RasterTechniqueKind.SATIN -> "QA_SATIN_REVIEW"
                RasterTechniqueKind.TATAMI -> "QA_TATAMI_REVIEW"
                RasterTechniqueKind.RUNNING -> "QA_RUNNING_REVIEW"
            }
            require(
                input.format == required &&
                    input.sourceBytes.isEmpty() && input.isModified &&
                    input.endFound && input.hoopProfile == composed.hoopProfile &&
                    input.threadColors == listOf(part.color) &&
                    input.colorChanges == 0 && input.stitchCount > 0 &&
                    input.jumpCount > 0 && input.points.size in 3..90_000 &&
                    input.points.first().command == StitchCommand.JUMP &&
                    input.points.last().command == StitchCommand.END &&
                    input.points.dropLast(1).all {
                        it.colorIndex == 0 &&
                            it.command !in setOf(
                                StitchCommand.COLOR_CHANGE,
                                StitchCommand.END, StitchCommand.STOP,
                                StitchCommand.SEQUIN
                            )
                    }
            ) { "Camada revisada incompatível com o compositor." }

            val changeColor = colors.lastOrNull() != part.color
            if (changeColor) colors += part.color
            val colorIndex = colors.lastIndex
            if (index > 0) {
                val prev = lastSewn ?: error("Parte anterior sem STITCH.")
                consume(
                    EmbroideryPoint(
                        prev.xUnits, prev.yUnits, StitchCommand.TRIM,
                        if (changeColor) colorIndex - 1 else colorIndex
                    ), "TRIM entre componentes"
                )
                boundaryTrims++
                if (changeColor) {
                    consume(
                        EmbroideryPoint(
                            prev.xUnits, prev.yUnits,
                            StitchCommand.COLOR_CHANGE, colorIndex
                        ), "Troca de linha entre componentes"
                    )
                    changes++
                }
            }
            val commands = input.points.dropLast(1)
            // A split Satin or another manually reviewed sub-section must
            // explicitly TRIM then JUMP. Never silently use an STITCH to
            // cross an unsewn gap after a requested cut.
            for ((position,command) in commands.withIndex()) {
                if (command.command == StitchCommand.TRIM) {
                    require(position > 0 &&
                        commands[position-1].command == StitchCommand.STITCH &&
                        position+1 < commands.size &&
                        commands[position+1].command == StitchCommand.JUMP &&
                        commands[position+1].colorIndex == 0
                    ) { "Corte interno sem deslocamento JUMP imediato." }
                    internalTrims++
                    internalTravelJumps++
                }
                consume(
                    command.copy(colorIndex=colorIndex),
                    "Pontada original da parte " + part.id
                )
                if (command.command == StitchCommand.STITCH) stitches++
                if (command.command == StitchCommand.JUMP) jumps++
            }
            lastSewn = commands.lastOrNull()?.takeIf {
                it.command == StitchCommand.STITCH
            }?.copy(colorIndex=colorIndex)
            require(lastSewn != null) { "Parte termina sem STITCH real." }
        }
        val last = lastSewn ?: error("Nenhum STITCH na composição.")
        consume(
            EmbroideryPoint(
                last.xUnits,last.yUnits,StitchCommand.END,colors.lastIndex
            ), "END único e coerente"
        )
        require(
            cursor == actual.size &&
                composed.threadColors == colors &&
                composed.stitchCount == stitches &&
                composed.jumpCount == jumps &&
                composed.colorChanges == changes
        ) { "Camadas, pontos ou cores não conservados." }
        val audit = RasterThreadSequenceAudit.inspect(composed).getOrThrow()
        require(
            audit.stitches == stitches && audit.jumps == jumps &&
                audit.colorChanges == changes &&
                audit.trims == boundaryTrims + internalTrims &&
                internalTravelJumps == internalTrims
        ) { "Sequência montada inconsistente: cortes internos ou entre partes." }
        RasterMixedSequenceIntegrityReport(
            reviewedComponents=ordered.size,
            originalStitches=stitches,
            originalJumps=jumps,
            totalCommands=cursor,
            layerBoundaryTrims=boundaryTrims,
            layerBoundaryColorChanges=changes,
            internalTrims=internalTrims,
            internalTravelJumps=internalTravelJumps
        )
    }
}
