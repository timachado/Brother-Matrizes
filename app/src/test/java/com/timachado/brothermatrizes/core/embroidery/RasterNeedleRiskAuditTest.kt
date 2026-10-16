package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterNeedleRiskAuditTest {
    private fun p(x: Int, y: Int, command: StitchCommand, color: Int = 0) =
        EmbroideryPoint(x, y, command, color)

    private fun preview(
        commands: List<EmbroideryPoint>,
        hoop: HoopProfile = HoopProfile.H130X180,
        format: String = "QA_AUTO_STITCH"
    ): EmbroideryDesign {
        val positions = commands.filter {
            it.command == StitchCommand.STITCH || it.command == StitchCommand.JUMP
        }
        val bounds = EmbroideryBounds(
            positions.minOf { it.xUnits }, positions.maxOf { it.xUnits },
            positions.minOf { it.yUnits }, positions.maxOf { it.yUnits }
        )
        return EmbroideryDesign(
            fileName = "needle-qa",
            format = format,
            label = "Revisão da agulha",
            points = commands,
            bounds = bounds,
            stitchCount = commands.count { it.command == StitchCommand.STITCH },
            jumpCount = commands.count { it.command == StitchCommand.JUMP },
            colorChanges = commands.count {
                it.command == StitchCommand.COLOR_CHANGE
            },
            endFound = true, sourceBytes = ByteArray(0),
            threadColors = listOf(0xff143550.toInt()),
            isModified = true, hoopProfile = hoop
        )
    }

    @Test fun normalPitchInOriginalHoopPassesStaticQaScreen() {
        val commands = listOf(
            p(0,0,StitchCommand.JUMP),
            p(16,0,StitchCommand.STITCH),
            p(32,0,StitchCommand.STITCH),
            p(32,0,StitchCommand.END)
        )
        val report = RasterNeedleRiskAudit.inspect(
            preview(commands), HoopProfile.H130X180
        ).getOrThrow()
        assertEquals(2, report.stitchesExamined)
        assertEquals(1, report.jumpsExamined)
        assertTrue(report.smallestStitchMm >= .2)
        assertTrue(report.longestStitchMm <= 7.8)
    }

    @Test fun zeroPitchCannotPretendToBeSafeEmbroidery() {
        val commands = listOf(
            p(0,0,StitchCommand.JUMP),
            p(0,0,StitchCommand.STITCH),
            p(0,0,StitchCommand.END)
        )
        assertTrue(RasterNeedleRiskAudit.inspect(
            preview(commands), HoopProfile.H130X180
        ).isFailure)
    }

    @Test fun extremelyShortPitchIsRejected() {
        val commands = listOf(
            p(0,0,StitchCommand.JUMP),
            p(1,0,StitchCommand.STITCH),
            p(1,0,StitchCommand.END)
        )
        assertTrue(RasterNeedleRiskAudit.inspect(
            preview(commands), HoopProfile.H130X180
        ).isFailure)
    }

    @Test fun threePenetrationsInSameSmallSpotCannotBeReleased() {
        val commands = listOf(
            p(0,0,StitchCommand.JUMP),
            p(25,0,StitchCommand.STITCH),
            p(0,0,StitchCommand.JUMP),
            p(25,0,StitchCommand.STITCH),
            p(0,0,StitchCommand.JUMP),
            p(25,0,StitchCommand.STITCH),
            p(25,0,StitchCommand.END)
        )
        val report = RasterNeedleRiskAudit.inspect(
            preview(commands), HoopProfile.H130X180
        )
        assertTrue(report.isFailure)
        assertTrue(
            report.exceptionOrNull()?.message.orEmpty().contains("penetrações")
        )
    }

    @Test fun trimmedDesignNeedsAJumpBeforeAnotherNeedlePenetration() {
        val commands = listOf(
            p(0,0,StitchCommand.JUMP),
            p(25,0,StitchCommand.STITCH),
            p(25,0,StitchCommand.TRIM),
            p(45,0,StitchCommand.STITCH),
            p(45,0,StitchCommand.END)
        )
        assertTrue(RasterNeedleRiskAudit.inspect(
            preview(commands), HoopProfile.H130X180
        ).isFailure)
    }

    @Test fun moreThanHoopLimitsIsRejected() {
        val commands = listOf(
            p(0,0,StitchCommand.JUMP),
            p(2000,0,StitchCommand.STITCH),
            p(2000,0,StitchCommand.END)
        )
        assertTrue(RasterNeedleRiskAudit.inspect(
            preview(commands), HoopProfile.H100X100
        ).isFailure)
    }

    @Test fun importedPESIsNotModifiedByExperimentalNeedleScreen() {
        val commands = listOf(
            p(0,0,StitchCommand.JUMP),
            p(20,0,StitchCommand.STITCH),
            p(20,0,StitchCommand.END)
        )
        assertTrue(RasterNeedleRiskAudit.inspect(
            preview(commands, format = "PES"), HoopProfile.H130X180
        ).isFailure)
    }
}
