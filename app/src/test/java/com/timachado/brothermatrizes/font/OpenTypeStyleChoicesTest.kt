package com.timachado.brothermatrizes.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenTypeStyleChoicesTest {
    @Test fun defaultStyleNeverEnablesAlternates() {
        val choices = OpenTypeStyleChoices.fromTags(setOf("ss02", "ss01", "aalt"))
        assertNull(choices.first().tag)
        assertNull(choices.first().settings)
        assertEquals(listOf(null, "ss01", "ss02", "aalt"), choices.map { it.tag })
    }

    @Test fun filtersOutAutomaticLigaturesAndUnknownTags() {
        val choices = OpenTypeStyleChoices.fromTags(
            setOf("liga", "kern", "calt", "ss01", "ABCD", "aalt")
        )
        assertEquals(listOf(null, "ss01", "aalt"), choices.map { it.tag })
    }

    @Test fun rejectedTagsCannotBeInjectedIntoAndroidPaint() {
        val choices = OpenTypeStyleChoices.fromTags(setOf("ss01", "aalt"))
        assertNull(OpenTypeStyleChoices.fontSettings("xxx';bad"))
        assertNull(OpenTypeStyleChoices.verifiedTag("ss02", choices))
        assertNull(OpenTypeStyleChoices.verifiedTag("xxxx", choices))
        assertEquals("ss01", OpenTypeStyleChoices.verifiedTag("ss01", choices))
        assertEquals("'ss01' 1", OpenTypeStyleChoices.fontSettings("ss01"))
        assertTrue(OpenTypeStyleChoices.isAllowedTag("ss20"))
    }

    @Test fun fontsWithoutOptionalStylesOnlyHaveDefault() {
        val rows = OpenTypeStyleChoices.fromTags(emptySet())
        assertEquals(1, rows.size)
        assertEquals("Padrão", rows.first().label)
    }
}
