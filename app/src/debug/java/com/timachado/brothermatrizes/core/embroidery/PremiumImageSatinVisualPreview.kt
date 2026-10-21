package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * VISUAL satin-like coverage for the Brother Simulator ONLY.
 *
 * For each original opaque color band, alternates needle movements between
 * its two edges as rows advance, revealing filled lettering over the source
 * artwork ghost. Adjacent rows may connect only if they share source ink at
 * the endpoints; different glyphs/counters are separated with JUMP/TRIM.
 *
 * This is NOT embroidery digitization: wide spans are subdivided, and no
 * underlay, compensation, pull/tension, machine trimming or thread-density
 * calibration is computed. QA_IMAGE_VISUAL must never be exported to PES.
 */
internal object PremiumImageSatinVisualPreview {
    private data class ColorLayer(
        val family:Int,
        val top:Int,
        val count:Int
    )

    fun create(width:Int,height:Int,argb:IntArray,
        widthMm:Float,hoop:HoopProfile):Result<EmbroideryDesign> = runCatching {
        require(width in 8..160 && height in 8..160 &&
            argb.size==width*height &&
            widthMm.isFinite() && widthMm in 10f..100f) {
            "Imagem inválida para simulação visual."
        }
        val heightMm=widthMm*height/width
        require(widthMm<=hoop.usableWidthMm &&
            heightMm<=hoop.usableHeightMm) {
            "Desenho ultrapassa a área útil do bastidor."
        }
        val groups=IntArray(argb.size) { index ->
            if((argb[index] ushr 24)>=160)
                PremiumImageVisualStitchPreview.colorFamily(argb[index])
            else -1
        }
        val ink=groups.count { it>=0 }
        require(ink in 12 until groups.size) {
            "Importe uma imagem com tinta e fundo transparente."
        }

        val layers=ArrayList<ColorLayer>()
        for(family in 0..6) {
            val count=groups.count { it==family }
            if(count<2) continue
            val top=(0 until height).first { y ->
                (0 until width).any { x -> groups[y*width+x]==family }
            }
            layers.add(ColorLayer(family,top,count))
        }
        // The icon at top of PANOBIANCO appears before main lettering,
        // then ACADEMIAS. This follows the layout rather than hardcoding
        // any particular trademark or imported image content.
        layers.sortWith(compareBy<ColorLayer> {it.top}.thenBy{it.family})
        require(layers.isNotEmpty())

        fun ux(x:Float)=((x/width-.5f)*widthMm*10f).roundToInt()
        fun uy(y:Float)=((.5f-y/height)*heightMm*10f).roundToInt()

        val colors=listOf(
            0x252525,0xF5F2EB,0xE4AC46,0xD8544B,
            0x42916F,0x518ECA,0x9377BB
        )
        val commands=ArrayList<EmbroideryPoint>()
        val threads=ArrayList<Int>()
        var stitchCount=0
        var jumpCount=0
        var changes=0
        var lastX=0
        var lastY=0
        for(layer in layers) {
            val family=layer.family
            val index=threads.size
            if(threads.isNotEmpty()) {
                commands.add(EmbroideryPoint(
                    lastX,lastY,StitchCommand.COLOR_CHANGE,index
                ))
                changes++
            }
            threads.add(colors[family])
            var prevRow=-100
            var prevEndX=-100_000
            var prevEndY=0
            var prevLeft=-1
            var prevRight=-1
            var rowCount=0

            for(y in 0 until height) {
                val segments=ArrayList<IntRange>()
                var x=0
                while(x<width) {
                    if(groups[y*width+x]!=family) { x++; continue }
                    val left=x
                    while(x<width && groups[y*width+x]==family) x++
                    segments.add(left until x)
                }
                if(segments.isEmpty())continue
                // Alternate start side each row so adjacent stripes form a
                // zig-zag of thick stitches across the region's two rails.
                // Multiple letter components on a row are never sewn across.
                val leftToRight=(rowCount%2==0)
                rowCount++
                val ordered=if(leftToRight) segments
                    else segments.asReversed()
                for(seg in ordered) {
                    val left=seg.first
                    val right=seg.last+1
                    val startPixel=if(leftToRight) left.toFloat()
                        else right.toFloat()
                    val endPixel=if(leftToRight) right.toFloat()
                        else left.toFloat()
                    val x0=ux(startPixel)
                    val x1=ux(endPixel)
                    val yy=uy(y+.5f)
                    if(x0==x1)continue

                    // Only connect two successive rows if there is ink
                    // overlap and their needle endpoints are adjacent. All
                    // other transitions must be unsewn moves (JUMP).
                    val touchesLastBand=prevRow==y-1 &&
                        maxOf(prevLeft,left) <= minOf(prevRight,right) &&
                        abs(x0-prevEndX) <=
                            ((widthMm*10f/width)*2.5f).roundToInt()
                    val canConnect=touchesLastBand &&
                        abs(yy-prevEndY) <=
                            ((heightMm*10f/height)*2f).roundToInt()+1
                    if(!canConnect) {
                        if(stitchCount>0) {
                            commands.add(EmbroideryPoint(
                                lastX,lastY,StitchCommand.TRIM,index
                            ))
                        }
                        commands.add(EmbroideryPoint(
                            x0,yy,StitchCommand.JUMP,index
                        ))
                        jumpCount++
                    } else if(lastX!=x0 || lastY!=yy) {
                        commands.add(EmbroideryPoint(
                            x0,yy,StitchCommand.STITCH,index
                        ))
                        stitchCount++
                    }

                    // A dense-looking visual satin band: traverse from one
                    // boundary to the other in short simulated increments.
                    // Long bands are subdivided rather than forging one
                    // physically unsafe 10+ mm satin stitch.
                    val steps=ceil(abs(x1-x0)/18.0).toInt()
                        .coerceAtLeast(1)
                    var px=x0
                    for(step in 1..steps) {
                        val sx=x0+(x1-x0)*step/steps
                        if(sx==px)continue
                        commands.add(EmbroideryPoint(
                            sx,yy,StitchCommand.STITCH,index
                        ))
                        stitchCount++
                        px=sx
                    }
                    lastX=x1
                    lastY=yy
                    prevRow=y
                    prevEndX=x1
                    prevEndY=yy
                    prevLeft=left
                    prevRight=right
                    require(commands.size<=42_000) {
                        "Desenho complexo demais para o simulador."
                    }
                }
            }
        }
        require(stitchCount in 10..30_000 && threads.isNotEmpty()) {
            "Não há pontos suficientes para visualizar a costura."
        }
        commands.add(EmbroideryPoint(
            lastX,lastY,StitchCommand.END,threads.lastIndex
        ))
        val active=commands.filter {
            it.command==StitchCommand.JUMP ||
                it.command==StitchCommand.STITCH
        }
        val design=EmbroideryDesign(
            fileName="IMAGEM_SATIN_VISUAL_SEM_PES.qa",
            format="QA_IMAGE_VISUAL",
            label="Simulação Satin visual — não exportável",
            points=commands,
            bounds=EmbroideryBounds(
                active.minOf {it.xUnits},active.maxOf{it.xUnits},
                active.minOf {it.yUnits},active.maxOf{it.yUnits}
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
        design
    }
}
