package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Independently implemented image -> digitized-fill simulator inspired by
 * documented image-to-embroidery workflows (NOT proprietary Artspira code).
 *
 * Color quantization, 4-connected component preservation, per-region
 * one-direction fill and real STITCH/JUMP/TRIM/COLOR_CHANGE playback.
 * This is an approximation for review: no fabric compensation, underlay,
 * calibrated density or physical machine certification.
 * Format QA_IMAGE_VISUAL is NEVER exported to PES, DST or JEF.
 */
internal object PremiumImageDigitizedFillPreview {
    private data class Swatch(val rgb:Int,val weight:Int)
    private data class Layer(val color:Int,val pixels:List<Int>,val top:Int)
    private data class Region(
        val pixels:List<Int>,val top:Int,val left:Int,
        val minX:Int,val maxX:Int,val minY:Int,val maxY:Int
    )
    private fun channel(rgb:Int,shift:Int) = (rgb ushr shift) and 255
    internal fun distance(a:Int,b:Int):Int {
        val dr=channel(a,16)-channel(b,16)
        val dg=channel(a,8)-channel(b,8)
        val db=channel(a,0)-channel(b,0)
        return dr*dr+dg*dg+db*db
    }
    internal fun dominantColors(argb:IntArray,maxColors:Int):List<Int> {
        val counts=HashMap<Int,Int>()
        for(p in argb) {
            if((p ushr 24)<176)continue
            val rgb=p and 0xffffff
            // Reduce noise without losing dominant logo colors.
            val key=((rgb ushr 19) shl 19) or
                (((rgb ushr 11) and 31) shl 11) or
                (((rgb ushr 3) and 31) shl 3)
            counts[key]=(counts[key]?:0)+1
        }
        require(counts.isNotEmpty()) {
            "Não há tinta visível para digitalização."
        }
        val values=counts.map { Swatch(it.key,it.value) }
        val seed=values.maxWithOrNull(compareBy<Swatch>{it.weight}
            .thenBy{it.rgb})!!.rgb
        val centers=ArrayList<Int>()
        centers+=seed
        repeat((maxColors-1).coerceAtLeast(0)) {
            val next=values.maxByOrNull { entry ->
                entry.weight.toLong()*
                    centers.minOf { distance(entry.rgb,it) }.toLong()
            }!!.rgb
            if(centers.any {distance(it,next)<225})return@repeat
            if(next !in centers)centers+=next
        }
        repeat(5) {
            val sums=Array(centers.size){LongArray(4)}
            for(entry in values) {
                val index=centers.indices.minByOrNull {
                    distance(entry.rgb,centers[it])
                }!!
                val weight=entry.weight.toLong()
                sums[index][0]+=channel(entry.rgb,16)*weight
                sums[index][1]+=channel(entry.rgb,8)*weight
                sums[index][2]+=channel(entry.rgb,0)*weight
                sums[index][3]+=weight
            }
            for(i in centers.indices)if(sums[i][3]>0L) {
                val s=sums[i]
                centers[i]=(((s[0]/s[3]).toInt() and 255) shl 16) or
                    (((s[1]/s[3]).toInt() and 255) shl 8) or
                    ((s[2]/s[3]).toInt() and 255)
            }
        }
        return centers.distinct()
    }

