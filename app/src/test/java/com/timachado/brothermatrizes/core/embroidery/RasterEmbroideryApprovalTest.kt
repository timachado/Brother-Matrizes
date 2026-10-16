package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterEmbroideryApprovalTest {
    @Test fun experimentalImageStitchPathsAreNeverExportable() {
        assertFalse(RasterEmbroideryApproval.EXPORT_ALLOWED)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
        assertTrue(RasterEmbroideryApproval.isReadOnlyPreview(true))
        assertTrue(RasterEmbroideryApproval.WARNING.contains("NÃO ENVIAR PARA A MÁQUINA"))
    }

    @Test fun originalImportedMatrixSimulatorNotAffected() {
        // Only the experimental image-to-embroidery entrypoint is locked.
        assertFalse(RasterEmbroideryApproval.isReadOnlyPreview(false))
    }
}
