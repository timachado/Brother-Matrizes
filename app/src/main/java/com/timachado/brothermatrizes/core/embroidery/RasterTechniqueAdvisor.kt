package com.timachado.brothermatrizes.core.embroidery

/**
 * Non-destructive technique assessment. Distinguishes "a candidate can be
 * demonstrated with real Satin stitches" from "the rest of the artwork can
 * safely be exported". FILL and RUNNING are suggestions ONLY: no stitch
 * commands are synthesized for them.
 */
internal enum class RasterTechnique {
    SATIN_QA, FILL_NEEDS_DIGITIZING, RUNNING_NEEDS_DIGITIZING, MANUAL_REVIEW
}

internal data class RasterTechniqueCandidate(
    val partId: Int,
    val color: Int,
    val widthMm: Float,
    val heightMm: Float,
    val technique: RasterTechnique,
    val explanation: String
)

internal data class RasterTechniqueReport(
    val candidates: List<RasterTechniqueCandidate>,
    val warnings: List<String>,
    val sourceComplex: Boolean
) {
    /** A plan is not a PES, even when all suggested sections are Satin. */
    val needsDigitizing: Boolean get() =
        sourceComplex || warnings.isNotEmpty() ||
            candidates.any { it.technique != RasterTechnique.SATIN_QA }

    fun brief(): String = buildString {
        if (sourceComplex) append("Arte multitécnica detectada. ")
        if (candidates.isEmpty()) {
            append("Não foi possível separar objetos automaticamente.")
        } else {
            val satin = candidates.count { it.technique == RasterTechnique.SATIN_QA }
            val fill = candidates.count {
                it.technique == RasterTechnique.FILL_NEEDS_DIGITIZING
            }
            val running = candidates.count {
                it.technique == RasterTechnique.RUNNING_NEEDS_DIGITIZING
            }
            val manual = candidates.size - satin - fill - running
            append("${candidates.size} partes: ")
            append("${satin} Satin QA; ${fill} preenchimento pendente; ")
            append("${running} contorno pendente; ${manual} revisão manual.")
        }
        if (needsDigitizing) {
            append(" Não é uma matriz PES pronta para máquina.")
        } else {
            append(" Trajetórias Satin preliminares: exige homologação.")
        }
    }
}

/**
 * Separates source components and chooses an appropriate NEXT DIGITIZATION
 * route. For Satin, requires both the original rail-mask validation and the
 * newly added comparison of actual STITCH segments against source pixels.
 *
 * A label "fill" means "implement safe fill later", not "draw raster rows and
 * call them machine-ready". This keeps the traditional PES simulator intact.
 */
internal object RasterTechniqueAdvisor {
    fun assess(
        image: RasterImagePixels,
        widthMm: Float,
        hoop: HoopProfile
    ): Result<RasterTechniqueReport> = runCatching {
        require(image.width in 8..512 && image.height in 8..512 &&
            widthMm.isFinite() && widthMm in 10f..200f) {
            "Dimensões da imagem inválidas."
        }
        val heightMm = widthMm * image.height / image.width.toFloat()
        require(widthMm <= hoop.usableWidthMm &&
            heightMm <= hoop.usableHeightMm) {
            "Ajuste a imagem à área útil do bastidor."
        }
        val risky = RasterArtworkComplexityAudit.examine(image)
        val detected = RasterSatinObjectEditor.detect(image)
        if (detected.isFailure) {
            val cause = detected.exceptionOrNull()?.message ?:
                "Segmentação de objetos indisponível."
            return@runCatching RasterTechniqueReport(
                candidates = emptyList(),
                warnings = listOf("Separação incompleta: $cause"),
                sourceComplex = risky.requiresMultiTechniqueReview
            )
        }
        val components = detected.getOrThrow()
        val selected = components.take(48)
        val warnings = mutableListOf<String>()
        if (components.size > 24) {
            warnings += "Muitos elementos: revisar agrupamento e sequência de cores."
        }
        val mmPerPixel = widthMm / image.width.toFloat()
        val items = selected.map { part ->
            val w = part.mask.maxX - part.mask.minX + 1
            val h = part.mask.maxY - part.mask.minY + 1
            val physicalWidth = w * mmPerPixel
            val physicalHeight = h * mmPerPixel
            val minSpan = minOf(physicalWidth, physicalHeight)
            val inkDensity = part.pixelCount.toFloat() / (w * h).coerceAtLeast(1)
            fun item(
                type: RasterTechnique, reason: String
            ) = RasterTechniqueCandidate(
                partId = part.id, color = part.color,
                widthMm = physicalWidth, heightMm = physicalHeight,
                technique = type, explanation = reason
            )
            // Very thin/short components need an outline/run path, not a
            // physically invalid Satin column.
            if (minSpan < .8f || part.pixelCount < 8) {
                return@map item(
                    RasterTechnique.RUNNING_NEEDS_DIGITIZING,
                    "Traço fino: estudar ponto corrido e compensação."
                )
            }
            // A solid wide area needs underlay and Tatami/Fill selection.
            // Do not pass it to arbitrary raster zigzag fallback.
            if (minSpan > 7.0f && inkDensity >= .64f) {
                return@map item(
                    RasterTechnique.FILL_NEEDS_DIGITIZING,
                    "Área larga: estudar preenchimento e sustentação dos pontos."
                )
            }
            // Geometry may still be a ring, branch, curved letter. The
            // topology planner only supports conservative eligible cases.
            val proposed = RasterAutoSatinRails.propose(image, part, widthMm)
                .fold(
                    onSuccess = { Result.success(listOf(it)) },
                    onFailure = {
                        RasterSatinTopologyPlanner.propose(
                            image, part, widthMm
                        )
                    }
                )
            val sections = proposed.getOrNull()
            if (sections != null) {
                val preview = RasterSatinObjectEditor.preview(
                    image, part, sections, widthMm, hoop,
                    minimumCoverage = .90f
                ).getOrNull()
                if (preview != null) {
                    val measured = RasterStitchFidelityAudit.compare(
                        image, part, preview, widthMm
                    ).getOrNull()
                    if (measured != null &&
                        measured.coverage >= .78 &&
                        measured.offInkFraction <= .10
                    ) {
                        return@map item(
                            RasterTechnique.SATIN_QA,
                            "Coluna Satin com pontos verificados por geometria 2D; " +
                                "ainda não homologada para máquina."
                        )
                    }
                }
            }
            if (minSpan > 7f && inkDensity >= .45f) {
                return@map item(
                    RasterTechnique.FILL_NEEDS_DIGITIZING,
                    "Região espessa ou irregular: avaliar técnica de preenchimento."
                )
            }
            item(
                RasterTechnique.MANUAL_REVIEW,
                "Geometria não reconhecida com segurança: rever contorno, " +
                    "ramificações e ordem de bordado."
            )
        }
        RasterTechniqueReport(
            candidates = items, warnings = warnings,
            sourceComplex = risky.requiresMultiTechniqueReview
        )
    }
}
