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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.timachado.brothermatrizes.HomeScreen
import com.timachado.brothermatrizes.CreateNameScreen
import com.timachado.brothermatrizes.CreateDrawingScreen
import com.timachado.brothermatrizes.ConverterScreen
import com.timachado.brothermatrizes.EditorScreen
import com.timachado.brothermatrizes.NameEditorScreen
import com.timachado.brothermatrizes.MonogramScreen
import com.timachado.brothermatrizes.MachineTransferScreen
import com.timachado.brothermatrizes.ProjectLibraryScreen
import com.timachado.brothermatrizes.EmbroideryDisplayMode
import com.timachado.brothermatrizes.SimulatorScreen
import com.timachado.brothermatrizes.ViewerScreen
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.NameCompositionMode
import com.timachado.brothermatrizes.core.embroidery.NamePersonalization
import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.core.embroidery.PesExportProfile
import com.timachado.brothermatrizes.R
import com.timachado.brothermatrizes.desktop.resources.desktopPainterResource
import com.timachado.brothermatrizes.desktop.platform.DesktopFlpProjectFiles
import com.timachado.brothermatrizes.desktop.platform.DesktopProjectCatalog
import com.timachado.brothermatrizes.desktop.platform.DesktopProjectRecycleBin
import com.timachado.brothermatrizes.desktop.platform.DesktopFlpBackupExporter
import com.timachado.brothermatrizes.desktop.platform.DesktopFlpBackupRestorer
import com.timachado.brothermatrizes.desktop.platform.DesktopAndroidBackupInspector
import com.timachado.brothermatrizes.desktop.platform.DesktopAndroidProjectRecovery
import com.timachado.brothermatrizes.desktop.platform.DesktopLibraryActivityStore
import com.timachado.brothermatrizes.desktop.platform.DesktopSentMatrixHistory
import com.timachado.brothermatrizes.desktop.platform.DesktopRecentProjectRecovery
import com.timachado.brothermatrizes.desktop.platform.DesktopLibraryRecentTracker
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveInventory
import com.timachado.brothermatrizes.desktop.platform.DesktopEmbroideryFiles
import com.timachado.brothermatrizes.desktop.platform.DesktopMachineTransferFile
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsShareHandoff
import com.timachado.brothermatrizes.desktop.platform.DesktopLaunchDocument
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsAccountQaOptIn
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
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.Toolkit
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.JDialog
import javax.swing.filechooser.FileNameExtensionFilter
import javax.imageio.ImageIO
import org.jetbrains.skia.Image
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
    val accountScreenshot = args.singleOrNull() == "--capture-account-qa"
    val fontsScreenshot = args.singleOrNull() == "--capture-fonts-qa"
    val transferScreenshot = args.singleOrNull() == "--capture-machine-transfer-qa"
    val drawingScreenshot = args.singleOrNull() == "--capture-create-drawing-qa"
    val archiveScreenshot = args.singleOrNull() == "--capture-archive-qa"
    val nameEditorScreenshot = args.singleOrNull() == "--capture-name-editor-qa"
    val nameEditorScrollScreenshot = args.singleOrNull() == "--capture-name-editor-scroll-qa"
    val nameEditorCtrlAQa = args.singleOrNull() == "--qa-name-editor-ctrl-a-apply"
    val nameEditorKeyboardQa =
        args.singleOrNull() == "--qa-name-editor-keyboard-apply" || nameEditorCtrlAQa
    val nameEditorApplyQa =
        args.singleOrNull() == "--qa-name-editor-click-apply" || nameEditorKeyboardQa
    val editorCapture = nameEditorScreenshot || nameEditorScrollScreenshot || nameEditorApplyQa
    val openPesChooserQa = args.singleOrNull() == "--qa-open-chooser-pes"
    val openJefChooserQa = args.singleOrNull() == "--qa-open-chooser-jef"
    val openDstChooserQa = args.singleOrNull() == "--qa-open-chooser-dst"
    val openFlpChooserQa = args.singleOrNull() == "--qa-open-chooser-flp"
    val fileChooserQa =
        openPesChooserQa || openJefChooserQa || openDstChooserQa || openFlpChooserQa
    val saveDialogNewQa = args.singleOrNull() == "--qa-save-flp-dialog-new"
    val saveDialogProtectQa = args.singleOrNull() == "--qa-save-flp-dialog-protect"
    val saveDialogQa = saveDialogNewQa || saveDialogProtectQa
    val screenshotMode =
        compactScreenshot || wideScreenshot || createNameScreenshot ||
            editorCapture || fileChooserQa || saveDialogQa ||
                accountScreenshot || fontsScreenshot || transferScreenshot ||
                drawingScreenshot || archiveScreenshot
    // Um caminho vindo do Explorer abre a matriz diretamente no Viewer.
    // As capturas QA continuam aceitando somente suas flags explícitas.
    val launchDocument = if (screenshotMode) null
        else DesktopLaunchDocument.requestedPath(args)
    require(args.isEmpty() || screenshotMode || launchDocument != null) {
        "Argumentos inválidos. Abra um FLP, DST, PES ou JEF válido."
    }
    val chooserQaFormat = when {
        openPesChooserQa -> "PES"
        openJefChooserQa -> "JEF"
        openDstChooserQa -> "DST"
        openFlpChooserQa -> "FLP"
        else -> null
    }
    val chooserQaFile = chooserQaFormat?.let { format ->
        val sampleFromAndroid = format == "PES" || format == "JEF"
        val filename = if (sampleFromAndroid) "random1.${format.lowercase()}"
                       else "qa-matriz-original.${format.lowercase()}"
        val target = Path.of("build/qa/chooser-fixtures", filename)
            .toAbsolutePath().normalize()
        Files.createDirectories(target.parent)
        if (sampleFromAndroid) {
            val source = Path.of("../app/src/test/resources/samples", filename)
                .toAbsolutePath().normalize()
            check(Files.isRegularFile(source) && !Files.isSymbolicLink(source)) {
                "A referência Android do seletor não está disponível."
            }
            // Copiar os binários de referência: nenhum original ou cliente é alterado.
            Files.copy(source, target)
            check(Files.readAllBytes(source).contentEquals(Files.readAllBytes(target)))
        } else {
            // DST e FLP de QA são criados pelo motor/codec oficial com uma
            // matriz sintética BM+Maria; não exigem dados reais do usuário.
            val synthetic = syntheticNameEditorQaDesign()
            if (format == "DST") {
                val converted = MatrixConverter.convert(synthetic, "DST").getOrThrow()
                Files.write(
                    target, converted.bytes,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
                )
            } else {
                DesktopFlpProjectFiles.saveNew(synthetic, target)
            }
            val reloaded = DesktopEmbroideryFiles.open(target)
            check(reloaded.points.isNotEmpty() && reloaded.endFound) {
                "A matriz sintética ${format} falhou antes do seletor."
            }
        }
        target
    }
    val chooserQaInitialBytes = chooserQaFile?.let(Files::readAllBytes)
    val saveDialogQaFile = if (saveDialogQa) {
        val fileName = if (saveDialogProtectQa)
            "qa-projeto-existente.flp" else "qa-projeto-novo.flp"
        Path.of("build/qa/save-fixtures", fileName).toAbsolutePath().normalize().also {
            Files.createDirectories(it.parent)
            if (saveDialogProtectQa) {
                DesktopFlpProjectFiles.saveNew(syntheticNameEditorQaDesign(), it)
            }
        }
    } else null
    val saveDialogQaOriginalBytes =
        if (saveDialogProtectQa) Files.readAllBytes(checkNotNull(saveDialogQaFile))
        else null
    // O mesmo monograma PREMIUM original da versão Android é utilizado
    // no ícone da janela e na barra de tarefas. Falhar em caso de ausência
    // é preferível a publicar o ícone genérico do Compose Desktop.
    val officialWindowIcon = remember {
        val resource = "drawable/brother_bm_launcher_safe.webp"
        val imageBytes = requireNotNull(
            Thread.currentThread().contextClassLoader.getResourceAsStream(resource)
        ) { "Ícone premium original Brother Matrizes ausente da distribuição." }
            .use { it.readBytes() }
        check(imageBytes.size > 128) { "Ícone original Brother Matrizes inválido." }
        BitmapPainter(Image.makeFromEncoded(imageBytes).toComposeImageBitmap())
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Brother Matrizes — Windows (QA interna)",
        icon = officialWindowIcon,
        state = WindowState(
            size = when {
                compactScreenshot || createNameScreenshot -> DpSize(420.dp, 680.dp)
                wideScreenshot -> DpSize(920.dp, 680.dp)
                editorCapture || fileChooserQa || saveDialogQa ||
                    accountScreenshot || fontsScreenshot ||
                    transferScreenshot || drawingScreenshot ||
                    archiveScreenshot -> DpSize(920.dp, 680.dp)
                else -> DpSize(1000.dp, 860.dp)
            }
        )
    ) {
        // Menu desktop não modifica a hierarquia original Compose Android.
        // Desabilitado durante capturas golden para manter a mesma viewport.
        var monogramLaunchRequest by remember { mutableIntStateOf(0) }
        var drawingLaunchRequest by remember { mutableIntStateOf(0) }
        var libraryFolderRequest by remember { mutableIntStateOf(0) }
        var archiveLaunchRequest by remember { mutableIntStateOf(0) }
        if (!screenshotMode) {
            MenuBar {
                Menu("Criar") {
                    Item("Monograma", onClick = { monogramLaunchRequest++ })
                    Item("Desenho / SVG", onClick = { drawingLaunchRequest++ })
                }
                Menu("Biblioteca") {
                    Item("Selecionar pasta FLP…", onClick = { libraryFolderRequest++ })
                    Item("Analisar ZIP / RAR / 7Z (somente leitura)…", onClick = { archiveLaunchRequest++ })
                }
            }
        }
        val composeWindow = window
        LaunchedEffect(screenshotMode) {
            if (saveDialogQa) {
                // JFileChooser Swing real de "Salvar como", confirmado por
                // Enter do teclado. Apenas FLP sintético em build/qa.
                composeWindow.setLocation(20, 20)
                try {
                    withContext(Dispatchers.IO) {
                        val deadline = System.nanoTime() + 40_000_000_000L
                        while (System.nanoTime() < deadline && java.awt.Window.getWindows().none {
                            it is JDialog && it.isShowing &&
                                it.title.contains("Salvar projeto original Brother Matrizes FLP")
                        }) Thread.sleep(250L)
                        check(java.awt.Window.getWindows().any {
                            it is JDialog && it.isShowing &&
                                it.title.contains("Salvar projeto original Brother Matrizes FLP")
                        }) { "O diálogo real Salvar como não foi apresentado." }
                        val robot = Robot()
                        robot.keyPress(KeyEvent.VK_ENTER)
                        robot.keyRelease(KeyEvent.VK_ENTER)
                        val marker = Path.of(
                            if (saveDialogProtectQa) "build/qa/save-flp-protected.txt"
                            else "build/qa/save-flp-created.txt"
                        )
                        while (System.nanoTime() < deadline && !Files.isRegularFile(marker)) {
                            Thread.sleep(250L)
                        }
                        check(Files.isRegularFile(marker)) {
                            "Salvar como não produziu a confirmação esperada."
                        }
                        val target = checkNotNull(saveDialogQaFile)
                        check(Files.isRegularFile(target)) {
                            "O arquivo FLP de QA não existe."
                        }
                        if (saveDialogProtectQa) {
                            check(Files.readAllBytes(target).contentEquals(
                                checkNotNull(saveDialogQaOriginalBytes)
                            )) { "A tentativa de sobrescrita alterou o FLP existente." }
                        } else {
                            val restored = DesktopFlpProjectFiles.open(target)
                            check(restored.editableName?.options?.text == "Maria" &&
                                restored.editableName?.original?.points?.isNotEmpty() == true) {
                                "O Salvar como perdeu o nome editável no FLP."
                            }
                        }
                        Thread.sleep(900L)
                        check(java.awt.Window.getWindows().none {
                            it is JDialog && it.isShowing &&
                                it.title.contains("Salvar projeto original Brother Matrizes FLP")
                        }) { "O seletor Salvar como não foi fechado." }
                        val pane = composeWindow.contentPane
                        val origin = pane.locationOnScreen
                        val bounds = Rectangle(origin.x, origin.y, pane.width, pane.height)
                        val screen = Toolkit.getDefaultToolkit().screenSize
                        check(bounds.width >= 700 && bounds.height >= 600 &&
                            bounds.x >= 0 && bounds.y >= 0 &&
                            bounds.x + bounds.width <= screen.width &&
                            bounds.y + bounds.height <= screen.height - 42) {
                            "A janela QA do Salvar como excedeu a área do Windows."
                        }
                        val screenshot = robot.createScreenCapture(bounds)
                        val output = File(
                            if (saveDialogProtectQa) "build/qa/save-flp-protected-viewer.png"
                            else "build/qa/save-flp-created-viewer.png"
                        )
                        output.parentFile.mkdirs()
                        check(ImageIO.write(screenshot, "png", output) &&
                            output.length() > 10_000L) {
                            "A captura após Salvar como está ausente."
                        }
                        println(if (saveDialogProtectQa)
                            "BROTHER_WINDOWS_SAVE_DIALOG_PROTECT_FLPAUTH_OK"
                        else "BROTHER_WINDOWS_SAVE_DIALOG_NEW_FLPRT_OK")
                        exitProcess(0)
                    }
                } catch (error: Throwable) {
                    System.err.println(
                        "BROTHER_WINDOWS_SAVE_DIALOG_QA_FAIL: " +
                            (error.message ?: error.javaClass.simpleName)
                    )
                    exitProcess(2)
                }
                return@LaunchedEffect
            }
            if (fileChooserQa) {
                // A janela inicial 1000x860 invadia a taskbar no runner
                // 1024x768, apesar de a seleção PES/JEF já ter funcionado.
                // Posicionar a viewport de 920x680dp na área segura.
                composeWindow.setLocation(20, 20)
                try {
                // Exercita o JFileChooser REAL do comando Abrir da Home:
                // janela Swing aparece, o teclado confirma a seleção de
                // arquivo pré-posicionado (cópia sintética de referência), o
                // callback importa e navega para o Visualizador. Sem API
                // privada do seletor e sem dados de clientes.
                withContext(Dispatchers.IO) {
                    val deadline = System.nanoTime() + 40_000_000_000L
                    var dialogVisible = false
                    while (System.nanoTime() < deadline) {
                        dialogVisible = java.awt.Window.getWindows().any { window ->
                            window is JDialog && window.isShowing &&
                                window.title.contains("Abrir matriz DST")
                        }
                        if (dialogVisible) break
                        Thread.sleep(250L)
                    }
                    check(dialogVisible) { "O JFileChooser da Home não foi apresentado." }
                    val robot = Robot()
                    robot.keyPress(KeyEvent.VK_ENTER)
                    robot.keyRelease(KeyEvent.VK_ENTER)
                    val format = checkNotNull(chooserQaFormat)
                    val marker = Path.of("build/qa/chooser-${format.lowercase()}-opened.txt")
                    while (System.nanoTime() < deadline && !Files.isRegularFile(marker)) {
                        Thread.sleep(250L)
                    }
                    check(Files.isRegularFile(marker)) {
                        "O diálogo não importou a matriz ${format} no Visualizador."
                    }
                    Thread.sleep(1_400L)
                    check(java.awt.Window.getWindows().none { window ->
                        window is JDialog && window.isShowing &&
                            window.title.contains("Abrir matriz DST")
                    }) { "JFileChooser não foi fechado após confirmar a matriz." }
                    val pane = composeWindow.contentPane
                    val point = pane.locationOnScreen
                    val bounds = Rectangle(point.x, point.y, pane.width, pane.height)
                    val screen = Toolkit.getDefaultToolkit().screenSize
                    check(bounds.width >= 700 && bounds.height >= 600 &&
                        bounds.x >= 0 && bounds.y >= 0 &&
                        bounds.x + bounds.width <= screen.width &&
                        bounds.y + bounds.height <= screen.height - 42
                    ) {
                        "O Viewer QA excedeu a área visível do monitor Windows: " +
                            "$bounds; tela=${screen.width}x${screen.height}"
                    }
                    val screenshot = robot.createScreenCapture(bounds)
                    val output = File("build/qa/chooser-${format.lowercase()}-viewer.png")
                    output.parentFile.mkdirs()
                    check(ImageIO.write(screenshot, "png", output) &&
                        output.length() > 10_000L
                    ) { "Visualizador da matriz importada não foi capturado." }
                    check(
                        Files.readAllBytes(checkNotNull(chooserQaFile))
                            .contentEquals(checkNotNull(chooserQaInitialBytes))
                    ) { "O seletor alterou os bytes da cópia de trabalho." }
                    if (format == "PES" || format == "JEF") {
                        check(
                            Files.readAllBytes(chooserQaFile).contentEquals(
                                Files.readAllBytes(Path.of(
                                    "../app/src/test/resources/samples",
                                    "random1.${format.lowercase()}"
                                ))
                            )
                        ) { "O arquivo Android de referência foi alterado." }
                    }
                    println("BROTHER_WINDOWS_CHOOSER_${format}_VIEWER_OK")
                    exitProcess(0)
                }
                } catch (error: Throwable) {
                    System.err.println(
                        "BROTHER_WINDOWS_CHOOSER_QA_FAIL: " +
                            (error.message ?: error.javaClass.simpleName)
                    )
                    exitProcess(2)
                }
                return@LaunchedEffect
            }
            if (screenshotMode) {
                // Renderização estabilizada em Windows real do runner;
                // não é captura simulada da Home Android.
                // O runner Windows tem taskbar e resolução menor que o Android.
                // A janela anterior de 890dp invadia a barra do sistema.
                composeWindow.setLocation(20, 20)
                delay(
                    when {
                        editorCapture -> 11_000L
                        createNameScreenshot || accountScreenshot -> 5_000L
                        else -> 2_500L
                    }
                )
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
                    val robot = Robot()
                    if (nameEditorApplyQa) {
                        // Clique físico do mouse no CTA da UI Android,
                        // sem invocar callbacks artificialmente por código.
                        // A própria tela precisa completar onApply, salvar FLP
                        // sintético via codec oficial e reabrir o projeto.
                        val file = Path.of(
                            when {
                                nameEditorCtrlAQa -> "build/qa/name-editor-ctrl-a-synthetic.flp"
                                nameEditorKeyboardQa -> "build/qa/name-editor-keyboard-synthetic.flp"
                                else -> "build/qa/name-editor-apply-synthetic.flp"
                            }
                        )
                        check(!Files.exists(file)) {
                            "Arquivo QA anterior não pode ser sobrescrito."
                        }
                        if (nameEditorKeyboardQa) {
                            // O campo NameEditorScreen original fica parcialmente
                            // visível acima do CTA fixo na janela 904×641.
                            // Clique físico no TextField, Ctrl+A e digitação
                            // AWT real; não atribuir draft ou onApply pelo host.
                            robot.mouseMove(
                                bounds.x + 230,
                                bounds.y + bounds.height - 71
                            )
                            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                            delay(400L)
                            // O Ctrl+A no runner Compose Desktop não selecionou
                            // o texto (a primeira QA produziu "Mariaana").
                            // Campo limitado a 24 caracteres pelo Android:
                            // ir ao fim e apagar fisicamente até 24 posições.
                            // Não manipular estado interno do Android por código.
                            if (nameEditorCtrlAQa) {
                                // Diagnóstico separado do atalho Windows: NÃO
                                // usar Backspace/Home nem alterar o draft.
                                // O campo precisa executar Ctrl+A nativamente.
                                delay(1_000L)
                                robot.keyPress(KeyEvent.VK_CONTROL)
                                robot.keyPress(KeyEvent.VK_A)
                                delay(180L)
                                robot.keyRelease(KeyEvent.VK_A)
                                robot.keyRelease(KeyEvent.VK_CONTROL)
                                delay(700L)
                            } else {
                                robot.keyPress(KeyEvent.VK_END)
                                robot.keyRelease(KeyEvent.VK_END)
                                repeat(24) {
                                    robot.keyPress(KeyEvent.VK_BACK_SPACE)
                                    robot.keyRelease(KeyEvent.VK_BACK_SPACE)
                                    delay(35L)
                                }
                            }
                            for (key in intArrayOf(KeyEvent.VK_A, KeyEvent.VK_N, KeyEvent.VK_A)) {
                                robot.keyPress(key)
                                robot.keyRelease(key)
                                delay(90L)
                            }
                            delay(3_500L)
                            val inputPng = File(
                                if (nameEditorCtrlAQa) "build/qa/name-editor-ctrl-a-input-qa.png"
                                else "build/qa/name-editor-keyboard-input-qa.png"
                            )
                            inputPng.parentFile.mkdirs()
                            check(ImageIO.write(
                                robot.createScreenCapture(bounds), "png", inputPng
                            ) && inputPng.length() > 10_000L) {
                                "Não foi possível registrar a digitação QA real."
                            }
                        }
                        robot.mouseMove(
                            bounds.x + bounds.width / 2,
                            bounds.y + bounds.height - 38
                        )
                        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                        delay(3_200L)
                        check(Files.isRegularFile(file) && Files.size(file) > 100L) {
                            "O clique real não acionou Aplicar nome/FLP sintético."
                        }
                        val reopened = DesktopFlpProjectFiles.open(file)
                        val expectedName = if (nameEditorKeyboardQa) "ana" else "Maria"
                        check(reopened.editableName?.options?.text == expectedName &&
                            reopened.editableName?.original?.points?.isNotEmpty() == true) {
                            "FLP do Editor não preservou o nome editável/original."
                        }
                        val post = robot.createScreenCapture(bounds)
                        val png = File(
                            when {
                                nameEditorCtrlAQa -> "build/qa/name-editor-ctrl-a-applied-qa.png"
                                nameEditorKeyboardQa -> "build/qa/name-editor-keyboard-applied-qa.png"
                                else -> "build/qa/name-editor-applied-qa.png"
                            }
                        )
                        check(ImageIO.write(post, "png", png) && png.length() > 10_000) {
                            "Captura do visualizador após Aplicar não foi gerada."
                        }
                        println(
                            (when {
                                nameEditorCtrlAQa -> "BROTHER_WINDOWS_NAME_EDITOR_CTRL_A_MOUSE_FLP_OK "
                                nameEditorKeyboardQa -> "BROTHER_WINDOWS_NAME_EDITOR_KEYBOARD_MOUSE_FLP_OK "
                                else -> "BROTHER_WINDOWS_NAME_EDITOR_APPLY_MOUSE_FLP_OK "
                            }) +
                                "pontos=${reopened.points.size}"
                        )
                        exitProcess(0)
                    }
                    val beforeScroll = if (nameEditorScrollScreenshot)
                        robot.createScreenCapture(bounds) else null
                    if (nameEditorScrollScreenshot) {
                        // Rolagem no conteúdo abaixo da prévia, não nos gestos
                        // do bastidor. Confirma acessibilidade dos controles
                        // inferiores em monitores Windows de 768 px.
                        robot.mouseMove(
                            bounds.x + (bounds.width * 0.23f).toInt(),
                            bounds.y + (bounds.height * 0.81f).toInt()
                        )
                        robot.mouseWheel(10)
                        delay(1_400L)
                    }
                    val screenshot = robot.createScreenCapture(bounds)
                    if (beforeScroll != null) {
                        var changed = 0
                        for (y in 80 until screenshot.height - 84 step 16) {
                            for (x in 45 until screenshot.width - 45 step 16) {
                                if (beforeScroll.getRGB(x, y) !=
                                    screenshot.getRGB(x, y)) changed++
                            }
                        }
                        check(changed >= 180) {
                            "Editor de Nomes não respondeu à rolagem do mouse " +
                                "(amostras de pixels alterados=$changed)."
                        }
                    }
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
                    if (editorCapture || transferScreenshot || drawingScreenshot || archiveScreenshot) {
                        // Telas de detalhe usam tela cheia sem barra inferior.
                        // Exigir uma composição real rica em pixels, impedindo
                        // upload silencioso de janela vazia ou da área de trabalho.
                        val rgbColors = buildSet {
                            for (y in 19 until screenshot.height - 19 step 11) {
                                for (x in 19 until screenshot.width - 19 step 11) {
                                    add(screenshot.getRGB(x, y) and 0xFFFFFF)
                                }
                            }
                        }
                        check(bounds.width >= 700 && bounds.height >= 600 &&
                            rgbColors.size >= 18
                        ) {
                            "Editor de Nomes não renderizado corretamente no Windows " +
                                "(cores distintas=${rgbColors.size}, área=$bounds)."
                        }
                    } else {
                        check(correctNav >= sampleX.size * 0.70) {
                            "Captura rejeitada: navegação Android não está " +
                                "visível no rodapé do Windows (amostras=$correctNav/" +
                                "${sampleX.size})."
                        }
                    }
                    val filename = when {
                        createNameScreenshot -> "create-name-windows-original-port.png"
                        accountScreenshot -> "account-windows-readonly-qa.png"
                        fontsScreenshot -> "fonts-windows-readonly-qa.png"
                        transferScreenshot -> "machine-transfer-windows-qa.png"
                        drawingScreenshot -> "create-drawing-windows-qa.png"
                        archiveScreenshot -> "archive-inventory-windows-qa.png"
                        nameEditorScreenshot -> "name-editor-windows-original-port.png"
                        nameEditorScrollScreenshot -> "name-editor-windows-scrolled-qa.png"
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
                        (when {
                            editorCapture -> "BROTHER_WINDOWS_NAME_EDITOR_SCREENSHOT_OK "
                            accountScreenshot -> "BROTHER_WINDOWS_ACCOUNT_SCREENSHOT_OK "
                            fontsScreenshot -> "BROTHER_WINDOWS_FONTS_SCREENSHOT_OK "
                            transferScreenshot -> "BROTHER_WINDOWS_MACHINE_TRANSFER_SCREENSHOT_OK "
                            drawingScreenshot -> "BROTHER_WINDOWS_CREATE_DRAWING_SCREENSHOT_OK "
                            archiveScreenshot -> "BROTHER_WINDOWS_ARCHIVE_PREVIEW_SCREENSHOT_OK "
                            else -> "BROTHER_WINDOWS_FIEL_HOME_SCREENSHOT_OK "
                        }) +
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
                startInAccountQa = accountScreenshot,
                startInFontsQa = fontsScreenshot,
                startInTransferQa = transferScreenshot,
                startInDrawingQa = drawingScreenshot,
                startInArchiveQa = archiveScreenshot,
                startInNameEditorQa = editorCapture,
                startInNameEditorApplyQa = nameEditorApplyQa,
                startInNameEditorKeyboardQa = nameEditorKeyboardQa,
                startInNameEditorCtrlAQa = nameEditorCtrlAQa,
                startInChooserQaFile = chooserQaFile,
                startInLaunchDocument = launchDocument,
                disableRecentRecovery = screenshotMode,
                startInSaveDialogQaFile = saveDialogQaFile,
                startInSaveDialogProtectQa = saveDialogProtectQa,
                startInSaveDialogOriginalBytes = saveDialogQaOriginalBytes,
                monogramLaunchRequest = monogramLaunchRequest,
                drawingLaunchRequest = drawingLaunchRequest,
                archiveLaunchRequest = archiveLaunchRequest,
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

private enum class ActiveDetail { NONE, VIEWER, SIMULATOR, CONVERTER, MONOGRAM, EDITOR, NAME_EDITOR, TRANSFER, DRAWING, ARCHIVE_PREVIEW, COMMUNITY, TELEGRAM }

/**
 * Matriz sintética SEM dados de cliente para teste visual específico.
 * Usa o mesmo gerador Android que a QA unitária; nenhum arquivo é aberto,
 * enviado, exportado ou salvo. A camada personalizada faz o autoajuste
 * inicial do Editor começar no mesmo estado que um projeto já editável.
 */
private fun syntheticNameEditorQaDesign(): EmbroideryDesign {
    val base = TextLayoutGenerator.generate(
        TextLayoutOptions(
            textOptions = TextMatrixOptions(
                text = "TM",
                heightMm = 12f,
                style = TextStitchStyle.RUNNING,
                outputFormat = "DST",
                hoopProfile = null,
                enforceHoop = false
            )
        )
    ).getOrThrow()
    return NamePersonalization.add(
        original = base.copy(hoopProfile = null),
        options = TextMatrixOptions(
            text = "Maria",
            heightMm = 8f,
            style = TextStitchStyle.SATIN,
            outputFormat = "DST",
            hoopProfile = null,
            enforceHoop = false
        ),
        centerXUnits = 0,
        centerYUnits = 0,
        compositionMode = NameCompositionMode.MONOGRAM_OVERLAY
    ).getOrThrow()
}

@Composable
private fun FielHomeHost(
    startInCreate: Boolean = false,
    startInAccountQa: Boolean = false,
    startInFontsQa: Boolean = false,
    startInTransferQa: Boolean = false,
    startInDrawingQa: Boolean = false,
    startInArchiveQa: Boolean = false,
    startInNameEditorQa: Boolean = false,
    startInNameEditorApplyQa: Boolean = false,
    startInNameEditorKeyboardQa: Boolean = false,
    startInNameEditorCtrlAQa: Boolean = false,
    startInChooserQaFile: Path? = null,
    startInLaunchDocument: Path? = null,
    disableRecentRecovery: Boolean = false,
    startInSaveDialogQaFile: Path? = null,
    startInSaveDialogProtectQa: Boolean = false,
    startInSaveDialogOriginalBytes: ByteArray? = null,
    monogramLaunchRequest: Int = 0,
    drawingLaunchRequest: Int = 0,
    archiveLaunchRequest: Int = 0,
    libraryFolderRequest: Int = 0
) {
    // Shared QA-only Google/DPAPI controller: one lock per window, retained
    // while navigating Minha Conta -> Fontes. Default build remains OFF.
    val accountQaEnvironment = remember {
        runCatching { DesktopWindowsAccountQaOptIn.openWhenOptedIn() }
            .getOrNull()
    }
    DisposableEffect(accountQaEnvironment) {
        onDispose { accountQaEnvironment?.close() }
    }
    val fontCatalogAccess = remember(accountQaEnvironment) {
        DesktopWindowsAccountQaOptIn.fontCatalogAccessForQa(
            accountQaEnvironment
        )
    }
    val telegramSearchAccess = remember(accountQaEnvironment) {
        DesktopWindowsAccountQaOptIn.telegramSearchAccessForQa(accountQaEnvironment)
    }

    var area by remember {
        mutableStateOf(
            when {
                startInAccountQa -> HomeArea.ACCOUNT
                startInFontsQa -> HomeArea.FONTS
                startInCreate -> HomeArea.CREATE
                else -> HomeArea.HOME
            }
        )
    }
    var detail by remember {
        mutableStateOf(
            when {
                startInNameEditorQa -> ActiveDetail.NAME_EDITOR
                startInTransferQa -> ActiveDetail.TRANSFER
                startInDrawingQa -> ActiveDetail.DRAWING
                startInArchiveQa -> ActiveDetail.ARCHIVE_PREVIEW
                startInSaveDialogQaFile != null -> ActiveDetail.VIEWER
                else -> ActiveDetail.NONE
            }
        )
    }
    LaunchedEffect(drawingLaunchRequest) {
        if (drawingLaunchRequest > 0) {
            area = HomeArea.CREATE
            detail = ActiveDetail.DRAWING
        }
    }
    LaunchedEffect(monogramLaunchRequest) {
        if (monogramLaunchRequest > 0) {
            area = HomeArea.CREATE
            detail = ActiveDetail.MONOGRAM
        }
    }
    var activeDesign by remember {
        mutableStateOf(
            if (startInNameEditorQa || startInSaveDialogQaFile != null || startInTransferQa)
                syntheticNameEditorQaDesign() else null
        )
    }
    var displayMode by remember { mutableStateOf(EmbroideryDisplayMode.SOLID) }
    var hoop by remember { mutableStateOf(HoopProfile.H100X100) }
    var showConnections by remember { mutableStateOf(false) }
    // Archive QA is metadata-only and intentionally never persists selected
    // archive names, contents, passwords or per-item commercial reservations.
    var archiveBusy by remember { mutableStateOf(false) }
    var archiveError by remember { mutableStateOf<String?>(null) }
    var archivePreview by remember {
        mutableStateOf<DesktopArchiveInventory.Preview?>(
            if (startInArchiveQa)
                DesktopArchiveInventory.Preview(
                    "QA-arquivo-sintetico.zip",
                    entries = listOf(
                        DesktopArchiveInventory.Item(
                            0, "Colecao/flor.pes", "flor.pes", "PES", 8192,
                            DesktopArchiveInventory.Category.MATRIX
                        ),
                        DesktopArchiveInventory.Item(
                            1, "Fontes/letra.ttf", "letra.ttf", "TTF", 4096,
                            DesktopArchiveInventory.Category.FONT
                        )
                    ),
                    incompatibleCount = 1,
                    scannedCount = 3,
                    uncompressedBytes = 13_312
                )
            else null
        )
    }
    LaunchedEffect(archiveLaunchRequest) {
        if (archiveLaunchRequest > 0) {
            archivePreview = null
            archiveError = null
            area = HomeArea.LIBRARY
            detail = ActiveDetail.ARCHIVE_PREVIEW
        }
    }
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
    // Exact Android SentMatrixRecord fields, stored independently from FLP
    // metadata. Only a successful native Windows machine-file save adds a
    // history record; opening or previewing a file never counts as delivery.
    val sentHistory = remember { DesktopSentMatrixHistory() }
    val initialSentState = remember(sentHistory) { runCatching { sentHistory.list() } }
    var sentMatrices by remember {
        mutableStateOf(initialSentState.getOrDefault(emptyList()))
    }
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
                // Startup recovery is only for normal Windows launches.
                // QA captures and shell-file openings retain their exact
                // initial state and never read extra customer documents.
                val normalStartup = !disableRecentRecovery &&
                    !startInCreate && !startInAccountQa &&
                    !startInFontsQa && !startInNameEditorQa &&
                    !startInTransferQa && !startInDrawingQa && !startInArchiveQa &&
                    startInChooserQaFile == null &&
                    startInLaunchDocument == null &&
                    startInSaveDialogQaFile == null
                runCatching {
                    withContext(Dispatchers.IO) {
                        val scanned = DesktopProjectCatalog.scan(Path.of(remembered))
                        val recovered = if (normalStartup)
                            DesktopRecentProjectRecovery.mostRecentValid(
                                scanned, initialData.recents
                            ) else null
                        scanned to recovered
                    }
                }.onSuccess { (scanned, recovered) ->
                    catalog = scanned
                    if (recovered != null && activeDesign == null &&
                        area == HomeArea.HOME && detail == ActiveDetail.NONE) {
                        // Display last-opened FLP in Android's original Home
                        // Recent card; keep every project file untouched.
                        activeDesign = recovered.design
                    }
                }.onFailure {
                    snackbar.showSnackbar(
                        "Reabra a pasta FLP da Biblioteca: " +
                            (it.message ?: "pasta indisponível")
                    )
                }
            }
        }
    }

    suspend fun registerOpenedOrSavedFlp(file: Path) {
        val selectedFolderSnapshot = selectedLibraryFolder ?: return
        val tracked = runCatching {
            withContext(Dispatchers.IO) {
                DesktopLibraryRecentTracker.refreshIfInLibrary(
                    selectedFolderSnapshot, file
                )
            }
        }.getOrElse { error ->
            snackbar.showSnackbar(
                "A Biblioteca não foi atualizada: " +
                    (error.message ?: "pasta FLP indisponível")
            )
            return
        }
        if (tracked != null && selectedLibraryFolder == selectedFolderSnapshot) {
            catalog = tracked.catalog
            recentProjectIds = DesktopLibraryRecentTracker.putFirst(
                tracked.id, recentProjectIds
            )
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

    fun analyzeArchiveOnly() {
        if (archiveBusy) return
        scope.launch {
            val chooser = JFileChooser().apply {
                dialogTitle = "Analisar pacote ZIP / RAR / 7Z — somente leitura"
                fileSelectionMode = JFileChooser.FILES_ONLY
                isAcceptAllFileFilterUsed = false
                fileFilter = FileNameExtensionFilter(
                    "Pacotes ZIP, RAR e 7Z", "zip", "rar", "7z"
                )
            }
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@launch
            val selected = chooser.selectedFile.toPath().toAbsolutePath().normalize()
            archiveBusy = true
            archivePreview = null
            archiveError = null
            try {
                val inspected = withContext(Dispatchers.IO) {
                    DesktopArchiveInventory.inspect(selected)
                }
                archivePreview = inspected
            } catch (failure: Exception) {
                archiveError = failure.message ?: "Pacote ZIP/RAR/7Z inválido ou protegido por senha."
            } finally {
                archiveBusy = false
            }
        }
    }

    fun backupLocalFlpProjects() {
        val chosen = catalog?.folder
        if (chosen == null) {
            scope.launch {
                snackbar.showSnackbar(
                    "Primeiro selecione uma pasta de projetos FLP no menu Biblioteca."
                )
            }
            return
        }
        scope.launch {
            // Unlike Android's full backup, this exports LOCAL FLP projects
            // only; no TTF/OTF, device settings or account data are included.
            val chooser = JFileChooser().apply {
                dialogTitle = "Backup dos projetos FLP locais (não inclui fontes)"
                selectedFile = File("Brother-Matrizes-projetos-FLP.zip")
                fileSelectionMode = JFileChooser.FILES_ONLY
                isAcceptAllFileFilterUsed = false
                fileFilter = FileNameExtensionFilter(
                    "Backup local de projetos FLP (*.zip)", "zip"
                )
            }
            if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return@launch
            val selected = chooser.selectedFile.toPath().toAbsolutePath().normalize()
            runCatching {
                withContext(Dispatchers.IO) {
                    DesktopFlpBackupExporter.exportLocalFlp(chosen, selected)
                }
            }.fold(onSuccess = { result ->
                snackbar.showSnackbar(
                    "Backup local FLP criado: ${result.exportedProjects} projeto(s). " +
                        "Fontes, conta e configurações não estão incluídas."
                )
            }, onFailure = { error ->
                snackbar.showSnackbar(
                    "Backup local FLP não criado: ${error.message ?: "erro de acesso"}"
                )
            })
        }
    }

    fun restoreLocalFlpProjects() {
        scope.launch {
            // FLP ZIP local is restored into a new folder. Original Android
            // backups are only inspected; fonts/projects are NOT imported.
            val sourceChooser = JFileChooser().apply {
                dialogTitle = "Restaurar FLP local ou analisar backup original Android"
                fileSelectionMode = JFileChooser.FILES_ONLY
                isAcceptAllFileFilterUsed = false
                fileFilter = FileNameExtensionFilter(
                    "FLP local ZIP ou backup Android", "zip",
                    "brother_matrizes-fullbackup", "brother_matrizes-backup"
                )
            }
            if (sourceChooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@launch
            val backup = sourceChooser.selectedFile.toPath().toAbsolutePath().normalize()
            val isLocalFlpZip = backup.fileName.toString()
                .endsWith(".zip", ignoreCase = true)
            // Android archives require explicit informed consent. A preview
            // alone never writes projects, fonts, quotas or account data.
            val androidPreview = if (!isLocalFlpZip) {
                val checked = runCatching {
                    withContext(Dispatchers.IO) {
                        DesktopAndroidBackupInspector.inspect(backup)
                    }
                }.getOrElse { error ->
                    snackbar.showSnackbar(
                        "Backup Android recusado: " +
                            (error.message ?: "formato inválido ou incompleto")
                    )
                    return@launch
                }
                if (checked.projectCount !in 1..200) {
                    snackbar.showSnackbar(
                        "Recuperação Windows admite de 1 a 200 matrizes por backup. " +
                            "Nenhum arquivo foi restaurado."
                    )
                    return@launch
                }
                val accepted = JOptionPane.showConfirmDialog(
                    null,
                    "Backup Android verificado: " + checked.projectCount +
                        " matriz(es), " + checked.fontCount + " fonte(s).\n" +
                        "Recuperar APENAS os projetos FLP em uma NOVA pasta?\n" +
                        "Fontes, conta, licenças e configurações NÃO serão importadas.",
                    "Recuperar matrizes do Android — confirmação",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
                )
                if (accepted != JOptionPane.YES_OPTION) return@launch
                checked
            } else null
            val folderChooser = JFileChooser().apply {
                dialogTitle = "Escolher pasta PAI para criar uma NOVA pasta restaurada"
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                selectedFile = catalog?.folder?.toFile()
            }
            if (folderChooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@launch
            val parent = folderChooser.selectedFile.toPath().toAbsolutePath().normalize()
            runCatching {
                withContext(Dispatchers.IO) {
                    val recovered = if (androidPreview != null) {
                        val result = DesktopAndroidProjectRecovery.restoreProjectsOnly(
                            backup, parent
                        )
                        result.folder to result.restoredProjects
                    } else {
                        val result = DesktopFlpBackupRestorer.restoreLocalFlpZip(
                            backup, parent
                        )
                        result.folder to result.importedProjects
                    }
                    Triple(
                        recovered.first, recovered.second,
                        DesktopProjectCatalog.scan(recovered.first)
                    )
                }
            }.fold(onSuccess = { (newFolder, projectCount, scanned) ->
                // No existing FLP was replaced: switch to the new folder
                // so the recovered projects can be opened immediately.
                catalog = scanned
                selectedLibraryFolder = newFolder.toString()
                area = HomeArea.LIBRARY
                detail = ActiveDetail.NONE
                snackbar.showSnackbar(
                    "Recuperados " + projectCount +
                        " projeto(s) FLP em nova pasta: " + newFolder.toString() +
                        if (androidPreview != null)
                            ". Fontes Android não foram importadas."
                        else ""
                )
            }, onFailure = { error ->
                snackbar.showSnackbar(
                    "Restauração FLP cancelada: " +
                        (error.message ?: "backup inválido ou inacessível")
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
                "$feature ainda não está disponível no Windows. " +
                    "As funções prontas continuam acessíveis; este recurso " +
                    "precisa de homologação antes da publicação comercial."
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

    fun exportDesign(
        targetFormat: String,
        peDesign: Boolean = false,
        revealInExplorer: Boolean = false
    ) {
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
                    }.onSuccess { saved ->
                        if (revealInExplorer) {
                            // A opção de compartilhar no Windows prepara uma
                            // cópia e revela o arquivo, sem enviar a ninguém.
                            runCatching {
                                DesktopWindowsShareHandoff.revealSavedMatrix(saved)
                            }.onSuccess {
                                snackbar.showSnackbar(
                                    "Matriz salva e localizada no Explorer. " +
                                        "Escolha o aplicativo ou destinatário manualmente."
                                )
                            }.onFailure {
                                snackbar.showSnackbar(
                                    "Matriz salva. Não foi possível abrir o Explorer; " +
                                        "acesse a pasta escolhida para compartilhar."
                                )
                            }
                        } else {
                            snackbar.showSnackbar("Matriz salva como novo arquivo.")
                        }
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

    fun exportMachineFile(
        format: String,
        selectedHoop: HoopProfile,
        forMachineApp: Boolean
    ) {
        val design = activeDesign ?: return
        scope.launch {
            // Same Android machine safety validator and converter used by
            // the original Transfer screen; no direct USB/Wi-Fi protocol.
            val prepared = runCatching {
                withContext(Dispatchers.Default) {
                    DesktopMachineTransferFile.prepare(design, format, selectedHoop)
                }
            }.getOrElse { error ->
                snackbar.showSnackbar(
                    "Matriz não exportada: " + (error.message ?: "validação recusada")
                )
                return@launch
            }
            val chooser = JFileChooser().apply {
                dialogTitle = if (forMachineApp)
                    "Salvar arquivo para importar no aplicativo da bordadeira"
                else
                    "Salvar matriz em arquivo ou pendrive selecionado"
                selectedFile = File(prepared.suggestedName)
                fileSelectionMode = JFileChooser.FILES_ONLY
                isAcceptAllFileFilterUsed = false
                fileFilter = FileNameExtensionFilter(
                    "Matriz ${prepared.format} (*.${prepared.format.lowercase()})",
                    prepared.format.lowercase()
                )
            }
            if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return@launch
            val chosen = chooser.selectedFile.toPath().toAbsolutePath().normalize()
            runCatching {
                withContext(Dispatchers.IO) {
                    DesktopMachineTransferFile.saveNew(prepared, chosen)
                }
            }.fold(onSuccess = { saved ->
                // Android recordMachineDelivery is called only after a
                // successful handoff. Here this means the user-selected
                // machine file was written, not physically received.
                val historyUpdate = runCatching {
                    withContext(Dispatchers.IO) {
                        sentHistory.recordExport(
                            saved.fileName.toString(),
                            prepared.format,
                            if (forMachineApp) "Wi-Fi / app" else "USB / OTG"
                        )
                    }
                }
                historyUpdate.onSuccess { sentMatrices = it }
                snackbar.showSnackbar(
                    "Arquivo ${prepared.format} salvo: ${saved.fileName}. " +
                        (if (forMachineApp)
                            "Abra-o no software da bordadeira; não houve envio Wi-Fi."
                        else
                            "Confirme o arquivo no pendrive e na máquina antes de bordar.") +
                        if (historyUpdate.isFailure)
                            " Histórico de enviados não atualizado."
                        else
                            " Operação registrada na aba Enviados."
                )
            }, onFailure = { error ->
                snackbar.showSnackbar(
                    "Arquivo não salvo: " + (error.message ?: "destino inválido")
                )
            })
        }
    }

    fun saveProject() {
        val design = activeDesign ?: return
        scope.launch {
            val chooser = JFileChooser().apply {
                dialogTitle = "Salvar projeto original Brother Matrizes FLP"
                selectedFile = if (startInSaveDialogQaFile != null)
                    startInSaveDialogQaFile.toFile()
                else File("brother-matrizes-projeto.flp")
                fileSelectionMode = JFileChooser.FILES_ONLY
                isAcceptAllFileFilterUsed = false
                fileFilter = FileNameExtensionFilter(
                    "Projetos Brother Matrizes (*.flp)", "flp"
                )
            }
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                runCatching {
                    val selected = chooser.selectedFile.toPath().toAbsolutePath().normalize()
                    if (startInSaveDialogQaFile != null) {
                        check(selected == startInSaveDialogQaFile) {
                            "Seleção do Salvar como diferente da cópia QA."
                        }
                    }
                    withContext(Dispatchers.IO) {
                        DesktopFlpProjectFiles.saveNew(design, selected)
                    }
                }.onSuccess { file ->
                    if (startInSaveDialogQaFile != null && !startInSaveDialogProtectQa) {
                        val reopened = DesktopFlpProjectFiles.open(file)
                        check(reopened.points == design.points &&
                            reopened.editableName?.options?.text == "Maria") {
                            "A gravação pelo Salvar como perdeu os dados FLP."
                        }
                        Files.writeString(
                            Path.of("build/qa/save-flp-created.txt"), "CREATE_NEW_FLPRT_OK",
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
                        )
                    }
                    registerOpenedOrSavedFlp(file)
                    snackbar.showSnackbar("Projeto FLP salvo: ${file.fileName}")
                }.onFailure { error ->
                    if (startInSaveDialogProtectQa &&
                        error is java.nio.file.FileAlreadyExistsException) {
                        val target = checkNotNull(startInSaveDialogQaFile)
                        check(Files.readAllBytes(target).contentEquals(
                            checkNotNull(startInSaveDialogOriginalBytes)
                        )) { "O arquivo original de QA foi alterado ao rejeitar sobrescrita." }
                        Files.writeString(
                            Path.of("build/qa/save-flp-protected.txt"), "OVERWRITE_REJECTED",
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
                        )
                    }
                    snackbar.showSnackbar("Projeto FLP não salvo: ${error.message}")
                }
            }
        }
    }

    fun openProject(destination: ActiveDetail = ActiveDetail.VIEWER) {
        scope.launch {
            val chooser = JFileChooser().apply {
                dialogTitle = "Abrir matriz DST, PES, JEF ou projeto FLP"
                fileSelectionMode = JFileChooser.FILES_ONLY
                isAcceptAllFileFilterUsed = false
                fileFilter = FileNameExtensionFilter(
                    "Matrizes e projetos Brother Matrizes (*.dst, *.pes, *.jef, *.flp)",
                    "dst", "pes", "jef", "flp"
                )
                if (startInChooserQaFile != null) {
                    currentDirectory = startInChooserQaFile.parent.toFile()
                    selectedFile = startInChooserQaFile.toFile()
                }
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                val selected = chooser.selectedFile.toPath().toAbsolutePath().normalize()
                runCatching {
                    if (startInChooserQaFile != null) {
                        check(selected == startInChooserQaFile) {
                            "O seletor abriu arquivo diferente da cópia isolada de QA."
                        }
                    }
                    withContext(Dispatchers.IO) {
                        DesktopEmbroideryFiles.open(selected)
                    }
                }.onSuccess { design ->
                    openDesign(design, displayMode, destination)
                    registerOpenedOrSavedFlp(selected)
                    if (startInChooserQaFile != null) {
                        val selectedExtension =
                            startInChooserQaFile.fileName.toString().substringAfterLast('.')
                                .lowercase()
                        val expectedDesignFormat =
                            if (selectedExtension == "flp") "DST"
                            else selectedExtension.uppercase()
                        check(design.format.equals(expectedDesignFormat, ignoreCase = true) &&
                            design.stitchCount > 0 && design.endFound) {
                            "A matriz selecionada não foi interpretada corretamente."
                        }
                        if (selectedExtension == "flp") {
                            check(design.editableName?.options?.text == "Maria") {
                                "O projeto FLP perdeu o nome original editável."
                            }
                        }
                        val marker = Path.of(
                            "build/qa/chooser-${selectedExtension}-opened.txt"
                        )
                        Files.writeString(
                            marker,
                            "ABERTO_NO_VIEWER ${selectedExtension.uppercase()} " +
                                "${design.format} ${design.stitchCount}",
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
                        )
                    }
                    snackbar.showSnackbar(
                        when (destination) {
                            ActiveDetail.TRANSFER -> "Matriz aberta em Enviar para a máquina."
                            ActiveDetail.EDITOR -> "Matriz aberta no Editor."
                            ActiveDetail.SIMULATOR -> "Matriz aberta no Simulador."
                            ActiveDetail.CONVERTER -> "Matriz aberta no Conversor."
                            else -> "Matriz aberta no visualizador."
                        }
                    )
                }.onFailure {
                    snackbar.showSnackbar("Não foi possível abrir a matriz: ${it.message}")
                }
            }
        }
    }

    // Windows Explorer / Open With passes a local document as argv[0].
    // No dialog is needed. Validate using the Android codecs off the UI thread.
    // A failed open never mutates source files or unlocks commercial features.
    LaunchedEffect(startInLaunchDocument) {
        if (startInLaunchDocument != null) {
            runCatching {
                withContext(Dispatchers.IO) {
                    DesktopEmbroideryFiles.open(startInLaunchDocument)
                }
            }.fold(onSuccess = { design ->
                openDesign(design, EmbroideryDisplayMode.SOLID, ActiveDetail.VIEWER)
                registerOpenedOrSavedFlp(startInLaunchDocument)
                snackbar.showSnackbar("Matriz aberta: ${startInLaunchDocument.fileName}")
            }, onFailure = {
                snackbar.showSnackbar("Não foi possível abrir a matriz: ${it.message}")
            })
        }
    }

    LaunchedEffect(startInChooserQaFile) {
        if (startInChooserQaFile != null) openProject()
    }
    LaunchedEffect(startInSaveDialogQaFile) {
        if (startInSaveDialogQaFile != null) saveProject()
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
                                    // First access must actually let the user
                                    // choose a folder, not display an empty
                                    // library with an unactionable snackbar.
                                    chooseLibraryFolder()
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
        // Home mantém a hierarquia/hero originais do Android. As ferramentas
        // de trabalho usam a largura de PC disponível sem redesenhar a UI:
        // o editor, visualizador, simulador e desenhos admitem até 1060dp,
        // enquanto Criar e Biblioteca admitem até 980dp. Em telas menores,
        // fillMaxWidth preserva a responsividade do Compose.
        val desktopMaxWidth = when {
            detail != ActiveDetail.NONE -> 1060.dp
            area == HomeArea.CREATE || area == HomeArea.LIBRARY -> 980.dp
            else -> 700.dp
        }
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
          Box(Modifier.widthIn(max = desktopMaxWidth).fillMaxWidth().fillMaxHeight()) {
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
                    onEdit = { detail = ActiveDetail.EDITOR },
                    onEditName = { detail = ActiveDetail.NAME_EDITOR },
                    onConvert = { detail = ActiveDetail.CONVERTER },
                    onTransfer = { detail = ActiveDetail.TRANSFER },
                    onSaveProject = { saveProject() },
                    onSaveCopy = { exportDesign(MatrixConverter.preferredBrotherFormat) },
                    onShare = {
                        exportDesign(
                            MatrixConverter.preferredBrotherFormat,
                            revealInExplorer = true
                        )
                    },
                    onSavePeDesign = { exportDesign("PES", peDesign = true) },
                    onSharePeDesign = {
                        exportDesign(
                            "PES", peDesign = true, revealInExplorer = true
                        )
                    },
                    onPositionChange = { activeDesign = it }
                )
            } else if (detail == ActiveDetail.NAME_EDITOR && activeDesign != null) {
                // Formulário oficial Android de personalização de matriz:
                // somente fontes EMBUTIDAS são funcionais nesta QA Windows.
                // Importação TTF/OTF permanece bloqueada: nenhuma autorização
                // nem cobrança de cota comercial é simulada.
                NameEditorScreen(
                    original = checkNotNull(activeDesign),
                    referenceHoop = hoop,
                    onBack = { detail = ActiveDetail.VIEWER },
                    onApply = { personalized ->
                        if (startInNameEditorApplyQa) {
                            val expectedName =
                                if (startInNameEditorKeyboardQa) "ana" else "Maria"
                            check(personalized.editableName?.options?.text == expectedName) {
                                "A edição QA não refletiu a digitação real do teclado."
                            }
                            val qaFile = Path.of(
                                when {
                                    startInNameEditorCtrlAQa ->
                                        "build/qa/name-editor-ctrl-a-synthetic.flp"
                                    startInNameEditorKeyboardQa ->
                                        "build/qa/name-editor-keyboard-synthetic.flp"
                                    else -> "build/qa/name-editor-apply-synthetic.flp"
                                }
                            )
                            Files.createDirectories(qaFile.parent)
                            // Escrita CREATE_NEW; jamais tocar em projetos reais.
                            DesktopFlpProjectFiles.saveNew(personalized, qaFile)
                            val loaded = DesktopFlpProjectFiles.open(qaFile)
                            check(loaded.points == personalized.points &&
                                loaded.editableName?.options?.text == expectedName) {
                                "O projeto FLP não sobreviveu ao round-trip."
                            }
                        }
                        activeDesign = personalized
                        hoop = personalized.hoopProfile ?: hoop
                        detail = ActiveDetail.VIEWER
                    }
                )
            } else if (detail == ActiveDetail.EDITOR && activeDesign != null) {
                // Composable EditorScreen original Android, controle de retorno
                // adaptado ao desktop. A edição é aplicada em memória; jamais
                // gravar arquivo ou alterar a fonte FLP automaticamente.
                EditorScreen(
                    design = checkNotNull(activeDesign),
                    onBack = { detail = ActiveDetail.VIEWER },
                    onApply = { edited ->
                        activeDesign = edited
                        hoop = edited.hoopProfile ?: HoopProfile.H100X100
                        detail = ActiveDetail.VIEWER
                    }
                )
            } else if (detail == ActiveDetail.ARCHIVE_PREVIEW) {
                DesktopArchivePreviewScreen(
                    preview = archivePreview,
                    busy = archiveBusy,
                    error = archiveError,
                    onChoosePackage = { analyzeArchiveOnly() },
                    onBack = {
                        archivePreview = null
                        archiveError = null
                        detail = ActiveDetail.NONE
                        area = HomeArea.LIBRARY
                    }
                )
            } else if (detail == ActiveDetail.DRAWING) {
                // Tela CreateDrawingScreen Compose original Android, com
                // gestos de mouse e seletor SVG local Windows. O desenho
                // continua usando VectorMatrixGenerator oficial.
                CreateDrawingScreen(
                    onBack = {
                        detail = ActiveDetail.NONE
                        area = HomeArea.HOME
                    },
                    onCreate = { design ->
                        openDesign(
                            design, EmbroideryDisplayMode.SOLID, ActiveDetail.VIEWER
                        )
                    },
                    onSimulate = { design ->
                        openDesign(
                            design, EmbroideryDisplayMode.SOLID, ActiveDetail.SIMULATOR
                        )
                    }
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
            } else if (detail == ActiveDetail.TRANSFER && activeDesign != null) {
                // Tela Compose oficial Android: mesmo formato, bastidor e
                // bloqueios de compatibilidade. Apenas os callbacks de
                // filesystem são substituídos por adaptação Windows.
                MachineTransferScreen(
                    design = checkNotNull(activeDesign),
                    onBack = { detail = ActiveDetail.VIEWER },
                    onUsbOtg = { format, selectedHoop ->
                        exportMachineFile(format, selectedHoop, false)
                    },
                    onWifi = { format, selectedHoop ->
                        // App da máquina é um destino manual de arquivo.
                        // Não declarar envio Wi-Fi nem conexão automática.
                        exportMachineFile(format, selectedHoop, true)
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
                    onShare = { format ->
                        exportDesign(format, revealInExplorer = true)
                    }
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
            } else if (detail == ActiveDetail.TELEGRAM) {
                DesktopTelegramMatrixSearchScreen(
                    projects = catalog?.projects.orEmpty(),
                    onBack = { detail = ActiveDetail.NONE },
                    // This QA path only opens a local file for inspection.
                    // It does NOT persist an imported library record or
                    // bypass the official import_matrix quota transaction.
                    onOpenLocalFile = {
                        detail = ActiveDetail.NONE
                        openProject()
                    },
                    onRequireGoogle = {
                        detail = ActiveDetail.NONE
                        area = HomeArea.ACCOUNT
                    },
                    onOpenSavedProject = { summary ->
                        val selected = catalog?.pathById?.get(summary.id)
                        if (selected == null) {
                            scope.launch {
                                snackbar.showSnackbar(
                                    "Projeto não encontrado na Biblioteca."
                                )
                            }
                        } else {
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        DesktopEmbroideryFiles.open(selected)
                                    }
                                }.fold(
                                    onSuccess = { design ->
                                        recentProjectIds = DesktopLibraryRecentTracker.putFirst(
                                            summary.id, recentProjectIds
                                        )
                                        openDesign(
                                            design, EmbroideryDisplayMode.SOLID, ActiveDetail.VIEWER
                                        )
                                    },
                                    onFailure = { error ->
                                        snackbar.showSnackbar(
                                            "FLP recusado: " +
                                                (error.message ?: "arquivo inválido")
                                        )
                                    }
                                )
                            }
                        }
                    },
                    access = telegramSearchAccess
                )
            } else if (detail == ActiveDetail.COMMUNITY) {
                // Android BrotherCommunityScreen: same group, updates,
                // support and feedback actions with Windows browser/mail.
                // Firebase Android push remains a documented parity gate.
                DesktopBrotherCommunityScreen(
                    onBack = { detail = ActiveDetail.NONE },
                    versionName = "Windows QA"
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
            } else if (area == HomeArea.ACCOUNT) {
                // Minha Conta de QA: login somente por opt-in explícito.
                // O preview do servidor não autoriza recursos comerciais.
                DesktopAccountQaScreen(
                    onBack = { area = HomeArea.HOME },
                    qaEnvironment = accountQaEnvironment
                )
            } else if (area == HomeArea.FONTS) {
                // Catálogo real de presets Android; importação TTF/OTF
                // permanece sem OAuth/cota comercial homologados.
                DesktopFontCatalogScreen(
                    onBack = { area = HomeArea.HOME },
                    onCreateName = { openCreate() },
                    onRequireGoogle = { area = HomeArea.ACCOUNT },
                    access = fontCatalogAccess
                )
            } else if (area == HomeArea.LIBRARY) {
                // UI, abas, cartões e metadados copiados do Android.
                // Apenas o catálogo de arquivos FLP local é integrado nesta QA.
                ProjectLibraryScreen(
                    projects = catalog?.projects.orEmpty(),
                    favoriteProjectIds = favorites,
                    recentProjectIds = recentProjectIds,
                    sentMatrices = sentMatrices,
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
                    onTransfer = { summary ->
                        val file = catalog?.pathById?.get(summary.id)
                        if (file == null) {
                            scope.launch {
                                snackbar.showSnackbar("Projeto não encontrado na Biblioteca.")
                            }
                        } else {
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        DesktopEmbroideryFiles.open(file)
                                    }
                                }.fold(onSuccess = { design ->
                                    recentProjectIds = DesktopLibraryRecentTracker.putFirst(
                                        summary.id, recentProjectIds
                                    )
                                    openDesign(
                                        design, EmbroideryDisplayMode.SOLID,
                                        ActiveDetail.TRANSFER
                                    )
                                }, onFailure = { error ->
                                    snackbar.showSnackbar(
                                        "Projeto não enviado: " +
                                            (error.message ?: "FLP inválido")
                                    )
                                })
                            }
                        }
                    },
                    onToggleFavorite = { summary ->
                        favorites = if (summary.id in favorites)
                            favorites - summary.id else favorites + summary.id
                    },
                    // Ação nativa de exclusão recuperável: exige confirmação
                    // explícita e envia o FLP validado à Lixeira do Windows.
                    // Nunca excluir permanentemente ou tocar no Android.
                    onDelete = { project ->
                        val library = catalog
                        if (library == null || project.id !in library.pathById) {
                            scope.launch {
                                snackbar.showSnackbar(
                                    "Projeto não localizado. Atualize a Biblioteca."
                                )
                            }
                        } else {
                            val answer = JOptionPane.showConfirmDialog(
                                null,
                                "Mover o projeto ${project.fileName} à Lixeira do Windows?\n" +
                                    "O arquivo não será apagado definitivamente. " +
                                    "Você poderá restaurá-lo pela Lixeira.",
                                "Remover projeto da Biblioteca",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.WARNING_MESSAGE
                            )
                            if (answer == JOptionPane.YES_OPTION) {
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            DesktopProjectRecycleBin.moveConfirmed(
                                                library.folder, project
                                            )
                                            DesktopProjectCatalog.scan(library.folder)
                                        }
                                    }.fold(onSuccess = { updated ->
                                        catalog = updated
                                        favorites = favorites - project.id
                                        recentProjectIds = recentProjectIds.filterNot {
                                            it == project.id
                                        }
                                        snackbar.showSnackbar(
                                            "Projeto enviado à Lixeira do Windows."
                                        )
                                    }, onFailure = { error ->
                                        snackbar.showSnackbar(
                                            "Projeto não removido: " +
                                                (error.message ?: "operação recusada")
                                        )
                                    })
                                }
                            }
                        }
                    },
                    onDeleteSent = { record ->
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    sentHistory.remove(record.id)
                                }
                            }.fold(
                                onSuccess = {
                                    sentMatrices = it
                                    snackbar.showSnackbar("Registro removido; arquivo de matriz preservado.")
                                },
                                onFailure = {
                                    snackbar.showSnackbar("Não foi possível remover o registro de envio.")
                                }
                            )
                        }
                    },
                    onClearSent = {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) { sentHistory.clear() }
                            }.fold(
                                onSuccess = {
                                    sentMatrices = it
                                    snackbar.showSnackbar("Histórico Enviados limpo; matrizes preservadas.")
                                },
                                onFailure = {
                                    snackbar.showSnackbar("Não foi possível limpar o histórico Enviados.")
                                }
                            )
                        }
                    },
                    onBackup = { backupLocalFlpProjects() },
                    onRestore = { restoreLocalFlpProjects() },
                    onFonts = { area = HomeArea.FONTS },
                    onSearchMatrices = { detail = ActiveDetail.TELEGRAM },
                    onRequireGoogle = { area = HomeArea.ACCOUNT }
                )
            } else if (area == HomeArea.HOME) {
                // Fonte original HomeScreen.kt do Android, não uma recriação.
                HomeScreen(
                    recent = activeDesign,
                    onCreateName = { openCreate() },
                    onFonts = { area = HomeArea.FONTS },
                    onTransfer = {
                        if (activeDesign != null) detail = ActiveDetail.TRANSFER
                        else openProject(ActiveDetail.TRANSFER)
                    },
                    onOpen = { openProject() },
                    onRecent = {
                        if (activeDesign != null) detail = ActiveDetail.VIEWER
                        else {
                            area = HomeArea.LIBRARY
                            detail = ActiveDetail.NONE
                            // If no local catalog has been selected yet, show
                            // the real Windows folder picker immediately.
                            if (catalog == null) chooseLibraryFolder()
                        }
                    },
                    onSimulate = {
                        if (activeDesign != null) detail = ActiveDetail.SIMULATOR
                        else openProject(ActiveDetail.SIMULATOR)
                    },
                    onEdit = {
                        if (activeDesign != null) detail = ActiveDetail.EDITOR
                        else openProject(ActiveDetail.EDITOR)
                    },
                    onConvert = {
                        if (activeDesign != null) detail = ActiveDetail.CONVERTER
                        else openProject(ActiveDetail.CONVERTER)
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
                    onCommunity = {
                        area = HomeArea.HOME
                        detail = ActiveDetail.COMMUNITY
                    }
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
