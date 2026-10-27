package com.timachado.brothermatrizes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryFontPreset
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.NameAutoPlacement
import com.timachado.brothermatrizes.core.embroidery.NamePersonalization
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.font.ImportedFontMatrixGenerator
import com.timachado.brothermatrizes.font.ImportedFontStore
import com.timachado.brothermatrizes.core.account.BrotherMatrizesAccountService
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class NameDraft(
    val name: String = "Maria",
    val font: EmbroideryFontPreset = EmbroideryFontPreset.ELEGANT,
    val height: Float = 12f,
    val spacing: Float = 1.2f,
    val density: Float = .45f,
    val color: Int = 0xB22222,
    val rotation: Float = 0f,
    val importedFontId: String? = null,
    val x: Int,
    val y: Int
)

@Composable
fun NameEditorScreen(
    original: EmbroideryDesign,
    referenceHoop: HoopProfile,
    onBack: () -> Unit,
    onApply: (EmbroideryDesign) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importedFonts by remember(context) {
        mutableStateOf(ImportedFontStore.list(context))
    }
    var fontImportStatus by remember { mutableStateOf<String?>(null) }
    var importingFont by remember { mutableStateOf(false) }

    var draft by remember(original) {
        val existing = original.editableName
        mutableStateOf(
            NameDraft(
                name = existing?.options?.text ?: "Maria",
                font = existing?.options?.font ?: EmbroideryFontPreset.ELEGANT,
                height = existing?.options?.heightMm ?: 12f,
                spacing = existing?.options?.spacingMm ?: 1.2f,
                density = existing?.options?.satinDensityMm ?: .45f,
                color = existing?.options?.color ?: 0xB22222,
                rotation = existing?.options?.rotationDegrees ?: 0f,
                importedFontId = existing?.options?.importedFontId,
                x = existing?.centerXUnits ?: (original.bounds.minXUnits.toLong() +
                    original.bounds.maxXUnits.toLong()).div(2).toInt(),
                y = existing?.centerYUnits ?: (original.bounds.minYUnits.toLong() +
                    original.bounds.maxYUnits.toLong()).div(2).toInt()
            )
        )
    }
    var undo by remember(original) { mutableStateOf(emptyList<NameDraft>()) }
    var redo by remember(original) { mutableStateOf(emptyList<NameDraft>()) }
    var fontsExpanded by remember { mutableStateOf(false) }
    var zoomMode by remember(original) { mutableStateOf(false) }
    var transformNameMode by remember(original) { mutableStateOf(false) }
    var selectingOldName by remember(original) { mutableStateOf(false) }
    var baseDesign by remember(original) { mutableStateOf(original) }

    fun setDraft(next: NameDraft, history: Boolean = true) {
        if (next == draft) return
        if (history) undo = (undo + draft).takeLast(40)
        redo = emptyList()
        draft = next
    }

    val fontPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !importingFont) scope.launch {
            importingFont = true
            fontImportStatus = null
            try {
                val qaImport = NameQaImportPolicy.allowLocalImportWithoutAccount(
                    debugBuild = BuildConfig.DEBUG, applicationId = context.packageName
                )
                val key = if (qaImport) null else {
                    val allowed = BrotherMatrizesAccountService.authorizeImport(
                        context = context, isFont = true
                    )
                    if (allowed.isFailure) {
                        fontImportStatus = allowed.exceptionOrNull()?.message ?:
                            "Entre com Google para importar fontes."
                        return@launch
                    }
                    allowed.getOrThrow()
                }
                val before = importedFonts.map { it.id }.toSet()
                val imported = withContext(Dispatchers.IO) {
                    ImportedFontStore.importFont(context, uri)
                }
                if (key != null) {
                    BrotherMatrizesAccountService.finalizeImport(
                        context = context, isFont = true, requestKey = key,
                        success = imported.isSuccess &&
                            imported.getOrNull()?.id !in before
                    )
                }
                imported.fold(
                    onSuccess = { font ->
                        importedFonts = ImportedFontStore.list(context)
                        autoFitEnabled = true
                        setDraft(draft.copy(importedFontId = font.id))
                        fontImportStatus = "Fonte ${font.displayName} selecionada."
                    },
                    onFailure = { error ->
                        fontImportStatus = error.message ?: "Arquivo de fonte inválido."
                    }
                )
            } finally {
                importingFont = false
            }
        }
    }

    // Geração completa e validação nunca ocorrem na thread de desenho/UI.
    // LaunchedEffect cancela o job obsoleto quando texto/posição muda.
    data class ComputedPreview(
        val base: EmbroideryDesign,
        val settings: NameDraft,
        val result: Result<EmbroideryDesign>
    )

    var computed by remember(original) {
        mutableStateOf<ComputedPreview?>(null)
    }
    var autoFitEnabled by remember(original) {
        mutableStateOf(original.editableName == null)
    }
    var autoFitRequest by remember(original) { mutableIntStateOf(0) }
    var fitting by remember(original) { mutableStateOf(false) }
    var autoFitError by remember(original) { mutableStateOf<String?>(null) }
    var dragging by remember(original) { mutableStateOf(false) }
    var dragPx by remember(original) { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    fun optionsFor(value: NameDraft) = TextMatrixOptions(
        text = value.name,
        font = value.font,
        importedFontId = value.importedFontId,
        heightMm = value.height,
        spacingMm = value.spacing,
        satinDensityMm = value.density,
        rotationDegrees = value.rotation,
        style = TextStitchStyle.SATIN,
        outputFormat = original.format,
        color = value.color,
        hoopProfile = referenceHoop,
        enforceHoop = true
    )

    // A Biblioteca de Fontes já usa o motor nativo de contornos TTF/OTF.
    // Aqui ele é reutilizado integralmente na composição com a matriz.
    fun generateImported(options: TextMatrixOptions): Result<EmbroideryDesign> {
        val id = options.importedFontId ?: return Result.failure(
            IllegalArgumentException("Fonte importada não selecionada.")
        )
        val font = importedFonts.firstOrNull { it.id == id }
            ?: return Result.failure(IllegalArgumentException(
                "Fonte ausente. Reimporte o TTF/OTF na Biblioteca de Fontes."
            ))
        return ImportedFontMatrixGenerator.generateText(font, options.text, options)
    }

    LaunchedEffect(baseDesign, draft, referenceHoop, importedFonts) {
        delay(180L) // Gestos/teclado rápidos não geram centenas de matrizes.
        val target = draft
        val source = baseDesign
        val options = optionsFor(target)
        val validated = withContext(Dispatchers.Default) {
            NamePersonalization.add(
                source, options, target.x, target.y,
                importedGenerator = if (options.importedFontId != null) ::generateImported else null
            )
        }
        computed = ComputedPreview(source, target, validated)
    }

    LaunchedEffect(baseDesign, draft.name, draft.font, draft.importedFontId,
        referenceHoop, importedFonts, autoFitEnabled, autoFitRequest
    ) {
        if (!autoFitEnabled || draft.name.isBlank()) {
            fitting = false
            return@LaunchedEffect
        }
        delay(350L)
        fitting = true
        autoFitError = null
        try {
            val initial = draft
            val source = baseDesign
            val fit = withContext(Dispatchers.Default) {
                NameAutoPlacement.find(
                    original = source,
                    requested = optionsFor(initial),
                    centerXUnits = (source.bounds.minXUnits.toLong() +
                        source.bounds.maxXUnits.toLong()).div(2).toInt(),
                    centerYUnits = (source.bounds.minYUnits.toLong() +
                        source.bounds.maxYUnits.toLong()).div(2).toInt(),
                    importedGenerator = if (initial.importedFontId != null)
                        ::generateImported else null
                )
            }
            fit.fold(
                onSuccess = { found ->
                    val next = initial.copy(
                        font = found.options.font,
                        height = found.options.heightMm,
                        x = found.centerXUnits,
                        y = found.centerYUnits
                    )
                    // Marcar automaticidade resolvida para evitar pesquisa
                    // infinita quando o mecanismo troca fonte por Compacta.
                    autoFitEnabled = false
                    setDraft(next, history = false)
                    computed = ComputedPreview(source, next, Result.success(found.design))
                },
                onFailure = { error ->
                    autoFitError = error.message
                    autoFitEnabled = false
                }
            )
        } finally {
            fitting = false
        }
    }

    val currentPreview = computed?.takeIf {
        it.base === baseDesign && it.settings == draft
    }
    val preview = currentPreview?.result?.getOrNull()
    val currentError = currentPreview?.result?.exceptionOrNull()?.message
    val previewBusy = currentPreview == null || fitting || dragging

    val paddingPx = with(LocalDensity.current) { 36.dp.toPx() }
    val portraitHoop =
        (original.bounds.widthMm > original.bounds.heightMm) !=
            (referenceHoop.widthMm > referenceHoop.heightMm) &&
            referenceHoop.widthMm != referenceHoop.heightMm
    val frameWidthUnits = (if (portraitHoop) referenceHoop.heightMm
        else referenceHoop.widthMm) * 10f
    val frameHeightUnits = (if (portraitHoop) referenceHoop.widthMm
        else referenceHoop.heightMm) * 10f
    val canvasScale = minOf(
        (canvasSize.width - 2f * paddingPx) / frameWidthUnits,
        (canvasSize.height - 2f * paddingPx) / frameHeightUnits
    ).coerceAtLeast(.01f)
    val direction = if (original.sourceYAxisDown) 1f else -1f

    val transformName by rememberUpdatedState<(Offset, Float, Float) -> Unit> { pan, zoom, rotation ->
        autoFitEnabled = false
        setDraft(draft.copy(
            x = draft.x + (pan.x / canvasScale).roundToInt(),
            y = draft.y + (pan.y / (canvasScale * direction)).roundToInt(),
            height = (draft.height * zoom).coerceIn(4f, 30f),
            rotation = (draft.rotation + rotation).coerceIn(-180f, 180f)
        ), history = false)
    }

    val commitDrag by rememberUpdatedState<(Offset) -> Unit> { moved ->
        if (moved != Offset.Zero) {
            autoFitEnabled = false
            setDraft(draft.copy(
                x = draft.x + (moved.x / canvasScale).roundToInt(),
                y = draft.y + (moved.y / (canvasScale * direction)).roundToInt()
            ))
        }
        dragging = false
        dragPx = Offset.Zero
    }

    if (selectingOldName) {
        ImportedNameSelectionPanel(
            original = original,
            hoop = referenceHoop,
            onCancel = { selectingOldName = false },
            onConfirm = { selected ->
                baseDesign = selected.originalWithoutBlock
                setDraft(draft.copy(
                    x = selected.centerXUnits,
                    y = selected.centerYUnits
                ))
                selectingOldName = false
                zoomMode = false
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("‹ Voltar", color = FioGold) }
            Text(
                "Adicionar / Editar Nome",
                color = FioText,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    val previous = undo.lastOrNull()
                    if (previous != null) {
                        redo = redo + draft
                        undo = undo.dropLast(1)
                        draft = previous
                    }
                },
                enabled = undo.isNotEmpty()
            ) { Text("Desfazer") }
            TextButton(
                onClick = {
                    val next = redo.lastOrNull()
                    if (next != null) {
                        undo = undo + draft
                        redo = redo.dropLast(1)
                        draft = next
                    }
                },
                enabled = redo.isNotEmpty()
            ) { Text("Refazer") }
        }

        if (original.editableName == null &&
            original.points.any { it.command ==
                com.timachado.brothermatrizes.core.embroidery.StitchCommand.COLOR_CHANGE } &&
            baseDesign === original
        ) {
            OutlinedButton(
                onClick = { selectingOldName = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Substituir nome existente (selecionar bloco)")
            }
        }

        if (baseDesign !== original) {
            OutlinedButton(
                onClick = {
                    baseDesign = original
                    zoomMode = false
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Desfazer seleção do nome antigo")
            }
        }

        Text(
            "Arraste para mover; em Girar/Redimensionar use dois dedos para pinçar e girar. " +
                "O encaixe automático procura espaço livre sem alterar as flores.",
            color = FioTextMuted,
            fontSize = 12.sp
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                zoomMode = false
                transformNameMode = false
            }) {
                Text(if (!zoomMode && !transformNameMode) "✓ Mover nome" else "Mover nome")
            }
            OutlinedButton(onClick = {
                zoomMode = true
                transformNameMode = false
            }) {
                Text(if (zoomMode) "✓ Zoom e pan" else "Zoom e pan")
            }
        }
        OutlinedButton(
            onClick = {
                transformNameMode = !transformNameMode
                zoomMode = false
            },
            modifier = Modifier.fillMaxWidth().testTag("brother_name_gesture")
        ) {
            Text(if (transformNameMode) "✓ Girar, pinçar e mover o nome"
                else "Girar / Redimensionar com dois dedos")
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(285.dp)
                .onSizeChanged { canvasSize = it }
                .then(
                    if (zoomMode) Modifier
                    else if (transformNameMode) Modifier.pointerInput(
                        original, referenceHoop, paddingPx
                    ) {
                        detectTransformGestures { _, pan, zoom, rotation ->
                            transformName(pan, zoom, rotation)
                        }
                    }
                    else Modifier.pointerInput(original, referenceHoop, paddingPx) {
                    detectDragGestures(
                        onDragStart = {
                            dragging = true
                            dragPx = Offset.Zero
                        },
                        onDragEnd = { commitDrag(dragPx) },
                        onDragCancel = {
                            dragging = false
                            dragPx = Offset.Zero
                        }
                    ) { change, amount ->
                        change.consume()
                        // Apenas um fantasma de texto é movido por frame.
                        // O arquivo real é calculado UMA vez, ao soltar.
                        dragPx += amount
                    }
                })
        ) {
            EmbroideryCanvas(
                design = if (dragging || previewBusy) baseDesign else preview ?: baseDesign,
                hoop = referenceHoop,
                interactive = zoomMode,
                modifier = Modifier.fillMaxSize()
            )
            if (dragging || (transformNameMode && previewBusy)) {
                val midX = (baseDesign.bounds.minXUnits.toLong() +
                    baseDesign.bounds.maxXUnits.toLong()) / 2f
                val midY = (baseDesign.bounds.minYUnits.toLong() +
                    baseDesign.bounds.maxYUnits.toLong()) / 2f
                Text(
                    draft.name,
                    color = FioGold,
                    fontSize = (draft.height * 1.6f).coerceIn(12f, 42f).sp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset {
                            IntOffset(
                                ((draft.x - midX) * canvasScale + dragPx.x).roundToInt(),
                                ((draft.y - midY) * canvasScale * direction +
                                    dragPx.y).roundToInt()
                            )
                        }
                        .background(Color(0xE6121A27))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }

        when {
            fitting -> Text(
                "Encaixando nome automaticamente na área livre…",
                color = FioTextMuted, fontSize = 12.sp
            )
            previewBusy -> Text(
                "Atualizando prévia de pontadas sem travar a tela…",
                color = FioTextMuted, fontSize = 12.sp
            )
            preview == null -> Text(
                currentError ?: autoFitError ?: "Prévia indisponível.",
                color = Color(0xFFFF9393), fontSize = 12.sp
            )
            else -> Text(
                "Prévia válida: ${preview.stitchCount} pontos • " +
                    "${preview.bounds.widthMm} × ${preview.bounds.heightMm} mm",
                color = FioTextMuted, fontSize = 12.sp
            )
        }

        OutlinedButton(
            onClick = {
                autoFitRequest++
                autoFitEnabled = true
            },
            modifier = Modifier.fillMaxWidth().testTag("brother_name_auto_fit")
        ) {
            Text("✧ Ajustar automaticamente à matriz")
        }

        OutlinedTextField(
            value = draft.name,
            onValueChange = {
                autoFitEnabled = true
                setDraft(draft.copy(name = it.take(24)))
            },
            label = { Text("Nome para bordar") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("brother_name_input")
        )

        Box {
            OutlinedButton(onClick = {
                importedFonts = ImportedFontStore.list(context)
                fontsExpanded = true
            }, modifier = Modifier.fillMaxWidth().testTag("brother_name_font_selector")) {
                val label = if (draft.importedFontId == null) draft.font.displayName
                    else importedFonts.firstOrNull {
                        it.id == draft.importedFontId
                    }?.displayName ?: "Fonte importada ausente"
                Text("Fonte: " + label)
            }
            DropdownMenu(
                expanded = fontsExpanded,
                onDismissRequest = { fontsExpanded = false }
            ) {
                EmbroideryFontPreset.entries.forEach { font ->
                    DropdownMenuItem(
                        text = { Text("BM • " + font.displayName) },
                        onClick = {
                            fontsExpanded = false
                            autoFitEnabled = true
                            setDraft(draft.copy(font = font, importedFontId = null))
                        }
                    )
                }
                importedFonts.forEach { font ->
                    DropdownMenuItem(
                        text = { Text("TTF/OTF • " + font.displayName) },
                        onClick = {
                            fontsExpanded = false
                            autoFitEnabled = true
                            setDraft(draft.copy(importedFontId = font.id))
                        }
                    )
                }
            }
        }
        OutlinedButton(
            onClick = {
                fontPicker.launch(arrayOf(
                    "font/ttf", "font/otf",
                    "application/x-font-ttf", "application/x-font-opentype",
                    "application/octet-stream", "*/*"
                ))
            },
            enabled = !importingFont,
            modifier = Modifier.fillMaxWidth().testTag("brother_name_font_import")
        ) {
            Text(if (importingFont) "Importando fonte…" else
                "+ Importar TTF/OTF do celular (DaFont, NetFontes)")
        }
        if (fontImportStatus != null) {
            Text(fontImportStatus.orEmpty(), color = FioTextMuted, fontSize = 12.sp)
        }
        if (draft.importedFontId != null &&
            importedFonts.none { it.id == draft.importedFontId }
        ) {
            Text("A fonte deste projeto não está instalada neste aparelho. " +
                "Reimporte o mesmo TTF/OTF para editar o nome.",
                color = Color(0xFFFF9393), fontSize = 12.sp)
        }

        Text("Altura: " + "%.1f".format(draft.height) + " mm", color = FioText)
        Slider(
            value = draft.height,
            onValueChange = {
                autoFitEnabled = false
                setDraft(draft.copy(height = it), history = false)
            },
            valueRange = 4f..30f
        )
        Text("Espaçamento: " + "%.1f".format(draft.spacing) + " mm", color = FioText)
        Slider(
            value = draft.spacing,
            onValueChange = {
                autoFitEnabled = false
                setDraft(draft.copy(spacing = it), history = false)
            },
            valueRange = 0f..5f
        )
        Text("Densidade Satin: " + "%.2f".format(draft.density) + " mm", color = FioText)
        Slider(
            value = draft.density,
            onValueChange = {
                autoFitEnabled = false
                setDraft(draft.copy(density = it), history = false)
            },
            valueRange = .3f..1.0f
        )

        Text("Rotação do nome: ${draft.rotation.roundToInt()}°", color = FioText)
        Slider(
            value = draft.rotation,
            onValueChange = {
                autoFitEnabled = false
                setDraft(draft.copy(rotation = (it / 5f).roundToInt() * 5f), history = false)
            },
            valueRange = -180f..180f
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                autoFitEnabled = false
                setDraft(draft.copy(rotation = (draft.rotation - 15f).coerceAtLeast(-180f)))
            }) { Text("↶ 15°") }
            OutlinedButton(onClick = {
                autoFitEnabled = false
                setDraft(draft.copy(rotation = 0f))
            }) { Text("0°") }
            OutlinedButton(onClick = {
                autoFitEnabled = false
                setDraft(draft.copy(rotation = (draft.rotation + 15f).coerceAtMost(180f)))
            }) { Text("↷ 15°") }
        }

        Text("Cor da linha", color = FioText)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(0xB22222, 0xE6BE70, 0x173E6E, 0xB83A83, 0x17846B, 0x202020)
                .forEachIndexed { index, color ->
                    OutlinedButton(
                        onClick = { setDraft(draft.copy(color = color)) }
                    ) {
                        Text(
                            if (color == draft.color) "✓ ${index + 1}" else "● ${index + 1}",
                            color = Color(color or 0xFF000000.toInt())
                        )
                    }
                }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                setDraft(draft.copy(
                    x = (baseDesign.bounds.minXUnits.toLong() +
                        baseDesign.bounds.maxXUnits.toLong()).div(2).toInt(),
                    y = (baseDesign.bounds.minYUnits.toLong() +
                        baseDesign.bounds.maxYUnits.toLong()).div(2).toInt()
                ))
            }) { Text("Centralizar nome") }
            OutlinedButton(onClick = { setDraft(draft.copy(x = draft.x - 10)) }) {
                Text("←")
            }
            OutlinedButton(onClick = { setDraft(draft.copy(x = draft.x + 10)) }) {
                Text("→")
            }
        }
        Text(
            "Nomes salvos em Minhas Matrizes permanecem editáveis. " +
                "Após exportar e reabrir PES/DST, a troca de nome existente exige " +
                "seleção manual de um bloco de cor isolado; regiões ambíguas são bloqueadas.",
            color = FioTextMuted,
            fontSize = 11.sp
        )
        Button(
            onClick = { preview?.let(onApply) },
            enabled = preview != null && !previewBusy,
            modifier = Modifier.fillMaxWidth().testTag("brother_name_apply")
        ) {
            Text("Aplicar nome e abrir matriz personalizada")
        }
        Spacer(Modifier.height(8.dp))
    }
}
