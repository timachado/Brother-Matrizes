package com.timachado.brothermatrizes.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrotherMeteredActionTest {
    @Test fun hasSixUniqueWireOperations() {
        assertEquals(3, BrotherMeteredAction.entries.map { it.operation }.toSet().size)
        assertEquals(BrotherMeteredAction.RAR_RECEIVE,
            BrotherMeteredAction.fromOperation("rar_receive"))
    }

    @Test fun cannotTranslateUnknownOperationsIntoAuthorizedActions() {
        assertNull(BrotherMeteredAction.fromOperation("import_matrix"))
        assertNull(BrotherMeteredAction.fromOperation("unlock_pro"))
        assertNull(BrotherMeteredAction.fromOperation(""))
    }
}
