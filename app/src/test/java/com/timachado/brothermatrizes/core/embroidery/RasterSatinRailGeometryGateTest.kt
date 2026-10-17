package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Geometry is checked BEFORE any proposed Satin is treated as needle paths. */
class RasterSatinRailGeometryGateTest {
    private val ink = 0xffd97924.toInt()
    private val widthMm = 90f
    private val source = RasterImagePixels(
        160, 100, IntArray(160 * 100) { i ->
            val x = i % 160
            val y = i / 160
            if (x in 10..110 && y in 10..60) ink else 0
        }, "coluna Satin QA"
    )
    private val part = RasterSatinObject(
        id = 1, color = ink, pixelCount = 101 * 51,
        mask = SeparatedColorArtwork(
            colors = listOf(ink),
            pixels = IntArray(160 * 100) { i ->
                val x = i % 160
                val y = i / 160
                if (x in 10..110 && y in 10..60) 0 else -1
            },
            minX = 10, maxX = 110, minY = 10, maxY = 60
        )
    )
    private fun rail(x: Float, y: Float, half: Float = .055f) =
        SatinRailPair(x,y-half,x,y+half)

    private fun audit(
        rails: List<SatinRailPair>,
        holes: Int = 0
    ) = RasterSatinRailGeometryGate.inspect(
        source, part, listOf(ManualSatinSection(rails)), widthMm, holes
    )

    @Test fun smoothlyCurvedAndGraduallyTaperedSatinCanBeReviewed() {
        val rails = listOf(
            rail(.10f,.45f,.050f),
            rail(.30f,.43f,.055f),
            rail(.50f,.48f,.070f),
            rail(.70f,.53f,.072f),
            rail(.90f,.56f,.060f)
        )
        val result = audit(rails).getOrThrow()
        assertEquals(1, result.sectionCount)
        assertEquals(5, result.inspectedPairs)
        assertTrue(result.minWidthMm > .78)
        assertTrue(result.maxWidthMm < 7.0)
        assertTrue(result.maxAdjacentWidthRatio < 3.0)
        assertTrue(result.maxCenterTurnDegrees < 120.0)
        assertFalse(result.exportPermitted)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun invertedLeftRightRailPairCannotTwistTheStitches() {
        val rails = listOf(
            rail(.15f,.50f), rail(.45f,.50f),
            SatinRailPair(.75f,.56f,.75f,.44f)
        )
        assertTrue(audit(rails).isFailure)
    }

    @Test fun suddenThicknessChangeIsRefused() {
        val rails = listOf(
            rail(.15f,.5f,.015f),
            rail(.45f,.5f,.145f),
            rail(.75f,.5f,.145f)
        )
        // 0.86 mm vs 8.3 mm: cannot be sewn as one stable Satin column.
        assertTrue(audit(rails).isFailure)
    }

    @Test fun excessivelyWideColumnMustNotBecomeLongSatinSpans() {
        val rails = listOf(
            rail(.15f,.50f,.17f), rail(.55f,.50f,.17f),
            rail(.85f,.50f,.17f)
        )
        assertTrue(audit(rails).isFailure)
    }

    @Test fun stationaryOrHairpinCenterlineIsRejected() {
        assertTrue(audit(listOf(
            rail(.20f,.50f), rail(.20f,.50f), rail(.65f,.50f)
        )).isFailure)
        assertTrue(audit(listOf(
            rail(.20f,.50f), rail(.75f,.50f), rail(.30f,.50f)
        )).isFailure)
    }

    @Test fun holeMustNotBeBridgedByConnectingSeparateSections() {
        val sections = listOf(
            ManualSatinSection(listOf(
                rail(.12f,.45f), rail(.36f,.45f), rail(.48f,.45f)
            )),
            ManualSatinSection(listOf(
                rail(.51f,.45f), rail(.70f,.45f), rail(.85f,.45f)
            ), connectToPrevious = true)
        )
        assertTrue(RasterSatinRailGeometryGate.inspect(
            source,part,sections,widthMm,enclosedHoles=1
        ).isFailure)
        assertTrue(RasterSatinRailGeometryGate.inspect(
            source,part,sections.map { it.copy(connectToPrevious=false) },
            widthMm,enclosedHoles=1
        ).isSuccess)
    }

    @Test fun outOfCanvasOrInvalidNumbersFailClosed() {
        assertTrue(audit(listOf(
            rail(.1f,.5f), rail(1.1f,.5f)
        )).isFailure)
        assertTrue(audit(listOf(
            rail(.1f,.5f), rail(Float.NaN,.5f)
        )).isFailure)
        assertTrue(RasterSatinRailGeometryGate.inspect(
            source,part,emptyList(),widthMm,0
        ).isFailure)
    }
}
