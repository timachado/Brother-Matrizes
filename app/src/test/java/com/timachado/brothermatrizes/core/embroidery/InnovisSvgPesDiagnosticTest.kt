package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressão motivada por vídeo real na Innov-is:
 * a máquina abre um PES gerado pelo Studio SVG 1.11.11, mas a miniatura
 * parece parcialmente desenhada. Este teste NÃO substitui ler o PES real
 * da máquina nem um bordado de prova.
 *
 * Valida um SVG de duas ilhas separadas através de
 * SVG -> Tatami (+ acabamento opcional) -> PES Brother -> reimportação.
 */
class InnovisSvgPesDiagnosticTest {
    private fun rect(
        x0: Float, y0: Float, x1: Float, y1: Float
    ): VectorPath = VectorPath(
        points = listOf(
            VectorPoint(x0, y0),
            VectorPoint(x1, y0),
            VectorPoint(x1, y1),
            VectorPoint(x0, y1)
        ),
        closed = true
    )

    @Test
    fun twoSeparatedRegionsSurviveBrotherMachinePesWithSameDimensions() {
        val svg = VectorArtwork(
            label = "Duas regiões separadas para Innovis",
            paths = listOf(
                rect(0f, 0f, 80f, 90f),
                rect(140f, 10f, 220f, 100f)
            )
        )
        for (finish in listOf(false, true)) {
            val original = VectorMatrixGenerator.generate(
                artwork = svg,
                options = VectorMatrixOptions(
                    widthMm = 60f,
                    fillMode = VectorFillMode.TATAMI,
                    tatamiRowSpacingMm = 0.5f,
                    tatamiContourFinish = finish,
                    hoopProfile = HoopProfile.H100X100,
                    outputFormat = "PES"
                )
            ).getOrThrow()
            val exported = MatrixConverter.convert(
                original, "PES", if (finish) "com-contornos" else "tatami"
            ).getOrThrow()
            assertEquals("#PES0001",
                exported.bytes.copyOfRange(0, 8).toString(Charsets.US_ASCII))

            val reopened = EmbroideryIoParser.parse(
                exported.fileName, exported.bytes
            )
            assertTrue("PES precisa reabrir integralmente: $reopened",
                reopened is EmbroideryLoadResult.Success)
            val actual = (reopened as EmbroideryLoadResult.Success).design
            assertTrue("Largura mudou após gravar PES (acabamento=$finish): " +
                "${original.bounds.widthMm} -> ${actual.bounds.widthMm}",
                abs(original.bounds.widthMm - actual.bounds.widthMm) <= 0.6f)
            assertTrue("Altura mudou após gravar PES (acabamento=$finish): " +
                "${original.bounds.heightMm} -> ${actual.bounds.heightMm}",
                abs(original.bounds.heightMm - actual.bounds.heightMm) <= 0.6f)
            assertTrue("PES perdeu mais de 20% dos pontos (acabamento=$finish)",
                actual.stitchCount >= original.stitchCount * 0.8f)
            assertTrue("PES perdeu o comando END", actual.endFound)

            val stitches = actual.points.filter { it.command == StitchCommand.STITCH }
            val minX = stitches.minOf { it.xUnits }
            val maxX = stitches.maxOf { it.xUnits }
            val xSplitL = minX + (maxX - minX) * 0.37
            val xSplitR = minX + (maxX - minX) * 0.63
            assertTrue("Região esquerda sumiu do PES", stitches.any { it.xUnits < xSplitL })
            assertTrue("Região direita sumiu do PES", stitches.any { it.xUnits > xSplitR })
        }
    }

    @Test
    fun pesDimensionsWithSmallerRequestedSvgAreNotSilentlyReplaced() {
        val artwork = VectorArtwork(
            label = "Região pequena",
            paths = listOf(rect(0f, 0f, 333f, 226f))
        )
        val generated = VectorMatrixGenerator.generate(
            artwork,
            VectorMatrixOptions(
                widthMm = 33.3f,
                fillMode = VectorFillMode.TATAMI,
                tatamiRowSpacingMm = 0.5f,
                outputFormat = "PES"
            )
        ).getOrThrow()
        val pes = MatrixConverter.convert(generated, "PES", "innovis").getOrThrow()
        val parsed = EmbroideryIoParser.parse(
            pes.fileName, pes.bytes
        ) as EmbroideryLoadResult.Success
        assertTrue(abs(generated.bounds.widthMm - parsed.design.bounds.widthMm) <= 0.6f)
        assertTrue(abs(generated.bounds.heightMm - parsed.design.bounds.heightMm) <= 0.6f)
        assertTrue(parsed.design.stitchCount > 0)
    }
}
