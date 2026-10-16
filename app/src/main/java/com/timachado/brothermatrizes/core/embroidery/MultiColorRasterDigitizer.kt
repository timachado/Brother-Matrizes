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
/** Tipos de costura para cada cor isolada da arte. */
enum class RasterLayerStitchMode {
    FILL,
    SATIN_STRAIGHT,
    SATIN_CURVED,
    MANUAL_SATIN
}

/** Edição não destrutiva: índice da cor detectada, ordem, visibilidade e parâmetros locais. */
data class ColorLayerEdit(
    val sourceIndex: Int,
    val enabled: Boolean = true,
    val threadColor: Int? = null,
    val rowSpacingMm: Float? = null,
    val stitchMode: RasterLayerStitchMode = RasterLayerStitchMode.FILL,
    val satinPullCompensationMm: Float = 0.15f,
    val satinCenterUnderlay: Boolean = true,
    val manualSections: List<ManualSatinSection> = emptyList()
)

data class MultiColorRasterOptions(
    val widthMm: Float = 60f,
    val maxColors: Int = 2,
    val outputFormat: String = MatrixConverter.preferredBrotherFormat,
    val hoopProfile: HoopProfile? = HoopProfile.H100X100,
    val rowSpacingMm: Float = 0.5f,
    val stitchLengthMm: Float = 2.8f,
    val backgroundTolerance: Int = 72,
    val layers: List<ColorLayerEdit> = emptyList(),
    /** Only for image-to-bordado QA; classic raster generation remains unchanged. */
    val preserveFineDetails: Boolean = false,
    /** Preview only: Satin columns with vertical needle direction, never FILL. */
    val verticalSatinPreviewOnly: Boolean = false
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
        // In experimental QA only: verify that a small color actually forms
        // a compact 8-connected region. A scattered camera-noise bin with
        // six pixels must NOT become a new embroidery thread.
        val compactFineBins = HashSet<Int>()
        if (options.preserveFineDetails) {
            fun binOf(p: Int): Int =
                ((((p ushr 16) and 255) / 8) shl 10) or
                    (((((p ushr 8) and 255) / 8)) shl 5) or
                    ((p and 255) / 8)
            val visited = BooleanArray(n)
            val queue = IntArray(n)
            for (root in 0 until n) {
                if (!active[root] || visited[root]) continue
                val bin = binOf(source.argb[root])
                if (bin in compactFineBins) continue
                var head = 0
                var tail = 1
                visited[root] = true
                queue[0] = root
                while (head < tail) {
                    val i = queue[head++]
                    val x = i % source.width
                    val y = i / source.width
                    for (dy in -1..1) for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x+dx
                        val ny = y+dy
                        if (nx !in 0 until source.width ||
                            ny !in 0 until source.height) continue
                        val next = ny*source.width+nx
                        if (active[next] && !visited[next] &&
                            binOf(source.argb[next]) == bin) {
                            visited[next] = true
                            queue[tail++] = next
                        }
                    }
                }
                if (tail >= 4) compactFineBins += bin
            }
        }
        // Keep the commercial/default palette seeding exactly as before.
        // QA first selects substantial colors, then uses any remaining
        // slots for coherent accents of >=4 connected pixels.
        val seeds = ArrayList<Int>()
        val normalSeedThreshold = max(3, ink / 500)
        val primaryThreshold = max(10, ink / 200)
        fun seed(bin: Int) {
            val p = rgb(
                ((bin ushr 10) and 31) * 8 + 4,
                ((bin ushr 5) and 31) * 8 + 4,
                (bin and 31) * 8 + 4
            )
            if (seeds.size < options.maxColors &&
                seeds.none { distance(it, p) < 70 }) seeds += p
        }
        if (options.preserveFineDetails) {
            for ((bin, count) in sortedBins) {
                if (count >= primaryThreshold) seed(bin)
                if (seeds.size == options.maxColors) break
            }
            if (seeds.size < options.maxColors) {
                for ((bin, count) in sortedBins) {
                    if (count >= 4 && bin in compactFineBins) seed(bin)
                    if (seeds.size == options.maxColors) break
                }
            }
        } else {
            for ((bin, count) in sortedBins) {
                if (count >= normalSeedThreshold) seed(bin)
                if (seeds.size == options.maxColors) break
            }
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
            .filter { hist[it] >= if (options.preserveFineDetails) 4
                else max(10, ink / 200) }
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
        // Correct a pixel erroneously assigned to another thread color *inside*
        // a solid neighborhood (e.g., orange camera noise on white letters).
        // Never bridge a transparent gap, erase isolated strokes or alter edges.
        val cleaned = RasterPaletteFidelity.cleanIsolatedColorBleed(
            labels, source.width, source.height, selected.size
        )
        require(right - left >= 2 && bottom - top >= 2) {
            "Desenho muito fino. Use arte com mais resolução."
        }
        SeparatedColorArtwork(
            colors = selected.map { seeds[it] },
            pixels = cleaned,
            minX = left, maxX = right, minY = top, maxY = bottom
        )
    }

    fun generate(
        source: RasterImagePixels,
        options: MultiColorRasterOptions
    ): Result<EmbroideryDesign> =
        separate(source, options).fold(
            onSuccess = { generateSeparated(source, it, options) },
            onFailure = { Result.failure(it) }
        )

    /** Reutiliza a separação em cache do editor ao ajustar cores ou ordem. */
    fun generateSeparated(
        source: RasterImagePixels,
        art: SeparatedColorArtwork,
        options: MultiColorRasterOptions
    ): Result<EmbroideryDesign> = runCatching {
        validate(source, options)
        require(art.pixels.size == source.width * source.height &&
            art.colors.isNotEmpty() && art.colors.size <= options.maxColors &&
            art.minX >= 0 && art.minY >= 0 &&
            art.maxX < source.width && art.maxY < source.height &&
            art.minX <= art.maxX && art.minY <= art.maxY) {
            "Camadas de imagem inválidas."
        }
        val layers = if (options.layers.isEmpty()) {
            art.colors.indices.map { ColorLayerEdit(sourceIndex = it) }
        } else options.layers
        require(layers.size == art.colors.size &&
            layers.map { it.sourceIndex }.toSet() == art.colors.indices.toSet()) {
            "Lista de camadas diferente das cores detectadas."
        }
        if (options.verticalSatinPreviewOnly) {
            require(layers.all { it.stitchMode == RasterLayerStitchMode.SATIN_STRAIGHT &&
                !it.satinCenterUnderlay }) {
                "Prévia vertical permite apenas Satin reto sem underlay horizontal."
            }
        }
        layers.forEach { layer ->
            require(layer.threadColor == null || layer.threadColor in 0..0xFFFFFF) {
                "Cor de linha inválida."
            }
            require(layer.rowSpacingMm == null || layer.rowSpacingMm in 0.4f..1f) {
                "Espaçamento de pontos inválido."
            }
            require(layer.satinPullCompensationMm in 0f..0.3f) {
                "Compensação Satin deve ficar entre 0 e 0,3 mm."
            }
        }
        val activeLayers = layers.filter { it.enabled }
        require(activeLayers.isNotEmpty()) {
            "Ative pelo menos uma camada antes de gerar a matriz."
        }
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
        val maxStitchLength = options.stitchLengthMm * 10f
        var lastX = 0
        var lastY = 0
        var hasStitched = false

        for ((colorIndex, layer) in activeLayers.withIndex()) {
            if (layer.stitchMode == RasterLayerStitchMode.MANUAL_SATIN) {
                val satin = ManualSatinRails.generate(
                    sections = layer.manualSections,
                    outputColorIndex = colorIndex,
                    widthMm = options.widthMm,
                    imageAspectRatio = croppedWidth.toFloat() / croppedHeight,
                    densityMm = layer.rowSpacingMm ?: options.rowSpacingMm,
                    pullCompensationMm = layer.satinPullCompensationMm,
                    centerUnderlay = layer.satinCenterUnderlay
                ).getOrElse { error ->
                    throw IllegalArgumentException(
                        "Camada ${layer.sourceIndex + 1}: " +
                            (error.message ?: "trilhos manuais inválidos."),
                        error
                    )
                }
                if (hasStitched) {
                    out += EmbroideryPoint(lastX, lastY, StitchCommand.TRIM, colorIndex - 1)
                    out += EmbroideryPoint(lastX, lastY, StitchCommand.COLOR_CHANGE, colorIndex)
                }
                out.addAll(satin)
                require(out.size <= MAX_STITCHES) {
                    "Muitos pontos; reduza o tamanho do desenho."
                }
                val last = satin.last()
                lastX = last.xUnits
                lastY = last.yUnits
                hasStitched = true
                continue
            }
            if (layer.stitchMode == RasterLayerStitchMode.SATIN_STRAIGHT ||
                layer.stitchMode == RasterLayerStitchMode.SATIN_CURVED) {
                val satin = (if (layer.stitchMode == RasterLayerStitchMode.SATIN_CURVED) {
                    RasterCurvedSatin.generate(
                        source = source,
                        art = art,
                        sourceColorIndex = layer.sourceIndex,
                        outputColorIndex = colorIndex,
                        widthMm = options.widthMm,
                        densityMm = layer.rowSpacingMm ?: options.rowSpacingMm,
                        compensationMm = layer.satinPullCompensationMm,
                        centerUnderlay = layer.satinCenterUnderlay
                    )
                } else RasterStraightSatin.generate(
                    source = source,
                    art = art,
                    sourceColorIndex = layer.sourceIndex,
                    outputColorIndex = colorIndex,
                    widthMm = options.widthMm,
                    densityMm = layer.rowSpacingMm ?: options.rowSpacingMm,
                    compensationMm = layer.satinPullCompensationMm,
                    centerUnderlay = layer.satinCenterUnderlay,
                    verticalNeedleDirectionOnly = options.verticalSatinPreviewOnly
                )).getOrElse { error ->
                    throw IllegalArgumentException(
                        "Camada ${layer.sourceIndex + 1}: " +
                            (error.message ?: "não é compatível com o Satin selecionado."),
                        error
                    )
                }
                if (hasStitched) {
                    out += EmbroideryPoint(lastX, lastY, StitchCommand.TRIM, colorIndex - 1)
                    out += EmbroideryPoint(lastX, lastY, StitchCommand.COLOR_CHANGE, colorIndex)
                }
                out.addAll(satin)
                require(out.size <= MAX_STITCHES) {
                    "Logo muito complexo. Reduza cores ou dimensões."
                }
                val last = satin.last()
                lastX = last.xUnits
                lastY = last.yUnits
                hasStitched = true
                continue
            }
            val verticalStep = (layer.rowSpacingMm ?: options.rowSpacingMm) * 10f
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
                        art.pixels[y * source.width + x] != layer.sourceIndex) x++
                    if (x > art.maxX) break
                    val start = x
                    while (x <= art.maxX &&
                        art.pixels[y * source.width + x] == layer.sourceIndex) x++
                    val spanMm10 = (x - start) * unitsPerPixel
                    // Legacy mode keeps the previous 0.8mm/2px threshold.
                    // Fine-detail QA mode also preserves 0.4mm single-pixel
                    // strokes instead of dropping letters of small labels.
                    val keepSpan = if (options.preserveFineDetails) {
                        spanMm10 >= 4f
                    } else {
                        x - start >= 2 && (x - start - 1) * unitsPerPixel >= 8f
                    }
                    if (keepSpan) spans += start to x - 1
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
        out += EmbroideryPoint(lastX, lastY, StitchCommand.END, activeLayers.lastIndex)
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
            threadColors = activeLayers.map { it.threadColor ?: art.colors[it.sourceIndex] },
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
