package com.timachado.brothermatrizes.core.embroidery

/**
 * QA-only bridge from a textured transparent raster to a reviewed,
 * flat-color SOURCE REFERENCE for the existing mixed-technique editor.
 *
 * This gate never reconstructs stitch geometry. Even on success, its
 * flat-color reference is NOT the original photograph and must be
 * explicitly accepted by the operator before any individual stitch
 * previews are generated. The imported source remains unchanged.
 */
internal data class RasterTextureReconstructionReport(
    val original: RasterImagePixels,
    val flatReference: RasterImagePixels,
    val sourceOpaquePixels: Int,
    val originalColorBuckets: Int,
    val threadColorFamilies: Int,
    val disconnectedComponents: Int,
    val ambiguousPixels: Int,
    val antialiasedEdgePixels: Int,
    val contourEvidence: RasterAntialiasedContourReport? = null,
    val reviewRequired: Boolean = true,
    val exportPermitted: Boolean = false
) {
    fun message(): String =
        "Fonte QA provisória: " + disconnectedComponents +
            " parte(s), " + threadColorFamilies + " família(s) de cor, " +
            sourceOpaquePixels + " px de silhueta preservados; " +
            ambiguousPixels + " px com cor incerta e " +
            antialiasedEdgePixels + " px de borda sem opacidade plena. " +
            (contourEvidence?.let { it.summary() + " " } ?: "") +
            "Revise a prévia original e as cores planas antes de aceitar. " +
            "Nenhuma agulhada ou PES foi criado."
}

internal object RasterTextureReconstructionGate {
    private const val MAX_SIDE = 512
    private const val ALPHA_LIMIT = 180
    // Contour safety comes from per-pixel geometry, not a rigid 5% ratio.

    fun prepare(
        imported: RasterImagePixels,
        previousStudy: RasterThreadTextureStudyReport
    ): Result<RasterTextureReconstructionReport> = runCatching {
        require(
            imported.width in 8..MAX_SIDE &&
                imported.height in 8..MAX_SIDE &&
                imported.argb.size == imported.width*imported.height &&
                previousStudy.flatPreview.width == imported.width &&
                previousStudy.flatPreview.height == imported.height &&
                previousStudy.flatPreview.argb.size == imported.argb.size &&
                previousStudy.originalAlphaHolesPreserved &&
                previousStudy.reviewRequired &&
                !previousStudy.exportPermitted &&
                !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "A imagem ou o estudo QA não corresponde à referência." }

        // Reject cross-image or tampered reports. Recompute from the imported
        // pixels, rather than treating metadata in a prior report as a proof.
        val recomputed = RasterThreadTextureStudy.inspect(imported).getOrThrow()
        require(
            previousStudy.sourceArgbFingerprint ==
                recomputed.sourceArgbFingerprint &&
                previousStudy.sourceArgbFingerprint != 0L &&
            previousStudy.foregroundPixels == recomputed.foregroundPixels &&
                previousStudy.originalToneBuckets == recomputed.originalToneBuckets &&
                previousStudy.suggestedColorFamilies ==
                    recomputed.suggestedColorFamilies &&
                previousStudy.ambiguousPixels == recomputed.ambiguousPixels &&
                previousStudy.flatPreview.argb.contentEquals(
                    recomputed.flatPreview.argb
                )
        ) { "O estudo de cores não pertence à imagem importada atual." }

        val contour = RasterAntialiasedContourGuard.inspect(imported)
            .getOrThrow()
        var foreground = 0
        var fringe = 0
        val rebuilt = recomputed.flatPreview
        for (i in imported.argb.indices) {
            val alpha = imported.argb[i] ushr 24
            val reconstructed = rebuilt.argb[i] ushr 24
            if (alpha >= ALPHA_LIMIT) {
                require(reconstructed == 255) {
                    "O recorte reconstruído perdeu um pixel de linha original."
                }
                foreground++
            } else {
                require(reconstructed == 0) {
                    "O recorte criou tinta no fundo/vazado da letra."
                }
                if (alpha in 1 until ALPHA_LIMIT) fringe++
            }
        }
        require(foreground == previousStudy.foregroundPixels &&
            foreground == contour.solidPixels &&
            fringe == contour.weakAlphaPixels &&
            !contour.exportPermitted && contour.requiresOriginalComparison
        ) {
            "Contornos com transparência ambígua: ajuste o recorte " +
                "antes de propor pontos reais."
        }

        // The same strict detector used at assembly time must approve ALL
        // components NOW. Tiny disconnected accents or textured leftovers
        // cause a complete rejection rather than being silently dropped.
        val components = RasterExactColorComponentDetector.detect(
            rebuilt
        ).getOrThrow()
        require(
            components.sumOf { it.pixelCount } == foreground &&
                components.all { part ->
                    part.mask.pixels.indices.count {
                        part.mask.pixels[it] == 0
                    } == part.pixelCount
                }
        ) {
            "Uma letra ou detalhe desapareceu da referência reconstruída."
        }
        RasterTextureReconstructionReport(
            original=imported,
            flatReference=rebuilt,
            sourceOpaquePixels=foreground,
            originalColorBuckets=recomputed.originalToneBuckets,
            threadColorFamilies=recomputed.suggestedColorFamilies,
            disconnectedComponents=components.size,
            ambiguousPixels=recomputed.ambiguousPixels,
            antialiasedEdgePixels=fringe,
            contourEvidence=contour
        )
    }
}
