package com.timachado.brothermatrizes.core.embroidery

/**
 * Side-by-side Premium QA laboratory ONLY. Real PNG alpha boundaries produce
 * actual STITCH/JUMP/TRIM points via the official vector generator. No
 * automatic fill, no photographs, no physical Brother approval.
 * The release source set supplies a fail-closed stub.
 */
object PremiumImagePhysicalLab {
    private data class Edge(val from: Int, val to: Int)

    fun propose(
        width: Int, height: Int, argb: IntArray,
        chosenWidthMm: Float, hoop: HoopProfile
    ): Result<EmbroideryDesign> = runCatching {
        require(width in 8..160 && height in 8..160 &&
            argb.size == width * height &&
            chosenWidthMm.isFinite() && chosenWidthMm in 10f..100f &&
            chosenWidthMm * height / width <= hoop.usableHeightMm &&
            chosenWidthMm <= hoop.usableWidthMm
        ) { "Use PNG transparente de até 160 px, 10–100 mm e dentro do bastidor." }
        val visible = BooleanArray(argb.size) { (argb[it] ushr 24) >= 208 }
        val ink = visible.count { it }
        require(ink in 24..18_000 && ink < argb.size &&
            !visible[0] && !visible[width-1] &&
            !visible[(height-1)*width] && !visible.last()
        ) { "Remova o fundo da imagem; este laboratório aceita silhuetas PNG transparentes." }
        fun filled(x: Int,y: Int): Boolean =
            x in 0 until width && y in 0 until height && visible[y*width+x]
        fun key(x:Int,y:Int):Int = y * (width+1) + x
        fun point(v:Int) = VectorPoint(
            (v % (width+1)).toFloat(), (v / (width+1)).toFloat()
        )
        // A binary raster often has diagonal-only pixel contact (especially
        // antialiased text). It creates two incoming and two outgoing edges
        // at the same geometric vertex. That is NOT an open outline.
        // Pair each incoming edge with its RIGHTMOST outgoing turn: for a
        // clockwise pixel boundary this keeps 4-connected ink islands
        // separate instead of stitching diagonally across transparency.
        // Preserve all source boundaries; no dilation, deletion or invented
        // connecting bridge is permitted.
        val boundaryEdges = ArrayList<Edge>()
        val outgoing = HashMap<Int,MutableList<Int>>()
        val incoming = HashMap<Int,Int>()
        fun edge(a:Int,b:Int) {
            require(a!=b && boundaryEdges.size < 25_000) {
                "Contorno excessivamente complexo para este laboratório."
            }
            val id=boundaryEdges.size
            boundaryEdges+=Edge(a,b)
            outgoing.getOrPut(a){ArrayList(2)}.add(id)
            incoming[b]=(incoming[b]?:0)+1
        }
        for (y in 0 until height) for (x in 0 until width) {
            if (!filled(x,y)) continue
            if (!filled(x,y-1)) edge(key(x,y),key(x+1,y))
            if (!filled(x+1,y)) edge(key(x+1,y),key(x+1,y+1))
            if (!filled(x,y+1)) edge(key(x+1,y+1),key(x,y+1))
            if (!filled(x-1,y)) edge(key(x,y+1),key(x,y))
        }
        val edgeCount=boundaryEdges.size
        require(edgeCount in 12..25_000 &&
            outgoing.keys == incoming.keys &&
            outgoing.all { (node, exits) ->
                exits.size in 1..2 && incoming[node]==exits.size
            }
        ) { "Borda alfa aberta ou topologia inválida." }

        // Build a permutation over directed boundary edges. Rightmost exit
        // is deterministic at a shared corner; its mate takes the other
        // exit. Thus each edge participates in exactly one closed contour.
        fun turnScore(from:Edge,to:Edge):Int {
            val ax=from.to%(width+1)-from.from%(width+1)
            val ay=from.to/(width+1)-from.from/(width+1)
            val bx=to.to%(width+1)-to.from%(width+1)
            val by=to.to/(width+1)-to.from/(width+1)
            val cross=ax*by-ay*bx
            val dot=ax*bx+ay*by
            return when {
                cross>0 -> 3    // right turn around the source ink
                dot>0 -> 2      // straight
                cross<0 -> 1    // left turn
                else -> 0       // reversals are not allowed
            }
        }
        val nextEdge=IntArray(edgeCount)
        boundaryEdges.forEachIndexed { id, segment ->
            val exits=outgoing[segment.to]
                ?: error("Contorno sem segmento de saída.")
            val chosen=exits.maxByOrNull { turnScore(segment,boundaryEdges[it]) }
                ?: error("Contorno sem continuação.")
            require(turnScore(segment,boundaryEdges[chosen])>0) {
                "Borda tenta voltar sobre o próprio pixel."
            }
            nextEdge[id]=chosen
        }
        // Two incoming branches may never use the same outgoing edge.
        val arrivalCount=IntArray(edgeCount)
        for (following in nextEdge) arrivalCount[following]++
        require(arrivalCount.all { it==1 }) {
            "Contorno duplicado no ponto de contato diagonal."
        }

        val visited=BooleanArray(edgeCount)
        val loops=ArrayList<VectorPath>()
        for (initial in boundaryEdges.indices) {
            if (visited[initial]) continue
            val vertices=ArrayList<Int>()
            var current=initial
            do {
                require(!visited[current]) {
                    "A sequência de contorno retornou a um trecho já visitado."
                }
                visited[current]=true
                vertices+=boundaryEdges[current].from
                current=nextEdge[current]
                require(vertices.size<=edgeCount) {
                    "Contorno com fechamento inválido."
                }
            } while(current!=initial)
            require(vertices.size>=4) {
                "Contorno menor que quatro segmentos."
            }
            val turns=ArrayList<VectorPoint>()
            for (i in vertices.indices) {
                val prev=point(vertices[(i+vertices.size-1)%vertices.size])
                val here=point(vertices[i])
                val next=point(vertices[(i+1)%vertices.size])
                val ax=here.x-prev.x
                val ay=here.y-prev.y
                val bx=next.x-here.x
                val by=next.y-here.y
                if(ax*by!=ay*bx) turns+=here
            }
            require(turns.size in 4..3000) {
                "Contorno excede o limite de detalhes para simulação."
            }
            loops+=VectorPath(turns,closed=true)
            require(loops.size<=256) {
                "Mais de 256 contornos. Separe a imagem em elementos."
            }
        }
        require(visited.all { it }) {
            "Existem partes do contorno que não foram analisadas."
        }
        val draft=VectorMatrixGenerator.generate(
            VectorArtwork(loops,label="LABORATORIO_IMG_CONTORNO"),
            VectorMatrixOptions(
                widthMm=chosenWidthMm,
                stitchStyle=TextStitchStyle.RUNNING,
                runningStitchLengthMm=2f,
                color=0xDFAF56,
                outputFormat="PES",
                hoopProfile=hoop,
                enforceHoop=true
            )
        ).getOrThrow()
        // Complex artwork can be SIMULATED to reveal omissions before a
        // file is considered for physical testing. PES remains separately
        // limited to <=3500 stitches by pesForBench.
        require(draft.stitchCount in 10..15_000 &&
            draft.colorChanges==0 &&
            draft.threadColors.size==1 &&
            draft.points.lastOrNull()?.command==StitchCommand.END &&
            draft.points.count { it.command==StitchCommand.JUMP }==loops.size &&
            HoopValidator.validate(draft,hoop).fits
        ) { "Pontos ou dimensões excedem o escopo da simulação de laboratório." }
        EmbroideryStressPolicy.requireGeneratedSafe(draft)
        draft
    }

