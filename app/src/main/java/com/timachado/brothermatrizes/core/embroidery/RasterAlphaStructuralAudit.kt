package com.timachado.brothermatrizes.core.embroidery

/**
 * QA diagnostics only, working on the user's actual source pixels.
 * Finds threshold-dependent ink connectors between surviving components and
 * counts enclosed transparent counters. Neither a connector nor a counter
 * is assumed to be a specific PANOBIANCO letter.
 *
 * Foreground uses 8-neighbor connectivity and background uses its
 * complementary 4-neighbor connectivity, avoiding diagonal-hole ambiguity.
 * No source pixels are changed and no embroidery commands are generated.
 */
internal data class RasterAlphaBridgeCandidate(
    val fromThreshold: Int,
    val toThreshold: Int,
    val minX: Int,
    val minY: Int,
    val maxX: Int,
    val maxY: Int,
    val connectorPixels: Int,
    val survivingRegionsTouched: Int,
    val indices: List<Int>
) {
    fun summary(): String =
        "conexão alfa QA (${minX},${minY})–(${maxX},${maxY}): " +
            "${connectorPixels} px abaixo de ${toThreshold}, " +
            "${survivingRegionsTouched} regiões vizinhas"
}

internal data class RasterAlphaStructuralInterval(
    val fromThreshold: Int,
    val toThreshold: Int,
    val holesBefore: Int,
    val holesAfter: Int,
    val splitConnectorCandidates: List<RasterAlphaBridgeCandidate>
) {
    fun summary(): String =
        "${fromThreshold}→${toThreshold}: " +
            "vazados internos ${holesBefore}→${holesAfter}, " +
            "conexões frágeis=${splitConnectorCandidates.size}" +
            (if(splitConnectorCandidates.isEmpty()) "" else "; " +
                splitConnectorCandidates.take(3).joinToString(" | ") {
                    it.summary()
                })
}

internal data class RasterAlphaStructuralAuditReport(
    val source: RasterImagePixels,
    val intervals: List<RasterAlphaStructuralInterval>,
    val exportPermitted: Boolean = false,
    val reviewRequired: Boolean = true
) {
    fun summary(): String =
        "Auditoria espacial alfa QA: " +
            intervals.joinToString("; ") { it.summary() } +
            ". Vazados são cavidades na máscara, não letras " +
            "identificadas. Nenhum STITCH/PES foi autorizado."

    /** Amber = erased band; magenta = erased component touching >1 island. */
    fun preview(fromThreshold:Int,toThreshold:Int): RasterImagePixels {
        val interval=intervals.singleOrNull {
            it.fromThreshold==fromThreshold && it.toThreshold==toThreshold
        } ?: error("Intervalo não verificado na auditoria de alfa.")
        val amber=0xffffa447.toInt()
        val magenta=0xffff45ba.toInt()
        val gray=0xff9ba4ad.toInt()
        val output=IntArray(source.argb.size) { i ->
            val a=source.argb[i] ushr 24
            when {
                a<fromThreshold -> 0
                a<toThreshold -> amber
                else -> gray
            }
        }
        interval.splitConnectorCandidates.forEach { candidate ->
            candidate.indices.forEach { pixel ->
                require(output[pixel]==amber) { "Candidato fora da faixa alfa." }
                output[pixel]=magenta
            }
        }
        return RasterImagePixels(
            source.width,source.height,output,
            "Auditoria de ligações e vazados ${fromThreshold}→${toThreshold}"
        )
    }
}

