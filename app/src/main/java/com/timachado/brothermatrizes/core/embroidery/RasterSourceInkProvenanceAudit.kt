package com.timachado.brothermatrizes.core.embroidery

/**
 * Independent QA provenance screen for edited raster components.
 *
 * A mask is an untrusted proposal, even if every generated needle movement
 * lies perfectly ON that mask: a stale mask can conceal a new counter or
 * falsely label a photograph's background as stitchable ink.
 *
 * Check the ORIGINAL pixel at every intended mask-ink position. This does
 * not recolor, fill, reconstruct or rewrite the imported artwork and does
 * not grant PES/export or physical machine approval.
 */
internal data class RasterSourceInkProvenanceReport(
    val originalInkPixelsChecked: Int,
    val exportPermitted: Boolean = false
)

internal object RasterSourceInkProvenanceAudit {
    private const val MAX_PIXELS = 512 * 512
    // Conservative color-family check for flat artwork. The generated
    // palette may use a mean color rather than the exact 8-bit source RGB.
    // It is a *software screen*, not validated thread color matching.
    private const val MAX_CHANNEL_COLOR_DISTANCE_SQUARED = 64 * 64

    fun inspect(
        source: RasterImagePixels,
        part: RasterSatinObject
    ): Result<RasterSourceInkProvenanceReport> = runCatching {
        require(source.width in 8..512 && source.height in 8..512 &&
            source.argb.size == source.width * source.height &&
            source.argb.size in 64..MAX_PIXELS &&
            part.mask.pixels.size == source.argb.size &&
            part.mask.minX in 0 until source.width &&
            part.mask.maxX in part.mask.minX until source.width &&
            part.mask.minY in 0 until source.height &&
            part.mask.maxY in part.mask.minY until source.height &&
            part.pixelCount > 0 &&
            !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Máscara sem proveniência válida nos pixels originais." }

        val expectedR = (part.color ushr 16) and 255
        val expectedG = (part.color ushr 8) and 255
        val expectedB = part.color and 255
        var occupied = 0
        for (index in part.mask.pixels.indices) {
            require(part.mask.pixels[index] == 0 || part.mask.pixels[index] == -1) {
                "Máscara contém rótulo inesperado; refaça a seleção."
            }
            val maskInk = part.mask.pixels[index] == 0
            if (!maskInk) continue
            val x = index % source.width
            val y = index / source.width
            require(x in part.mask.minX..part.mask.maxX &&
                y in part.mask.minY..part.mask.maxY
            ) { "Máscara alterada fora da área selecionada." }
            val pixel = source.argb[index]
            require((pixel ushr 24) >= 180) {
                "A máscara antiga cobre um vazado transparente em $x,$y."
            }
            val dr = ((pixel ushr 16) and 255) - expectedR
            val dg = ((pixel ushr 8) and 255) - expectedG
            val db = (pixel and 255) - expectedB
            require(dr * dr + dg * dg + db * db <=
                MAX_CHANNEL_COLOR_DISTANCE_SQUARED
            ) {
                "A cor da imagem original mudou em $x,$y; " +
                    "refaça a seleção e confira os trilhos."
            }
            occupied++
        }
        require(occupied == part.pixelCount) {
            "A contagem de tinta da máscara diverge da imagem original."
        }

        // Examine the mask's bounding rectangle PLUS its 1-pixel halo.
        // Newly connected ink can extend *outside* the previous bounding
        // box; limiting this check to the old box would silently drop a new
        // stroke from the final embroidery. Disconnected components remain
        // the responsibility of whole-artwork retention/multi-layer QA.
        fun sameSourceColor(index: Int): Boolean {
            val pixel = source.argb[index]
            if ((pixel ushr 24) < 180) return false
            val dr = ((pixel ushr 16) and 255) - expectedR
            val dg = ((pixel ushr 8) and 255) - expectedG
            val db = (pixel and 255) - expectedB
            return dr * dr + dg * dg + db * db <=
                MAX_CHANNEL_COLOR_DISTANCE_SQUARED
        }
        val x0 = (part.mask.minX - 1).coerceAtLeast(0)
        val x1 = (part.mask.maxX + 1).coerceAtMost(source.width - 1)
        val y0 = (part.mask.minY - 1).coerceAtLeast(0)
        val y1 = (part.mask.maxY + 1).coerceAtMost(source.height - 1)
        for (y in y0..y1) {
            for (x in x0..x1) {
                val index = y * source.width + x
                if (part.mask.pixels[index] == 0 ||
                    !sameSourceColor(index)) continue
                var touchesSelectedInk = false
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val xx = x + dx
                    val yy = y + dy
                    if (xx !in 0 until source.width ||
                        yy !in 0 until source.height) continue
                    if (part.mask.pixels[yy * source.width + xx] == 0)
                        touchesSelectedInk = true
                }
                require(!touchesSelectedInk) {
                    "A imagem original ganhou tinta conectada fora da máscara " +
                        "em $x,$y. Refaça a seleção para não perder detalhes."
                }
            }
        }
        RasterSourceInkProvenanceReport(originalInkPixelsChecked = occupied)
    }
}
