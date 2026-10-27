package com.timachado.brothermatrizes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timachado.brothermatrizes.core.embroidery.*
import com.timachado.brothermatrizes.core.project.ProjectCodec
import com.timachado.brothermatrizes.font.ImportedFont
import com.timachado.brothermatrizes.font.ImportedFontMatrixGenerator
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * QA Android real (emulador): exercita o editor Compose e o simulador nativo.
 * Moldura SINTÉTICA, não substitui o PES original MOLDURA FLORES nem teste físico.
 */
@RunWith(AndroidJUnit4::class)
class NameEditorAndroidQaTest {
    @get:Rule val composeRule = createComposeRule()

    private fun frame(): EmbroideryDesign {
        val points = listOf(
            EmbroideryPoint(-380, -380, StitchCommand.JUMP, 0),
            EmbroideryPoint(-380, 380, StitchCommand.STITCH, 0),
            EmbroideryPoint(380, 380, StitchCommand.STITCH, 0),
            EmbroideryPoint(380, -380, StitchCommand.STITCH, 0),
            EmbroideryPoint(-380, -380, StitchCommand.STITCH, 0),
            EmbroideryPoint(-380, -380, StitchCommand.END, 0)
        )
        return EmbroideryIntegrity.normalize(
            EmbroideryDesign(
                fileName = "qa-moldura-flores.pes",
                format = "PES",
                label = "QA - MOLDURA SINTÉTICA",
                points = points,
                bounds = EmbroideryBounds(-380, 380, -380, 380),
                stitchCount = 4,
                jumpCount = 1,
                colorChanges = 0,
                endFound = true,
                sourceBytes = ByteArray(0),
                threadColors = listOf(0x17846B),
                hoopProfile = HoopProfile.H100X100
            )
        ).getOrThrow().design
    }

    @Test
    fun mariaCanChangeToSophiaAndOpenNativeSimulator() {
        val original = frame()
        var submitted: EmbroideryDesign? = null
        var displayed by mutableStateOf<EmbroideryDesign?>(null)

        composeRule.setContent {
            val current = displayed
            if (current == null) {
                NameEditorScreen(
                    original = original,
                    referenceHoop = HoopProfile.H100X100,
                    onBack = {},
                    onApply = {
                        submitted = it
                        displayed = it
                    }
                )
            } else {
                SimulatorScreen(
                    design = current,
                    referenceHoop = HoopProfile.H100X100,
                    onBack = {}
                )
            }
        }
        composeRule.onNodeWithTag("brother_name_input").assertExists()
            .performTextClearance()
        composeRule.onNodeWithTag("brother_name_input")
            .performTextInput("Sophia")
        composeRule.waitUntil(timeoutMillis = 30_000) {
            !composeRule.onNodeWithTag("brother_name_apply")
                .fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
        }
        composeRule.onNodeWithTag("brother_name_apply").performScrollTo()
            .performClick()
        composeRule.runOnIdle {
            // O SimulatorScreen não tem obrigação de expor o título "Simulação".
            // Verificar a navegação por seu estado real, não por uma string UI.
            assertTrue(displayed != null)
            val edited = requireNotNull(submitted)
            assertEquals("Sophia", edited.editableName?.options?.text)
            assertEquals(original.points.dropLast(1),
                edited.points.take(original.points.size - 1))
            assertTrue(edited.stitchCount > original.stitchCount)

            // Fechamento, reabertura e exportação da mesma sequência de pontos.
            val restored = ProjectCodec.decode(ProjectCodec.encode(edited))
            assertEquals("Sophia", restored.editableName?.options?.text)
            val pes = MatrixConverter.convert(restored, "PES", "qa-android")
                .getOrThrow()
            val dst = MatrixConverter.convert(restored, "DST", "qa-android")
                .getOrThrow()
            for (file in listOf(pes, dst)) {
                val opened = EmbroideryLoader.loadBytes(file.fileName, file.bytes)
                assertTrue(
                    "Não reabriu " + file.format,
                    opened is EmbroideryLoadResult.Success
                )
            }
        }
    }

    @Test
    fun directTouchHandleMovesNameAndSimpleRotationIsSaved() {
        val source = frame()
        var applied: EmbroideryDesign? = null
        composeRule.setContent {
            NameEditorScreen(
                original = source,
                referenceHoop = HoopProfile.H100X100,
                onBack = {},
                onApply = { applied = it }
            )
        }
        // Sem alternar modos: botões de giro e alça são visíveis imediatamente.
        composeRule.onNodeWithTag("brother_name_rotate_right")
            .assertExists().performClick()
        composeRule.onNodeWithTag("brother_name_drag_handle")
            .assertExists()
            .performTouchInput {
                swipe(
                    start = center,
                    end = center + androidx.compose.ui.geometry.Offset(25f, 0f),
                    durationMillis = 330
                )
            }
        composeRule.waitUntil(timeoutMillis = 30_000) {
            !composeRule.onNodeWithTag("brother_name_apply")
                .fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
        }
        composeRule.onNodeWithTag("brother_name_apply")
            .performScrollTo().performClick()
        composeRule.runOnIdle {
            val edited = requireNotNull(applied)
            assertEquals(15f, edited.editableName?.options?.rotationDegrees ?: -1f)
            assertTrue((edited.editableName?.centerXUnits ?: 0) > 0)
            assertEquals(source.points.dropLast(1),
                edited.points.take(source.points.size - 1))
        }
    }

