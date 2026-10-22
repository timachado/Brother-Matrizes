package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * An independent, read-only inspection of image-derived QA playback.
 *
 * Important: passing geometric checks does NOT certify safe machine
 * embroidery. There is no fabric compensation, underlay, measured density,
 * independent PES verification or actual Brother stitch-out in this flow.
 */
data class PremiumImageQaReport(
    val stitchesChecked: Int,
    val jumpMoves: Int,
    val trims: Int,
    val colorChanges: Int,
    val zeroLengthStitches: Int,
    val veryShortStitches: Int,
    val longStitches: Int,
    val longUntrimmedJumps: Int,
    val transparentCrossings: Int,
    val countConsistent: Boolean,
    val warnings: List<String>
) {
    // Intentionally NO readyToExport / machineSafe / approved property:
    // visual preview validation can never authorize a physical PES.
    val warningCount: Int get() = warnings.size
}

object PremiumImageQaDiagnostics {
    fun inspect(
        design: EmbroideryDesign,
        sourceWidth: Int = 0,
        sourceHeight: Int = 0,
        sourceArgb: IntArray? = null,
        physicalWidthMm: Float = 0f
    ): PremiumImageQaReport {
        val hasSource = sourceArgb != null &&
            sourceWidth in 8..4096 && sourceHeight in 8..4096 &&
            sourceArgb.size == sourceWidth * sourceHeight &&
            physicalWidthMm.isFinite() && physicalWidthMm > 0f

        val heightMm = if(hasSource)
            physicalWidthMm * sourceHeight / sourceWidth else 0f

        fun crossesTransparent(a: EmbroideryPoint, b: EmbroideryPoint):Boolean {
            if(!hasSource) return false
            val raster = sourceArgb!!
            // Machine coordinates are 0.1 mm; 0,0 is centered on the
            // imported raster for ALL current PremiumImage QA previews.
            val xa = (a.xUnits / (physicalWidthMm * 10f) + .5f) * sourceWidth
            val ya = (.5f - a.yUnits / (heightMm * 10f)) * sourceHeight
            val xb = (b.xUnits / (physicalWidthMm * 10f) + .5f) * sourceWidth
            val yb = (.5f - b.yUnits / (heightMm * 10f)) * sourceHeight
            val steps=ceil(hypot(
                (xb-xa).toDouble(),(yb-ya).toDouble()
            )/0.30).toInt().coerceIn(1,2048)
            for(i in 1 until steps) {
                val amount=i.toFloat()/steps
                val xx=kotlin.math.floor(xa+(xb-xa)*amount).toInt()
                val yy=kotlin.math.floor(ya+(yb-ya)*amount).toInt()
                if(xx !in 0 until sourceWidth ||
                    yy !in 0 until sourceHeight ||
                    ((raster[yy*sourceWidth+xx] ushr 24) < 160))return true
            }
            return false
        }

        var stitches = 0
        var jumps = 0
        var trims = 0
        var changes = 0
        var zeros = 0
        var shortCount = 0
        var longCount = 0
        var untrimmedJumps = 0
        var transparentCrossings = 0
        var previous:EmbroideryPoint? = null
        var sinceTrim=false
        for(point in design.points) {
            when(point.command) {
                StitchCommand.STITCH -> {
                    stitches++
                    val from = previous
                    if(from != null) {
                        val dx=(point.xUnits.toLong()-from.xUnits).toDouble()
                        val dy=(point.yUnits.toLong()-from.yUnits).toDouble()
                        val len=hypot(dx,dy)
                        if(len<0.5)zeros++
                        else if(len<3.0)shortCount++ // <0.3 mm
                        if(len>80.0)longCount++ // >8 mm, review only
                        if(crossesTransparent(from,point))
                            transparentCrossings++
                    }
                    previous=point
                    sinceTrim=false
                }
                StitchCommand.JUMP -> {
                    jumps++
                    val from=previous
                    if(from!=null && !sinceTrim) {
                        val dist=hypot(
                            (point.xUnits.toLong()-from.xUnits).toDouble(),
                            (point.yUnits.toLong()-from.yUnits).toDouble()
                        )
                        if(dist>120.0)untrimmedJumps++
                    }
                    previous=point
                    sinceTrim=false
                }
                StitchCommand.TRIM -> {
                    trims++
                    sinceTrim=true
                }
                StitchCommand.COLOR_CHANGE -> {
                    changes++
                    sinceTrim=true
                }
                StitchCommand.STOP,StitchCommand.END,
                StitchCommand.SEQUIN -> {
                    sinceTrim=true
                }
            }
        }
        val consistent=stitches==design.stitchCount &&
            jumps==design.jumpCount && changes==design.colorChanges &&
            design.endFound &&
            design.points.lastOrNull()?.command==StitchCommand.END
        val warnings=ArrayList<String>()
        if(!consistent) warnings+="Contagem ou sequência de comandos inconsistente."
        if(zeros>0)warnings+="$zeros pontos sem deslocamento da agulha."
        if(shortCount>stitches/10 && shortCount>5)
            warnings+="$shortCount pontos muito curtos (abaixo de 0,3 mm)."
        if(longCount>0)
            warnings+="$longCount movimentos costurados acima de 8 mm."
        if(untrimmedJumps>0)
            warnings+="$untrimmedJumps saltos longos sem corte explícito."
        if(trims>stitches/8 && trims>8)
            warnings+="$trims cortes para $stitches pontos: sequência ineficiente."
        if(transparentCrossings>0)
            warnings+="$transparentCrossings trajetos de agulha atravessam vazados do PNG."
        warnings+="Prévia experimental: SEM liberação para máquina ou PES."
        return PremiumImageQaReport(
            stitches,jumps,trims,changes,zeros,shortCount,longCount,
            untrimmedJumps,transparentCrossings,consistent,warnings
        )
    }
}
