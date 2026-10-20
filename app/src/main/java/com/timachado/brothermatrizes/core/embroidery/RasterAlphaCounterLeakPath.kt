package com.timachado.brothermatrizes.core.embroidery

/**
 * QA ONLY: find where an enclosed background counter (a hole in the ink)
 * becomes connected to the outer background as the alpha threshold rises.
 *
 * Ink is 8-connected; background and path search are 4-connected.
 * Flood-fill the high-threshold exterior once per interval, with a parent
 * direction towards the edge. Each former hole can then provide a concrete
 * opening witness made only of source pixels deleted in that interval.
 * The route is one possible shortest exterior path, not an edit proposal,
 * and is NEVER fed to any STITCH/PES generator.
 */
internal data class RasterQaOpenedCounter(
    val fromThreshold:Int,
    val toThreshold:Int,
    val holeMinX:Int,
    val holeMinY:Int,
    val holeMaxX:Int,
    val holeMaxY:Int,
    val holePixels:Int,
    val openingPixels:List<Int>,
    val openingMinX:Int,
    val openingMinY:Int,
    val openingMaxX:Int,
    val openingMaxY:Int
) {
    fun summary():String =
        "vazado QA (${holeMinX},${holeMinY})–" +
            "(${holeMaxX},${holeMaxY}), ${holePixels} px: " +
            "abertura em (${openingMinX},${openingMinY})–" +
            "(${openingMaxX},${openingMaxY}), " +
            "${openingPixels.size} px removidos na rota de fuga"
}

internal data class RasterQaCounterLeakInterval(
    val fromThreshold:Int,
    val toThreshold:Int,
    val openedCounters:List<RasterQaOpenedCounter>
) {
    fun summary():String =
        "${fromThreshold}→${toThreshold}: " +
            "${openedCounters.size} vazado(s) aberto(s)" +
            (if(openedCounters.isNotEmpty()) "; " +
                openedCounters.take(5).joinToString(" | ") { it.summary() }
             else "")
}

internal data class RasterAlphaCounterLeakReport(
    val source:RasterImagePixels,
    val intervals:List<RasterQaCounterLeakInterval>,
    val exportPermitted:Boolean=false,
    val requiresManualReview:Boolean=true
) {
    fun summary():String =
        "Localização de vazados perdidos QA: " +
            intervals.joinToString("; ") { it.summary() } +
            ". Ciano = rota de abertura detectada no raster original. " +
            "Essas coordenadas não são caminhos de agulha."

    fun overlay(
        fromThreshold:Int,toThreshold:Int,
        base:RasterImagePixels
    ):RasterImagePixels {
        require(base.width==source.width &&
            base.height==source.height &&
            base.argb.size==source.argb.size
        ) { "Prévia incompatível com imagem original." }
        val interval=intervals.singleOrNull {
            it.fromThreshold==fromThreshold &&
                it.toThreshold==toThreshold
        } ?: error("Intervalo não auditado.")
        val cyan=0xff14c9e5.toInt()
        val copy=base.argb.copyOf()
        for(counter in interval.openedCounters) {
            for(index in counter.openingPixels) {
                val alpha=source.argb[index] ushr 24
                require(alpha in fromThreshold until toThreshold) {
                    "A rota do vazado não pertence à faixa alfa excluída."
                }
                copy[index]=cyan
            }
        }
        return RasterImagePixels(
            source.width,source.height,copy,
            "Prévia QA de vazados e aberturas ${fromThreshold}→${toThreshold}"
        )
    }
}

internal object RasterAlphaCounterLeakPath {
    fun inspect(
        source:RasterImagePixels,
        intervals:List<Pair<Int,Int>>
    ):Result<RasterAlphaCounterLeakReport> = runCatching {
        val w=source.width
        val h=source.height
        val n=w*h
        require(w in 8..512 && h in 8..512 &&
            source.argb.size==n && intervals.size in 1..8 &&
            intervals.distinct().size==intervals.size &&
            intervals.all { (a,b) -> a in 1..254 && b in 2..255 && a<b } &&
            !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Fonte ou faixas fora dos limites da auditoria QA." }
        val alpha=IntArray(n) { source.argb[it] ushr 24 }
        val queue=IntArray(n)

        // Four neighbours. The same dual-connectivity rule is used by
        // RasterAlphaStructuralAudit for enclosed-counter counting.
        fun neighbours(p:Int):IntArray {
            val x=p%w;val y=p/w
            return intArrayOf(
                if(x>0)p-1 else -1,
                if(x<w-1)p+1 else -1,
                if(y>0)p-w else -1,
                if(y<h-1)p+w else -1
            )
        }

        val audits=intervals.map { (from,to) ->
            // parent==-2: not part of exterior (or ink).
            // parent==-1: edge seed. Otherwise parent leads toward the edge.
            val parent=IntArray(n) { -2 }
            var head=0;var tail=0
            for(i in 0 until n) {
                val x=i%w;val y=i/w
                if((x==0||y==0||x==w-1||y==h-1) &&
                    alpha[i]<to && parent[i]==-2
                ) {
                    parent[i]=-1
                    queue[tail++]=i
                }
            }
            while(head<tail) {
                val p=queue[head++]
                for(next in neighbours(p)) {
                    if(next<0 || parent[next]!=-2 || alpha[next]>=to)
                        continue
                    parent[next]=p
                    queue[tail++]=next
                }
            }

            val seen=BooleanArray(n)
            val opened=ArrayList<RasterQaOpenedCounter>()
            for(root in 0 until n) {
                if(alpha[root]>=from || seen[root])continue
                seen[root]=true
                queue[0]=root
                head=0;tail=1
                var outsideAtLow=false
                var left=w;var top=h;var right=0;var bottom=0
                while(head<tail) {
                    val p=queue[head++]
                    val x=p%w;val y=p/w
                    left=minOf(left,x);top=minOf(top,y)
                    right=maxOf(right,x);bottom=maxOf(bottom,y)
                    if(x==0||y==0||x==w-1||y==h-1)
                        outsideAtLow=true
                    for(next in neighbours(p)) {
                        if(next<0 || seen[next] || alpha[next]>=from)
                            continue
                        seen[next]=true
                        queue[tail++]=next
                    }
                }
                // Enclosed at from, but attached to the outer background at
                // to: the counter truly opened under the selected threshold.
                if(outsideAtLow || parent[root]==-2)continue
                val band=ArrayList<Int>()
                var at=root
                var walk=0
                while(at>=0 && walk++<=n) {
                    if(alpha[at] in from until to)band+=at
                    at=parent[at]
                }
                require(walk<=n+1 && band.isNotEmpty()) {
                    "Vazado abriu sem rota de pixels removidos verificável."
                }
                var x0=w;var y0=h;var x1=0;var y1=0
                for(pixel in band) {
                    val x=pixel%w;val y=pixel/w
                    x0=minOf(x0,x);y0=minOf(y0,y)
                    x1=maxOf(x1,x);y1=maxOf(y1,y)
                }
                opened+=RasterQaOpenedCounter(
                    from,to,left,top,right,bottom,tail,band,
                    x0,y0,x1,y1
                )
            }
            RasterQaCounterLeakInterval(from,to,
                opened.sortedWith(
                    compareByDescending<RasterQaOpenedCounter> {
                        it.holePixels
                    }.thenBy { it.holeMinY }.thenBy { it.holeMinX }
                )
            )
        }
        RasterAlphaCounterLeakReport(source,audits)
    }
}
