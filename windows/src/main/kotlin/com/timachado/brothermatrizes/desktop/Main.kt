package com.timachado.brothermatrizes.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.timachado.brothermatrizes.core.embroidery.EditTransform
import com.timachado.brothermatrizes.core.embroidery.MonogramStyle
import java.nio.file.Path
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.JLabel
import javax.swing.SwingConstants
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal enum class DesktopWorkspace(val label: String) {
    HOME("Início"), VIEWER("Visualizar"), NAME("Criar nome"),
    MONOGRAM("Monogramas"), FONT("Fontes"), EDIT("Editor"),
    LIBRARY("Biblioteca")
}

// Valores oficiais Fio* do tema Android 1.11.8, compartilhados por contrato de QA.
private val cyan: Color get() = DesktopBrandColors.gold
private val navy: Color get() = DesktopBrandColors.background
private val surface: Color get() = DesktopBrandColors.surface
private val muted: Color get() = DesktopBrandColors.muted
private val palette = listOf(
    Color(0xFF45D9FD), Color(0xFFF3BB60), Color(0xFFFC6C8A),
    Color(0xFF73D6AE), Color(0xFFC3AAFF), Color(0xFFE4E9F0)
)

/**
 * CLI restrita a diagnóstico local: usada exclusivamente para confirmar que
 * o aplicativo EMPACOTADO carrega seus módulos oficiais no runner Windows.
 * Não dispensa homologação visual/instalação física do software.
 */
fun main(args: Array<String>) {
    if (args.size == 2 && args[0] == "--smoke-test") {
        DesktopRuntimeSmoke.execute(java.nio.file.Path.of(args[1]))
        return
    }
    if (args.size == 2 && args[0] == "--export-qa-fixtures") {
        require(System.getenv("BROTHER_WINDOWS_QA_FIXTURES") == "1") {
            "Geração de amostras restrita ao ambiente de QA autorizado."
        }
        DesktopQaFixturePack.generateNew(java.nio.file.Path.of(args[1]))
        return
    }
    if (args.size == 2 &&
        args[0] in setOf("--ui-smoke-test", "--ui-synthetic-smoke-test", "--ui-wheel-smoke-test")) {
        require(System.getenv("BROTHER_WINDOWS_QA_UI") == "1") {
            "Captura da interface permitida somente em ambiente QA autorizado."
        }
        launchDesktopApp(
            java.nio.file.Path.of(args[1]),
            syntheticUi = args[0] != "--ui-smoke-test",
            wheelUi = args[0] == "--ui-wheel-smoke-test"
        )
        return
    }
    require(args.isEmpty()) { "Argumentos não reconhecidos." }
    launchDesktopApp()
}

private fun launchDesktopApp(
    uiReport: Path? = null,
    syntheticUi: Boolean = false,
    wheelUi: Boolean = false
) = application {
    // A janela de 1280x820dp ultrapassava a area util de desktops pequenos.
    // Limitar ao workspace exclui a barra de tarefas e preserva os controles.
    val workArea = remember {
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    }
    val initialWidth = (workArea.width - 48).coerceAtMost(1280).coerceAtLeast(640)
    val initialHeight = (workArea.height - 48).coerceAtMost(820).coerceAtLeast(500)
    Window(
        onCloseRequest = ::exitApplication,
        title = "Brother Matrizes Desktop — QA",
        state = rememberWindowState(width = initialWidth.dp, height = initialHeight.dp)
    ) {
        val syntheticDocument = remember(syntheticUi) {
            if (syntheticUi) DesktopMatrixService.createName("QA", 12f) else null
        }
        if (uiReport != null) {
            LaunchedEffect(uiReport, syntheticUi, wheelUi) {
                delay(2500)
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        if (wheelUi) {
                            DesktopUiSmoke.captureWheelZoom(window, uiReport)
                        } else {
                            DesktopUiSmoke.capture(window, uiReport, syntheticUi)
                        }
                    }
                }
                result.onFailure {
                    System.err.println("BROTHER_WINDOWS_UI_SMOKE_FAILED: ${it.message}")
                    kotlin.system.exitProcess(1)
                }
                exitApplication()
            }
        }
        var darkTheme by remember { mutableStateOf(true) }
        BrotherDesktopTheme(darkTheme = darkTheme) {
            DesktopScreen(
                initialDocument = syntheticDocument,
                darkTheme = darkTheme,
                onToggleTheme = { darkTheme = !darkTheme }
            )
        }
    }
}

