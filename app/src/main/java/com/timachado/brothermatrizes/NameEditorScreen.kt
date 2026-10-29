package com.timachado.brothermatrizes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryFontPreset
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.MatrixNameRotation
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
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos

private data class NameDraft(
    val name: String = "Maria",
    val font: EmbroideryFontPreset = EmbroideryFontPreset.ELEGANT,
    val height: Float = 12f,
    val spacing: Float = 1.2f,
    val density: Float = .45f,
    val color: Int = 0xB22222,
    val rotation: Float = 0f,
    val importedFontId: String? = null,
    val widthScale: Float = 1f,
    val heightScale: Float = 1f,
    val horizontalGlyphScale: Float = 1f,
    val x: Int,
    val y: Int
)

private data class EditorSnapshot(
    val name: NameDraft,
    val background: EmbroideryDesign,
    val matrixRotation: Float
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
                widthScale = existing?.options?.widthScale ?: 1f,
                heightScale = existing?.options?.heightScale ?: 1f,
                horizontalGlyphScale =
                    existing?.options?.horizontalGlyphScale ?: 1f,
                x = existing?.centerXUnits ?: (original.bounds.minXUnits.toLong() +
                    original.bounds.maxXUnits.toLong()).div(2).toInt(),
                y = existing?.centerYUnits ?: (original.bounds.minYUnits.toLong() +
                    original.bounds.maxYUnits.toLong()).div(2).toInt()
            )
        )
    }
    var undo by remember(original) { mutableStateOf(emptyList<EditorSnapshot>()) }
    var redo by remember(original) { mutableStateOf(emptyList<EditorSnapshot>()) }
    var fontsExpanded by remember { mutableStateOf(false) }
    var selectingOldName by remember(original) { mutableStateOf(false) }
    var oldNameBlockSelected by remember(original) { mutableStateOf(false) }
    var baseDesign by remember(original) { mutableStateOf(original) }
    var matrixRotation by remember(original) { mutableStateOf(0f) }
    var matrixRotationError by remember(original) { mutableStateOf<String?>(null) }

    fun snapshot() = EditorSnapshot(draft, baseDesign, matrixRotation)
    fun setDraft(next: NameDraft, history: Boolean = true) {
        if (next == draft) return
        if (history) undo = (undo + snapshot()).takeLast(40)
        redo = emptyList()
        draft = next
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
        // Em uma nova personalização, primeiro procurar um encaixe seguro.
        // Não mostrar o falso aviso de colisão do nome temporário
        // "Maria" em fonte BM Elegante antes da escolha da Adamiya.
        // Em nomes já existentes, preservar a edição e o tamanho salvos.
        mutableStateOf(original.editableName == null)
    }
    var autoFitRequest by remember(original) { mutableIntStateOf(0) }
    var reprocessRequest by remember(original) { mutableIntStateOf(0) }
    var reprocessResult by remember(original) { mutableStateOf<String?>(null) }
    var fitting by remember(original) { mutableStateOf(false) }
    var autoFitError by remember(original) { mutableStateOf<String?>(null) }
    var dragging by remember(original) { mutableStateOf(false) }
    var dragPx by remember(original) { mutableStateOf(Offset.Zero) }
    var gestureScaleX by remember(original) { mutableStateOf(1f) }
    var gestureScaleY by remember(original) { mutableStateOf(1f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

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

    fun optionsFor(value: NameDraft) = TextMatrixOptions(
        text = value.name,
        font = value.font,
        importedFontId = value.importedFontId,
        heightMm = value.height,
        spacingMm = if (value.importedFontId != null) 0f else value.spacing,
        satinDensityMm = value.density,
        rotationDegrees = value.rotation,
        widthScale = value.widthScale,
        heightScale = value.heightScale,
        horizontalGlyphScale = value.horizontalGlyphScale,
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

    LaunchedEffect(baseDesign, draft, referenceHoop, importedFonts,
        autoFitEnabled, reprocessRequest) {
        // Enquanto o encaixe automático pesquisa, não gerar a mesma fonte
        // em paralelo em outro job: fontes TTF/OTF podem ser pesadas.
        if (autoFitEnabled) return@LaunchedEffect
        // O resultado vindo do autoajuste já contém pontadas validadas.
        // Evita a segunda geração lenta e um aviso vermelho atrasado.
        if (reprocessRequest == 0 && computed?.base === baseDesign &&
            computed?.settings == draft && computed?.result?.isSuccess == true
        ) return@LaunchedEffect
        delay(560L) // aguardar pausa real entre gestos e edições de controles
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
        if (reprocessRequest > 0) {
            reprocessResult = validated.fold(
                onSuccess = { newMatrix ->
                    if (original.editableName != null && original.points == newMatrix.points) {
                        "Reprocessamento concluído, mas a sequência de pontadas permaneceu igual. " +
                            "É necessário analisar o arquivo original da fonte para identificar a falha."
                    } else {
                        "Novas pontadas geradas. Para atualizar a simulação, " +
                            "toque em Aplicar nome e abra a matriz personalizada."
                    }
                },
                onFailure = { error ->
                    "Não foi possível reprocessar: " + (error.message ?: "erro desconhecido")
                }
            )
        }
    }

    LaunchedEffect(baseDesign, draft.name, draft.font, draft.importedFontId,
        referenceHoop, importedFonts, autoFitEnabled, autoFitRequest
    ) {
        if (!autoFitEnabled || draft.name.isBlank()) {
            fitting = false
            return@LaunchedEffect
        }
        delay(750L)
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
                        widthScale = found.options.widthScale,
                        heightScale = found.options.heightScale,
                        rotation = found.options.rotationDegrees,
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
    val previewBusy = currentPreview == null || fitting || autoFitEnabled

    val paddingPx = with(LocalDensity.current) { 36.dp.toPx() }
    val backgroundDesign = baseDesign.editableName?.original ?: baseDesign
    val canvasScale = NameEditorPlacement.screenOffset(
        backgroundDesign, referenceHoop, 0, 0,
        canvasSize.width, canvasSize.height, paddingPx
    ).unitsPerPixel
    val direction = if (backgroundDesign.sourceYAxisDown) 1f else -1f

    val commitGesture by rememberUpdatedState<(Offset, Float, Float) -> Unit> {
            moved, onScreenX, onScreenY ->
        autoFitEnabled = false
        val delta = NameEditorPlacement.dragUnits(
            moved.x, moved.y, canvasScale, direction
        )
        val radians = Math.toRadians(draft.rotation.toDouble())
        val isSideways = abs(sin(radians)) > abs(cos(radians))
        val localX = if (isSideways) onScreenY else onScreenX
        val localY = if (isSideways) onScreenX else onScreenY
        val next = draft.copy(
            x = draft.x + delta.first,
            y = draft.y + delta.second,
            widthScale = (draft.widthScale * localX).coerceIn(.5f, 2.5f),
            heightScale = (draft.heightScale * localY).coerceIn(.5f, 2.5f)
        )
        setDraft(next)
        dragging = false
        dragPx = Offset.Zero
        gestureScaleX = 1f
        gestureScaleY = 1f
    }

    fun rotate(deltaDegrees: Float) {
        autoFitEnabled = false
        setDraft(draft.copy(rotation =
            (draft.rotation + deltaDegrees).coerceIn(-180f, 180f)))
    }

    fun rotateMatrix(deltaDegrees: Float) {
        if (abs(deltaDegrees) < 0.001f) return
        autoFitEnabled = false
        matrixRotationError = null
        MatrixNameRotation.rotate(
            background = baseDesign,
            nameXUnits = draft.x,
            nameYUnits = draft.y,
            nameRotationDegrees = draft.rotation,
            deltaDegrees = deltaDegrees,
            hoop = referenceHoop
        ).fold(
            onSuccess = { transformed ->
                undo = (undo + snapshot()).takeLast(40)
                redo = emptyList()
                baseDesign = transformed.background
                draft = draft.copy(
                    x = transformed.nameXUnits,
                    y = transformed.nameYUnits,
                    rotation = transformed.nameRotationDegrees
                )
                matrixRotation = MatrixNameRotation.normalizeDegrees(
                    matrixRotation + deltaDegrees
                )
                computed = null
            },
            onFailure = { matrixRotationError =
                it.message ?: "Não foi possível girar a matriz com segurança."
            }
        )
    }

    fun resize(deltaMm: Float) {
        autoFitEnabled = false
        setDraft(draft.copy(height =
            (draft.height + deltaMm).coerceIn(4f, 30f)))
    }

    if (selectingOldName) {
        ImportedNameSelectionPanel(
            original = original,
            hoop = referenceHoop,
            onCancel = { selectingOldName = false },
            onConfirm = { selected ->
                baseDesign = selected.originalWithoutBlock
                oldNameBlockSelected = true
                setDraft(draft.copy(
                    x = selected.centerXUnits,
                    y = selected.centerYUnits
                ))
                selectingOldName = false
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
                        redo = redo + snapshot()
                        undo = undo.dropLast(1)
                        baseDesign = previous.background
                        matrixRotation = previous.matrixRotation
                        draft = previous.name
                        matrixRotationError = null
                    }
                },
                enabled = undo.isNotEmpty()
            ) { Text("Desfazer") }
            TextButton(
                onClick = {
                    val next = redo.lastOrNull()
                    if (next != null) {
                        undo = undo + snapshot()
                        redo = redo.dropLast(1)
                        baseDesign = next.background
                        matrixRotation = next.matrixRotation
                        draft = next.name
                        matrixRotationError = null
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

        if (oldNameBlockSelected) {
            OutlinedButton(
                onClick = {
                    baseDesign = original
                    oldNameBlockSelected = false
                    matrixRotation = 0f
                    computed = null
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Desfazer seleção do nome antigo")
            }
        }

        Text(
            "Arraste com um dedo para mover. Use dois dedos para ampliar " +
                "a largura e a altura independentemente.",
            color = FioTextMuted, fontSize = 12.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { rotate(-15f) },
                modifier = Modifier.weight(1f).testTag("brother_name_rotate_left")
            ) { Text("↶ 15°") }
            OutlinedButton(
                onClick = {
                    autoFitEnabled = false
                    setDraft(draft.copy(rotation = 0f))
                },
                modifier = Modifier.weight(1f).testTag("brother_name_rotate_reset")
            ) { Text("Endireitar") }
            OutlinedButton(
                onClick = { rotate(15f) },
                modifier = Modifier.weight(1f).testTag("brother_name_rotate_right")
            ) { Text("↷ 15°") }
        }

        Text("Rotação da matriz: " + matrixRotation.roundToInt() + "°",
            color = FioText, modifier = Modifier.testTag("brother_matrix_rotation_label"))
        Text("Gira a moldura e o nome juntos. A rotação altera as pontadas reais.",
            color = FioTextMuted, fontSize = 11.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { rotateMatrix(-15f) },
                modifier = Modifier.weight(1f).testTag("brother_matrix_rotate_left")
            ) { Text("↶ 15°") }
            OutlinedButton(
                onClick = {
                    rotateMatrix(MatrixNameRotation.normalizeDegrees(-matrixRotation))
                },
                modifier = Modifier.weight(1f).testTag("brother_matrix_rotate_reset")
            ) { Text("Endireitar") }
            OutlinedButton(
                onClick = { rotateMatrix(15f) },
                modifier = Modifier.weight(1f).testTag("brother_matrix_rotate_right")
            ) { Text("↷ 15°") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(0f, 90f, 180f, 270f).forEach { angle ->
                OutlinedButton(
                    onClick = {
                        rotateMatrix(MatrixNameRotation.normalizeDegrees(
                            angle - matrixRotation
                        ))
                    },
                    modifier = Modifier.weight(1f).testTag(
                        "brother_matrix_rotate_" + angle.toInt()
                    )
                ) { Text(angle.toInt().toString() + "°") }
            }
        }
        matrixRotationError?.let { message ->
            Text(message, color = Color(0xFFFF9393), fontSize = 12.sp,
                modifier = Modifier.testTag("brother_matrix_rotation_error"))
        }

        // Girar um PES/DST sem necessariamente inserir um novo nome.
        // A matriz rotacionada é uma cópia; o arquivo importado permanece intacto.
        OutlinedButton(
            onClick = { onApply(baseDesign) },
            enabled = abs(matrixRotation) >= .001f && matrixRotationError == null,
            modifier = Modifier.fillMaxWidth()
                .testTag("brother_matrix_apply_rotation_only")
        ) {
            Text("Aplicar só a rotação da matriz")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { resize(-1f) },
                modifier = Modifier.weight(1f).testTag("brother_name_size_down")
            ) { Text("A− Menor") }
            OutlinedButton(
                onClick = {
                    autoFitEnabled = false
                    setDraft(draft.copy(
                        x = (baseDesign.bounds.minXUnits.toLong() +
                            baseDesign.bounds.maxXUnits.toLong()).div(2).toInt(),
                        y = (baseDesign.bounds.minYUnits.toLong() +
                            baseDesign.bounds.maxYUnits.toLong()).div(2).toInt()
                    ))
                },
                modifier = Modifier.weight(1f).testTag("brother_name_center")
            ) { Text("Centralizar") }
            OutlinedButton(
                onClick = { resize(1f) },
                modifier = Modifier.weight(1f).testTag("brother_name_size_up")
            ) { Text("A+ Maior") }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .onSizeChanged { canvasSize = it }
                .testTag("brother_name_canvas")
                // Gesto em toda a prévia: o nome continua arrastável sem
                // exibir a antiga alça escura sobre flores e borboletas.
                .pointerInput(original, referenceHoop) {
                    // Gestos combinados: um dedo move; dois dedos permitem
                    // pinça horizontal e vertical sem recalcular Satin
                    // durante o movimento.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var scaleX = 1f
                        var scaleY = 1f
                        var twoFingers = false
                        dragging = true
                        dragPx = Offset.Zero
                        gestureScaleX = 1f
                        gestureScaleY = 1f
                        do {
                            val event = awaitPointerEvent()
                            val active = event.changes.filter { it.pressed }
                            if (active.size >= 2) {
                                twoFingers = true
                                val first = active[0]
                                val second = active[1]
                                val oldDx = abs(first.previousPosition.x -
                                    second.previousPosition.x)
                                val oldDy = abs(first.previousPosition.y -
                                    second.previousPosition.y)
                                val dx = abs(first.position.x - second.position.x)
                                val dy = abs(first.position.y - second.position.y)
                                if (oldDx > 14f && dx > 14f) {
                                    scaleX = (scaleX * dx / oldDx).coerceIn(.5f, 2.5f)
                                }
                                if (oldDy > 14f && dy > 14f) {
                                    scaleY = (scaleY * dy / oldDy).coerceIn(.5f, 2.5f)
                                }
                                gestureScaleX = scaleX
                                gestureScaleY = scaleY
                            } else if (active.size == 1 && !twoFingers) {
                                val change = active.first()
                                dragPx += change.position - change.previousPosition
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } while (event.changes.any { it.pressed })
                        commitGesture(dragPx, scaleX, scaleY)
                    }
                }
        ) {
            EmbroideryCanvas(
                design = backgroundDesign,
                hoop = referenceHoop,
                interactive = false,
                displayMode = EmbroideryDisplayMode.SOLID,
                modifier = Modifier.fillMaxSize()
            )
            // Camada com os pontos Satin reais; o enquadramento da moldura
            // não muda entre repouso, arraste e atualização da prévia.
            if (!previewBusy && preview != null) {
                val stitchedCenter = NameEditorPlacement.screenOffset(
                    backgroundDesign, referenceHoop, draft.x, draft.y,
                    canvasSize.width, canvasSize.height, paddingPx
                )
                NameEditorStitchLayer(
                    original = backgroundDesign,
                    result = preview,
                    hoop = referenceHoop,
                    modifier = Modifier.graphicsLayer {
                        transformOrigin = TransformOrigin(
                            (.5f + stitchedCenter.x /
                                canvasSize.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                            (.5f + stitchedCenter.y /
                                canvasSize.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                        )
                        scaleX = gestureScaleX
                        scaleY = gestureScaleY
                        translationX = dragPx.x
                        translationY = dragPx.y
                    }
                )
            }

            // Alça alinhada com o mesmo centro, escala e orientação de
            // EmbroideryCanvas. Somente o nome responde ao arraste.
            val namePosition = NameEditorPlacement.screenOffset(
                design = backgroundDesign, hoop = referenceHoop,
                xUnits = draft.x, yUnits = draft.y,
                canvasWidth = canvasSize.width,
                canvasHeight = canvasSize.height,
                paddingPixels = paddingPx
            )
            val nameX = namePosition.x + dragPx.x
            val nameY = namePosition.y + dragPx.y
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset {
                        IntOffset(nameX.roundToInt(), nameY.roundToInt())
                    }
                    // Alvo acessível sem o antigo marcador escuro.
                    .defaultMinSize(minWidth = 112.dp, minHeight = 50.dp)
                    .testTag("brother_name_drag_handle"),
                contentAlignment = Alignment.Center
            ) {
                // Não desenhar o nome com a fonte genérica do Android
                // quando o Satin real falhou: aquela imagem NÃO representa
                // a fonte Adamya/TTF selecionada nem é uma matriz segura.
                // A área de toque permanece disponível; o erro aparece abaixo.

            }
        }

        Text("Um dedo: mover • Dois dedos: expandir lateralmente e para cima.",
            color = FioTextMuted, fontSize = 11.sp)

        when {
            (fitting || autoFitEnabled) -> Text(
                "Encaixando nome automaticamente na área livre…",
                color = FioTextMuted, fontSize = 12.sp
            )
            previewBusy -> Text(
                "Atualizando prévia de pontadas sem travar a tela…",
                color = FioTextMuted, fontSize = 12.sp
            )
            preview == null -> Text(
                autoFitError ?: currentError ?: "Prévia de pontadas indisponível. Use o ajuste automático.",
                color = Color(0xFFFF9393), fontSize = 12.sp
            )
            else -> Text(
                "Pontadas calculadas: ${preview.stitchCount} pontos • " +
                    "${preview.bounds.widthMm} × ${preview.bounds.heightMm} mm",
                color = FioTextMuted, fontSize = 12.sp
            )
        }

        if (original.editableName != null) {
            OutlinedButton(
                onClick = {
                    autoFitEnabled = false
                    NamePersonalization.invalidatePreparedPreview()
                    computed = null
                    reprocessResult = "Recalculando pontadas com o motor de fontes atualizado…"
                    reprocessRequest++
                },
                modifier = Modifier.fillMaxWidth()
                    .testTag("brother_name_reprocess_glyphs")
            ) {
                Text("↻ Reprocessar pontadas da fonte sem mudar o encaixe")
            }
            reprocessResult?.let { message ->
                Text(message, color = FioTextMuted, fontSize = 12.sp)
            }
        }

        OutlinedButton(
            onClick = {
                autoFitError = null
                autoFitRequest++
                autoFitEnabled = true
            },
            enabled = !fitting,
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
                        text = {
                            val duplicateName = importedFonts.count {
                                it.displayName.equals(font.displayName, ignoreCase = true)
                            } > 1
                            Text("TTF/OTF • " + font.displayName +
                                if (duplicateName) " (" + font.fileName + ")" else "")
                        },
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
        Text("Largura: " + (draft.widthScale * 100).roundToInt() +
            "%", color = FioText)
        Slider(
            value = draft.widthScale,
            onValueChange = {
                autoFitEnabled = false
                setDraft(draft.copy(widthScale = it), history = false)
            },
            valueRange = .5f..2.5f,
            modifier = Modifier.testTag("brother_name_width_scale")
        )
        Text("Expansão vertical: " + (draft.heightScale * 100).roundToInt() +
            "%", color = FioText)
        Slider(
            value = draft.heightScale,
            onValueChange = {
                autoFitEnabled = false
                setDraft(draft.copy(heightScale = it), history = false)
            },
            valueRange = .5f..2.5f,
            modifier = Modifier.testTag("brother_name_height_scale")
        )
        Text("Expansão horizontal: " +
            (draft.horizontalGlyphScale * 100f).roundToInt() + "%",
            color = FioText)
        Text("Ajusta a largura de cada letra, sem alterar o espaçamento da palavra.",
            color = FioTextMuted, fontSize = 11.sp)
        Slider(
            value = draft.horizontalGlyphScale,
            onValueChange = {
                autoFitEnabled = false
                setDraft(draft.copy(horizontalGlyphScale = it), history = false)
            },
            valueRange = .7f..1.4f,
            modifier = Modifier.testTag("brother_name_horizontal_expansion")
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