internal object RasterAlphaStructuralAudit {
    fun inspect(
        source: RasterImagePixels,
        intervals: List<Pair<Int,Int>>
    ): Result<RasterAlphaStructuralAuditReport> = runCatching {
        val w=source.width
        val h=source.height
        val n=w*h
        require(w in 8..512 && h in 8..512 &&
            source.argb.size==n &&
            intervals.size in 1..8 &&
            intervals.distinct().size==intervals.size &&
            intervals.all { (a,b) -> a in 1..254 && b in 2..255 && a<b } &&
            !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Faixas alfa ou imagem fora dos limites da auditoria QA." }
        val alpha=IntArray(n) { source.argb[it] ushr 24 }
        val queue=IntArray(n)

        fun regions(threshold:Int):IntArray {
            val labels=IntArray(n)
            var id=0
            for(start in 0 until n) {
                if(alpha[start]<threshold || labels[start]!=0) continue
                id++
                labels[start]=id
                queue[0]=start
                var head=0;var tail=1
                while(head<tail) {
                    val pos=queue[head++]
                    val x=pos%w;val y=pos/w
                    for(dy in -1..1)for(dx in -1..1) {
                        if(dx==0 && dy==0)continue
                        val xx=x+dx;val yy=y+dy
                        if(xx !in 0 until w || yy !in 0 until h)continue
                        val next=yy*w+xx
                        if(alpha[next]<threshold || labels[next]!=0)continue
                        labels[next]=id
                        queue[tail++]=next
                    }
                }
            }
            return labels
        }

        fun enclosedCounters(threshold:Int):Int {
            val seen=BooleanArray(n)
            var holes=0
            for(start in 0 until n) {
                if(alpha[start]>=threshold || seen[start])continue
                seen[start]=true
                queue[0]=start
                var head=0;var tail=1
                var external=false
                while(head<tail) {
                    val at=queue[head++]
                    val x=at%w;val y=at/w
                    if(x==0 || x==w-1 || y==0 || y==h-1)
                        external=true
                    val offsets=intArrayOf(
                        if(x>0) at-1 else -1,
                        if(x<w-1) at+1 else -1,
                        if(y>0) at-w else -1,
                        if(y<h-1) at+w else -1
                    )
                    for(next in offsets) {
                        if(next<0 || seen[next] || alpha[next]>=threshold)continue
                        seen[next]=true
                        queue[tail++]=next
                    }
                }
                if(!external)holes++
            }
            return holes
        }

        val levels=intervals.flatMap { listOf(it.first,it.second) }.distinct()
        val masks=levels.associateWith { regions(it) }
        val holes=levels.associateWith { enclosedCounters(it) }
        val audits=intervals.map { (from,to) ->
            val lower=masks.getValue(from)
            val upper=masks.getValue(to)
            val seen=BooleanArray(n)
            val bridges=ArrayList<RasterAlphaBridgeCandidate>()
            for(start in 0 until n) {
                if(alpha[start] !in from until to || seen[start])continue
                val parent=lower[start]
                require(parent>0) { "Pixels removidos sem região original." }
                seen[start]=true
                queue[0]=start
                var head=0;var tail=1
                var minX=w;var minY=h;var maxX=0;var maxY=0
                val touched=HashSet<Int>()
                val pixels=ArrayList<Int>()
                while(head<tail) {
                    val at=queue[head++]
                    pixels+=at
                    val x=at%w;val y=at/w
                    minX=minOf(minX,x);minY=minOf(minY,y)
                    maxX=maxOf(maxX,x);maxY=maxOf(maxY,y)
                    for(dy in -1..1)for(dx in -1..1) {
                        if(dx==0 && dy==0)continue
                        val xx=x+dx;val yy=y+dy
                        if(xx !in 0 until w || yy !in 0 until h)continue
                        val next=yy*w+xx
                        val upperId=upper[next]
                        if(upperId>0) {
                            require(lower[next]==parent) {
                                "Conector não pertence a uma única região original."
                            }
                            touched+=upperId
                        }
                        if(alpha[next] !in from until to || seen[next])
                            continue
                        require(lower[next]==parent) {
                            "Pixels de uma mesma faixa mudaram de região."
                        }
                        seen[next]=true
                        queue[tail++]=next
                    }
                }
                if(touched.size>1) {
                    bridges+=RasterAlphaBridgeCandidate(
                        from,to,minX,minY,maxX,maxY,pixels.size,
                        touched.size,pixels
                    )
                }
            }
            RasterAlphaStructuralInterval(
                from,to,holes.getValue(from),holes.getValue(to),
                bridges.sortedWith(
                    compareByDescending<RasterAlphaBridgeCandidate> {
                        it.survivingRegionsTouched
                    }.thenByDescending { it.connectorPixels }
                        .thenBy { it.minY }.thenBy { it.minX }
                )
            )
        }
        RasterAlphaStructuralAuditReport(source,audits)
    }
}
