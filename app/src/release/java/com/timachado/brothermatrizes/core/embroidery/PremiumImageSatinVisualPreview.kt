package com.timachado.brothermatrizes.core.embroidery

/** Commercial APK: experimental raster Satin simulator is disabled. */
internal object PremiumImageSatinVisualPreview {
    fun create(width:Int,height:Int,argb:IntArray,
        widthMm:Float,hoop:HoopProfile
    ):Result<EmbroideryDesign> = Result.failure(
        IllegalStateException(
            "Simulação Satin experimental indisponível no APK comercial."
        )
    )
}
