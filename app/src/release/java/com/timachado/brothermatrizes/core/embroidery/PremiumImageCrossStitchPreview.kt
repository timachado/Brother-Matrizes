package com.timachado.brothermatrizes.core.embroidery

/** Commercial build: cross-stitch from source image remains experimental. */
internal object PremiumImageCrossStitchPreview {
    fun create(
        width:Int,height:Int,argb:IntArray,
        widthMm:Float,hoop:HoopProfile,maxColors:Int=4
    ):Result<EmbroideryDesign> = Result.failure(
        IllegalStateException("Ponto cruz de imagem não homologado para exportação.")
    )
}
