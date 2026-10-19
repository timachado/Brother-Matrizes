package com.timachado.brothermatrizes.core.embroidery

/**
 * Tracks the SAME pixels and 8-connected foreground components as alpha
 * thresholds increase. A 20->20 island count can conceal a split plus
 * disappearance; global histogram totals cannot reveal this lineage.
 *
 * QA evidence only: a connected component is NOT necessarily one letter,
 * a closed contour is NOT necessarily a valid embroidery counter, and the
 * previews below are NEVER used to create any stitch or PES commands.
 */
internal data class RasterAlphaComponentChange(
    val fromThreshold: Int,
    val toThreshold: Int,
    val parentId: Int,
    val children: Int,
    val sourcePixels: Int,
    val retainedPixels: Int,
    val removedPixels: Int,
    val minX: Int,
    val minY: Int,
    val maxX: Int,
    val maxY: Int
) {
    val disappeared: Boolean get() = children == 0
    val split: Boolean get() = children > 1
    fun hint(): String =
        "Região QA (${minX},${minY})–(${maxX},${maxY}): " +
            if (disappeared)
                "desapareceu ao passar ${fromThreshold}→${toThreshold}"
            else
                "dividiu em ${children} regiões ao passar " +
                    "${fromThreshold}→${toThreshold}"
}

internal data class RasterAlphaTransitionReview(
    val fromThreshold: Int,
    val toThreshold: Int,
    val baseComponents: Int,
    val survivingComponents: Int,
    val pixelsRemoved: Int,
    val parentsSplit: Int,
    val parentsLost: Int,
    val changes: List<RasterAlphaComponentChange>
) {
    val hasStructuralChange: Boolean get() =
        parentsSplit > 0 || parentsLost > 0
    fun summary(): String =
        "${fromThreshold}→${toThreshold}: " +
            "${baseComponents}→${survivingComponents} regiões, " +
            "${pixelsRemoved} px removidos, " +
            "${parentsSplit} divisões e ${parentsLost} desaparecimentos" +
            (if (changes.isNotEmpty())
                "; " + changes.take(4).joinToString(" | ") { it.hint() }
             else "")
}

internal data class RasterAlphaComponentLineageReport(
    val source: RasterImagePixels,
    val transitions: List<RasterAlphaTransitionReview>,
    val exportPermitted: Boolean = false,
    val reviewRequired: Boolean = true
) {
    fun summary(): String =
        "Evolução dos componentes QA (comparação espacial da foto): " +
            transitions.filter {
                it.fromThreshold >= 160 && it.toThreshold <= 240
            }.joinToString("; ") { it.summary() } +
            ". Variação no número total de regiões não prova, sozinha, " +
            "que uma letra foi cortada. Não são pontos de bordado nem PES."

    /**
     * Two-tone pixel LOSS overlay relative to a lower working threshold:
     * gray = retained by both, amber = lost between thresholds. It is
     * explicitly a visualization rather than a mask for digitization.
     */
    fun lossPreview(fromThreshold: Int, toThreshold: Int): RasterImagePixels {
        require(transitions.any {
            it.fromThreshold == fromThreshold && it.toThreshold == toThreshold
        }) { "Intervalo de alfa não analisado na imagem QA." }
        val gray=0xff979fa8.toInt()
        val amber=0xffffa447.toInt()
        val output=IntArray(source.argb.size) { index ->
            val alpha=source.argb[index] ushr 24
            when {
                alpha < fromThreshold -> 0
                alpha < toThreshold -> amber
                else -> gray
            }
        }
        return RasterImagePixels(
            source.width,source.height,output,
            "Diferença de alfa ${fromThreshold}–${toThreshold} (QA)"
        )
    }
}

internal object RasterAlphaComponentLineage {
    private data class Labeled(
        val labels: IntArray,
        val componentCount: Int,
        val componentSizes: IntArray,
        val minX: IntArray,
        val minY: IntArray,
        val maxX: IntArray,
        val maxY: IntArray
    )

