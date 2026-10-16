package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.hypot

/**
 * Fail-closed *static* screening of the actual needle-penetration commands
 * produced by Imagem para Bordado. Never used on files opened through the
 * pre-existing PES/JEF/DST readers.
 *
 * The Brother documentation warns that zero/very short pitches and repeated
 * or overly dense penetrations can break needles and thread. We deliberately
 * flag these conditions before a QA simulator preview.
 *
 * THESE THRESHOLDS ARE CONSERVATIVE EXPERIMENTAL SOFTWARE GATES, NOT
 * BROTHER-SPECIFIED LIMITS OR A GUARANTEE AGAINST NEEDLE BREAKAGE.
 * Machine model, fabric, backing, needle, thread and a real stitch-out still
 * require verification before any export to a physical embroidery machine.
 */
internal data class RasterNeedleRiskReport(
    val stitchesExamined: Int,
    val jumpsExamined: Int,
    val smallestStitchMm: Double,
    val longestStitchMm: Double,
    val peakNeedlePenetrationsPerCell: Int
)

internal object RasterNeedleRiskAudit {
    private const val MAX_COMMANDS = 90_000
    private const val MIN_PITCH_MM = 0.2
    private const val MAX_PITCH_MM = 7.8
    private const val CELL_UNITS = 3 // 0.3 mm; hotspot screen only

    fun inspect(design: EmbroideryDesign, hoop: HoopProfile): Result<RasterNeedleRiskReport> =
        runCatching {
            require(
                design.format in setOf(
                    "QA_AUTO_STITCH", "QA_SATIN_REVIEW",
                    "QA_RUNNING_REVIEW", "QA_TATAMI_REVIEW"
                ) && design.sourceBytes.isEmpty() && design.isModified &&
                    design.points.size in 3..MAX_COMMANDS &&
                    design.hoopProfile == hoop
            ) {
                "A análise da agulha só aceita trajetórias experimentais " +
                    "não exportáveis e com bastidor confirmado."
            }
            val commands = design.points
            require(commands.last().command == StitchCommand.END &&
                commands.count { it.command == StitchCommand.END } == 1
            ) {
                "Sequência sem encerramento único."
            }
            require(design.threadColors.isNotEmpty()) {
                "A sequência não possui mapeamento de linha."
            }
            val sx = hoop.usableWidthMm * 5.0
            val sy = hoop.usableHeightMm * 5.0
            val hitCount = HashMap<Long, Int>()
            var peaks = 0
            var stitches = 0
            var jumps = 0
            var minimum = Double.POSITIVE_INFINITY
            var maximum = 0.0
            var previous: EmbroideryPoint? = null
            fun inside(p: EmbroideryPoint): Boolean =
                kotlin.math.abs(p.xUnits.toDouble()) <= sx &&
                    kotlin.math.abs(p.yUnits.toDouble()) <= sy
            fun distance(a: EmbroideryPoint, b: EmbroideryPoint) =
                hypot(
                    (b.xUnits - a.xUnits).toDouble(),
                    (b.yUnits - a.yUnits).toDouble()
                ) / 10.0
            for ((index, p) in commands.withIndex()) {
                require(p.colorIndex in design.threadColors.indices) {
                    "Índice de linha fora do mapa no comando ${index + 1}."
                }
                require(inside(p)) {
                    "Comando ${index + 1} fora do bastidor útil."
                }
                when (p.command) {
                    StitchCommand.JUMP -> {
                        // JUMP moves the embroidery arm with no needle
                        // penetration. Never measure a jump as a STITCH.
                        previous = p
                        jumps++
                    }
                    StitchCommand.STITCH -> {
                        val earlier = previous ?: throw IllegalArgumentException(
                            "Comando ${index + 1}: ponto costurado sem movimento " +
                                "de entrada após corte ou troca de cor."
                        )
                        require(earlier.colorIndex == p.colorIndex) {
                            "Mudança de cor sem instrução apropriada."
                        }
                        val pitch = distance(earlier, p)
                        require(pitch >= MIN_PITCH_MM && pitch <= MAX_PITCH_MM) {
                            "Comando ${index + 1}: passo de ${"%.2f".format(java.util.Locale.US,pitch)} mm " +
                                "fora da faixa preliminar de QA. " +
                                "Zero, pontos excessivamente próximos ou muito longos " +
                                "exigem revisão antes do bordado."
                        }
                        minimum = minOf(minimum, pitch)
                        maximum = maxOf(maximum, pitch)
                        // Quantize *needle penetration locations*, not all
                        // crossings of Satin lines. Multiple passes through
                        // the same tiny fabric region are a specific warning.
                        val cellX = Math.floorDiv(p.xUnits, CELL_UNITS)
                        val cellY = Math.floorDiv(p.yUnits, CELL_UNITS)
                        val key = (cellX.toLong() shl 32) xor
                            (cellY.toLong() and 0xffff_ffffL)
                        val hits = (hitCount[key] ?: 0) + 1
                        hitCount[key] = hits
                        peaks = maxOf(peaks, hits)
                        require(hits < 3) {
                            "Três ou mais penetrações concentradas numa região " +
                                "de 0,3 mm. Risco de acúmulo: rever densidade " +
                                "e camadas antes de qualquer teste na máquina."
                        }
                        previous = p
                        stitches++
                    }
                    StitchCommand.TRIM, StitchCommand.COLOR_CHANGE,
                    StitchCommand.STOP -> {
                        // Following STITCH must start with an explicit JUMP:
                        // no hidden diagonal after a thread trim/change.
                        previous = null
                    }
                    StitchCommand.SEQUIN -> error(
                        "Comando de lantejoula não suportado na ferramenta QA."
                    )
                    StitchCommand.END -> {
                        require(index == commands.lastIndex) {
                            "Encerramento prematuro da sequência."
                        }
                        previous = null
                    }
                }
            }
            require(stitches > 0 && jumps > 0 &&
                design.stitchCount == stitches &&
                design.jumpCount == jumps
            ) {
                "Contagem de pontos não corresponde aos comandos da matriz."
            }
            RasterNeedleRiskReport(
                stitchesExamined = stitches,
                jumpsExamined = jumps,
                smallestStitchMm = minimum,
                longestStitchMm = maximum,
                peakNeedlePenetrationsPerCell = peaks
            )
        }
}
