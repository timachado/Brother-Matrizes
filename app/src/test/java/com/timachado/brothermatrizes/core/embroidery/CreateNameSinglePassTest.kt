package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertTrue
import org.junit.Test

class CreateNameSinglePassTest {

    @Test
    fun disablingUnderlayRemovesTheExtraForwardAndReversePasses() {
        val singlePass =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "MARIA",
                        heightMm = 18f,
                        style =
                            TextStitchStyle.SATIN,
                        satinUnderlayMode =
                            SatinUnderlayMode.NONE
                    )
                )
                .getOrThrow()

        val repeatedPasses =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "MARIA",
                        heightMm = 18f,
                        style =
                            TextStitchStyle.SATIN,
                        satinUnderlayMode =
                            SatinUnderlayMode.CENTER
                    )
                )
                .getOrThrow()

        assertTrue(
            "Sem underlay o Criar Nome deve ter menos pontadas e não repetir a passada central de ida/volta.",
            singlePass.stitchCount <
                repeatedPasses.stitchCount
        )
    }
}
