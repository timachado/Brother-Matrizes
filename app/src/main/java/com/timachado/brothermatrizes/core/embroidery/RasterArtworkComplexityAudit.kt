package com.timachado.brothermatrizes.core.embroidery

/**
 * Preflight risk signal, NOT an embroidery digitizer.
 *
 * An imported JPG is not a PES: it has no stitch order, density, JUMP/TRIM,
 * underlay or thread mapping. In particular, navy/gold multi-tone logos should
 * not be presented as automatic single-mode Satin designs.
 *
 * It never changes the existing original PES simulator or readers.
 */
internal data class RasterArtworkComplexity(
    val foregroundPixels: Int,
    val backgroundPixels: Int,
    val darkBluePixels: Int,
    val warmGoldPixels: Int,
    val tonalBuckets: Int,
    val requiresMultiTechniqueReview: Boolean
)

internal object RasterArtworkComplexityAudit {
    fun examine(image: RasterImagePixels): RasterArtworkComplexity {
        require(image.width in 1..512 && image.height in 1..512 &&
            image.argb.size == image.width * image.height) {
            "Imagem de referência inválida."
        }
        var foreground = 0
        var background = 0
        var darkBlue = 0
        var warmGold = 0
        val buckets = HashSet<Int>()
        // Independent from any particular brand palette: grayscale photos
        // and high-shade logos must not bypass review merely because they
        // have no navy/gold combination.
        val fineTones = HashSet<Int>()
        for (argb in image.argb) {
            val alpha = argb ushr 24
            val r = argb ushr 16 and 0xFF
            val g = argb ushr 8 and 0xFF
            val b = argb and 0xFF
            if (alpha < 128 || (r >= 238 && g >= 238 && b >= 238)) {
                background++
                continue
            }
            foreground++
            if (r < 100 && g < 150 && b > r + 12 && b >= g) darkBlue++
            if (r > 95 && r > g * 1.08f && g > b * 1.12f) warmGold++
            buckets += ((r / 24) shl 8) + ((g / 24) shl 4) + (b / 24)
            fineTones += ((r / 8) shl 10) or ((g / 8) shl 5) or (b / 8)
        }
        // Conservative mixed-ink signal: multiple tonal shades in both
        // dark/cool and gold/warm portions, on a prominent light background.
        // Not all advanced logos follow this palette, so no all-clear is implied
        // when it is false. Geometry validators still independently run.
        val complexMixedLogo = background > image.argb.size / 8 &&
            darkBlue > image.argb.size / 100 &&
            warmGold > image.argb.size / 100 &&
            buckets.size >= 4 &&
            foreground > image.argb.size / 10
        // Conservative preflight for multishade photographs and gradients,
        // including monochrome photography with no warm/gold/navy pixels.
        // Synthetic two-color vector shapes still pass this stage.
        val complexGradient = background > image.argb.size / 10 &&
            foreground > image.argb.size / 5 &&
            fineTones.size >= 20
        val complex = complexMixedLogo || complexGradient
        return RasterArtworkComplexity(
            foregroundPixels = foreground,
            backgroundPixels = background,
            darkBluePixels = darkBlue,
            warmGoldPixels = warmGold,
            tonalBuckets = buckets.size,
            requiresMultiTechniqueReview = complex
        )
    }
}
