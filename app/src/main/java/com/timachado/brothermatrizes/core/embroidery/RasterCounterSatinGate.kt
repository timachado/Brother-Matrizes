package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Detects enclosed negative spaces ("counters" in O, B, P) directly from
 * the ORIGINAL connected-object binary mask, before route selection.
 *
 * Confirms that the FINAL quantized Satin STITCH stream does not cross the
 * interior of a counter. JUMP and TRIM are travel, not sewn segments.
 *
 * An edge pixel of a hole is excluded from the forbidden core to tolerate
 * the fixed 0.15-mm QA pull-compensation. This is a geometrical screening
 * rule, not Brother stitch-density guidance or a physical stitch-out.
 *
 * Other ink coverage, color fidelity, excessive needle penetrations, PES
 * compatibility and fabric QA are independent mandatory reviews.
 */
internal data class RasterCounterSatinReport(
    val counterCount: Int,
    val largestCounterPixels: Int,
    val sewnSegmentsInspected: Int,
    val counterCoreSamples: Int
)

internal object RasterCounterSatinGate {
    private const val MAX_HOLES = 2
    private const val MIN_HOLE_PIXELS = 16
    private const val MAX_SAMPLES = 350_000

    private data class Regions(
        val count: Int,
        val largest: Int,
        val core: BooleanArray
    )

    private fun regions(
        image: RasterImagePixels,
        part: RasterSatinObject
    ): Regions {
        require(image.width in 8..512 && image.height in 8..512 &&
            image.argb.size == image.width * image.height &&
            part.mask.pixels.size == image.argb.size
        ) { "Imagem incompatível com análise de contraformas." }
        val mask=part.mask
        val w=mask.maxX-mask.minX+1
        val h=mask.maxY-mask.minY+1
        require(w>=8 && h>=8 && w*h<=200_000) {
            "Região pequena ou complexa demais para contraformas."
        }
        fun ink(x:Int,y:Int):Boolean =
            mask.pixels[(mask.minY+y)*image.width+mask.minX+x]==0
        val outer=BooleanArray(w*h)
        val queue=IntArray(w*h)
        var head=0
        var tail=0
        fun exterior(x:Int,y:Int) {
            val p=y*w+x
            if(outer[p] || ink(x,y))return
            outer[p]=true
            queue[tail++]=p
        }
        for(x in 0 until w) {
            exterior(x,0)
            exterior(x,h-1)
        }
        for(y in 0 until h) {
            exterior(0,y)
            exterior(w-1,y)
        }
        fun walkFlood() {
            while(head<tail) {
                val p=queue[head++]
                val x=p%w
                val y=p/w
                if(x>0)exterior(x-1,y)
                if(x<w-1)exterior(x+1,y)
                if(y>0)exterior(x,y-1)
                if(y<h-1)exterior(x,y+1)
            }
        }
        walkFlood()
        val visited=outer.copyOf()
        val hole=BooleanArray(w*h)
        var count=0
        var largest=0
        for(root in 0 until w*h) {
            if(visited[root] || ink(root%w,root/w))continue
            head=0;tail=0
            queue[tail++]=root
            visited[root]=true
            val component=ArrayList<Int>()
            while(head<tail) {
                val p=queue[head++]
                component+=p
                val x=p%w;val y=p/w
                fun add(nx:Int,ny:Int) {
                    if(nx !in 0 until w || ny !in 0 until h)return
                    val v=ny*w+nx
                    if(visited[v] || ink(nx,ny))return
                    visited[v]=true
                    queue[tail++]=v
                }
                add(x-1,y);add(x+1,y);add(x,y-1);add(x,y+1)
            }
            require(component.size>=MIN_HOLE_PIXELS) {
                "Contraforma pequena demais: revisar manualmente."
            }
            count++
            require(count<=MAX_HOLES) {
                "Mais de duas contraformas: revisão manual necessária."
            }
            largest=maxOf(largest,component.size)
            for(p in component)hole[p]=true
        }
        val core=BooleanArray(image.argb.size)
        for(y in 1 until h-1) for(x in 1 until w-1) {
            val p=y*w+x
            if(!hole[p])continue
            var interior=true
            for(dy in -1..1) for(dx in -1..1)
                if(!hole[(y+dy)*w+x+dx])interior=false
            if(interior) core[(mask.minY+y)*image.width+mask.minX+x]=true
        }
        return Regions(count,largest,core)
    }

