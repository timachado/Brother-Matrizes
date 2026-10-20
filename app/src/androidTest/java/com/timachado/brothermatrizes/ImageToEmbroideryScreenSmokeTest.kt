package com.timachado.brothermatrizes

import androidx.compose.ui.test.onNodeWithText
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.timachado.brothermatrizes.core.embroidery.RasterEmbroideryApproval
import com.timachado.brothermatrizes.ui.theme.BrotherMatrizesTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Runs on a virtual Android device in GitHub Actions, never on a user's
 * phone. Guards the most costly QA regressions: screen startup, source
 * selection UX, distinguishable QA version and NO accidental PES export.
 */
@RunWith(AndroidJUnit4::class)
class ImageToEmbroideryScreenSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun emptyImageScreenStartsWithRealQaWarningAndNoPesExport() {
        // The real screen automatically opens Android's file chooser on
        // its first launch. Treat this scenario as a user who has closed
        // the chooser without selecting a file, so the QA UI is foreground.
        val session=SimpleImageSession().apply { pickerLaunched=true }
        compose.setContent {
            BrotherMatrizesTheme {
                SimpleImageToEmbroideryScreen(
                    onBack={},
                    onSimulate={ _,_,_,_ ->
                        error("Empty image must never simulate stitches.")
                    },
                    session=session
                )
            }
        }
        compose.onNodeWithText("Imagem para Bordado").assertExists()
        compose.onNodeWithText(
            "APK EXPERIMENTAL: QA V6 — Segurança do Microdetalhe"
        ).assertExists()
        compose.onNodeWithText(
            "Escolha a imagem e confira no bastidor"
        ).assertExists()
        compose.onNodeWithText("Exportar PES").assertDoesNotExist()
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }
}
