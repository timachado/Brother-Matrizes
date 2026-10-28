package com.timachado.brothermatrizes

import com.timachado.brothermatrizes.core.embroidery.EmbroideryBounds
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class HoopAutoSelectionTest {

    @Test
    fun portrait178mmNeedsHoopWithSafe180mmLength() {
        // A borda de 5 mm em cada lado deixa somente 170 mm úteis no H130X180.
        assertEquals(
            HoopProfile.H140X200,
            recommendedHoopFor(
                design(
                    widthUnits = 692,
                    heightUnits = 1784
                )
            )
        )
    }

    @Test
    fun portrait128mmUses130x180InsteadOf100x100() {
        assertEquals(
            HoopProfile.H130X180,
            recommendedHoopFor(
                design(
                    widthUnits = 702,
                    heightUnits = 1281
                )
            )
        )
    }

    @Test
    fun landscape166mmUsesSame130x180PhysicalHoop() {
        assertEquals(
            HoopProfile.H130X180,
            recommendedHoopFor(
                design(
                    widthUnits = 1664,
                    heightUnits = 476
                )
            )
        )
    }

    @Test
    fun smallMatrix95mmNeedsMoreThan100x100WithSafetyMargin() {
        // 100×100 mm tem área útil 90×90 mm; 95 mm não cabe com segurança.
        assertEquals(
            HoopProfile.H130X180,
            recommendedHoopFor(
                design(
                    widthUnits = 950,
                    heightUnits = 950
                )
            )
        )
    }


    @Test
    fun uploadedPes858x1202Uses130x180Automatically() {
        assertEquals(
            HoopProfile.H130X180,
            recommendedHoopFor(
                design(
                    widthUnits =
                        858,
                    heightUnits =
                        1202
                )
            )
        )
    }

    private fun design(
        widthUnits: Int,
        heightUnits: Int
    ): EmbroideryDesign =
        EmbroideryDesign(
            fileName = "regression.pes",
            format = "PES",
            label = null,
            points = emptyList(),
            bounds =
                EmbroideryBounds(
                    minXUnits = 0,
                    maxXUnits = widthUnits,
                    minYUnits = 0,
                    maxYUnits = heightUnits
                ),
            stitchCount = 0,
            jumpCount = 0,
            colorChanges = 0,
            endFound = true,
            sourceBytes =
                byteArrayOf(1)
        )
}
