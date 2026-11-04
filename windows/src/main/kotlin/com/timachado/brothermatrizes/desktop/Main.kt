package com.timachado.brothermatrizes.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import javax.swing.JLabel
import javax.swing.SwingConstants
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val cyan = Color(0xFF4EDAFF)
private val navy = Color(0xFF0A1625)
private val surface = Color(0xFF14273B)
private val muted = Color(0xFFACBDCD)
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
    if (args.size == 2 && args[0] == "--ui-smoke-test") {
        require(System.getenv("BROTHER_WINDOWS_QA_UI") == "1") {
            "Captura da interface permitida somente em ambiente QA autorizado."
        }
        launchDesktopApp(java.nio.file.Path.of(args[1]))
        return
    }
    require(args.isEmpty()) { "Argumentos não reconhecidos." }
    launchDesktopApp()
}

private fun launchDesktopApp(uiReport: Path? = null) = application {
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
        if (uiReport != null) {
            LaunchedEffect(uiReport) {
                delay(2500)
                val result = withContext(Dispatchers.IO) {
                    runCatching { DesktopUiSmoke.capture(window, uiReport) }
                }
                result.onFailure {
                    System.err.println("BROTHER_WINDOWS_UI_SMOKE_FAILED: ${it.message}")
                    kotlin.system.exitProcess(1)
                }
                exitApplication()
            }
        }
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = cyan, background = navy, surface = surface, onSurface = Color.White
            )
        ) {
            DesktopScreen()
        }
    }
}

