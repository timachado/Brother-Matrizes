package com.timachado.brothermatrizes.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** Contratos da captura real no runner Windows, sem dados de clientes. */
class NameEditorScreenshotQaTest {
    private val root = Path.of("").toAbsolutePath().normalize()

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
        assertTrue(host.contains("if (startInNameEditorQa) ActiveDetail.NAME_EDITOR"))
        assertTrue(host.contains("if (startInNameEditorQa) syntheticNameEditorQaDesign() else null"))
        assertTrue(host.contains("nameEditorScreenshot -> 11_000L"))
        assertTrue(host.contains("if (nameEditorScreenshot)"))
        assertTrue(host.contains("rgbColors.size >= 18"))
        assertTrue(host.contains("onEditName = { detail = ActiveDetail.NAME_EDITOR }"))
        assertTrue(host.contains("NameEditorScreen("))
        assertTrue(host.contains("importedFontId"))
        assertTrue(host.contains("--capture-name-editor-qa"))
        assertTrue(yaml.contains("--capture-name-editor-qa"))
        assertTrue(yaml.contains("windows-fiel/build/qa/name-editor-windows-original-port.png"))
    }
}
