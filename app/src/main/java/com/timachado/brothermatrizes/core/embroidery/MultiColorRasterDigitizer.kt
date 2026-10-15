package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Protótipo offline para logos de até quatro cores.
 *
 * Não digitaliza uma fotografia inteira: pressupõe uma arte recortada e com
 * fundo transparente/uniforme. A cor de fundo é estimada pela borda da imagem.
 * As ilhas de cor recebem pontos de preenchimento alinhados por fileiras,
 * com deslocamento alternado ("Tatami-like"); Satin e underlay profissional
 * continuam fora do escopo desta etapa.
 */
data class MultiColorRasterOptions(
    val widthMm: Float = 60f,
    val maxColors: Int = 2,
    val outputFormat: String = MatrixConverter.preferredBrotherFormat,
    val hoopProfile: HoopProfile? = HoopProfile.H100X100,
    val rowSpacingMm: Float = 0.5f,
    val stitchLengthMm: Float = 2.8f,
    val backgroundTolerance: Int = 72
)

data class SeparatedColorArtwork(
    val colors: List<Int>,
    /** -1 indica transparência/fundo. Outros valores indexam colors. */
    val pixels: IntArray,
    val minX: Int,
    val maxX: Int,
    val minY: Int,
    val maxY: Int
)

object MultiColorRasterDigitizer {
    private const val MAX_STITCHES = 130_000

    fun separate(
        source: RasterImagePixels,
        options: MultiColorRasterOptions
    ): Result<SeparatedColorArtwork> = runCatching {
        validate(source, options)
        val n = source.width * source.height
        val hasAlpha = source.argb.any { it ushr 24 < 128 }
        // Amostrar a borda, em vez de todos os pixels: o logotipo nunca
        // deveria ocupar a borda de uma arte bem recortada.
        val redBorder = ArrayList<Int>()
        val greenBorder = ArrayList<Int>()
        val blueBorder = ArrayList<Int>()
        if (!hasAlpha) {
            for (y in 0 until source.height) {
                for (x in 0 until source.width) {
                    if (y > 1 && y < source.height - 2 &&
                        x > 1 && x < source.width - 2) continue
                    val p = source.argb[y * source.width + x]
                    redBorder += (p ushr 16) and 255
                    greenBorder += (p ushr 8) and 255
                    blueBorder += p and 255
                }
            }
        }
        val bg = if (hasAlpha) 0 else rgb(
            median(redBorder), median(greenBorder), median(blueBorder)
        )
        val active = BooleanArray(n)
        val bins = HashMap<Int, Int>()
        var ink = 0
        for (i in 0 until n) {
            val p = source.argb[i]
            val show = if (hasAlpha) {
                p ushr 24 >= 180
            } else {
                distance(p, bg) >= options.backgroundTolerance
            }
            active[i] = show
            if (show) {
                ink++
                // Blocos RGB de 8 níveis para suavizar ruído JPEG.
                val key = (((p ushr 16) and 255) / 8 shl 10) or
                    ((((p ushr 8) and 255) / 8) shl 5) or
                    ((p and 255) / 8)
                bins[key] = (bins[key] ?: 0) + 1
            }
        }
        require(ink >= 15 && ink.toFloat() / n < 0.73f) {
            "Imagem sem desenho isolado: remova o fundo e recorte somente o logo."
        }
        val sortedBins = bins.entries.sortedByDescending { it.value }
        // Centros de cor distintos, ignorando tons intermediários de anti-aliasing.
        val seeds = ArrayList<Int>()
        for ((bin, count) in sortedBins) {
            if (count < max(3, ink / 500)) continue
            val p = rgb(
                ((bin ushr 10) and 31) * 8 + 4,
                ((bin ushr 5) and 31) * 8 + 4,
                (bin and 31) * 8 + 4
            )
            if (seeds.none { distance(it, p) < 70 }) seeds += p
            if (seeds.size == options.maxColors) break
        }
        require(seeds.isNotEmpty()) {
            "Não foi possível reconhecer as cores do desenho."
        }

        // Agrupamento dos pixels válidos por seus centros de cor.
        // Impede que milhares de tonalidades do JPEG virem milhares de linhas.
        repeat(5) {
            val sumR = LongArray(seeds.size)
            val sumG = LongArray(seeds.size)
            val sumB = LongArray(seeds.size)
            val count = IntArray(seeds.size)
            for (i in 0 until n) {
                if (!active[i]) continue
                val p = source.argb[i]
                val nearest = seeds.indices.minBy { distanceSquared(p, seeds[it]) }
                sumR[nearest] += (p ushr 16) and 255
                sumG[nearest] += (p ushr 8) and 255
                sumB[nearest] += p and 255
                count[nearest]++
            }
            for (i in seeds.indices) {
                if (count[i] > 0) {
                    seeds[i] = rgb(
                        (sumR[i] / count[i]).toInt(),
                        (sumG[i] / count[i]).toInt(),
                        (sumB[i] / count[i]).toInt()
                    )
                }
            }
        }

        val labels = IntArray(n) { -1 }
        val hist = IntArray(seeds.size)
        for (i in 0 until n) {
            if (!active[i]) continue
            val nearest = seeds.indices.minBy { distanceSquared(source.argb[i], seeds[it]) }
            labels[i] = nearest
            hist[nearest]++
        }
        val selected = seeds.indices
            .filter { hist[it] >= max(10, ink / 200) }
            .sortedByDescending { hist[it] }
        require(selected.isNotEmpty()) {
            "Os detalhes são pequenos demais para digitalização."
        }
        val remap = IntArray(seeds.size) { -1 }
        selected.forEachIndexed { index, original -> remap[original] = index }
        var left = source.width
        var right = -1
        var top = source.height
        var bottom = -1
        for (i in labels.indices) {
            val old = labels[i]
            labels[i] = if (old < 0) -1 else remap[old]
            if (labels[i] < 0) continue
            val x = i % source.width
            val y = i / source.width
            left = minOf(left, x)
            right = maxOf(right, x)
            top = minOf(top, y)
            bottom = maxOf(bottom, y)
        }
        require(right - left >= 2 && bottom - top >= 2) {
            "Desenho muito fino. Use arte com mais resolução."
        }
        SeparatedColorArtwork(
            colors = selected.map { seeds[it] },
            pixels = labels,
            minX = left, maxX = right, minY = top, maxY = bottom
        )
    }

