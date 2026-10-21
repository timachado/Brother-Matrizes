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
        val outgoing = HashMap<Int,Int>()
        val inbound = HashMap<Int,Int>()
        var edges=0
        fun edge(a:Int,b:Int) {
            require(outgoing.putIfAbsent(a,b)==null && inbound.putIfAbsent(b,a)==null) {
                "Contorno com toque diagonal ou ramificação ambígua; separe os detalhes."
            }
            edges++
            require(edges<=14_000) { "Contorno excessivamente complexo." }
        }
        for (y in 0 until height) for (x in 0 until width) {
            if (!filled(x,y)) continue
            if (!filled(x,y-1)) edge(key(x,y),key(x+1,y))
            if (!filled(x+1,y)) edge(key(x+1,y),key(x+1,y+1))
            if (!filled(x,y+1)) edge(key(x+1,y+1),key(x,y+1))
            if (!filled(x-1,y)) edge(key(x,y+1),key(x,y))
        }
        require(edges in 12..14_000 && outgoing.size == inbound.size &&
            outgoing.keys == inbound.keys
        ) { "Borda alfa aberta, conectividade ambígua ou imagem sem contorno." }
        val pending = outgoing.keys.toMutableSet()
        val loops = ArrayList<VectorPath>()
        while (pending.isNotEmpty()) {
            val start=pending.first()
            val vertices=ArrayList<Int>()
            var current=start
            do {
                require(pending.remove(current)) {
                    "Contorno cruzado: existe ponto repetido antes de fechar."
                }
                vertices += current
                current = outgoing[current]
                    ?: error("Borda sem continuação.")
                require(vertices.size<=edges) { "Contorno sem fechamento." }
            } while (current != start)
            require(vertices.size>=4) { "Ilha menor que o permitido." }
            // Keep every true bend, so a diagonal shortcut can never cross
            // an original transparent counter after raster->vector conversion.
            val turns=ArrayList<VectorPoint>()
            for (i in vertices.indices) {
                val prev=point(vertices[(i+vertices.size-1)%vertices.size])
                val here=point(vertices[i])
                val next=point(vertices[(i+1)%vertices.size])
                val ax=here.x-prev.x
                val ay=here.y-prev.y
                val bx=next.x-here.x
                val by=next.y-here.y
                if (ax*by!=ay*bx) turns+=here
            }
            require(turns.size in 4..3000) { "Contorno com cantos excessivos." }
            loops+=VectorPath(turns,closed=true)
            require(loops.size<=8) { "Mais de oito contornos: divida a imagem." }
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
        require(draft.stitchCount in 10..3500 &&
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
            design.fileName.startsWith("LABORATORIO_IMG_CONTORNO") &&
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