@Composable
private fun DesktopScreen(
    initialDocument: DesktopMatrixDocument? = null,
    darkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {}
) {
    // A versão normal continua iniciando vazia. Somente o diagnóstico
    // autorizado injeta "QA", gerado localmente pelo motor oficial.
    var document by remember { mutableStateOf(initialDocument) }
    var workspace by remember { mutableStateOf(
        if (initialDocument == null) DesktopWorkspace.HOME else DesktopWorkspace.VIEWER
    ) }
    var editTimeline by remember { mutableStateOf<DesktopEditTimeline?>(null) }
    val preview = remember(document) { document?.let(DesktopMatrixService::preview) }
    var message by remember { mutableStateOf("Versão Windows de homologação: sem acesso à conta ou arquivos em nuvem.") }
    var isError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var scaleText by remember { mutableStateOf("1.00") }
    var horizontalScaleText by remember { mutableStateOf("1.00") }
    var verticalScaleText by remember { mutableStateOf("1.00") }
    var angleText by remember { mutableStateOf("0") }
    var xMmText by remember { mutableStateOf("0") }
    var yMmText by remember { mutableStateOf("0") }
    var mirrorX by remember { mutableStateOf(false) }
    var mirrorY by remember { mutableStateOf(false) }
    var threadColorSlotText by remember { mutableStateOf("1") }
    var threadColorHexText by remember { mutableStateOf("#E6BE70") }
    var format by remember { mutableStateOf("PES") }
    var peDesign by remember { mutableStateOf(false) }
    var nameText by remember { mutableStateOf("") }
    var nameHeightText by remember { mutableStateOf("12") }
    var satinDensityText by remember { mutableStateOf("0.40") }
    var glyphSpacingText by remember { mutableStateOf("0.00") }
    var nameStyle by remember { mutableStateOf(TextStitchStyle.RUNNING) }
    var fontPreview by remember { mutableStateOf<DesktopFontPreview?>(null) }
    var monogramInitials by remember { mutableStateOf("") }
    var monogramHeight by remember { mutableStateOf("22") }
    var monogramStyle by remember { mutableStateOf(MonogramStyle.CLASSIC) }
    var letterSession by remember { mutableStateOf<DesktopLetterSession?>(null) }
    var letterPreviewDocument by remember { mutableStateOf<DesktopMatrixDocument?>(null) }
    var selectedLetterIndex by remember { mutableStateOf(0) }
    var letterRegions by remember { mutableStateOf<List<DesktopLetterRegion>>(emptyList()) }
    // Computação de limites fora da thread da UI; seleção habilitada só após
    // confirmar correspondência EXATA com as pontadas do documento atual.
    LaunchedEffect(letterSession, letterPreviewDocument, document) {
        letterRegions = emptyList()
        val layout = letterSession?.current
        val active = letterPreviewDocument
        if (layout != null && active != null && document === active) {
            val measured = withContext(Dispatchers.Default) {
                runCatching { DesktopLetterComposer.renderDetailed(layout) }
            }.getOrNull()
            if (measured != null &&
                measured.document.current.points == active.current.points) {
                letterRegions = measured.regions
            }
        }
    }
    var letterMoveX by remember { mutableStateOf("0") }
    var letterMoveY by remember { mutableStateOf("0") }
    var letterScale by remember { mutableStateOf("1.00") }
    var letterRotation by remember { mutableStateOf("0") }
    val recentFiles = remember { DesktopRecentFiles() }
    var recentPaths by remember { mutableStateOf(recentFiles.list()) }
    val favoriteFiles = remember { DesktopFavorites() }
    var favoritePaths by remember { mutableStateOf(favoriteFiles.list()) }
    val librarySelection = remember { DesktopLibrarySelection() }
    var libraryFolder by remember { mutableStateOf(librarySelection.recall()?.toString()) }
    var libraryEntries by remember { mutableStateOf<List<LocalMatrixEntry>>(emptyList()) }
    var librarySearch by remember { mutableStateOf("") }
    var libraryFilter by remember { mutableStateOf("TODOS") }
    var libraryPage by remember { mutableStateOf(0) }
    var stitchProgress by remember { mutableStateOf(1f) }
    // Navegação da prévia é visual e reinicia ao abrir um projeto diferente.
    var previewZoom by remember(document?.original) { mutableStateOf(1f) }
    var previewPan by remember(document?.original) { mutableStateOf(Offset.Zero) }
    var moveWholeDesign by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val detailScroll = rememberScrollState()
    LaunchedEffect(workspace) { detailScroll.scrollTo(0) }

    fun report(text: String, error: Boolean = false) {
        message = text
        isError = error
    }

    fun recordRecent(path: Path) {
        // O histórico é opcional. A abertura local continua funcional se
        // a preferência do Windows estiver indisponível ou bloqueada.
        runCatching {
            recentFiles.record(path)
            recentPaths = recentFiles.list()
        }
    }

    fun toggleFavorite(path: Path) {
        runCatching { favoriteFiles.toggle(path) }
            .onSuccess { added ->
                favoritePaths = favoriteFiles.list()
                libraryPage = 0
                report(if (added) "Adicionado aos favoritos locais." else
                    "Removido dos favoritos locais.")
            }.onFailure {
                report("Favorito não atualizado: ${it.message}", true)
            }
    }

    fun openRecent(path: Path) {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (path.fileName.toString().endsWith(".flp", ignoreCase = true)) {
                        DesktopProjectService.open(path)
                    } else {
                        DesktopMatrixService.openFromFile(path)
                    }
                }
            }
            busy = false
            result.onSuccess { loaded ->
                document = loaded
                editTimeline = DesktopEditTimeline.start(loaded)
                letterSession = null
                letterPreviewDocument = null
                stitchProgress = 1f
                format = loaded.current.format
                peDesign = false
                scaleText = "1.00"
                horizontalScaleText = "1.00"
                verticalScaleText = "1.00"
                angleText = "0"
                xMmText = "0"
                yMmText = "0"
                mirrorX = false
                mirrorY = false
                threadColorSlotText = "1"
                threadColorHexText = "#E6BE70"
                recordRecent(path)
                workspace = DesktopWorkspace.VIEWER
                report("Arquivo recente aberto localmente; original preservado.")
            }.onFailure {
                report("Recente não pôde ser aberto: ${it.message}", true)
            }
        }
    }

    // Fluxo único de abertura, compartilhado pelo cabeçalho e pela Home Desktop.
    // Preserva o mesmo parser do Android e não modifica o arquivo selecionado.
    fun chooseMatrixFile() {
                    val chooser = JFileChooser().apply {
                        dialogTitle = "Abrir matriz de bordado (DST, PES ou JEF)"
                        fileFilter = FileNameExtensionFilter(
                            "Matrizes (*.dst, *.pes, *.jef)", "dst", "pes", "jef"
                        )
                        isAcceptAllFileFilterUsed = false
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        val file = chooser.selectedFile.toPath()
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { DesktopMatrixService.openFromFile(file) }
                            }
                            busy = false
                            result.onSuccess {
                                document = it
                                editTimeline = DesktopEditTimeline.start(it)
                                stitchProgress = 1f
                                format = it.current.format
                                scaleText = "1.00"
                                threadColorSlotText = "1"
                                threadColorHexText = "#E6BE70"
                                horizontalScaleText = "1.00"
                                verticalScaleText = "1.00"
                                angleText = "0"
                                xMmText = "0"
                                yMmText = "0"
                                mirrorX = false
                                mirrorY = false
                                peDesign = false
                                recordRecent(file)
                                letterSession = null
                                letterPreviewDocument = null
                                workspace = DesktopWorkspace.VIEWER
                                report("Matriz ${it.current.format} aberta localmente. O arquivo original não será modificado.")
                            }.onFailure {
                                report("Falha ao abrir: ${it.message ?: "arquivo inválido"}.", true)
                            }
                        }
                    }
    }

    // Atalhos do canvas reutilizam as mesmas ações dos botões: nenhum
    // atalho grava arquivos, altera o Android ou opera sobre TextFields.
    fun requestUndo() {
        val current = editTimeline ?: return
        if (busy || !current.canUndo) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching { current.undo() }
            }
            busy = false
            result.onSuccess { transition ->
                editTimeline = transition.timeline
                document = transition.document
                stitchProgress = 1f
                report("Último ajuste desfeito; arquivos no disco preservados.")
            }.onFailure { report("Não foi possível desfazer: ${it.message}", true) }
        }
    }

    fun requestRedo() {
        val current = editTimeline ?: return
        if (busy || !current.canRedo) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching { current.redo() }
            }
            busy = false
            result.onSuccess { transition ->
                editTimeline = transition.timeline
                document = transition.document
                stitchProgress = 1f
                report("Ajuste refeito pelo motor oficial; arquivo original intacto.")
            }.onFailure { report("Não foi possível refazer: ${it.message}", true) }
        }
    }

    // Arrastar no modo mover altera apenas o projeto atual em MEMÓRIA.
    // A distância em pixels é convertida para unidades de bordado via
    // a escala vigente, sem copiar pontos ou sobrescrever arquivos.
    fun completeMatrixDrag(delta: Offset, canvasSize: androidx.compose.ui.unit.IntSize) {
        fun rollbackVisual() {
            previewPan = Offset(
                previewPan.x - delta.x, previewPan.y - delta.y
            )
        }
        val loaded = document
        val beforePreview = preview
        if (busy || workspace != DesktopWorkspace.EDIT || loaded == null ||
            beforePreview == null || !moveWholeDesign ||
            (letterSession != null && loaded === letterPreviewDocument)) {
            rollbackVisual()
            return
        }
        val parsed = runCatching {
            DesktopEditorDrag.fromPixels(
                bounds = beforePreview.bounds,
                canvasWidth = canvasSize.width.toFloat(),
                canvasHeight = canvasSize.height.toFloat(),
                zoom = previewZoom,
                delta = delta
            )
        }
        parsed.onFailure { error ->
            rollbackVisual()
            report("Não foi possível mover: ${error.message}", true)
        }
        val position = parsed.getOrNull() ?: return
        if (position.xUnits == 0 && position.yUnits == 0) {
            rollbackVisual()
            return
        }
        val adjustment = EditTransform(
            offsetXUnits = position.xUnits,
            offsetYUnits = position.yUnits
        )
        val before = editTimeline ?: DesktopEditTimeline.start(loaded)
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val updated = DesktopMatrixService.edit(loaded, adjustment)
                    DesktopHistoryTransition(
                        timeline = before.record(DesktopEditAction.Transform(adjustment)),
                        document = updated
                    )
                }
            }
            busy = false
            result.onSuccess { transition ->
                document = transition.document
                editTimeline = transition.timeline
                stitchProgress = 1f
                report(
                    "Matriz movida: X ${position.xUnits / 10f} mm, " +
                        "Y ${position.yUnits / 10f} mm. " +
                        "Desfazer disponível; original preservado."
                )
            }.onFailure { error ->
                rollbackVisual()
                report("Movimento recusado pelo motor: ${error.message}", true)
            }
        }
    }

    fun editorShortcut(action: DesktopEditorShortcut) {
        if (busy || workspace != DesktopWorkspace.EDIT) return
        when (action) {
            DesktopEditorShortcut.UNDO -> requestUndo()
            DesktopEditorShortcut.REDO -> requestRedo()
            DesktopEditorShortcut.FIT -> {
                previewZoom = 1f
                previewPan = Offset.Zero
            }
            DesktopEditorShortcut.ZOOM_IN -> {
                previewZoom = (previewZoom * 1.25f)
                    .coerceAtMost(DesktopPreviewNavigation.MAX_ZOOM)
            }
            DesktopEditorShortcut.ZOOM_OUT -> {
                previewZoom = (previewZoom / 1.25f).coerceAtLeast(1f)
                if (previewZoom == 1f) previewPan = Offset.Zero
            }
        }
    }

    // Confirma o arrasto de uma letra através do gerador e das verificações
    // do motor oficial. O original em disco não é alterado; cada gesto gera
    // uma única revisão de parâmetros na sessão de letras.
    fun moveSingleLetter(
        index: Int, delta: Offset, dimensions: androidx.compose.ui.unit.IntSize
    ) {
        val session = letterSession ?: return
        val active = letterPreviewDocument ?: return
        val pv = preview ?: return
        if (busy || document !== active ||
            workspace !in setOf(DesktopWorkspace.NAME, DesktopWorkspace.EDIT) ||
            index !in session.current.editableIndices ||
            letterRegions.none { it.index == index }) return
        val changed = runCatching {
            DesktopLetterDrag.move(
                layout = session.current,
                index = index,
                bounds = pv.bounds,
                canvasWidth = dimensions.width.toFloat(),
                canvasHeight = dimensions.height.toFloat(),
                zoom = previewZoom, deltaPx = delta
            )
        }
        changed.onFailure {
            report("Movimento da letra recusado: ${it.message}", true)
        }
        val updatedLayout = changed.getOrNull() ?: return
        if (updatedLayout == session.current) return
        busy = true
        scope.launch {
            val rendered = withContext(Dispatchers.Default) {
                runCatching { DesktopLetterComposer.renderDetailed(updatedLayout) }
            }
            busy = false
            rendered.onSuccess { next ->
                // Não registrar o gesto se outra sessão substituiu a matriz.
                if (document !== active || letterSession !== session) return@onSuccess
                val nextSession = session.record(updatedLayout)
                letterSession = nextSession
                letterPreviewDocument = next.document
                document = next.document
                editTimeline = DesktopEditTimeline.start(next.document)
                letterRegions = next.regions
                selectedLetterIndex = index
                val item = updatedLayout.objects[index]
                letterMoveX = item.moveXmm.toString()
                letterMoveY = item.moveYmm.toString()
                letterScale = item.scale.toString()
                letterRotation = item.rotationDegrees.toString()
                stitchProgress = 1f
                report(
                    "Letra ${index + 1} movida para X=${item.moveXmm} mm, " +
                        "Y=${item.moveYmm} mm. Desfazer letra disponível."
                )
            }.onFailure {
                report("Edição da letra bloqueada: ${it.message}", true)
            }
        }
    }

    fun beginSingleLetterDrag(index: Int) {
        val layout = letterSession?.current ?: return
        if (busy || index !in layout.editableIndices) return
        selectedLetterIndex = index
        val item = layout.objects[index]
        letterMoveX = item.moveXmm.toString()
        letterMoveY = item.moveYmm.toString()
        letterScale = item.scale.toString()
        letterRotation = item.rotationDegrees.toString()
    }

    fun selectLetterInPreview(
        point: Offset, dimensions: androidx.compose.ui.unit.IntSize
    ) {
        val layout = letterSession?.current ?: return
        val activeDocument = letterPreviewDocument ?: return
        val currentPreview = preview ?: return
        if (busy || document !== activeDocument || letterRegions.isEmpty()) return
        val chosen = runCatching {
            DesktopLetterPicker.pick(
                designBounds = currentPreview.bounds,
                regions = letterRegions,
                width = dimensions.width.toFloat(),
                height = dimensions.height.toFloat(),
                zoom = previewZoom,
                pan = previewPan,
                cursor = point
            )
        }.getOrNull() ?: return
        if (chosen !in layout.editableIndices) return
        selectedLetterIndex = chosen
        val obj = layout.objects[chosen]
        letterMoveX = obj.moveXmm.toString()
        letterMoveY = obj.moveYmm.toString()
        letterScale = obj.scale.toString()
        letterRotation = obj.rotationDegrees.toString()
        report("Objeto ${chosen + 1} (${obj.character}) selecionado pelo mouse.")
    }

    Surface(modifier = Modifier.fillMaxSize(), color = navy) {
        Column(modifier = Modifier.fillMaxSize().padding(22.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Brother", fontSize = 26.sp,
                        color = DesktopBrandColors.gold, fontWeight = FontWeight.Bold)
                    Text("Matrizes", fontSize = 17.sp,
                        color = DesktopBrandColors.goldSoft, fontWeight = FontWeight.SemiBold)
                    Text("T.I. Machado • Windows • QA não comercial",
                        color = muted, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(
                        enabled = !busy,
                        onClick = onToggleTheme
                    ) {
                        Text(if (darkTheme) "Modo claro" else "Modo escuro",
                            fontSize = 11.sp)
                    }
                Button(enabled = !busy, onClick = { chooseMatrixFile() }) {
                    Text(if (busy) "Processando..." else "Abrir DST / PES / JEF")
                }
                }
            }

            Spacer(Modifier.height(10.dp))
            // Navegação ampla para desktop, baseada nas rotas reais do Android.
            // Não exibe como prontas as rotas ainda não portadas.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DesktopWorkspace.entries.forEach { target ->
                    TextButton(
                        enabled = !busy,
                        onClick = { workspace = target }
                    ) {
                        Text(
                            if (workspace == target) "● ${target.label}" else target.label,
                            color = if (workspace == target) cyan else muted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                color = if (isError) DesktopBrandColors.danger else muted,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .background(surface, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (workspace == DesktopWorkspace.HOME) {
                        DesktopHomeLanding(
                            enabled = !busy,
                            onCreateName = { workspace = DesktopWorkspace.NAME },
                            onOpenMatrix = { chooseMatrixFile() },
                            onFonts = { workspace = DesktopWorkspace.FONT },
                            onMonogram = { workspace = DesktopWorkspace.MONOGRAM },
                            onEditor = { workspace = DesktopWorkspace.EDIT },
                            onLibrary = { workspace = DesktopWorkspace.LIBRARY }
                        )
                    } else if (preview == null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Visualização técnica de bordado", fontSize = 21.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("Abra um arquivo DST, PES ou JEF do computador.", color = muted)
                            Text("Nenhuma matriz é enviada à nuvem.", color = muted, fontSize = 12.sp)
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val previewThreadColors = remember(document) {
                                document?.current?.let { current ->
                                    runCatching { DesktopThreadColorEditor.palette(current) }
                                        .getOrElse { current.threadColors }
                                }.orEmpty()
                            }
                            StitchPreview(
                                preview, stitchProgress, previewThreadColors,
                                zoom = previewZoom, pan = previewPan,
                                onPan = { delta ->
                                    previewPan = Offset(
                                        (previewPan.x + delta.x).coerceIn(-2000f, 2000f),
                                        (previewPan.y + delta.y).coerceIn(-2000f, 2000f)
                                    )
                                },
                                keyboardEnabled = workspace == DesktopWorkspace.EDIT,
                                onEditorShortcut = ::editorShortcut,
                                selectedLetterRegion = if (
                                    document === letterPreviewDocument &&
                                    workspace in setOf(DesktopWorkspace.NAME, DesktopWorkspace.EDIT)
                                ) letterRegions.firstOrNull { it.index == selectedLetterIndex }
                                    else null,
                                allowLetterSelection = !busy &&
                                    document === letterPreviewDocument &&
                                    workspace in setOf(DesktopWorkspace.NAME, DesktopWorkspace.EDIT) &&
                                    letterRegions.isNotEmpty(),
                                onLetterSelection = ::selectLetterInPreview,
                                allowLetterDrag = !busy &&
                                    document === letterPreviewDocument &&
                                    workspace in setOf(DesktopWorkspace.NAME, DesktopWorkspace.EDIT) &&
                                    letterRegions.isNotEmpty(),
                                letterRegions = letterRegions,
                                onLetterDragStarted = ::beginSingleLetterDrag,
                                onLetterDragCompleted = ::moveSingleLetter,
                                moveWholeDesign = workspace == DesktopWorkspace.EDIT &&
                                    moveWholeDesign && document != null && !busy &&
                                    (letterSession == null || document !== letterPreviewDocument),
                                onMoveCompleted = ::completeMatrixDrag,
                                onMoveCancelled = { delta ->
                                    previewPan = Offset(
                                        previewPan.x - delta.x,
                                        previewPan.y - delta.y
                                    )
                                },
                                onWheel = { wheel, cursor, canvasSize ->
                                    val updated = DesktopPreviewNavigation.zoomForWheel(
                                        previewZoom, wheel
                                    )
                                    if (updated != previewZoom) {
                                        previewPan = if (updated == 1f) {
                                            Offset.Zero
                                        } else {
                                            DesktopPreviewNavigation.panForZoomAt(
                                                previewZoom, updated, previewPan, cursor,
                                                canvasSize.width.toFloat(),
                                                canvasSize.height.toFloat()
                                            )
                                        }
                                        previewZoom = updated
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Prévia: ${"%.2f".format(Locale.forLanguageTag("pt-BR"), previewZoom)}x", color = muted, fontSize = 11.sp)
                                TextButton(
                                    enabled = previewZoom > 1f,
                                    onClick = {
                                        previewZoom = (previewZoom / 2f).coerceAtLeast(1f)
                                        if (previewZoom == 1f) previewPan = Offset.Zero
                                    }
                                ) { Text("Zoom −") }
                                TextButton(onClick = {
                                    previewZoom = 1f
                                    previewPan = Offset.Zero
                                }) { Text("Ajustar") }
                                TextButton(
                                    enabled = previewZoom < DesktopPreviewNavigation.MAX_ZOOM,
                                    onClick = {
                                        previewZoom = (previewZoom * 2f)
                                            .coerceAtMost(DesktopPreviewNavigation.MAX_ZOOM)
                                    }
                                ) { Text("Zoom +") }
                            }
                            // Manter uma linha de ajuda em 1x e em zoom ampliado
                            // evita redimensionar o Canvas após o gesto da roda.
                            Text(
                                if (document === letterPreviewDocument &&
                                    workspace in setOf(DesktopWorkspace.NAME, DesktopWorkspace.EDIT))
                                    "Clique para selecionar e arraste a letra para movê-la; ajuste escala e rotação em Criar nome."
                                else if (workspace == DesktopWorkspace.EDIT && moveWholeDesign &&
                                    (letterSession == null || document !== letterPreviewDocument))
                                    "Modo mover: arraste a matriz inteira para alterar sua posição (mm)."
                                else if (previewZoom > 1f)
                                    "Arraste para navegar pela prévia; a roda amplia no cursor."
                                else
                                    "Use a roda do mouse ou Zoom + para ampliar.",
                                fontSize = 11.sp, color = muted
                            )
                            Text(
                                "Sequência visual: ${(preview.points.size * stitchProgress).toInt()} de ${preview.points.size} comandos (não é tempo real de máquina)",
                                color = muted,
                                fontSize = 12.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { stitchProgress = 0f }) {
                                    Text("Início")
                                }
                                Slider(
                                    value = stitchProgress,
                                    onValueChange = { stitchProgress = it },
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    stitchProgress = (stitchProgress + 0.1f).coerceAtMost(1f)
                                }) { Text("+10%") }
                                TextButton(onClick = { stitchProgress = 1f }) {
                                    Text("Fim")
                                }
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier.width(330.dp).fillMaxHeight()
                        .background(surface, RoundedCornerShape(18.dp))
                        .verticalScroll(detailScroll)
                        .padding(17.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (workspace == DesktopWorkspace.HOME) {
                        Text("Início", color = cyan, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("Brother Matrizes para Windows — preparação da versão integral 1.11.8.",
                            color = muted, fontSize = 12.sp)
                        Text("Ferramentas principais e atalhos estão na área central.",
                            color = muted, fontSize = 12.sp)
                        Text("Conta, comunidade, desenhos, transferência e fluxos completos " +
                            "de simulação ainda exigem implementação específica no Windows.",
                            color = muted, fontSize = 11.sp)
                        Text("Favoritos neste computador", color = cyan,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        if (favoritePaths.isEmpty()) {
                            Text("Marque uma matriz como favorita na Biblioteca.",
                                color = muted, fontSize = 11.sp)
                        }
                        favoritePaths.take(8).forEach { favorite ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    enabled = !busy,
                                    onClick = { openRecent(favorite) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(favorite.fileName.toString(), maxLines = 1)
                                }
                                TextButton(
                                    enabled = !busy,
                                    onClick = { toggleFavorite(favorite) }
                                ) { Text("★", fontSize = 18.sp) }
                            }
                        }
                        if (favoritePaths.isNotEmpty()) {
                            TextButton(enabled = !busy, onClick = {
                                runCatching { favoriteFiles.clear() }
                                    .onSuccess {
                                        favoritePaths = emptyList()
                                        libraryPage = 0
                                        report("Favoritos locais apagados.")
                                    }
                                    .onFailure {
                                        report("Falha ao limpar favoritos: ${it.message}", true)
                                    }
                            }) { Text("Limpar favoritos") }
                        }
                        Text("Favoritos guardam apenas caminhos locais, sem alterar os arquivos.",
                            color = muted, fontSize = 11.sp)
                        Text("Recentes neste computador", color = cyan,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        if (recentPaths.isEmpty()) {
                            Text("Nenhum projeto aberto recentemente.",
                                color = muted, fontSize = 11.sp)
                        }
                        recentPaths.take(6).forEach { recent ->
                            TextButton(
                                enabled = !busy,
                                onClick = { openRecent(recent) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(recent.fileName.toString(), maxLines = 1)
                            }
                        }
                        if (recentPaths.isNotEmpty()) {
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    runCatching { recentFiles.clear() }
                                        .onSuccess {
                                            recentPaths = emptyList()
                                            report("Histórico local de recentes apagado.")
                                        }
                                        .onFailure { report(
                                            "Não foi possível apagar recentes: ${it.message}", true
                                        ) }
                                }
                            ) { Text("Limpar arquivos recentes") }
                        }
                        Text("Recentes guardam somente caminhos selecionados neste PC. " +
                            "Arquivos não são abertos automaticamente nem enviados à nuvem.",
                            color = muted, fontSize = 11.sp)
                        Text("Esta QA não substitui a versão Android comercial.",
                            color = muted, fontSize = 11.sp)
                    }
                    if (workspace == DesktopWorkspace.VIEWER) {
                    Text("Matriz atual", color = cyan, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Button(enabled = !busy, onClick = {
                        val chooser = JFileChooser().apply {
                            dialogTitle = "Abrir projeto editável Brother Matrizes (.flp)"
                            fileFilter = FileNameExtensionFilter("Projeto Brother Matrizes (*.flp)", "flp")
                            isAcceptAllFileFilterUsed = false
                        }
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            val chosen = chooser.selectedFile.toPath()
                            busy = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { DesktopProjectService.open(chosen) }
                                }
                                busy = false
                                result.onSuccess {
                                    document = it
                                    editTimeline = DesktopEditTimeline.start(it)
                                    format = "DST"
                                    peDesign = false
                                    stitchProgress = 1f
                                    scaleText = "1.00"
                                    threadColorSlotText = "1"
                                    threadColorHexText = "#E6BE70"
                                    horizontalScaleText = "1.00"
                                    verticalScaleText = "1.00"
                                    angleText = "0"
                                    xMmText = "0"
                                    yMmText = "0"
                                    mirrorX = false
                                    mirrorY = false
                                    recordRecent(chosen)
                                    report("Projeto FLP Android/Windows carregado. Pontos e propriedades de edição restaurados.")
                                }.onFailure { report("Projeto FLP inválido: ${it.message}", true) }
                            }
                        }
                    }) { Text("Abrir projeto editável .FLP") }
                    val doc = document
                    if (doc == null) {
                        Text("Nenhum arquivo aberto.", color = muted)
                    } else {
                        val d = doc.current
                        Detail("Arquivo", d.fileName)
                        Detail("Formato original", doc.original.format)
                        Detail("Estado", if (doc.edited) "Editada em memória" else "Original")
                        Detail("Pontos", "%,d".format(Locale.forLanguageTag("pt-BR"), d.stitchCount))
                        Detail("Saltos / trocas de cor", "${d.jumpCount} / ${d.colorChanges}")
                        Detail("Tamanho", "%.1f × %.1f mm".format(
                            Locale.forLanguageTag("pt-BR"),
                            d.bounds.widthMm, d.bounds.heightMm
                        ))
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Bastidores — pré-checagem",
                            color = cyan, fontWeight = FontWeight.SemiBold
                        )
                        val hoopAssessments = remember(doc) {
                            runCatching { DesktopHoopAdvisor.analyze(doc.current) }.getOrNull()
                        }
                        if (hoopAssessments == null) {
                            Text(
                                "Não foi possível calcular o enquadramento desta matriz.",
                                color = DesktopBrandColors.danger, fontSize = 11.sp
                            )
                        } else {
                            hoopAssessments.forEach { item ->
                                val status = when (item.status) {
                                    DesktopHoopFit.DIRECT -> "Dentro da margem"
                                    DesktopHoopFit.ALTERNATE_ORIENTATION -> "Verificar orientação 90°"
                                    DesktopHoopFit.EXCEEDS -> "Excede a margem"
                                }
                                val statusColor = when (item.status) {
                                    DesktopHoopFit.DIRECT -> cyan
                                    DesktopHoopFit.ALTERNATE_ORIENTATION -> Color(0xFFF3BB60)
                                    DesktopHoopFit.EXCEEDS -> muted
                                }
                                Text(
                                    "${item.hoop.displayName} · $status",
                                    color = statusColor, fontSize = 11.sp
                                )
                            }
                            Text(
                                "Usa margens internas oficiais. Orientação alternativa não garante " +
                                    "suporte da bordadeira; confirme bastidor e posição no equipamento.",
                                color = muted, fontSize = 10.sp
                            )
                        }
                    }
                    if (doc != null) {
                        Spacer(Modifier.height(7.dp))
                        Text("Cores de linha — edição reversível", color = cyan, fontWeight = FontWeight.SemiBold)
                        val availableColors = remember(doc) {
                            runCatching { DesktopThreadColorEditor.palette(doc.current) }.getOrNull()
                        }
                        if (availableColors == null) {
                            Text(
                                "A paleta desta matriz excede o limite seguro da QA (64 linhas).",
                                color = muted, fontSize = 11.sp
                            )
                        } else {
                            Text(
                                "${availableColors.size} linha(s). Alterar RGB não modifica pontadas, saltos ou densidade.",
                                color = muted, fontSize = 11.sp
                            )
                            availableColors.take(8).forEachIndexed { index, color ->
                                Text(
                                    "Linha ${index + 1}: ${DesktopThreadColorEditor.hex(color)}",
                                    color = Color(0xFF000000.toInt() or color), fontSize = 11.sp
                                )
                            }
                            if (availableColors.size > 8) {
                                Text("Mostrando 8 das ${availableColors.size} linhas; use o índice para editar outras.", color = muted, fontSize = 10.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = threadColorSlotText,
                                    onValueChange = { threadColorSlotText = it.take(3) },
                                    label = { Text("Linha (1–${availableColors.size})") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = threadColorHexText,
                                    onValueChange = { threadColorHexText = it.take(7) },
                                    label = { Text("RGB #RRGGBB") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(enabled = !busy, onClick = {
                                    val index = threadColorSlotText.trim().toIntOrNull()?.minus(1)
                                    if (index == null || index !in availableColors.indices) {
                                        report("Escolha uma linha de 1 a ${availableColors.size}.", true)
                                    } else {
                                        threadColorHexText = DesktopThreadColorEditor.hex(availableColors[index])
                                    }
                                }) { Text("Carregar cor atual", fontSize = 11.sp) }
                                Button(enabled = !busy, onClick = {
                                    val source = document ?: return@Button
                                    val index = threadColorSlotText.trim().toIntOrNull()?.minus(1)
                                    val parsed = runCatching { DesktopThreadColorEditor.parseHex(threadColorHexText) }
                                    if (index == null || index !in availableColors.indices) {
                                        report("Escolha uma linha de 1 a ${availableColors.size}.", true)
                                    } else if (parsed.isFailure) {
                                        report(parsed.exceptionOrNull()?.message ?: "Cor hexadecimal inválida.", true)
                                    } else {
                                        val rgb = parsed.getOrThrow()
                                        val before = editTimeline ?: DesktopEditTimeline.start(source)
                                        busy = true
                                        scope.launch {
                                            val result = withContext(Dispatchers.Default) {
                                                runCatching {
                                                    val changed = DesktopThreadColorEditor.recolor(source, index, rgb)
                                                    val advanced = if (changed == source) before else
                                                        before.record(DesktopEditAction.RecolorThread(index, rgb))
                                                    DesktopHistoryTransition(advanced, changed)
                                                }
                                            }
                                            busy = false
                                            result.onSuccess { transition ->
                                                document = transition.document
                                                editTimeline = transition.timeline
                                                stitchProgress = 1f
                                                report("Cor atualizada em memória. Desfazer/Refazer disponível; arquivo original intacto.")
                                            }.onFailure { report("Edição de cor recusada: ${it.message}", true) }
                                        }
                                    }
                                }) { Text("Aplicar RGB", fontSize = 11.sp) }
                            }
                            Text(
                                "PES/JEF podem guardar cores; DST não é um formato de paleta completa. Valide a saída na máquina.",
                                color = muted, fontSize = 10.sp
                            )
                        }
                    }
                    }
                    if (workspace == DesktopWorkspace.FONT) {
                    Spacer(Modifier.height(7.dp))
                    Text("Fonte externa TTF/OTF — Satin experimental", color = cyan, fontWeight = FontWeight.SemiBold)
                    Button(enabled = !busy, onClick = {
                        val chooser = JFileChooser().apply {
                            dialogTitle = "Selecionar fonte TTF/OTF para pré-visualização"
                            fileFilter = FileNameExtensionFilter("Fontes TTF/OTF (*.ttf, *.otf)", "ttf", "otf")
                            isAcceptAllFileFilterUsed = false
                        }
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            val selected = chooser.selectedFile.toPath()
                            busy = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { DesktopFontPreviewService.open(selected) }
                                }
                                busy = false
                                result.onSuccess {
                                    fontPreview = it
                                    report("Fonte reconhecida: ${it.family}. Para bordar, digite o nome abaixo e escolha Gerar Satin QA.")
                                }.onFailure {
                                    fontPreview = null
                                    report("Fonte não carregada: ${it.message}", true)
                                }
                            }
                        }
                    }) { Text("Carregar fonte TTF/OTF para prévia") }
                    fontPreview?.let { chosen ->
                        val sample = nameText.trim().ifBlank { "Brother Matrizes" }
                        val absent = sample.filter { !chosen.awtFont.canDisplay(it) }.toSet().joinToString("")
                        Text("Fonte: ${chosen.family} (${chosen.fileName})", color = muted, fontSize = 11.sp)
                        SwingPanel(
                            factory = {
                                JLabel("Brother Matrizes", SwingConstants.CENTER).apply {
                                    font = chosen.awtFont.deriveFont(22f)
                                    isOpaque = true
                                    background = java.awt.Color(244, 249, 253)
                                    foreground = java.awt.Color(15, 32, 50)
                                }
                            },
                            update = {
                                it.font = chosen.awtFont.deriveFont(22f)
                                it.text = sample
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        )
                        if (absent.isNotEmpty()) {
                            Text(
                                "Glifos ausentes na amostra: $absent",
                                fontSize = 11.sp, color = DesktopBrandColors.danger
                            )
                        }
                    }
                    Text(
                        "Contornos Java2D + motor Satin oficial. Geração ainda experimental: não usar em produção ou máquinas de clientes.",
                        color = muted, fontSize = 11.sp
                    )
                    }
                    if (workspace == DesktopWorkspace.NAME) {
                    Spacer(Modifier.height(8.dp))
                    Text("Criar novo nome — motor oficial", color = cyan, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it.take(24) },
                        label = { Text("Nome (até 24 caracteres)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = nameHeightText,
                        onValueChange = { nameHeightText = it },
                        label = { Text("Altura do nome (4 a 60 mm)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = satinDensityText,
                            onValueChange = { satinDensityText = it },
                            label = { Text("Densidade Satin (0,3–1,2 mm)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = glyphSpacingText,
                            onValueChange = { glyphSpacingText = it },
                            label = { Text("Entreletras (0–3 mm)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        enabled = !busy && fontPreview != null && nameText.isNotBlank(),
                        onClick = {
                            val file = fontPreview?.sourcePath ?: return@Button
                            val cleanName = nameText.trim()
                            val height = nameHeightText.trim().replace(',', '.').toFloatOrNull()
                            val density = satinDensityText.trim().replace(',', '.').toFloatOrNull()
                            val spacing = glyphSpacingText.trim().replace(',', '.').toFloatOrNull()
                            if (height == null || !height.isFinite() || height !in 4f..60f) {
                                report("Informe altura entre 4 e 60 mm para fonte externa.", true)
                            } else if (density == null || !density.isFinite() || density !in 0.3f..1.2f) {
                                report("Densidade Satin inválida: 0,3 a 1,2 mm.", true)
                            } else if (spacing == null || !spacing.isFinite() || spacing !in 0f..3f) {
                                report("Entreletras inválido: 0 a 3 mm.", true)
                            } else {
                                busy = true
                                scope.launch {
                                    val result = withContext(Dispatchers.Default) {
                                        runCatching {
                                            DesktopMatrixService.createFromImportedFont(
                                                file, cleanName, height, density, spacing
                                            )
                                        }
                                    }
                                    busy = false
                                    result.onSuccess {
                                        document = it
                                        editTimeline = DesktopEditTimeline.start(it)
                                        format = "DST"
                                        stitchProgress = 1f
                                        peDesign = false
                                        scaleText = "1.00"
                                        threadColorSlotText = "1"
                                        threadColorHexText = "#E6BE70"
                                        horizontalScaleText = "1.00"
                                        verticalScaleText = "1.00"
                                        angleText = "0"
                                        xMmText = "0"
                                        yMmText = "0"
                                        mirrorX = false
                                        mirrorY = false
                                        report("Matriz Satin TTF/OTF gerada e verificada. QA apenas; teste físico ainda pendente.")
                                    }.onFailure {
                                        report("Satin bloqueado por qualidade/integridade: ${it.message}", true)
                                    }
                                }
                            }
                        }
                    ) { Text(if (busy) "Verificando..." else "Gerar bordado Satin com fonte importada (QA)") }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (choice in listOf(TextStitchStyle.RUNNING, TextStitchStyle.SATIN)) {
                            TextButton(enabled = !busy, onClick = { nameStyle = choice }) {
                                Text(
                                    (if (nameStyle == choice) "● " else "") +
                                        if (choice == TextStitchStyle.RUNNING) "Ponto corrido" else "Satin"
                                )
                            }
                        }
                    }
                    Button(enabled = !busy && nameText.isNotBlank(), onClick = {
                        val clean = nameText
                        val height = nameHeightText.trim().replace(',', '.').toFloatOrNull()
                        val style = nameStyle
                        if (height == null || !height.isFinite() || height !in 4f..60f) {
                            report("Altura inválida: informe de 4 a 60 mm.", true)
                        } else {
                            busy = true
                            scope.launch {
                                val result = withContext(Dispatchers.Default) {
                                    runCatching { DesktopMatrixService.createName(clean, height, style) }
                                }
                                busy = false
                                result.onSuccess {
                                    document = it
                                    editTimeline = DesktopEditTimeline.start(it)
                                    format = "DST"
                                    peDesign = false
                                    scaleText = "1.00"
                                    threadColorSlotText = "1"
                                    threadColorHexText = "#E6BE70"
                                    horizontalScaleText = "1.00"
                                    verticalScaleText = "1.00"
                                    angleText = "0"
                                    xMmText = "0"
                                    yMmText = "0"
                                    mirrorX = false
                                    mirrorY = false
                                    stitchProgress = 1f
                                    report("Novo nome gerado. A matriz aberta anteriormente não foi alterada.")
                                }.onFailure { report("Nome não gerado: ${it.message}", true) }
                            }
                        }
                    }) {
                        Text("Criar nome como nova matriz")
                    }
                    Text(
                        "Nomes integrados e fontes importadas estão disponíveis. Para ajustes " +
                            "de letras separadas use a composição individual abaixo.",
                        color = muted, fontSize = 11.sp
                    )

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Objetos individuais — letras novas (QA)",
                        color = cyan, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Crie até 10 letras pelo motor Android oficial. Mova, escale e gire " +
                            "cada uma separadamente. Não separa objetos de DST/PES/JEF existentes.",
                        color = muted, fontSize = 11.sp
                    )
                    Button(enabled = !busy && nameText.isNotBlank(), onClick = {
                        val height = nameHeightText.trim().replace(',', '.').toFloatOrNull()
                        val created = runCatching {
                            DesktopLetterSession.start(
                                DesktopLetterComposer.start(
                                    nameText, height ?: Float.NaN, nameStyle
                                )
                            )
                        }
                        created.onFailure {
                            report("Composição não iniciada: ${it.message}", true)
                        }
                        created.onSuccess { session ->
                            busy = true
                            scope.launch {
                                val result = withContext(Dispatchers.Default) {
                                    runCatching { DesktopLetterComposer.render(session.current) }
                                }
                                busy = false
                                result.onSuccess { rendered ->
                                    letterSession = session
                                    letterPreviewDocument = rendered
                                    document = rendered
                                    editTimeline = DesktopEditTimeline.start(rendered)
                                    selectedLetterIndex = session.current.editableIndices.first()
                                    letterMoveX = "0"
                                    letterMoveY = "0"
                                    letterScale = "1.00"
                                    letterRotation = "0"
                                    format = "DST"
                                    peDesign = false
                                    stitchProgress = 1f
                                    report(
                                        "Composição por letras iniciada. Selecione uma letra e " +
                                            "ajuste individualmente. Nada foi gravado em disco."
                                    )
                                }.onFailure {
                                    report("Composição recusada: ${it.message}", true)
                                }
                            }
                        }
                    }) { Text("Criar composição por letras") }
                    Button(enabled = !busy, onClick = {
                        val chooser = JFileChooser().apply {
                            dialogTitle = "Abrir FLP com camadas de letras (.bmlayers)"
                            fileFilter = FileNameExtensionFilter(
                                "Projeto Brother Matrizes com camadas (*.flp)", "flp"
                            )
                            isAcceptAllFileFilterUsed = false
                        }
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            val file = chooser.selectedFile.toPath().toAbsolutePath()
                            busy = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { DesktopLetterProjectBundle.open(file) }
                                }
                                busy = false
                                result.onSuccess { project ->
                                    document = project.document
                                    editTimeline = DesktopEditTimeline.start(project.document)
                                    letterSession = project.session
                                    letterPreviewDocument = project.document
                                    selectedLetterIndex =
                                        project.session.current.editableIndices.first()
                                    val letter = project.session.current.objects[selectedLetterIndex]
                                    letterMoveX = letter.moveXmm.toString()
                                    letterMoveY = letter.moveYmm.toString()
                                    letterScale = letter.scale.toString()
                                    letterRotation = letter.rotationDegrees.toString()
                                    format = "DST"
                                    peDesign = false
                                    stitchProgress = 1f
                                    scaleText = "1.00"
                                    horizontalScaleText = "1.00"
                                    verticalScaleText = "1.00"
                                    angleText = "0"
                                    xMmText = "0"
                                    yMmText = "0"
                                    mirrorX = false
                                    mirrorY = false
                                    threadColorSlotText = "1"
                                    threadColorHexText = "#E6BE70"
                                    report(
                                        "FLP e camadas recuperados. Letras reeditáveis nesta sessão. " +
                                            "O FLP original não foi alterado."
                                    )
                                }.onFailure {
                                    report(
                                        "Não foi possível recuperar camadas: ${it.message}. " +
                                            "O FLP comum continua acessível pela abertura normal.",
                                        true
                                    )
                                }
                            }
                        }
                    }) { Text("Abrir FLP + camadas editáveis") }
                    val activeLetters = if (document === letterPreviewDocument) {
                        letterSession
                    } else null
                    activeLetters?.let { session ->
                        Text(
                            "Selecione uma letra para editar:",
                            color = muted, fontSize = 11.sp
                        )
                        session.current.editableIndices.forEach { index ->
                            val item = session.current.objects[index]
                            TextButton(enabled = !busy, onClick = {
                                selectedLetterIndex = index
                                letterMoveX = item.moveXmm.toString()
                                letterMoveY = item.moveYmm.toString()
                                letterScale = item.scale.toString()
                                letterRotation = item.rotationDegrees.toString()
                            }) {
                                Text(
                                    (if (index == selectedLetterIndex) "● " else "") +
                                        "Objeto ${index + 1}: ${item.character}",
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            OutlinedTextField(
                                value = letterMoveX,
                                onValueChange = { letterMoveX = it },
                                label = { Text("Mover X (mm)") },
                                singleLine = true, modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = letterMoveY,
                                onValueChange = { letterMoveY = it },
                                label = { Text("Mover Y (mm)") },
                                singleLine = true, modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            OutlinedTextField(
                                value = letterScale,
                                onValueChange = { letterScale = it },
                                label = { Text("Escala 0,5–2") },
                                singleLine = true, modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = letterRotation,
                                onValueChange = { letterRotation = it },
                                label = { Text("Girar ±180°") },
                                singleLine = true, modifier = Modifier.weight(1f)
                            )
                        }
                        Button(enabled = !busy, onClick = {
                            fun parse(text: String): Float =
                                text.trim().replace(',', '.').toFloatOrNull() ?: Float.NaN
                            val modified = runCatching {
                                DesktopLetterComposer.update(
                                    session.current, selectedLetterIndex,
                                    parse(letterMoveX), parse(letterMoveY),
                                    parse(letterScale), parse(letterRotation)
                                )
                            }
                            modified.onFailure {
                                report("Parâmetros da letra inválidos: ${it.message}", true)
                            }
                            modified.onSuccess { next ->
                                if (next == session.current) {
                                    report("Esta letra já possui os parâmetros informados.")
                                } else {
                                    busy = true
                                    scope.launch {
                                        val result = withContext(Dispatchers.Default) {
                                            runCatching { DesktopLetterComposer.render(next) }
                                        }
                                        busy = false
                                        result.onSuccess { rendered ->
                                            letterSession = session.record(next)
                                            letterPreviewDocument = rendered
                                            document = rendered
                                            editTimeline = DesktopEditTimeline.start(rendered)
                                            stitchProgress = 1f
                                            report(
                                                "Letra ${selectedLetterIndex + 1} ajustada. " +
                                                    "Desfazer/Refazer letra disponíveis."
                                            )
                                        }.onFailure {
                                            report("Edição individual bloqueada: ${it.message}", true)
                                        }
                                    }
                                }
                            }
                        }) { Text("Aplicar à letra selecionada") }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            TextButton(enabled = !busy && session.canUndo, onClick = {
                                val previous = session.undo()
                                busy = true
                                scope.launch {
                                    val result = withContext(Dispatchers.Default) {
                                        runCatching { DesktopLetterComposer.render(previous.current) }
                                    }
                                    busy = false
                                    result.onSuccess { rendered ->
                                        letterSession = previous
                                        letterPreviewDocument = rendered
                                        document = rendered
                                        editTimeline = DesktopEditTimeline.start(rendered)
                                        stitchProgress = 1f
                                        val item = previous.current.objects[selectedLetterIndex]
                                        letterMoveX = item.moveXmm.toString()
                                        letterMoveY = item.moveYmm.toString()
                                        letterScale = item.scale.toString()
                                        letterRotation = item.rotationDegrees.toString()
                                        report("Ajuste individual da letra desfeito.")
                                    }.onFailure { report("Desfazer letra: ${it.message}", true) }
                                }
                            }) { Text("Desfazer letra", fontSize = 11.sp) }
                            TextButton(enabled = !busy && session.canRedo, onClick = {
                                val next = session.redo()
                                busy = true
                                scope.launch {
                                    val result = withContext(Dispatchers.Default) {
                                        runCatching { DesktopLetterComposer.render(next.current) }
                                    }
                                    busy = false
                                    result.onSuccess { rendered ->
                                        letterSession = next
                                        letterPreviewDocument = rendered
                                        document = rendered
                                        editTimeline = DesktopEditTimeline.start(rendered)
                                        stitchProgress = 1f
                                        val item = next.current.objects[selectedLetterIndex]
                                        letterMoveX = item.moveXmm.toString()
                                        letterMoveY = item.moveYmm.toString()
                                        letterScale = item.scale.toString()
                                        letterRotation = item.rotationDegrees.toString()
                                        report("Ajuste individual da letra refeito.")
                                    }.onFailure { report("Refazer letra: ${it.message}", true) }
                                }
                            }) { Text("Refazer letra", fontSize = 11.sp) }
                        }
                        Button(enabled = !busy, onClick = {
                            val activeDocument = document ?: return@Button
                            val currentLayout = session.current
                            val chooser = JFileChooser().apply {
                                dialogTitle =
                                    "Salvar novo projeto FLP + arquivo auxiliar de camadas"
                                selectedFile = java.io.File("letras_windows_qa.flp")
                                fileFilter = FileNameExtensionFilter(
                                    "Projeto Brother Matrizes (*.flp)", "flp"
                                )
                                isAcceptAllFileFilterUsed = false
                            }
                            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                                val requested = chooser.selectedFile.toPath().toAbsolutePath()
                                val output = if (
                                    requested.fileName.toString().endsWith(
                                        ".flp", ignoreCase = true
                                    )
                                ) requested else requested.resolveSibling(
                                    requested.fileName.toString() + ".flp"
                                )
                                busy = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            DesktopLetterProjectBundle.saveNew(
                                                currentLayout, activeDocument, output
                                            )
                                        }
                                    }
                                    busy = false
                                    result.onSuccess { pair ->
                                        report(
                                            "FLP oficial salvo: ${pair.first.fileName}; " +
                                                "camadas: ${pair.second.fileName}. " +
                                                "Mantenha os dois arquivos juntos para reeditar."
                                        )
                                    }.onFailure {
                                        report(
                                            "Não foi possível salvar camadas: ${it.message}. " +
                                                "Nenhum arquivo preexistente foi substituído.",
                                            true
                                        )
                                    }
                                }
                            }
                        }) { Text("Salvar novo FLP + camadas") }
                        Text(
                            "O FLP oficial permanece compatível com Android. " +
                                "Para recuperar letras individuais, mantenha o arquivo " +
                                ".bmlayers junto do FLP. Arquivos importados não são separados. " +
                                "Desfazer/Refazer continua restrito à sessão.",
                            color = muted, fontSize = 10.sp
                        )
                    }

                    }
                    if (workspace == DesktopWorkspace.MONOGRAM) {
                    Spacer(Modifier.height(8.dp))
                    Text("Monogramas — motor oficial", color = cyan, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = monogramInitials,
                        onValueChange = { monogramInitials = it.take(3) },
                        label = { Text("1 a 3 iniciais") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = monogramHeight,
                        onValueChange = { monogramHeight = it },
                        label = { Text("Altura (8 a 50 mm)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (style in MonogramStyle.entries) {
                            TextButton(enabled = !busy, onClick = { monogramStyle = style }) {
                                Text(
                                    (if (monogramStyle == style) "● " else "") + style.displayName,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    Button(
                        enabled = !busy && monogramInitials.isNotBlank(),
                        onClick = {
                            val initials = monogramInitials
                            val height = monogramHeight.trim().replace(',', '.').toFloatOrNull()
                            val style = monogramStyle
                            if (height == null || !height.isFinite() || height !in 8f..50f) {
                                report("Monograma: altura deve estar entre 8 e 50 mm.", true)
                            } else {
                                busy = true
                                scope.launch {
                                    val result = withContext(Dispatchers.Default) {
                                        runCatching {
                                            DesktopMatrixService.createMonogram(initials, style, height)
                                        }
                                    }
                                    busy = false
                                    result.onSuccess {
                                        document = it
                                        editTimeline = DesktopEditTimeline.start(it)
                                        format = "DST"
                                        peDesign = false
                                        scaleText = "1.00"
                                        threadColorSlotText = "1"
                                        threadColorHexText = "#E6BE70"
                                        horizontalScaleText = "1.00"
                                        verticalScaleText = "1.00"
                                        angleText = "0"
                                        xMmText = "0"
                                        yMmText = "0"
                                        mirrorX = false
                                        mirrorY = false
                                        stitchProgress = 1f
                                        report("Monograma criado como matriz nova. Original anterior preservado.")
                                    }.onFailure { report("Monograma não gerado: ${it.message}", true) }
                                }
                            }
                        }
                    ) { Text("Criar monograma") }
                    Text(
                        "Estilos Linear, Clássico e Empilhado. Criação local experimental.",
                        color = muted, fontSize = 11.sp
                    )

                    }
                    if (workspace == DesktopWorkspace.EDIT) {
                    Spacer(Modifier.height(8.dp))
                    Text("Ajustes geométricos — QA", color = cyan, fontWeight = FontWeight.SemiBold)
                    if (document === letterPreviewDocument && letterSession != null) {
                        val active = letterSession?.current
                        val obj = active?.objects?.getOrNull(selectedLetterIndex)
                        if (obj != null) {
                            Text(
                                "Letra selecionada: ${selectedLetterIndex + 1} — ${obj.character}",
                                color = DesktopBrandColors.goldSoft, fontSize = 12.sp
                            )
                            TextButton(enabled = !busy, onClick = {
                                workspace = DesktopWorkspace.NAME
                            }) { Text("Editar propriedades da letra") }
                        }
                    }
                    val editableLetterDocument =
                        letterSession != null && document === letterPreviewDocument
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = moveWholeDesign && !editableLetterDocument,
                            onCheckedChange = { moveWholeDesign = it },
                            enabled = document != null && !busy && !editableLetterDocument
                        )
                        Text("Mover matriz inteira arrastando no canvas", fontSize = 11.sp)
                    }
                    Text(
                        if (editableLetterDocument)
                            "Para preservar as camadas, mova as letras pelos controles individuais " +
                                "em Criar nome. Movimento da matriz inteira indisponível neste modo."
                        else
                            "Com esta opção ligada, arrastar edita a posição em milímetros " +
                                "e entra no histórico Desfazer/Refazer. " +
                                "Desligada, a prévia pode ser deslocada apenas visualmente com zoom.",
                        fontSize = 11.sp, color = muted
                    )
                    Text(
                        "Clique na prévia para ativar atalhos: Ctrl+Z desfazer, " +
                            "Ctrl+Y / Ctrl+Shift+Z refazer, Ctrl+0 ajustar, " +
                            "Ctrl++ ampliar e Ctrl+- reduzir.",
                        fontSize = 11.sp, color = muted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedTextField(
                            value = scaleText,
                            onValueChange = { scaleText = it },
                            label = { Text("Escala 1.0") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = angleText,
                            onValueChange = { angleText = it },
                            label = { Text("Graus") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedTextField(
                            value = horizontalScaleText,
                            onValueChange = { horizontalScaleText = it },
                            label = { Text("Expansão H (0,5–2,5)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = verticalScaleText,
                            onValueChange = { verticalScaleText = it },
                            label = { Text("Expansão V (0,5–2,5)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        "1,00 = sem expansão. Aplicar não sobrescreve a matriz original.",
                        fontSize = 11.sp, color = muted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedTextField(
                            value = xMmText,
                            onValueChange = { xMmText = it },
                            label = { Text("Mover X (mm)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = yMmText,
                            onValueChange = { yMmText = it },
                            label = { Text("Mover Y (mm)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = mirrorX, onCheckedChange = { mirrorX = it })
                        Text("Espelhar H", fontSize = 12.sp)
                        Checkbox(checked = mirrorY, onCheckedChange = { mirrorY = it })
                        Text("Espelhar V", fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Button(enabled = document != null && !busy, onClick = {
                            val loaded = document ?: return@Button
                            val parsed = runCatching {
                                DesktopTransformInput.parse(
                                    scale = scaleText,
                                    horizontal = horizontalScaleText,
                                    vertical = verticalScaleText,
                                    angle = angleText,
                                    moveX = xMmText,
                                    moveY = yMmText,
                                    mirrorHorizontal = mirrorX,
                                    mirrorVertical = mirrorY
                                )
                            }
                            parsed.onFailure { report("Ajuste inválido: ${it.message}", true) }
                            val transform = parsed.getOrNull()
                            if (transform != null) {
                                val before = editTimeline ?: DesktopEditTimeline.start(loaded)
                                busy = true
                                scope.launch {
                                    val result = withContext(Dispatchers.Default) {
                                        runCatching {
                                            val changed = DesktopMatrixService.edit(loaded, transform)
                                            val advanced = before.record(DesktopEditAction.Transform(transform))
                                            DesktopHistoryTransition(advanced, changed)
                                        }
                                    }
                                    busy = false
                                    result.onSuccess { transition ->
                                        editTimeline = transition.timeline
                                        document = transition.document
                                        stitchProgress = 1f
                                        report("Ajuste aplicado. Desfazer e refazer estão disponíveis nesta sessão.")
                                    }.onFailure {
                                        report("Ajuste recusado: ${it.message}", true)
                                    }
                                }
                            }
                        }) { Text("Aplicar") }
                        TextButton(enabled = document != null && !busy, onClick = {
                            val loaded = document ?: return@TextButton
                            if (loaded.current != loaded.original) {
                                val before = editTimeline ?: DesktopEditTimeline.start(loaded)
                                busy = true
                                scope.launch {
                                    val result = withContext(Dispatchers.Default) {
                                        runCatching {
                                            val restored = DesktopMatrixService.reset(loaded)
                                            val advanced = before.record(DesktopEditAction.RestoreOriginal)
                                            DesktopHistoryTransition(advanced, restored)
                                        }
                                    }
                                    busy = false
                                    result.onSuccess { transition ->
                                        editTimeline = transition.timeline
                                        document = transition.document
                                        stitchProgress = 1f
                                        report("Original restaurado. A operação pode ser desfeita nesta sessão.")
                                    }.onFailure { report("Restauração recusada: ${it.message}", true) }
                                }
                            } else {
                                report("A matriz já está no estado original.")
                            }
                        }) { Text("Restaurar original") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            enabled = !busy && editTimeline?.canUndo == true,
                            onClick = { requestUndo() }
                        ) { Text("Desfazer") }
                        TextButton(
                            enabled = !busy && editTimeline?.canRedo == true,
                            onClick = { requestRedo() }
                        ) { Text("Refazer") }
                        Text(
                            "${editTimeline?.position ?: 0}/${editTimeline?.total ?: 0} · até 8 ações",
                            fontSize = 10.sp, color = muted
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Text("Projeto editável Android / Windows", color = cyan, fontWeight = FontWeight.SemiBold)
                    Button(enabled = document != null && !busy, onClick = {
                        val loaded = document ?: return@Button
                        val stem = loaded.current.fileName.substringBeforeLast('.')
                            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                            .take(42).ifBlank { "matriz" }
                        val chooser = JFileChooser().apply {
                            dialogTitle = "Salvar projeto nativo FLP como novo arquivo"
                            selectedFile = java.io.File("${stem}_windows_qa.flp")
                            fileFilter = FileNameExtensionFilter("Projeto Brother Matrizes (*.flp)", "flp")
                            isAcceptAllFileFilterUsed = false
                        }
                        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                            val requested = chooser.selectedFile.toPath().toAbsolutePath()
                            val path = if (requested.fileName.toString().endsWith(".flp", ignoreCase = true))
                                requested else requested.resolveSibling(requested.fileName.toString() + ".flp")
                            busy = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { DesktopProjectService.saveNew(loaded, path) }
                                }
                                busy = false
                                result.onSuccess {
                                    report("Projeto .FLP salvo sem substituir nenhum arquivo existente: $it")
                                }.onFailure {
                                    report("Erro ao salvar FLP: ${it.message}. Original preservado.", true)
                                }
                            }
                        }
                    }) { Text("Salvar projeto editável .FLP") }
                    Text("Formato nativo do Android. Nesta QA o projeto salvo contém o estado atual, não um histórico de desfazer.", color = muted, fontSize = 11.sp)

                    Spacer(Modifier.height(6.dp))
                    Text("Salvar cópia convertida", color = cyan, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (choice in listOf("PES", "DST", "JEF")) {
                            TextButton(enabled = !busy, onClick = {
                                format = choice
                                if (choice != "PES") peDesign = false
                            }) {
                                Text(if (choice == format) "● $choice" else choice)
                            }
                        }
                    }
                    if (format == "PES") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = peDesign, onCheckedChange = { peDesign = it },
                                enabled = !busy)
                            Text("Perfil PE-DESIGN PC (experimental)", fontSize = 12.sp)
                        }
                    }
                    Button(enabled = document != null && !busy, onClick = {
                        val loaded = document ?: return@Button
                        val stem = loaded.original.fileName.substringBeforeLast('.')
                            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                            .take(48).ifBlank { "matriz" }
                        val chooser = JFileChooser().apply {
                            dialogTitle = "Salvar nova cópia (não substitui arquivos existentes)"
                            selectedFile = java.io.File(
                                "${stem}_windows_qa.${format.lowercase(Locale.ROOT)}"
                            )
                            fileFilter = FileNameExtensionFilter(
                                "Matriz $format (*.${format.lowercase(Locale.ROOT)})",
                                format.lowercase(Locale.ROOT)
                            )
                            isAcceptAllFileFilterUsed = false
                        }
                        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                            val suffix = ".${format.lowercase(Locale.ROOT)}"
                            val requested = chooser.selectedFile.toPath().toAbsolutePath()
                            val file = if (requested.fileName.toString().lowercase(Locale.ROOT).endsWith(suffix))
                                requested else requested.resolveSibling(requested.fileName.toString() + suffix)
                            val selected = format
                            val pcProfile = peDesign
                            busy = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        DesktopMatrixService.saveNew(loaded, selected, file, pcProfile)
                                    }
                                }
                                busy = false
                                result.onSuccess {
                                    report("Cópia de QA salva: $it. Original preservado.")
                                }.onFailure {
                                    report("Não foi possível salvar: ${it.message}. Nunca substituímos arquivo existente.", true)
                                }
                            }
                        }
                    }) { Text(if (busy) "Processando..." else "Salvar como novo arquivo") }
                    Text(
                        "Cópias exportadas passam por releitura. A compatibilidade real com PE-DESIGN 11 ainda não foi homologada. Não usar em produção.",
                        color = muted, fontSize = 12.sp
                    )

                    }
                    if (workspace == DesktopWorkspace.LIBRARY) {
                    Spacer(Modifier.height(12.dp))

                    Text("Backup e recuperação local (QA)", color = cyan, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Salva uma cópia nova no formato FLP Android em pasta escolhida. " +
                            "Não envia dados à nuvem e não substitui arquivos existentes.",
                        color = muted, fontSize = 11.sp
                    )
                    Button(enabled = !busy && document != null, onClick = {
                        val snapshot = document ?: return@Button
                        val editableLayers = if (document === letterPreviewDocument) {
                            letterSession?.current
                        } else null
                        val chooser = JFileChooser().apply {
                            dialogTitle = "Escolher pasta para novo backup FLP"
                            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                            isAcceptAllFileFilterUsed = false
                        }
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            val folder = chooser.selectedFile.toPath().toAbsolutePath().normalize()
                            busy = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        DesktopBackupService.create(snapshot, folder, editableLayers)
                                    }
                                }
                                busy = false
                                result.onSuccess { saved ->
                                    report("Backup local criado: ${saved.fileName}. " +
                                        "Preserve também o .bmlayers, quando houver.")
                                }.onFailure {
                                    report("Backup não criado: ${it.message}", true)
                                }
                            }
                        }
                    }) { Text("Criar backup do projeto atual (FLP)") }
                    Button(enabled = !busy, onClick = {
                        val chooser = JFileChooser().apply {
                            dialogTitle = "Escolher pasta de backups Brother Matrizes"
                            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                            isAcceptAllFileFilterUsed = false
                        }
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            val folder = chooser.selectedFile.toPath().toAbsolutePath().normalize()
                            busy = true
                            scope.launch {
                                val listed = withContext(Dispatchers.IO) {
                                    runCatching { DesktopBackupService.list(folder) }
                                }
                                val options = listed.getOrElse {
                                    busy = false
                                    report("Não foi possível listar backups: ${it.message}", true)
                                    return@launch
                                }
                                if (options.isEmpty()) {
                                    busy = false
                                    report("Nenhum backup Brother Matrizes FLP nesta pasta.")
                                    return@launch
                                }
                                val chosen = JOptionPane.showInputDialog(
                                    null,
                                    "Selecione a cópia FLP a recuperar (arquivo original intacto).",
                                    "Brother Matrizes — restaurar backup",
                                    JOptionPane.PLAIN_MESSAGE,
                                    null,
                                    options.map { it.fileName.toString() }.toTypedArray(),
                                    options.first().fileName.toString()
                                ) as? String
                                if (chosen == null) {
                                    busy = false
                                    report("Recuperação cancelada. Nenhum arquivo alterado.")
                                    return@launch
                                }
                                val target = options.firstOrNull { it.fileName.toString() == chosen }
                                if (target == null) {
                                    busy = false
                                    report("Backup não corresponde à seleção.", true)
                                    return@launch
                                }
                                if (document != null) {
                                    val confirmation = JOptionPane.showConfirmDialog(
                                        null,
                                        "Recuperar o backup substituirá o projeto aberto nesta sessão. " +
                                            "Salve eventuais alterações não gravadas antes de continuar. " +
                                            "Deseja prosseguir?",
                                        "Brother Matrizes — confirmar recuperação",
                                        JOptionPane.YES_NO_OPTION,
                                        JOptionPane.WARNING_MESSAGE
                                    )
                                    if (confirmation != JOptionPane.YES_OPTION) {
                                        busy = false
                                        report("Recuperação cancelada. Projeto atual mantido.")
                                        return@launch
                                    }
                                }
                                val restored = withContext(Dispatchers.IO) {
                                    runCatching { DesktopBackupService.restore(target) }
                                }
                                busy = false
                                restored.onSuccess { backup ->
                                    document = backup.document
                                    editTimeline = DesktopEditTimeline.start(backup.document)
                                    letterSession = backup.letterSession
                                    letterPreviewDocument = if (backup.letterSession != null) {
                                        backup.document
                                    } else null
                                    if (backup.letterSession != null) {
                                        val index = backup.letterSession.current.editableIndices.first()
                                        selectedLetterIndex = index
                                        val letter = backup.letterSession.current.objects[index]
                                        letterMoveX = letter.moveXmm.toString()
                                        letterMoveY = letter.moveYmm.toString()
                                        letterScale = letter.scale.toString()
                                        letterRotation = letter.rotationDegrees.toString()
                                    }
                                    format = backup.document.current.format
                                    peDesign = false
                                    stitchProgress = 1f
                                    scaleText = "1.00"
                                    horizontalScaleText = "1.00"
                                    verticalScaleText = "1.00"
                                    angleText = "0"
                                    xMmText = "0"
                                    yMmText = "0"
                                    mirrorX = false
                                    mirrorY = false
                                    threadColorSlotText = "1"
                                    threadColorHexText = "#E6BE70"
                                    workspace = DesktopWorkspace.VIEWER
                                    report("Cópia FLP restaurada em memória. " +
                                        "Os arquivos originais em disco não foram modificados.")
                                }.onFailure {
                                    report("Recuperação recusada: ${it.message}", true)
                                }
                            }
                        }
                    }) { Text("Recuperar backup da pasta escolhida") }
                    Spacer(Modifier.height(8.dp))
                    Text("Biblioteca local (somente leitura)", color = cyan, fontWeight = FontWeight.SemiBold)
                    Button(enabled = !busy, onClick = {
                        val chooser = JFileChooser().apply {
                            dialogTitle = "Importar matriz de pacote ZIP (QA)"
                            fileFilter = FileNameExtensionFilter("Pacote ZIP (*.zip)", "zip")
                            isAcceptAllFileFilterUsed = false
                        }
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            val zipPath = chooser.selectedFile.toPath()
                            busy = true
                            scope.launch {
                                val inventory = withContext(Dispatchers.IO) {
                                    runCatching { DesktopArchiveImporter.inspect(zipPath) }
                                }
                                val entries = inventory.getOrElse { error ->
                                    busy = false
                                    report("ZIP recusado: ${error.message}", true)
                                    return@launch
                                }
                                if (entries.isEmpty()) {
                                    busy = false
                                    report("Nenhuma matriz DST, PES ou JEF válida no ZIP.", true)
                                    return@launch
                                }
                                val choice = JOptionPane.showInputDialog(
                                    null,
                                    "Selecione a matriz a abrir; o ZIP não será modificado.",
                                    "Brother Matrizes — importar ZIP",
                                    JOptionPane.PLAIN_MESSAGE,
                                    null,
                                    entries.toTypedArray(),
                                    entries.first()
                                ) as? DesktopArchiveMatrix
                                if (choice == null) {
                                    busy = false
                                    report("Importação cancelada. Nenhum arquivo alterado.")
                                    return@launch
                                }
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { DesktopArchiveImporter.openMatrix(zipPath, choice) }
                                }
                                busy = false
                                result.onSuccess { loaded ->
                                    document = loaded
                                    editTimeline = DesktopEditTimeline.start(loaded)
                                    letterSession = null
                                    letterPreviewDocument = null
                                    stitchProgress = 1f
                                    format = loaded.current.format
                                    peDesign = false
                                    scaleText = "1.00"
                                    horizontalScaleText = "1.00"
                                    verticalScaleText = "1.00"
                                    threadColorSlotText = "1"
                                    threadColorHexText = "#E6BE70"
                                    angleText = "0"
                                    xMmText = "0"
                                    yMmText = "0"
                                    mirrorX = false
                                    mirrorY = false
                                    report("Matriz ${loaded.current.format} importada do ZIP em memória. Pacote preservado.")
                                }.onFailure { error ->
                                    report("Não foi possível abrir matriz do ZIP: ${error.message}", true)
                                }
                            }
                        }
                    }) { Text("Importar matriz de ZIP (sem extrair pastas)") }
                    Button(enabled = !busy, onClick = {
                        val chooser = JFileChooser().apply {
                            dialogTitle = "Selecionar pasta de matrizes no computador"
                            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                            isAcceptAllFileFilterUsed = false
                        }
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            val folder = chooser.selectedFile.toPath()
                            busy = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { DesktopLocalLibrary.list(folder) }
                                }
                                busy = false
                                result.onSuccess {
                                    libraryFolder = folder.toAbsolutePath().normalize().toString()
                                    libraryEntries = it
                                    librarySearch = ""
                                    libraryFilter = "TODOS"
                                    libraryPage = 0
                                    val persisted = runCatching { librarySelection.remember(folder) }.isSuccess
                                    report(
                                        "Biblioteca local: ${it.size} matriz(es). " +
                                        if (persisted) "Pasta lembrada neste computador." else
                                            "Pasta listada; não foi possível guardar a preferência."
                                    )
                                }.onFailure { report("Erro ao listar pasta: ${it.message}", true) }
                            }
                        }
                    }) { Text("Selecionar pasta do computador") }
                    libraryFolder?.let { path ->
                        Text("Pasta: $path", color = muted, fontSize = 11.sp)
                        Text("${libraryEntries.size} matriz(es) nesta sessão", color = muted, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(enabled = !busy, onClick = {
                                busy = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching { DesktopLocalLibrary.list(Path.of(path)) }
                                    }
                                    busy = false
                                    result.onSuccess {
                                        libraryEntries = it
                                        libraryPage = 0
                                        report("Pasta atualizada: ${it.size} matriz(es).")
                                    }.onFailure { report("Erro ao atualizar pasta: ${it.message}", true) }
                                }
                            }) { Text("Atualizar pasta") }
                            TextButton(enabled = !busy, onClick = {
                                val cleared = runCatching { librarySelection.forget() }
                                if (cleared.isSuccess) {
                                    libraryFolder = null
                                    libraryEntries = emptyList()
                                    librarySearch = ""
                                    libraryFilter = "TODOS"
                                    libraryPage = 0
                                    report("Preferência local da pasta apagada.")
                                } else {
                                    report("Não foi possível limpar a preferência: ${cleared.exceptionOrNull()?.message}", true)
                                }
                            }) { Text("Esquecer pasta") }
                        }
                    }
                    val libraryView = remember(
                        libraryEntries, librarySearch, libraryFilter, libraryPage, favoritePaths
                    ) {
                        DesktopLibraryBrowser.page(
                            libraryEntries, librarySearch, libraryFilter, libraryPage,
                            favoritePaths.toSet()
                        )
                    }
                    if (libraryFolder != null) {
                        OutlinedTextField(
                            value = librarySearch,
                            onValueChange = { librarySearch = it.take(80); libraryPage = 0 },
                            label = { Text("Buscar matriz pelo nome") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Filtrar formato", color = muted, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            for (type in listOf("TODOS", "DST", "PES")) {
                                TextButton(
                                    enabled = !busy,
                                    onClick = { libraryFilter = type; libraryPage = 0 }
                                ) { Text(if (libraryFilter == type) "✓ $type" else type, fontSize = 11.sp) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            for (type in listOf("JEF", "FLP", "FAVORITOS")) {
                                TextButton(
                                    enabled = !busy,
                                    onClick = { libraryFilter = type; libraryPage = 0 }
                                ) { Text(if (libraryFilter == type) "✓ $type" else type, fontSize = 11.sp) }
                            }
                        }
                        Text(
                            "${libraryView.matching} resultado(s)" +
                                if (libraryView.totalPages > 0)
                                    " · página ${libraryView.currentPage + 1}/${libraryView.totalPages}"
                                else "",
                            color = muted, fontSize = 11.sp
                        )
                    }
                    for (entry in libraryView.items) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                val file = entry.path
                                busy = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            if (entry.format == "FLP") DesktopProjectService.open(file)
                                            else DesktopMatrixService.openFromFile(file)
                                        }
                                    }
                                    busy = false
                                    result.onSuccess {
                                        document = it
                                        editTimeline = DesktopEditTimeline.start(it)
                                        format = it.current.format
                                        peDesign = false
                                        stitchProgress = 1f
                                        scaleText = "1.00"
                                        threadColorSlotText = "1"
                                        threadColorHexText = "#E6BE70"
                                        horizontalScaleText = "1.00"
                                        verticalScaleText = "1.00"
                                        angleText = "0"
                                        xMmText = "0"
                                        yMmText = "0"
                                        mirrorX = false
                                        mirrorY = false
                                        recordRecent(file)
                                        report("Matriz ou projeto da biblioteca aberto localmente, sem modificar o arquivo de origem.")
                                    }.onFailure { report("Falha ao abrir da biblioteca: ${it.message}", true) }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("${entry.name}  ·  ${entry.format}", maxLines = 1)
                        }
                        TextButton(
                            enabled = !busy,
                            onClick = { toggleFavorite(entry.path) }
                        ) {
                            Text(
                                if (entry.path in favoritePaths) "★" else "☆",
                                fontSize = 18.sp
                            )
                        }
                        }
                    }
                    if (libraryFolder != null && libraryView.totalPages > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                enabled = !busy && libraryView.currentPage > 0,
                                onClick = { libraryPage = libraryView.currentPage - 1 }
                            ) { Text("Anterior") }
                            TextButton(
                                enabled = !busy &&
                                    libraryView.currentPage + 1 < libraryView.totalPages,
                                onClick = { libraryPage = libraryView.currentPage + 1 }
                            ) { Text("Próxima") }
                        }
                    }
                    } // Fim da seção Biblioteca.
                }
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Column {
        Text(label, color = muted, fontSize = 11.sp)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
    }
}

