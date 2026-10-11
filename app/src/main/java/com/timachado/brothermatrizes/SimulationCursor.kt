package com.timachado.brothermatrizes

import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.StitchCommand

/**
 * Posicao real da agulha no simulador.
 *
 * pointLimit e a quantidade de comandos JA executados, nao o indice do
 * proximo comando. Movimentos JUMP/STITCH/SEQUIN atualizam a posicao;
 * TRIM, STOP, COLOR_CHANGE e END nao movem a agulha visualmente.
 */
internal object SimulationCursor {
    private fun isMovement(command: StitchCommand) =
        command == StitchCommand.JUMP ||
            command == StitchCommand.STITCH ||
            command == StitchCommand.SEQUIN

    fun needlePoint(
        points: List<EmbroideryPoint>,
        completedCommands: Int
    ): EmbroideryPoint? {
        if (points.isEmpty()) return null
        val completed = completedCommands.coerceIn(0, points.size)

        // Antes de dar play, apresentar a entrada efetiva do bordado em vez
        // de um marcador em (0,0) ou de uma posicao de controle.
        if (completed == 0) {
            return points.firstOrNull { isMovement(it.command) }
        }

        for (i in completed - 1 downTo 0) {
            if (isMovement(points[i].command)) return points[i]
        }
        return points.firstOrNull { isMovement(it.command) }
    }

    /**
     * Os sliders usam STITCHs efetivos e devem executar inclusive os
     * comandos finais (TRIM/STOP) quando o usuario seleciona 100%.
     */
    fun pointIndexForStitchCount(prefix: IntArray, targetStitches: Int): Int {
        if (prefix.isEmpty() || targetStitches <= 0) return 0
        if (targetStitches >= prefix.last()) return prefix.lastIndex
        var low = 0
        var high = prefix.lastIndex
        while (low < high) {
            val middle = low + (high - low) / 2
            if (prefix[middle] < targetStitches) low = middle + 1
            else high = middle
        }
        return low.coerceIn(0, prefix.lastIndex)
    }
}
