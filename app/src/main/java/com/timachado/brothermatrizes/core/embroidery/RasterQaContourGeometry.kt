package com.timachado.brothermatrizes.core.embroidery

import java.util.Locale

/**
 * Grid-aligned contour candidates from the EXACT imported raster.
 *
 * Stage zero of digitization: these are perimeter coordinates, NOT a
 * running-stitch sequence, satin columns, tatami fill or machine file.
 * Source alpha is unchanged, and the output cannot be used for export.
 *
 * Ink pixels are represented as closed unit squares. A clockwise outer
 * boundary has positive signed area in y-down image coordinates. Counter
 * boundaries have negative area. If diagonal-touch geometry would give
 * a vertex two outgoing edges, fail closed rather than silently join
 * unrelated letters or invent a route through the ambiguous corner.
 */
internal data class RasterQaContourVertex(val x: Int, val y: Int)

internal data class RasterQaVectorLoop(
    val vertices: List<RasterQaContourVertex>,
    val signedTwiceAreaPixels: Long,
    val perimeterPixels: Int
) {
    val isCounter: Boolean get() = signedTwiceAreaPixels < 0L
    val areaPixels: Double get() = kotlin.math.abs(
        signedTwiceAreaPixels.toDouble()
    ) / 2.0
}

internal data class RasterQaContourGeometryReport(
    val source: RasterImagePixels,
    val fingerprint: String,
    val threshold: Int,
    val chosenWidthMm: Float,
    val mmPerPixel: Float,
    val inkPixels: Int,
    val loops: List<RasterQaVectorLoop>,
    val exportPermitted: Boolean = false,
    val requiresOperatorReview: Boolean = true
) {
    val outerContours: Int get() = loops.count { !it.isCounter }
    val counterContours: Int get() = loops.count { it.isCounter }

    fun summary(): String {
        fun fmt(v: Float) = String.format(Locale.US, "%.3f", v)
        return "QA-CONTORNOS-VETORIAIS-V1 | " +
            "imagem=" + source.width + "x" + source.height +
            " | fingerprint=" + fingerprint +
            " | alfa=" + threshold +
            " | largura=" + fmt(chosenWidthMm) + " mm" +
            " | mm/px=" + fmt(mmPerPixel) +
            " | pixels-tinta=" + inkPixels +
            " | contornos-externos=" + outerContours +
            " | vazados=" + counterContours +
            " | segmentos=" + loops.sumOf { it.perimeterPixels } +
            " | GEOMETRIA PARA REVISÃO, NÃO É STITCH/PES. " +
            "Sem decisão sobre direção, densidade, tipo de ponto, " +
            "mínimo físico ou homologação na máquina."
    }

    /** QA overlay only; never a digitized matrix. */
    fun boundaryPreview(): RasterImagePixels {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w*h) { i ->
            if ((source.argb[i] ushr 24) >= threshold)
                0xff939da7.toInt()
            else 0
        }
        // Highlight only retained original foreground pixels; do not
        // wrap x=w into the next raster row at the right image edge.
        fun paintIfInk(x:Int,y:Int,paint:Int) {
            if(x in 0 until w && y in 0 until h) {
                val i=y*w+x
                if((source.argb[i] ushr 24)>=threshold) pixels[i]=paint
            }
        }
        for(loop in loops) {
            val paint=if(loop.isCounter)
                0xffffab51.toInt() else 0xff31c7ee.toInt()
            for(i in 0 until loop.vertices.size-1) {
                val a=loop.vertices[i]
                val b=loop.vertices[i+1]
                if(a.x==b.x) {
                    val y=minOf(a.y,b.y)
                    paintIfInk(a.x-1,y,paint)
                    paintIfInk(a.x,y,paint)
                } else {
                    val x=minOf(a.x,b.x)
                    paintIfInk(x,a.y-1,paint)
                    paintIfInk(x,a.y,paint)
                }
            }
        }
        return RasterImagePixels(w,h,pixels,
            "Prévia QA contornos geométricos — não é bordado")
    }
}

