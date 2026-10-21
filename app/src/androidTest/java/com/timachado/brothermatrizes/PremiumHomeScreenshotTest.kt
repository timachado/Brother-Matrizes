package com.timachado.brothermatrizes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
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
        // Ferramentas maiores exigem rolagem em algumas telas. Capturar a
        // primeira dobra agora e validar o botão de Recente depois da foto.
        composeRule.waitForIdle()

        // Pixel Launcher do emulador pode exibir um ANR do SISTEMA, mesmo com
        // o Brother Matrizes funcionando normalmente. Fechamos apenas esse
        // processo de launcher e falhamos para qualquer outro ANR.
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        repeat(3) {
            val launcherAnr = device.findObject(
                By.textContains("Pixel Launcher isn't responding")
            )
            if (launcherAnr != null) {
                val closeLauncher = device.findObject(By.text("Close app"))
                check(closeLauncher != null) {
                    "Pixel Launcher ANR sem opcao segura para fechar"
                }
                closeLauncher.click()
                device.wait(
                    Until.gone(By.textContains("Pixel Launcher isn't responding")),
                    6_000L
                )
            }
        }
        check(!device.hasObject(By.textContains("isn't responding"))) {
            "Janela ANR detectada sobre a Home; captura nao sera aceita"
        }

        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshot = InstrumentationRegistry.getInstrumentation()
            .uiAutomation.takeScreenshot()
        requireNotNull(screenshot) { "Android nao gerou a captura da Home premium" }
        // A Home sempre é azul-noite: um centro muito claro indica modal do
        // sistema/consentimento sobre a tela, mesmo se aparecer após o check.
        val center = screenshot.getPixel(screenshot.width / 2, screenshot.height / 2)
        val brightness = (
            android.graphics.Color.red(center) +
            android.graphics.Color.green(center) +
            android.graphics.Color.blue(center)
        ) / 3
        check(brightness < 175) {
            "Captura rejeitada: possivel dialogo claro sobre a Home (centro=$brightness)"
        }
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

        // QA de acessibilidade funcional: nenhum CTA deve ficar inacessível
        // somente porque há cartões maiores acima dele.
        composeRule.onNodeWithText("Criar primeiro nome")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Recente").assertIsDisplayed()
        val navTop = composeRule.onNodeWithText("Início")
            .fetchSemanticsNode().boundsInRoot.top
        var recentBottom = composeRule.onNodeWithText("Criar primeiro nome")
            .fetchSemanticsNode().boundsInRoot.bottom
        // Deslizar na propria area rolavel, nao na raiz do Scaffold.
        // A raiz tambem contem a NavigationBar e pode ignorar o gesto.
        repeat(4) { attempt ->
            if (recentBottom <= navTop - 30f) return@repeat
            composeRule.onNodeWithTag("brother_home_scroll")
                .performTouchInput { swipeUp() }
            composeRule.waitForIdle()
            recentBottom = composeRule.onNodeWithText("Criar primeiro nome")
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot.bottom
            println("QA Recente: tentativa=$attempt, CTA.bottom=$recentBottom, nav.top=$navTop")
        }
        check(recentBottom <= navTop - 30f) {
            "CTA de Recente permanece encoberto mesmo apos rolar grid: bottom=$recentBottom nav=$navTop"
        }


        // Prova de que o simbolo dourado aprovado foi empacotado, nao apenas
        // desenhado no mockup. Captura o icone real solicitado ao PackageManager.
        val bm = BitmapFactory.decodeResource(
            targetContext.resources, R.drawable.brother_bm_premium_approved
        )
        requireNotNull(bm) { "Drawable BM premium nao decodificado no Android" }
        check(bm.width >= 180 && bm.height >= 180) {
            "Recurso de marca do Android tem resolucao insuficiente"
        }
        val launcher = targetContext.packageManager.getApplicationIcon(targetContext.packageName)
        val launcherBitmap = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        launcher.setBounds(0, 0, 432, 432)
        launcher.draw(Canvas(launcherBitmap))
        val launcherOutput = File(targetContext.filesDir, "premium-launcher-icon.png")
        launcherOutput.outputStream().use { out ->
            check(launcherBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                "Falha ao salvar icone de launcher adaptativo"
            }
        }
        check(launcherOutput.length() > 5_000) {
            "Captura do icone Android invalida"
        }
        // Verificar a zona segura do monograma no launcher REAL.
        // No primeiro PNG o dourado encostava nas bordas e letras foram cortadas.
        var minGoldX = 432
        var maxGoldX = -1
        var minGoldY = 432
        var maxGoldY = -1
        for (y in 0 until 432 step 2) {
            for (x in 0 until 432 step 2) {
                val pixel = launcherBitmap.getPixel(x, y)
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                if (android.graphics.Color.alpha(pixel) > 200 &&
                    red > 110 && green > 65 &&
                    red > green * 1.14 && green > blue * 1.23
                ) {
                    minGoldX = minOf(minGoldX, x)
                    maxGoldX = maxOf(maxGoldX, x)
                    minGoldY = minOf(minGoldY, y)
                    maxGoldY = maxOf(maxGoldY, y)
                }
            }
        }
        check(maxGoldX >= 0) { "O monograma BM nao foi identificado no launcher" }
        check(minGoldX >= 30 && maxGoldX <= 402 &&
              minGoldY >= 30 && maxGoldY <= 402) {
            "Monograma fora da zona segura: x=$minGoldX..$maxGoldX, " +
                "y=$minGoldY..$maxGoldY"
        }
    }
}
