package com.timachado.brothermatrizes.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OpenTypePositionedStyleTest {
    @Test fun standardNamePreservesOneWholeRun() {
        val style = OpenTypePositionedStyle()
        assertFalse(style.isEnabled)
        assertEquals(listOf(OpenTypePositionedStyle.TextRun("Maria", null)),
            style.runs("Maria"))
    }

    @Test fun separateInitialAndFinalOrnamentsPreserveMiddleWhole() {
        val style = OpenTypePositionedStyle("ss01", "ss02")
        assertTrue(style.isEnabled)
        assertEquals(listOf(
            OpenTypePositionedStyle.TextRun("M", "ss01"),
            OpenTypePositionedStyle.TextRun("ari", null),
            OpenTypePositionedStyle.TextRun("a", "ss02")
        ), style.runs("Maria"))
    }

    @Test fun initialOrFinalOnlyDoesNotRestyleRemainingLetters() {
        assertEquals(listOf(
            OpenTypePositionedStyle.TextRun("M", "aalt"),
            OpenTypePositionedStyle.TextRun("aria", null)
        ), OpenTypePositionedStyle(initialTag = "aalt").runs("Maria"))
        assertEquals(listOf(
            OpenTypePositionedStyle.TextRun("Mari", null),
            OpenTypePositionedStyle.TextRun("a", "ss02")
        ), OpenTypePositionedStyle(finalTag = "ss02").runs("Maria"))
    }

    @Test fun twoLettersHaveNoEmptyMiddleSegment() {
        assertEquals(listOf(
            OpenTypePositionedStyle.TextRun("A", "ss01"),
            OpenTypePositionedStyle.TextRun("n", "ss02")
        ), OpenTypePositionedStyle("ss01", "ss02").runs("An"))
    }

    @Test fun unicodeCodePointsAreNotCutInHalf() {
        assertEquals(listOf(
            OpenTypePositionedStyle.TextRun("A", "ss01"),
            OpenTypePositionedStyle.TextRun("😀", null),
            OpenTypePositionedStyle.TextRun("a", "ss02")
        ), OpenTypePositionedStyle("ss01", "ss02").runs("A😀a"))
    }

    @Test fun noInjectionIntoFeatureTag() {
        try {
            OpenTypePositionedStyle(initialTag = "x');0")
                .runs("Maria")
            fail("Deveria rejeitar tag irregular")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun disallowsFlourishesAtNonLatinBoundariesOrLongNames() {
        for (sample in listOf("1Maria", "Maria!", "L".repeat(12), "A")) {
            try {
                OpenTypePositionedStyle(initialTag = "ss01").runs(sample)
                fail("Deveria rejeitar texto: $sample")
            } catch (_: IllegalArgumentException) {}
        }
    }
}
