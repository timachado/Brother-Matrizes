package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A *conservative* QA running-stitch path for a one-pixel-wide, open curve
 * with a single four-connected route. Unlike the earlier monotonic
 * RasterRunningOutlinePreview, this handles U-turns and non-monotonic
 * details, but NOT normal letters with thick strokes, loops, junctions,
 * overlapping branches or anti-aliased multi-pixel lines.
 *
 * Graph topology must have precisely two endpoints and no branches or
 * cycles. The actual rounded needle-down segments must remain on source
 * ink. Unsupported art fails closed; QA_RUNNING_REVIEW cannot export PES.
 *
 * Stitch spacing is a TEST heuristic, not a published Brother machine
 * safety limit or physical stitch-out certification.
 */
internal object RasterCurvedRunningPreview {
    private const val MAX_PATH_PIXELS = 2_500
    private const val TARGET_STITCH_MM = 1.5
    private const val MIN_STITCH_MM = .24
    private const val MAX_STITCH_MM = 2.6

    fun create(
        source: RasterImagePixels,
        part: RasterSatinObject,
        fullWidthMm: Float,
        hoop: HoopProfile
    ): Result<EmbroideryDesign> = runCatching {
        require(source.width in 8..512 && source.height in 8..512 &&
            source.argb.size == source.width * source.height &&
            part.mask.pixels.size == source.argb.size &&
            fullWidthMm.isFinite() && fullWidthMm in 10f..200f
        ) { "Imagem incompatível com ponto corrido curvo." }
        val fullHeightMm = fullWidthMm * source.height / source.width
        require(fullWidthMm <= hoop.usableWidthMm &&
            fullHeightMm <= hoop.usableHeightMm
        ) { "Área completa ultrapassa o bastidor útil." }
        val pixelMm = fullWidthMm.toDouble() / source.width
        require(pixelMm in .25..1.0) {
            "Escala insuficiente para o contorno curvo sem pontos muito curtos."
        }
        val m = part.mask
        val w = m.maxX-m.minX+1
        val h = m.maxY-m.minY+1
        require(w>=5 && h>=5 && part.pixelCount in 20..MAX_PATH_PIXELS &&
            part.pixelCount < w*h*.4
        ) {
            "Esta rotina aceita somente contornos finos abertos, não preenchimentos."
        }
        val points = ArrayList<Int>(part.pixelCount)
        for (y in m.minY..m.maxY) for (x in m.minX..m.maxX) {
            val index = y*source.width+x
            if (m.pixels[index]==0) points+=index
        }
        require(points.size == part.pixelCount)
        val ink = points.toHashSet()
        fun neighbours(pixel: Int): List<Int> {
            val x=pixel%source.width
            val y=pixel/source.width
            val nearby=ArrayList<Int>(4)
            if (x>0 && pixel-1 in ink) nearby+=pixel-1
            if (x<source.width-1 && pixel+1 in ink) nearby+=pixel+1
            if (y>0 && pixel-source.width in ink) nearby+=pixel-source.width
            if (y<source.height-1 && pixel+source.width in ink) nearby+=pixel+source.width
            return nearby
        }
        val ends=points.filter { neighbours(it).size==1 }
        require(ends.size==2 && points.all {
            neighbours(it).size in 1..2
        }) {
            "Contorno com loop, bifurcação, ilhas desconectadas ou pontos soltos."
        }

        val path=ArrayList<Int>(points.size)
        var previous=-1
        var current=ends.first()
        while (true) {
            require(path.size<points.size+1) { "Ciclo encontrado no contorno." }
            path+=current
            val adjacent=neighbours(current).filter { it!=previous }
            if (adjacent.isEmpty()) break
            require(adjacent.size==1) { "Ramificação ambígua no contorno." }
            previous=current
            current=adjacent.single()
        }
        require(path.size==points.size && path.last()==ends.last()) {
            "Contorno desconectado ou trajetória incompleta."
        }
        val pathIndex=HashMap<Int,Int>(path.size)
        path.forEachIndexed { index,pixel -> pathIndex[pixel]=index }
        // A diagonal self-contact far from the traced route could hide a
        // crossing. Diagonals caused by an ordinary right-angle bend are
        // local (two indices apart) and remain allowed.
        for ((index,pixel) in path.withIndex()) {
            val x=pixel%source.width
            val y=pixel/source.width
            for (dy in listOf(-1,1)) for (dx in listOf(-1,1)) {
                val nx=x+dx;val ny=y+dy
                if(nx !in 0 until source.width ||
                    ny !in 0 until source.height)continue
                val other=pathIndex[ny*source.width+nx] ?: continue
                require(kotlin.math.abs(other-index)<=2) {
                    "Contorno curvo se toca ou cruza: exige revisão manual."
                }
            }
        }

        data class P(val x: Double,val y: Double)
        fun midpoint(pixel:Int):P=P(
            pixel%source.width+.5,pixel/source.width+.5
        )
        val anchors=ArrayList<P>()
        anchors+=midpoint(path.first())
        var lastAnchor=path.first()
        for(i in 1 until path.size) {
            val turning=if(i<path.lastIndex) {
                val before=path[i]-path[i-1]
                val after=path[i+1]-path[i]
                before!=after
            } else true
            val a=midpoint(lastAnchor)
            val b=midpoint(path[i])
            val spacing=hypot(b.x-a.x,b.y-a.y)*pixelMm
            if ((turning && spacing>=MIN_STITCH_MM) ||
                spacing>=TARGET_STITCH_MM || i==path.lastIndex
            ) {
                if(spacing<MIN_STITCH_MM && i!=path.lastIndex) continue
                require(spacing in MIN_STITCH_MM..MAX_STITCH_MM) {
                    "Passo do ponto corrido fora da faixa de QA."
                }
                anchors+=b
                lastAnchor=path[i]
            }
        }
        require(anchors.size in 3..MAX_PATH_PIXELS)
        val unitsPerPixel=pixelMm*10.0
        fun stitch(p:P,command:StitchCommand)=EmbroideryPoint(
            ((p.x-source.width/2.0)*unitsPerPixel).roundToInt(),
            ((source.height/2.0-p.y)*unitsPerPixel).roundToInt(),
            command,0
        )
        val commands=ArrayList<EmbroideryPoint>()
        commands+=stitch(anchors.first(),StitchCommand.JUMP)
        anchors.drop(1).forEach { commands+=stitch(it,StitchCommand.STITCH) }
        commands+=stitch(anchors.last(),StitchCommand.END)
        val sewn=commands.dropLast(1)
        val bounds=EmbroideryBounds(
            sewn.minOf { it.xUnits },sewn.maxOf { it.xUnits },
            sewn.minOf { it.yUnits },sewn.maxOf { it.yUnits }
        )
        val draft=EmbroideryDesign(
            fileName="CONTORNO-CURVO-QA.qa",
            format="QA_RUNNING_REVIEW",
            label="Ponto corrido curvo, sem exportação ou aprovação física",
            points=commands,bounds=bounds,
            stitchCount=commands.count { it.command==StitchCommand.STITCH },
            jumpCount=1,colorChanges=0,endFound=true,
            sourceBytes=ByteArray(0),threadColors=listOf(part.color),
            isModified=true,hoopProfile=hoop
        )
        // Check final integer STITCH geometry, not just planned pixel points.
        RasterSewnMaskGate.inspect(
            source,part,draft,fullWidthMm
        ).getOrThrow()
        RasterNeedleRiskAudit.inspect(draft,hoop).getOrThrow()
        draft
    }
}
