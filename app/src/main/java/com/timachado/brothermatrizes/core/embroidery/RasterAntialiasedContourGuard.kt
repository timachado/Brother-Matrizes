package com.timachado.brothermatrizes.core.embroidery

/**
 * QA-only topology gate for source antialiasing. A hard global 5% fringe
 * threshold rejects legitimate thin letters simply because they have a large
 * perimeter. Instead, require EVERY partially transparent pixel below the
 * working-ink alpha threshold to lie within two pixels of exactly one solid
 * connected ink island.
 *
 * The outer fringe is NEVER added to the flat source or to STITCH. This only
 * establishes whether a reference can be shown for explicit operator review.
 * Dense shadows, detached faint accents and alpha bridges between letters
 * fail closed rather than being silently discarded.
 */
internal data class RasterAntialiasedContourReport(
    val solidPixels: Int,
    val weakAlphaPixels: Int,
    val strongPartialAlphaPixels: Int,
    val solidIslands: Int,
    val maxWeakDistancePixels: Int,
    val requiresOriginalComparison: Boolean = true,
    val exportPermitted: Boolean = false
) {
    fun summary(): String =
        "Contorno QA: $weakAlphaPixels px de franja fraca " +
            "a no máximo $maxWeakDistancePixels px da tinta, " +
            "$strongPartialAlphaPixels px com alfa 180..254, " +
            "$solidIslands ilha(s) de tinta. " +
            "Pixels fracos não entram na matriz provisória. " +
            "Compare a silhueta original, vazados e letras antes de aceitar."
}

internal object RasterAntialiasedContourGuard {
    private const val THRESHOLD = 180
    private const val RADIUS = 2
    private const val MAX_WEAK_RATIO = .65

    fun inspect(source: RasterImagePixels): Result<RasterAntialiasedContourReport> =
        runCatching {
            val w=source.width
            val h=source.height
            require(w in 8..512 && h in 8..512 &&
                source.argb.size==w*h &&
                !RasterEmbroideryApproval.canExportFromImageTool()
            ) { "Imagem ou permissão fora dos limites do contorno QA." }

            val alpha=IntArray(w*h) { source.argb[it] ushr 24 }
            val labels=IntArray(w*h)
            val queue=IntArray(w*h)
            var islands=0
            var solid=0
            var strong=0
            for(i in alpha.indices) {
                if(alpha[i]>=THRESHOLD) {
                    solid++
                    if(alpha[i]<255) strong++
                }
                if(alpha[i]<THRESHOLD || labels[i]!=0) continue
                islands++
                labels[i]=islands
                queue[0]=i
                var front=0
                var back=1
                while(front<back) {
                    val pos=queue[front++]
                    val x=pos%w
                    val y=pos/w
                    for(dy in -1..1) for(dx in -1..1) {
                        if(dx==0 && dy==0) continue
                        val xx=x+dx
                        val yy=y+dy
                        if(xx !in 0 until w || yy !in 0 until h) continue
                        val at=yy*w+xx
                        if(alpha[at]>=THRESHOLD && labels[at]==0) {
                            labels[at]=islands
                            queue[back++]=at
                        }
                    }
                }
            }
            require(solid>=24 && islands>=1) {
                "Imagem sem traços sólidos para proteger contornos."
            }

            var weak=0
            var farthest=0
            for(i in alpha.indices) {
                if(alpha[i] !in 1 until THRESHOLD) continue
                weak++
                val x=i%w
                val y=i/w
                var nearIsland=0
                var nearest=RADIUS+1
                for(dy in -RADIUS..RADIUS) for(dx in -RADIUS..RADIUS) {
                    val xx=x+dx
                    val yy=y+dy
                    if(xx !in 0 until w || yy !in 0 until h) continue
                    val label=labels[yy*w+xx]
                    if(label==0) continue
                    // Chebyshev pixel envelope follows horizontal, vertical
                    // and diagonal anti-aliased edges without eroding corners.
                    val distance=maxOf(kotlin.math.abs(dx),kotlin.math.abs(dy))
                    nearest=minOf(nearest,distance)
                    require(nearIsland==0 || nearIsland==label) {
                        "Franja alfa junta duas letras ou ilhas de tinta " +
                            "na posição (${x}, ${y}); faça recorte manual."
                    }
                    nearIsland=label
                }
                require(nearIsland!=0 && nearest<=RADIUS) {
                    "Pixel semitransparente isolado em (${x}, ${y}): " +
                        "possível letra, acento ou sombra perdida. " +
                        "Não é seguro apagar essa parte."
                }
                farthest=maxOf(farthest,nearest)
            }
            // An extremely large weak-alpha area is not a narrow contour even
            // if several thin solid strokes happen to be nearby.
            require(weak <= maxOf(8,(solid*MAX_WEAK_RATIO).toInt())) {
                "Alfa difuso em proporção elevada; confira a imagem e " +
                    "reconstrua letras/contornos manualmente."
            }
            RasterAntialiasedContourReport(
                solidPixels=solid,weakAlphaPixels=weak,
                strongPartialAlphaPixels=strong,
                solidIslands=islands,
                maxWeakDistancePixels=farthest
            )
        }
}
