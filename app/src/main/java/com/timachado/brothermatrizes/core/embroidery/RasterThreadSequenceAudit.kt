package com.timachado.brothermatrizes.core.embroidery

/**
 * Verifica a ordem operacional de uma prévia automática de imagem.
 * Não codifica PES, não modifica pontos e não certifica o bordado físico.
 * Opera exclusivamente no fluxo experimental QA_AUTO_STITCH.
 */
internal data class RasterThreadSequenceReport(
    val stitches: Int,
    val jumps: Int,
    val trims: Int,
    val colorChanges: Int,
    val colorsUsed: Int
)

internal object RasterThreadSequenceAudit {
    private const val MAX_COMMANDS = 90_000

    fun inspect(design: EmbroideryDesign): Result<RasterThreadSequenceReport> =
        runCatching {
            require(
                design.format == "QA_AUTO_STITCH" &&
                    design.isModified && design.sourceBytes.isEmpty() &&
                    design.points.size in 3..MAX_COMMANDS &&
                    design.threadColors.size in 1..25 &&
                    design.endFound
            ) {
                "A auditoria da sequência só aceita a prévia automática " +
                    "de imagem, nunca matrizes PES/DST/JEF."
            }

            val commands = design.points
            require(
                commands.first().command == StitchCommand.JUMP &&
                    commands.first().colorIndex == 0 &&
                    commands.last().command == StitchCommand.END &&
                    commands.count { it.command == StitchCommand.END } == 1
            ) { "Sequência de imagem sem entrada inicial ou fim único." }

            var activeColor = 0
            var needlePosition: EmbroideryPoint? = null
            var lastSewnPosition: EmbroideryPoint? = null
            var stitchedSinceTrim = false
            var previousCommand: StitchCommand? = null
            var stitches = 0
            var jumps = 0
            var trims = 0
            var changes = 0
            val sewnColors = BooleanArray(design.threadColors.size)

            for ((index, point) in commands.withIndex()) {
                require(point.colorIndex in design.threadColors.indices) {
                    "Comando de bordado " + (index + 1) +
                        ": índice de linha inválido."
                }
                when (point.command) {
                    StitchCommand.JUMP -> {
                        require(point.colorIndex == activeColor) {
                            "Salto com cor diferente da linha ativa."
                        }
                        needlePosition = point
                        jumps++
                    }
                    StitchCommand.STITCH -> {
                        require(point.colorIndex == activeColor &&
                            needlePosition != null &&
                            previousCommand != StitchCommand.TRIM &&
                            previousCommand != StitchCommand.COLOR_CHANGE
                        ) {
                            "Ponto costurado sem JUMP válido após corte/troca " +
                                "ou com cor inconsistente."
                        }
                        stitchedSinceTrim = true
                        sewnColors[activeColor] = true
                        needlePosition = point
                        lastSewnPosition = point
                        stitches++
                    }
                    StitchCommand.TRIM -> {
                        val last = lastSewnPosition
                        require(
                            point.colorIndex == activeColor &&
                                previousCommand == StitchCommand.STITCH &&
                                stitchedSinceTrim && last != null &&
                                point.xUnits == last.xUnits &&
                                point.yUnits == last.yUnits
                        ) {
                            "Corte duplicado, sem costura prévia ou deslocado " +
                                "do último ponto costurado."
                        }
                        stitchedSinceTrim = false
                        needlePosition = null
                        trims++
                    }
                    StitchCommand.COLOR_CHANGE -> {
                        val last = lastSewnPosition
                        require(
                            previousCommand == StitchCommand.TRIM &&
                                activeColor + 1 < design.threadColors.size &&
                                point.colorIndex == activeColor + 1 &&
                                last != null &&
                                point.xUnits == last.xUnits &&
                                point.yUnits == last.yUnits
                        ) {
                            "Troca de linha sem corte prévio, fora de ordem " +
                                "ou deslocada do último ponto."
                        }
                        activeColor++
                        needlePosition = null
                        changes++
                    }
                    StitchCommand.END -> {
                        val last = lastSewnPosition
                        require(index == commands.lastIndex &&
                            previousCommand == StitchCommand.STITCH &&
                            point.colorIndex == activeColor &&
                            last != null &&
                            point.xUnits == last.xUnits &&
                            point.yUnits == last.yUnits
                        ) { "Fim de matriz sem ponto final coerente." }
                    }
                    StitchCommand.STOP, StitchCommand.SEQUIN -> {
                        error("Comando especial não aprovado para imagem QA.")
                    }
                }
                previousCommand = point.command
            }
            require(
                stitches > 0 && jumps > 0 &&
                    stitches == design.stitchCount &&
                    jumps == design.jumpCount &&
                    changes == design.colorChanges &&
                    changes + 1 == design.threadColors.size &&
                    sewnColors.all { it }
            ) {
                "Contagens, ordem de linhas ou cores efetivamente costuradas " +
                    "não correspondem à prévia automática."
            }
            RasterThreadSequenceReport(
                stitches = stitches, jumps = jumps, trims = trims,
                colorChanges = changes, colorsUsed = sewnColors.count { it }
            )
        }
}
