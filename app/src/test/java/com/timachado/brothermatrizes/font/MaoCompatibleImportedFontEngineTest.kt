package com.timachado.brothermatrizes.font

import org.junit.Assert.assertEquals
import org.junit.Test

class MaoCompatibleImportedFontEngineTest {

    @Test
    fun connectedColumnWinsBeforeEarlierDistantLeg() {
        assertEquals(
            "Uma coluna conectada ao traço atual deve ser concluída antes de viajar para uma perna distante.",
            9,
            MaoCompatibleImportedFontEngine
                .debugContinuitySelection()
        )
    }

    @Test
    fun nextColumnEntersFromNearestEndWithoutChangingRails() {
        assertEquals(
            "A coluna seguinte deve inverter apenas a ordem das rows para entrar pelo extremo mais próximo.",
            100f,
            MaoCompatibleImportedFontEngine
                .debugNearestEndOrientation(),
            0.01f
        )
    }
}
