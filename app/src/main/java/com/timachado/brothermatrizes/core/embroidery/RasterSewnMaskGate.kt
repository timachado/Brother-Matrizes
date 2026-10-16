package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Independent final-command gate for QA fill paths. Unlike planners that
 * examine their own floating-point candidates, this checks the actual
 * integer (0.1 mm) STITCH commands passed to the original simulator.
 *
 * It ignores TRIM/JUMP travel as those moves do NOT sew thread. All sampled
 * segments must remain on the component's original opaque ink, including
 * counters in O/B/P and other negative spaces. Only subpixel rounding
 * tolerance is allowed (0.85 source pixel), not arbitrary missing geometry.
 *
 * This gate is NOT physical stitch-out certification or permission to export
 * PES; thread width, pull compensation and fabric density remain unverified.
 */
internal data class RasterSewnMaskResult(
    val sewnSegments: Int,
    val sampledPositions: Int,
    val jumpCommands: Int,
    val trimCommands: Int
)

internal object RasterSewnMaskGate {
    private const val MAX_SAMPLES = 1_000_000
    private const val PIXEL_TOLERANCE = 0.85

    fun inspect(
        image: RasterImagePixels,
        part: RasterSatinObject,
        design: EmbroideryDesign,
        widthMm: Float
    ): Result<RasterSewnMaskResult> = runCatching {
        require(image.width in 8..512 && image.height in 8..512 &&
            image.argb.size == image.width * image.height &&
            part.mask.pixels.size == image.argb.size &&
            widthMm.isFinite() && widthMm in 10f..200f &&
            design.sourceBytes.isEmpty() && design.isModified &&
            design.format == "QA_TATAMI_REVIEW" &&
            design.points.size in 3..90_000
        ) {
            "Auditoria de máscara exclusiva para Tatami QA sem arquivo PES."
        }
        val mmPerPixel = widthMm.toDouble() / image.width
        val unitsPerPixel = mmPerPixel * 10.0
        fun point(p: EmbroideryPoint): Pair<Double, Double> =
            Pair(
                p.xUnits / unitsPerPixel + image.width / 2.0,
                image.height / 2.0 - p.yUnits / unitsPerPixel
            )
        fun inkAt(x: Double, y: Double): Boolean {
            val ix = floor(x).toInt()
            val iy = floor(y).toInt()
            for (dy in -1..1) for (dx in -1..1) {
                val px = ix + dx
                val py = iy + dy
                if (px !in part.mask.minX..part.mask.maxX ||
                    py !in part.mask.minY..part.mask.maxY) continue
                if (part.mask.pixels[py*image.width+px] != 0) continue
                if (hypot(px+.5-x,py+.5-y) <= PIXEL_TOLERANCE) {
                    return true
                }
            }
            return false
        }

        var position: EmbroideryPoint? = null
        var segments = 0
        var samples = 0
        var jumps = 0
        var trims = 0
        for ((index, command) in design.points.withIndex()) {
            when (command.command) {
                StitchCommand.JUMP -> {
                    // A jump is travel with the needle up, not a stitch.
                    position = command
                    jumps++
                }
                StitchCommand.STITCH -> {
                    val start = position ?: throw IllegalArgumentException(
                        "STITCH sem posição inicial após TRIM/COLOR_CHANGE."
                    )
                    require(command.colorIndex == start.colorIndex) {
                        "STITCH entre cores incompatíveis."
                    }
                    val (ax,ay) = point(start)
                    val (bx,by) = point(command)
                    val lengthPx = hypot(bx-ax,by-ay)
                    require(lengthPx * mmPerPixel in .20..7.80) {
                        "Passo do ponto fora do limite experimental."
                    }
                    val steps = ceil(lengthPx * 4).toInt().coerceAtLeast(1)
                    for (i in 0..steps) {
                        val t = i.toDouble() / steps
                        val x = ax+(bx-ax)*t
                        val y = ay+(by-ay)*t
                        samples++
                        require(samples <= MAX_SAMPLES) {
                            "Amostragem de pontos excessiva."
                        }
                        require(inkAt(x,y)) {
                            "Ponto costurado ${index+1} atravessa área vazia " +
                                "da imagem em ${floor(x).toInt()},${floor(y).toInt()}. " +
                                "Simulação Tatami recusada."
                        }
                    }
                    position = command
                    segments++
                }
                StitchCommand.TRIM -> {
                    trims++
                    position = null
                }
                StitchCommand.COLOR_CHANGE, StitchCommand.STOP -> position = null
                StitchCommand.END -> require(index == design.points.lastIndex) {
                    "END prematuro na sequência."
                }
                StitchCommand.SEQUIN -> error(
                    "Comando SEQUIN não suportado na auditoria."
                )
            }
        }
        require(segments > 0 && jumps > 0 &&
            segments == design.stitchCount && jumps == design.jumpCount &&
            design.points.last().command == StitchCommand.END
        ) {
            "Número de segmentos e saltos não corresponde à matriz QA."
        }
        RasterSewnMaskResult(
            sewnSegments=segments,sampledPositions=samples,
            jumpCommands=jumps,trimCommands=trims
        )
    }
}