@Composable
private fun StitchPreview(
    preview: DstPreview, progress: Float, threadColors: List<Int>,
    zoom: Float, pan: Offset, onPan: (Offset) -> Unit,
    onWheel: (Float, Offset, androidx.compose.ui.unit.IntSize) -> Unit,
    keyboardEnabled: Boolean,
    onEditorShortcut: (DesktopEditorShortcut) -> Unit,
    selectedLetterRegion: DesktopLetterRegion?,
    allowLetterSelection: Boolean,
    onLetterSelection: (Offset, androidx.compose.ui.unit.IntSize) -> Unit,
    allowLetterDrag: Boolean,
    letterRegions: List<DesktopLetterRegion>,
    onLetterDragStarted: (Int) -> Unit,
    onLetterDragCompleted: (Int, Offset, androidx.compose.ui.unit.IntSize) -> Unit,
    moveWholeDesign: Boolean,
    onMoveCompleted: (Offset, androidx.compose.ui.unit.IntSize) -> Unit,
    onMoveCancelled: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnPan by rememberUpdatedState(onPan)
    val latestOnWheel by rememberUpdatedState(onWheel)
    val latestOnEditorShortcut by rememberUpdatedState(onEditorShortcut)
    val latestOnLetterSelection by rememberUpdatedState(onLetterSelection)
    val latestLetterRegions by rememberUpdatedState(letterRegions)
    val latestOnLetterDragStarted by rememberUpdatedState(onLetterDragStarted)
    val latestOnLetterDragCompleted by rememberUpdatedState(onLetterDragCompleted)
    val latestPreviewPan by rememberUpdatedState(pan)
    var draggingLetterIndex by remember { mutableStateOf<Int?>(null) }
    var draggingLetterOffset by remember { mutableStateOf(Offset.Zero) }
    val latestOnMoveCompleted by rememberUpdatedState(onMoveCompleted)
    val latestOnMoveCancelled by rememberUpdatedState(onMoveCancelled)
    val focusRequester = remember { FocusRequester() }
    val dragModifier = if (allowLetterDrag) {
        Modifier.pointerInput(allowLetterDrag, preview, zoom) {
            var total = Offset.Zero
            var target: Int? = null
            detectDragGestures(
                onDragStart = { start ->
                    total = Offset.Zero
                    target = runCatching {
                        DesktopLetterPicker.pick(
                            designBounds = preview.bounds,
                            regions = latestLetterRegions,
                            width = size.width.toFloat(),
                            height = size.height.toFloat(),
                            zoom = zoom,
                            pan = latestPreviewPan,
                            cursor = start
                        )
                    }.getOrNull()
                    draggingLetterIndex = target
                    draggingLetterOffset = Offset.Zero
                    target?.let { latestOnLetterDragStarted(it) }
                },
                onDragEnd = {
                    val selected = target
                    val completed = total
                    target = null
                    draggingLetterIndex = null
                    draggingLetterOffset = Offset.Zero
                    total = Offset.Zero
                    if (selected != null && completed != Offset.Zero) {
                        latestOnLetterDragCompleted(selected, completed, size)
                    }
                },
                onDragCancel = {
                    target = null
                    total = Offset.Zero
                    draggingLetterIndex = null
                    draggingLetterOffset = Offset.Zero
                }
            ) { change, dragAmount ->
                change.consume()
                total += dragAmount
                if (target != null) {
                    // A moldura acompanha o gesto; as pontadas são
                    // recalculadas somente ao soltar o mouse.
                    draggingLetterOffset = total
                } else if (zoom > 1f) {
                    latestOnPan(dragAmount)
                }
            }
        }
    } else if (moveWholeDesign) {
        Modifier.pointerInput(moveWholeDesign, zoom) {
            var accumulated = Offset.Zero
            detectDragGestures(
                onDragStart = { accumulated = Offset.Zero },
                onDragEnd = {
                    val completed = accumulated
                    accumulated = Offset.Zero
                    if (completed != Offset.Zero) {
                        latestOnMoveCompleted(completed, size)
                    }
                },
                onDragCancel = {
                    latestOnMoveCancelled(accumulated)
                    accumulated = Offset.Zero
                }
            ) { change, dragAmount ->
                change.consume()
                accumulated += dragAmount
                latestOnPan(dragAmount)
            }
        }
    } else if (zoom > 1f) {
        Modifier.pointerInput(zoom) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                latestOnPan(dragAmount)
            }
        }
    } else {
        Modifier
    }
    val wheelModifier = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Scroll) {
                    val change = event.changes.firstOrNull()
                    if (change != null && !change.isConsumed) {
                        val wheelY = change.scrollDelta.y
                        if (wheelY.isFinite() && wheelY != 0f) {
                            latestOnWheel(wheelY, change.position, size)
                            change.consume()
                        }
                    }
                }
            }
        }
    }
    // Somente o canvas no módulo Editor recebe os atalhos. TextFields,
    // menus e demais controles preservam sua própria edição de teclado.
    // O smoke da roda, executado no módulo Visualizar, permanece independente.
    val keyboardModifier = if (keyboardEnabled) {
        Modifier
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    val shortcut = DesktopEditorKeymap.resolve(
                        key = event.key,
                        control = event.isCtrlPressed,
                        shift = event.isShiftPressed,
                        alt = event.isAltPressed
                    )
                    if (shortcut != null) {
                        latestOnEditorShortcut(shortcut)
                        true
                    } else false
                }
            }
            .focusable()
    } else Modifier
    val tapModifier = if (keyboardEnabled || allowLetterSelection) {
        Modifier.pointerInput(keyboardEnabled, allowLetterSelection) {
            detectTapGestures(onTap = { cursor ->
                if (keyboardEnabled) focusRequester.requestFocus()
                if (allowLetterSelection) {
                    latestOnLetterSelection(cursor, size)
                }
            })
        }
    } else Modifier
    Canvas(
        modifier = modifier.fillMaxSize().clipToBounds()
            .padding(18.dp).then(dragModifier).then(wheelModifier)
            .then(keyboardModifier).then(tapModifier)
    ) {
        renderPreview(
            preview, progress, threadColors, zoom, pan,
            selectedLetterRegion,
            if (draggingLetterIndex == selectedLetterRegion?.index)
                draggingLetterOffset else Offset.Zero
        )
    }
}

