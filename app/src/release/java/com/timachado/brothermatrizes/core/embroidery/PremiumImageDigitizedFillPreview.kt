package com.timachado.brothermatrizes.core.embroidery

/** Production build: preview image-to-embroidery remains under QA. */
internal object PremiumImageDigitizedFillPreview {
    fun create(
        width:Int,height:Int,argb:IntArray,
        widthMm:Float,hoop:HoopProfile,maxColors:Int=4
    ):Result<EmbroideryDesign> = Result.failure(
        IllegalStateException("Conversão digitalizada experimental não homologada.")
    )
}
