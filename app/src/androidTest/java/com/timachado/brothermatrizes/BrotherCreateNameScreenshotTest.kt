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
 * Imagem de referência real da rota Criar Nome, no Android OFICIAL.
 * Exclusivamente androidTest da branch Windows fiel. Não modifica
 * código comercial, configurações nem fontes do usuário.
 */
class BrotherCreateNameScreenshotTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun captureOriginalCreatorHomeNavigation() {
        composeRule.waitUntil(30_000) {
            composeRule.onAllNodesWithText("O que vamos\nbordar hoje?")
                .fetchSemanticsNodes().isNotEmpty()
        }
        // Mesmas permissões de primeiros acessos do teste PremiumHome:
        // fechar novidades e rejeitar opt-in de notificações no emulador.
        composeRule.waitUntil(25_000) {
            composeRule.onAllNodesWithText("Entendi")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Entendi").performClick()
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText("Agora não")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Agora não").performClick()
        composeRule.onNodeWithText("Criar nome").assertIsDisplayed().performClick()
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText("Criar Nome")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Criar Nome").assertIsDisplayed()
        composeRule.onNodeWithText("‹ Voltar").assertIsDisplayed()
        composeRule.waitForIdle()

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val image = checkNotNull(
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        ) { "Emulador não capturou a rota Criar Nome original." }
        val output = File(ctx.filesDir, "android-original-create-name-1118.png")
        output.outputStream().use {
            check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        image.recycle()
        check(output.length() > 10_000) { "Captura Android Criar Nome inválida." }
    }
}
