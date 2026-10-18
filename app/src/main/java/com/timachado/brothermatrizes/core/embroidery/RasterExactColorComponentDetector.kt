package com.timachado.brothermatrizes.core.embroidery

/**
 * QA source-of-truth detector for a deliberately narrow set of flat-color
 * logos: no quantization, no color guessing, no discarding small accents.
 * Textured photos require assisted digitization and are rejected.
 */
internal object RasterExactColorComponentDetector {
    private const val MAX_COMPONENTS = RasterQaComponentBudget.MAX_COMPONENTS
    private const val MAX_COLORS = RasterQaComponentBudget.MAX_COLORS
    private const val MAX_PIXELS = 512 * 512

    fun detect(source: RasterImagePixels): Result<List<RasterSatinObject>> =
        runCatching {
            val w = source.width
            val h = source.height
            require(w in 8..512 && h in 8..512 &&
                source.argb.size == w*h && source.argb.size <= MAX_PIXELS &&
                !RasterEmbroideryApproval.canExportFromImageTool()
            ) { "Fonte inadequada para separar cores exatas em QA." }

            val usesAlpha = source.argb.any { it ushr 24 < 180 }
            val background = if (usesAlpha) null else {
                val corner = source.argb[0]
                require(listOf(
                    source.argb[w-1],
                    source.argb[(h-1)*w],
                    source.argb[w*h-1]
                ).all { it == corner }) {
                    "Arte opaca sem fundo uniforme: recorte e remova o fundo."
                }
                corner
            }
            fun foreground(index: Int): Boolean {
                val a = source.argb[index] ushr 24
                if (usesAlpha) {
                    require(a == 0 || a >= 180) {
                        "Transparência parcial pode ocultar contornos finos. " +
                            "Refaça a separação sem perder detalhes."
                    }
                    return a >= 180
                }
                return source.argb[index] != background
            }
            val foreground = BooleanArray(w*h) { foreground(it) }
            val colors = HashSet<Int>()
            for (i in foreground.indices) {
                if (foreground[i]) {
                    colors += source.argb[i]
                    require(colors.size <= MAX_COLORS) {
                        "Mais de quatro tons exatos na origem. " +
                            "Imagem com textura exige reconstrução assistida."
                    }
                }
            }
            require(foreground.count { it } >= 30) {
                "Imagem sem elementos sólidos suficientes."
            }
            val labels = IntArray(w*h)
            val queue = IntArray(w*h)
            val parts = ArrayList<RasterSatinObject>()
            var originalInk = 0
            for (root in labels.indices) {
                if (!foreground[root] || labels[root] != 0) continue
                require(parts.size < MAX_COMPONENTS) {
                    "Mais de 24 elementos: ajuste o recorte antes da montagem QA."
                }
                val id = parts.size+1
                val color = source.argb[root]
                labels[root] = id
                queue[0] = root
                var first = 0
                var tail = 1
                var minX = w
                var minY = h
                var maxX = -1
                var maxY = -1
                while (first < tail) {
                    val position = queue[first++]
                    val x = position%w
                    val y = position/w
                    minX=minOf(minX,x)
                    minY=minOf(minY,y)
                    maxX=maxOf(maxX,x)
                    maxY=maxOf(maxY,y)
                    for (dy in -1..1) for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val xx=x+dx
                        val yy=y+dy
                        if (xx !in 0 until w || yy !in 0 until h) continue
                        val next=yy*w+xx
                        if(foreground[next] && labels[next]==0 &&
                            source.argb[next]==color) {
                            labels[next]=id
                            queue[tail++]=next
                        }
                    }
                }
                require(tail >= 15) {
                    "Detalhe pequeno de $tail pixel(is) na arte: " +
                        "nenhum ponto pode ser omitido; amplie e revise."
                }
                originalInk += tail
                val localId = id
                val pixels = IntArray(w*h) {
                    if(labels[it]==localId) 0 else -1
                }
                parts += RasterSatinObject(
                    id=id,color=color,pixelCount=tail,
                    mask=SeparatedColorArtwork(
                        colors=listOf(color),pixels=pixels,
                        minX=minX,maxX=maxX,minY=minY,maxY=maxY
                    )
                )
            }
            require(parts.size in 2..MAX_COMPONENTS &&
                parts.sumOf { it.pixelCount } == originalInk &&
                originalInk == foreground.count { it }
            ) {
                "Nem todas as partes da imagem foram preservadas."
            }
            parts
        }
}
