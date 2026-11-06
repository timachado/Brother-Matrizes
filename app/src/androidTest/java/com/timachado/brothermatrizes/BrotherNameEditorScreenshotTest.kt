package com.timachado.brothermatrizes

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.NameCompositionMode
import com.timachado.brothermatrizes.core.embroidery.NamePersonalization
import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.ui.theme.BrotherMatrizesTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Imagem Android ORIGINAL da mesma fixture TM + Maria do Desktop.
 * Usada apenas para comparar pixels, sem dados de clientes ou publicação.
 */
@RunWith(AndroidJUnit4::class)
class BrotherNameEditorScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private fun syntheticFixture(): EmbroideryDesign {
        val base = TextLayoutGenerator.generate(TextLayoutOptions(
            textOptions = TextMatrixOptions(
                text = "TM", heightMm = 12f,
                style = TextStitchStyle.RUNNING, outputFormat = "DST",
                hoopProfile = null, enforceHoop = false
            )
        )).getOrThrow()
        return NamePersonalization.add(
            original = base.copy(hoopProfile = null),
            options = TextMatrixOptions(
                text = "Maria", heightMm = 8f,
                style = TextStitchStyle.SATIN, outputFormat = "DST",
                hoopProfile = null, enforceHoop = false
            ),
            centerXUnits = 0, centerYUnits = 0,
            compositionMode = NameCompositionMode.MONOGRAM_OVERLAY
        ).getOrThrow()
    }

    /** Recusa capturas com erro de launcher ou modal sobre o editor. */
    private fun ensureTargetVisible() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrument)
        val pkg = instrument.targetContext.packageName
        val alert = device.hasObject(By.textContains("Pixel Launcher isn't responding")) ||
            device.hasObject(By.textContains("Pixel Launcher keeps stopping"))
        if (alert) {
            val close = device.findObject(By.text("Close app"))
                ?: error("Launcher bloqueou a captura Android.")
            close.click()
            device.waitForIdle()
        }
        composeRule.waitUntil(15_000) { device.currentPackageName == pkg }
        check(device.currentPackageName == pkg) { "Outra aplicação cobre o editor Android." }
        val root = checkNotNull(instrument.uiAutomation.rootInActiveWindow)
        try {
            check(root.packageName?.toString() == pkg) {
                "A janela ativa não é o Editor de Nomes Android."
            }
        } finally {
            root.recycle()
        }
        check(!device.hasObject(By.textContains("isn't responding"))) {
            "Alerta de sistema visível na captura."
        }
    }

    @Test
    fun captureOriginalNameEditorWithSameWindowsFixture() {
        val fixture = syntheticFixture()
        assertTrue(fixture.editableName != null)
        composeRule.setContent {
            BrotherMatrizesTheme(darkTheme = true) {
                NameEditorScreen(
                    original = fixture,
                    referenceHoop = HoopProfile.H100X100,
                    onBack = {}, onApply = {}
                )
            }
        }
        composeRule.onNodeWithText("Editar nome").assertIsDisplayed()
        // Desbloqueio do botão confirma prévia de pontadas pronta.
        composeRule.waitUntil(90_000) {
            runCatching {
                composeRule.onNodeWithTag("brother_name_apply").assertIsEnabled()
            }.isSuccess
        }
        composeRule.waitForIdle()
        ensureTargetVisible()
        val instrument = InstrumentationRegistry.getInstrumentation()
        val bmp = checkNotNull(instrument.uiAutomation.takeScreenshot()) {
            "Não foi possível capturar o Android real."
        }
        val screenshot = File(instrument.targetContext.filesDir,
            "android-original-name-editor-1118.png")
        screenshot.outputStream().use { out ->
            check(bmp.compress(Bitmap.CompressFormat.PNG, 100, out))
        }
        bmp.recycle()
        assertTrue(screenshot.length() > 10_000L)
        ensureTargetVisible()
    }
}
