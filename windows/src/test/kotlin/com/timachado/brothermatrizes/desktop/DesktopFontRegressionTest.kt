package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.font.AndroidSkiaOutlineExtractor
import com.timachado.brothermatrizes.font.ImportedFont
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regressão de shaping de fontes presentes no próprio Windows runner.
 *
 * Não instala nem distribui fontes de terceiros. Caracteres e palavras
 * conhecidas verificam que o contorno permanece FINITO e não perde todos
 * os glifos. Comparação de costura física e equivalência Skia/Java2D
 * continuam fora do alcance do teste automatizado.
 */
class DesktopFontRegressionTest {
    private fun availableFonts(): List<Path> {
        val dir = System.getenv("WINDIR") ?: return emptyList()
        return listOf(
            "arial.ttf", "segoeui.ttf", "georgia.ttf",
            "segoesc.ttf", // Segoe Script, se instalada
            "comic.ttf", // Comic Sans, se instalada
            "calibri.ttf"
        ).map { Path.of(dir, "Fonts", it) }
            .filter { Files.isRegularFile(it) }
            .take(5)
    }

    @Test
    fun `palavras inteiras e pares com possiveis ligaduras mantem contornos validos`() {
        val fonts = availableFonts()
        if (fonts.isEmpty()) return // sem fontes de sistema no ambiente
        val samples = listOf("ANA", "office", "fl", "OO")
        for (file in fonts) {
            val data = ImportedFont(
                id = "teste-local", displayName = file.fileName.toString(),
                fileName = file.fileName.toString(), extension = "ttf",
                absolutePath = file.toAbsolutePath().toString()
            )
            for (word in samples) {
                val shape = AndroidSkiaOutlineExtractor.extract(
                    font = data, text = word,
                    targetCapHeightUnits = 180f,
                    spacingUnits = 0f, rotationDegrees = 0f
                )
                val points = shape.glyphPolygons.flatten().flatten()
                assertTrue(points.size >= 6, "${file.fileName}: $word sem contorno suficiente")
                assertTrue(points.all { it.x.isFinite() && it.y.isFinite() },
                    "${file.fileName}: $word com coordenadas inválidas")
                assertTrue(shape.sourceWidth > 2f && shape.sourceHeight > 2f,
                    "${file.fileName}: $word com dimensão inválida")
                assertTrue(shape.glyphPolygons.size in 1..word.length,
                    "${file.fileName}: $word com quantidade de grupos inesperada")
            }
        }
    }

    @Test
    fun `contraforma da letra O nao desaparece na extracao Java2D`() {
        val file = availableFonts().firstOrNull() ?: return
        val imported = ImportedFont(
            id = "teste", displayName = file.fileName.toString(),
            fileName = file.fileName.toString(), extension = "ttf",
            absolutePath = file.toAbsolutePath().toString()
        )
        val shape = AndroidSkiaOutlineExtractor.extract(imported, "O", 180f, 0f, 0f)
        assertTrue(shape.glyphPolygons.any { it.size >= 2 },
            "Letra O perdeu o contorno interno; Satin não é seguro")
    }

    @Test
    fun `palavra ANA usa motor Satin oficial e produz DST relido`() {
        val file = availableFonts().firstOrNull() ?: return
        val document = DesktopMatrixService.createFromImportedFont(
            fontPath = file, text = "ANA", heightMm = 18f,
            densityMm = 0.4f, spacingMm = 0f
        )
        assertTrue(document.current.stitchCount > 50)
        assertTrue(document.current.endFound)
        val result = DesktopMatrixService.convert(document, "DST")
        assertTrue(result.bytes.size > 512)
        val reloaded = DesktopMatrixService.open(result.fileName, result.bytes)
        assertTrue(reloaded.current.stitchCount > 0)
    }

    @Test
    fun `parametros de densidade e entreletras invalidos sao recusados`() {
        val file = Path.of("fonte-inexistente.ttf")
        for (density in listOf(Float.NaN, -0.3f, 2.0f)) {
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                DesktopMatrixService.createFromImportedFont(file, "ANA", 18f, density, 0f)
            }
        }
        for (spacing in listOf(Float.NaN, -1f, 4f)) {
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                DesktopMatrixService.createFromImportedFont(file, "ANA", 18f, 0.4f, spacing)
            }
        }
    }

    @Test
    fun `repeticao de geometria e rotacao nao alteram tamanho do arquivo fonte`() {
        val file = availableFonts().firstOrNull() ?: return
        val imported = ImportedFont(
            id = "teste", displayName = "fonte",
            fileName = file.fileName.toString(), extension = "ttf",
            absolutePath = file.toAbsolutePath().toString()
        )
        val before = Files.size(file)
        val first = AndroidSkiaOutlineExtractor.extract(imported, "ANA", 160f, 0f, 0f)
        val second = AndroidSkiaOutlineExtractor.extract(imported, "ANA", 160f, 0f, 0f)
        assertEquals(first.glyphPolygons, second.glyphPolygons)
        val rotated = AndroidSkiaOutlineExtractor.extract(imported, "ANA", 160f, 0f, 90f)
        assertTrue(rotated.glyphPolygons.isNotEmpty())
        assertEquals(before, Files.size(file))
    }
}
