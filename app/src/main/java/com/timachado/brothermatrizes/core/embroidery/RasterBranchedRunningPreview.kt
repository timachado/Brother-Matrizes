package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * QA-only needle path for a thin SINGLE three-way junction (T/Y motif).
 *
 * Strict 4-neighbor tree: exactly three degree-1 endpoints, one degree-3
 * junction, every other pixel degree 2. The three arms are split at that
 * junction and never merged by a straight STITCH across an uninked corner.
 * The two longest arms form one continuous sewn section; the third gets a
 * separate TRIM + JUMP before sewing it. The source must be 1 pixel wide.
 *
 * Not a skeletonizer and NOT a general typeface/embroidery digitizer:
 * thicker letters, loops, diagonal-only joins, multiple branches, crosses
 * or ambiguous self-touch must be reviewed manually.
 * Does not generate PES bytes or certify fabric/needle behavior.
 */
internal object RasterBranchedRunningPreview {
    private const val MIN_PITCH_MM = .24
    private const val MAX_PITCH_MM = 2.6
    private const val TARGET_PITCH_MM = 1.5
    private const val MAX_PIXELS = 2_500

    fun create(
        image: RasterImagePixels,
        part: RasterSatinObject,
        fullWidthMm: Float,
        hoop: HoopProfile
    ): Result<EmbroideryDesign> = runCatching {
        require(image.width in 8..512 && image.height in 8..512 &&
            part.mask.pixels.size == image.argb.size &&
            image.argb.size == image.width * image.height &&
            fullWidthMm.isFinite() && fullWidthMm in 10f..200f
        ) { "Imagem inválida para ponto corrido ramificado." }
        val fullHeightMm = fullWidthMm * image.height / image.width
        require(fullWidthMm <= hoop.usableWidthMm &&
            fullHeightMm <= hoop.usableHeightMm
        ) { "A arte não cabe na área útil do bastidor." }
        val pixelMm = fullWidthMm.toDouble()/image.width
        require(pixelMm in .25..1.0) {
            "Escala inadequada para agulhadas nos contornos."
        }
        val mask=part.mask
        val w=mask.maxX-mask.minX+1
        val h=mask.maxY-mask.minY+1
        require(w>=8 && h>=8 && part.pixelCount in 30..MAX_PIXELS &&
            part.pixelCount < w*h*.45
        ) { "Somente um traço ramificado fino em QA." }
        val ink=HashSet<Int>()
        for(y in mask.minY..mask.maxY) for(x in mask.minX..mask.maxX) {
            val index=y*image.width+x
            if(mask.pixels[index]==0)ink+=index
        }
        require(ink.size==part.pixelCount)

        fun neighbours(p:Int):List<Int> {
            val x=p%image.width
            val y=p/image.width
            return buildList {
                if(x>0 && p-1 in ink)add(p-1)
                if(x<image.width-1 && p+1 in ink)add(p+1)
                if(y>0 && p-image.width in ink)add(p-image.width)
                if(y<image.height-1 && p+image.width in ink)add(p+image.width)
            }
        }
        val ends=ink.filter { neighbours(it).size==1 }.sorted()
        val junctions=ink.filter { neighbours(it).size==3 }
        require(ends.size==3 && junctions.size==1 &&
            ink.all { neighbours(it).size in 1..3 }
        ) {
            "Contorno com ciclos, espessura, ilhas ou várias bifurcações."
        }
        val junction=junctions.single()
        val arms=ArrayList<List<Int>>(3)
        val all=HashSet<Int>()
        all+=junction
        for (first in neighbours(junction)) {
            val path=ArrayList<Int>()
            path+=junction
            var before=junction
            var cursor=first
            while (true) {
                require(cursor !in all && path.size<=ink.size) {
                    "Caminhos em ciclo ou duplicação de pontos."
                }
                path+=cursor
                all+=cursor
                val options=neighbours(cursor).filter { it!=before }
                if(options.isEmpty())break
                require(options.size==1) {
                    "Ramificação secundária não permitida."
                }
                before=cursor
                cursor=options.single()
            }
            require(path.last() in ends && path.size>=9 &&
                (path.size-1)*pixelMm>=3.0
            ) {
                "Uma das três hastes é curta demais para ponto corrido seguro."
            }
            arms+=path
        }
        require(arms.size==3 && all.size==ink.size &&
            arms.map { it.last() }.toSet()==ends.toSet()
        ) {
            "A divisão em três caminhos não cobriu toda a tinta."
        }

        // Check distant diagonal contacts. An ordinary pair meeting at
        // the junction shares a four-neighbor pivot, so it is acceptable.
        // A nonlocal diagonal connection could represent a crossing or
        // two outlines touching without an unambiguous seam.
        val owner=HashMap<Int,Pair<Int,Int>>(ink.size)
        for((armIndex,path) in arms.withIndex())
            for((index,p) in path.withIndex()) {
                if(index>0)owner[p]=armIndex to index
            }
        for (p in ink) {
            val x=p%image.width
            val y=p/image.width
            for(dx in listOf(-1,1)) for(dy in listOf(-1,1)) {
                val nx=x+dx;val ny=y+dy
                if(nx !in 0 until image.width ||
                    ny !in 0 until image.height)continue
                val other=ny*image.width+nx
                if(other !in ink)continue
                val a=owner[p]
                val b=owner[other]
                if(a!=null && b!=null) {
                    require(
                        (a.first==b.first &&
                            kotlin.math.abs(a.second-b.second)<=2) ||
                        (a.first!=b.first && a.second<=1 && b.second<=1)
                    ) {
                        "Autocontato diagonal entre hastes: revisar cruzamento."
                    }
                }
            }
        }

        val longest=arms.indices.sortedWith(
            compareByDescending<Int> { arms[it].size }.thenBy { it }
        )
        val first=arms[longest[0]]
        val second=arms[longest[1]]
        val third=arms[longest[2]]
        val main=first.asReversed()+second.drop(1)

        data class P(val x:Double,val y:Double)
        fun point(pixel:Int):P=P(
            pixel%image.width+.5,pixel/image.width+.5
        )
        fun anchors(path:List<Int>):List<P> {
            val selected=ArrayList<P>()
            selected+=point(path.first())
            var anchorPixel=path.first()
            for(i in 1 until path.size) {
                val before=if(i>0)path[i]-path[i-1] else 0
                val after=if(i<path.lastIndex)path[i+1]-path[i] else before
                val turning=before!=after || path[i]==junction
                val a=point(anchorPixel)
                val b=point(path[i])
                val d=hypot(a.x-b.x,a.y-b.y)*pixelMm
                if(turning || d>=TARGET_PITCH_MM || i==path.lastIndex) {
                    if(d<MIN_PITCH_MM && i!=path.lastIndex)continue
                    require(d in MIN_PITCH_MM..MAX_PITCH_MM) {
                        "Ponto corrido com passo fora dos limites de QA."
                    }
                    selected+=b
                    anchorPixel=path[i]
                }
            }
            require(selected.size>=3)
            return selected
        }

        val one=anchors(main)
        val spur=anchors(third)
        val units=pixelMm*10.0
        fun command(p:P,type:StitchCommand)=EmbroideryPoint(
            ((p.x-image.width/2.0)*units).roundToInt(),
            ((image.height/2.0-p.y)*units).roundToInt(),
            type,0
        )
        val output=ArrayList<EmbroideryPoint>(one.size+spur.size+4)
        output+=command(one.first(),StitchCommand.JUMP)
        for(p in one.drop(1))output+=command(p,StitchCommand.STITCH)
        val previous=output.last()
        output+=previous.copy(command=StitchCommand.TRIM)
        output+=command(spur.first(),StitchCommand.JUMP)
        for(p in spur.drop(1))output+=command(p,StitchCommand.STITCH)
        output+=output.last().copy(command=StitchCommand.END)
        val positions=output.filter {
            it.command==StitchCommand.JUMP ||
                it.command==StitchCommand.STITCH
        }
        val bounds=EmbroideryBounds(
            positions.minOf { it.xUnits },positions.maxOf { it.xUnits },
            positions.minOf { it.yUnits },positions.maxOf { it.yUnits }
        )
        val draft=EmbroideryDesign(
            fileName="RAMIFICACAO-CORRIDO-QA.qa",
            format="QA_RUNNING_REVIEW",
            label="Ponto corrido ramificado em duas seções, somente QA",
            points=output,bounds=bounds,
            stitchCount=output.count { it.command==StitchCommand.STITCH },
            jumpCount=output.count { it.command==StitchCommand.JUMP },
            colorChanges=0,endFound=true,
            sourceBytes=ByteArray(0),threadColors=listOf(part.color),
            isModified=true,hoopProfile=hoop
        )
        // Independent post-rounding verification of every sewn STITCH. It
        // never mistakes the jump between branches for needle penetration.
        RasterSewnMaskGate.inspect(image,part,draft,fullWidthMm).getOrThrow()
        RasterNeedleRiskAudit.inspect(draft,hoop).getOrThrow()
        draft
    }
}
