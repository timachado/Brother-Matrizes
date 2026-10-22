package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Visual-only, DIRECTIONAL Satin study based on the shape of each imported
 * stroke. It follows a thinned centerline and finds the TWO actual rails
 * perpendicular to the local tangent. It never applies fixed horizontal
 * rows to the logo or crosses transparent counters to fill a disconnected
 * area. Actual machine-ready Satin additionally needs proper rail editing,
 * underlay, fabric compensation and independent stitch-out verification.
 *
 * Format QA_IMAGE_VISUAL is non-exportable by MatrixConverter.
 */
internal object PremiumImageSatinVisualPreview {
    private val threadPalette = listOf(
        0x252525, 0xF5F2EB, 0xE4AC46, 0xD8544B,
        0x42916F, 0x518ECA, 0x9377BB
    )
    private val dx = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
    private val dy = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1)
    private data class Stroke(val points:List<Int>,val firstY:Int,val firstX:Int)
    private data class ColorLayer(val family:Int,val top:Int)

    private fun thin(original:BooleanArray,w:Int,h:Int):BooleanArray {
        val mask=original.copyOf()
        val discard=ArrayList<Int>()
        // Zhang-Suen topology-preserving skeletonization, not a raster
        // direction preference. Ends and counters remain separated.
        repeat(96) {
            var changed=false
            for(pass in 0..1) {
                discard.clear()
                for(y in 1 until h-1) for(x in 1 until w-1) {
                    val i=y*w+x
                    if(!mask[i])continue
                    val n=BooleanArray(8) { k ->
                        mask[(y+dy[k])*w+x+dx[k]]
                    }
                    val count=n.count {it}
                    if(count !in 2..6)continue
                    var transitions=0
                    for(k in 0..7) if(!n[k] && n[(k+1)%8])
                        transitions++
                    if(transitions!=1)continue
                    val keep=if(pass==0) {
                        (n[0] && n[2] && n[4]) ||
                            (n[2] && n[4] && n[6])
                    } else {
                        (n[0] && n[2] && n[6]) ||
                            (n[0] && n[4] && n[6])
                    }
                    if(!keep)discard+=i
                }
                if(discard.isNotEmpty()) {
                    changed=true
                    for(i in discard)mask[i]=false
                }
            }
            if(!changed)return mask
        }
        return mask
    }

    private fun strokes(skeleton:BooleanArray,w:Int,h:Int):List<Stroke> {
        val adjacency=Array(skeleton.size){ArrayList<Int>(3)}
        for(i in skeleton.indices) {
            if(!skeleton[i])continue
            val x=i%w
            val y=i/w
            for(k in 0..7) {
                val nx=x+dx[k]
                val ny=y+dy[k]
                if(nx !in 0 until w || ny !in 0 until h)continue
                val j=ny*w+nx
                if(!skeleton[j])continue
                // Avoid shortcut diagonals around orthogonal stair-step
                // skeleton pixels, which otherwise manufacture forks.
                if(dx[k]!=0 && dy[k]!=0 &&
                    (skeleton[y*w+nx] || skeleton[ny*w+x]))continue
                adjacency[i].add(j)
            }
        }
        val traversed=HashSet<Long>()
        fun code(a:Int,b:Int):Long {
            val lo=minOf(a,b)
            val hi=maxOf(a,b)
            return (lo.toLong() shl 32) or hi.toLong()
        }
        val chains=ArrayList<Stroke>()
        fun walk(start:Int,first:Int) {
            val chain=ArrayList<Int>()
            chain+=start
            var prev=start
            var current=first
            traversed.add(code(prev,current))
            while(true) {
                chain+=current
                val next=adjacency[current].filter {
                    it!=prev && !traversed.contains(code(current,it))
                }
                if(adjacency[current].size!=2 || next.size!=1)break
                val chosen=next.first()
                prev=current
                current=chosen
                traversed.add(code(prev,current))
                if(chain.size>skeleton.size)break
            }
            if(chain.size>=2) {
                val min=chain.minWith(compareBy<Int> {it/w}.thenBy{it%w})
                chains+=Stroke(chain,min/w,min%w)
            }
        }
        // Prefer endpoints and true fork-to-fork trails. Closed glyphs
        // (e.g. O) have only degree-two pixels and are traced afterward.
        for(i in skeleton.indices) {
            if(!skeleton[i] || adjacency[i].size==2)continue
            for(j in adjacency[i]) {
                if(!traversed.contains(code(i,j)))walk(i,j)
            }
        }
        for(i in skeleton.indices) {
            if(!skeleton[i])continue
            for(j in adjacency[i]) {
                if(!traversed.contains(code(i,j)))walk(i,j)
            }
        }
        return chains.sortedWith(
            compareBy<Stroke>{it.firstY}.thenBy{it.firstX}
        )
    }

    /** Ray along each local normal, stopping at ORIGINAL edge or counter. */
    private fun rail(
        centerX:Float,centerY:Float,
        normalX:Double,normalY:Double,
        direction:Double,
        region:BooleanArray,w:Int,h:Int
    ):Pair<Float,Float> {
        var distance=0.0
        var lastX=centerX
        var lastY=centerY
        // step less than half a pixel to avoid skipping thin counters
        while(distance < 64.0) {
            distance+=0.4
            val x=centerX+normalX*distance*direction
            val y=centerY+normalY*distance*direction
            val ix=x.toInt()
            val iy=y.toInt()
            if(ix !in 0 until w || iy !in 0 until h ||
                !region[iy*w+ix])break
            lastX=x.toFloat()
            lastY=y.toFloat()
        }
        return Pair(lastX,lastY)
    }

    private fun insidePath(
        ax:Float,ay:Float,bx:Float,by:Float,
        region:BooleanArray,w:Int,h:Int
    ):Boolean {
        val length=hypot((bx-ax).toDouble(),(by-ay).toDouble())
        val steps=ceil(length/0.35).toInt().coerceAtLeast(1)
        for(i in 0..steps) {
            val ratio=i.toFloat()/steps
            val x=(ax+(bx-ax)*ratio).toInt()
            val y=(ay+(by-ay)*ratio).toInt()
            if(x !in 0 until w || y !in 0 until h ||
                !region[y*w+x])return false
        }
        return true
    }

    fun create(
        width:Int,height:Int,argb:IntArray,
        widthMm:Float,hoop:HoopProfile
    ):Result<EmbroideryDesign> = runCatching {
        require(width in 8..160 && height in 8..160 &&
            argb.size==width*height &&
            widthMm.isFinite() && widthMm in 10f..100f) {
            "Imagem inválida para simulação visual."
        }
        val heightMm=widthMm*height/width
        require(widthMm<=hoop.usableWidthMm &&
            heightMm<=hoop.usableHeightMm) {
            "O desenho ultrapassa a área útil do bastidor."
        }
        val groups=IntArray(argb.size) { i ->
            if((argb[i] ushr 24)>=160)
                PremiumImageVisualStitchPreview.colorFamily(argb[i])
            else -1
        }
        require(groups.count{it>=0} in 12 until groups.size) {
            "Imagem requer tinta e fundo transparente."
        }
        val layers=ArrayList<ColorLayer>()
        for(family in threadPalette.indices) {
            val first=groups.indexOfFirst{it==family}
            if(first>=0 && groups.count{it==family}>=2)
                layers+=ColorLayer(family,first/width)
        }
        layers.sortWith(compareBy<ColorLayer>{it.top}.thenBy{it.family})
        require(layers.isNotEmpty())

        fun ux(x:Float)=((x/width-.5f)*widthMm*10f).roundToInt()
        fun uy(y:Float)=((.5f-y/height)*heightMm*10f).roundToInt()

        val points=ArrayList<EmbroideryPoint>()
        val threads=ArrayList<Int>()
        var stitchCount=0
        var jumpCount=0
        var changes=0
        var lastX=0
        var lastY=0
        for(layer in layers) {
            val region=BooleanArray(groups.size) {groups[it]==layer.family}
            val skeleton=thin(region,width,height)
            val rails=strokes(skeleton,width,height)
            if(rails.isEmpty())continue
            val colorIndex=threads.size
            if(colorIndex>0) {
                points+=EmbroideryPoint(
                    lastX,lastY,StitchCommand.COLOR_CHANGE,colorIndex
                )
                changes++
            }
            threads+=threadPalette[layer.family]
            for(stroke in rails) {
                var wasStarted=false
                var previousX=0f
                var previousY=0f
                for(i in stroke.points.indices) {
                    val cell=stroke.points[i]
                    val cx=cell%width+.5f
                    val cy=cell/width+.5f
                    val before=stroke.points[maxOf(0,i-1)]
                    val after=stroke.points[minOf(stroke.points.lastIndex,i+1)]
                    val tangentX=(after%width-before%width).toDouble()
                    val tangentY=(after/width-before/width).toDouble()
                    val magnitude=hypot(tangentX,tangentY)
                    if(magnitude<0.01)continue
                    val normalX=-tangentY/magnitude
                    val normalY=tangentX/magnitude
                    val left=rail(cx,cy,normalX,normalY,-1.0,
                        region,width,height)
                    val right=rail(cx,cy,normalX,normalY,1.0,
                        region,width,height)
                    val endA=if(i%2==0)left else right
                    val endB=if(i%2==0)right else left
                    val x0=ux(endA.first)
                    val y0=uy(endA.second)
                    val x1=ux(endB.first)
                    val y1=uy(endB.second)
                    if(x0==x1 && y0==y1)continue
                    val connecting=wasStarted &&
                        insidePath(previousX,previousY,endA.first,endA.second,
                            region,width,height)
                    if(!connecting) {
                        if(stitchCount>0) {
                            points+=EmbroideryPoint(
                                lastX,lastY,StitchCommand.TRIM,colorIndex
                            )
                        }
                        points+=EmbroideryPoint(x0,y0,StitchCommand.JUMP,colorIndex)
                        jumpCount++
                    } else if(x0!=lastX || y0!=lastY) {
                        points+=EmbroideryPoint(x0,y0,StitchCommand.STITCH,colorIndex)
                        stitchCount++
                    }
                    // The transverse needle run is subdivided for the
                    // visual engine's draw rate; this is NOT an approved
                    // physical Satin width or stitch-density setting.
                    val span=hypot(
                        (x1-x0).toDouble(),(y1-y0).toDouble()
                    )
                    val steps=ceil(span/18.0).toInt().coerceAtLeast(1)
                    var px=x0
                    var py=y0
                    for(step in 1..steps) {
                        val xx=x0+(x1-x0)*step/steps
                        val yy=y0+(y1-y0)*step/steps
                        if(xx==px && yy==py)continue
                        points+=EmbroideryPoint(xx,yy,StitchCommand.STITCH,colorIndex)
                        px=xx;py=yy
                        stitchCount++
                    }
                    lastX=x1
                    lastY=y1
                    previousX=endB.first
                    previousY=endB.second
                    wasStarted=true
                    require(points.size<=45000) {
                        "Desenho detalhado demais para reprodução dos pontos."
                    }
                }
            }
        }
        require(stitchCount in 10..30000 && threads.isNotEmpty()) {
            "Não foi possível gerar trilhos visuais para a arte."
        }
        points+=EmbroideryPoint(lastX,lastY,StitchCommand.END,threads.lastIndex)
        val active=points.filter {
            it.command==StitchCommand.JUMP ||
                it.command==StitchCommand.STITCH
        }
        val design=EmbroideryDesign(
            fileName="IMAGEM_SATIN_DIRECIONAL_NAO_EXPORTAR.qa",
            format="QA_IMAGE_VISUAL",
            label="Estudo visual Satin seguindo eixos dos traços — SEM PES",
            points=points,
            bounds=EmbroideryBounds(
                active.minOf{it.xUnits},active.maxOf{it.xUnits},
                active.minOf{it.yUnits},active.maxOf{it.yUnits}
            ),
            stitchCount=stitchCount,
            jumpCount=jumpCount,
            colorChanges=changes,
            endFound=true,
            sourceBytes=ByteArray(0),
            threadColors=threads,
            sourceYAxisDown=false,
            isModified=true,
            hoopProfile=hoop
        )
        EmbroideryStressPolicy.requireGeneratedSafe(design)
        require(HoopValidator.validate(design,hoop).fits)
        require(MatrixConverter.convert(design,"PES").isFailure) {
            "A prévia visual não pode ser exportada."
        }
        design
    }
}
