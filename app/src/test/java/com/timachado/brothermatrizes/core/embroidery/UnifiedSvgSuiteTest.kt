package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fluxo de regressão da suíte SVG completa.
 * Exercita exportar → importar SVG → criar contornos/Tatami →
 * gerar matrizes compatíveis. Catálogo online tem testes de URL separados.
 */
class UnifiedSvgSuiteTest {
    private val artwork = VectorArtwork(
        label = "Coração de teste SVG",
        paths = listOf(
            VectorPath(
                listOf(
                    VectorPoint(0f, 0f),
                    VectorPoint(110f, 0f),
                    VectorPoint(110f, 70f),
                    VectorPoint(0f, 70f)
                ),
                closed = true
            )
        )
    )

    @Test
    fun exportImportGenerateTatamiAndOutlineWithoutChangingDefault() {
        val source = VectorSvgExporter.export(artwork).getOrThrow()
        val imported = SimpleSvgParser.parse("desenho-unificado.svg", source).getOrThrow()
        assertEquals(1, imported.paths.size)
        assertTrue(imported.paths.single().closed)

        val baseOptions = VectorMatrixOptions(
            widthMm = 60f,
            fillMode = VectorFillMode.TATAMI,
            tatamiRowSpacingMm = 0.5f,
            outputFormat = "PES",
            hoopProfile = HoopProfile.H100X100
        )
        val tatamiOnly = VectorMatrixGenerator.generate(imported, baseOptions).getOrThrow()
        val withFinish = VectorMatrixGenerator.generate(
            imported, baseOptions.copy(tatamiContourFinish = true)
        ).getOrThrow()
        val tatamiRestored = VectorMatrixGenerator.generate(
            imported, baseOptions.copy(tatamiContourFinish = false)
        ).getOrThrow()

        assertTrue(tatamiOnly.stitchCount > 0)
        assertTrue(withFinish.stitchCount > tatamiOnly.stitchCount)
        assertTrue(withFinish.points.count { it.command == StitchCommand.TRIM } >
            tatamiOnly.points.count { it.command == StitchCommand.TRIM })
        assertEquals(tatamiOnly.points, tatamiRestored.points)
        assertTrue(HoopValidator.validate(withFinish, HoopProfile.H100X100).fits)

        for (format in listOf("PES", "DST", "JEF")) {
            val bytes = MatrixConverter.convert(withFinish, format, "svg-suite-qa")
                .getOrThrow().bytes
            assertTrue("Arquivo $format vazio", bytes.isNotEmpty())
        }
    }

    @Test
    fun runningAndSatinModesRemainAvailableAfterIntegration() {
        for (style in listOf(TextStitchStyle.RUNNING, TextStitchStyle.SATIN)) {
            val result = VectorMatrixGenerator.generate(
                artwork, VectorMatrixOptions(
                    widthMm = 60f,
                    stitchStyle = style,
                    fillMode = VectorFillMode.NONE
                )
            ).getOrThrow()
            assertTrue(result.stitchCount > 0)
            assertTrue(result.endFound)
        }
    }
}
