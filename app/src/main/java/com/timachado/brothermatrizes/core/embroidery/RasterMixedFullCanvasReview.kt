package com.timachado.brothermatrizes.core.embroidery

/**
 * QA-only, whole-picture missing-STITCH map built from the SAME per-part
 * needle commands that were assembled for Simular Bordado.
 *
 * This is a raster evidence layer, NOT a source of stitch commands. The
 * transparent background and internal holes of letters are never painted.
 */
internal data class RasterMixedFullCanvasPart(
    val id: Int,
    val color: Int,
    val inkPixels: Int,
    val coveredPixels: Int,
    val uncoveredPixels: Int,
    val regionsWithoutStitches: Int
) {
    val coverage: Double get() = if (inkPixels == 0) 0.0
        else coveredPixels.toDouble()/inkPixels
}

internal data class RasterMixedFullCanvasReport(
    val originalInkPixels: Int,
    val coveredInkPixels: Int,
    val uncoveredInkPixels: Int,
    val uncoveredRegions: Int,
    val parts: List<RasterMixedFullCanvasPart>,
    val coverageOverlay: RasterImagePixels,
    val exportPermitted: Boolean = false,
    val requiresPhysicalStitchOut: Boolean = true
) {
    val coverage: Double get() = if (originalInkPixels == 0) 0.0
        else coveredInkPixels.toDouble()/originalInkPixels

    fun summary(): String =
        "Desenho inteiro: " + coveredInkPixels + " de " +
            originalInkPixels + " pixels cobertos (" +
            (coverage*100).toInt() + "%); " +
            uncoveredInkPixels + " pixels sem pontos em " +
            uncoveredRegions + " regiões. " +
            "Cinza = cobertura amostrada; laranja = falta. " +
            "Amostragem QA, não bordado físico nem PES."
}

internal object RasterMixedFullCanvasReview {
    private const val COVERED = 0xff7a8288.toInt()
    private const val GAP = 0xffff8f2d.toInt()

    fun inspect(
        source: RasterImagePixels,
        confirmed: List<RasterReviewedTechniqueLayer>,
        assembled: EmbroideryDesign,
        fullWidthMm: Float,
        hoop: HoopProfile
    ): Result<RasterMixedFullCanvasReport> = runCatching {
        require(source.width in 8..512 && source.height in 8..512 &&
            source.argb.size == source.width*source.height &&
            confirmed.size in 2..RasterQaComponentBudget.MAX_COMPONENTS &&
            confirmed.map {it.component.id}.distinct().size == confirmed.size &&
            assembled.hoopProfile == hoop &&
            fullWidthMm.isFinite() && fullWidthMm in 10f..200f &&
            !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Imagem ou sequência fora dos limites da auditoria integral." }

        // Recheck the actual composed command stream, including internal
        // TRIM/JUMP, order and color indices. A supplied list of layers
        // without a matching assembled design is never sufficient evidence.
        RasterMixedSequenceIntegrityAudit.inspect(
            assembled, confirmed
        ).getOrThrow()
        val canonical=RasterExactColorComponentDetector.detect(
            source
        ).getOrThrow()
        require(canonical.size==confirmed.size) {
            "Há letras/detalhes da imagem original fora da composição."
        }

        val unmatched=canonical.toMutableList()
        val painted=IntArray(source.argb.size)
        val sourceInk=BooleanArray(source.argb.size)
        val data=ArrayList<RasterMixedFullCanvasPart>()
        var inkTotal=0
        var coveredTotal=0
        var missingTotal=0
        var regionsTotal=0

        for (layer in confirmed.sortedBy {it.sewingOrder}) {
            val part=layer.component
            val index=unmatched.indexOfFirst {candidate ->
                candidate.color==part.color &&
                    candidate.pixelCount==part.pixelCount &&
                    candidate.mask.minX==part.mask.minX &&
                    candidate.mask.minY==part.mask.minY &&
                    candidate.mask.maxX==part.mask.maxX &&
                    candidate.mask.maxY==part.mask.maxY &&
                    candidate.mask.pixels.contentEquals(part.mask.pixels)
            }
            require(index>=0) {
                "A letra confirmada difere da imagem original."
            }
            unmatched.removeAt(index)

            val evidence=RasterMixedStitchVisualAudit.inspect(
                source,part,layer.design,fullWidthMm,hoop
            ).getOrThrow()
            val overlay=evidence.coverageOverlay
            require(overlay.argb.size==painted.size &&
                evidence.originalInkPixels==part.pixelCount &&
                evidence.stitchedInkPixels+evidence.uncoveredInkPixels==
                    part.pixelCount
            ) { "Cobertura de uma parte não corresponde à tinta original." }

            for (i in part.mask.pixels.indices) {
                if (part.mask.pixels[i]!=0) {
                    require(overlay.argb[i]==0) {
                        "Cobertura visual pintou o vazado/fundo da letra."
                    }
                    continue
                }
                require(!sourceInk[i] &&
                    source.argb[i]==part.color &&
                    overlay.argb[i] in setOf(COVERED,GAP)
                ) { "Uma tinta duplicada, alterada ou sem classificação." }
                sourceInk[i]=true
                painted[i]=overlay.argb[i]
            }

            inkTotal+=part.pixelCount
            coveredTotal+=evidence.stitchedInkPixels
            missingTotal+=evidence.uncoveredInkPixels
            regionsTotal+=evidence.uncoveredRegions
            data+=RasterMixedFullCanvasPart(
                id=part.id,color=part.color,inkPixels=part.pixelCount,
                coveredPixels=evidence.stitchedInkPixels,
                uncoveredPixels=evidence.uncoveredInkPixels,
                regionsWithoutStitches=evidence.uncoveredRegions
            )
        }
        require(unmatched.isEmpty() &&
            inkTotal==canonical.sumOf {it.pixelCount} &&
            inkTotal==sourceInk.count {it} &&
            coveredTotal==painted.count {it==COVERED} &&
            missingTotal==painted.count {it==GAP} &&
            coveredTotal+missingTotal==inkTotal &&
            regionsTotal==data.sumOf {it.regionsWithoutStitches}
        ) {
            "Mapa completo perdeu ou duplicou pixels/cor/regiões."
        }
        RasterMixedFullCanvasReport(
            originalInkPixels=inkTotal,
            coveredInkPixels=coveredTotal,
            uncoveredInkPixels=missingTotal,
            uncoveredRegions=regionsTotal,
            parts=data,
            coverageOverlay=RasterImagePixels(
                source.width,source.height,painted,
                "QA global: laranja = tinta sem STITCH, cinza = coberta"
            )
        )
    }
}
