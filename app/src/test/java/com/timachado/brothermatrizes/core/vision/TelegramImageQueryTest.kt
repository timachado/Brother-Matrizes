package com.timachado.brothermatrizes.core.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelegramImageQueryTest {
    @Test fun recognizesNameFromEmbroideredCaption() {
        assertEquals("priscila", TelegramImageQuery.fromOcr("Matriz PRISCILA"))
    }
    @Test fun recognizesDistinctiveEmbroideryCode() {
        assertEquals("bm-98734", TelegramImageQuery.fromOcr("Bordado BM-98734 disponível"))
    }
    @Test fun rejectsGenericImageLabels() {
        assertNull(TelegramImageQuery.fromLabels(
            listOf("tableware" to 0.99f, "flower" to 0.98f)
        ))
    }
    @Test fun usesOnlySpecificVisualSubjectsWhenNoName() {
        assertEquals("girassol", TelegramImageQuery.choose(
            "", listOf("flower" to 0.98f, "sunflower" to 0.91f)
        ))
    }
    @Test fun blankImageHasNoSearchQuery() {
        assertNull(TelegramImageQuery.choose("", emptyList()))
    }
}