    /** Any returned positive count is an instruction to use counter-aware
     *  Satin planning, never unstructured fill through a hole. */
    fun count(
        image: RasterImagePixels,
        part: RasterSatinObject
    ): Result<Int> = runCatching {
        regions(image,part).count
    }

    fun inspect(
        image: RasterImagePixels,
        part: RasterSatinObject,
        design: EmbroideryDesign,
        fullWidthMm: Float
    ): Result<RasterCounterSatinReport> = runCatching {
        require(design.format=="QA_SATIN_REVIEW" &&
            design.sourceBytes.isEmpty() && design.isModified &&
            design.points.size in 3..90_000 &&
            design.points.last().command==StitchCommand.END &&
            fullWidthMm.isFinite() && fullWidthMm in 10f..200f
        ) {
            "Auditoria de contraformas aceita somente Satin experimental."
        }
        val r=regions(image,part)
        require(r.count in 1..MAX_HOLES) {
            "Sem contraforma confirmada para esta rota Satin."
        }
        require(r.core.any { it }) {
            "Contraforma estreita demais para confirmar proteção da agulha."
        }
        val mmPerPixel=fullWidthMm/image.width.toDouble()
        val unit=mmPerPixel*10.0
        fun coord(p:EmbroideryPoint)=
            (p.xUnits/unit+image.width/2.0) to
                (image.height/2.0-p.yUnits/unit)
        var last:EmbroideryPoint?=null
        var inspected=0
        var samples=0
        var coreSamples=0
        for ((index,p) in design.points.withIndex()) {
            when(p.command) {
                StitchCommand.JUMP -> last=p
                StitchCommand.STITCH -> {
                    val first=last ?: error(
                        "STITCH sem JUMP após um corte ou troca de linha."
                    )
                    require(first.colorIndex==p.colorIndex) {
                        "Trajetória Satin atravessa cores."
                    }
                    val (ax,ay)=coord(first)
                    val (bx,by)=coord(p)
                    val len=hypot(bx-ax,by-ay)
                    require(len*mmPerPixel in .2..7.8) {
                        "Passo Satin fora dos limites experimentais."
                    }
                    val steps=ceil(len*5.0).toInt().coerceAtLeast(1)
                    for(i in 0..steps) {
                        samples++
                        require(samples<=MAX_SAMPLES) {
                            "Auditoria de contraformas excedeu limite."
                        }
                        val t=i.toDouble()/steps
                        val x=ax+(bx-ax)*t
                        val y=ay+(by-ay)*t
                        val px=floor(x).toInt()
                        val py=floor(y).toInt()
                        if(px in 0 until image.width &&
                            py in 0 until image.height &&
                            r.core[py*image.width+px]) {
                            coreSamples++
                            error("Pontada ${index+1} atravessa o interior " +
                                "de uma letra com contraforma. Satin recusado.")
                        }
                    }
                    inspected++
                    last=p
                }
                StitchCommand.TRIM,StitchCommand.COLOR_CHANGE,
                StitchCommand.STOP -> last=null
                StitchCommand.END -> require(index==design.points.lastIndex) {
                    "Comando END antes do último ponto."
                }
                StitchCommand.SEQUIN -> error(
                    "Lantejoulas não suportadas na prévia."
                )
            }
        }
        require(inspected==design.stitchCount && inspected>0) {
            "Contagem real de pontadas Satin inconsistente."
        }
        RasterCounterSatinReport(
            counterCount=r.count,largestCounterPixels=r.largest,
            sewnSegmentsInspected=inspected,counterCoreSamples=coreSamples
        )
    }
}
