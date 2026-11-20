package com.timachado.brothermatrizes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test Android do editor SVG. Não usa arquivos pessoais, não grava
 * documentos fora do sandbox e não altera a instalação comercial.
 */
@RunWith(AndroidJUnit4::class)
class SvgDrawingAndroidQaTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun svgImportControlsAreAccessible() {
        composeRule.setContent {
            CreateDrawingScreen(
                onBack = {},
                onCreate = {},
                onSimulate = {}
            )
        }

        composeRule.onNodeWithText("Criar desenho/logo").assertIsDisplayed()
        composeRule.onNodeWithText("Importar arquivo SVG")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun tatamiIsOptInAndExposesDensityControls() {
        composeRule.setContent {
            CreateDrawingScreen(
                onBack = {},
                onCreate = {},
                onSimulate = {}
            )
        }
        composeRule.onNodeWithTag("brother_svg_tatami_mode")
            .performScrollTo().performClick()
        composeRule.onNodeWithText("Espaçamento das linhas Tatami: 0,5 mm")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("0,6 mm")
            .performScrollTo().performClick()
        composeRule.onNodeWithText("Espaçamento das linhas Tatami: 0,6 mm")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("brother_svg_tatami_mode")
            .performScrollTo().performClick()
        composeRule.onNodeWithText("Ponto corrido segue o contorno/caminho importado.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun contourFinishIsOptInAndCanBeDisabledWithoutLosingTatami() {
        composeRule.setContent {
            CreateDrawingScreen(
                onBack = {},
                onCreate = {},
                onSimulate = {}
            )
        }
        composeRule.onNodeWithTag("brother_svg_tatami_mode")
            .performScrollTo().performClick()
        composeRule.onNodeWithText("Desativado: somente preenchimento Tatami, como na versão aprovada.")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("brother_svg_tatami_detail_finish")
            .performScrollTo().performClick()
        composeRule.onNodeWithText(
            "● Acabamento: contornos e detalhes"
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("brother_svg_tatami_detail_finish")
            .performScrollTo().performClick()
        composeRule.onNodeWithText("Desativado: somente preenchimento Tatami, como na versão aprovada.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun freehandStrokeEnablesSavingAsSvg() {
        composeRule.setContent {
            CreateDrawingScreen(
                onBack = {},
                onCreate = {},
                onSimulate = {}
            )
        }

        composeRule.onNodeWithText("Desenhar")
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithTag("brother_svg_drawing_pad")
            .performTouchInput {
                swipe(
                    start = center + Offset(-50f, -40f),
                    end = center + Offset(50f, 40f),
                    durationMillis = 500
                )
            }

        composeRule.waitForIdle()

        composeRule.onNodeWithText("Salvar desenho como SVG")
            .performScrollTo()
            .assertIsEnabled()
    }
}
