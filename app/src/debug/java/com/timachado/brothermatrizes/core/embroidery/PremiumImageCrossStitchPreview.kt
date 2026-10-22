package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.roundToInt

/**
 * Independent cross-stitch image preview. Converts each sufficiently solid
 * source-image cell into two diagonals (an X) using JUMP and STITCH, grouped
 * by source palette colors. Nothing here is copied from Artspira or exported
 * to PES; real machine clearance needs independent checks and stitch-out.
 */
internal object PremiumImageCrossStitchPreview {
    private data class Cell(val x:Int,val y:Int,val color:Int)

    fun create(
        width:Int,height:Int,argb:IntArray,
        widthMm:Float,hoop:HoopProfile,maxColors:Int=4
    ):Result<EmbroideryDesign> = runCatching {
        require(width in 8..160 && height in 8..160 &&
            argb.size==width*height &&
            widthMm.isFinite() && widthMm in 10f..100f &&
            maxColors in 2..8
        ) { "Imagem inválida para ponto cruz." }
        val heightMm=widthMm*height/width
        require(widthMm<=hoop.usableWidthMm &&
            heightMm<=hoop.usableHeightMm
        ) { "Desenho excede a área útil do bastidor." }
        val palette=PremiumImageDigitizedFillPreview.dominantColors(
            argb,maxColors
        )
        val cellSize=if(maxOf(width,height)>=120)4 else 3
        val cells=ArrayList<Cell>()
        for(y in 0 until height step cellSize)
            for(x in 0 until width step cellSize) {
                val xe=minOf(x+cellSize,width)
                val ye=minOf(y+cellSize,height)
                val counts=IntArray(palette.size)
                var opaque=0
                for(py in y until ye)for(px in x until xe) {
                    val pixel=argb[py*width+px]
                    if((pixel ushr 24)<176)continue
                    val chosen=palette.indices.minByOrNull {
                        PremiumImageDigitizedFillPreview.distance(
                            pixel and 0xffffff,palette[it]
                        )
                    }!!
                    counts[chosen]++
                    opaque++
                }
                val area=(xe-x)*(ye-y)
                if(opaque*5<area*4)continue
                val family=counts.indices.maxByOrNull {counts[it]}!!
                if(counts[family]*5<area*4)continue
                cells+=Cell(x,y,family)
            }
        require(cells.size in 5..4000) {
            "Ponto cruz: reduza a quantidade de detalhes ou importe PNG mais definido."
        }
        // Simulate top-to-bottom per color, keeping each needle travel
        // separate; never claim a continuous sewn bridge between crosses.
        val commands=ArrayList<EmbroideryPoint>()
        val colors=ArrayList<Int>()
        var stitches=0
        var jumps=0
        var changes=0
        var lastX=0
        var lastY=0
        fun ux(x:Float)=((x/width-.5f)*widthMm*10).roundToInt()
        fun uy(y:Float)=((.5f-y/height)*heightMm*10).roundToInt()
        for(family in palette.indices) {
            val selected=cells.filter {it.color==family}
            if(selected.isEmpty())continue
            val index=colors.size
            if(colors.isNotEmpty()) {
                commands+=EmbroideryPoint(lastX,lastY,
                    StitchCommand.COLOR_CHANGE,index)
                changes++
            }
            colors+=palette[family]
            for(cell in selected) {
                val x=cell.x.toFloat()
                val y=cell.y.toFloat()
                val right=minOf(cell.x+cellSize,width).toFloat()
                val bottom=minOf(cell.y+cellSize,height).toFloat()
                // Margin retains separation between adjacent X cells.
                val margin=0.15f
                val leftUnits=ux(x+margin)
                val rightUnits=ux(right-margin)
                val topUnits=uy(y+margin)
                val bottomUnits=uy(bottom-margin)
                // Within one cluster, jumping to the next X is not a
                // reason to cut the thread on every single cell.
                // Frequent unnecessary cuts made the earlier simulation
                // unrealistic and hide discontinuities in the source.
                if(stitches>0 &&
                    kotlin.math.abs(lastX-leftUnits)+
                        kotlin.math.abs(lastY-topUnits)>60) {
                    commands+=EmbroideryPoint(lastX,lastY,
                        StitchCommand.TRIM,index)
                }
                commands+=EmbroideryPoint(
                    leftUnits,topUnits,StitchCommand.JUMP,index)
                jumps++
                commands+=EmbroideryPoint(
                    rightUnits,bottomUnits,StitchCommand.STITCH,index)
                stitches++
                commands+=EmbroideryPoint(
                    rightUnits,topUnits,StitchCommand.JUMP,index)
                jumps++
                commands+=EmbroideryPoint(
                    leftUnits,bottomUnits,StitchCommand.STITCH,index)
                stitches++
                lastX=leftUnits
                lastY=bottomUnits
                require(commands.size<=28_000)
            }
        }
        require(stitches>=10 && colors.isNotEmpty())
        commands+=EmbroideryPoint(lastX,lastY,
            StitchCommand.END,colors.lastIndex)
        val active=commands.filter {
            it.command==StitchCommand.JUMP ||
                it.command==StitchCommand.STITCH
        }
        val design=EmbroideryDesign(
            fileName="PONTO_CRUZ_IMAGEM_QA_SEM_PES.qa",
            format="QA_IMAGE_VISUAL",
            label="Ponto cruz simulado a partir da imagem • sem PES",
            points=commands,
            bounds=EmbroideryBounds(
                active.minOf{it.xUnits},active.maxOf{it.xUnits},
                active.minOf{it.yUnits},active.maxOf{it.yUnits}
            ),
            stitchCount=stitches,
            jumpCount=jumps,
            colorChanges=changes,
            endFound=true,
            sourceBytes=ByteArray(0),
            threadColors=colors,
            isModified=true,
            hoopProfile=hoop
        )
        EmbroideryStressPolicy.requireGeneratedSafe(design)
        require(HoopValidator.validate(design,hoop).fits)
        require(MatrixConverter.convert(design,"PES").isFailure)
        design
    }
}
