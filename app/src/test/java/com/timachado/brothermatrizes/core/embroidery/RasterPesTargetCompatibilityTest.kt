package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class RasterPesTargetCompatibilityTest {
    private fun sample(
        hoop: HoopProfile = HoopProfile.H130X180,
        steps: Int = 2
    ): EmbroideryDesign {
        val points = mutableListOf(EmbroideryPoint(0,0,StitchCommand.JUMP,0))
        repeat(steps) { i ->
            points += EmbroideryPoint((i+1)*15,0,StitchCommand.STITCH,0)
        }
        points += EmbroideryPoint(steps*15,0,StitchCommand.END,0)
        return EmbroideryDesign(
            fileName = "QA-PES-universal-not-certified.qa",
            format = "QA_AUTO_STITCH",
            label = "prévia",
            points = points,
            bounds = EmbroideryBounds(0,steps*15,0,0),
            stitchCount = steps, jumpCount = 1,
            colorChanges = 0, endFound = true,
            sourceBytes = ByteArray(0),
            threadColors = listOf(0xff133956.toInt()),
            isModified = true, hoopProfile = hoop
        )
    }

    /** Test-only fictional limits; NEVER presented as Brother model data. */
    private fun describedProfile(
        width: Float = 100f,
        height: Float = 100f,
        maxStitches: Int = 100,
        supported: Set<Int> = setOf(6)
    ) = PesTargetProfile(
        model = "SAMPLE TEST PROFILE, NOT A REAL MACHINE",
        usableWidthMm = width, usableHeightMm = height,
        maxStitches = maxStitches,
        maxColorChanges = 20,
        acceptedPesVersions = supported,
        limitsVerifiedAgainstManual = true
    )

    @Test fun universalPESIsNeverTreatedAsVerifiedModel() {
        val result = RasterPesTargetCompatibility.assess(
            sample(), HoopProfile.H130X180
        ).getOrThrow()
        assertEquals(2,result.stitchCount)
        assertFalse(result.machineProfileConfirmed)
        assertFalse(result.pesVersionConfirmed)
        assertFalse(result.exportPermitted)
        assertTrue(result.warnings.any { it.contains("Modelo") })
    }

    @Test fun evenVerifiedMetadataDoesNotEnableMachineExport() {
        val report = RasterPesTargetCompatibility.assess(
            sample(), HoopProfile.H130X180,
            describedProfile(), intendedPesVersion = 6
        ).getOrThrow()
        assertTrue(report.machineProfileConfirmed)
        assertTrue(report.pesVersionConfirmed)
        assertFalse(report.exportPermitted)
        assertTrue(report.warnings.any { it.contains("teste físico") })
    }

    @Test fun declaredMachineWithSmallerHoopRefusesCoordinates() {
        val original = sample()
        val offset = original.copy(
            points = original.points.map {
                it.copy(xUnits = it.xUnits + 500)
            },
            bounds = EmbroideryBounds(500,530,0,0)
        )
        val check = RasterPesTargetCompatibility.assess(
            offset, HoopProfile.H130X180,
            describedProfile(width = 90f)
        )
        assertTrue(check.isFailure)
    }

    @Test fun stitchCountCannotExceedTargetProfile() {
        val check = RasterPesTargetCompatibility.assess(
            sample(steps = 3), HoopProfile.H130X180,
            describedProfile(maxStitches = 2)
        )
        assertTrue(check.isFailure)
    }

    @Test fun unsupportedPESGenerationIsRejected() {
        val check = RasterPesTargetCompatibility.assess(
            sample(), HoopProfile.H130X180,
            describedProfile(supported = setOf(5)),
            intendedPesVersion = 6
        )
        assertTrue(check.isFailure)
    }

    @Test fun unverifiedIncompleteProfileCannotClaimDocumentation() {
        val broken = PesTargetProfile(
            model = "Unknown Brother", usableWidthMm = null,
            usableHeightMm = 100f, maxStitches = null,
            maxColorChanges = null, acceptedPesVersions = null,
            limitsVerifiedAgainstManual = true
        )
        assertTrue(RasterPesTargetCompatibility.assess(
            sample(),HoopProfile.H130X180,broken
        ).isFailure)
    }

    @Test fun actualPESOrMismatchedHoopCannotEnterQAGate() {
        assertTrue(RasterPesTargetCompatibility.assess(
            sample().copy(format = "PES"), HoopProfile.H130X180
        ).isFailure)
        assertTrue(RasterPesTargetCompatibility.assess(
            sample(), HoopProfile.H100X100
        ).isFailure)
    }
}
