package com.timachado.brothermatrizes.core.embroidery

/**
 * Source-bound, READ-ONLY candidate for protecting a previously enclosed
 * raster counter (hole) against alpha-threshold erosion. Every highlighted
 * pixel existed in the original imported image. This candidate is NOT a
 * new bitmap for digitization and may NOT be sent to any STITCH/PES engine.
 *
 * A single witness path may fail to close a hole when alternate routes to
 * the exterior exist. A failure to close is reported, never masked.
 */
internal data class RasterQaCounterClosureProposal(
    val source: RasterImagePixels,
    val fromThreshold: Int,
    val toThreshold: Int,
    val sourceHoles: Int,
    val cutoffHoles: Int,
    val candidateHoles: Int,
    val cutoffRegions: Int,
    val candidateRegions: Int,
    val candidatePixels: List<Int>,
    val exportPermitted: Boolean = false,
    val requiresManualReview: Boolean = true
) {
    val holeRestored: Boolean get() =
        sourceHoles > cutoffHoles && candidateHoles == sourceHoles
    val regionTopologyPreserved: Boolean get() =
        candidateRegions == cutoffRegions
    val candidateForVisualReview: Boolean get() =
        holeRestored && regionTopologyPreserved

    fun summary(): String =
        "Proteção localizada QA ${fromThreshold}→${toThreshold}: " +
            "vazados ${sourceHoles}→${cutoffHoles}→${candidateHoles}; " +
            "regiões após corte/candidata ${cutoffRegions}/${candidateRegions}; " +
            "${candidatePixels.size} px originais reaproveitados na prévia. " +
            (if (candidateForVisualReview)
                "Topologia global preservada nesta máscara candidata; " +
                    "inspeção visual e verificação por letra ainda obrigatórias."
             else
                "A rota isolada não comprovou recuperação segura: " +
                    "não utilizar como máscara de digitalização.") +
            " Nenhum STITCH/PES foi criado nem autorizado."

    fun preview(): RasterImagePixels {
        val highlight=0xff66db87.toInt()
        val stable=0xff939da7.toInt()
        val output=IntArray(source.argb.size) { index ->
            if ((source.argb[index] ushr 24) >= toThreshold) stable else 0
        }
        for(index in candidatePixels) {
            require(index in output.indices &&
                (source.argb[index] ushr 24) in fromThreshold until toThreshold
            ) { "Pixel de proteção QA não pertence à origem." }
            output[index]=highlight
        }
        return RasterImagePixels(
            source.width,source.height,output,
            "Máscara candidata QA: verde=faixa original restaurada"
        )
    }
}

