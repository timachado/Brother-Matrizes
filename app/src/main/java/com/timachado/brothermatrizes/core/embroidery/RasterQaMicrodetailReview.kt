package com.timachado.brothermatrizes.core.embroidery

import java.util.Locale

/**
 * Read-only microdetail review, not stitch digitization.
 *
 * The 5-pixel raster counter reported by PANOBIANCO may be a genuine tiny
 * negative-space detail or alpha antialiasing. Preserving the number of holes
 * cannot establish either its intent or its physical stitchability.
 */
internal data class RasterQaMicrodetailReport(
    val source:RasterImagePixels,
    val sourceWidthMm:Float,
    val pixelSizeMm:Float,
    val holeMinX:Int,
    val holeMinY:Int,
    val holeMaxX:Int,
    val holeMaxY:Int,
    val sourceHolePixels:Int,
    val restoredPixels:List<Int>,
    val sourceFingerprint:String,
    val counterRestoredInMask:Boolean,
    val topologyIntact:Boolean,
    val exportPermitted:Boolean=false,
    val manualReviewRequired:Boolean=true
) {
    val detailWidthMm:Float get() =
        (holeMaxX-holeMinX+1)*pixelSizeMm
    val detailHeightMm:Float get() =
        (holeMaxY-holeMinY+1)*pixelSizeMm
    val centerX:Int get()=(holeMinX+holeMaxX)/2
    val centerY:Int get()=(holeMinY+holeMaxY)/2

    fun summary(operatorNote:String="não classificado"):String {
        val mm: (Float)->String={ value ->
            String.format(Locale.US,"%.3f",value)
        }
        return "QA-MICRODETALHE-V5 | " +
            "imagem=${source.width}x${source.height} | " +
            "fingerprint=${sourceFingerprint} | " +
            "largura-escolhida=${mm(sourceWidthMm)} mm | " +
            "escala=${mm(pixelSizeMm)} mm/px | " +
            "vazado=(${holeMinX},${holeMinY})–" +
                "(${holeMaxX},${holeMaxY}) | " +
            "caixa-fisica=${mm(detailWidthMm)}x" +
                "${mm(detailHeightMm)} mm | " +
            "pixels-do-vazado=${sourceHolePixels} | " +
            "pixels-reincluidos=${restoredPixels.size} | " +
            "cavidade-matematicamente-recuperada=${counterRestoredInMask} | " +
            "regioes-preservadas=${topologyIntact} | " +
            "classificacao-visual=${operatorNote} | " +
            "As dimensões são escala geométrica do raster, " +
            "NÃO largura mínima viável de bordado; sem trilhos, " +
            "STITCH, PES ou aprovação física."
    }

    /**
     * Pixelated magnification *within* an immutable QA canvas. Each enlarged
     * block samples ONE unchanged source coordinate (nearest neighbour):
     * gray = >=208; amber = 180..207; green = witnessed reinstatement;
     * charcoal = below 180. There is no interpolation and no stitch preview.
     */
    fun zoomPreview():RasterImagePixels {
        val w=source.width
        val h=source.height
        val output=IntArray(w*h)
        val restored=restoredPixels.toHashSet()
        val level=208
        val halfWindow=12
        val sourceWindow=2*halfWindow+1
        for(y in 0 until h) {
            val sy=(centerY-halfWindow+
                (y.toLong()*sourceWindow/h).toInt()).coerceIn(0,h-1)
            for(x in 0 until w) {
                val sx=(centerX-halfWindow+
                    (x.toLong()*sourceWindow/w).toInt()).coerceIn(0,w-1)
                val pos=sy*w+sx
                val a=source.argb[pos] ushr 24
                output[y*w+x]=when {
                    restored.contains(pos)->0xff60d58c.toInt()
                    a>=level->0xffb1bbc4.toInt()
                    a>=180->0xffefa75b.toInt()
                    else->0xff2d3947.toInt()
                }
            }
        }
        return RasterImagePixels(
            w,h,output,
            "QA Zoom de pixels originais (não é matriz de pontos)"
        )
    }
}

internal object RasterQaMicrodetailReview {
    fun inspect(
        closure:RasterQaCounterClosureProposal,
        leak:RasterAlphaCounterLeakReport,
        chosenWidthMm:Float
    ):Result<RasterQaMicrodetailReport> = runCatching {
        val image=closure.source
        require(
            image.width in 8..512 && image.height in 8..512 &&
            image.argb.size==image.width*image.height &&
            leak.source === image &&
            chosenWidthMm.isFinite() &&
            chosenWidthMm in 10f..300f &&
            closure.fromThreshold==180 &&
            closure.toThreshold==208 &&
            closure.requiresManualReview &&
            closure.candidateForVisualReview &&
            !closure.exportPermitted &&
            !leak.exportPermitted &&
            !RasterEmbroideryApproval.canExportFromImageTool()
        ) {
            "O microdetalhe requer origem, escala e recuperação QA válidas."
        }
        val counter=leak.intervals.singleOrNull {
            it.fromThreshold==closure.fromThreshold &&
                it.toThreshold==closure.toThreshold
        }?.openedCounters?.singleOrNull()
            ?: error("Vazado único não localizado para revisão ampliada.")
        require(counter.holePixels>0 &&
            counter.holeMinX in 0 until image.width &&
            counter.holeMaxX in counter.holeMinX until image.width &&
            counter.holeMinY in 0 until image.height &&
            counter.holeMaxY in counter.holeMinY until image.height &&
            closure.candidatePixels.isNotEmpty() &&
            closure.candidatePixels.all {
                it in image.argb.indices &&
                    (image.argb[it] ushr 24) in 180 until 208
            } &&
            closure.candidatePixels.containsAll(counter.openingPixels)
        ) { "Pixel ou vazado incompatível com a arte de origem." }
        var sourceHash=1469598103934665603L
        for(pixel in image.argb) {
            sourceHash=(sourceHash xor (pixel.toLong() and 0xffffffffL)) *
                1099511628211L
        }
        RasterQaMicrodetailReport(
            source=image,sourceWidthMm=chosenWidthMm,
            pixelSizeMm=chosenWidthMm/image.width.toFloat(),
            holeMinX=counter.holeMinX,holeMinY=counter.holeMinY,
            holeMaxX=counter.holeMaxX,holeMaxY=counter.holeMaxY,
            sourceHolePixels=counter.holePixels,
            restoredPixels=closure.candidatePixels,
            sourceFingerprint=java.lang.Long.toHexString(sourceHash),
            counterRestoredInMask=closure.holeRestored,
            topologyIntact=closure.regionTopologyPreserved
        )
    }
}
