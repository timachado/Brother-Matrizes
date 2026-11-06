package com.timachado.brothermatrizes.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
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
import com.timachado.brothermatrizes.CreateNameScreen
import com.timachado.brothermatrizes.EmbroideryDisplayMode
import com.timachado.brothermatrizes.SimulatorScreen
import com.timachado.brothermatrizes.ViewerScreen
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.PesExportProfile
import com.timachado.brothermatrizes.R
import com.timachado.brothermatrizes.desktop.resources.desktopPainterResource
import com.timachado.brothermatrizes.ui.theme.BrotherMatrizesTheme
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioNavigationMuted
import com.timachado.brothermatrizes.ui.theme.FioNavigationSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import javax.swing.JFileChooser
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
    val compactScreenshot = args.singleOrNull() == "--capture-home-qa"
    val wideScreenshot = args.singleOrNull() == "--capture-home-wide-qa"
    val screenshotMode = compactScreenshot || wideScreenshot
    require(args.isEmpty() || screenshotMode) {
        "Argumentos inválidos. Use --capture-home-qa ou " +
            "--capture-home-wide-qa."
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Brother Matrizes — validação de fidelidade Android (QA)",
        state = WindowState(
            size = when {
                compactScreenshot -> DpSize(420.dp, 680.dp)
                wideScreenshot -> DpSize(920.dp, 680.dp)
                else -> DpSize(1000.dp, 860.dp)
            }
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
                    // Detectar navegação inferior real, não barras da janela,
                    // taskbar ou espaços pretos. Na Home oficial o fundo da
                    // barra inferior é FioNavigationSurface = #102A40.
                    val navY = screenshot.height - 22
                    val sampleX = (24 until screenshot.width - 24 step 12).toList()
                    val correctNav = sampleX.count { x ->
                        val rgb = screenshot.getRGB(x, navY)
                        val red = rgb shr 16 and 255
                        val green = rgb shr 8 and 255
                        val blue = rgb and 255
                        kotlin.math.abs(red - 16) <= 9 &&
                            kotlin.math.abs(green - 42) <= 10 &&
                            kotlin.math.abs(blue - 64) <= 11
                    }
                    check(correctNav >= sampleX.size * 0.70) {
                        "Captura rejeitada: navegação Android não está " +
                            "visível no rodapé do Windows (amostras=$correctNav/" +
                            "${sampleX.size})."
                    }
                    val filename = if (wideScreenshot)
                        "home-windows-wide-original-port.png"
                    else "home-windows-original-port.png"
                    val out = File("build/qa/$filename")
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

private enum class ActiveDetail { NONE, VIEWER, SIMULATOR }

@Composable
private fun FielHomeHost() {
    var area by remember { mutableStateOf(HomeArea.HOME) }
    var detail by remember { mutableStateOf(ActiveDetail.NONE) }
    var activeDesign by remember { mutableStateOf<EmbroideryDesign?>(null) }
    var displayMode by remember { mutableStateOf(EmbroideryDisplayMode.SOLID) }
    var hoop by remember { mutableStateOf(HoopProfile.H100X100) }
    var showConnections by remember { mutableStateOf(false) }
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

    fun openCreate() {
        area = HomeArea.CREATE
        detail = ActiveDetail.NONE
    }

    fun openDesign(
        design: EmbroideryDesign, mode: EmbroideryDisplayMode,
        destination: ActiveDetail
    ) {
        activeDesign = design
        displayMode = mode
        hoop = design.hoopProfile ?: HoopProfile.H100X100
        area = HomeArea.CREATE
        detail = destination
    }

    fun exportDesign(targetFormat: String, peDesign: Boolean = false) {
        val design = activeDesign ?: return
        scope.launch {
            val generated = withContext(Dispatchers.Default) {
                MatrixConverter.convert(
                    design, targetFormat,
                    pesExportProfile = if (peDesign)
                        PesExportProfile.PE_DESIGN_PC
                    else PesExportProfile.BROTHER_MACHINE
                )
            }
            generated.fold(onSuccess = { converted ->
                // Usuário escolhe o caminho; CREATE_NEW protege o original.
                val selected = JFileChooser().apply {
                    dialogTitle = "Salvar matriz Brother Matrizes"
                    selectedFile = File(converted.fileName)
                }
                if (selected.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    runCatching {
                        val file = selected.selectedFile.toPath().toAbsolutePath().normalize()
                        require(file.fileName.toString().lowercase().endsWith(
                            "." + targetFormat.lowercase()
                        )) { "Extensão do arquivo deve ser .$targetFormat." }
                        Files.write(file, converted.bytes, StandardOpenOption.CREATE_NEW)
                    }.onSuccess {
                        snackbar.showSnackbar("Matriz salva como novo arquivo.")
                    }.onFailure {
                        snackbar.showSnackbar(
                            "Falha ao salvar a matriz: ${it.message}"
                        )
                    }
                }
            }, onFailure = {
                snackbar.showSnackbar(
                    "Conversão ${targetFormat.uppercase()} recusada: ${it.message}"
                )
            })
        }
    }

    Scaffold(
        containerColor = FioBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (detail == ActiveDetail.NONE) {
            // Mesmas cinco abas, sequência, formato e tokens da MainActivity
            // Android 1.11.8; não usar barra lateral da versão rejeitada.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
              NavigationBar(
                modifier = Modifier
                    .widthIn(max = 700.dp)
                    .fillMaxWidth()
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
                        onClick = {
                            if (target == HomeArea.CREATE) openCreate()
                            else { area = target; detail = ActiveDetail.NONE }
                        },
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
            }
        }
    ) { padding ->
        // Uma grade Android de duas colunas ocupando 1000dp produz
        // cartões e hero esticados. O limite é aplicado SOMENTE pelo
        // host Windows, preservando 100% do HomeScreen Android original.
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
          Box(Modifier.widthIn(max = 700.dp).fillMaxWidth().fillMaxHeight()) {
            if (detail == ActiveDetail.VIEWER && activeDesign != null) {
                val design = checkNotNull(activeDesign)
                ViewerScreen(
                    design = design,
                    displayMode = displayMode,
                    onDisplayModeChange = { displayMode = it },
                    referenceHoop = hoop,
                    onReferenceHoopChange = { hoop = it },
                    showConnections = showConnections,
                    onShowConnectionsChange = { showConnections = it },
                    onBack = { detail = ActiveDetail.NONE },
                    onOpen = { informNotPorted("Abrir matriz") },
                    onSimulate = { detail = ActiveDetail.SIMULATOR },
                    onEdit = { informNotPorted("Editor") },
                    onEditName = { informNotPorted("Editar nome") },
                    onConvert = { informNotPorted("Converter") },
                    onTransfer = { informNotPorted("Enviar") },
                    onSaveProject = { informNotPorted("Salvar projeto FLP") },
                    onSaveCopy = { exportDesign(MatrixConverter.preferredBrotherFormat) },
                    onShare = { informNotPorted("Compartilhar") },
                    onSavePeDesign = { exportDesign("PES", peDesign = true) },
                    onSharePeDesign = { informNotPorted("Compartilhar PE-Design") },
                    onPositionChange = { activeDesign = it }
                )
            } else if (detail == ActiveDetail.SIMULATOR && activeDesign != null) {
                val design = checkNotNull(activeDesign)
                SimulatorScreen(
                    design = design,
                    displayMode = displayMode,
                    referenceHoop = hoop,
                    showConnections = showConnections,
                    onBack = { detail = ActiveDetail.VIEWER }
                )
            } else if (area == HomeArea.CREATE) {
                CreateNameScreen(
                    onBack = { area = HomeArea.HOME },
                    onCreate = { design, mode ->
                        openDesign(design, mode, ActiveDetail.VIEWER)
                    },
                    onSimulate = { design, mode ->
                        openDesign(design, mode, ActiveDetail.SIMULATOR)
                    }
                )
            } else if (area == HomeArea.HOME) {
                // Fonte original HomeScreen.kt do Android, não uma recriação.
                HomeScreen(
                    recent = null,
                    onCreateName = { openCreate() },
                    onFonts = { area = HomeArea.FONTS },
                    onTransfer = { informNotPorted("Enviar") },
                    onOpen = { informNotPorted("Abrir matriz") },
                    onRecent = { informNotPorted("Recente") },
                    onSimulate = {
                        if (activeDesign != null) detail = ActiveDetail.SIMULATOR
                        else informNotPorted("Crie ou abra uma matriz primeiro")
                    },
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
}
