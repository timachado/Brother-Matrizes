package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualSatinSectionJoinTest {
    private fun rails(startV: Float, shiftU: Float = 0f): List<SatinRailPair> =
        (0..5).map { i ->
            val v = startV + .05f * i
            SatinRailPair(.35f + shiftU, v, .39f + shiftU, v)
        }

    private fun generate(sections: List<ManualSatinSection>): Result<List<EmbroideryPoint>> =
        ManualSatinRails.generate(
            sections = sections,
            outputColorIndex = 0,
            widthMm = 65f,
            imageAspectRatio = 1.8f,
            densityMm = .5f,
            pullCompensationMm = .1f,
            centerUnderlay = true
        )

    @Test fun explicitNearbyJoinHasNoTrimOrHiddenJumpBetweenSegments() {
        val first = ManualSatinSection(rails(.12f))
        val second = ManualSatinSection(rails(.37f), connectToPrevious = true)
        val joined = generate(listOf(first, second)).getOrThrow()
        val unjoined = generate(listOf(first, second.copy(connectToPrevious = false)))
            .getOrThrow()
        assertEquals(0, joined.count { it.command == StitchCommand.TRIM })
        assertEquals(1, unjoined.count { it.command == StitchCommand.TRIM })
        assertTrue(joined.count { it.command == StitchCommand.JUMP } <
            unjoined.count { it.command == StitchCommand.JUMP })
        assertTrue(joined.count { it.command == StitchCommand.STITCH } > 30)
        assertTrue(joined.all { it.colorIndex == 0 })
    }

    @Test fun farApartRailsMustBeTrimmedOrRequireReposition() {
        val first = ManualSatinSection(rails(.12f))
        val second = ManualSatinSection(rails(.37f, shiftU = .3f),
            connectToPrevious = true)
        val result = generate(listOf(first, second))
        assertTrue("Conexão distante não deve costurar por cima do tecido", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("1,5 mm") == true)
    }

    @Test fun firstSectionCannotRequestJoiningToNonexistentSection() {
        val a = ManualSatinSection(rails(.12f), connectToPrevious = true)
        assertTrue(generate(listOf(a)).isFailure)
    }
}