    fun generate(
        source: RasterImagePixels,
        options: MultiColorRasterOptions
    ): Result<EmbroideryDesign> = runCatching {
        val art = separate(source, options).getOrThrow()
        val croppedWidth = art.maxX - art.minX + 1
        val croppedHeight = art.maxY - art.minY + 1
        val unitsPerPixel = options.widthMm * 10f / croppedWidth
        val designWidth = croppedWidth * unitsPerPixel
        val designHeight = croppedHeight * unitsPerPixel
        fun xUnit(pixel: Float): Int =
            ((pixel - art.minX + .5f) * unitsPerPixel - designWidth / 2f).roundToInt()
        fun yUnit(pixel: Float): Int =
            (designHeight / 2f - (pixel - art.minY + .5f) * unitsPerPixel).roundToInt()

        val out = ArrayList<EmbroideryPoint>()
        val verticalStep = options.rowSpacingMm * 10f
        val maxStitchLength = options.stitchLengthMm * 10f
        var lastX = 0
        var lastY = 0
        var hasStitched = false

        for (colorIndex in art.colors.indices) {
            var layerStitched = false
            var row = 0
            var yUnitsFromTop = 0f
            val maxYUnits = (art.maxY - art.minY) * unitsPerPixel
            while (yUnitsFromTop <= maxYUnits) {
                val y = (art.minY + yUnitsFromTop / unitsPerPixel)
                    .roundToInt().coerceIn(art.minY, art.maxY)
                val spans = ArrayList<Pair<Int, Int>>()
                var x = art.minX
                while (x <= art.maxX) {
                    while (x <= art.maxX &&
                        art.pixels[y * source.width + x] != colorIndex) x++
                    if (x > art.maxX) break
                    val start = x
                    while (x <= art.maxX &&
                        art.pixels[y * source.width + x] == colorIndex) x++
                    if (x - start >= 2 && (x - start - 1) * unitsPerPixel >= 8f) {
                        spans += start to x - 1
                    }
                }
                val reverse = row % 2 == 1
                val ordered = if (reverse) spans.asReversed() else spans
                for ((left, right) in ordered) {
                    val fromX = xUnit((if (reverse) right else left).toFloat())
                    val toX = xUnit((if (reverse) left else right).toFloat())
                    val atY = yUnit(y.toFloat())

                    if (!layerStitched) {
                        if (hasStitched) {
                            out += EmbroideryPoint(lastX, lastY, StitchCommand.TRIM, colorIndex - 1)
                            out += EmbroideryPoint(lastX, lastY, StitchCommand.COLOR_CHANGE, colorIndex)
                        }
                        layerStitched = true
                    }
                    // Nunca atravessar um buraco da letra/logotipo bordando:
                    // recomeçar cada faixa com JUMP, mesmo quando a ilha é próxima.
                    if (hasStitched &&
                        hypot((fromX - lastX).toDouble(), (atY - lastY).toDouble()) > 80.0
                    ) {
                        out += EmbroideryPoint(lastX, lastY, StitchCommand.TRIM, colorIndex)
                    }
                    out += EmbroideryPoint(fromX, atY, StitchCommand.JUMP, colorIndex)

                    val delta = toX - fromX
                    val sign = if (delta >= 0) 1 else -1
                    val distance = abs(delta).toFloat()
                    val shift = if (row % 2 == 0) 0f else maxStitchLength * .45f
                    var offset = maxStitchLength - shift
                    while (offset < distance) {
                        out += EmbroideryPoint(
                            fromX + sign * offset.roundToInt(), atY,
                            StitchCommand.STITCH, colorIndex
                        )
                        offset += maxStitchLength
                    }
                    out += EmbroideryPoint(toX, atY, StitchCommand.STITCH, colorIndex)
                    hasStitched = true
                    lastX = toX
                    lastY = atY
                    require(out.size <= MAX_STITCHES) {
                        "Logo muito complexo. Reduza cores ou dimensões."
                    }
                }
                row++
                yUnitsFromTop += verticalStep
            }
            require(layerStitched) {
                "Camada de cor fina demais. Aumente o tamanho do desenho."
            }
        }
        require(hasStitched && out.size > 10) { "Desenho sem pontos suficientes." }
        out += EmbroideryPoint(lastX, lastY, StitchCommand.END, art.colors.lastIndex)
        val useful = out.filter { it.command != StitchCommand.END }
        val format = options.outputFormat.uppercase()
        val matrix = EmbroideryDesign(
            fileName = "imagem-multicor.${format.lowercase()}",
            format = format,
            label = source.label,
            points = out,
            bounds = EmbroideryBounds(
                useful.minOf { it.xUnits }, useful.maxOf { it.xUnits },
                useful.minOf { it.yUnits }, useful.maxOf { it.yUnits }
            ),
            stitchCount = out.count { it.command == StitchCommand.STITCH },
            jumpCount = out.count { it.command == StitchCommand.JUMP },
            colorChanges = out.count { it.command == StitchCommand.COLOR_CHANGE },
            endFound = true,
            sourceBytes = ByteArray(0),
            threadColors = art.colors,
            isModified = true,
            hoopProfile = options.hoopProfile
        )
        MachineFinishing.apply(matrix).design
    }

