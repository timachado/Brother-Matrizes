package com.timachado.brothermatrizes.core.embroidery

import org.embroideryio.embroideryio.EmbConstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmbroideryIoCommandTest {
    @Test
    fun preservesGeometryCommandsFromEmbroideryIo() {
        assertEquals(
            StitchCommand.STITCH,
            mapEmbroideryIoCommand(
                EmbConstant.SEW_TO
            )
        )

        assertEquals(
            StitchCommand.STITCH,
            mapEmbroideryIoCommand(
                EmbConstant.NEEDLE_AT
            )
        )

        assertEquals(
            StitchCommand.JUMP,
            mapEmbroideryIoCommand(
                EmbConstant.STITCH_BREAK
            )
        )

        assertEquals(
            StitchCommand.TRIM,
            mapEmbroideryIoCommand(
                EmbConstant.SEQUENCE_BREAK
            )
        )

        assertEquals(
            StitchCommand.COLOR_CHANGE,
            mapEmbroideryIoCommand(
                EmbConstant.COLOR_BREAK
            )
        )
    }

    @Test
    fun ignoresEncoderOnlySpeedCommands() {
        assertNull(
            mapEmbroideryIoCommand(
                EmbConstant.SLOW
            )
        )

        assertNull(
            mapEmbroideryIoCommand(
                EmbConstant.FAST
            )
        )
    }
}
