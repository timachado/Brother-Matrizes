package com.timachado.brothermatrizes.core.embroidery

/** Assess raster stitchability BEFORE claiming a generated PES is ready. */
data class RasterStitchReadinessReport(
    val rasterWidthPx: Int,
    val estimatedMmPerPixel: Float,
    val shortSegmentFraction: Float,
    val fineSegments: Int,
    val warnings: List<String>
)

object RasterStitchReadiness {
    /**
     * Estimates whether visible glyph strokes are smaller than the 0.4 mm
     * practical minimum for a raster fill segment. This is a diagnostic,
     * not a guarantee of stitch quality or a replacement for test embroidery.
     */
    fun inspect(
        art: SeparatedColorArtwork,
        imageWidth: Int,
        imageHeight: Int,
        finalWidthMm: Float
    ): RasterStitchReadinessReport {
        require(imageWidth in 8..512 && imageHeight in 8..512 &&
            art.pixels.size == imageWidth * imageHeight &&
            finalWidthMm.isFinite() && finalWidthMm in 10f..200f) {
            "Arte ou dimensões de matriz inválidas."
        }
        val widthPixels = art.maxX - art.minX + 1
        val mmPerPixel = finalWidthMm / widthPixels.coerceAtLeast(1)
        var spans = 0
        var thin = 0
        var minLength = Float.POSITIVE_INFINITY
        for (y in art.minY..art.maxY) {
            var x = art.minX
            while (x <= art.maxX) {
                val label = art.pixels[y * imageWidth + x]
                if (label < 0) { x++; continue }
                val start = x
                while (x <= art.maxX && art.pixels[y * imageWidth + x] == label) x++
                spans++
                val widthMm = (x - start) * mmPerPixel
                if (widthMm < .4f) thin++
                if (widthMm < minLength) minLength = widthMm
            }
        }
        val shortFraction = thin.toFloat() / spans.coerceAtLeast(1)
        val warnings = ArrayList<String>()
        if (widthPixels < 180) {
            warnings += "Arte pequena (${widthPixels}px úteis). Importar uma versão de maior resolução pode preservar melhor as letras."
        }
        if (spans >= 20 && shortFraction >= .18f) {
            warnings += "${(shortFraction * 100).toInt()}% dos pequenos traços horizontais ficam abaixo de 0,4 mm. Texto fino pode sair fragmentado."
        }
        if (spans > 30 && minLength < .4f) {
            warnings += "Detalhes finos detectados: confira especialmente as letras menores na prévia do PES."
        }
        return RasterStitchReadinessReport(
            rasterWidthPx = widthPixels,
            estimatedMmPerPixel = mmPerPixel,
            shortSegmentFraction = shortFraction,
            fineSegments = thin,
            warnings = warnings
        )
    }
}
