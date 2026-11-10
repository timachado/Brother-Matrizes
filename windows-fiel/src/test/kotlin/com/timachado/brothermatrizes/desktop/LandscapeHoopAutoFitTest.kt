package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.landscapeHoopAutoScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LandscapeHoopAutoFitTest {
    @Test fun alreadyFitsWithoutChangingStitches() {
        assertEquals(1f, landscapeHoopAutoScale(100f, 70f, 170f, 120f))
    }
    @Test fun reframePortraitSourceInLandscapeHoop() {
        val f = landscapeHoopAutoScale(130f, 160f, 170f, 120f)
        assertTrue(f < 1f && f * 130f <= 170f && f * 160f <= 120f)
    }
    @Test fun reframeWideSourceInLandscapeHoop() {
        val f = landscapeHoopAutoScale(220f, 90f, 170f, 120f)
        assertTrue(f < 1f && f * 220f <= 170f && f * 90f <= 120f)
    }
    @Test fun rejectInvalidDesignBounds() {
        assertFailsWith<IllegalArgumentException> {
            landscapeHoopAutoScale(0f, 80f, 170f, 120f)
        }
        assertFailsWith<IllegalArgumentException> {
            landscapeHoopAutoScale(Float.NaN, 80f, 170f, 120f)
        }
    }
}
