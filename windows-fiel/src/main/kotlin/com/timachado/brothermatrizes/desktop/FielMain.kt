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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.timachado.brothermatrizes.HomeScreen
import com.timachado.brothermatrizes.CreateNameScreen
import com.timachado.brothermatrizes.ConverterScreen
import com.timachado.brothermatrizes.MonogramScreen
import com.timachado.brothermatrizes.ProjectLibraryScreen
import com.timachado.brothermatrizes.EmbroideryDisplayMode
import com.timachado.brothermatrizes.SimulatorScreen
import com.timachado.brothermatrizes.ViewerScreen
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.PesExportProfile
import com.timachado.brothermatrizes.R
import com.timachado.brothermatrizes.desktop.resources.desktopPainterResource
import com.timachado.brothermatrizes.desktop.platform.DesktopFlpProjectFiles
import com.timachado.brothermatrizes.desktop.platform.DesktopProjectCatalog
import com.timachado.brothermatrizes.desktop.platform.DesktopLibraryActivityStore
import com.timachado.brothermatrizes.desktop.platform.DesktopEmbroideryFiles
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
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
    val createNameScreenshot = args.singleOrNull() == "--capture-create-name-qa"
    val screenshotMode = compactScreenshot || wideScreenshot || createNameScreenshot
    require(args.isEmpty() || screenshotMode) {
        "Argumentos inválidos. Use apenas um modo de captura QA autorizado."
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Brother Matrizes — validação de fidelidade Android (QA)",
        state = WindowState(
            size = when {
                compactScreenshot || createNameScreenshot -> DpSize(420.dp, 680.dp)
                wideScreenshot -> DpSize(920.dp, 680.dp)
                else -> DpSize(1000.dp, 860.dp)
            }
        )
    ) {
        // Menu desktop não modifica a hierarquia original Compose Android.
        // Desabilitado durante capturas golden para manter a mesma viewport.
        var monogramLaunchRequest by remember { mutableIntStateOf(0) }
        var libraryFolderRequest by remember { mutableIntStateOf(0) }
        if (!screenshotMode) {
            MenuBar {
                Menu("Criar") {
                    Item("Monograma", onClick = { monogramLaunchRequest++ })
                }
                Menu("Biblioteca") {
                    Item("Selecionar pasta FLP…", onClick = { libraryFolderRequest++ })
                }
            }
        }
        val composeWindow = window
        LaunchedEffect(screenshotMode) {
            if (screenshotMode) {
                // Renderização estabilizada em Windows real do runner;
                // não é captura simulada da Home Android.
                // O runner Windows tem taskbar e resolução menor que o Android.
                // A janela anterior de 890dp invadia a barra do sistema.
                composeWindow.setLocation(20, 20)
                delay(if (createNameScreenshot) 5_000L else 2_500L)
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
                    val filename = when {
                        createNameScreenshot -> "create-name-windows-original-port.png"
                        wideScreenshot -> "home-windows-wide-original-port.png"
                        else -> "home-windows-original-port.png"
                    }
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
            FielHomeHost(
                startInCreate = createNameScreenshot,
                monogramLaunchRequest = monogramLaunchRequest,
                libraryFolderRequest = libraryFolderRequest
            )
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

private enum class ActiveDetail { NONE, VIEWER, SIMULATOR, CONVERTER, MONOGRAM }

@Composable
private fun FielHomeHost(
    startInCreate: Boolean = false,
    monogramLaunchRequest: Int = 0,
    libraryFolderRequest: Int = 0
) {
    var area by remember {
        mutableStateOf(if (startInCreate) HomeArea.CREATE else HomeArea.HOME)
    }
    var detail by remember { mutableStateOf(ActiveDetail.NONE) }
    LaunchedEffect(monogramLaunchRequest) {
        if (monogramLaunchRequest > 0) {
            area = HomeArea.CREATE
            detail = ActiveDetail.MONOGRAM
        }
    }
    var activeDesign by remember { mutableStateOf<EmbroideryDesign?>(null) }
    var displayMode by remember { mutableStateOf(EmbroideryDisplayMode.SOLID) }
    var hoop by remember { mutableStateOf(HoopProfile.H100X100) }
    var showConnections by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<DesktopProjectCatalog.ScanResult?>(null) }
    val libraryStore = remember { DesktopLibraryActivityStore() }
    val initialLibraryState = remember(libraryStore) {
        runCatching { libraryStore.load() }
    }
    val initialData = initialLibraryState.getOrDefault(DesktopLibraryActivityStore.State())
    var favorites by remember { mutableStateOf(initialData.favorites) }
    var recentProjectIds by remember { mutableStateOf(initialData.recents) }
    var selectedLibraryFolder by remember { mutableStateOf(initialData.selectedFolder) }
    val libraryPersistenceLock = remember { Mutex() }

    // Arquivo pequeno e versionado em %LOCALAPPDATA%; não é um backup
    // de projetos e não altera nenhum FLP. Operações de disco fora da UI.
    LaunchedEffect(favorites, recentProjectIds, selectedLibraryFolder) {
        if (initialLibraryState.isSuccess) {
            val snapshot = DesktopLibraryActivityStore.State(
                favorites = favorites,
                recents = recentProjectIds,
                selectedFolder = selectedLibraryFolder
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    libraryPersistenceLock.withLock {
                        libraryStore.save(snapshot)
                    }
                }
            }.onFailure {
                snackbar.showSnackbar(
                    "Preferências locais não salvas: " + (it.message ?: "erro de acesso")
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        if (initialLibraryState.isFailure) {
            snackbar.showSnackbar(
                "Preferências da Biblioteca inválidas: proteção contra sobrescrita ativada."
            )
        } else {
            val remembered = selectedLibraryFolder
            if (remembered != null) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        DesktopProjectCatalog.scan(Path.of(remembered))
                    }
                }.onSuccess { scanned ->
                    catalog = scanned
                }.onFailure {
                    snackbar.showSnackbar(
                        "Reabra a pasta FLP da Biblioteca: " +
                            (it.message ?: "pasta indisponível")
                    )
                }
            }
        }
    }

    fun chooseLibraryFolder() {
        scope.launch {
            val chooser = JFileChooser().apply {
                dialogTitle = "Selecionar pasta de projetos FLP — somente leitura"
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            }
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@launch
            val selected = chooser.selectedFile.toPath()
            runCatching {
                withContext(Dispatchers.IO) { DesktopProjectCatalog.scan(selected) }
            }.fold(onSuccess = { scanned ->
                catalog = scanned
                // Manter favoritos/recentes de outras pastas, caso o usuário
                // alterne entre bibliotecas FLP. Não apagar histórico externo.
                selectedLibraryFolder = scanned.folder.toString()
                area = HomeArea.LIBRARY
                detail = ActiveDetail.NONE
                snackbar.showSnackbar(
                    "Biblioteca FLP: ${scanned.projects.size} projeto(s), " +
                        "${scanned.skippedInvalid} arquivo(s) inválido(s) ignorado(s)."
                )
            }, onFailure = { error ->
                snackbar.showSnackbar(
                    "Não foi possível ler a pasta FLP: ${error.message}"
                )
            })
        }
    }

    LaunchedEffect(libraryFolderRequest) {
        if (libraryFolderRequest > 0) chooseLibraryFolder()
    }

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

    fun saveProject() {
        val design = activeDesign ?: return
        scope.launch {
            val chooser = JFileChooser().apply {
                dialogTitle = "Salvar projeto original Brother Matrizes FLP"
                selectedFile = File("brother-matrizes-projeto.flp")
            }
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                runCatching {
                    DesktopFlpProjectFiles.saveNew(design, chooser.selectedFile.toPath())
                }.onSuccess { file ->
                    snackbar.showSnackbar("Projeto FLP salvo: ${file.fileName}")
                }.onFailure {
                    snackbar.showSnackbar("Projeto FLP não salvo: ${it.message}")
                }
            }
        }
    }

    fun openProject() {
        scope.launch {
            val chooser = JFileChooser().apply {
                dialogTitle = "Abrir matriz DST, PES, JEF ou projeto FLP"
                fileSelectionMode = JFileChooser.FILES_ONLY
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                runCatching {
                    DesktopEmbroideryFiles.open(chooser.selectedFile.toPath())
                }.onSuccess { design ->
                    openDesign(design, displayMode, ActiveDetail.VIEWER)
                    snackbar.showSnackbar("Matriz aberta no visualizador.")
                }.onFailure {
                    snackbar.showSnackbar("Não foi possível abrir a matriz: ${it.message}")
                }
            }
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
                            else {
                                area = target
                                detail = ActiveDetail.NONE
                                if (target == HomeArea.LIBRARY && catalog == null) {
                                    scope.launch {
                                        snackbar.showSnackbar(
                                            "No menu Biblioteca, selecione uma pasta de projetos FLP."
                                        )
                                    }
                                }
                            }
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
                    onOpen = { openProject() },
                    onSimulate = { detail = ActiveDetail.SIMULATOR },
                    onEdit = { informNotPorted("Editor") },
                    onEditName = { informNotPorted("Editar nome") },
                    onConvert = { detail = ActiveDetail.CONVERTER },
                    onTransfer = { informNotPorted("Enviar") },
                    onSaveProject = { saveProject() },
                    onSaveCopy = { exportDesign(MatrixConverter.preferredBrotherFormat) },
                    onShare = { informNotPorted("Compartilhar") },
                    onSavePeDesign = { exportDesign("PES", peDesign = true) },
                    onSharePeDesign = { informNotPorted("Compartilhar PE-Design") },
                    onPositionChange = { activeDesign = it }
                )
            } else if (detail == ActiveDetail.MONOGRAM) {
                // MonogramScreen oficial do Android: mesmo formulário, preview,
                // cores, estilos e motor de pontadas. Não imitar visualmente.
                MonogramScreen(
                    onBack = { detail = ActiveDetail.NONE; area = HomeArea.HOME },
                    onCreate = { design ->
                        openDesign(design, EmbroideryDisplayMode.SOLID, ActiveDetail.VIEWER)
                    },
                    onSimulate = { design ->
                        openDesign(design, EmbroideryDisplayMode.SOLID, ActiveDetail.SIMULATOR)
                    }
                )
            } else if (detail == ActiveDetail.CONVERTER && activeDesign != null) {
                // Composable oficial Android; arquivos somente CREATE_NEW.
                ConverterScreen(
                    design = checkNotNull(activeDesign),
                    onBack = { detail = ActiveDetail.VIEWER },
                    onSave = { format -> exportDesign(format) },
                    // Compartilhamento requer integração nativa Windows;
                    // não simular entrega nem salvamento.
                    onShare = { informNotPorted("Compartilhar matriz convertida") }
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
            } else if (area == HomeArea.LIBRARY) {
                // UI, abas, cartões e metadados copiados do Android.
                // Apenas o catálogo de arquivos FLP local é integrado nesta QA.
                ProjectLibraryScreen(
                    projects = catalog?.projects.orEmpty(),
                    favoriteProjectIds = favorites,
                    recentProjectIds = recentProjectIds,
                    sentMatrices = emptyList(),
                    onBack = { area = HomeArea.HOME },
                    onOpen = { summary ->
                        val path: Path? = catalog?.pathById?.get(summary.id)
                        if (path == null) {
                            scope.launch { snackbar.showSnackbar("Projeto não encontrado na pasta selecionada.") }
                        } else {
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        DesktopEmbroideryFiles.open(path)
                                    }
                                }.fold(onSuccess = { design ->
                                    recentProjectIds = (
                                        listOf(summary.id) + recentProjectIds.filter { it != summary.id }
                                    ).take(30)
                                    openDesign(design, EmbroideryDisplayMode.SOLID, ActiveDetail.VIEWER)
                                }, onFailure = { error ->
                                    snackbar.showSnackbar("FLP recusado: ${error.message}")
                                })
                            }
                        }
                    },
                    onTransfer = { informNotPorted("Transferência para a máquina") },
                    onToggleFavorite = { summary ->
                        favorites = if (summary.id in favorites)
                            favorites - summary.id else favorites + summary.id
                    },
                    // Nunca excluir projeto sem fluxo homologado e confirmação.
                    onDelete = {
                        informNotPorted("Exclusão de projetos")
                    },
                    onDeleteSent = { informNotPorted("Histórico de envios") },
                    onClearSent = { informNotPorted("Limpeza de envios") },
                    onBackup = { informNotPorted("Backup da Biblioteca") },
                    onRestore = { informNotPorted("Restauração da Biblioteca") },
                    onFonts = { area = HomeArea.FONTS },
                    onSearchMatrices = { informNotPorted("Pesquisa Telegram") },
                    onRequireGoogle = { informNotPorted("Conta Google Windows") }
                )
            } else if (area == HomeArea.HOME) {
                // Fonte original HomeScreen.kt do Android, não uma recriação.
                HomeScreen(
                    recent = activeDesign,
                    onCreateName = { openCreate() },
                    onFonts = { area = HomeArea.FONTS },
                    onTransfer = { informNotPorted("Enviar") },
                    onOpen = { openProject() },
                    onRecent = {
                        if (activeDesign != null) detail = ActiveDetail.VIEWER
                        else informNotPorted("Recente")
                    },
                    onSimulate = {
                        if (activeDesign != null) detail = ActiveDetail.SIMULATOR
                        else informNotPorted("Crie ou abra uma matriz primeiro")
                    },
                    onEdit = { informNotPorted("Editor") },
                    onConvert = {
                        if (activeDesign != null) detail = ActiveDetail.CONVERTER
                        else scope.launch {
                            snackbar.showSnackbar("Abra ou crie uma matriz antes de converter.")
                        }
                    },
                    onClearRecent = {
                        // Apaga somente a referência de sessão, nunca arquivos.
                        activeDesign = null
                        recentProjectIds = emptyList()
                        scope.launch {
                            snackbar.showSnackbar("Histórico de recentes limpo; arquivos preservados.")
                        }
                    },
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
