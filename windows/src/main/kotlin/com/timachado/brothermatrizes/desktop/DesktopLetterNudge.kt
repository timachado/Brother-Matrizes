package com.timachado.brothermatrizes.desktop

import kotlin.math.roundToInt

/**
 * Deslocamento de uma camada de letra por passos exatos de 0,5 mm.
 * Unidades oficiais: 1 unidade = 0,1 mm.
 *
 * Só trabalha com composições de letras criadas no Windows ou reabertas
 * mediante validação do par FLP + .bmlayers. Nunca tenta decompor PES/DST/JEF.
 */
internal object DesktopLetterNudge {
    const val STEP_UNITS = 5 // 0,5 mm

    fun move(
        layout: DesktopLetterLayout,
        index: Int,
        deltaXUnits: Int,
        deltaYUnits: Int
    ): DesktopLetterLayout {
        require(index in layout.editableIndices) {
            "Selecione uma camada de letra válida."
        }
        require(
            (deltaXUnits == 0 && kotlin.math.abs(deltaYUnits) == STEP_UNITS) ||
                (deltaYUnits == 0 && kotlin.math.abs(deltaXUnits) == STEP_UNITS)
        ) {
            "O deslocamento precisa ser um passo de 0,5 mm em um eixo."
        }
        val current = layout.objects[index]
        // Arredondar à unidade do motor antes de somar, evitando
        // acumulação de ponto flutuante em vários movimentos do painel.
        val currentX = (current.moveXmm * 10f).roundToInt()
        val currentY = (current.moveYmm * 10f).roundToInt()
        val nextX = currentX.toLong() + deltaXUnits
        val nextY = currentY.toLong() + deltaYUnits
        require(nextX in -1000L..1000L && nextY in -1000L..1000L) {
            "Movimento individual ultrapassa ±100 mm por eixo."
        }
        return DesktopLetterComposer.update(
            layout, index,
            moveXmm = nextX.toFloat() / 10f,
            moveYmm = nextY.toFloat() / 10f,
            scale = current.scale,
            rotationDegrees = current.rotationDegrees
        )
    }
}
