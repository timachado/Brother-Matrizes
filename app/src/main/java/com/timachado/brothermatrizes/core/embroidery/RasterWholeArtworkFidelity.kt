package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max

/**
 * QA-only fidelity audit over the COMPLETE assembled STITCH stream.
 *
 * The per-component audit misses errors introduced during composition:
 * omitted colors, wrong thread indexes or misplaced components. This audit
 * re-separates the SOURCE using the same 4-color pipeline and compares each
 * final STITCH to its expected source-color region. It does not equate the
 * bitmap overlay with sewn stitches or provide physical fabric certification.
 *
 * Accepted simple logos are a narrow subset. Complex photograph/gradient
 * images remain blocked by RasterArtworkComplexityAudit.
 */
internal data class RasterWholeArtworkFidelityReport(
    val sourceInkPixels: Int,
    val inkPixelsCovered: Int,
    val sewnSamples: Int,
    val samplesOffInk: Int,
    val samplesWrongColor: Int,
    val significantColorMinimumCoverage: Double
) {
    val coverage: Double get() =
        if (sourceInkPixels == 0) 0.0
        else inkPixelsCovered.toDouble() / sourceInkPixels
    val offInkFraction: Double get() =
        if (sewnSamples == 0) 1.0
        else samplesOffInk.toDouble()/sewnSamples
    val wrongColorFraction: Double get() =
        if (sewnSamples == 0) 1.0
        else samplesWrongColor.toDouble()/sewnSamples
}

internal object RasterWholeArtworkFidelity {
    private const val MAX_SAMPLES = 180_000
    private const val MAX_STITCHES = 90_000

    fun inspect(
        image: RasterImagePixels,
        design: EmbroideryDesign,
        fullWidthMm: Float
    ): Result<RasterWholeArtworkFidelityReport> = runCatching {
        require(image.width in 8..512 && image.height in 8..512 &&
            image.argb.size == image.width * image.height &&
            design.format == "QA_AUTO_STITCH" &&
            design.sourceBytes.isEmpty() && design.isModified &&
            design.points.size in 3..MAX_STITCHES &&
            fullWidthMm.isFinite() && fullWidthMm in 10f..200f &&
            design.threadColors.isNotEmpty() &&
            design.points.last().command == StitchCommand.END
        ) {
            "Auditoria completa exige prévia QA e imagem original, nunca PES."
        }
        val source = MultiColorRasterDigitizer.separate(
            image,
            MultiColorRasterOptions(maxColors=4,preserveFineDetails=true)
        ).getOrThrow()
        require(source.pixels.size == image.argb.size &&
            source.colors.isNotEmpty() && source.colors.size <= 4
        ) { "Separação da imagem original incompatível." }

        val mmPx=fullWidthMm.toDouble()/image.width
        val unitsPx=mmPx*10.0
        val pixels=image.width*image.height
        val covered=BooleanArray(pixels)
        val sourceTotals=IntArray(source.colors.size)
        val sourceCovered=IntArray(source.colors.size)
        for (idx in source.pixels.indices) {
            val color=source.pixels[idx]
            if (color>=0) sourceTotals[color]++
        }
        val significantThreshold=max(8,sourceTotals.sum()/200)
        val coverRadius=(.34/mmPx+.40).coerceIn(.70,5.0)
        val coverSearch=ceil(coverRadius).toInt()
        val localSearch=ceil((.20/mmPx+.50).coerceAtMost(3.0)).toInt()

        fun point(p: EmbroideryPoint): Pair<Double,Double> =
            (p.xUnits/unitsPx+image.width/2.0) to
                (image.height/2.0-p.yUnits/unitsPx)

        fun bestColorAt(x:Double,y:Double): Int {
            val cx=floor(x).toInt()
            val cy=floor(y).toInt()
            var result=-1
            var closest=Double.POSITIVE_INFINITY
            for (dy in -localSearch..localSearch)
                for (dx in -localSearch..localSearch) {
                    val px=cx+dx;val py=cy+dy
                    if (px !in 0 until image.width ||
                        py !in 0 until image.height) continue
                    val color=source.pixels[py*image.width+px]
                    if (color<0) continue
                    val dist=hypot(px+.5-x,py+.5-y)
                    if (dist<closest) {
                        closest=dist
                        result=color
                    }
                }
            return if (closest<=.20/mmPx+.55) result else -1
        }

        var previous: EmbroideryPoint?=null
        var samples=0
        var wrong=0
        var outside=0
        var stitches=0
        var jumps=0
        for (p in design.points) {
            when(p.command) {
                StitchCommand.JUMP -> { previous=p; jumps++ }
                StitchCommand.STITCH -> {
                    val start=previous ?: error(
                        "STITCH após corte sem JUMP de posicionamento."
                    )
                    require(start.colorIndex==p.colorIndex &&
                        p.colorIndex in design.threadColors.indices) {
                        "Transição de cor não documentada."
                    }
                    val (ax,ay)=point(start)
                    val (bx,by)=point(p)
                    val len=hypot(bx-ax,by-ay)
                    val steps=ceil(len*2.0).toInt().coerceAtLeast(1)
                    require(len*mmPx in .2..7.8)
                    val threadColor=design.threadColors[p.colorIndex]
                    for (i in 0..steps) {
                        samples++
                        require(samples<=MAX_SAMPLES) {
                            "Amostras de fidelidade acima do limite QA."
                        }
                        val t=i.toDouble()/steps
                        val x=ax+(bx-ax)*t
                        val y=ay+(by-ay)*t
                        val matched=bestColorAt(x,y)
                        if(matched<0) {
                            outside++
                        } else if(matched>=source.colors.size ||
                            source.colors[matched]!=threadColor) {
                            wrong++
                        }
                        val cx=floor(x).toInt()
                        val cy=floor(y).toInt()
                        for(dy in -coverSearch..coverSearch)
                            for(dx in -coverSearch..coverSearch) {
                                val px=cx+dx;val py=cy+dy
                                if(px !in 0 until image.width ||
                                    py !in 0 until image.height)continue
                                val idx=py*image.width+px
                                val label=source.pixels[idx]
                                if(label<0 || source.colors[label]!=threadColor)continue
                                if(hypot(px+.5-x,py+.5-y)<=coverRadius)
                                    covered[idx]=true
                            }
                    }
                    previous=p
                    stitches++
                }
                StitchCommand.TRIM,StitchCommand.COLOR_CHANGE,
                StitchCommand.STOP -> previous=null
                StitchCommand.END -> previous=null
                StitchCommand.SEQUIN -> error("Comando QA incompatível.")
            }
        }
        require(stitches==design.stitchCount &&
            jumps==design.jumpCount && stitches>0 && samples>0
        ) {
            "Contagem de pontos inválida para a comparação."
        }
        var ink=0
        var matched=0
        for (idx in source.pixels.indices) {
            val color=source.pixels[idx]
            if(color<0)continue
            ink++
            if(covered[idx]) {
                matched++
                sourceCovered[color]++
            }
        }
        var minimum=1.0
        for (i in sourceTotals.indices) {
            if(sourceTotals[i] < significantThreshold)continue
            minimum=minOf(minimum,
                sourceCovered[i].toDouble()/sourceTotals[i])
        }
        RasterWholeArtworkFidelityReport(
            sourceInkPixels=ink,inkPixelsCovered=matched,
            sewnSamples=samples,samplesOffInk=outside,
            samplesWrongColor=wrong,
            significantColorMinimumCoverage=minimum
        )
    }
}