internal object RasterQaCounterClosureStudy {
    /**
     * Analyse the existing opening witnesses only. Nothing is written
     * back to the source and the embroidery engine never sees the mask.
     */
    fun prepare(
        study: RasterAlphaThresholdSweepReport,
        fromThreshold: Int = 180,
        toThreshold: Int = 208
    ): Result<RasterQaCounterClosureProposal> = runCatching {
        val image=study.source
        val w=image.width
        val h=image.height
        require(w in 8..512 && h in 8..512 &&
            image.argb.size==w*h &&
            study.requiresOperatorReview && !study.exportPermitted &&
            !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Imagem ou aprovação inadequada para proteção QA." }
        val structural=study.structuralAudit ?:
            error("Auditoria de vazados incompleta.")
        val leaks=study.counterLeakEvidence ?:
            error("Rota de abertura dos vazados ausente.")
        require(structural.source === image && leaks.source === image) {
            "Auditorias se referem a outra imagem."
        }
        val interval=structural.intervals.singleOrNull {
            it.fromThreshold==fromThreshold && it.toThreshold==toThreshold
        } ?: error("Faixa alfa não auditada.")
        val detected=leaks.intervals.singleOrNull {
            it.fromThreshold==fromThreshold && it.toThreshold==toThreshold
        } ?: error("Abertura alfa não analisada nesta faixa.")
        require(interval.holesBefore>interval.holesAfter &&
            detected.openedCounters.isNotEmpty()
        ) { "Não há vazado com rota de abertura comprovada nesse intervalo." }

        val n=w*h
        val alpha=IntArray(n) { image.argb[it] ushr 24 }
        val candidate=BooleanArray(n) { alpha[it]>=toThreshold }
        val seen=BooleanArray(n)
        val restore=ArrayList<Int>()
        for(counter in detected.openedCounters) {
            require(counter.fromThreshold==fromThreshold &&
                counter.toThreshold==toThreshold &&
                counter.openingPixels.isNotEmpty()
            ) { "Rota do vazado incompatível." }
            for(index in counter.openingPixels) {
                require(index in 0 until n &&
                    alpha[index] in fromThreshold until toThreshold
                ) { "A rota QA não corresponde aos pixels da fonte." }
                if(!seen[index]) {
                    seen[index]=true
                    restore+=index
                    candidate[index]=true
                }
            }
        }
        require(restore.size in 1..2048) {
            "Faixa candidata extensa ou vazia: revisão manual obrigatória."
        }

        val queue=IntArray(n)
        fun holes(mask: BooleanArray):Int {
            val visited=BooleanArray(n)
            var holeCount=0
            for(start in 0 until n) {
                if(mask[start] || visited[start])continue
                visited[start]=true
                queue[0]=start
                var head=0
                var end=1
                var touchesEdge=false
                while(head<end) {
                    val pos=queue[head++]
                    val x=pos%w
                    val y=pos/w
                    if(x==0 || y==0 || x==w-1 || y==h-1)
                        touchesEdge=true
                    val neighbors=intArrayOf(
                        if(x>0) pos-1 else -1,
                        if(x<w-1) pos+1 else -1,
                        if(y>0) pos-w else -1,
                        if(y<h-1) pos+w else -1
                    )
                    for(next in neighbors) {
                        if(next<0 || mask[next] || visited[next])continue
                        visited[next]=true
                        queue[end++]=next
                    }
                }
                if(!touchesEdge)holeCount++
            }
            return holeCount
        }
        fun regions(mask:BooleanArray):Int {
            val visited=BooleanArray(n)
            var count=0
            for(start in 0 until n) {
                if(!mask[start] || visited[start])continue
                count++
                visited[start]=true
                queue[0]=start
                var head=0
                var end=1
                while(head<end) {
                    val pos=queue[head++]
                    val x=pos%w
                    val y=pos/w
                    for(dy in -1..1)for(dx in -1..1) {
                        if(dx==0 && dy==0)continue
                        val xx=x+dx
                        val yy=y+dy
                        if(xx !in 0 until w || yy !in 0 until h)continue
                        val next=yy*w+xx
                        if(!mask[next] || visited[next])continue
                        visited[next]=true
                        queue[end++]=next
                    }
                }
            }
            return count
        }
        val from=BooleanArray(n) { alpha[it]>=fromThreshold }
        val upper=BooleanArray(n) { alpha[it]>=toThreshold }
        val sourceHoles=holes(from)
        val cutoffHoles=holes(upper)
        require(sourceHoles==interval.holesBefore &&
            cutoffHoles==interval.holesAfter
        ) { "Contagens de vazados divergem do relatório original." }
        val cutoffRegions=regions(upper)
        val candidateRegions=regions(candidate)
        val candidateHoles=holes(candidate)
        RasterQaCounterClosureProposal(
            source=image,fromThreshold=fromThreshold,toThreshold=toThreshold,
            sourceHoles=sourceHoles,cutoffHoles=cutoffHoles,
            candidateHoles=candidateHoles,
            cutoffRegions=cutoffRegions,candidateRegions=candidateRegions,
            candidatePixels=restore.sorted()
        )
    }
}
