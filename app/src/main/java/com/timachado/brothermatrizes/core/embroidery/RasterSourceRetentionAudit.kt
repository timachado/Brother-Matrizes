package com.timachado.brothermatrizes.core.embroidery

/**
 * Checks IMAGE INTAKE against the original pixels, BEFORE any vectorization.
 *
 * Previous whole-artwork fidelity checks used the same reduced palette as
 * the component detector. A tiny third-color accent discarded by the
 * segmenter could therefore disappear from both the generated stitches AND
 * the so-called original reference, yielding a false "pass".
 *
 * This audit independently estimates original foreground pixels from alpha
 * or the median of border pixels. It then checks whether those pixels were
 * retained, or incorrectly recolored, in the four-color segmentation.
 *
 * Thresholds are deliberately conservative QA heuristics, NOT embroidery
 * machine limits or proof of visual/physical fidelity.
 */
internal data class RasterSourceRetentionReport(
    val foregroundPixels: Int,
    val omittedForegroundPixels: Int,
    val heavilyRecoloredPixels: Int,
    val largestDamagedDetailPixels: Int
) {
    val affectedFraction: Double get() =
        if (foregroundPixels == 0) 1.0
        else (omittedForegroundPixels + heavilyRecoloredPixels).toDouble() /
            foregroundPixels
    val preservesDetails: Boolean get() =
        largestDamagedDetailPixels < 4 &&
            affectedFraction <= .008
}

internal object RasterSourceRetentionAudit {
    private const val MIN_COLOR_DISTANCE = 72
    private const val RECOLOR_DISTANCE = 110

    fun inspect(
        source: RasterImagePixels
    ): Result<RasterSourceRetentionReport> = runCatching {
        require(source.width in 8..512 && source.height in 8..512 &&
            source.argb.size == source.width * source.height) {
            "Imagem original inválida para conferir detalhes."
        }
        val separated = MultiColorRasterDigitizer.separate(
            source, MultiColorRasterOptions(
                maxColors = 4, preserveFineDetails = true
            )
        ).getOrThrow()
        val n = source.argb.size
        require(separated.pixels.size == n &&
            separated.colors.size in 1..4
        ) {
            "A segmentação não corresponde aos pixels da imagem original."
        }
        val hasAlpha = source.argb.any { (it ushr 24) < 128 }
        val bordersR = ArrayList<Int>()
        val bordersG = ArrayList<Int>()
        val bordersB = ArrayList<Int>()
        if (!hasAlpha) {
            for (y in 0 until source.height) for (x in 0 until source.width) {
                if (x in 2 until source.width-2 &&
                    y in 2 until source.height-2) continue
                val c=source.argb[y*source.width+x]
                bordersR += (c ushr 16) and 255
                bordersG += (c ushr 8) and 255
                bordersB += c and 255
            }
        }
        fun median(a: MutableList<Int>): Int {
            if (a.isEmpty()) return 0
            a.sort()
            return a[a.size/2]
        }
        val br=median(bordersR)
        val bg=median(bordersG)
        val bb=median(bordersB)
        fun colorDistanceSquared(r:Int,g:Int,b:Int, c:Int): Int {
            val dr=r-((c ushr 16) and 255)
            val dg=g-((c ushr 8) and 255)
            val db=b-(c and 255)
            return dr*dr+dg*dg+db*db
        }
        val lost=BooleanArray(n)
        var activeCount=0
        var missing=0
        var heavilyRecolored=0
        for (i in 0 until n) {
            val pixel=source.argb[i]
            val r=(pixel ushr 16) and 255
            val g=(pixel ushr 8) and 255
            val b=pixel and 255
            val foreground=if (hasAlpha) (pixel ushr 24)>=180
                else {
                    val dr=r-br;val dg=g-bg;val db=b-bb
                    dr*dr+dg*dg+db*db>=
                        MIN_COLOR_DISTANCE*MIN_COLOR_DISTANCE
                }
            if (!foreground) continue
            activeCount++
            val assigned=separated.pixels[i]
            if (assigned !in separated.colors.indices) {
                missing++
                lost[i]=true
                continue
            }
            if (colorDistanceSquared(r,g,b,separated.colors[assigned])>
                RECOLOR_DISTANCE*RECOLOR_DISTANCE) {
                heavilyRecolored++
                lost[i]=true
            }
        }
        require(activeCount>=15) { "Arte sem detalhes de tinta suficientes." }

        // Eight-neighbor component traversal. A 4-pixel punctuation accent
        // matters even if the overall loss percentage is under 1%.
        val visited=BooleanArray(n)
        val queue=IntArray(n)
        var largest=0
        for (root in 0 until n) {
            if (!lost[root] || visited[root]) continue
            var head=0;var tail=1
            queue[0]=root
            visited[root]=true
            while (head<tail) {
                val cell=queue[head++]
                val x=cell%source.width
                val y=cell/source.width
                for (dy in -1..1) for (dx in -1..1) {
                    if(dx==0 && dy==0)continue
                    val xx=x+dx;val yy=y+dy
                    if(xx !in 0 until source.width ||
                        yy !in 0 until source.height)continue
                    val idx=yy*source.width+xx
                    if (lost[idx] && !visited[idx]) {
                        visited[idx]=true
                        queue[tail++]=idx
                    }
                }
            }
            largest=maxOf(largest,tail)
        }
        RasterSourceRetentionReport(
            foregroundPixels=activeCount,
            omittedForegroundPixels=missing,
            heavilyRecoloredPixels=heavilyRecolored,
            largestDamagedDetailPixels=largest
        )
    }

    fun requirePreserved(source: RasterImagePixels): Result<RasterSourceRetentionReport> =
        inspect(source).mapCatching { report ->
            require(report.preservesDetails) {
                "A separação automática perdeu ou recoloriu detalhes da imagem " +
                    "original: ${report.omittedForegroundPixels} pixels omitidos, " +
                    "${report.heavilyRecoloredPixels} recoloridos; " +
                    "maior detalhe prejudicado ${report.largestDamagedDetailPixels} px. " +
                    "Nenhuma simulação do logo inteiro foi liberada. " +
                    "Revise as cores e os detalhes antes de bordar."
            }
            report
        }
}
