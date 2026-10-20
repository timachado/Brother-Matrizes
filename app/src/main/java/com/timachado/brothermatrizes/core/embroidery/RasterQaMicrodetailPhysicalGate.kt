package com.timachado.brothermatrizes.core.embroidery

import java.util.Locale

/**
 * Physical-scale screening for a candidate raster counter.
 * This is not a machine specification, minimum stitch width, or an
 * automatic decision that any sub-millimetre feature is unembroiderable.
 *
 * The operator's visual hypothesis is independent of the simulated
 * counter topology. Even a "looks intentional" marking CANNOT release
 * a tiny raster feature into satin, tatami, running, PES or production.
 */
internal enum class RasterQaMicrodetailIntent(val caption:String) {
    UNCLASSIFIED("não classificado"),
    APPEARS_INTENTIONAL("aparenta ser detalhe intencional"),
    POSSIBLE_ARTIFACT("possível artefato de imagem");

    companion object {
        fun fromCaption(input:String): RasterQaMicrodetailIntent =
            entries.firstOrNull { it.caption==input } ?: UNCLASSIFIED
    }
}

internal data class RasterQaMicrodetailPhysicalScreen(
    val imageFingerprint:String,
    val chosenWidthMm:Float,
    val detailWidthMm:Float,
    val detailHeightMm:Float,
    val sourceHolePixels:Int,
    val restoredPixels:Int,
    val topologyRecovered:Boolean,
    val intent:RasterQaMicrodetailIntent,
    val subMillimetreWidth:Boolean,
    val physicalStitchOutVerified:Boolean=false,
    val mayAutoDigitize:Boolean=false,
    val mayExportPes:Boolean=false
) {
    val intentReviewed:Boolean get() =
        intent!=RasterQaMicrodetailIntent.UNCLASSIFIED

    // There is no stitch-out or thread/needle/fabric evidence in the
    // source raster diagnostics, so raster-based QA NEVER releases it.
    val requiresPhysicalValidation:Boolean get() =
        !physicalStitchOutVerified

    fun summary():String {
        fun fmt(value:Float)=String.format(Locale.US,"%.3f",value)
        return "QA-MICROSEGURANCA-V6 | " +
            "fingerprint=${imageFingerprint} | " +
            "largura-bordado=${fmt(chosenWidthMm)} mm | " +
            "vazado-fisico=${fmt(detailWidthMm)}x" +
                "${fmt(detailHeightMm)} mm | " +
            "pixels-vazado=${sourceHolePixels} | " +
            "pixels-originais-propostos=${restoredPixels} | " +
            "topologia-matematica-recuperada=${topologyRecovered} | " +
            "largura-submilimetrica=${subMillimetreWidth} " +
                "(alerta descritivo, não limite técnico de máquina) | " +
            "avaliacao-visual=${intent.caption} | " +
            "avaliacao-visual-registrada=${intentReviewed} | " +
            "teste-fisico-comprovado=${physicalStitchOutVerified} | " +
            "digitalizacao-automatica=${mayAutoDigitize} | " +
            "exportacao-PES=${mayExportPes} | " +
            if(intent==RasterQaMicrodetailIntent.POSSIBLE_ARTIFACT)
                "Possível ruído de alfa: verificar arte de origem " +
                    "antes de conservar como espaço bordado."
            else if(!intentReviewed)
                "Intenção visual não classificada: não aprovar " +
                    "esse microvazado sem análise da letra original."
            else
                "Intenção visual sugerida, mas ausência de prova " +
                    "física com tecido, linha e agulha: bloqueado."
    }
}

internal object RasterQaMicrodetailPhysicalGate {
    fun inspect(
        microdetail:RasterQaMicrodetailReport,
        intent:RasterQaMicrodetailIntent
    ):Result<RasterQaMicrodetailPhysicalScreen> = runCatching {
        require(
            microdetail.source.width in 8..512 &&
            microdetail.source.height in 8..512 &&
            microdetail.source.argb.size==
                microdetail.source.width*microdetail.source.height &&
            microdetail.detailWidthMm.isFinite() &&
            microdetail.detailHeightMm.isFinite() &&
            microdetail.detailWidthMm>0f &&
            microdetail.detailHeightMm>0f &&
            microdetail.sourceWidthMm.isFinite() &&
            microdetail.sourceWidthMm in 10f..300f &&
            microdetail.pixelSizeMm.isFinite() &&
            microdetail.sourceHolePixels>0 &&
            microdetail.restoredPixels.isNotEmpty() &&
            microdetail.counterRestoredInMask &&
            microdetail.topologyIntact &&
            microdetail.manualReviewRequired &&
            !microdetail.exportPermitted &&
            !RasterEmbroideryApproval.canExportFromImageTool()
        ) {
            "Microdetalhe inválido ou não protegido: " +
                "continua bloqueado para digitalização."
        }
        var hash=1469598103934665603L
        for(pixel in microdetail.source.argb) {
            hash=(hash xor (pixel.toLong() and 0xffffffffL)) *
                1099511628211L
        }
        require(
            java.lang.Long.toHexString(hash)==
                microdetail.sourceFingerprint &&
            microdetail.restoredPixels.all { i ->
                i in microdetail.source.argb.indices &&
                    (microdetail.source.argb[i] ushr 24) in 180 until 208
            }
        ) { "A imagem mudou ou os pixels QA não são os originais." }
        RasterQaMicrodetailPhysicalScreen(
            imageFingerprint=microdetail.sourceFingerprint,
            chosenWidthMm=microdetail.sourceWidthMm,
            detailWidthMm=microdetail.detailWidthMm,
            detailHeightMm=microdetail.detailHeightMm,
            sourceHolePixels=microdetail.sourceHolePixels,
            restoredPixels=microdetail.restoredPixels.size,
            topologyRecovered=microdetail.counterRestoredInMask &&
                microdetail.topologyIntact,
            intent=intent,
            subMillimetreWidth=microdetail.detailWidthMm<1.0f
        )
    }
}
