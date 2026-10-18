package com.timachado.brothermatrizes.core.embroidery

/**
 * Non-destructive QA audit of image alpha. Anti-aliased edges (alpha 1..254)
 * must NEVER be silently rounded to background or embroidery-thread color.
 * This audit does not modify the original image or generate stitches.
 */
internal data class RasterAlphaEdgeReviewReport(
    val totalPixels: Int,
    val transparentPixels: Int,
    val weakEdgePixels: Int,
    val strongEdgePixels: Int,
    val opaquePixels: Int,
    val firstPartialX: Int?,
    val firstPartialY: Int?
) {
    val partialPixels: Int get() = weakEdgePixels + strongEdgePixels
    val requiresManualReconstruction: Boolean get() = partialPixels > 0

    fun guidance(): String =
        if (!requiresManualReconstruction) {
            "Alfa QA: sem bordas semitransparentes na imagem analisada."
        } else {
            "Bordas semitransparentes: $partialPixels pixel(is), " +
                "$weakEdgePixels com alfa 1..179 e " +
                "$strongEdgePixels com alfa 180..254. " +
                "Primeiro detalhe em (${firstPartialX}, ${firstPartialY}). " +
                "Revise curvas, letras e vazados numa referência plana " +
                "aceita explicitamente; a imagem original será preservada. " +
                "Não reduzir alfa automaticamente nem gerar PES."
        }
}

internal object RasterAlphaEdgeReview {
    fun inspect(image: RasterImagePixels): Result<RasterAlphaEdgeReviewReport> =
        runCatching {
            require(image.width in 8..512 && image.height in 8..512 &&
                image.argb.size == image.width * image.height
            ) { "Imagem fora dos limites da auditoria de bordas QA." }
            var clear=0
            var weak=0
            var strong=0
            var opaque=0
            var first:Int?=null
            for (i in image.argb.indices) {
                when (image.argb[i] ushr 24) {
                    0 -> clear++
                    in 1..179 -> {
                        weak++
                        if (first == null) first=i
                    }
                    in 180..254 -> {
                        strong++
                        if (first == null) first=i
                    }
                    else -> opaque++
                }
            }
            require(clear + weak + strong + opaque == image.argb.size)
            RasterAlphaEdgeReviewReport(
                totalPixels=image.argb.size,
                transparentPixels=clear,
                weakEdgePixels=weak,
                strongEdgePixels=strong,
                opaquePixels=opaque,
                firstPartialX=first?.rem(image.width),
                firstPartialY=first?.div(image.width)
            )
        }
}
