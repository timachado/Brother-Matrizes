package com.timachado.brothermatrizes.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrotherMeteredActionTest {
    @Test fun uniqueWireOperationsAndExpectedFreeLimits() {
        assertEquals(4, BrotherMeteredAction.entries.map { it.operation }.toSet().size)
        assertEquals(5, BrotherMeteredAction.RAR_OPEN.freeMonthlyLimit)
        assertEquals(5, BrotherMeteredAction.RAR_SEND.freeMonthlyLimit)
        assertEquals(5, BrotherMeteredAction.TELEGRAM_SEARCH.freeMonthlyLimit)
        assertEquals(10, BrotherMeteredAction.DAFONT_SEARCH.freeMonthlyLimit)
    }

    @Test fun neverTreatArbitraryServerOperationAsAnAction() {
        assertEquals(BrotherMeteredAction.RAR_OPEN, BrotherMeteredAction.fromOperation("rar_open"))
        assertNull(BrotherMeteredAction.fromOperation("import_matrix"))
        assertNull(BrotherMeteredAction.fromOperation("pro"))
        assertNull(BrotherMeteredAction.fromOperation(""))
    }
}
