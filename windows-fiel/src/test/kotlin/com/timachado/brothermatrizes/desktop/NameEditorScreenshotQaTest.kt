package com.timachado.brothermatrizes.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** Contratos da captura real no runner Windows, sem dados de clientes. */
class NameEditorScreenshotQaTest {
    private val root = Path.of("").toAbsolutePath().normalize()


    @Test
    fun `as duas capturas usam a mesma matriz sintética TM Maria e composição`() {
        val desktop = Files.readString(root.resolve(
            "src/main/kotlin/com/timachado/brothermatrizes/desktop/FielMain.kt"
        ))
        val android = Files.readString(root.resolve(
            "../app/src/androidTest/java/com/timachado/brothermatrizes/BrotherNameEditorScreenshotTest.kt"
        ))
        val desktopFixture = desktop.substringAfter("private fun syntheticNameEditorQaDesign()")
            .substringBefore("@Composable")
        val androidFixture = android.substringAfter("private fun syntheticFixture()")
            .substringBefore("private fun ensureTargetVisible()")
        val commonTokens = listOf(
            "text = \"TM\"",
            "text = \"Maria\"",
            "heightMm = 12f",
            "heightMm = 8f",
            "style = TextStitchStyle.RUNNING",
            "style = TextStitchStyle.SATIN",
            "outputFormat = \"DST\"",
            "hoopProfile = null",
            "enforceHoop = false",
            "centerXUnits = 0",
            "centerYUnits = 0",
            "compositionMode = NameCompositionMode.MONOGRAM_OVERLAY"
        )
        commonTokens.forEach { token ->
            assertTrue(desktopFixture.contains(token), "Desktop não usa parâmetro: $token")
            assertTrue(androidFixture.contains(token), "Android não usa parâmetro: $token")
        }
        assertTrue(desktop.contains("HoopProfile.H100X100"))
        assertTrue(android.contains("referenceHoop = HoopProfile.H100X100"))
    }

