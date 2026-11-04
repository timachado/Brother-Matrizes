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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
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

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Brother Matrizes Desktop — QA",
        state = rememberWindowState(width = 1280.dp, height = 820.dp)
    ) {
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
    val preview = remember(document) { document?.let(DesktopMatrixService::preview) }
    var message by remember { mutableStateOf("Versão Windows de homologação: sem acesso à conta ou arquivos em nuvem.") }
    var isError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var scaleText by remember { mutableStateOf("1.00") }
    var angleText by remember { mutableStateOf("0") }
    var xMmText by remember { mutableStateOf("0") }
    var yMmText by remember { mutableStateOf("0") }
    var mirrorX by remember { mutableStateOf(false) }
    var mirrorY by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf("PES") }
    var peDesign by remember { mutableStateOf(false) }
    var nameText by remember { mutableStateOf("") }
    var nameHeightText by remember { mutableStateOf("12") }
    var nameStyle by remember { mutableStateOf(TextStitchStyle.RUNNING) }
    var libraryFolder by remember { mutableStateOf<String?>(null) }
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
                                stitchProgress = 1f
                                format = it.current.format
                                scaleText = "1.00"
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
                                    format = "DST"
                                    peDesign = false
                                    scaleText = "1.00"
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
                            val scale = scaleText.trim().replace(',', '.').toFloatOrNull()
                            val degrees = angleText.trim().replace(',', '.').toFloatOrNull()
                            val xMm = xMmText.trim().replace(',', '.').toFloatOrNull()
                            val yMm = yMmText.trim().replace(',', '.').toFloatOrNull()
                            if (scale == null || degrees == null || xMm == null || yMm == null ||
                                !xMm.isFinite() || !yMm.isFinite() ||
                                xMm !in -200f..200f || yMm !in -200f..200f
                            ) {
                                report("Informe escala, rotação e deslocamentos válidos (máx. 200 mm).", true)
                            } else {
                                busy = true
                                scope.launch {
                                    val result = withContext(Dispatchers.Default) {
                                        runCatching {
                                            DesktopMatrixService.edit(
                                                loaded,
                                                EditTransform(
                                                    scale = scale,
                                                    rotationDegrees = degrees,
                                                    offsetXUnits = (xMm * 10f).roundToInt(),
                                                    offsetYUnits = (yMm * 10f).roundToInt(),
                                                    mirrorHorizontal = mirrorX,
                                                    mirrorVertical = mirrorY
                                                )
                                            )
                                        }
                                    }
                                    busy = false
                                    result.onSuccess {
                                        document = it
                                        report("Ajustes aplicados em memória. A matriz original está preservada.")
                                    }.onFailure {
                                        report("Ajuste recusado: ${it.message}", true)
                                    }
                                }
                            }
                        }) { Text("Aplicar") }
                        TextButton(enabled = document != null && !busy, onClick = {
                            document = document?.let(DesktopMatrixService::reset)
                            report("Ajustes descartados. Matriz original restaurada.")
                        }) { Text("Restaurar original") }
                    }

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
                                    libraryFolder = folder.toString()
                                    libraryEntries = it
                                    report("Biblioteca local: ${it.size} matriz(es) encontrada(s). Nenhum arquivo foi movido.")
                                }.onFailure { report("Erro ao listar pasta: ${it.message}", true) }
                            }
                        }
                    }) { Text("Selecionar pasta do computador") }
                    libraryFolder?.let {
                        Text(it, color = muted, fontSize = 11.sp)
                        Text("${libraryEntries.size} matriz(es) encontrada(s)", color = muted, fontSize = 12.sp)
                    }
                    for (entry in libraryEntries.take(60)) {
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                val file = entry.path
                                busy = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching { DesktopMatrixService.openFromFile(file) }
                                    }
                                    busy = false
                                    result.onSuccess {
                                        document = it
                                        format = it.current.format
                                        peDesign = false
                                        stitchProgress = 1f
                                        scaleText = "1.00"
                                        angleText = "0"
                                        xMmText = "0"
                                        yMmText = "0"
                                        mirrorX = false
                                        mirrorY = false
                                        report("Matriz aberta da biblioteca local, sem modificações no arquivo de origem.")
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
