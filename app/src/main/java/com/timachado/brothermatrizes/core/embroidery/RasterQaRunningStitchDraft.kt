package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.roundToInt

/**
 * A machine-command SHAPED QA experiment from an exact raster silhouette.
 *
 * Creates real internal STITCH/JUMP/TRIM coordinates only for outlining and
 * simulator research; it never creates PES/JEF/DST bytes or a downloadable
 * embroidery file, and it never certifies a usable physical stitch-out.
 *
 * Pixel boundary tracing is not an embroidery underlay or an automatically
 * approved border location. Thin features, counters, fabric, travel and
 * color/technique decisions still need an operator.
 */
internal data class RasterQaRunningStitchDraftReport(
    val design: EmbroideryDesign,
    val originalFingerprint: String,
    val outlineCount: Int,
    val counterCount: Int,
    val thinContoursRequiringReview: Int,
    val needleRisk: RasterNeedleRiskReport,
    val threadDensity: RasterThreadDensityReport,
    val needsOperatorReview: Boolean = true,
    val needsMachineStitchOut: Boolean = true,
    val exportPermitted: Boolean = false
)

internal object RasterQaRunningStitchDraft {
    fun create(
        source: RasterImagePixels,
        chosenWidthMm: Float,
        hoop: HoopProfile,
        threshold: Int = 208,
        maxWaypointSpacingMm: Float = 2f
    ): Result<RasterQaRunningStitchDraftReport> = runCatching {
        require(!RasterEmbroideryApproval.canExportFromImageTool()) {
            "Exportação experimental não autorizada."
        }
        val geometry = RasterQaContourGeometry.inspect(
            source, chosenWidthMm, threshold
        ).getOrThrow()
        val outlineReport = RasterQaRunningPathStudy.prepare(
            geometry, maxWaypointSpacingMm
        ).getOrThrow()
        require(outlineReport.outlines.isNotEmpty() &&
            !outlineReport.exportPermitted && outlineReport.needsOperatorReview
        ) { "Contornos sem revisão obrigatória." }

        val physicalWidth = chosenWidthMm
        val physicalHeight = chosenWidthMm * source.height / source.width.toFloat()
        require(physicalWidth <= hoop.usableWidthMm &&
            physicalHeight <= hoop.usableHeightMm
        ) { "Imagem ultrapassa a área útil do bastidor." }

        fun mapped(p: RasterQaWaypointMm, command: StitchCommand) =
            EmbroideryPoint(
                ((p.x - physicalWidth / 2f) * 10f).roundToInt(),
                ((physicalHeight / 2f - p.y) * 10f).roundToInt(),
                command,
                0
            )

        val commands = ArrayList<EmbroideryPoint>()
        for (outline in outlineReport.outlines) {
            require(outline.isClosed && outline.waypoints.size >= 5) {
                "Contorno não fechado: revisar manualmente."
            }
            if (commands.isNotEmpty()) {
                val previous = commands.last()
                require(previous.command == StitchCommand.STITCH) {
                    "Contorno anterior terminou sem ponto costurado."
                }
                commands += previous.copy(command = StitchCommand.TRIM)
            }
            val path = outline.waypoints
            commands += mapped(path.first(), StitchCommand.JUMP)
            for (next in path.drop(1)) {
                val point = mapped(next, StitchCommand.STITCH)
                require(point.xUnits != commands.last().xUnits ||
                    point.yUnits != commands.last().yUnits
                ) { "Pontos colapsaram na escala de 0,1 mm." }
                commands += point
            }
        }
        require(commands.size in 5..16_384 &&
            commands.first().command == StitchCommand.JUMP &&
            commands.last().command == StitchCommand.STITCH
        ) { "Trajetória QA vazia ou excessivamente complexa." }
        val last = commands.last()
        commands += last.copy(command = StitchCommand.END)

        val travels = commands.filter {
            it.command == StitchCommand.STITCH || it.command == StitchCommand.JUMP
        }
        val candidate = EmbroideryDesign(
            fileName = "imagem-contorno-qa-nao-exportavel",
            format = "QA_RUNNING_REVIEW",
            label = "Contorno geométrico de imagem — somente QA",
            points = commands,
            bounds = EmbroideryBounds(
                travels.minOf { it.xUnits }, travels.maxOf { it.xUnits },
                travels.minOf { it.yUnits }, travels.maxOf { it.yUnits }
            ),
            stitchCount = commands.count { it.command == StitchCommand.STITCH },
            jumpCount = commands.count { it.command == StitchCommand.JUMP },
            colorChanges = 0,
            endFound = true,
            sourceBytes = ByteArray(0),
            threadColors = listOf(0x223344),
            isModified = true,
            hoopProfile = hoop,
            sourceYAxisDown = false
        )
        EmbroideryStressPolicy.requireGeneratedSafe(candidate)
        val risk = RasterNeedleRiskAudit.inspect(candidate, hoop).getOrThrow()
        // A safe needle pitch does not imply safe accumulated thread density.
        // Audit the ENTIRE sewn outline after converting mm to 0.1-mm units.
        val density = RasterThreadDensityPreflight.inspect(candidate).getOrThrow()
        require(density.sewnSegments == risk.stitchesExamined) {
            "Auditorias de pontos e densidade discordam."
        }
        require(!RasterEmbroideryApproval.canExportFromImageTool()) {
            "Bloqueio de exportação foi alterado durante a análise."
        }
        RasterQaRunningStitchDraftReport(
            design = candidate,
            originalFingerprint = outlineReport.sourceFingerprint,
            outlineCount = outlineReport.outlines.size,
            counterCount = outlineReport.outlines.count { it.isCounter },
            thinContoursRequiringReview =
                outlineReport.outlines.count { it.thinFeatureWarning },
            needleRisk = risk,
            threadDensity = density
        )
    }
}
