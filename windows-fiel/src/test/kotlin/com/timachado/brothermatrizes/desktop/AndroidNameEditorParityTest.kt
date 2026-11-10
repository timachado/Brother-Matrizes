package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.NameCompositionMode
import com.timachado.brothermatrizes.core.embroidery.NamePersonalization
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.desktop.platform.DesktopImportedFont
import com.timachado.brothermatrizes.desktop.platform.ImportedFontMatrixGenerator
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** QA sem credenciais, sem fontes de clientes e sem máquina de bordar. */
class AndroidNameEditorParityTest {
    private val root = Path.of("").toAbsolutePath().normalize()

    @Test
    fun `Editor preserva corpo Android e mantém importação comercial bloqueada`() {
        val original = Files.readString(root.resolve(
            "../app/src/main/java/com/timachado/brothermatrizes/NameEditorScreen.kt"
        )).replace("\r\n", "\n")
        val desktop = Files.readString(root.resolve(
            "build/generated/android-name-editor/kotlin/com/timachado/brothermatrizes/NameEditorScreen.kt"
        )).replace("\r\n", "\n")
        assertEquals(
            original.substringBefore("    val fontPicker = rememberLauncherForActivityResult(")
                .replace("import androidx.activity.compose.rememberLauncherForActivityResult\n", "")
                .replace("import androidx.activity.result.contract.ActivityResultContracts\n", "")
                .replace("import androidx.compose.ui.platform.LocalContext\n",
                    "import com.timachado.brothermatrizes.desktop.platform.LocalContext\n")
                .replace("import com.timachado.brothermatrizes.font.ImportedFontMatrixGenerator\n",
                    "import com.timachado.brothermatrizes.desktop.platform.ImportedFontMatrixGenerator\n")
                .replace("import com.timachado.brothermatrizes.font.ImportedFontStore\n",
                    "import com.timachado.brothermatrizes.desktop.platform.ImportedFontStore\n")
                .replace("import com.timachado.brothermatrizes.core.account.BrotherMatrizesAccountService\n", ""),
            desktop.substringBefore("    // Fontes externas TTF/OTF")
        )
        assertTrue(desktop.contains("NameAutoPlacement.find("))
        assertTrue(desktop.contains("NamePersonalization.add("))
        assertTrue(desktop.contains("NameEditorStitchLayer("))
        assertTrue(desktop.contains("brother_name_editor_pip"))
        assertTrue(desktop.contains("landscapeHoopAutoScale("))
        assertTrue(desktop.contains("onClick = { preview?.let(onApply) }"))
        assertTrue(desktop.contains("onApply(baseDesign)"))
        assertTrue(desktop.contains("requestFontImport()"))
        assertTrue(!desktop.contains("ActivityResultContracts"))
        assertTrue(!desktop.contains("BrotherMatrizesAccountService"))
        assertTrue(!desktop.contains("BuildConfig.DEBUG"))
        val host = Files.readString(root.resolve(
            "src/main/kotlin/com/timachado/brothermatrizes/desktop/FielMain.kt"
        ))
        assertTrue(host.contains("desktopFloatingPreview = true"))
        assertTrue(host.contains("desktopAutoFitLandscape = true"))
        assertTrue(host.contains("onEditName = { detail = ActiveDetail.NAME_EDITOR }"))
        assertTrue(host.contains("activeDesign = personalized"))
    }

    @Test
    fun `fonte externa não produz falsas pontadas no Windows`() {
        val options = TextMatrixOptions(
            text = "Maria", importedFontId = "fake-font.ttf",
            style = TextStitchStyle.SATIN
        )
        val result = ImportedFontMatrixGenerator.generateText(
            DesktopImportedFont("fake-font.ttf", "Fonte indisponível"),
            options.text, options
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `motor original personaliza matrizes sintéticas sem modificar pontos de origem`() {
        val base = TextLayoutGenerator.generate(TextLayoutOptions(
            textOptions = TextMatrixOptions(
                text = "TM", heightMm = 12f,
                style = TextStitchStyle.RUNNING, outputFormat = "DST",
                hoopProfile = null, enforceHoop = false
            )
        )).getOrThrow()
        val original = base.points.toList()
        val personalized = NamePersonalization.add(
            original = base.copy(hoopProfile = null),
            options = TextMatrixOptions(
                text = "Ana", heightMm = 8f,
                style = TextStitchStyle.SATIN, outputFormat = "DST",
                hoopProfile = null, enforceHoop = false
            ),
            centerXUnits = 0, centerYUnits = 0,
            compositionMode = NameCompositionMode.MONOGRAM_OVERLAY
        ).getOrThrow()
        assertEquals(original, base.points)
        assertTrue(personalized.points.count { it.command == StitchCommand.STITCH } >
            base.points.count { it.command == StitchCommand.STITCH })
        assertEquals(base.points.dropLast(1), personalized.points.take(base.points.size - 1))
        assertEquals("Ana", personalized.editableName?.options?.text)
    }
}
