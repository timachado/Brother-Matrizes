package com.timachado.brothermatrizes.core.embroidery

/**
 * Release fail-closed stub: simulation-only raster scanning remains QA.
 * Official commercial APK does not include the experimental generator.
 */
internal object PremiumImageVisualStitchPreview {
    fun create(
        width: Int,height: Int,argb: IntArray,widthMm: Float,hoop: HoopProfile
    ): Result<EmbroideryDesign> = Result.failure(
        IllegalStateException(
            "Simulação experimental de imagem não habilitada no aplicativo comercial."
        )
    )
}
