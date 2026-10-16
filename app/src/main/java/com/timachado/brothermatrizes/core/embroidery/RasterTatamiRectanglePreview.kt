package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * First constrained Tatami-like fill PREVIEW for one solid rectangle.
 *
 * The *actual needle path* is a back-and-forth set of evenly spaced fill
 * rows. Consecutive rows connect INSIDE the solid rectangle, not across a
 * hole. Deliberately excludes letters, photographs, counters, curved shapes,
 * gradients, split regions and overlap with other layers.
 *
 * It is not a Brother-approved filling technique: no calibrated underlay,
 * pull compensation, fabric-dependent density or stitch-out exists yet.
 * Never export/send QA_TATAMI_REVIEW to an embroidery machine.
 */
internal object RasterTatamiRectanglePreview {
    private const val MAX_COMMANDS = 7000
    private const val TARGET_ROW_SPACING_MM = .78
    private const val ROW_MARGIN_MM = .40
    private const val COLUMN_MARGIN_MM = .45
    private const val MAX_STITCH_MM = 2.8

    fun create(
        image: RasterImagePixels,
        part: RasterSatinObject,
        fullWidthMm: Float,
        hoop: HoopProfile
    ): Result<EmbroideryDesign> = runCatching {
        require(image.width in 8..512 && image.height in 8..512 &&
            image.argb.size == part.mask.pixels.size &&
            fullWidthMm.isFinite() && fullWidthMm in 10f..200f
        ) { "Imagem inválida para preencher." }
        val heightMm = fullWidthMm * image.height / image.width
        require(fullWidthMm <= hoop.usableWidthMm &&
            heightMm <= hoop.usableHeightMm) {
            "A arte ultrapassa o bastidor selecionado."
        }
        val mask = part.mask
        val w = mask.maxX - mask.minX + 1
        val h = mask.maxY - mask.minY + 1
        val mmPerPixel = fullWidthMm / image.width
        val wMm = w * mmPerPixel
        val hMm = h * mmPerPixel
        require(wMm in 7.0f..48.0f && hMm in 7.0f..48.0f &&
            w in 8..250 && h in 8..250
        ) {
            "Preenchimento experimental limitado a blocos de 7–48 mm. " +
                "Divida a arte grande ou revise manualmente."
        }
        // Reject ANY missing pixel: the scan path may only move through
        // contiguous, fully inked material. Never fill a hole in O/P/B.
        require(part.pixelCount == w*h &&
            (mask.minY..mask.maxY).all { y ->
                (mask.minX..mask.maxX).all { x ->
                    mask.pixels[y * image.width + x] == 0
                }
            }
        ) {
            "Este bloco tem vazios, diagonais ou contornos irregulares; " +
                "Tatami retangular bloqueado para não cruzar espaços sem tinta."
        }
        val usableHeight = hMm - (ROW_MARGIN_MM * 2)
        val usableWidth = wMm - (COLUMN_MARGIN_MM * 2)
        val rows = ceil(usableHeight / TARGET_ROW_SPACING_MM).toInt() + 1
        require(rows in 4..140) { "Densidade de linhas não validada." }
        val yStepMm = usableHeight / (rows - 1)
        require(yStepMm in .65..1.0) {
            "Distância entre fileiras fora do intervalo de QA."
        }

        data class Point(val x: Double, val y: Double)
        val needle = ArrayList<Point>()
        val left = mask.minX * mmPerPixel + COLUMN_MARGIN_MM
        val right = mask.minX * mmPerPixel + wMm - COLUMN_MARGIN_MM
        val top = mask.minY * mmPerPixel + ROW_MARGIN_MM

        for (row in 0 until rows) {
            val y = top + row * yStepMm
            // Alternating run length staggers penetration points on nearby
            // rows; never draw arbitrary horizontal raster "Satin" strips.
            val steps = ceil(usableWidth / (if (row % 2 == 0) 2.4 else 2.15))
                .toInt()
            require(steps in 3..48)
            for (j in 0..steps) {
                val t = j.toDouble() / steps
                val x = if (row % 2 == 0) left + usableWidth * t
                    else right - usableWidth * t
                needle += Point(x.toDouble(), y.toDouble())
            }
        }
        require(needle.size in 12..MAX_COMMANDS)
        val xMin = mask.minX * mmPerPixel
        val xMax = (mask.maxX+1) * mmPerPixel
        val yMin = mask.minY * mmPerPixel
        val yMax = (mask.maxY+1) * mmPerPixel
        val unit = 10.0
        fun at(p: Point, command: StitchCommand): EmbroideryPoint {
            require(p.x >= xMin && p.x <= xMax &&
                p.y >= yMin && p.y <= yMax) {
                "Coordenada fora do objeto."
            }
            return EmbroideryPoint(
                ((p.x - fullWidthMm/2) * unit).roundToInt(),
                ((heightMm/2 - p.y) * unit).roundToInt(),
                command, 0
            )
        }
        val out = ArrayList<EmbroideryPoint>(needle.size+1)
        out += at(needle.first(), StitchCommand.JUMP)
        for (i in 1 until needle.size) {
            val a = needle[i-1]
            val b = needle[i]
            val stepMm = hypot(b.x-a.x,b.y-a.y)
            require(stepMm in .20..MAX_STITCH_MM) {
                "Ponto Tatami fora do intervalo preliminar de QA."
            }
            out += at(b,StitchCommand.STITCH)
        }
        out += at(needle.last(), StitchCommand.END)
        val positioned = out.dropLast(1)
        val bounds = EmbroideryBounds(
            positioned.minOf { it.xUnits }, positioned.maxOf { it.xUnits },
            positioned.minOf { it.yUnits }, positioned.maxOf { it.yUnits }
        )
        require(bounds.widthMm <= hoop.usableWidthMm &&
            bounds.heightMm <= hoop.usableHeightMm)
        val draft = EmbroideryDesign(
            fileName = "TATAMI-RETANGULO-REVISAO.qa",
            format = "QA_TATAMI_REVIEW",
            label = "Tatami retangular experimental — NÃO enviar à máquina",
            points = out,
            bounds = bounds,
            stitchCount = out.count { it.command == StitchCommand.STITCH },
            jumpCount = 1, colorChanges = 0, endFound = true,
            sourceBytes = ByteArray(0),
            threadColors = listOf(part.color),
            isModified = true, hoopProfile = hoop
        )
        // Physical needle audit is a software screen only. A passed draft
        // remains non-exportable until machine-specific fabric QA.
        RasterNeedleRiskAudit.inspect(draft, hoop).getOrThrow()
        draft
    }
}
