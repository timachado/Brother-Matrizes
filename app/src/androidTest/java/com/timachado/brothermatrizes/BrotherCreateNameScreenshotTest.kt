package com.timachado.brothermatrizes

import android.graphics.Bitmap
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
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

    /**
     * Com Android Emulator iniciado por runner CI, o Pixel Launcher pode
     * mostrar um alerta de ANR acima da atividade Brother Matrizes. O teste
     * Compose continua verde mesmo com esse modal cobrindo o screenshot!
     * Bloqueamos captura cujo primeiro plano não é o app.
     */
    private fun ensureVisibleTarget() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrument)
        val expected = instrument.targetContext.packageName

        // Tratar apenas este problema reconhecido do emulador. Qualquer
        // outra janela estranha reprova o teste em vez de ser ignorada.
        val launcherAlert = device.hasObject(By.textContains("Pixel Launcher isn't responding")) ||
            device.hasObject(By.textContains("Pixel Launcher keeps stopping"))
        if (launcherAlert) {
            val button = device.findObject(By.text("Close app"))
                ?: error("ANR do launcher sem botão seguro para dispensar")
            button.click()
            device.waitForIdle()
        }
        composeRule.waitUntil(12_000) {
            device.currentPackageName == expected &&
                instrument.uiAutomation.rootInActiveWindow?.let { node ->
                    try {
                        node.packageName?.toString() == expected
                    } finally {
                        node.recycle()
                    }
                } == true
        }
        check(device.currentPackageName == expected) {
            "Captura recusada: janela ativa é ${device.currentPackageName}, esperada $expected."
        }
        val root = checkNotNull(instrument.uiAutomation.rootInActiveWindow) {
            "Captura recusada: nenhuma janela Android está ativa."
        }
        try {
            check(root.packageName?.toString() == expected) {
                "Captura recusada: uma janela do sistema encobre o Criar Nome."
            }
        } finally {
            root.recycle()
        }
        check(!device.hasObject(By.textContains("isn't responding"))) {
            "Captura recusada: alerta de ANR visível."
        }
    }

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
        ensureVisibleTarget()
        // Teste funcional de rota: é o formulário Criar Nome com a matriz
        // "Maria", não a Home por trás de um diálogo do Pixel Launcher.
        composeRule.onNodeWithText("Criar Nome").assertIsDisplayed()
        composeRule.onNodeWithText("‹ Voltar").assertIsDisplayed()
        ensureVisibleTarget()

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
        // A janela pode mudar durante takeScreenshot(): não aceitar imagem
        // se outro processo assumiu o primeiro plano nesse intervalo.
        ensureVisibleTarget()
    }
}
