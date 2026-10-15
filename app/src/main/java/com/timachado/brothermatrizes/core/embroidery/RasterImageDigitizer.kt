package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Digitalização raster local, experimental, para logos monocromáticos.
 * Não altera o motor de simulação ou os parsers PES/JEF/DST existentes.
 *
 * Cada faixa horizontal do primeiro plano vira pontos reais de preenchimento
 * corrido. Não tenta interpretar automaticamente fotografia, letras cursivas
 * conectadas, compensação de tecido nem bordados profissionais multicoloridos.
 */
data class RasterImagePixels(
    val width: Int,
    val height: Int,
    val argb: IntArray,
    val label: String
)

data class RasterImageOptions(
    val widthMm: Float = 60f,
    val threshold: Int = 140,
    val invert: Boolean = false,
    val threadColor: Int = 0xE6BE70,
    val outputFormat: String = MatrixConverter.preferredBrotherFormat,
    val hoopProfile: HoopProfile? = HoopProfile.H100X100,
    val rowSpacingMm: Float = 0.45f,
    val stitchLengthMm: Float = 2.5f
)

object RasterImageDigitizer {
    fun generate(
        source: RasterImagePixels,
        options: RasterImageOptions
    ): Result<EmbroideryDesign> = runCatching {
        require(source.width in 2..512 && source.height in 2..512) {
            "Imagem fora do limite seguro (512 px por lado)."
        }
        require(source.argb.size == source.width * source.height) {
            "Dados da imagem incompletos."
        }
        require(options.widthMm in 5f..200f) {
            "A largura deve estar entre 5 e 200 mm."
        }
        require(options.threshold in 20..235) {
            "Ajuste o contraste entre 20 e 235."
        }
        require(options.rowSpacingMm in 0.35f..1.0f) {
            "Espaçamento de preenchimento inválido."
        }
        require(options.stitchLengthMm in 1f..4f) {
            "Comprimento dos pontos inválido."
        }
        val format = options.outputFormat.uppercase()
        require(format in MatrixConverter.supportedFormats) {
            "Formato de bordado não suportado."
        }

        // PNGs transparentes usam o canal alfa: só pixels visíveis são bordados.
        // Para fotos opacas, um limiar simples escolhe traços escuros ou claros.
        val hasTransparency = source.argb.any { (it ushr 24) < 128 }
        val mask = BooleanArray(source.argb.size)
        var inkCount = 0
        var minX = source.width
        var maxX = -1
        var minY = source.height
        var maxY = -1
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val i = y * source.width + x
                val pixel = source.argb[i]
                val alpha = pixel ushr 24
                val red = (pixel ushr 16) and 255
                val green = (pixel ushr 8) and 255
                val blue = pixel and 255
                val luminance = (red * 299 + green * 587 + blue * 114) / 1000
                val ink = if (hasTransparency) {
                    alpha >= 128
                } else {
                    if (options.invert) luminance >= options.threshold
                    else luminance < options.threshold
                }
                mask[i] = ink
                if (ink) {
                    inkCount++
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        val occupancy = inkCount.toFloat() / mask.size
        require(inkCount >= 8 && occupancy >= 0.001f) {
            "Nenhum desenho detectado. Ajuste o contraste ou recorte o logo."
        }
        require(occupancy <= 0.72f) {
            "Área muito preenchida. Recorte apenas o logo e ajuste o fundo."
        }
        val croppedWidth = maxX - minX + 1
        val croppedHeight = maxY - minY + 1
        require(croppedWidth >= 2 && croppedHeight >= 2) {
            "O desenho é fino demais. Aumente a resolução ou use um SVG."
        }
        val unitsPerPixel = options.widthMm * 10f / croppedWidth
        val widthUnits = croppedWidth * unitsPerPixel
        val heightUnits = croppedHeight * unitsPerPixel
        val rowStep = max(1, (options.rowSpacingMm * 10f / unitsPerPixel).roundToInt())
        val maxStitchUnits = options.stitchLengthMm * 10f

        fun pxX(x: Int): Int =
            ((x - minX + 0.5f) * unitsPerPixel - widthUnits / 2f).roundToInt()
        fun pxY(y: Int): Int =
            (heightUnits / 2f - (y - minY + 0.5f) * unitsPerPixel).roundToInt()

        val output = ArrayList<EmbroideryPoint>()
        var prevX: Int? = null
        var prevY: Int? = null
        var rowNumber = 0
        var runCount = 0

        var y = minY
        while (y <= maxY) {
            val spans = ArrayList<Pair<Int, Int>>()
            var x = minX
            while (x <= maxX) {
                while (x <= maxX && !mask[y * source.width + x]) x++
                if (x > maxX) break
                val start = x
                while (x <= maxX && mask[y * source.width + x]) x++
                val end = x - 1
                if (end > start && (end - start) * unitsPerPixel >= 0.7f * 10f) {
                    spans += start to end
                }
            }

            val rightToLeft = rowNumber % 2 != 0
            val ordered = if (rightToLeft) spans.asReversed() else spans
            for ((left, right) in ordered) {
                runCount++
                require(runCount <= 16000) {
                    "Desenho muito complexo: recorte e simplifique a imagem."
                }
                val startX = pxX(if (rightToLeft) right else left)
                val endX = pxX(if (rightToLeft) left else right)
                val startY = pxY(y)
                val priorX = prevX
                val priorY = prevY
                if (priorX != null && priorY != null) {
                    val distance = hypot(
                        (startX - priorX).toDouble(),
                        (startY - priorY).toDouble()
                    )
                    if (distance <= 18.0) {
                        // Liga somente extremidades próximas (até 1,8 mm).
                        output += EmbroideryPoint(startX, startY, StitchCommand.STITCH, 0)
                    } else {
                        if (distance >= 80.0) {
                            output += EmbroideryPoint(priorX, priorY, StitchCommand.TRIM, 0)
                        }
                        output += EmbroideryPoint(startX, startY, StitchCommand.JUMP, 0)
                    }
                } else {
                    output += EmbroideryPoint(startX, startY, StitchCommand.JUMP, 0)
                }

                val dx = endX - startX
                val steps = max(1, ceil(kotlin.math.abs(dx) / maxStitchUnits).toInt())
                for (step in 1..steps) {
                    val stitchX = (startX + dx.toFloat() * step / steps).roundToInt()
                    output += EmbroideryPoint(stitchX, startY, StitchCommand.STITCH, 0)
                }
                prevX = endX
                prevY = startY
                require(output.size <= 130_000) {
                    "Quantidade de pontos acima do limite seguro para esta etapa."
                }
            }
            rowNumber++
            y += rowStep
        }

        require(output.count { it.command == StitchCommand.STITCH } >= 2) {
            "A imagem está fina demais para o tamanho escolhido. Aumente a largura."
        }
        output += EmbroideryPoint(prevX ?: 0, prevY ?: 0, StitchCommand.END, 0)

        val live = output.filter { it.command != StitchCommand.END }
        val design = EmbroideryDesign(
            fileName = "imagem-bordado.${format.lowercase()}",
            format = format,
            label = source.label,
            points = output,
            bounds = EmbroideryBounds(
                live.minOf { it.xUnits },
                live.maxOf { it.xUnits },
                live.minOf { it.yUnits },
                live.maxOf { it.yUnits }
            ),
            stitchCount = output.count { it.command == StitchCommand.STITCH },
            jumpCount = output.count { it.command == StitchCommand.JUMP },
            colorChanges = 0,
            endFound = true,
            sourceBytes = ByteArray(0),
            threadColors = listOf(options.threadColor),
            isModified = true,
            hoopProfile = options.hoopProfile
        )
        MachineFinishing.apply(design).design
    }
}
