package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.EditTransform
import kotlin.math.roundToInt

/**
 * Conversão segura dos campos do editor Windows para o contrato geométrico
 * oficial do Android, inclusive escala independente dos eixos X/Y.
 * Não executa operações nem grava projetos.
 */
internal object DesktopTransformInput {
    fun parse(
        scale: String,
        horizontal: String,
        vertical: String,
        angle: String,
        moveX: String,
        moveY: String,
        mirrorHorizontal: Boolean = false,
        mirrorVertical: Boolean = false
    ): EditTransform {
        fun number(raw: String, field: String): Float {
            val parsed = raw.trim().replace(',', '.').toFloatOrNull()
            require(parsed != null && parsed.isFinite()) {
                "$field: informe um número finito válido."
            }
            return parsed
        }

        val uniform = number(scale, "Escala geral")
        require(uniform in 0.25f..3f) {
            "Escala geral: permitido de 0,25 a 3,00."
        }
        val x = number(horizontal, "Expansão horizontal")
        val y = number(vertical, "Expansão vertical")
        require(x in 0.5f..2.5f && y in 0.5f..2.5f) {
            "Expansão horizontal/vertical: permitido de 0,50 a 2,50."
        }
        val rotation = number(angle, "Rotação")
        require(rotation in -360f..360f) {
            "Rotação: permitido de -360° a +360°."
        }
        val dx = number(moveX, "Mover X")
        val dy = number(moveY, "Mover Y")
        require(dx in -200f..200f && dy in -200f..200f) {
            "Deslocamento: permitido de -200 a +200 mm."
        }

        return EditTransform(
            scale = uniform,
            scaleX = x,
            scaleY = y,
            rotationDegrees = rotation,
            offsetXUnits = (dx * 10f).roundToInt(),
            offsetYUnits = (dy * 10f).roundToInt(),
            mirrorHorizontal = mirrorHorizontal,
            mirrorVertical = mirrorVertical
        )
    }
}
