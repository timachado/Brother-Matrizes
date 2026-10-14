package com.timachado.brothermatrizes.font

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenTypeRunSafetyTest {
    @Test fun wholeRunAcceptsOnlyTheExistingTwoToNineCodePointWindow() {
        assertFalse(OpenTypeRunSafety.allowsShaping(0))
        assertFalse(OpenTypeRunSafety.allowsShaping(1))
        assertTrue(OpenTypeRunSafety.allowsShaping(2))
        assertTrue(OpenTypeRunSafety.allowsShaping(9))
        assertFalse(OpenTypeRunSafety.allowsShaping(10))
        assertFalse(OpenTypeRunSafety.allowsShaping(24))
    }

    @Test fun availableAlternativesMatchTheSatinWidthBudget() {
        assertTrue(OpenTypeRunSafety.allowsAdvance(599f, 100f))
        assertTrue(OpenTypeRunSafety.allowsAdvance(600f, 100f))
        assertFalse(OpenTypeRunSafety.allowsAdvance(600.1f, 100f))
        assertFalse(OpenTypeRunSafety.allowsAdvance(610f, 100f))
    }

    @Test fun invalidMetricsCannotProduceFalsePositiveVariants() {
        assertFalse(OpenTypeRunSafety.allowsAdvance(0f, 100f))
        assertFalse(OpenTypeRunSafety.allowsAdvance(Float.NaN, 100f))
        assertFalse(OpenTypeRunSafety.allowsAdvance(Float.POSITIVE_INFINITY, 100f))
        assertFalse(OpenTypeRunSafety.allowsAdvance(300f, 0f))
        assertFalse(OpenTypeRunSafety.allowsAdvance(300f, Float.NaN))
    }
}