@Composable
private fun DesktopScreen() {
    var document by remember { mutableStateOf<DesktopMatrixDocument?>(null) }
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
    val librarySelection = remember { DesktopLibrarySelection() }
    var libraryFolder by remember { mutableStateOf(librarySelection.recall()?.toString()) }
    var libraryEntries by remember { mutableStateOf<List<LocalMatrixEntry>>(emptyList()) }
    var stitchProgress by remember { mutableStateOf(1f) }
    val scope = rememberCoroutineScope()

    fun report(text: String, error: Boolean = false) {
        message = text
        isError = error
    }

    Surface(modifier = Modifier.fillMaxSize(), color = navy) {
        Column(modifier = Modifier.fillMaxSize().padding(22.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("BROTHER MATRIZES", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("T.I. Machado  •  Windows Desktop  •  QA não comercial",
                        color = cyan, fontSize = 13.sp)
                }
                Button(enabled = !busy, onClick = {
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
                                horizontalScaleText = "1.00"
                                verticalScaleText = "1.00"
                                angleText = "0"
                                xMmText = "0"
                                yMmText = "0"
                                mirrorX = false
                                mirrorY = false
                                peDesign = false
                                report("Matriz ${it.current.format} aberta localmente. O arquivo original não será modificado.")
                            }.onFailure {
                                report("Falha ao abrir: ${it.message ?: "arquivo inválido"}.", true)
                            }
                        }
                    }
                }) { Text(if (busy) "Processando..." else "Abrir DST / PES / JEF") }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                message,
                color = if (isError) Color(0xFFFF9DA9) else muted,
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
                    if (preview == null) {
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
                            StitchPreview(preview, stitchProgress, Modifier.weight(1f))
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
                        .verticalScroll(rememberScrollState())
                        .padding(17.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                                    horizontalScaleText = "1.00"
                                    verticalScaleText = "1.00"
                                    angleText = "0"
                                    xMmText = "0"
                                    yMmText = "0"
                                    mirrorX = false
                                    mirrorY = false
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
                    }
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
                                fontSize = 11.sp, color = Color(0xFFFF9DA9)
                            )
                        }
                    }
                    Text(
                        "Contornos Java2D + motor Satin oficial. Geração ainda experimental: não usar em produção ou máquinas de clientes.",
                        color = muted, fontSize = 11.sp
                    )
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
                        "Fonte integrada inicial. Importação TTF/OTF, composição por camadas e monogramas ainda não estão disponíveis.",
                        color = muted,
                        fontSize = 11.sp
                    )

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

                    Spacer(Modifier.height(8.dp))
                    Text("Ajustes geométricos — QA", color = cyan, fontWeight = FontWeight.SemiBold)
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
                        TextButton(enabled = !busy && editTimeline?.canUndo == true, onClick = {
                            val current = editTimeline ?: return@TextButton
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
                                    report("Último ajuste desfeito. Original e arquivos em disco preservados.")
                                }.onFailure { report("Não foi possível desfazer: ${it.message}", true) }
                            }
                        }) { Text("Desfazer") }
                        TextButton(enabled = !busy && editTimeline?.canRedo == true, onClick = {
                            val current = editTimeline ?: return@TextButton
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
                                    report("Ajuste refeito com o motor oficial. Nada foi gravado em disco.")
                                }.onFailure { report("Não foi possível refazer: ${it.message}", true) }
                            }
                        }) { Text("Refazer") }
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

                    Spacer(Modifier.height(12.dp))
                    Text("Biblioteca local (somente leitura)", color = cyan, fontWeight = FontWeight.SemiBold)
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
                                        report("Pasta atualizada: ${it.size} matriz(es).")
                                    }.onFailure { report("Erro ao atualizar pasta: ${it.message}", true) }
                                }
                            }) { Text("Atualizar pasta") }
                            TextButton(enabled = !busy, onClick = {
                                val cleared = runCatching { librarySelection.forget() }
                                if (cleared.isSuccess) {
                                    libraryFolder = null
                                    libraryEntries = emptyList()
                                    report("Preferência local da pasta apagada.")
                                } else {
                                    report("Não foi possível limpar a preferência: ${cleared.exceptionOrNull()?.message}", true)
                                }
                            }) { Text("Esquecer pasta") }
                        }
                    }
                    for (entry in libraryEntries.take(60)) {
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
                                        horizontalScaleText = "1.00"
                                        verticalScaleText = "1.00"
                                        angleText = "0"
                                        xMmText = "0"
                                        yMmText = "0"
                                        mirrorX = false
                                        mirrorY = false
                                        report("Matriz ou projeto da biblioteca aberto localmente, sem modificar o arquivo de origem.")
                                    }.onFailure { report("Falha ao abrir da biblioteca: ${it.message}", true) }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${entry.name}  ·  ${entry.format}", maxLines = 1)
                        }
                    }
                    if (libraryEntries.size > 60) {
                        Text(
                            "Exibindo 60 de ${libraryEntries.size} matrizes. Use uma subpasta para visualizar as demais.",
                            color = muted, fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Column {
        Text(label, color = muted, fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun StitchPreview(preview: DstPreview, progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize().padding(18.dp)) {
        renderPreview(preview, progress)
    }
}

private fun DrawScope.renderPreview(design: DstPreview, progress: Float) {
    val width = (design.bounds.maxX.toLong() - design.bounds.minX.toLong()).coerceAtLeast(1L).toFloat()
    val height = (design.bounds.maxY.toLong() - design.bounds.minY.toLong()).coerceAtLeast(1L).toFloat()
    val usableW = (size.width - 40f).coerceAtLeast(1f)
    val usableH = (size.height - 40f).coerceAtLeast(1f)
    val scale = minOf(usableW / width, usableH / height)
    val offsetX = (size.width - width * scale) / 2f
    val offsetY = (size.height - height * scale) / 2f

    fun offset(x: Int, y: Int) = Offset(
        offsetX + (x.toLong() - design.bounds.minX.toLong()).toFloat() * scale,
        offsetY + (y.toLong() - design.bounds.minY.toLong()).toFloat() * scale
    )

    val stride = maxOf(1, (design.points.size + 199_999) / 200_000)
    var previousX = 0
    var previousY = 0
    val visibleCount = (design.points.size * progress.coerceIn(0f, 1f)).toInt()
    for (index in 0 until visibleCount) {
        val p = design.points[index]
        if (p.command == PreviewCommand.STITCH && index % stride == 0) {
            drawLine(
                color = palette[Math.floorMod(p.colorIndex, palette.size)],
                start = offset(previousX, previousY),
                end = offset(p.x, p.y),
                strokeWidth = 1.3f
            )
        }
        previousX = p.x
        previousY = p.y
    }
}
