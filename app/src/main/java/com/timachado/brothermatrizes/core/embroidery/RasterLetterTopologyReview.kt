package com.timachado.brothermatrizes.core.embroidery

/**
 * Diagnostic-only review of letter/symbol topology before choosing Satin,
 * Tatami or running stitch. Every measurement is from the exact selected
 * source mask; nothing here generates, edits or exports needle commands.
 *
 * 4-neighbor background flood is deliberate: a diagonal foreground contact
 * is reported separately rather than used to close a fragile letter counter.
 */
internal data class RasterLetterTopologyReport(
    val partId: Int,
    val inkPixels: Int,
    val enclosedCounters: Int,
    val smallestCounterPixels: Int,
    val contourCornerCount: Int,
    val ambiguousDiagonalContacts: Int,
    val vectorOutlineValid: Boolean,
    val manualGeometryRequired: Boolean,
    val reviewRequired: Boolean = true,
    val exportPermitted: Boolean = false
) {
    fun guidance(): String {
        val hints = ArrayList<String>()
        if (enclosedCounters > 0) {
            hints += "$enclosedCounters vazado(s) fechado(s)"
            hints += "proteger o centro das letras O/P/B"
        }
        if (smallestCounterPixels in 1..15)
            hints += "vazado menor que 16 px: ampliar e redesenhar"
        if (!vectorOutlineValid || ambiguousDiagonalContacts > 0)
            hints += "junção diagonal/contorno ambíguo: separar manualmente"
        if (contourCornerCount > 12)
            hints += "contorno curvo ou irregular: conferir trilhos por trecho"
        if (hints.isEmpty())
            hints += "forma simples: ainda conferir cobertura e densidade"
        return "Parte $partId • $inkPixels pixels • " +
            hints.joinToString("; ") +
            ". Diagnóstico visual QA, não são pontos nem PES."
    }
}

internal object RasterLetterTopologyReview {
    fun inspect(
        source: RasterImagePixels,
        part: RasterSatinObject
    ): Result<RasterLetterTopologyReport> = runCatching {
        val m = part.mask
        val w = m.maxX - m.minX + 1
        val h = m.maxY - m.minY + 1
        require(
            source.width in 8..512 && source.height in 8..512 &&
                source.argb.size == source.width*source.height &&
                m.pixels.size == source.argb.size &&
                m.minX >= 0 && m.minY >= 0 &&
                m.maxX < source.width && m.maxY < source.height &&
                w in 2..512 && h in 2..512 &&
                w*h <= 512*512 &&
                !RasterEmbroideryApproval.canExportFromImageTool()
        ) { "Imagem ou máscara incompatível com análise de letras." }

        val pixels = BooleanArray(w*h)
        var total = 0
        for (y in 0 until h) for (x in 0 until w) {
            val at = (m.minY+y)*source.width+m.minX+x
            if (m.pixels[at] != 0) continue
            require(source.argb[at] == part.color) {
                "O contorno não pertence à cor original selecionada."
            }
            pixels[y*w+x] = true
            total++
        }
        require(total == part.pixelCount && total >= 15) {
            "Pixels alterados ou elemento pequeno demais para esta revisão."
        }
        fun ink(x:Int,y:Int):Boolean =
            x in 0 until w && y in 0 until h && pixels[y*w+x]

        var diagonal = 0
        for (y in 0 until h-1) for(x in 0 until w-1) {
            if(ink(x,y) && ink(x+1,y+1) &&
                !ink(x+1,y) && !ink(x,y+1)) diagonal++
            if(ink(x+1,y) && ink(x,y+1) &&
                !ink(x,y) && !ink(x+1,y+1)) diagonal++
        }

        val visited = BooleanArray(w*h)
        val queue = IntArray(w*h)
        var counters = 0
        var smallest = Int.MAX_VALUE
        for (root in pixels.indices) {
            if(pixels[root] || visited[root]) continue
            var head = 0
            var tail = 1
            queue[0] = root
            visited[root] = true
            var count = 0
            var exterior = false
            while(head < tail) {
                val p=queue[head++]
                val x=p%w
                val y=p/w
                count++
                if(x==0 || y==0 || x==w-1 || y==h-1) exterior=true
                fun visit(n:Int) {
                    if(n<0 || n>=pixels.size || pixels[n] || visited[n])return
                    visited[n]=true
                    queue[tail++]=n
                }
                if(x>0)visit(p-1)
                if(x+1<w)visit(p+1)
                if(y>0)visit(p-w)
                if(y+1<h)visit(p+w)
            }
            if(!exterior) {
                counters++
                smallest=minOf(smallest,count)
            }
        }
        val traced=RasterVectorContours.trace(m,source.width)
        val outlines=traced.getOrNull()
        val corners=outlines?.sumOf { it.vertices.size } ?: 0
        require(counters<=64) {
            "Número de vazados acima do limite de revisão QA."
        }
        RasterLetterTopologyReport(
            partId=part.id,
            inkPixels=total,
            enclosedCounters=counters,
            smallestCounterPixels=if(counters==0) 0 else smallest,
            contourCornerCount=corners,
            ambiguousDiagonalContacts=diagonal,
            vectorOutlineValid=traced.isSuccess,
            manualGeometryRequired=traced.isFailure || diagonal>0 ||
                (counters>0 && smallest<16) || corners>12
        )
    }
}
