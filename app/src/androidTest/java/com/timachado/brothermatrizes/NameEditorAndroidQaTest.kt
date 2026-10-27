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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timachado.brothermatrizes.core.embroidery.*
import com.timachado.brothermatrizes.core.project.ProjectCodec
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
            val nodes = composeRule.onNodeWithTag("brother_name_apply")
                .fetchSemanticsNodes()
            nodes.isNotEmpty() && nodes[0].config
                .getOrNull(SemanticsProperties.Disabled) == null
        }
        composeRule.onNodeWithTag("brother_name_apply").performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Simulação").assertExists()
        composeRule.runOnIdle {
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
            val nodes = composeRule.onNodeWithTag("brother_name_apply")
                .fetchSemanticsNodes()
            nodes.isNotEmpty() && nodes[0].config
                .getOrNull(SemanticsProperties.Disabled) == null
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
