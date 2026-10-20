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
        // Segunda janela de primeiro uso: consentimento independente do Firebase.
        // Dispensar apenas no emulador, sem alterar o fluxo real de opt-in.
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText("Agora não").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Agora não").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Agora não").fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(heroTitle).assertIsDisplayed()
        composeRule.onNodeWithText("Criar nome").assertIsDisplayed()
        composeRule.onNodeWithText("Início").assertIsDisplayed()
        // Validacao de primeira dobra no perfil Pixel 6 (1080x2400).
        // O botao Recente deve estar inteiramente acima da barra inferior.
        composeRule.onNodeWithText("Recente").assertIsDisplayed()
        val recentButton = composeRule.onNodeWithText("Criar primeiro nome")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val navLabel = composeRule.onNodeWithText("Início")
            .fetchSemanticsNode().boundsInRoot
        check(recentButton.bottom <= navLabel.top - 90f) {
            "O CTA de Recente esta encoberto pela barra inferior: " +
                "CTA.bottom=${recentButton.bottom}, nav.top=${navLabel.top}"
        }
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
