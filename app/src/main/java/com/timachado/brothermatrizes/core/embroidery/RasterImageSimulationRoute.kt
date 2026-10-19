package com.timachado.brothermatrizes.core.embroidery

/**
 * QA decision for the primary action on Imagem para Bordado.
 * Never present an automatic whole-photo simulation when existing audits
 * already prove that the source must be reconstructed first.
 * This is navigation only: it cannot authorize or produce PES.
 */
internal enum class RasterImageSimulationRoute {
    DIRECT_REVIEW,
    PREPARE_FLAT_SOURCE,
    REVIEW_MIXED_LAYERS
}

internal object RasterImageSimulationRoutePlanner {
    fun choose(
        complexArtwork: Boolean,
        partialAlphaEdges: Boolean,
        flatReferenceAccepted: Boolean,
        manualSatinEditorActive: Boolean
    ): RasterImageSimulationRoute {
        if (manualSatinEditorActive) {
            // Existing local, reviewed Satin section uses its conservative
            // standalone preview gate. It is never an automatic full-logo PES.
            return RasterImageSimulationRoute.DIRECT_REVIEW
        }
        if (flatReferenceAccepted) {
            // Even an accepted flat reference still requires individual
            // stitches, color order, coverage and readiness checks.
            return RasterImageSimulationRoute.REVIEW_MIXED_LAYERS
        }
        if (complexArtwork || partialAlphaEdges) {
            return RasterImageSimulationRoute.PREPARE_FLAT_SOURCE
        }
        return RasterImageSimulationRoute.DIRECT_REVIEW
    }
}
