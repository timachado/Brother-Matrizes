package com.timachado.brothermatrizes.core.embroidery

/**
 * Bridges one selected FLAT-COLOR review component to the existing editable
 * Satin editor without silently discarding any ink, moving it in the hoop,
 * merging adjacent colors or turning a photograph into stitch commands.
 *
 * A draft has NOT been touched by its human reviewer. No machine/export
 * permission is conveyed by a successful proposal.
 */
internal data class RasterAssistedSatinDraft(
    val elementId: Int,
    val item: RasterSatinObject,
    val sections: List<ManualSatinSection>,
    val counterCount: Int,
    val sourcePixelCount: Int,
    val manualTouchRequired: Boolean = true,
    val exportPermitted: Boolean = false
)

internal object RasterAssistedSatinDraftPlanner {
    fun prepare(
        original: RasterImagePixels,
        plan: RasterAssistedElementPlan,
        elementId: Int,
        widthMm: Float,
        hoop: HoopProfile
    ): Result<RasterAssistedSatinDraft> = runCatching {
        val part = plan.elements.firstOrNull { it.id == elementId } ?:
            error("Escolha uma parte existente da imagem.")
        require(original.width in 8..512 && original.height in 8..512 &&
            original.argb.size == original.width * original.height &&
            plan.originalAlphaPreserved && !plan.exportPermitted &&
            widthMm.isFinite() && widthMm in 10f..200f &&
            !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Origem, escala ou política de exportação incompatível." }
        val heightMm = widthMm * original.height / original.width.toFloat()
        require(widthMm <= hoop.usableWidthMm &&
            heightMm <= hoop.usableHeightMm
        ) { "Reduza a arte à área útil do bastidor antes da revisão Satin." }
        // Use the precise original (uncropped) photo coordinates. A cropped
        // silhouette would shift the Satin rails relative to the full hoop.
        val isolated = plan.isolate(elementId).getOrThrow()
        require(isolated.width == original.width &&
            isolated.height == original.height
        ) { "A máscara isolada alterou a posição no bastidor." }
        val alpha = IntArray(original.argb.size) { i ->
            if ((isolated.argb[i] ushr 24) >= 180) 0 else -1
        }
        val occupied = alpha.count { it == 0 }
        require(occupied == part.pixelCount && occupied >= 8 &&
            alpha.indices.all { index ->
                alpha[index] != 0 || (original.argb[index] ushr 24) >= 180
            }
        ) { "Uma parte foi perdida ou escapou da silhueta original." }
        val mask = SeparatedColorArtwork(
            colors = listOf(part.color),
            pixels = alpha,
            minX = part.minX, maxX = part.maxX,
            minY = part.minY, maxY = part.maxY
        )
        val item = RasterSatinObject(
            id = part.id, color = part.color,
            pixelCount = occupied, mask = mask
        )
        val counted = RasterCounterSatinGate.count(original, item)
            .getOrElse { error ->
                // Even a tiny or ambiguous void must never disappear from
                // the stitch candidate through an unsafe fallback.
                throw IllegalArgumentException(
                    "A letra exige revisão da contraforma: " +
                        (error.message ?: "vazado ambíguo.")
                )
            }
        require(counted == part.enclosedHoles) {
            "Contagens divergentes de vazados; edição Satin recusada."
        }
        val sections = if (counted > 0) {
            // Never offer a straight rail that would bridge the O/B/P hole.
            RasterSatinTopologyPlanner.propose(original, item, widthMm)
                .getOrElse {
                    throw IllegalArgumentException(
                        "Contraforma preservada, mas os trilhos Satin " +
                            "da letra exigem digitalização profissional " +
                            "assistida; nenhum caminho inseguro foi criado."
                    )
                }
        } else {
            RasterAutoSatinRails.propose(original, item, widthMm)
                .getOrNull()?.let { listOf(it) }
                ?: RasterSatinTopologyPlanner.propose(original, item, widthMm)
                    .getOrNull()
                ?: listOf(
                    RasterSatinObjectEditor.suggest(
                        original, item, widthMm
                    ).getOrThrow()
                )
        }
        require(sections.isNotEmpty() && sections.size <= 40 &&
            sections.all { it.rails.size in 2..100 }
        ) { "Proposta Satin sem pares de trilhos editáveis." }

        // Width/curvature/rail-order check before any needle simulation.
        // Re-run the same gate after every human edit in the screen.
        RasterSatinRailGeometryGate.inspect(
            original, item, sections, widthMm, counted
        ).getOrThrow()

        if (counted > 0) {
            // Counter-safe proposals must pass the physical SEWN-SEGMENT
            // counter audit even before a human begins adjusting them.
            // All edits in the screen are rechecked before the simulator.
            val trial = RasterSatinObjectEditor.preview(
                original, item, sections, widthMm, hoop,
                minimumCoverage = .90f
            ).getOrThrow()
            RasterCounterSatinGate.inspect(
                original, item, trial, widthMm
            ).getOrThrow()
        }
        RasterAssistedSatinDraft(
            elementId = elementId,
            item = item,
            sections = sections,
            counterCount = counted,
            sourcePixelCount = occupied
        )
    }

    fun requireSafeCounterPath(
        original: RasterImagePixels,
        item: RasterSatinObject,
        design: EmbroideryDesign,
        fullWidthMm: Float
    ): Result<Unit> = runCatching {
        val counters = RasterCounterSatinGate.count(original, item)
            .getOrThrow()
        if (counters > 0) {
            RasterCounterSatinGate.inspect(
                original, item, design, fullWidthMm
            ).getOrThrow()
        }
    }
}
