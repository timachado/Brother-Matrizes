package com.timachado.brothermatrizes

import com.timachado.brothermatrizes.core.embroidery.EmbroideryBounds
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendedHoopForNameTest {
    private fun mock(widthUnits: Int, heightUnits: Int): EmbroideryDesign =
        EmbroideryDesign(
            fileName = "moldura.pes",
            format = "PES",
            label = null,
            points = emptyList(),
            bounds = EmbroideryBounds(0, widthUnits, 0, heightUnits),
            stitchCount = 0,
            jumpCount = 0,
            colorChanges = 0,
            endFound = true,
            sourceBytes = byteArrayOf()
        )

    @Test
    fun crest193By110NeedsHoopLargerThanNominal140By200() {
        // O bastidor 140x200 tem apenas 130x190 mm ÚTEIS.
        val selected = recommendedHoopFor(mock(1930, 1100))
        assertEquals(HoopProfile.H160X260, selected)
    }

    @Test
    fun portraitFloral105By157CanUse130By180WithSafetyMargin() {
        val selected = recommendedHoopFor(mock(1050, 1570))
        assertEquals(HoopProfile.H130X180, selected)
    }
}
