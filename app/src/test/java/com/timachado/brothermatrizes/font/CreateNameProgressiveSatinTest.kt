package com.timachado.brothermatrizes.font

import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateNameProgressiveSatinTest {

    @Test
    fun progressiveUnderlayDoesNotReturnAcrossFinishedRows() {
        val stitches =
            ReferenceImportedFontEngine
                .debugProgressiveCenterUnderlayPath()
                .filter {
                    it.command ==
                        StitchCommand.STITCH
                }

        val xSequence =
            stitches.map {
                it.xUnits
            }

        assertTrue(
            "Criar Nome não pode percorrer a região inteira e depois voltar para repeti-la.",
            xSequence
                .zipWithNext()
                .all {
                        pair ->
                    pair.second >=
                        pair.first -
                            3
                }
        )
    }

    @Test
    fun progressiveSatinUsesLocalizedCoverageInsteadOfWholeColumnReplay() {
        val points =
            ReferenceImportedFontEngine
                .debugReferencePath(
                    connected =
                        true,
                    includeUnderlay =
                        true
                )

        assertTrue(
            points.any {
                it.command ==
                    StitchCommand.STITCH
            }
        )

        assertTrue(
            "Regiões conectadas não devem criar TRIM/reinício entre passadas locais.",
            points.none {
                it.command ==
                    StitchCommand.TRIM
            }
        )
    }
}
