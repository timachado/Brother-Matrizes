package com.timachado.brothermatrizes.core.embroidery

object EmbroideryStressPolicy {
    const val MAX_GENERATED_COMMANDS =
        500_000

    const val MAX_GUIDE_COMMANDS =
        500_000

    const val MAX_SEGMENTS_PER_MOVE =
        10_000

    fun requireGeneratedSafe(
        design: EmbroideryDesign
    ) {
        require(
            design.points
                .size <=
                MAX_GENERATED_COMMANDS
        ) {
            "A matriz gerada ficou complexa demais para processar com segurança no celular."
        }

        require(
            design.guidePoints
                .size <=
                MAX_GUIDE_COMMANDS
        ) {
            "O contorno vetorial ficou complexo demais para processar com segurança no celular."
        }
    }
}
