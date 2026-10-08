package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbroiderySequenceAuditTest {
    private fun maria(): EmbroideryDesign =
        TextMatrixGenerator.generate(
            TextMatrixOptions(
                text = "Maria",
                heightMm = 18f,
                style = TextStitchStyle.SATIN,
                outputFormat = "PES",
                color = 0xE63946
            )
        ).getOrThrow()

    @Test
    fun pesReopeningMustKeepTheSameStitchedPathInTheSameOrder() {
        val generated = maria()
        val canonical = GeneratedMatrixPipeline.canonicalize(
            design = generated,
            outputSuffix = "maria-order-audit"
        )
        assertTrue(canonical is EmbroideryLoadResult.Success)
        val reopened = (canonical as EmbroideryLoadResult.Success).design
        val audit = EmbroiderySequenceAudit.compare(generated, reopened)
        assertTrue(
            "PES precisa preservar o percurso. ${audit.summary()}\n" +
                "BEFORE=${audit.before.checkpoints}\n" +
                "AFTER=${audit.after.checkpoints}",
            audit.orderedPathPreserved
        )
    }

    @Test
    fun auditDetectsChangingTheOrderWhileKeepingTheSameGeometry() {
        val generated = maria()
        val reversed = generated.copy(
            points = generated.points.reversed()
        )
        val audit = EmbroiderySequenceAudit.compare(generated, reversed)
        assertFalse(
            "Reordenar pontos sem mudar a geometria precisa ser detectado: ${audit.summary()}",
            audit.orderedPathPreserved
        )
    }

    @Test
    fun reportShowsReturnsAndProducesSortableSequenceCheckpoints() {
        val design = maria()
        val report = EmbroiderySequenceAudit.inspect("generated", design)
        assertEquals(11, report.checkpoints.size)
        assertEquals(0, report.checkpoints.first().fraction)
        assertEquals(100, report.checkpoints.last().fraction)
        assertEquals(12, report.csv().trimEnd().lines().size)
        assertTrue(report.stitches > 0)
    }
}