    private fun validate(source: RasterImagePixels, options: MultiColorRasterOptions) {
        require(source.width in 8..512 && source.height in 8..512 &&
            source.argb.size == source.width * source.height) { "Imagem inválida." }
        require(options.maxColors in 2..4) { "Limite de cores: de 2 a 4." }
        require(options.widthMm in 10f..200f) { "Largura inválida." }
        require(options.rowSpacingMm in .4f..1f) { "Espaçamento inválido." }
        require(options.stitchLengthMm in 1.5f..4f) { "Comprimento de ponto inválido." }
        require(options.backgroundTolerance in 35..125) { "Tolerância de fundo inválida." }
        require(options.outputFormat.uppercase() in MatrixConverter.supportedFormats) {
            "Formato de matriz não suportado."
        }
    }
    private fun median(values: List<Int>): Int =
        values.sorted().let { it[it.size / 2] }
    private fun rgb(r: Int, g: Int, b: Int) =
        (r.coerceIn(0, 255) shl 16) or
            (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    private fun distance(a: Int, b: Int) = sqrt(distanceSquared(a, b).toDouble()).toInt()
    private fun distanceSquared(a: Int, b: Int): Int {
        val dr = ((a ushr 16) and 255) - ((b ushr 16) and 255)
        val dg = ((a ushr 8) and 255) - ((b ushr 8) and 255)
        val db = (a and 255) - (b and 255)
        return dr * dr + dg * dg + db * db
    }
}
