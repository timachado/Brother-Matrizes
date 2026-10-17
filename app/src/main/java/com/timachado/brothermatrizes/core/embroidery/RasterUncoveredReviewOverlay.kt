package com.timachado.brothermatrizes.core.embroidery

/**
 * Visual-only, full-canvas highlight of the EXACT non-covered source pixels.
 * A gray pixel is source ink covered by the STITCH preview. Orange is real
 * source ink still missing stitches. Transparent counters remain transparent.
 *
 * This does not add points, touch the imported image, auto-select Satin/Tatami,
 * encode PES/JEF/DST, or claim compatibility with a Brother machine.
 */
internal object RasterUncoveredReviewOverlay {
    private const val ALPHA_LIMIT = 180
    private const val COVERED = 0xff7a8288.toInt()
    private const val GAP = 0xffff8f2d.toInt()
    private const val OTHER_GAP = 0xffe9c3a1.toInt()

    fun render(
        original: RasterImagePixels,
        item: RasterSatinObject,
        report: RasterUncoveredRegionReport,
        selectedRegionId: Int = 0
    ): Result<RasterImagePixels> = runCatching {
        val b = item.mask
        val w = report.localWidth
        val h = report.localHeight
        require(
            original.width in 8..512 && original.height in 8..512 &&
                original.argb.size == original.width * original.height &&
                b.pixels.size == original.argb.size &&
                w in 1..512 && h in 1..512 &&
                report.originX == b.minX && report.originY == b.minY &&
                b.maxX == b.minX + w - 1 &&
                b.maxY == b.minY + h - 1 &&
                report.originX in 0 until original.width &&
                report.originY in 0 until original.height &&
                report.originX + w <= original.width &&
                report.originY + h <= original.height &&
                report.regionIds.size == w * h &&
                !report.exportPermitted &&
                !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Máscara de lacunas e imagem original incompatíveis." }
        require(selectedRegionId == 0 ||
            report.regions.any { it.id == selectedRegionId }
        ) { "Região de revisão não disponível; nada será desenhado." }

        val preview = IntArray(original.argb.size)
        var inkCount = 0
        var gaps = 0
        var selected = 0
        val counts = IntArray(report.totalRegions + 1)
        for (y in 0 until h) for (x in 0 until w) {
            val local = y * w + x
            val global = (report.originY + y) * original.width +
                report.originX + x
            val ink = b.pixels[global] == 0
            val region = report.regionIds[local]
            require(region in 0..report.totalRegions) {
                "O mapa de regiões contém identificador inválido."
            }
            if (!ink) {
                require(region == 0) {
                    "Contrafoma transparente recebeu destaque indevido."
                }
                continue
            }
            require(original.argb[global] ushr 24 >= ALPHA_LIMIT) {
                "A foto original não contém linha nesta posição."
            }
            inkCount++
            if (region > 0) {
                counts[region]++
                gaps++
                if (region == selectedRegionId) selected++
            }
            // Never paint a non-ink original pixel, including letter holes.
            preview[global] = when {
                region == 0 -> COVERED
                selectedRegionId == 0 || region == selectedRegionId -> GAP
                else -> OTHER_GAP
            }
        }
        require(inkCount == item.pixelCount &&
            inkCount == report.sourcePixels &&
            gaps == report.uncoveredPixels &&
            inkCount - gaps == report.coveredPixels &&
            counts.count { it > 0 } == report.totalRegions &&
            report.regions.all { it.id in 1..report.totalRegions &&
                counts[it.id] == it.pixelCount } &&
            (selectedRegionId == 0 ||
                selected == report.regions.first {
                    it.id == selectedRegionId
                }.pixelCount)
        ) { "Uma área sem cobertura foi modificada ou omitida." }
        RasterImagePixels(
            original.width, original.height, preview,
            "Visualização QA: laranja = lacuna, cinza = cobertura"
        )
    }
}