    @Test
    fun portrait13x18AllowsAddingNameWithCorrectHoopOrientation() {
        val sample = frame()
        val points = listOf(
            EmbroideryPoint(-820, -691, StitchCommand.JUMP, 0),
            EmbroideryPoint(-820, 885, StitchCommand.STITCH, 0),
            EmbroideryPoint(234, 885, StitchCommand.STITCH, 0),
            EmbroideryPoint(234, -691, StitchCommand.STITCH, 0),
            EmbroideryPoint(-820, -691, StitchCommand.STITCH, 0),
            EmbroideryPoint(-820, -691, StitchCommand.END, 0)
        )
        val portrait = EmbroideryIntegrity.normalize(
            sample.copy(
                fileName = "qa-moldura-13x18-retrato.pes",
                points = points,
                hoopProfile = HoopProfile.H130X180,
                sourceYAxisDown = true
            )
        ).getOrThrow().design
        var applied: EmbroideryDesign? = null
        composeRule.setContent {
            NameEditorScreen(
                original = portrait,
                referenceHoop = HoopProfile.H130X180,
                onBack = {},
                onApply = { applied = it }
            )
        }
        composeRule.waitUntil(timeoutMillis = 30_000) {
            !composeRule.onNodeWithTag("brother_name_apply")
                .fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
        }
        composeRule.onNodeWithTag("brother_name_apply")
            .performScrollTo().performClick()
        composeRule.runOnIdle {
            val result = requireNotNull(applied)
            assertEquals(HoopProfile.H130X180, result.hoopProfile)
            assertTrue(result.bounds.widthMm <= 120f)
            assertTrue(result.bounds.heightMm <= 170f)
            assertEquals(portrait.points.dropLast(1),
                result.points.take(portrait.points.size - 1))
        }
    }

    @Test
    fun realTtfFontCreatesEditableStitchesAndAutoFitsWithoutFallback() {
        // Usa um TTF real presente no emulador, sem baixar nem armazenar
        // material de terceiros no repositório público.
        val fontFile = listOf(
            File("/system/fonts/Roboto-Regular.ttf"),
            File("/system/fonts/NotoSans-Regular.ttf")
        ).firstOrNull { it.isFile }
            ?: error("Fonte TTF de sistema ausente no emulador.")
        val font = ImportedFont(
            id = fontFile.name, displayName = "QA TTF",
            fileName = fontFile.name, extension = "ttf",
            absolutePath = fontFile.absolutePath
        )
        val source = frame()
        val options = TextMatrixOptions(
            text = "Ana", importedFontId = font.id,
            heightMm = 8f, spacingMm = 0f,
            style = TextStitchStyle.SATIN,
            outputFormat = "PES",
            hoopProfile = HoopProfile.H100X100
        )
        val importer: (TextMatrixOptions) -> Result<EmbroideryDesign> = {
            ImportedFontMatrixGenerator.generateText(font, it.text, it)
        }
        val personal = NamePersonalization.add(
            source, options, 0, 0, importer
        ).getOrThrow()
        assertEquals(font.id, personal.editableName?.options?.importedFontId)
        assertEquals(source.points.dropLast(1),
            personal.points.take(source.points.size - 1))
        assertTrue(personal.stitchCount > source.stitchCount)
        val stored = ProjectCodec.decode(ProjectCodec.encode(personal))
        assertEquals(font.id, stored.editableName?.options?.importedFontId)
        assertEquals(source.points, stored.editableName?.original?.points)
        // Encaixe deve manter exatamente a família importada.
        val fit = runBlocking {
            NameAutoPlacement.find(
                original = source,
                requested = options,
                importedGenerator = importer
            )
        }.getOrThrow()
        assertEquals(font.id, fit.options.importedFontId)
        assertEquals(source.points.dropLast(1),
            fit.design.points.take(source.points.size - 1))
    }

    @Test
    fun nameBlockSelectionMayBeCancelledWithoutDeletingArtwork() {
        val original = NamePersonalization.add(
            frame(),
            TextMatrixOptions(
                text = "Maria",
                style = TextStitchStyle.SATIN,
                heightMm = 10f
            ),
            0, 0
        ).getOrThrow().copy(editableName = null)

        composeRule.setContent {
            NameEditorScreen(
                original = original,
                referenceHoop = HoopProfile.H100X100,
                onBack = {},
                onApply = {}
            )
        }
        composeRule.onNodeWithText(
            "Substituir nome existente (selecionar bloco)"
        ).performClick()
        composeRule.onNodeWithText("Selecionar nome antigo").assertExists()
        composeRule.onNodeWithText("Cancelar").performClick()
        composeRule.onNodeWithTag("brother_name_input").assertExists()
        assertEquals(1, original.points.count { it.command == StitchCommand.END })
    }
}
