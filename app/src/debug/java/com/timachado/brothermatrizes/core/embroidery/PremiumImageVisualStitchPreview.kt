package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A simulation-only, coarse raster-to-needle SCAN preview for artwork whose
 * real silhouette cannot safely be digitized as a closed running contour.
 *
 * It shows actual STITCH/JUMP/COLOR_CHANGE command playback in the existing
 * SimulatorScreen, but deliberately does NOT claim to be finished embroidery:
 * no underlay, pull compensation, fabric behavior, durable fill, or machine
 * approval. It must never be converted to PES/DST/JEF.
 *
 * No morphological closure, dilation, inferred bridge or pixel repainting.
 * Transparent holes remain travel regions, never STITCH regions.
 */
internal object PremiumImageVisualStitchPreview {
    private val palette = listOf(
        0x222222, // very dark
        0xF3F0E8, // pale/white
        0xE0AC4C, // orange/gold
        0xD9544D, // red
        0x48906B, // green
        0x518BC3, // blue
        0x9174B4  // violet/magenta
    )

    private fun colorFamily(pixel: Int): Int {
        val r = (pixel ushr 16) and 255
        val g = (pixel ushr 8) and 255
        val b = pixel and 255
        val hi = maxOf(r, g, b)
        val lo = minOf(r, g, b)
        if (hi - lo < 35) return if ((r + g + b) / 3 >= 148) 1 else 0
        return when {
            r >= g + 24 && g >= b + 10 -> 2
            r > g + 20 && r > b + 20 -> 3
            g > r + 18 && g > b + 10 -> 4
            b > r + 18 && b > g + 12 -> 5
            else -> 6
        }
    }

    fun create(
        width: Int,
        height: Int,
        argb: IntArray,
        widthMm: Float,
        hoop: HoopProfile
    ): Result<EmbroideryDesign> = runCatching {
        require(width in 8..160 && height in 8..160 &&
            argb.size == width * height &&
            widthMm.isFinite() && widthMm in 10f..100f
        ) { "A prévia aceita PNG de até 160 px e 10–100 mm." }
        val heightMm = widthMm * height / width
        require(widthMm <= hoop.usableWidthMm &&
            heightMm <= hoop.usableHeightMm
        ) { "A imagem ultrapassa a área útil do bastidor." }

        val groups = IntArray(argb.size) { i ->
            if ((argb[i] ushr 24) >= 160) colorFamily(argb[i]) else -1
        }
        val ink = groups.count { it >= 0 }
        require(ink in 16 until groups.size) {
            "A simulação requer tinta e fundo transparente. " +
                "Fotografias sem recorte não geram pontos de bordado confiáveis."
        }
        val frequency = IntArray(palette.size)
        for (group in groups) if (group >= 0) frequency[group]++
        val selected = frequency.indices.filter { frequency[it] >= 2 }
        require(selected.isNotEmpty()) { "Sem regiões de linha para simular." }

        fun needleX(x: Float) =
            ((x / width - .5f) * widthMm * 10f).roundToInt()
        fun needleY(y: Float) =
            ((.5f - y / height) * heightMm * 10f).roundToInt()

        val commands = ArrayList<EmbroideryPoint>()
        val threads = ArrayList<Int>()
        var stitches = 0
        var jumps = 0
        var changes = 0
        var previousX = 0
        var previousY = 0
        var hasStitches = false

        // Every 1–2 source rows become a discrete simulated pass. No stitches
        // bridge separated letter glyphs, islands or transparent counters.
        val rowStride = if (height > 110) 2 else 1
        for (family in selected) {
            var familyUsed = false
            for (y in 0 until height step rowStride) {
                var x = 0
                while (x < width) {
                    if (groups[y * width + x] != family) {
                        x++
                        continue
                    }
                    val left = x
                    while (x < width && groups[y * width + x] == family) x++
                    val right = x - 1
                    if (right - left < 1) continue

                    val x0 = needleX(left + .5f)
                    val x1 = needleX(right + .5f)
                    val yy = needleY(y + .5f)
                    if (x0 == x1) continue
                    if (!familyUsed) {
                        val colorIndex = threads.size
                        require(colorIndex < 7)
                        if (hasStitches) {
                            commands.add(EmbroideryPoint(
                                previousX, previousY,
                                StitchCommand.COLOR_CHANGE, colorIndex
                            ))
                            changes++
                        }
                        threads.add(palette[family])
                        familyUsed = true
                    }
                    val colorIndex = threads.size - 1
                    // Conspicuous separation between disconnected raster
                    // runs, unlike an erroneous straight sewn diagonal.
                    if (hasStitches &&
                        hypot((x0-previousX).toDouble(),
                            (yy-previousY).toDouble()) > 80.0) {
                        commands.add(EmbroideryPoint(
                            previousX, previousY, StitchCommand.TRIM,colorIndex
                        ))
                    }
                    commands.add(EmbroideryPoint(x0,yy,StitchCommand.JUMP,colorIndex))
                    jumps++
                    val span = abs(x1-x0)
                    val parts = ((span + 19) / 20).coerceAtLeast(1)
                    for (step in 1..parts) {
                        val tx = x0 + (x1-x0) * step / parts
                        commands.add(EmbroideryPoint(tx,yy,StitchCommand.STITCH,colorIndex))
                        stitches++
                    }
                    hasStitches = true
                    previousX = x1
                    previousY = yy
                    require(commands.size <= 35_000) {
                        "Imagem muito complexa para reprodução de pontos no celular."
                    }
                }
            }
        }
        require(stitches in 10..15_000 && commands.isNotEmpty() &&
            threads.isNotEmpty()
        ) { "Sem pontos suficientes para uma prévia de costura." }
        commands.add(EmbroideryPoint(
            previousX,previousY,StitchCommand.END,threads.size-1
        ))
        val locations = commands.filter {
            it.command == StitchCommand.STITCH ||
                it.command == StitchCommand.JUMP
        }
        val result = EmbroideryDesign(
            fileName = "SIMULACAO_VISUAL_SEM_PES.qa",
            format = "QA_IMAGE_VISUAL",
            label = "Estudo visual de pontos — NÃO exportável",
            points = commands,
            bounds = EmbroideryBounds(
                locations.minOf { it.xUnits }, locations.maxOf { it.xUnits },
                locations.minOf { it.yUnits }, locations.maxOf { it.yUnits }
            ),
            stitchCount = stitches,
            jumpCount = jumps,
            colorChanges = changes,
            endFound = true,
            sourceBytes = ByteArray(0),
            threadColors = threads,
            isModified = true,
            hoopProfile = hoop,
            sourceYAxisDown = false
        )
        EmbroideryStressPolicy.requireGeneratedSafe(result)
        require(HoopValidator.validate(result,hoop).fits)
        result
    }
}
