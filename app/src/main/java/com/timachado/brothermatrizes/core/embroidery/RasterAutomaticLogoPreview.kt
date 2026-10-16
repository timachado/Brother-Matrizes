package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.hypot

/**
 * First genuinely automatic whole-artwork QA preview for simple logos.
 *
 * Each connected color object is digitized independently from its ORIGINAL
 * image geometry and is validated against its own source mask. The paths are
 * then assembled in explicit order with TRIM/JUMP/COLOR_CHANGE commands.
 *
 * All-or-nothing: if even one component needs professional digitization
 * (tatami/outline/letter split), NO whole-logo simulation is emitted.
 * Image previews and passed unit tests do NOT constitute PES approval.
 */
internal object RasterAutomaticLogoPreview {
    private const val MAX_OBJECTS = 24
    private const val MAX_STITCHES = 90_000
    private const val MAX_COLOR_CHANGES = 24

    fun create(
        source: RasterImagePixels,
        widthMm: Float,
        hoop: HoopProfile
    ): Result<EmbroideryDesign> = runCatching {
        require(source.width in 8..512 && source.height in 8..512 &&
            source.argb.size == source.width * source.height &&
            widthMm.isFinite() && widthMm in 10f..200f) {
            "Imagem incompatível com a digitalização automática."
        }
        val heightMm = widthMm * source.height / source.width.toFloat()
        require(widthMm <= hoop.usableWidthMm && heightMm <= hoop.usableHeightMm) {
            "Reduza a arte ou selecione um bastidor maior."
        }
        val audit = RasterArtworkComplexityAudit.examine(source)
        require(!audit.requiresMultiTechniqueReview) {
            "Esta imagem possui tonalidades e objetos que exigem técnicas " +
                "diferentes (Satin, preenchimento e contorno). A simulação " +
                "automática do logo inteiro ainda não foi validada. " +
                "Faça a revisão dos elementos no editor."
        }

        val parts = RasterSatinObjectEditor.detect(source).getOrThrow()
        require(parts.size in 1..MAX_OBJECTS) {
            "A imagem contém ${parts.size} elementos. Separe a arte em " +
                "partes menores para revisar as colunas e a ordem das linhas."
        }
        val output = ArrayList<EmbroideryPoint>()
        val threadColors = ArrayList<Int>()
        var currentColor: Int? = null
        var colorChanges = 0
        var previous: EmbroideryPoint? = null

        for ((index, part) in parts.withIndex()) {
            // Thin line: create actual RUNNING stitch coordinates rather than
            // misclassifying a sub-millimetre outline as a Satin column.
            // A rejected line NEVER falls back to an inappropriate fill.
            val minSpan = minOf(
                part.mask.maxX - part.mask.minX + 1,
                part.mask.maxY - part.mask.minY + 1
            ) * widthMm / source.width
            val running = minSpan < .8f
            val individual = if (running) {
                RasterRunningOutlinePreview.create(
                    source, part, widthMm, hoop
                ).getOrElse { reason ->
                    throw IllegalArgumentException(
                        "Elemento ${index + 1}: contorno fino incompatível. " +
                            (reason.message ?: "Revisão necessária.")
                    )
                }
            } else {
                val simple = RasterAutoSatinRails.propose(source, part, widthMm)
                val candidate = simple.fold(
                    onSuccess = { Result.success(listOf(it)) },
                    onFailure = {
                        RasterSatinTopologyPlanner.propose(source, part, widthMm)
                    }
                )
                val sections = candidate.getOrElse { reason ->
                    throw IllegalArgumentException(
                        "Elemento ${index + 1} de ${parts.size}: " +
                            (reason.message ?: "contorno ainda não compatível.") +
                            " Não foi gerado um bordado incompleto."
                    )
                }
                RasterSatinObjectEditor.preview(
                    source, part, sections, widthMm, hoop,
                    minimumCoverage = .90f
                ).getOrElse { reason ->
                    throw IllegalArgumentException(
                        "Elemento ${index + 1} de ${parts.size}: " +
                            (reason.message ?: "pontos fora da arte.") +
                            " Revise os trilhos desta parte."
                    )
                }
            }
            require(individual.sourceBytes.isEmpty() &&
                individual.format in setOf(
                    "QA_SATIN_REVIEW", "QA_RUNNING_REVIEW"
                ) && individual.isModified &&
                individual.stitchCount > 0 && individual.endFound) {
                "O elemento ${index + 1} não passou pela validação QA."
            }
            // A plausible-looking path is insufficient. Compare the ACTUAL
            // sewn segments, not just the edit handles, with original ink.
            val fidelity = RasterStitchFidelityAudit.compare(
                source, part, individual, widthMm
            ).getOrElse { error ->
                throw IllegalArgumentException(
                    "Elemento ${index + 1}: auditoria dos pontos falhou. " +
                        (error.message ?: "Revise a matriz.")
                )
            }
            val requiredCoverage = if (running) .72 else .78
            val allowedOutside = if (running) .15 else .10
            require(fidelity.coverage >= requiredCoverage &&
                fidelity.offInkFraction <= allowedOutside) {
                "Elemento ${index + 1}: bordado não corresponde ao desenho " +
                    "(cobertura ${(fidelity.coverage * 100).toInt()}%, " +
                    "pontos fora ${(fidelity.offInkFraction * 100).toInt()}%). " +
                    "Simulação do logo inteiro bloqueada."
            }
            val points = individual.points.filter { it.command != StitchCommand.END }
            require(points.isNotEmpty() && points.first().command == StitchCommand.JUMP) {
                "A parte ${index + 1} não possui deslocamento inicial seguro."
            }
            val switchColor = currentColor != part.color
            if (switchColor) {
                require(colorChanges < MAX_COLOR_CHANGES)
                currentColor = part.color
                threadColors += part.color
                if (previous != null) colorChanges++
            }
            val outIndex = threadColors.lastIndex
            if (previous != null) {
                output += EmbroideryPoint(
                    previous.xUnits, previous.yUnits, StitchCommand.TRIM,
                    outIndex - if (switchColor) 1 else 0
                )
                if (switchColor) output += EmbroideryPoint(
                    previous.xUnits, previous.yUnits,
                    StitchCommand.COLOR_CHANGE, outIndex
                )
            }
            // Prevent a hidden stitched jump between disconnected letters.
            output.addAll(points.map { it.copy(colorIndex = outIndex) })
            previous = output.last()
            require(output.size < MAX_STITCHES) {
                "Mais de 90 mil comandos. Reduza a arte."
            }
        }
        val last = previous ?: error("Nenhum ponto validado foi produzido.")
        output += EmbroideryPoint(last.xUnits, last.yUnits,
            StitchCommand.END, threadColors.lastIndex)
        val actual = output.filter {
            it.command == StitchCommand.STITCH || it.command == StitchCommand.JUMP
        }
        require(actual.isNotEmpty() &&
            output.any { it.command == StitchCommand.STITCH }) {
            "Nenhuma trajetória verificável encontrada."
        }
        val b = EmbroideryBounds(
            actual.minOf { it.xUnits }, actual.maxOf { it.xUnits },
            actual.minOf { it.yUnits }, actual.maxOf { it.yUnits }
        )
        // Positioning is relative to the entire image, not each crop. Permit
        // only the hoop's usable area, including possible pull compensation.
        require(b.widthMm <= hoop.usableWidthMm &&
            b.heightMm <= hoop.usableHeightMm &&
            actual.all {
                kotlin.math.abs(it.xUnits) <= hoop.usableWidthMm * 5f &&
                kotlin.math.abs(it.yUnits) <= hoop.usableHeightMm * 5f
            }) {
            "Pontos ultrapassam o bastidor útil. Ajuste o tamanho da arte."
        }

        // Detect unintended long STITCH bridging where interpolation or an
        // object's endpoint is geometrically unsound.
        var lastPosition: EmbroideryPoint? = null
        for (p in output) {
            if (p.command == StitchCommand.JUMP ||
                p.command == StitchCommand.COLOR_CHANGE ||
                p.command == StitchCommand.TRIM) {
                lastPosition = if (p.command == StitchCommand.JUMP) p else null
                continue
            }
            if (p.command != StitchCommand.STITCH) continue
            val prev = lastPosition
            if (prev != null) require(
                hypot((p.xUnits - prev.xUnits).toDouble(),
                    (p.yUnits - prev.yUnits).toDouble()) <= 78.0
            ) {
                "Foi encontrada uma pontada superior a 7,8 mm. Simulação bloqueada."
            }
            lastPosition = p
        }
        require(!RasterEmbroideryApproval.canExportFromImageTool()) {
            "O modo experimental não pode exportar matrizes PES."
        }
        EmbroideryDesign(
            fileName = "LOGO-AUTO-REVISAO.qa",
            format = "QA_AUTO_STITCH",
            label = "Prévia automática de ${parts.size} elementos validados — NÃO enviar à máquina",
            points = output,
            bounds = b,
            stitchCount = output.count { it.command == StitchCommand.STITCH },
            jumpCount = output.count { it.command == StitchCommand.JUMP },
            colorChanges = colorChanges,
            endFound = true,
            sourceBytes = ByteArray(0),
            threadColors = threadColors,
            isModified = true,
            hoopProfile = hoop
        )
    }
}
