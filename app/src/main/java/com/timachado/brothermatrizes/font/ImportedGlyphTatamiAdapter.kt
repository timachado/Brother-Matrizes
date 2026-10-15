package com.timachado.brothermatrizes.font

import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.EmbroideryStressPolicy
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import kotlin.math.roundToInt

/**
 * Ponte EXPERIMENTAL. Não emite END, cor, TRIM nem acabamento de máquina.
 * NÃO ativar na versão comercial até aprovação em Android e bordadeira real.
 */
internal object ImportedGlyphTatamiAdapter {
    const val QA_ONLY_ENABLED: Boolean = false

    fun digitize(
        contours: List<List<CleanRoomSatinEngine.V>>,
        rowPitchMm: Float = 0.375f,
        maxStitchMm: Float = 2.7f
    ): List<EmbroideryPoint> {
        require(rowPitchMm.isFinite() && maxStitchMm.isFinite())
        val converted = contours.map { ring ->
            ring.map { ImportedGlyphTatamiPlanner.P(it.x, it.y) }
        }
        val result = ImportedGlyphTatamiPlanner.plan(
            converted, rowPitchMm * 10f, maxStitchMm * 10f
        )
        require(result.size <= EmbroideryStressPolicy.MAX_GENERATED_COMMANDS) {
            "Fonte produziu comandos em excesso; reduza a altura ou simplifique o desenho."
        }
        return result.map { move ->
            EmbroideryPoint(
                move.point.x.roundToInt(),
                move.point.y.roundToInt(),
                if (move.move == ImportedGlyphTatamiPlanner.Move.JUMP)
                    StitchCommand.JUMP else StitchCommand.STITCH,
                0
            )
        }
    }
}