private fun DrawScope.renderPreview(
    design: DstPreview, progress: Float, threadColors: List<Int>,
    zoom: Float, pan: Offset, selectedRegion: DesktopLetterRegion?,
    dragOutlineOffset: Offset
) {
    if (size.width <= 0f || size.height <= 0f) return
    val viewport = DesktopPreviewNavigation.viewport(
        design.bounds, size.width, size.height, zoom, pan
    )

    val stride = maxOf(1, (design.points.size + 199_999) / 200_000)
    var previousX = 0
    var previousY = 0
    val visibleCount = (design.points.size * progress.coerceIn(0f, 1f)).toInt()
    for (index in 0 until visibleCount) {
        val p = design.points[index]
        if (p.command == PreviewCommand.STITCH && index % stride == 0) {
            drawLine(
                color = threadColors.getOrNull(p.colorIndex)?.let {
                    Color(0xFF000000.toInt() or (it and 0xFFFFFF))
                } ?: palette[Math.floorMod(p.colorIndex, palette.size)],
                start = viewport.screenPoint(previousX, previousY),
                end = viewport.screenPoint(p.x, p.y),
                strokeWidth = 1.3f
            )
        }
        previousX = p.x
        previousY = p.y
    }
    if (selectedRegion != null) {
        val box = selectedRegion.bounds
        val a = viewport.screenPoint(box.minX, box.minY)
        val b = viewport.screenPoint(box.maxX, box.maxY)
        drawRect(
            color = DesktopBrandColors.gold,
            topLeft = Offset(
                minOf(a.x, b.x) + dragOutlineOffset.x,
                minOf(a.y, b.y) + dragOutlineOffset.y
            ),
            size = Size(
                (maxOf(a.x, b.x) - minOf(a.x, b.x)).coerceAtLeast(1f),
                (maxOf(a.y, b.y) - minOf(a.y, b.y)).coerceAtLeast(1f)
            ),
            style = Stroke(width = 2f)
        )
    }
}
