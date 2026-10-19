package com.timachado.brothermatrizes.core.embroidery

/**
 * Read-only QA evidence from the ACTUAL Android-imported pixels, not a
 * screenshot or a hypothetical reconstructed logo. No raw pixels are copied
 * into the text report, and no STITCH/PES is generated.
 *
 * Detects a dangerous case missed by a weak-alpha-only contour check:
 * two distinct fully opaque cores joined by pixels with alpha 180..254.
 * Such a connection can turn two separate letters into one component.
 */
internal data class RasterSourceTopologyReport(
    val width: Int,
    val height: Int,
    val transparent: Int,
    val weakAlpha: Int,
    val strongAlpha: Int,
    val fullyOpaque: Int,
    val opaqueCores: Int,
    val thresholdIslands: Int,
    val highAlphaBridges: Int,
    val unanchoredThresholdIslands: Int,
    val fingerprint: String,
    val reviewRequired: Boolean = true,
    val exportPermitted: Boolean = false
) {
    val highAlphaTopologySafe: Boolean get() =
        highAlphaBridges == 0 && unanchoredThresholdIslands == 0

    /**
     * Fully opaque core topology is meaningful only when core coverage is
     * substantial. Sparse alpha=255 peaks in a translucent illustration can
     * give false bridges and false "islands without opaque core" warnings.
     */
    val opaqueAnchorsReliable: Boolean get() =
        fullyOpaque >= 64 && (fullyOpaque+strongAlpha)>0 &&
            fullyOpaque.toDouble()/(fullyOpaque+strongAlpha) >= .05

    fun summary(): String =
        "Diagnóstico QA da imagem importada: ${width}x${height} px; " +
            "transparência total=${transparent}; alfa 1..179=${weakAlpha}; " +
            "alfa 180..254=${strongAlpha}; tinta opaca=${fullyOpaque}; " +
            "núcleos opacos=${opaqueCores}; ilhas alfa >=180=" +
            "${thresholdIslands}; conexões por alfa forte=" +
            "${highAlphaBridges}; ilhas sem núcleo opaco=" +
            "${unanchoredThresholdIslands}; fingerprint=${fingerprint}. " +
            (if (!opaqueAnchorsReliable)
                "Núcleos alfa 255 insuficientes: essas ligações e ilhas " +
                    "são indicadores INCONCLUSIVOS, não defeitos provados. " +
                    "Compare os limiares de alfa e revise cada letra. "
             else if (highAlphaTopologySafe)
                "Nenhuma união entre núcleos opacos detectada. "
             else
                "Possível junção ou detalhe perdido entre núcleos opacos: " +
                    "revisão manual obrigatória. ") +
            "Esta é uma análise local da foto, NÃO é matriz nem PES."
}

internal object RasterSourceTopologyDiagnostics {
    fun inspect(source: RasterImagePixels): Result<RasterSourceTopologyReport> =
        runCatching {
            val w = source.width
            val h = source.height
            require(w in 8..512 && h in 8..512 &&
                source.argb.size == w*h &&
                !RasterEmbroideryApproval.canExportFromImageTool()
            ) { "Imagem inválida para diagnóstico de topologia QA." }

            val n = w*h
            val alpha = IntArray(n)
            var transparent = 0
            var weak = 0
            var strong = 0
            var opaque = 0
            var fingerprint = 1469598103934665603L
            for (i in 0 until n) {
                val value = source.argb[i]
                fingerprint = (fingerprint xor
                    (value.toLong() and 0xffffffffL)) * 1099511628211L
                alpha[i] = value ushr 24
                when(alpha[i]) {
                    0 -> transparent++
                    in 1..179 -> weak++
                    in 180..254 -> strong++
                    else -> opaque++
                }
            }

            // Two 8-connected segmentations. The alpha>=180 image is the
            // provisional flat silhouette; alpha==255 cores are its anchors.
            fun label(threshold: Int): Pair<IntArray,Int> {
                val labels = IntArray(n)
                val queue = IntArray(n)
                var count = 0
                for (start in 0 until n) {
                    if (alpha[start] < threshold || labels[start] != 0) continue
                    count++
                    labels[start] = count
                    queue[0] = start
                    var head = 0
                    var end = 1
                    while (head < end) {
                        val pos = queue[head++]
                        val x = pos%w
                        val y = pos/w
                        for (dy in -1..1) for (dx in -1..1) {
                            if (dx==0 && dy==0) continue
                            val xx = x+dx
                            val yy = y+dy
                            if (xx !in 0 until w || yy !in 0 until h) continue
                            val next = yy*w+xx
                            if (alpha[next] >= threshold && labels[next] == 0) {
                                labels[next] = count
                                queue[end++] = next
                            }
                        }
                    }
                }
                return labels to count
            }
            val (cores, coreCount) = label(255)
            val (solid, solidCount) = label(180)
            val anchors = IntArray(solidCount + 1)
            val bridgeIslands = BooleanArray(solidCount + 1)
            for (i in 0 until n) {
                val island = solid[i]
                val core = cores[i]
                if (island == 0 || core == 0) continue
                if (anchors[island] == 0) anchors[island] = core
                else if (anchors[island] != core) bridgeIslands[island] = true
            }
            val bridged = bridgeIslands.count { it }
            val unanchored = (1..solidCount).count { anchors[it] == 0 }
            RasterSourceTopologyReport(
                width=w,height=h,transparent=transparent,weakAlpha=weak,
                strongAlpha=strong,fullyOpaque=opaque,
                opaqueCores=coreCount,thresholdIslands=solidCount,
                highAlphaBridges=bridged,
                unanchoredThresholdIslands=unanchored,
                fingerprint=java.lang.Long.toHexString(fingerprint)
            )
        }
}