    @Test
    fun `a captura QA abre editor real com matriz sintética e isolamento`() {
        val host = Files.readString(root.resolve(
            "src/main/kotlin/com/timachado/brothermatrizes/desktop/FielMain.kt"
        ))
        val yaml = Files.readString(root.resolve(
            "../.github/workflows/brother-windows-fiel-home-qa.yml"
        ))
        assertTrue(host.contains("fun syntheticNameEditorQaDesign()"))
        assertTrue(host.contains("NamePersonalization.add("))
        assertTrue(host.contains("startInNameEditorQa"))
        assertTrue(host.contains("startInNameEditorQa -> ActiveDetail.NAME_EDITOR"))
        assertTrue(host.contains("if (startInNameEditorQa || startInSaveDialogQaFile != null || startInTransferQa)"))
        assertTrue(host.contains("editorCapture -> 11_000L"))
        // A captura de telas de detalhe inclui também a Comunidade;
        // manter a condição concreta e o isolamento visual, sem depender
        // da mesma formatação de linha do código-fonte.
        assertTrue(host.replace(Regex("""\s+"""), " ").contains(
            "if (editorCapture || transferScreenshot || drawingScreenshot || " +
                "archiveScreenshot || communityScreenshot)"
        ))
        assertTrue(host.contains("rgbColors.size >= 18"))
        assertTrue(host.contains("onEditName = { detail = ActiveDetail.NAME_EDITOR }"))
        assertTrue(host.contains("NameEditorScreen("))
        assertTrue(host.contains("NameCompositionMode.MONOGRAM_OVERLAY"))
        assertTrue(host.contains("--capture-name-editor-qa"))
        assertTrue(yaml.contains("--capture-name-editor-qa"))
        assertTrue(yaml.contains("windows-fiel/build/qa/name-editor-windows-original-port.png"))
        assertTrue(host.contains("--capture-name-editor-scroll-qa"))
        assertTrue(host.contains("robot.mouseWheel(10)"))
        assertTrue(host.contains("changed >= 180"))
        assertTrue(yaml.contains("--capture-name-editor-scroll-qa"))
        assertTrue(yaml.contains("windows-fiel/build/qa/name-editor-windows-scrolled-qa.png"))
        assertTrue(host.contains("--qa-name-editor-click-apply"))
        assertTrue(host.contains("robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)"))
        assertTrue(host.contains("DesktopFlpProjectFiles.saveNew(personalized, qaFile)"))
        assertTrue(host.contains("reopened.editableName?.options?.text == expectedName"))
        assertTrue(yaml.contains("name-editor-applied-qa.png"))
        assertTrue(yaml.contains("--qa-name-editor-click-apply"))
        assertTrue(host.contains("--qa-name-editor-keyboard-apply"))
        assertTrue(host.contains("KeyEvent.VK_BACK_SPACE"))
        assertTrue(host.contains("repeat(24)"))
        assertTrue(host.contains("KeyEvent.VK_END"))
        assertTrue(host.contains("KeyEvent.VK_N"))
        assertTrue(host.contains("if (startInNameEditorKeyboardQa)"))
        assertTrue(host.contains("loaded.editableName?.options?.text == expectedName"))
        assertTrue(yaml.contains("--qa-name-editor-keyboard-apply"))
        assertTrue(yaml.contains("name-editor-keyboard-input-qa.png"))
        assertTrue(host.contains("--qa-name-editor-ctrl-a-apply"))
        assertTrue(host.contains("robot.keyPress(KeyEvent.VK_CONTROL)"))
        assertTrue(host.contains("robot.keyPress(KeyEvent.VK_A)"))
        assertTrue(host.contains("nameEditorCtrlAQa ->"))
        assertTrue(yaml.contains("--qa-name-editor-ctrl-a-apply"))
        assertTrue(yaml.contains("name-editor-ctrl-a-applied-qa.png"))
        assertTrue(host.contains("--qa-open-chooser-pes"))
        assertTrue(host.contains("--qa-open-chooser-jef"))
        assertTrue(host.contains("chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION"))
        assertTrue(host.contains("robot.keyPress(KeyEvent.VK_ENTER)"))
        assertTrue(host.contains("currentDirectory = startInChooserQaFile.parent.toFile()"))
        assertTrue(host.contains("DesktopEmbroideryFiles.open(selected)"))
        assertTrue(host.contains("BROTHER_WINDOWS_CHOOSER_"))
        assertTrue(yaml.contains("chooser-pes-viewer.png"))
        assertTrue(yaml.contains("chooser-jef-viewer.png"))
        assertTrue(host.contains("--qa-open-chooser-dst"))
        assertTrue(host.contains("--qa-open-chooser-flp"))
        assertTrue(host.contains("MatrixConverter.convert(synthetic, \"DST\")"))
        assertTrue(host.contains("DesktopFlpProjectFiles.saveNew(synthetic, target)"))
        assertTrue(host.contains("chooserQaInitialBytes"))
        assertTrue(host.contains("selectedExtension == \"flp\""))
        assertTrue(yaml.contains("--qa-open-chooser-dst"))
        assertTrue(yaml.contains("--qa-open-chooser-flp"))
        assertTrue(yaml.contains("chooser-dst-viewer.png"))
        assertTrue(yaml.contains("chooser-flp-viewer.png"))
        assertTrue(host.contains("--qa-save-flp-dialog-new"))
        assertTrue(host.contains("--qa-save-flp-dialog-protect"))
        assertTrue(host.contains("chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION"))
        assertTrue(host.contains("FileNameExtensionFilter("))
        assertTrue(host.contains("StandardOpenOption.CREATE_NEW"))
        assertTrue(host.contains("FileAlreadyExistsException"))
        assertTrue(host.contains("BROTHER_WINDOWS_SAVE_DIALOG_PROTECT_FLPAUTH_OK"))
        assertTrue(host.contains("BROTHER_WINDOWS_SAVE_DIALOG_NEW_FLPRT_OK"))
        assertTrue(yaml.contains("--qa-save-flp-dialog-new"))
        assertTrue(yaml.contains("--qa-save-flp-dialog-protect"))
        assertTrue(yaml.contains("save-flp-created-viewer.png"))
        assertTrue(yaml.contains("save-flp-protected-viewer.png"))
    }
}
