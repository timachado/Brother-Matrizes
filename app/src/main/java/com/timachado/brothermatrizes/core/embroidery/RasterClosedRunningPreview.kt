package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * QA-only running-stitch generator for a SINGLE closed, 1px-wide contour
 * (outline O, ring or ornament), NOT a filled letter. A connected
 * 4-neighbour graph must be an unbranched cycle, every vertex degree 2.
 *
 * The last needle-down STITCH closes the ring by following the contour
 * back to the original JUMP coordinate. No chord through the empty centre.
 *
 * Thick letter strokes, counters, antialiasing, branching, self-contact
 * and multiple loops remain unhandled. No PES export or physical approval.
 */
internal object RasterClosedRunningPreview {
    private const val MAX_PIXELS = 2500
    private const val TARGET_STITCH_MM = 1.55
    private const val MIN_STITCH_MM = .24
    private const val MAX_STITCH_MM = 2.60

    fun create(
        image: RasterImagePixels,
        part: RasterSatinObject,
        fullWidthMm: Float,
        hoop: HoopProfile
    ): Result<EmbroideryDesign> = runCatching {
        require(image.width in 8..512 && image.height in 8..512 &&
            image.argb.size == image.width * image.height &&
            part.mask.pixels.size == image.argb.size &&
            fullWidthMm.isFinite() && fullWidthMm in 10f..200f
        ) { "Imagem inválida para contorno circular QA." }
        val fullHeightMm = fullWidthMm * image.height / image.width
        require(fullWidthMm <= hoop.usableWidthMm &&
            fullHeightMm <= hoop.usableHeightMm
        ) { "Contorno fora da área útil do bastidor." }
        val pixelMm = fullWidthMm.toDouble()/image.width
        require(pixelMm in .25..1.0) {
            "Escala incompatível com o passo do contorno."
        }
        val mask = part.mask
        val w = mask.maxX-mask.minX+1
        val h = mask.maxY-mask.minY+1
        require(w>=8 && h>=8 &&
            part.pixelCount in 32..MAX_PIXELS &&
            part.pixelCount < .40*w*h
        ) {
            "Somente uma linha fina fechada, sem preenchimento ou texto grosso."
        }
        val ink = HashSet<Int>(part.pixelCount)
        for(y in mask.minY..mask.maxY) for(x in mask.minX..mask.maxX) {
            val cell=y*image.width+x
            if(mask.pixels[cell]==0)ink+=cell
        }
        require(ink.size==part.pixelCount)
        fun neighbors(p:Int):List<Int> = buildList {
            val x=p%image.width
            val y=p/image.width
            if(x>0 && p-1 in ink)add(p-1)
            if(x<image.width-1 && p+1 in ink)add(p+1)
            if(y>0 && p-image.width in ink)add(p-image.width)
            if(y<image.height-1 && p+image.width in ink)add(p+image.width)
        }
        require(ink.all { neighbors(it).size==2 }) {
            "Contorno possui ponta solta, bifurcação, traço grosso ou cruzamento."
        }

        // One connected, non-self-intersecting cycle only.
        val start = ink.minOrNull() ?: error("Contorno vazio.")
        val route = ArrayList<Int>(ink.size+1)
        val visited = HashSet<Int>()
        var prior=-1
        var current=start
        while(true) {
            require(route.size<=ink.size) { "Ciclo excedeu comprimento previsto." }
            route+=current
            visited+=current
            val next=neighbors(current).filter { it!=prior }
            require(next.size in 1..2)
            val chosen=if(prior<0) next.minOrNull()!! else next.single()
            if(chosen==start)break
            require(chosen !in visited) {
                "Contorno com ciclo secundário ou autocontato."
            }
            prior=current
            current=chosen
        }
        require(route.size==ink.size && visited.size==ink.size) {
            "A máscara possui mais de um anel ou não foi percorrida por completo."
        }
        val pathIndex=HashMap<Int,Int>(route.size)
        route.forEachIndexed { i,p->pathIndex[p]=i }
        // A diagonal touching a distant section is ambiguous: the renderer
        // cannot infer intended underpasses or pull compensation.
        for((i,p) in route.withIndex()) {
            val x=p%image.width
            val y=p/image.width
            for(dx in listOf(-1,1)) for(dy in listOf(-1,1)) {
                val xx=x+dx;val yy=y+dy
                if(xx !in 0 until image.width ||
                    yy !in 0 until image.height)continue
                val index=pathIndex[yy*image.width+xx] ?: continue
                val linear=abs(index-i)
                require(minOf(linear,route.size-linear)<=2) {
                    "Contorno fechado com autocontato diagonal distante."
                }
            }
        }
        data class P(val x:Double,val y:Double)
        fun p(cell:Int):P=P(cell%image.width+.5,cell/image.width+.5)
        val anchors=ArrayList<P>()
        anchors+=p(start)
        var lastAnchor=start
        // Add the original start as the final pixel. This produces an
        // actual closing STITCH (not a zero-length END).
        val circular=route+start
        for(i in 1 until circular.size) {
            val incoming=circular[i]-circular[i-1]
            val next=if(i<circular.lastIndex)
                circular[i+1]-circular[i] else circular[1]-circular[0]
            val corner=incoming!=next
            val before=p(lastAnchor)
            val now=p(circular[i])
            val step=hypot(now.x-before.x,now.y-before.y)*pixelMm
            if(corner || step>=TARGET_STITCH_MM ||
                i==circular.lastIndex) {
                require(step in MIN_STITCH_MM..MAX_STITCH_MM) {
                    "Pontada do contorno fechado muito curta ou comprida."
                }
                anchors+=now
                lastAnchor=circular[i]
            }
        }
        require(anchors.size>=5 && anchors.size<=MAX_PIXELS+1 &&
            anchors.last()==anchors.first()
        )
        val units=pixelMm*10.0
        fun cmd(point:P,command:StitchCommand):EmbroideryPoint =
            EmbroideryPoint(
                ((point.x-image.width/2.0)*units).roundToInt(),
                ((image.height/2.0-point.y)*units).roundToInt(),
                command,0
            )
        val commands=ArrayList<EmbroideryPoint>(anchors.size+1)
        commands+=cmd(anchors.first(),StitchCommand.JUMP)
        for(point in anchors.drop(1))
            commands+=cmd(point,StitchCommand.STITCH)
        commands+=commands.last().copy(command=StitchCommand.END)
        val sewn=commands.dropLast(1)
        val bounds=EmbroideryBounds(
            sewn.minOf { it.xUnits },sewn.maxOf { it.xUnits },
            sewn.minOf { it.yUnits },sewn.maxOf { it.yUnits }
        )
        val result=EmbroideryDesign(
            fileName="CONTORNO-FECHADO-REVISAO.qa",
            format="QA_RUNNING_REVIEW",
            label="Contorno fino fechado, apenas simulação",
            points=commands,bounds=bounds,
            stitchCount=commands.count { it.command==StitchCommand.STITCH },
            jumpCount=1,colorChanges=0,endFound=true,
            sourceBytes=ByteArray(0),threadColors=listOf(part.color),
            isModified=true,hoopProfile=hoop
        )
        RasterSewnMaskGate.inspect(image,part,result,fullWidthMm).getOrThrow()
        RasterNeedleRiskAudit.inspect(result,hoop).getOrThrow()
        result
    }
}
