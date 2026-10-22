package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Simulator ONLY. A faithful visual outline of the original alpha/color
 * regions is traced with directional grid edges. No horizontal raster-scan
 * pseudo-fills, and no bridges through transparent counters.
 *
 * The embroidery sequence is a visual DRAFT: not a real compensated
 * Satin/Tatami digitization or a physical machine pattern. MatrixConverter
 * explicitly rejects QA_IMAGE_VISUAL in all machine formats.
 *
 * The playback uses the already existing Brother SimulatorScreen and
 * MachineSimulationCanvas. This is original source code, not copied from
 * any external application's proprietary APK.
 */
internal object PremiumImageVisualStitchPreview {
    private data class Edge(val from: Int,val to: Int)

    private val palette = listOf(
        0x252525, // dark ink
        0xF5F2EB, // light ink
        0xE4AC46, // gold/orange
        0xD8544B, // red
        0x42916F, // green
        0x518ECA, // blue
        0x9377BB  // purple
    )

    internal fun colorFamily(pixel: Int): Int {
        val r = (pixel ushr 16) and 255
        val g = (pixel ushr 8) and 255
        val b = pixel and 255
        val hi = maxOf(r,g,b)
        val lo = minOf(r,g,b)
        if(hi-lo < 35) return if((r+g+b)/3 >= 148) 1 else 0
        return when {
            r>=g+24 && g>=b+10 -> 2
            r>g+20 && r>b+20 -> 3
            g>r+18 && g>b+10 -> 4
            b>r+18 && b>g+12 -> 5
            else -> 6
        }
    }

    /** One winding loop per closed raster edge circuit. */
    private fun traceContours(width:Int,height:Int,filled:BooleanArray):
        List<List<Pair<Int,Int>>> {
        fun key(x:Int,y:Int) = y*(width+1)+x
        val edges=ArrayList<Edge>()
        val outgoing=HashMap<Int,MutableList<Int>>()
        val arrivals=HashMap<Int,Int>()
        fun edge(a:Int,b:Int) {
            require(edges.size < 52000) {
                "Desenho detalhado demais para simular os contornos em segurança."
            }
            val id=edges.size
            edges.add(Edge(a,b))
            outgoing.getOrPut(a){ArrayList(2)}.add(id)
            arrivals[b]=(arrivals[b]?:0)+1
        }
        fun opaque(x:Int,y:Int) =
            x in 0 until width && y in 0 until height &&
                filled[y*width+x]
        for(y in 0 until height) for(x in 0 until width) {
            if(!opaque(x,y)) continue
            if(!opaque(x,y-1)) edge(key(x,y),key(x+1,y))
            if(!opaque(x+1,y)) edge(key(x+1,y),key(x+1,y+1))
            if(!opaque(x,y+1)) edge(key(x+1,y+1),key(x,y+1))
            if(!opaque(x-1,y)) edge(key(x,y+1),key(x,y))
        }
        if(edges.isEmpty()) return emptyList()
        require(arrivals.keys == outgoing.keys &&
            outgoing.all { (vertex,ways) ->
                ways.size in 1..2 && arrivals[vertex]==ways.size
            }) { "Imagem possui um contorno aberto ou ramificado." }

        fun turn(a:Edge,b:Edge):Int {
            val dx=a.to%(width+1)-a.from%(width+1)
            val dy=a.to/(width+1)-a.from/(width+1)
            val ex=b.to%(width+1)-b.from%(width+1)
            val ey=b.to/(width+1)-b.from/(width+1)
            val cross=dx*ey-dy*ex
            val dot=dx*ex+dy*ey
            return when { cross>0 -> 3; dot>0 -> 2; cross<0 -> 1; else -> 0 }
        }
        val next=IntArray(edges.size)
        for((i,segment) in edges.withIndex()) {
            val options=outgoing[segment.to]
                ?: error("Contorno sem continuação.")
            next[i]=options.maxByOrNull { turn(segment,edges[it]) }
                ?: error("Contorno sem saída.")
            require(turn(segment,edges[next[i]])>0) {
                "Contorno contém retrocesso inesperado."
            }
        }
        val indegree=IntArray(edges.size)
        for(i in next) indegree[i]++
        require(indegree.all{it==1}) {
            "Contorno com contato diagonal ambíguo."
        }
        val seen=BooleanArray(edges.size)
        val loops=ArrayList<List<Pair<Int,Int>>>()
        for(first in edges.indices) {
            if(seen[first]) continue
            val vertices=ArrayList<Int>()
            var e=first
            do {
                require(!seen[e]) { "Contorno duplicado." }
                seen[e]=true
                vertices.add(edges[e].from)
                e=next[e]
                require(vertices.size<=edges.size) {
                    "Trajetória sem fechamento."
                }
            } while(e!=first)
            if(vertices.size<4) continue
            // Remove redundant consecutive collinear vertices, never
            // geometric corners. This retains individual holes and glyphs.
            val corners=ArrayList<Pair<Int,Int>>()
            for(i in vertices.indices) {
                val previous=vertices[(i+vertices.size-1)%vertices.size]
                val current=vertices[i]
                val following=vertices[(i+1)%vertices.size]
                val ax=current%(width+1)-previous%(width+1)
                val ay=current/(width+1)-previous/(width+1)
                val bx=following%(width+1)-current%(width+1)
                val by=following/(width+1)-current/(width+1)
                if(ax*by!=ay*bx) {
                    corners.add(Pair(
                        current%(width+1),current/(width+1)
                    ))
                }
            }
            require(corners.size>=4)
            loops.add(corners)
            require(loops.size<=1000) {
                "Há detalhes demais para simular todos os elementos."
            }
        }
        require(seen.all{it})
        return loops
    }

