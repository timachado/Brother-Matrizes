package com.timachado.brothermatrizes

import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.StitchCommand

/**
 * Os pontos armazenam a POSICAO da agulha APOS cada comando.
 *
 * Em JUMP a maquina se move SEM costurar, mas o destino continua sendo
 * a origem da proxima STITCH. Descartar essa posicao apaga a primeira
 * linha de costura apos o salto no desenho.
 *
 * A funcao emite somente segmentos fisicos STITCH. Nunca desenha
 * segmentos para JUMP, TRIM, STOP ou COLOR_CHANGE.
 */
internal fun visitRenderedStitchSegments(
    points: List<EmbroideryPoint>,
    pointLimit: Int = points.size,
    onSegment: (from: EmbroideryPoint, to: EmbroideryPoint) -> Unit
) {
    var needlePosition: EmbroideryPoint? = null
    for (index in 0 until pointLimit.coerceIn(0, points.size)) {
        val point = points[index]
        when (point.command) {
            StitchCommand.STITCH -> {
                needlePosition?.let { onSegment(it, point) }
                needlePosition = point
            }
            StitchCommand.END -> break
            else -> {
                // Salto, parada, corte, troca de cor ou lantejoula:
                // atualizar a posicao SEM desenhar linha.
                needlePosition = point
            }
        }
    }
}