    fun pesForBench(design:EmbroideryDesign):Result<ConvertedMatrix> = runCatching {
        require(design.format=="PES" &&
            design.fileName.startsWith("laboratorio_img_contorno") &&
            design.stitchCount in 10..3500 &&
            design.threadColors.size==1 &&
            design.colorChanges==0 &&
            design.endFound &&
            design.hoopProfile!=null &&
            HoopValidator.validate(design,design.hoopProfile!!).fits &&
            design.points.lastOrNull()?.command==StitchCommand.END &&
            design.sourceBytes.isEmpty()
        ) { "Somente contorno validado do laboratório QA pode gerar PES de teste." }
        MatrixConverter.convert(design,"PES","TESTE_FISICO_NAO_HOMOLOGADO")
            .getOrThrow().also { converted ->
                require(converted.bytes.size>=512 &&
                    converted.bytes.copyOfRange(0,4)
                        .toString(Charsets.US_ASCII)=="#PES"
                ) { "PES de teste não possui cabeçalho válido." }
                val parsed=EmbroideryLoader.loadBytes(converted.fileName,converted.bytes)
                require(parsed is EmbroideryLoadResult.Success &&
                    parsed.design.stitchCount>0
                ) { "Não foi possível reabrir o PES de teste." }
            }
    }
}