    fun inspect(
        source: RasterImagePixels,
        levels: List<Int>
    ): Result<RasterAlphaComponentLineageReport> = runCatching {
        val w=source.width
        val h=source.height
        require(w in 8..512 && h in 8..512 &&
            source.argb.size == w*h &&
            levels.size in 2..10 &&
            levels.all { it in 1..255 } &&
            levels.zipWithNext().all { (a,b) -> a<b } &&
            !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Imagem ou escala alfa fora do diagnóstico QA." }
        val alpha=IntArray(w*h) { source.argb[it] ushr 24 }
        val queue=IntArray(w*h)

        fun label(level:Int):Labeled {
            val labels=IntArray(w*h)
            val sizes=ArrayList<Int>()
            val minsX=ArrayList<Int>()
            val minsY=ArrayList<Int>()
            val maxsX=ArrayList<Int>()
            val maxsY=ArrayList<Int>()
            sizes+=0;minsX+=0;minsY+=0;maxsX+=0;maxsY+=0
            var count=0
            for(root in alpha.indices) {
                if(alpha[root]<level || labels[root]!=0) continue
                count++
                labels[root]=count
                queue[0]=root
                var head=0
                var tail=1
                var left=w;var top=h;var right=0;var bottom=0
                while(head<tail) {
                    val pos=queue[head++]
                    val x=pos%w
                    val y=pos/w
                    left=minOf(left,x);top=minOf(top,y)
                    right=maxOf(right,x);bottom=maxOf(bottom,y)
                    for(dy in -1..1) for(dx in -1..1) {
                        if(dx==0 && dy==0)continue
                        val xx=x+dx
                        val yy=y+dy
                        if(xx !in 0 until w || yy !in 0 until h)continue
                        val p=yy*w+xx
                        if(alpha[p]<level || labels[p]!=0)continue
                        labels[p]=count
                        queue[tail++]=p
                    }
                }
                sizes+=tail
                minsX+=left;minsY+=top
                maxsX+=right;maxsY+=bottom
            }
            return Labeled(
                labels,count,sizes.toIntArray(),
                minsX.toIntArray(),minsY.toIntArray(),
                maxsX.toIntArray(),maxsY.toIntArray()
            )
        }

        var current=label(levels.first())
        val reviews=ArrayList<RasterAlphaTransitionReview>()
        for(i in 1 until levels.size) {
            val next=label(levels[i])
            val children=Array(current.componentCount+1) { HashSet<Int>() }
            val retained=IntArray(current.componentCount+1)
            for(pos in alpha.indices) {
                val parent=current.labels[pos]
                if(parent==0)continue
                val child=next.labels[pos]
                if(child!=0) {
                    children[parent].add(child)
                    retained[parent]++
                }
            }
            val changes=ArrayList<RasterAlphaComponentChange>()
            var splits=0
            var lost=0
            for(parent in 1..current.componentCount) {
                val count=children[parent].size
                if(count>1)splits++
                if(count==0)lost++
                if(count!=1) changes+=RasterAlphaComponentChange(
                    fromThreshold=levels[i-1],toThreshold=levels[i],
                    parentId=parent,children=count,
                    sourcePixels=current.componentSizes[parent],
                    retainedPixels=retained[parent],
                    removedPixels=current.componentSizes[parent]-retained[parent],
                    minX=current.minX[parent],minY=current.minY[parent],
                    maxX=current.maxX[parent],maxY=current.maxY[parent]
                )
            }
            val removed=current.componentSizes.sum()-next.componentSizes.sum()
            require(removed>=0 && removed ==
                current.componentSizes.indices.sumOf { p ->
                    current.componentSizes[p]-retained[p]
                }
            ) { "A linha de componentes perdeu contagens de alfa." }
            reviews+=RasterAlphaTransitionReview(
                fromThreshold=levels[i-1],toThreshold=levels[i],
                baseComponents=current.componentCount,
                survivingComponents=next.componentCount,
                pixelsRemoved=removed,
                parentsSplit=splits,parentsLost=lost,
                changes=changes.sortedWith(
                    compareByDescending<RasterAlphaComponentChange> {
                        it.sourcePixels
                    }.thenBy { it.minY }.thenBy { it.minX }
                )
            )
            current=next
        }
        RasterAlphaComponentLineageReport(source,reviews)
    }
}
