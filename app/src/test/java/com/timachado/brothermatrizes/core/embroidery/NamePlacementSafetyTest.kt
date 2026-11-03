package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class NamePlacementSafetyTest {
    private fun design(vararg commands: EmbroideryPoint): EmbroideryDesign =
        EmbroideryIntegrity.normalize(
            EmbroideryDesign(
                fileName = "safety.pes",
                format = "PES",
                label = null,
                points = commands.toList(),
                bounds = EmbroideryBounds(0, 0, 0, 0),
                stitchCount = 0,
                jumpCount = 0,
                colorChanges = 0,
                endFound = true,
                sourceBytes = byteArrayOf(),
                threadColors = listOf(0x123456)
            )
        ).getOrThrow().design

    private fun line(x1: Int, y1: Int, x2: Int, y2: Int) = design(
        EmbroideryPoint(x1, y1, StitchCommand.JUMP, 0),
        EmbroideryPoint(x2, y2, StitchCommand.STITCH, 0),
        EmbroideryPoint(x2, y2, StitchCommand.END, 0)
    )

    @Test fun rejectsOverlappingStitchSegments() {
        val background = line(-100, 0, 100, 0)
        val name = line(0, -100, 0, 100)
        assertTrue(NamePlacementSafety.validate(background, name).isFailure)
    }

    @Test fun rejectsStitchWithinOnePointFiveMillimeters() {
        val background = line(-100, 0, 100, 0)
        val name = line(-100, 10, 100, 10)
        assertTrue(NamePlacementSafety.validate(background, name).isFailure)
    }

    @Test fun acceptsSeparatedStitches() {
        val background = line(-100, 0, 100, 0)
        val name = line(-100, 40, 100, 40)
        assertTrue(NamePlacementSafety.validate(background, name).isSuccess)
    }

    @Test fun warningUsesTheActiveDesignLabelInsteadOfFixedMotifs() {
        val background = line(-100, 0, 100, 0).copy(
            label = "Coroa ornamental", fileName = "ornamento-01.pes"
        )
        val name = line(0, -100, 0, 100)
        val message = NamePlacementSafety.validate(background, name)
            .exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("Coroa ornamental"))
        assertTrue(message.contains("1,5 mm"))
        assertFalse(message.contains("flores", ignoreCase = true))
        assertFalse(message.contains("borboletas", ignoreCase = true))
    }

    @Test fun warningFollowsTheCurrentMatrixWhenSwitchingFiles() {
        val base = line(-100, 0, 100, 0)
        val name = line(0, -100, 0, 100)
        val first = base.copy(label = null, fileName = "brasao-01.pes")
        val second = base.copy(label = null, fileName = "moldura-geometrica.dst")
        val firstMessage = NamePlacementSafety.validate(first, name)
            .exceptionOrNull()?.message.orEmpty()
        val secondMessage = NamePlacementSafety.validate(second, name)
            .exceptionOrNull()?.message.orEmpty()
        assertTrue(firstMessage.contains("brasao-01"))
        assertTrue(secondMessage.contains("moldura-geometrica"))
        assertFalse(secondMessage.contains("brasao-01"))
    }

    @Test fun warningReportsConfiguredClearanceNotAlwaysOnePointFive() {
        val background = line(-100, 0, 100, 0)
        val name = line(0, -100, 0, 100)
        val message = NamePlacementSafety.validate(background, name, clearanceUnits = 20)
            .exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("2 mm"))
        assertFalse(message.contains("1,5 mm"))
    }

    @Test fun ignoresJumpsAsStitches() {
        val frame = design(
            EmbroideryPoint(-100, -100, StitchCommand.JUMP, 0),
            EmbroideryPoint(-100, 100, StitchCommand.STITCH, 0),
            EmbroideryPoint(100, 100, StitchCommand.JUMP, 0),
            EmbroideryPoint(100, 120, StitchCommand.STITCH, 0),
            EmbroideryPoint(100, 120, StitchCommand.END, 0)
        )
        val name = line(0, -20, 0, 20)
        assertTrue(NamePlacementSafety.validate(frame, name).isSuccess)
    }
}
