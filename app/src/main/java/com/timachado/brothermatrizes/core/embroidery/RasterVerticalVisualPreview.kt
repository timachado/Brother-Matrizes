package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * VISUALIZATION ONLY. It plots vertical needle-direction samples of a
 * separated raster for the unchanged SimulatorScreen to animate.
 *
 * It does NOT compute satin rails, coverage, underlay, appropriate tie-offs,
 * thread tension or safe machine movement. It is not an embroidery pattern.
 * The only caller is the blocked-export image QA route; never send to PES.
 */
object RasterVerticalVisualPreview {
    private const val MAX_POINTS = 65_000

    fun create(
        image: RasterImagePixels,
        separated: SeparatedColorArtwork,
        widthMm: Float,
        hoop: HoopProfile
    ): Result<EmbroideryDesign> = runCatching {
        require(image.width in 8..512 && image.height in 8..512 &&
            separated.pixels.size == image.width * image.height &&
            separated.colors.isNotEmpty() && separated.colors.size <= 4 &&
            widthMm in 10f..200f && widthMm.isFinite()) {
            "Imagem inválida para prévia."
        }
        val inkWidth = separated.maxX - separated.minX + 1
        val inkHeight = separated.maxY - separated.minY + 1
        require(inkWidth > 2 && inkHeight > 2) { "Arte sem área suficiente." }
        val unitsPerPixel = widthMm * 10f / inkWidth
        val heightMm = inkHeight * unitsPerPixel / 10f
        require(widthMm <= hoop.usableWidthMm && heightMm <= hoop.usableHeightMm) {
            "A arte ultrapassa a área útil do bastidor."
        }
        fun xUnit(x: Int) = ((x - separated.minX + .5f) * unitsPerPixel -
            widthMm * 5f).roundToInt()
        fun yUnit(y: Int) = ((inkHeight * .5f - (y - separated.minY + .5f)) *
            unitsPerPixel).roundToInt()

        val output = ArrayList<EmbroideryPoint>()
        // A horizontal STEP between sample columns is always JUMP, NEVER STITCH.
        // Only vertical segments are drawn; long vertical spans split for display.
        val stepX = max(1, (4.5f / unitsPerPixel).roundToInt())
        val maxVerticalPixels = max(1, (45f / unitsPerPixel).toInt())
        for (color in separated.colors.indices) {
            val begin = output.size
            val columns = (separated.minX..separated.maxX step stepX).toList()
            for (x in columns) {
                var y = separated.minY
                while (y <= separated.maxY) {
                    while (y <= separated.maxY &&
                        separated.pixels[y * image.width + x] != color) y++
                    if (y > separated.maxY) break
                    val first = y
                    while (y <= separated.maxY &&
                        separated.pixels[y * image.width + x] == color) y++
                    val last = y - 1
                    if (first == last) continue
                    var from = first
                    while (from < last) {
                        val to = minOf(last, from + maxVerticalPixels)
                        if (output.size + 4 > MAX_POINTS) {
                            throw IllegalArgumentException(
                                "Arte muito detalhada para a prévia visual. Recorte uma parte."
                            )
                        }
                        output += EmbroideryPoint(xUnit(x), yUnit(from), StitchCommand.JUMP, color)
                        output += EmbroideryPoint(xUnit(x), yUnit(to), StitchCommand.STITCH, color)
                        from = to
                    }
                }
            }
            if (output.size > begin && color < separated.colors.lastIndex) {
                val last = output.last()
                output += EmbroideryPoint(last.xUnits, last.yUnits, StitchCommand.TRIM, color)
                output += EmbroideryPoint(last.xUnits, last.yUnits, StitchCommand.COLOR_CHANGE, color + 1)
            }
        }
        require(output.count { it.command == StitchCommand.STITCH } > 3) {
            "Não há detalhes suficientes para a prévia. Amplie a seleção."
        }
        val last = output.last()
        output += EmbroideryPoint(last.xUnits, last.yUnits, StitchCommand.END,
            separated.colors.lastIndex)
        EmbroideryDesign(
            fileName = "PREVIA-VISUAL-NAO-EXPORTAR.qa",
            format = "QA_PREVIEW",
            label = "Amostragem visual vertical — NÃO É MATRIZ SATIN",
            points = output,
            bounds = EmbroideryBounds(
                output.minOf { it.xUnits }, output.maxOf { it.xUnits },
                output.minOf { it.yUnits }, output.maxOf { it.yUnits }
            ),
            stitchCount = output.count { it.command == StitchCommand.STITCH },
            jumpCount = output.count { it.command == StitchCommand.JUMP },
            colorChanges = output.count { it.command == StitchCommand.COLOR_CHANGE },
            endFound = true,
            sourceBytes = ByteArray(0),
            threadColors = separated.colors,
            isModified = true,
            hoopProfile = hoop
        )
    }
}
