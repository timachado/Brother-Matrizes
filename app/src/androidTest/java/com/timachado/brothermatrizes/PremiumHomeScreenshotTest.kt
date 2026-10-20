package com.timachado.brothermatrizes

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test

/**
 * QA visual independente do APK comercial. Captura a Home real do Android
 * para revisão em GitHub Actions, preservando a instalação de produção.
 */
class PremiumHomeScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun captureHomeWithApprovedEmbroideryHero() {
        val heroTitle = "O que vamos\nbordar hoje?"
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(heroTitle)
                .fetchSemanticsNodes().isNotEmpty()
        }
        // A janela de novidades aparece apenas na primeira abertura da versao.
        // Fechar como usuario, preservando a preferencia e o fluxo de producao.
        composeRule.waitUntil(timeoutMillis = 25_000) {
            composeRule.onAllNodesWithText("Entendi").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Entendi").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Entendi").fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(heroTitle).assertIsDisplayed()
        composeRule.onNodeWithText("Criar nome").assertIsDisplayed()
        composeRule.onNodeWithText("Início").assertIsDisplayed()
        composeRule.waitForIdle()

        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshot = InstrumentationRegistry.getInstrumentation()
            .uiAutomation.takeScreenshot()
        requireNotNull(screenshot) { "Android nao gerou a captura da Home premium" }
        val output = File(targetContext.filesDir, "premium-home-screenshot.png")
        output.outputStream().use { stream ->
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Falha ao salvar captura da Home premium"
            }
        }
        screenshot.recycle()
        check(output.length() > 10_000L) {
            "Captura visual gerada com tamanho invalido"
        }
    }
}