    fun create(
        width:Int,height:Int,argb:IntArray,
        widthMm:Float,hoop:HoopProfile
    ):Result<EmbroideryDesign> = runCatching {
        require(width in 8..160 && height in 8..160 &&
            argb.size==width*height &&
            widthMm.isFinite() && widthMm in 10f..100f
        ) { "Importe imagem de até 160 px e escolha 10–100 mm." }
        val heightMm=widthMm*height/width
        require(widthMm<=hoop.usableWidthMm &&
            heightMm<=hoop.usableHeightMm
        ) { "O desenho ultrapassa a área útil do bastidor." }

        val groups=IntArray(argb.size){ i ->
            if((argb[i] ushr 24)>=160) colorFamily(argb[i]) else -1
        }
        val ink=groups.count{it>=0}
        require(ink in 16 until groups.size) {
            "Simulação exige imagem com tinta e fundo transparente."
        }
        val frequencies=IntArray(palette.size)
        for(family in groups) if(family>=0) frequencies[family]++
        val layers=frequencies.indices.filter{frequencies[it]>=2}
        require(layers.isNotEmpty()) { "Imagem sem detalhes identificáveis." }

        fun xUnits(x:Float) =
            ((x/width-.5f)*widthMm*10f).roundToInt()
        fun yUnits(y:Float) =
            ((.5f-y/height)*heightMm*10f).roundToInt()

        val commands=ArrayList<EmbroideryPoint>()
        val threads=ArrayList<Int>()
        var stitches=0
        var jumps=0
        var changes=0
        var lastX=0
        var lastY=0
        for(family in layers) {
            val mask=BooleanArray(groups.size){groups[it]==family}
            val loops=traceContours(width,height,mask)
            if(loops.isEmpty()) continue
            val colorIndex=threads.size
            if(threads.isNotEmpty()) {
                commands.add(EmbroideryPoint(
                    lastX,lastY,StitchCommand.COLOR_CHANGE,colorIndex
                ))
                changes++
            }
            threads.add(palette[family])
            // Large perimeters get evenly spaced STITCH movements; every
            // true raster corner remains a stitch endpoint.
            for(loop in loops) {
                require(loop.size>=4)
                val first=loop.first()
                val startX=xUnits(first.first.toFloat())
                val startY=yUnits(first.second.toFloat())
                if(stitches>0) {
                    commands.add(EmbroideryPoint(
                        lastX,lastY,StitchCommand.TRIM,colorIndex
                    ))
                }
                commands.add(EmbroideryPoint(
                    startX,startY,StitchCommand.JUMP,colorIndex
                ))
                jumps++
                var x=startX
                var y=startY
                for(i in 1..loop.size) {
                    val vertex=loop[i%loop.size]
                    val xx=xUnits(vertex.first.toFloat())
                    val yy=yUnits(vertex.second.toFloat())
                    val steps=ceil(hypot(
                        (xx-x).toDouble(),(yy-y).toDouble()
                    )/18.0).toInt().coerceAtLeast(1)
                    val fromX=x
                    val fromY=y
                    for(t in 1..steps) {
                        val tx=fromX+(xx-fromX)*t/steps
                        val ty=fromY+(yy-fromY)*t/steps
                        if(tx==x && ty==y) continue
                        commands.add(EmbroideryPoint(
                            tx,ty,StitchCommand.STITCH,colorIndex
                        ))
                        stitches++
                        x=tx
                        y=ty
                    }
                    require(commands.size<=45000) {
                        "Desenho complexo demais para visualização de pontos."
                    }
                }
                lastX=x
                lastY=y
            }
        }
        require(stitches in 10..25000 && threads.isNotEmpty()) {
            "Contornos insuficientes ou complexos demais para simular."
        }
        commands.add(EmbroideryPoint(
            lastX,lastY,StitchCommand.END,threads.lastIndex
        ))
        val traveled=commands.filter {
            it.command==StitchCommand.STITCH ||
                it.command==StitchCommand.JUMP
        }
        val result=EmbroideryDesign(
            fileName="SIMULACAO_CONTORNOS_VISUAIS.qa",
            format="QA_IMAGE_VISUAL",
            label="Prévia de contornos para simulação — não exportável",
            points=commands,
            bounds=EmbroideryBounds(
                traveled.minOf{it.xUnits},traveled.maxOf{it.xUnits},
                traveled.minOf{it.yUnits},traveled.maxOf{it.yUnits}
            ),
            stitchCount=stitches,
            jumpCount=jumps,
            colorChanges=changes,
            endFound=true,
            sourceBytes=ByteArray(0),
            threadColors=threads,
            sourceYAxisDown=false,
            isModified=true,
            hoopProfile=hoop
        )
        EmbroideryStressPolicy.requireGeneratedSafe(result)
        require(HoopValidator.validate(result,hoop).fits)
        result
    }
}
