package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.NameCompositionMode
import com.timachado.brothermatrizes.core.embroidery.NamePersonalization
import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.desktop.platform.DesktopFlpProjectFiles
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * QA LOCAL do formato oficial FLP v11. Não recebe dados de clientes,
 * não necessita login, não envia bytes à rede e não toca em projetos reais.
 */
class DesktopNamePersonalizationPersistenceTest {
    @Test
    fun `editar Maria para Ana preserva a base e sobrevive ao FLP oficial`() {
        val root = TextLayoutGenerator.generate(TextLayoutOptions(
            textOptions = TextMatrixOptions(
                text = "TM", heightMm = 12f,
                style = TextStitchStyle.RUNNING, outputFormat = "DST",
                hoopProfile = null, enforceHoop = false
            )
        )).getOrThrow().copy(hoopProfile = null)
        val basePoints = root.points.toList()
        val first = NamePersonalization.add(
            original = root,
            options = TextMatrixOptions(
                text = "Maria", heightMm = 8f,
                style = TextStitchStyle.SATIN, outputFormat = "DST",
                hoopProfile = null, enforceHoop = false
            ),
            centerXUnits = 0, centerYUnits = 0,
            compositionMode = NameCompositionMode.MONOGRAM_OVERLAY
        ).getOrThrow()
        val firstPoints = first.points.toList()
        val edited = NamePersonalization.add(
            original = first,
            options = first.editableName!!.options.copy(text = "Ana"),
            centerXUnits = first.editableName!!.centerXUnits,
            centerYUnits = first.editableName!!.centerYUnits,
            compositionMode = NameCompositionMode.MONOGRAM_OVERLAY
        ).getOrThrow()

        assertEquals(basePoints, root.points)
        assertEquals(firstPoints, first.points)
        assertNotEquals(firstPoints, edited.points)
        assertEquals("Ana", edited.editableName?.options?.text)
        assertEquals(basePoints, edited.editableName?.original?.points)
        assertEquals(NameCompositionMode.MONOGRAM_OVERLAY,
            edited.editableName?.compositionMode)

        val directory = Files.createTempDirectory("brother-name-flp-qa-")
        val file = directory.resolve("projeto-sintetico.flp")
        try {
            DesktopFlpProjectFiles.saveNew(edited, file)
            val before = Files.readAllBytes(file)
            assertTrue(before.size > 100)
            val restored = DesktopFlpProjectFiles.open(file)
            assertEquals(edited.points, restored.points)
            assertEquals("Ana", restored.editableName?.options?.text)
            assertEquals(basePoints, restored.editableName?.original?.points)
            assertEquals(NameCompositionMode.MONOGRAM_OVERLAY,
                restored.editableName?.compositionMode)

            // CREATE_NEW: rejeitar sobrescrita até quando destino é igual.
            assertFailsWith<Exception> {
                DesktopFlpProjectFiles.saveNew(first, file)
            }
            assertContentEquals(before, Files.readAllBytes(file))
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `persistência rejeita extensão incorreta sem criar arquivo`() {
        val emptyFolder = Files.createTempDirectory("brother-name-flp-ext-qa-")
        val wrong = emptyFolder.resolve("nao-salvar.pes")
        try {
            val original = TextLayoutGenerator.generate(TextLayoutOptions(
                textOptions = TextMatrixOptions(
                    text = "BM", heightMm = 9f,
                    style = TextStitchStyle.RUNNING,
                    outputFormat = "DST",
                    hoopProfile = null, enforceHoop = false
                )
            )).getOrThrow()
            assertFailsWith<IllegalArgumentException> {
                DesktopFlpProjectFiles.saveNew(original, wrong)
            }
            assertTrue(!Files.exists(wrong))
        } finally {
            Files.deleteIfExists(wrong)
            Files.deleteIfExists(emptyFolder)
        }
    }
}
