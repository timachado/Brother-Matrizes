package com.timachado.brothermatrizes.core.embroidery

/**
 * Non-destructive analysis of the actual alpha channels on a raster imported
 * through Android. This is a series of QA silhouettes, NOT automatic thread
 * digitization or approval. An ordinary logo may have almost no alpha 255.
 */
internal data class RasterThresholdSlice(
    val threshold: Int,
    val inkPixels: Int,
    val components: Int,
    val tinyComponents: Int
)

internal data class RasterAlphaThresholdSweepReport(
    val source: RasterImagePixels,
    val visiblePixels: Int,
    val fullyOpaquePixels: Int,
    val slices: List<RasterThresholdSlice>,
    val componentLineage: RasterAlphaComponentLineageReport? = null,
    val structuralAudit: RasterAlphaStructuralAuditReport? = null,
    val exportPermitted: Boolean = false,
    val requiresOperatorReview: Boolean = true
) {
    val alpha255AnchorReliable: Boolean get() =
        visiblePixels > 0 &&
            fullyOpaquePixels >= 64 &&
            fullyOpaquePixels.toDouble() / visiblePixels >= .05

    fun summary(): String {
        val profiles = slices.joinToString("; ") {
            "${it.threshold}: ${it.inkPixels} px, " +
                "${it.components} regiões (${it.tinyComponents} pequenas)"
        }
        return "QA de limiares alfa — ${source.width}x${source.height}; " +
            "pixels visíveis=${visiblePixels}; opacos=${fullyOpaquePixels}. " +
            "Limiar → pixels/regiões: " + profiles + ". " +
            (if (alpha255AnchorReliable)
                "Núcleos opacos suficientes para uma comparação auxiliar."
             else
                "Poucos núcleos alfa 255: ligações entre eles não " +
                    "comprovam união indevida de letras.") +
            " Prévia apenas para comparar a silhueta e os vazados. " +
            (componentLineage?.let { " " + it.summary() } ?: "") +
            (structuralAudit?.let { " " + it.summary() } ?: "") +
            " Nenhum ponto de máquina ou PES foi criado."
    }

    /**
     * Overlay only. Gray means pixels retained by the selected alpha
     * threshold; amber marks the weaker rim near this threshold.
     * Never pass it to any embroidery stitch generator.
     */
    fun silhouettePreview(threshold: Int): RasterImagePixels {
        require(slices.any { it.threshold == threshold })
        val amber = 0xffffa447.toInt()
        val gray = 0xff989fa7.toInt()
        val result = IntArray(source.argb.size) { i ->
            val alpha = source.argb[i] ushr 24
            when {
                alpha < threshold -> 0
                alpha < minOf(255, threshold+24) -> amber
                else -> gray
            }
        }
        return RasterImagePixels(
            source.width,source.height,result,
            "Prévia QA limiar alfa ${threshold} — cinza sólido, âmbar borda"
        )
    }
}

internal object RasterAlphaThresholdSweep {
    private val LEVELS = listOf(64,96,128,160,180,208,224,240,255)

    fun inspect(source: RasterImagePixels): Result<RasterAlphaThresholdSweepReport> =
        runCatching {
            val w=source.width
            val h=source.height
            require(w in 8..512 && h in 8..512 &&
                source.argb.size == w*h &&
                !RasterEmbroideryApproval.canExportFromImageTool()
            ) { "Imagem fora dos limites do estudo dos limiares alfa QA." }
            val alpha=IntArray(w*h) { source.argb[it] ushr 24 }
            val totalVisible=alpha.count { it>0 }
            val opaque=alpha.count { it==255 }
            require(totalVisible>=24) { "Arte sem tinta visível suficiente." }
            val queue=IntArray(w*h)
            val sections=ArrayList<RasterThresholdSlice>()
            for(level in LEVELS) {
                val seen=BooleanArray(w*h)
                var components=0
                var small=0
                var ink=0
                for(root in alpha.indices) {
                    if(alpha[root]<level) continue
                    ink++
                    if(seen[root])continue
                    seen[root]=true
                    components++
                    queue[0]=root
                    var first=0
                    var end=1
                    while(first<end) {
                        val at=queue[first++]
                        val x=at%w
                        val y=at/w
                        for(dy in -1..1) for(dx in -1..1) {
                            if(dx==0 && dy==0)continue
                            val xx=x+dx
                            val yy=y+dy
                            if(xx !in 0 until w || yy !in 0 until h)continue
                            val next=yy*w+xx
                            if(alpha[next]<level || seen[next])continue
                            seen[next]=true
                            queue[end++]=next
                        }
                    }
                    if(end<15) small++
                }
                sections+=RasterThresholdSlice(level,ink,components,small)
            }
            require(sections.zipWithNext().all { (a,b) ->
                a.inkPixels>=b.inkPixels
            } && sections.last().inkPixels==opaque
            ) { "As contagens de pixels dos limiares se contradizem." }
            val lineage=RasterAlphaComponentLineage.inspect(
                source,LEVELS
            ).getOrThrow()
            require(lineage.transitions.size==LEVELS.size-1 &&
                lineage.transitions.indices.all { i ->
                    lineage.transitions[i].baseComponents ==
                        sections[i].components &&
                    lineage.transitions[i].survivingComponents ==
                        sections[i+1].components &&
                    lineage.transitions[i].pixelsRemoved ==
                        sections[i].inkPixels-sections[i+1].inkPixels
                }
            ) { "Contagens e continuidade das regiões não coincidem." }
            val structural=RasterAlphaStructuralAudit.inspect(
                source,listOf(
                    160 to 180,180 to 208,208 to 224,224 to 240
                )
            ).getOrThrow()
            require(structural.intervals.all { interval ->
                val match=lineage.transitions.single {
                    it.fromThreshold==interval.fromThreshold &&
                        it.toThreshold==interval.toThreshold
                }
                // A weak-alpha connector can only reach two strong-alpha
                // child regions when the lower-level parent actually splits.
                (match.parentsSplit>0 ||
                    interval.splitConnectorCandidates.isEmpty()) &&
                    interval.holesBefore>=0 && interval.holesAfter>=0
            }) { "Conexões frágeis divergentes da topologia original." }
            RasterAlphaThresholdSweepReport(
                source=source,visiblePixels=totalVisible,
                fullyOpaquePixels=opaque,slices=sections,
                componentLineage=lineage,structuralAudit=structural
            )
        }
}
