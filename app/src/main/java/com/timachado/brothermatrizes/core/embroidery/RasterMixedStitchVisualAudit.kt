package com.timachado.brothermatrizes.core.embroidery

/**
 * Truthful comparison of individual mixed-technique QA stitch commands with
 * the ORIGINAL colored component. A coverage map represents sampled sewn
 * STITCH segments, never a simulated stitched fabric photograph.
 *
 * This is strictly a non-exportable visual review, not Brother approval.
 */
internal data class RasterMixedStitchVisualEvidence(
    val originalInkPixels: Int,
    val stitchedInkPixels: Int,
    val uncoveredInkPixels: Int,
    val uncoveredRegions: Int,
    val stitchSamples: Int,
    val offInkSamples: Int,
    val sourceOverlay: RasterImagePixels,
    val coverageOverlay: RasterImagePixels,
    val exportPermitted: Boolean = false
) {
    val coverage: Double get() =
        if (originalInkPixels == 0) 0.0
        else stitchedInkPixels.toDouble() / originalInkPixels

    val outsideInkFraction: Double get() =
        if (stitchSamples == 0) 1.0
        else offInkSamples.toDouble() / stitchSamples

    fun brief(): String =
        "Prévia dos STITCH: " + stitchedInkPixels + " de " +
            originalInkPixels + " pixels cobertos (" +
            (coverage * 100).toInt() + "%); " + uncoveredInkPixels +
            " px faltantes em " + uncoveredRegions + " região(ões). " +
            "Pontos fora da tinta: " +
            (outsideInkFraction * 100).toInt() +
            "%. Comparação raster QA, não é PES."
}

internal object RasterMixedStitchVisualAudit {
    fun inspect(
        source: RasterImagePixels,
        part: RasterSatinObject,
        design: EmbroideryDesign,
        fullWidthMm: Float,
        hoop: HoopProfile
    ): Result<RasterMixedStitchVisualEvidence> = runCatching {
        require(source.width in 8..512 && source.height in 8..512 &&
            source.argb.size == source.width * source.height &&
            part.mask.pixels.size == source.argb.size &&
            design.sourceBytes.isEmpty() &&
            design.hoopProfile == hoop &&
            design.threadColors == listOf(part.color) &&
            design.format in setOf(
                "QA_SATIN_REVIEW", "QA_TATAMI_REVIEW", "QA_RUNNING_REVIEW"
            ) &&
            design.endFound &&
            design.points.lastOrNull()?.command == StitchCommand.END &&
            fullWidthMm.isFinite() && fullWidthMm in 10f..200f &&
            fullWidthMm <= hoop.usableWidthMm &&
            fullWidthMm * source.height / source.width <= hoop.usableHeightMm &&
            !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "A revisão visual exige os pontos QA válidos da parte original." }

        var originalInk = 0
        val originalIsolated = IntArray(source.argb.size)
        for (i in source.argb.indices) {
            if (part.mask.pixels[i] != 0) continue
            require(source.argb[i] == part.color) {
                "A máscara/cor já não corresponde à imagem original."
            }
            originalInk++
            originalIsolated[i] = source.argb[i]
        }
        require(originalInk == part.pixelCount && originalInk > 0) {
            "Contagem de pixels mudou durante revisão da prévia."
        }

        val fidelity = RasterStitchFidelityAudit.compare(
            source,part,design,fullWidthMm
        ).getOrThrow()
        val gaps = RasterUncoveredRegionAudit.inspect(
            source,part,fidelity,fullWidthMm
        ).getOrThrow()
        val overlay = RasterUncoveredReviewOverlay.render(
            source,part,gaps
        ).getOrThrow()
        require(fidelity.inkPixels == originalInk &&
            fidelity.coveredInkPixels + gaps.uncoveredPixels == originalInk &&
            gaps.totalRegions >= 0 &&
            !gaps.exportPermitted
        ) { "O relatório visual perdeu pixels ou alterou os vazados." }

        RasterMixedStitchVisualEvidence(
            originalInkPixels=originalInk,
            stitchedInkPixels=fidelity.coveredInkPixels,
            uncoveredInkPixels=gaps.uncoveredPixels,
            uncoveredRegions=gaps.totalRegions,
            stitchSamples=fidelity.stitchSamples,
            offInkSamples=fidelity.offInkSamples,
            sourceOverlay=RasterImagePixels(
                source.width,source.height,originalIsolated,
                "Imagem original isolada para conferência QA"
            ),
            coverageOverlay=overlay
        )
    }
}
