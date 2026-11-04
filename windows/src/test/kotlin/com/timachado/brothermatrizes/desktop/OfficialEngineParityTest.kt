package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.DstParser
import com.timachado.brothermatrizes.core.embroidery.EditTransform
import com.timachado.brothermatrizes.core.embroidery.EmbroideryLoadResult
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.MatrixEditor
import com.timachado.brothermatrizes.core.embroidery.PesExportProfile
import com.timachado.brothermatrizes.core.embroidery.TextMatrixGenerator
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Executa o motor oficial Android no JVM/Windows, não uma cópia adaptada.
 * Testes de arquivos reais e PE-DESIGN 11 ainda são bloqueadores comerciais.
 */
class OfficialEngineParityTest {
    private fun fixture(): ByteArray = ByteArray(512) { 0x20 } +
        byteArrayOf(0x01, 0x00, 0x03, 0x01, 0x00, 0x03,
            0x00, 0x00, 0xF3.toByte())

    @Test
    fun `Windows usa a mesma geometria e contagem do parser Android`() {
        val data = fixture()
        val official = DstParser.parse("origem.dst", data) as EmbroideryLoadResult.Success
        val desktop = DstPreviewReader.read("origem.dst", data)
        assertEquals(official.design.stitchCount, desktop.stitchCount)
        assertEquals(official.design.jumpCount, desktop.jumpCount)
        assertEquals(official.design.bounds.widthMm.toDouble(), desktop.bounds.widthMm)
        assertEquals(official.design.points.map { it.xUnits }, desktop.points.map { it.x })
    }

    @Test
    fun `Editor oficial conserva projeto original ao aplicar deslocamento`() {
        val design = (DstParser.parse("origem.dst", fixture()) as EmbroideryLoadResult.Success).design
        val untouched = design.points.map { it.xUnits to it.yUnits }
        val edited = MatrixEditor.apply(design, EditTransform(offsetXUnits = 20, offsetYUnits = 15))
        assertTrue(edited.isModified)
        assertEquals(untouched, design.points.map { it.xUnits to it.yUnits })
        assertEquals(design.points[0].xUnits + 20, edited.points[0].xUnits)
        assertEquals(design.points[0].yUnits + 15, edited.points[0].yUnits)
    }

    @Test
    fun `Gerador oficial cria nome com pontadas para a versão Windows`() {
        val design = TextMatrixGenerator.generate(
            TextMatrixOptions(text = "BM", heightMm = 12f, outputFormat = "DST")
        ).getOrThrow()
        assertTrue(design.stitchCount > 0)
        assertTrue(design.points.isNotEmpty())
    }

    @Test
    fun `Conversor oficial cria DST sem alterar o projeto de origem`() {
        val design = (DstParser.parse("origem.dst", fixture()) as EmbroideryLoadResult.Success).design
        val data = fixture()
        val converted = MatrixConverter.convert(
            design, targetFormat = "DST", outputSuffix = "windows_qa"
        ).getOrThrow()
        assertTrue(converted.bytes.size > 512)
        assertTrue(converted.fileName.endsWith(".dst"))
        assertTrue(design.sourceBytes.contentEquals(data))
    }

    @Test
    fun `Conversor oficial produz PES para perfil PE Design PC`() {
        val design = TextMatrixGenerator.generate(
            TextMatrixOptions(text = "BM", heightMm = 12f, outputFormat = "PES")
        ).getOrThrow()
        val pes = MatrixConverter.convert(
            design,
            targetFormat = "PES",
            outputSuffix = "pe_design_windows_qa",
            pesExportProfile = PesExportProfile.PE_DESIGN_PC
        ).getOrThrow()
        assertTrue(pes.bytes.size > 12)
        assertEquals("#PES", pes.bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        // Este teste não comprova abertura no PE-DESIGN 11 (Windows real).
    }
}
