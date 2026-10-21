package com.timachado.brothermatrizes.core.embroidery

/** Production flavor: no PES laboratory handoff is compiled. */
internal object RasterPhysicalQaTestBridge {
    fun createOutlinePes(
        source: RasterImagePixels,
        widthMm: Float,
        hoop: HoopProfile
    ): Result<ConvertedMatrix> = Result.failure(
        IllegalStateException(
            "Exportação física experimental indisponível na versão comercial."
        )
    )
}