    fun create(
        width:Int,height:Int,argb:IntArray,widthMm:Float,
        hoop:HoopProfile,maxColors:Int=4
    ):Result<EmbroideryDesign> = runCatching {
        require(width in 8..160 && height in 8..160 &&
            argb.size==width*height &&
            widthMm.isFinite() && widthMm in 10f..100f &&
            maxColors in 2..8
        ) { "Use PNG com 8–160 px, 2–8 cores e largura de 10–100 mm." }
        val heightMm=widthMm*height/width
        require(widthMm<=hoop.usableWidthMm &&
            heightMm<=hoop.usableHeightMm
        ) { "A imagem ultrapassa a área útil do bastidor." }
        val visible=argb.count {(it ushr 24)>=176}
        require(visible in 16 until argb.size) {
            "A prévia requer tinta e fundo transparente."
        }

        val colors=dominantColors(argb,maxColors)
        val indices=IntArray(argb.size) {i->
            if((argb[i] ushr 24)<176) -1 else
                colors.indices.minByOrNull {
                    distance(argb[i] and 0xffffff,colors[it])
                }!!
        }
        val visited=BooleanArray(indices.size)
        val layers=ArrayList<Pair<Int,Region>>()
        for(colorIndex in colors.indices) {
            for(start in indices.indices) {
                if(visited[start] || indices[start]!=colorIndex)continue
                val pending=ArrayDeque<Int>()
                val component=ArrayList<Int>()
                visited[start]=true
                pending.add(start)
                var loX=width
                var hiX=0
                var loY=height
                var hiY=0
                while(pending.isNotEmpty()) {
                    val i=pending.removeFirst()
                    val x=i%width
                    val y=i/width
                    component+=i
                    loX=minOf(loX,x);hiX=maxOf(hiX,x)
                    loY=minOf(loY,y);hiY=maxOf(hiY,y)
                    for((nx,ny) in arrayOf(
                        Pair(x-1,y),Pair(x+1,y),Pair(x,y-1),Pair(x,y+1)
                    )) {
                        if(nx !in 0 until width || ny !in 0 until height)
                            continue
                        val j=ny*width+nx
                        if(!visited[j] && indices[j]==colorIndex) {
                            visited[j]=true
                            pending.add(j)
                        }
                    }
                }
                if(component.size<3)continue
                layers+=Pair(colorIndex,Region(
                    component,loY,loX,loX,hiX,loY,hiY
                ))
                require(layers.size<=1200) {
                    "Muitos fragmentos; reduza o número de detalhes da imagem."
                }
            }
        }
        require(layers.isNotEmpty()) { "Nenhuma região adequada para simular." }
        layers.sortWith(compareBy<Pair<Int,Region>>{it.second.top}
            .thenBy{it.second.left}.thenBy{it.first})

        fun ux(x:Float)=((x/width-.5f)*widthMm*10).roundToInt()
        fun uy(y:Float)=((.5f-y/height)*heightMm*10).roundToInt()

        val points=ArrayList<EmbroideryPoint>()
        val threadColors=ArrayList<Int>()
        var stitchCount=0
        var jumpCount=0
        var changes=0
        var lastX=0
        var lastY=0
        var lastColor=-1
        // Assign contiguous thread runs; it may repeat a thread color after
        // a separate component to preserve icon -> words -> subtitle order.
        for((family,region) in layers) {
            if(family!=lastColor) {
                if(lastColor>=0) {
                    points+=EmbroideryPoint(
                        lastX,lastY,StitchCommand.COLOR_CHANGE,
                        threadColors.size
                    )
                    changes++
                }
                threadColors+=colors[family]
                lastColor=family
            }
            val colorIndex=threadColors.lastIndex
            val regionMask=BooleanArray(indices.size)
            region.pixels.forEach {regionMask[it]=true}
            // A sewn transition is permitted ONLY inside this exact
            // original component. Previous code trimmed/jumped for EVERY
            // stripe, creating an implausible rapid-fire command sequence.
            // A line-of-sight check also prevents traversing a letter
            // counter (transparent hole) with the needle down.
            fun sameInkPath(ax:Float,ay:Float,bx:Float,by:Float):Boolean {
                val length=kotlin.math.hypot((bx-ax).toDouble(),(by-ay).toDouble())
                val samples=ceil(length/0.30).toInt().coerceAtLeast(1)
                for(k in 0..samples) {
                    val amount=k.toFloat()/samples
                    val ix=(ax+(bx-ax)*amount).toInt()
                    val iy=(ay+(by-ay)*amount).toInt()
                    if(ix !in 0 until width || iy !in 0 until height ||
                        !regionMask[iy*width+ix])return false
                }
                return true
            }
            var previousInkX=0f
            var previousInkY=0f
            var hasPreviousInk=false
            // Direction follows the component's long axis: vertical letter
            // stalks receive transverse horizontal fill, horizontal strokes
            // transverse vertical fill. This is NOT global horizontal scan.
            val tall=region.maxY-region.minY>=region.maxX-region.minX
            val slices=if(tall)
                region.minY..region.maxY else region.minX..region.maxX
            var reverse=false
            val stride=if(slices.count()>110)2 else 1
            for(slice in slices step stride) {
                val starts=ArrayList<IntRange>()
                var pos=if(tall)region.minX else region.minY
                val last=if(tall)region.maxX else region.maxY
                while(pos<=last) {
                    val offset=if(tall) slice*width+pos else pos*width+slice
                    if(!regionMask[offset]){pos++;continue}
                    val first=pos
                    while(pos<=last && regionMask[
                        if(tall) slice*width+pos else pos*width+slice
                    ])pos++
                    starts+=first until pos
                }
                if(starts.isEmpty())continue
                val ordered=if(reverse)starts.asReversed() else starts
                for(span in ordered) {
                    if(span.last-span.first<1)continue
                    // Use centers of ORIGINAL ink pixels, not the outside
                    // edge of each run. This avoids crossing background
                    // during a travel check or stitching into a counter.
                    val a=if(reverse)span.last+0.5f else span.first+0.5f
                    val b=if(reverse)span.first+0.5f else span.last+0.5f
                    val x0=if(tall) ux(a) else ux(slice+.5f)
                    val y0=if(tall) uy(slice+.5f) else uy(a)
                    val x1=if(tall) ux(b) else ux(slice+.5f)
                    val y1=if(tall) uy(slice+.5f) else uy(b)
                    if(x0==x1 && y0==y1)continue
                    val previousX=lastX
                    val previousY=lastY
                    val canSew=hasPreviousInk &&
                        sameInkPath(previousInkX,previousInkY,
                            if(tall)a else slice+0.5f,
                            if(tall)slice+0.5f else a) &&
                        (abs(previousX-x0)+abs(previousY-y0)) <= 30
                    if(canSew) {
                        if(previousX!=x0 || previousY!=y0) {
                            points+=EmbroideryPoint(
                                x0,y0,StitchCommand.STITCH,colorIndex)
                            stitchCount++
                        }
                    } else {
                        // Never fake continuous stitches between separated
                        // letters/regions. Reserve a TRIM for nontrivial
                        // movement, not for every neighboring scan line.
                        if(stitchCount>0 &&
                            abs(previousX-x0)+abs(previousY-y0)>60) {
                            points+=EmbroideryPoint(
                                previousX,previousY,StitchCommand.TRIM,colorIndex
                            )
                        }
                        points+=EmbroideryPoint(
                            x0,y0,StitchCommand.JUMP,colorIndex)
                        jumpCount++
                    }
                    // At most 1.8mm per simulated segment. NO intended
                    // physical Satin stitches or thread density implied.
                    val len=abs(x1-x0)+abs(y1-y0)
                    val steps=ceil(len/18.0).toInt().coerceAtLeast(1)
                    var px=x0
                    var py=y0
                    for(step in 1..steps) {
                        val xx=x0+(x1-x0)*step/steps
                        val yy=y0+(y1-y0)*step/steps
                        if(xx==px && yy==py)continue
                        points+=EmbroideryPoint(
                            xx,yy,StitchCommand.STITCH,colorIndex)
                        stitchCount++
                        px=xx;py=yy
                    }
                    lastX=x1;lastY=y1
                    previousInkX=if(tall)b else slice+0.5f
                    previousInkY=if(tall)slice+0.5f else b
                    hasPreviousInk=true
                    require(points.size<=50_000) {
                        "Pontos demais; diminua as cores ou dimensões."
                    }
                }
                reverse=!reverse
            }
        }
        require(stitchCount in 10..25_000 && threadColors.isNotEmpty())
        points+=EmbroideryPoint(
            lastX,lastY,StitchCommand.END,threadColors.lastIndex
        )
        val coordinates=points.filter {
            it.command==StitchCommand.JUMP ||
                it.command==StitchCommand.STITCH
        }
        val design=EmbroideryDesign(
            fileName="IMAGEM_DIGITALIZADA_QA_VISUAL.qa",
            format="QA_IMAGE_VISUAL",
            label="Enchimento digitalizado • visual • não exportável",
            points=points,
            bounds=EmbroideryBounds(
                coordinates.minOf{it.xUnits},coordinates.maxOf{it.xUnits},
                coordinates.minOf{it.yUnits},coordinates.maxOf{it.yUnits}
            ),
            stitchCount=stitchCount,
            jumpCount=jumpCount,
            colorChanges=changes,
            endFound=true,
            sourceBytes=ByteArray(0),
            threadColors=threadColors,
            isModified=true,
            hoopProfile=hoop
        )
        EmbroideryStressPolicy.requireGeneratedSafe(design)
        require(HoopValidator.validate(design,hoop).fits)
        require(MatrixConverter.convert(design,"PES").isFailure)
        design
    }
}