internal object RasterQaContourGeometry {
    fun inspect(
        source: RasterImagePixels,
        chosenWidthMm: Float,
        threshold: Int = 208
    ): Result<RasterQaContourGeometryReport> = runCatching {
        val w=source.width
        val h=source.height
        require(w in 8..512 && h in 8..512 &&
            source.argb.size==w*h &&
            threshold in 64..254 &&
            chosenWidthMm.isFinite() &&
            chosenWidthMm in 10f..300f &&
            !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Geometria QA inválida ou exportação indevidamente autorizada." }

        val ink=BooleanArray(w*h) {
            (source.argb[it] ushr 24)>=threshold
        }
        val count=ink.count { it }
        require(count in 1 until w*h) {
            "Imagem vazia ou sólida: contornos exigem revisão."
        }

        val vertexCount=(w+1)*(h+1)
        val outgoing=IntArray(vertexCount) { -1 }
        val incoming=IntArray(vertexCount)
        val start=ArrayList<Int>()
        val end=ArrayList<Int>()
        fun v(x:Int,y:Int)=y*(w+1)+x
        fun add(x0:Int,y0:Int,x1:Int,y1:Int) {
            val a=v(x0,y0)
            val b=v(x1,y1)
            require(outgoing[a]==-1 && incoming[b]==0) {
                "Contato diagonal ou vértice ambíguo: " +
                    "não conectar duas formas automaticamente."
            }
            require(start.size<200_000) {
                "Contorno excessivamente complexo para QA automático."
            }
            outgoing[a]=start.size
            incoming[b]=1
            start.add(a)
            end.add(b)
        }
        fun isInk(x:Int,y:Int):Boolean =
            x in 0 until w && y in 0 until h && ink[y*w+x]

        for(y in 0 until h)for(x in 0 until w) {
            if(!isInk(x,y))continue
            if(!isInk(x,y-1)) add(x,y,x+1,y)
            if(!isInk(x+1,y)) add(x+1,y,x+1,y+1)
            if(!isInk(x,y+1)) add(x+1,y+1,x,y+1)
            if(!isInk(x-1,y)) add(x,y+1,x,y)
        }
        require(start.isNotEmpty()) {"Nenhuma aresta de contorno encontrada."}
        val used=BooleanArray(start.size)
        val loops=ArrayList<RasterQaVectorLoop>()
        fun xy(v:Int)=RasterQaContourVertex(v%(w+1),v/(w+1))
        for(first in start.indices) {
            if(used[first])continue
            val firstVertex=start[first]
            var current=firstVertex
            val vertices=ArrayList<RasterQaContourVertex>()
            vertices.add(xy(firstVertex))
            var steps=0
            while(true) {
                val edge=outgoing[current]
                require(edge>=0 && !used[edge] && steps<start.size) {
                    "Borda não fecha sem ambiguidades. Revisão manual."
                }
                used[edge]=true
                current=end[edge]
                steps++
                vertices.add(xy(current))
                if(current==firstVertex)break
            }
            require(vertices.size>=5) {
                "Contorno degenerado na máscara."
            }
            var doubleArea=0L
            for(i in 0 until vertices.size-1) {
                val a=vertices[i]
                val b=vertices[i+1]
                doubleArea+=a.x.toLong()*b.y-b.x.toLong()*a.y
            }
            require(doubleArea!=0L) {"Contorno com área nula."}
            loops+=RasterQaVectorLoop(
                vertices=vertices,
                signedTwiceAreaPixels=doubleArea,
                perimeterPixels=steps
            )
        }
        require(used.all { it }) { "Arestas QA desconectadas." }
        val totalSignedArea=loops.sumOf { it.signedTwiceAreaPixels }
        require(totalSignedArea==count.toLong()*2L) {
            "Área dos contornos diverge da imagem fonte."
        }
        var fingerprint=1469598103934665603L
        for(pixel in source.argb) {
            fingerprint=(fingerprint xor
                (pixel.toLong() and 0xffffffffL))*1099511628211L
        }
        RasterQaContourGeometryReport(
            source=source,
            fingerprint=java.lang.Long.toHexString(fingerprint),
            threshold=threshold,
            chosenWidthMm=chosenWidthMm,
            mmPerPixel=chosenWidthMm/w.toFloat(),
            inkPixels=count,
            loops=loops
        )
    }
}
