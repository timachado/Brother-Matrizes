package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterThreadSequenceAuditTest {
    private fun p(x: Int, y: Int, cmd: StitchCommand, color: Int) =
        EmbroideryPoint(x, y, cmd, color)

    /** Sequência sintética semelhante ao compositor; nunca PES de máquina. */
    private fun example(): EmbroideryDesign {
        val points = listOf(
            p(0, 0, StitchCommand.JUMP, 0),
            p(20, 0, StitchCommand.STITCH, 0),
            p(40, 0, StitchCommand.STITCH, 0),
            p(40, 0, StitchCommand.TRIM, 0),
            p(100, 0, StitchCommand.JUMP, 0),
            p(120, 0, StitchCommand.STITCH, 0),
            p(120, 0, StitchCommand.TRIM, 0),
            p(120, 0, StitchCommand.COLOR_CHANGE, 1),
            p(0, 30, StitchCommand.JUMP, 1),
            p(20, 30, StitchCommand.STITCH, 1),
            p(40, 30, StitchCommand.STITCH, 1),
            p(40, 30, StitchCommand.END, 1)
        )
        return EmbroideryDesign(
            fileName = "imagem-preview.qa",
            format = "QA_AUTO_STITCH",
            label = "Imagem em revisão, não enviar à máquina",
            points = points,
            bounds = EmbroideryBounds(0, 120, 0, 30),
            stitchCount = 5, jumpCount = 3, colorChanges = 1,
            endFound = true, sourceBytes = ByteArray(0),
            threadColors = listOf(0xff143550.toInt(), 0xffc78c28.toInt()),
            isModified = true, hoopProfile = HoopProfile.H130X180
        )
    }

    @Test fun sameColorIslandsAndRealColorChangePassStaticGrammar() {
        val source = example()
        val report = RasterThreadSequenceAudit.inspect(source).getOrThrow()
        assertEquals(5, report.stitches)
        assertEquals(3, report.jumps)
        assertEquals(2, report.trims)
        assertEquals(1, report.colorChanges)
        assertEquals(2, report.colorsUsed)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun missingTrimBeforeNewThreadCannotPass() {
        val src = example()
        val index = src.points.indexOfFirst { it.command == StitchCommand.COLOR_CHANGE }
        val tampered = src.points.toMutableList().apply { removeAt(index - 1) }
        assertTrue(RasterThreadSequenceAudit.inspect(src.copy(points = tampered)).isFailure)
    }

    @Test fun duplicateTrimAndStitchWithoutJumpAreRejected() {
        val src = example()
        val trim = src.points.indexOfFirst { it.command == StitchCommand.TRIM }
        val duplicate = src.points.toMutableList().apply { add(trim + 1, src.points[trim]) }
        assertTrue(RasterThreadSequenceAudit.inspect(src.copy(points = duplicate)).isFailure)
        val withoutJump = src.points.filterIndexed { index, _ -> index != 4 }
        assertTrue(RasterThreadSequenceAudit.inspect(
            src.copy(points = withoutJump, jumpCount = 2)
        ).isFailure)
    }

    @Test fun swappingColorChangeAndJumpIsRejected() {
        val src = example()
        val malformed = src.points.toMutableList()
        val change = malformed.indexOfFirst { it.command == StitchCommand.COLOR_CHANGE }
        val temporary = malformed[change]
        malformed[change] = malformed[change + 1]
        malformed[change + 1] = temporary
        assertTrue(RasterThreadSequenceAudit.inspect(
            src.copy(points = malformed)
        ).isFailure)
    }

    @Test fun forgedPaletteOrMetadataCannotPass() {
        val src = example()
        assertTrue(RasterThreadSequenceAudit.inspect(
            src.copy(colorChanges = 0)
        ).isFailure)
        assertTrue(RasterThreadSequenceAudit.inspect(
            src.copy(threadColors = src.threadColors + 0xff334455.toInt())
        ).isFailure)
        val withWrongIndex = src.points.toMutableList().apply {
            val index = indexOfFirst { it.command == StitchCommand.COLOR_CHANGE }
            set(index, get(index).copy(colorIndex = 0))
        }
        assertTrue(RasterThreadSequenceAudit.inspect(
            src.copy(points = withWrongIndex)
        ).isFailure)
    }

    @Test fun importedFormatsAndPrematureEndAreExcluded() {
        val src = example()
        assertTrue(RasterThreadSequenceAudit.inspect(
            src.copy(format = "PES")
        ).isFailure)
        assertTrue(RasterThreadSequenceAudit.inspect(
            src.copy(format = "DST")
        ).isFailure)
        assertTrue(RasterThreadSequenceAudit.inspect(
            src.copy(format = "JEF")
        ).isFailure)
        val extraEnd = src.points + src.points.last()
        assertTrue(RasterThreadSequenceAudit.inspect(
            src.copy(points = extraEnd)
        ).isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }
}
