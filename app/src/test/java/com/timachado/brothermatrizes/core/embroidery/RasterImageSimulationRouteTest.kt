package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Synthetic QA navigation proof. No physical Android UI or PES certified. */
class RasterImageSimulationRouteTest {
    @Test fun photoWithTexturesCannotOfferAutomaticFullLogoSimulation() {
        assertEquals(
            RasterImageSimulationRoute.PREPARE_FLAT_SOURCE,
            RasterImageSimulationRoutePlanner.choose(
                complexArtwork = true,
                partialAlphaEdges = false,
                flatReferenceAccepted = false,
                manualSatinEditorActive = false
            )
        )
    }

    @Test fun photographWithEvenOnePartialAlphaPixelRequiresReconstruction() {
        assertEquals(
            RasterImageSimulationRoute.PREPARE_FLAT_SOURCE,
            RasterImageSimulationRoutePlanner.choose(
                complexArtwork = false,
                partialAlphaEdges = true,
                flatReferenceAccepted = false,
                manualSatinEditorActive = false
            )
        )
    }

    @Test fun acceptedReferenceStillRequiresLayerByLayerQa() {
        assertEquals(
            RasterImageSimulationRoute.REVIEW_MIXED_LAYERS,
            RasterImageSimulationRoutePlanner.choose(
                complexArtwork = true,
                partialAlphaEdges = true,
                flatReferenceAccepted = true,
                manualSatinEditorActive = false
            )
        )
        assertEquals(
            RasterImageSimulationRoute.REVIEW_MIXED_LAYERS,
            RasterImageSimulationRoutePlanner.choose(
                complexArtwork = false,
                partialAlphaEdges = false,
                flatReferenceAccepted = true,
                manualSatinEditorActive = false
            )
        )
    }

    @Test fun safeSimpleArtworkKeepsConservativeOriginalSimulation() {
        assertEquals(
            RasterImageSimulationRoute.DIRECT_REVIEW,
            RasterImageSimulationRoutePlanner.choose(
                complexArtwork = false,
                partialAlphaEdges = false,
                flatReferenceAccepted = false,
                manualSatinEditorActive = false
            )
        )
    }

    @Test fun explicitlySelectedManualSatinKeepsExistingPreviewGate() {
        assertEquals(
            RasterImageSimulationRoute.DIRECT_REVIEW,
            RasterImageSimulationRoutePlanner.choose(
                complexArtwork = true,
                partialAlphaEdges = true,
                flatReferenceAccepted = false,
                manualSatinEditorActive = true
            )
        )
    }

    @Test fun navigationNeverUnlocksExperimentalPes() {
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }
}
