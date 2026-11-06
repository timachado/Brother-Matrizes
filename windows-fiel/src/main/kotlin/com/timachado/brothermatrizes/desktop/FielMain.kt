package com.timachado.brothermatrizes.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.timachado.brothermatrizes.HomeScreen
import com.timachado.brothermatrizes.R
import com.timachado.brothermatrizes.desktop.resources.desktopPainterResource
import com.timachado.brothermatrizes.ui.theme.BrotherMatrizesTheme
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioNavigationMuted
import com.timachado.brothermatrizes.ui.theme.FioNavigationSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.io.File
import javax.imageio.ImageIO
import kotlin.system.exitProcess

/**
 * Port inicial e isolado da Home, NÃO um produto funcional.
 *
 * O conteúdo da Home é copiado automaticamente do Android comercial
 * sem modificar a hierarquia Compose. Somente o host e os adaptadores
 * de recursos são específicos do Windows.
 */
fun main(args: Array<String>) = application {
    // Modo de QA fechado: apenas uma captura da janela do próprio aplicativo.
    // Não aceita caminhos arbitrários de saída nem abre dados de clientes.
    val screenshotMode = args.singleOrNull() == "--capture-home-qa"
    require(args.isEmpty() || screenshotMode) {
        "Argumentos inválidos. Somente --capture-home-qa é permitido."
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Brother Matrizes — validação de fidelidade Android (QA)",
        state = WindowState(
            size = if (screenshotMode) DpSize(420.dp, 680.dp)
                else DpSize(1000.dp, 860.dp)
        )
    ) {
        val composeWindow = window
        LaunchedEffect(screenshotMode) {
            if (screenshotMode) {
                // Renderização estabilizada em Windows real do runner;
                // não é captura simulada da Home Android.
                // O runner Windows tem taskbar e resolução menor que o Android.
                // A janela anterior de 890dp invadia a barra do sistema.
                composeWindow.setLocation(20, 20)
                delay(2_500L)
                try {
                    val container = composeWindow.contentPane
                    val origin = container.locationOnScreen
                    val bounds = Rectangle(
                        origin.x, origin.y, container.width, container.height
                    )
                    val screen = Toolkit.getDefaultToolkit().screenSize
                    check(
                        bounds.width >= 320 && bounds.height >= 540 &&
                            bounds.x >= 0 && bounds.y >= 0 &&
                            bounds.x + bounds.width <= screen.width &&
                            bounds.y + bounds.height <= screen.height - 42
                    ) {
                        "Janela QA fora da área segura do monitor: " +
                            "$bounds, tela=${screen.width}x${screen.height}; " +
                            "a taskbar não pode aparecer no PNG."
                    }
                    val screenshot = Robot().createScreenCapture(bounds)
                    val out = File("build/qa/home-windows-original-port.png")
                    out.parentFile.mkdirs()
                    check(ImageIO.write(screenshot, "png", out) &&
                        out.length() > 10_000L) {
                        "Captura do canvas Windows não foi gerada."
                    }
                    println(
                        "BROTHER_WINDOWS_FIEL_HOME_SCREENSHOT_OK " +
                            "${bounds.width}x${bounds.height}"
                    )
                    exitProcess(0)
                } catch (error: Exception) {
                    System.err.println("BROTHER_WINDOWS_FIEL_HOME_SCREENSHOT_FAIL: ${error.message}")
                    exitProcess(2)
                }
            }
        }
        BrotherMatrizesTheme(darkTheme = true) {
            FielHomeHost()
        }
    }
}

private enum class HomeArea(
    val label: String,
    val drawable: Int
) {
    HOME("Início", R.drawable.ic_ms_home_rounded),
    CREATE("Criar", R.drawable.ic_ms_text_fields_rounded),
    LIBRARY("Biblioteca", R.drawable.ic_ms_folder_open_rounded),
    FONTS("Fontes", R.drawable.ic_ms_font_download_rounded),
    ACCOUNT("Conta", R.drawable.ic_ms_account_circle_rounded)
}

@Composable
private fun FielHomeHost() {
    var area by remember { mutableStateOf(HomeArea.HOME) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun informNotPorted(feature: String) {
        scope.launch {
            snackbar.showSnackbar(
                "$feature: ainda não portado. Esta build serve apenas para " +
                    "comparar a Home com o Android oficial."
            )
        }
    }

    Scaffold(
        containerColor = FioBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            // Mesmas cinco abas, sequência, formato e tokens da MainActivity
            // Android 1.11.8; não usar barra lateral da versão rejeitada.
            NavigationBar(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .testTag("brother_premium_bottom_bar"),
                containerColor = FioNavigationSurface,
                tonalElevation = 0.dp
            ) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = FioBackground,
                    selectedTextColor = FioGold,
                    indicatorColor = FioGold,
                    unselectedIconColor = FioNavigationMuted,
                    unselectedTextColor = FioNavigationMuted
                )
                HomeArea.entries.forEach { target ->
                    NavigationBarItem(
                        selected = area == target,
                        onClick = { area = target },
                        icon = {
                            Icon(
                                painter = desktopPainterResource(target.drawable),
                                contentDescription = target.label
                            )
                        },
                        label = { Text(target.label) },
                        colors = colors
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (area == HomeArea.HOME) {
                // Fonte original HomeScreen.kt do Android, não uma recriação.
                HomeScreen(
                    recent = null,
                    onCreateName = { informNotPorted("Criar nome") },
                    onFonts = { area = HomeArea.FONTS },
                    onTransfer = { informNotPorted("Enviar") },
                    onOpen = { informNotPorted("Abrir matriz") },
                    onRecent = { informNotPorted("Recente") },
                    onSimulate = { informNotPorted("Simulador") },
                    onEdit = { informNotPorted("Editor") },
                    onConvert = { informNotPorted("Converter") },
                    onClearRecent = { informNotPorted("Recentes") },
                    onUnavailable = { text -> informNotPorted(text) },
                    onCommunity = { informNotPorted("Comunidade e novidades") }
                )
            } else {
                // Transparente: as demais rotas serão portadas da fonte oficial,
                // nunca disfarçadas como funcionalidades prontas.
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "${area.label} — porting funcional pendente",
                        color = FioText,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "Esta etapa não é um Brother Matrizes Windows distribuível.",
                        color = FioText
                    )
                    Button(onClick = { area = HomeArea.HOME }) {
                        Text("Voltar ao Início")
                    }
                }
            }
        }
    }
}
