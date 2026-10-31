package com.timachado.brothermatrizes

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.pinch
import androidx.compose.ui.geometry.Offset
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
    fun matrixRotationIsIndependentFromNameAndUndoRestoresOriginal() {
        val base = frame()
        composeRule.setContent {
            NameEditorScreen(
                original = base,
                referenceHoop = HoopProfile.H100X100,
                onBack = {},
                onApply = {}
            )
        }
        composeRule.onNodeWithTag("brother_name_advanced_toggle").performClick()
        composeRule.onNodeWithTag("brother_matrix_rotate_90")
            .performScrollTo().assertExists().performClick()
        composeRule.onNodeWithText("Rotação da matriz: 90°").assertExists()
        composeRule.onNodeWithTag("brother_name_horizontal_expansion")
            .performScrollTo().assertExists()
        composeRule.onNodeWithText("Expansão horizontal: 100%").assertExists()
        composeRule.onNodeWithText("Desfazer").performClick()
        composeRule.onNodeWithText("Rotação da matriz: 0°").assertExists()
    }

    @Test
    fun viewerMovesMatrixAndNameIndependentlyAndReopensAtSavedPositions() {
        val compact = MatrixEditor.apply(frame(), EditTransform(scale = .5f))
        val named = NamePersonalization.add(
            compact, TextMatrixOptions(text = "Maria", heightMm = 8f),
            0, 0
        ).getOrThrow()
        var displayed by mutableStateOf(named)
        composeRule.setContent {
            ViewerScreen(
                design = displayed,
                displayMode = EmbroideryDisplayMode.SOLID,
                onDisplayModeChange = {},
                referenceHoop = HoopProfile.H100X100,
                onReferenceHoopChange = {},
                showConnections = false,
                onShowConnectionsChange = {},
                onBack = {},
                onOpen = {},
                onSimulate = {},
                onEdit = {},
                onEditName = {},
                onConvert = {},
                onTransfer = {},
                onSaveProject = {},
                onSaveCopy = {},
                onShare = {},
                onPositionChange = { displayed = it }
            )
        }
        val originalName = named.editableName!!.centerYUnits
        composeRule.onNodeWithTag("brother_position_matrix")
            .assertExists().performClick()
        composeRule.waitForIdle()
        val aligned = displayed
        composeRule.onNodeWithTag("brother_position_canvas")
            .performTouchInput {
                swipe(start = center, end = center + Offset(0f, 24f),
                    durationMillis = 500)
            }
        composeRule.waitForIdle()
        val movedMatrix = displayed
        assertTrue(movedMatrix.hoopPositionLocked)
        assertTrue(aligned.points != movedMatrix.points)
        assertEquals(aligned.editableName!!.centerYUnits,
            movedMatrix.editableName!!.centerYUnits)
        assertEquals(aligned.editableName!!.original.points[1].xUnits,
            movedMatrix.editableName!!.original.points[1].xUnits)

        composeRule.onNodeWithTag("brother_position_name")
            .assertExists().performClick()
        composeRule.onNodeWithTag("brother_position_canvas")
            .performTouchInput {
                // O teste do gesto usa a mesma direção para manter a
                // distância de 1,5 mm da moldura. O caso matriz para baixo /
                // nome para cima é coberto no teste geométrico unitário.
                swipe(start = center, end = center + Offset(0f, 24f),
                    durationMillis = 500)
            }
        composeRule.waitForIdle()
        val independentlyMoved = displayed
        assertEquals(movedMatrix.editableName!!.original.points,
            independentlyMoved.editableName!!.original.points)
        assertTrue(independentlyMoved.editableName!!.centerYUnits !=
            movedMatrix.editableName!!.centerYUnits)
        val reopened = ProjectCodec.decode(ProjectCodec.encode(independentlyMoved))
        assertEquals(independentlyMoved.points, reopened.points)
        assertEquals(independentlyMoved.editableName!!.centerYUnits,
            reopened.editableName!!.centerYUnits)
        assertTrue(reopened.hoopPositionLocked)
        assertEquals(originalName, named.editableName!!.centerYUnits)
    }

    @Test
    fun directDragWithoutModeMovesMatrixThenNameIndependentlyAndPersists() {
        val compact = MatrixEditor.apply(frame(), EditTransform(scale = .5f))
        val named = NamePersonalization.add(
            compact, TextMatrixOptions(text = "Maria", heightMm = 8f),
            0, 0
        ).getOrThrow()
        var displayed by mutableStateOf(named)
        composeRule.setContent {
            ViewerScreen(
                design = displayed,
                displayMode = EmbroideryDisplayMode.SOLID,
                onDisplayModeChange = {},
                referenceHoop = HoopProfile.H100X100,
                onReferenceHoopChange = {},
                showConnections = false,
                onShowConnectionsChange = {},
                onBack = {}, onOpen = {}, onSimulate = {},
                onEdit = {}, onEditName = {}, onConvert = {},
                onTransfer = {}, onSaveProject = {},
                onSaveCopy = {}, onShare = {},
                onPositionChange = { displayed = it }
            )
        }
        // Não tocar em Mover matriz/Mover nome: é precisamente a sequência
        // do vídeo de regressão. Antes era pan temporário da câmera.
        composeRule.onNodeWithTag("brother_position_canvas")
            .performTouchInput {
                swipe(
                    // Manter o toque dentro do Canvas em telas estreitas.
                    start = center - Offset(60f, 0f),
                    end = center - Offset(60f, -22f),
                    durationMillis = 500
                )
            }
        composeRule.waitForIdle()
        val movedBase = displayed
        assertTrue("Arraste sem botão precisa gerar coordenadas reais",
            movedBase.hoopPositionLocked)
        assertTrue("Flor deve mudar posição", movedBase.editableName!!.original.points !=
            named.editableName!!.original.points)
        assertEquals("Nome não deve acompanhar o fundo",
            named.editableName!!.centerYUnits,
            movedBase.editableName!!.centerYUnits)
        val baseline = movedBase.editableName!!.original.points
        composeRule.onNodeWithTag("brother_position_canvas")
            .performTouchInput {
                swipe(
                    start = center,
                    end = center - Offset(0f, 14f),
                    durationMillis = 500
                )
            }
        composeRule.waitForIdle()
        val movedName = displayed
        assertEquals("Flor parada ao arrastar só o nome",
            baseline, movedName.editableName!!.original.points)
        assertTrue("Nome deve mudar posição sem selecionar botão",
            movedName.editableName!!.centerYUnits != movedBase.editableName!!.centerYUnits)
        val reopened = ProjectCodec.decode(ProjectCodec.encode(movedName))
        assertTrue(reopened.hoopPositionLocked)
        assertEquals(movedName.points, reopened.points)
        assertEquals(movedName.editableName!!.centerYUnits,
            reopened.editableName!!.centerYUnits)
    }

    @Test
    fun nameEditorMatrixModeMovesOnlyFlowersAndKeepsNamePlace() {
        val original = MatrixEditor.apply(frame(), EditTransform(scale = .5f))
        val personalized = NamePersonalization.add(
            original, TextMatrixOptions(text = "Maria", heightMm = 8f), 0, 0
        ).getOrThrow()
        var applied: EmbroideryDesign? = null
        composeRule.setContent {
            NameEditorScreen(
                original = personalized,
                referenceHoop = HoopProfile.H100X100,
                onBack = {},
                onApply = { applied = it }
            )
        }
        composeRule.onNodeWithTag("brother_name_advanced_toggle").performClick()
        composeRule.onNodeWithTag("brother_name_editor_move_matrix")
            .performScrollTo().performClick()
        composeRule.onNodeWithText("✓ Mover matriz").assertExists()
        composeRule.onNodeWithTag("brother_name_canvas")
            .performTouchInput {
                swipe(start = center, end = center + Offset(0f, 9f),
                    durationMillis = 500)
            }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("brother_name_editor_move_error")
            .assertDoesNotExist()
        composeRule.waitUntil(30000) {
            runCatching {
                composeRule.onNodeWithTag("brother_name_apply")
                    .assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("brother_name_apply")
            .performClick()
        composeRule.waitForIdle()
        val result = requireNotNull(applied)
        val base = requireNotNull(result.editableName).original
        assertTrue(base.points != personalized.editableName!!.original.points)
        assertEquals(personalized.editableName!!.centerYUnits,
            requireNotNull(result.editableName).centerYUnits)
        val persisted = ProjectCodec.decode(ProjectCodec.encode(result))
        assertEquals(result.points, persisted.points)
        assertEquals(base.points, persisted.editableName!!.original.points)
    }

    @Test
    fun simulatorKeepsReprocessingFailureVisibleAndAllowsRetry() {
        val base = frame()
        val saved = NamePersonalization.add(
            base, TextMatrixOptions(text = "Maria", heightMm = 9f),
            0, 0
        ).getOrThrow()
        val imported = saved.copy(
            editableName = requireNotNull(saved.editableName).copy(
                options = requireNotNull(saved.editableName).options.copy(
                    importedFontId = "old-bertilda-id"
                )
            )
        )
        var attempts = 0
        composeRule.setContent {
            SimulatorScreen(
                design = imported,
                referenceHoop = HoopProfile.H100X100,
                onBack = {},
                onRebuildName = { attempts++ },
                rebuildStatus =
                    "REPROCESSAMENTO FALHOU: Fonte importada ausente. " +
                    "A sequência 62C7DCF3 NÃO mudou."
            )
        }
        composeRule.onNodeWithText("REPROCESSAMENTO FALHOU:", substring = true)
            .assertExists()
        composeRule.onNodeWithText("Gerar novas pontadas e simular")
            .assertExists()
            .performClick()
        composeRule.runOnIdle { assertEquals(1, attempts) }
    }

    @Test
    fun simulatorOffersDirectRegenerationForEditableImportedFont() {
        val original = frame()
        val saved = NamePersonalization.add(
            original,
            TextMatrixOptions(text = "Maria", heightMm = 9f),
            0, 0
        ).getOrThrow()
        // The font ID is deliberately synthetic: this test checks that
        // the reprocessing action is exposed, not font import permissions.
        val withImportedFont = saved.copy(
            editableName = requireNotNull(saved.editableName).copy(
                options = requireNotNull(saved.editableName).options.copy(
                    importedFontId = "test-bertilda-id"
                )
            )
        )
        var rebuilt = false
        composeRule.setContent {
            SimulatorScreen(
                design = withImportedFont,
                referenceHoop = HoopProfile.H100X100,
                onBack = {},
                onRebuildName = { rebuilt = true }
            )
        }
        composeRule.onNodeWithText("Gerar novas pontadas e simular")
            .assertExists()
            .performClick()
        composeRule.runOnIdle { assertTrue(rebuilt) }
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
        composeRule.onNodeWithTag("brother_name_apply")
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
        // In overlay, the matrix and name may intentionally intersect while dragging.
        // Exercise real touch movement without an unrelated free-area collision guard.
        composeRule.onNodeWithTag("brother_name_monogram_overlay")
            .performScrollTo().performClick()
        composeRule.onNodeWithTag("brother_name_rotate_right")
            .performScrollTo().assertExists().performClick()
        composeRule.onNodeWithTag("brother_name_rotation_value")
            .assertTextContains("15°", substring = true)
        // The initial auto-fit can legitimately choose negative X.
        // Capture its actual position after rotation rather than assuming 0.
        composeRule.waitUntil(timeoutMillis = 30_000) {
            !composeRule.onNodeWithTag("brother_name_apply")
                .fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
        }
        composeRule.onNodeWithTag("brother_name_apply")
            .performClick()
        val beforeX = composeRule.runOnIdle {
            val initial = requireNotNull(applied).editableName
                ?: error("Initial saved name has no editable layer")
            assertEquals("Giro de 15° deve persistir antes do arraste",
                15f, initial.options.rotationDegrees)
            initial.centerXUnits
        }
        composeRule.runOnIdle { applied = null }

        composeRule.onNodeWithTag("brother_name_drag_handle")
            .assertExists()
            .performTouchInput {
                swipe(
                    start = center,
                    end = center + androidx.compose.ui.geometry.Offset(8f, 0f),
                    durationMillis = 330
                )
            }
        composeRule.waitUntil(timeoutMillis = 30_000) {
            !composeRule.onNodeWithTag("brother_name_apply")
                .fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
        }
        composeRule.onNodeWithTag("brother_name_apply")
            .performClick()
        composeRule.runOnIdle {
            val edited = requireNotNull(applied)
            assertEquals(15f, edited.editableName?.options?.rotationDegrees ?: -1f)
            val afterX = requireNotNull(edited.editableName).centerXUnits
            assertTrue(
                "Arraste horizontal não foi salvo: antes=$beforeX, depois=$afterX",
                afterX > beforeX
            )
            assertEquals(source.points.dropLast(1),
                edited.points.take(source.points.size - 1))
        }
    }

    @Test
    fun oldSavedNameCanRegenerateWithoutDuplicatingOrMovingOriginalArt() {
        val source = frame()
        val saved = NamePersonalization.add(
            source, TextMatrixOptions(
                text = "Maria", heightMm = 9f,
                style = TextStitchStyle.SATIN
            ), 10, 15
        ).getOrThrow()
        var reprocessed: EmbroideryDesign? = null
        composeRule.setContent {
            NameEditorScreen(
                original = saved,
                referenceHoop = HoopProfile.H100X100,
                onBack = {},
                onApply = { reprocessed = it }
            )
        }
        composeRule.onNodeWithTag("brother_name_advanced_toggle").performClick()
        composeRule.onNodeWithTag("brother_name_reprocess_glyphs")
            .performScrollTo().assertExists().performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            !composeRule.onNodeWithTag("brother_name_apply")
                .fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
        }
        composeRule.onNodeWithTag("brother_name_apply")
            .performClick()
        composeRule.runOnIdle {
            val updated = requireNotNull(reprocessed)
            val old = requireNotNull(saved.editableName).options
            val regenerated = requireNotNull(updated.editableName).options
            // The editor intentionally normalizes outputFormat, hoopProfile,
            // and enforceHoop. Only the user's approved typography and geometry
            // must remain exactly unchanged across the rebuild.
            assertEquals(old.text, regenerated.text)
            assertEquals(old.font, regenerated.font)
            assertEquals(old.importedFontId, regenerated.importedFontId)
            assertEquals(old.heightMm, regenerated.heightMm)
            assertEquals(old.spacingMm, regenerated.spacingMm)
            assertEquals(old.satinDensityMm, regenerated.satinDensityMm)
            assertEquals(old.color, regenerated.color)
            assertEquals(old.rotationDegrees, regenerated.rotationDegrees)
            assertEquals(old.widthScale, regenerated.widthScale)
            assertEquals(old.heightScale, regenerated.heightScale)
            assertEquals("PES", regenerated.outputFormat)
            assertEquals(HoopProfile.H100X100, regenerated.hoopProfile)
            assertEquals(saved.editableName?.centerXUnits,
                updated.editableName?.centerXUnits)
            assertEquals(saved.editableName?.centerYUnits,
                updated.editableName?.centerYUnits)
            assertEquals(source.points.dropLast(1),
                updated.points.take(source.points.size - 1))
            assertEquals(1, updated.points.count { it.command == StitchCommand.END })
        }
    }

    @Test
    fun twoFingerPinchResizesRealNameOnBothAxesAndKeepsFrame() {
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
        // A primeira escolha manual deve cancelar o encaixe inicial
        // antes da pinça: evita corrida com cálculo da fonte em paralelo.
        composeRule.onNodeWithTag("brother_name_advanced_toggle").performClick()
        composeRule.onNodeWithTag("brother_name_editor_move_name")
            .performScrollTo().performClick()
        composeRule.onNodeWithTag("brother_name_canvas")
            .performTouchInput {
                pinch(
                    start0 = center + Offset(-20f, -20f),
                    end0 = center + Offset(-23f, -23f),
                    start1 = center + Offset(20f, 20f),
                    end1 = center + Offset(23f, 23f),
                    durationMillis = 480
                )
            }
        composeRule.waitUntil(timeoutMillis = 30_000) {
            !composeRule.onNodeWithTag("brother_name_apply")
                .fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
        }
        composeRule.onNodeWithTag("brother_name_apply")
            .performClick()
        composeRule.runOnIdle {
            val saved = requireNotNull(applied)
            val options = requireNotNull(saved.editableName).options
            assertTrue("Largura não expandiu", options.widthScale > 1.05f)
            assertTrue("Altura não expandiu", options.heightScale > 1.05f)
            assertEquals(source.points.dropLast(1),
                saved.points.take(source.points.size - 1))
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
            .performClick()
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
        composeRule.onNodeWithTag("brother_name_advanced_toggle").performClick()
        composeRule.onNodeWithText(
            "Substituir nome existente (selecionar bloco)"
        ).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 12_000) {
            composeRule.onAllNodesWithText("Selecionar nome antigo")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Selecionar nome antigo").assertExists()
        composeRule.onNodeWithText("Cancelar").performClick()
        composeRule.onNodeWithTag("brother_name_input").assertExists()
        assertEquals(1, original.points.count { it.command == StitchCommand.END })
    }
    @Test
    fun monogramNameOverLetterCanBeEditedSavedAndSimulated() {
        // Inicial sintética: uma haste horizontal atravessando a palavra
        // reproduz o caso de sobreposição intencional sem distribuir o PES real.
        val initial = EmbroideryIntegrity.normalize(
            frame().copy(
                points = listOf(
                    EmbroideryPoint(-380, 0, StitchCommand.JUMP, 0),
                    EmbroideryPoint(380, 0, StitchCommand.STITCH, 0),
                    EmbroideryPoint(380, 0, StitchCommand.END, 0)
                )
            )
        ).getOrThrow().design
        var applied: EmbroideryDesign? = null
        var shown by mutableStateOf<EmbroideryDesign?>(null)
        composeRule.setContent {
            val current = shown
            if (current == null) {
                NameEditorScreen(
                    original = initial,
                    referenceHoop = HoopProfile.H100X100,
                    onBack = {},
                    onApply = { applied = it; shown = it }
                )
            } else {
                SimulatorScreen(
                    design = current,
                    referenceHoop = HoopProfile.H100X100,
                    onBack = {}
                )
            }
        }
        composeRule.onNodeWithTag("brother_name_monogram_overlay")
            .performScrollTo().performClick()
        composeRule.onNodeWithTag("brother_name_overlay_hint").assertExists()
        composeRule.onNodeWithTag("brother_name_input").performScrollTo()
            .performTextClearance()
        composeRule.onNodeWithTag("brother_name_input")
            .performTextInput("Sophia")
        composeRule.waitUntil(timeoutMillis = 45_000) {
            !composeRule.onNodeWithTag("brother_name_apply")
                .fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
        }
        composeRule.onNodeWithTag("brother_name_apply")
            .performClick()
        composeRule.runOnIdle {
            val done = requireNotNull(applied)
            assertEquals(NameCompositionMode.MONOGRAM_OVERLAY,
                done.editableName?.compositionMode)
            assertEquals("Sophia", done.editableName?.options?.text)
            assertEquals(initial.points.dropLast(1),
                done.points.take(initial.points.size - 1))
            assertEquals(1, done.points.count { it.command == StitchCommand.END })
            assertTrue(done.stitchCount > initial.stitchCount)
            val reopened = ProjectCodec.decode(ProjectCodec.encode(done))
            assertEquals(NameCompositionMode.MONOGRAM_OVERLAY,
                reopened.editableName?.compositionMode)
            assertEquals(done.points, reopened.points)
            assertTrue(shown != null) // SimulatorScreen opened after applying
        }
    }


    @Test(timeout = 180_000)
    fun horizontalHoopVerticalInitialHorizontalNamePreset() {
        val base = EmbroideryIntegrity.normalize(frame().copy(
            points = listOf(
                EmbroideryPoint(-380, -160, StitchCommand.JUMP, 0),
                EmbroideryPoint(380, -160, StitchCommand.STITCH, 0),
                EmbroideryPoint(380, 160, StitchCommand.STITCH, 0),
                EmbroideryPoint(-380, 160, StitchCommand.STITCH, 0),
                EmbroideryPoint(-380, -160, StitchCommand.STITCH, 0),
                EmbroideryPoint(-380, -160, StitchCommand.END, 0)
            ),
            hoopProfile = HoopProfile.H130X180
        )).getOrThrow().design
        var exported: EmbroideryDesign? = null
        Log.i("BrotherGestureQA", "PRESET: before setContent")
        composeRule.setContent {
            NameEditorScreen(
                original = base, referenceHoop = HoopProfile.H130X180,
                onBack = {}, onApply = { exported = it }
            )
        }
        Log.i("BrotherGestureQA", "PRESET: content ready, opening hoop")
        composeRule.onNodeWithTag("brother_editor_tab_hoop").performClick()
        Log.i("BrotherGestureQA", "PRESET: hoop tab opened, applying preset")
        // Preset fixo no painel de Bastidor: toque direto, sem depender
        // de scroll ou do autoencaixe concorrente.
        composeRule.onNodeWithTag(
            "brother_layout_horizontal_hoop_vertical_letter_horizontal_name"
        ).assertExists().performClick()
        Log.i("BrotherGestureQA", "PRESET: applied, inspecting orientation")
        composeRule.onNodeWithTag("brother_selected_hoop_orientation")
            .assertExists()
        Log.i("BrotherGestureQA", "PRESET: opening name editor")
        composeRule.onNodeWithTag("brother_editor_tab_name").performClick()
        composeRule.onNodeWithTag("brother_name_input").performScrollTo()
            .performTextClearance()
        composeRule.onNodeWithTag("brother_name_input").performTextInput("Ana")
        Log.i("BrotherGestureQA", "PRESET: input ready, awaiting apply")
        composeRule.waitUntil(timeoutMillis = 45_000) {
            !composeRule.onNodeWithTag("brother_name_apply")
                .fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
        }
        Log.i("BrotherGestureQA", "PRESET: applying name")
        composeRule.onNodeWithTag("brother_name_apply")
            .performClick()
        Log.i("BrotherGestureQA", "PRESET: verifying saved design")
        composeRule.runOnIdle {
            val result = requireNotNull(exported)
            val layer = requireNotNull(result.editableName)
            assertEquals(HoopProfile.H130X180, result.hoopProfile)
            assertEquals(HoopOrientation.LANDSCAPE, result.hoopOrientation)
            assertEquals(NameCompositionMode.MONOGRAM_OVERLAY, layer.compositionMode)
            assertEquals(0f, layer.options.rotationDegrees)
            assertTrue(layer.original.bounds.heightMm > layer.original.bounds.widthMm)
            assertEquals(layer.original.points.dropLast(1),
                result.points.take(layer.original.points.size - 1))
            val reopened = ProjectCodec.decode(ProjectCodec.encode(result))
            assertEquals(HoopOrientation.LANDSCAPE, reopened.hoopOrientation)
            assertEquals(result.points, reopened.points)
        }
    }



    @Test
    fun smallerLetterDoesNotShrinkOrMoveEditableName() {
        val base = frame().copy(
            hoopProfile = HoopProfile.H130X180,
            hoopOrientation = HoopOrientation.LANDSCAPE
        )
        val options = TextMatrixOptions(
            "Maria", heightMm = 8f, hoopProfile = HoopProfile.H130X180,
            style = TextStitchStyle.SATIN
        )
        val named = NamePersonalization.add(
            base, options, 0, 0,
            compositionMode = NameCompositionMode.MONOGRAM_OVERLAY
        ).getOrThrow()
        var applied: EmbroideryDesign? = null
        composeRule.setContent {
            NameEditorScreen(original = named,
                referenceHoop = HoopProfile.H130X180,
                onBack = {}, onApply = { applied = it })
        }
        composeRule.onNodeWithTag("brother_editor_tab_letter").performClick()
        repeat(5) {
            composeRule.onNodeWithTag("brother_letter_smaller")
                .performScrollTo().performClick()
        }
        composeRule.onNodeWithText(
            "Tamanho da letra: 75% (independente do nome)"
        ).assertExists()
        composeRule.waitUntil(timeoutMillis = 45_000) {
            !composeRule.onNodeWithTag("brother_name_apply")
                .fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
        }
        composeRule.onNodeWithTag("brother_name_apply")
            .performClick()
        composeRule.runOnIdle {
            val result = requireNotNull(applied)
            assertTrue(result.editableName!!.original.bounds.widthMm <
                named.editableName!!.original.bounds.widthMm)
            assertEquals(named.editableName!!.options.text,
                result.editableName!!.options.text)
            assertEquals(named.editableName!!.options.heightMm,
                result.editableName!!.options.heightMm)
            assertEquals(named.editableName!!.options.rotationDegrees,
                result.editableName!!.options.rotationDegrees)
            assertEquals(named.editableName!!.options.color,
                result.editableName!!.options.color)
            assertEquals(named.editableName!!.centerXUnits,
                result.editableName!!.centerXUnits)
            assertEquals(named.editableName!!.centerYUnits,
                result.editableName!!.centerYUnits)
            val reopened = ProjectCodec.decode(ProjectCodec.encode(result))
            assertEquals(result.points, reopened.points)
            assertEquals(result.editableName!!.original.points,
                reopened.editableName!!.original.points)
        }
    }


    @Test
    fun easyNudgeMovesOnlyNameWithoutTwoFingerGesture() {
        val source = frame().copy(
            hoopProfile = HoopProfile.H130X180,
            hoopOrientation = HoopOrientation.LANDSCAPE
        )
        val named = NamePersonalization.add(
            source,
            TextMatrixOptions("Maria", heightMm = 8f,
                hoopProfile = HoopProfile.H130X180, style = TextStitchStyle.SATIN),
            0, 0, compositionMode = NameCompositionMode.MONOGRAM_OVERLAY
        ).getOrThrow()
        var applied: EmbroideryDesign? = null
        composeRule.setContent {
            NameEditorScreen(original = named,
                referenceHoop = HoopProfile.H130X180,
                onBack = {}, onApply = { applied = it })
        }
        composeRule.onNodeWithTag("brother_easy_controls_title").assertExists()
        composeRule.onNodeWithTag("brother_easy_move_right").performClick()
        composeRule.waitUntil(timeoutMillis = 45_000) {
            !composeRule.onNodeWithTag("brother_name_apply")
                .fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
        }
        composeRule.onNodeWithTag("brother_name_apply").performClick()
        composeRule.runOnIdle {
            val result = requireNotNull(applied)
            val layer = requireNotNull(result.editableName)
            val originalLayer = requireNotNull(named.editableName)
            assertEquals(originalLayer.centerXUnits + 10, layer.centerXUnits)
            assertEquals(originalLayer.centerYUnits, layer.centerYUnits)
            assertEquals(originalLayer.original.points, layer.original.points)
        }
    }

    @Test
    fun easyLetterSizeUsesDedicatedControlsWithoutTouchingName() {
        val source = frame().copy(
            hoopProfile = HoopProfile.H130X180,
            hoopOrientation = HoopOrientation.LANDSCAPE
        )
        val named = NamePersonalization.add(
            source,
            TextMatrixOptions("Maria", heightMm = 8f,
                hoopProfile = HoopProfile.H130X180, style = TextStitchStyle.SATIN),
            0, 0, compositionMode = NameCompositionMode.MONOGRAM_OVERLAY
        ).getOrThrow()
        var applied: EmbroideryDesign? = null
        composeRule.setContent {
            NameEditorScreen(original = named,
                referenceHoop = HoopProfile.H130X180,
                onBack = {}, onApply = { applied = it })
        }
        composeRule.onNodeWithTag("brother_editor_tab_letter").performClick()
        composeRule.onNodeWithTag("brother_easy_smaller").performClick()
        composeRule.onNodeWithText(
            "Tamanho da letra: 95% (independente do nome)"
        ).assertExists()
        composeRule.waitUntil(timeoutMillis = 45_000) {
            !composeRule.onNodeWithTag("brother_name_apply")
                .fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
        }
        composeRule.onNodeWithTag("brother_name_apply").performClick()
        composeRule.runOnIdle {
            val result = requireNotNull(applied)
            val layer = requireNotNull(result.editableName)
            assertEquals(named.editableName!!.options.text, layer.options.text)
            assertEquals(named.editableName!!.centerXUnits, layer.centerXUnits)
            assertTrue(layer.original.bounds.widthMm <
                named.editableName!!.original.bounds.widthMm)
        }
    }

    @Test
    fun simplifiedEditorStartsWithPreviewAndKeepsAdvancedFunctions() {
        composeRule.setContent {
            NameEditorScreen(
                original = frame(), referenceHoop = HoopProfile.H100X100,
                onBack = {}, onApply = {}
            )
        }
        composeRule.onNodeWithTag("brother_name_canvas").assertExists()
        composeRule.onNodeWithTag("brother_name_input").assertExists()
        composeRule.onNodeWithTag("brother_editor_tab_name").assertExists()
        composeRule.onNodeWithTag("brother_editor_tab_letter").performClick()
        composeRule.onNodeWithTag("brother_letter_smaller").assertExists()
        composeRule.onNodeWithTag("brother_editor_tab_hoop").performClick()
        composeRule.onNodeWithTag("brother_hoop_landscape").assertExists()
        composeRule.onNodeWithTag("brother_editor_tab_name").performClick()
        composeRule.onNodeWithTag("brother_name_input").assertExists()
        composeRule.onNodeWithTag("brother_name_advanced_toggle").performClick()
        composeRule.onNodeWithTag("brother_name_horizontal_expansion")
            .performScrollTo().assertExists()
        composeRule.onNodeWithTag("brother_matrix_rotate_90")
            .performScrollTo().assertExists()
        composeRule.onNodeWithTag("brother_name_apply").assertExists()
    }



    @Test
    fun twoFingersRotateLetterWithoutRotatingNameOrHoop() {
        val points = listOf(
            EmbroideryPoint(-220, -130, StitchCommand.JUMP, 0),
            EmbroideryPoint(220, -130, StitchCommand.STITCH, 0),
            EmbroideryPoint(220, 130, StitchCommand.STITCH, 0),
            EmbroideryPoint(-220, 130, StitchCommand.STITCH, 0),
            EmbroideryPoint(-220, -130, StitchCommand.STITCH, 0),
            EmbroideryPoint(-220, -130, StitchCommand.END, 0)
        )
        val source = EmbroideryIntegrity.normalize(frame().copy(
            points = points, hoopProfile = HoopProfile.H130X180,
            hoopOrientation = HoopOrientation.AUTO
        )).getOrThrow().design
        var applied: EmbroideryDesign? = null
        composeRule.setContent {
            NameEditorScreen(
                original = source, referenceHoop = HoopProfile.H130X180,
                onBack = {}, onApply = { applied = it }
            )
        }
        composeRule.onNodeWithTag("brother_name_monogram_overlay")
            .performScrollTo().performClick()
        composeRule.onNodeWithTag("brother_editor_tab_letter").performClick()
        composeRule.onNodeWithTag("brother_name_canvas").performTouchInput {
            pinch(
                start0 = center + Offset(-26f, 0f),
                end0 = center + Offset(0f, -26f),
                start1 = center + Offset(26f, 0f),
                end1 = center + Offset(0f, 26f),
                durationMillis = 600
            )
        }
        composeRule.waitUntil(timeoutMillis = 45_000) {
            !composeRule.onNodeWithTag("brother_name_apply")
                .fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
        }
        composeRule.onNodeWithTag("brother_name_apply").performClick()
        composeRule.runOnIdle {
            val result = requireNotNull(applied)
            val layer = requireNotNull(result.editableName)
            assertTrue("Letra não ficou vertical após giro com dois dedos",
                layer.original.bounds.heightMm > layer.original.bounds.widthMm)
            assertEquals(0f, layer.options.rotationDegrees)
            assertEquals(HoopOrientation.LANDSCAPE, result.hoopOrientation)
            assertEquals(source.points.map { it.command },
                layer.original.points.map { it.command })
        }
    }

    @Test
    fun explicitNameTabPinchNeverSwitchesToLetterOutsideNameStitches() {
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
        composeRule.onNodeWithTag("brother_name_monogram_overlay")
            .performScrollTo().performClick()
        composeRule.onNodeWithTag("brother_editor_tab_name").performClick()
        composeRule.onNodeWithTag("brother_name_canvas").performTouchInput {
            // A pinça começa fora da pequena região do texto Maria.
            // A aba Nome deve prevalecer e nenhuma pontada da letra pode mudar.
            pinch(
                start0 = center + Offset(-82f, -58f),
                end0 = center + Offset(-112f, -78f),
                start1 = center + Offset(82f, 58f),
                end1 = center + Offset(112f, 78f),
                durationMillis = 500
            )
        }
        composeRule.onNodeWithTag("brother_editor_tab_name").assertExists()
        composeRule.waitUntil(timeoutMillis = 45_000) {
            !composeRule.onNodeWithTag("brother_name_apply")
                .fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
        }
        composeRule.onNodeWithTag("brother_name_apply").performClick()
        composeRule.runOnIdle {
            val result = requireNotNull(applied)
            val layer = requireNotNull(result.editableName)
            assertTrue(layer.options.widthScale > 1.05f)
            assertTrue(layer.options.heightScale > 1.05f)
            assertEquals(source.points.dropLast(1),
                result.points.take(source.points.size - 1))
        }
    }

}
