package com.timachado.brothermatrizes.core.embroidery

/** Commercial build: physical image laboratory is not implemented. */
object PremiumImagePhysicalLab {
    fun propose(
        width:Int,height:Int,argb:IntArray,
        chosenWidthMm:Float,hoop:HoopProfile
    ):Result<EmbroideryDesign> = Result.failure(
        IllegalStateException("Prévia física experimental não disponível no APK comercial.")
    )
    fun pesForBench(design:EmbroideryDesign):Result<ConvertedMatrix> =
        Result.failure(IllegalStateException(
            "Exportação de imagem não homologada na versão comercial."
        ))
}
