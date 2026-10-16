package com.timachado.brothermatrizes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.embroidery.EmbroideryBounds
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.PreparedRasterPhoto
import com.timachado.brothermatrizes.core.embroidery.RasterArtworkAutoCrop
import com.timachado.brothermatrizes.core.embroidery.RasterArtworkCandidate
import com.timachado.brothermatrizes.core.embroidery.RasterArtworkCandidates
import com.timachado.brothermatrizes.core.embroidery.RasterCropBounds
import com.timachado.brothermatrizes.core.embroidery.RasterImageDecoder
import com.timachado.brothermatrizes.core.embroidery.RasterImagePixels
import com.timachado.brothermatrizes.core.embroidery.RasterGeometryAwareSatinPreview
import com.timachado.brothermatrizes.core.embroidery.RasterSatinObject
import com.timachado.brothermatrizes.core.embroidery.RasterSatinObjectEditor
import com.timachado.brothermatrizes.core.embroidery.RasterAutoSatinRails
import com.timachado.brothermatrizes.core.embroidery.ManualSatinSection
import com.timachado.brothermatrizes.core.embroidery.RasterPhotoPreparation
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Preserves the image and selected hoop when navigating back from the
 * real SimulatorScreen. Never persists an experimental raster as PES.
 */
class SimpleImageSession {
    val photoState = mutableStateOf<RasterImagePixels?>(null)
    val candidatesState = mutableStateOf<List<RasterArtworkCandidate>>(emptyList())
    val selectedIdState = mutableIntStateOf(0)
    val cropState = mutableStateOf(RasterCropBounds(0f, 0f, 1f, 1f))
    val preparedState = mutableStateOf<PreparedRasterPhoto?>(null)
    val revisionState = mutableIntStateOf(0)
    val hoopState = mutableStateOf(HoopProfile.H100X100)
    /** Fraction of available working area, independent of current hoop. */
    val fillState = mutableStateOf(.92f)
    val displayModeState = mutableStateOf(EmbroideryDisplayMode.REALISTIC)
    val showConnectionsState = mutableStateOf(false)
    // Non-exportable Satin draft belongs to the selected photo/crop.
    // Kept across a trip to SimulatorScreen; never attached to a PES.
    val satinEditorOpenState = mutableStateOf(false)
    val satinPartsState = mutableStateOf<List<RasterSatinObject>>(emptyList())
    val satinPartIndexState = mutableIntStateOf(0)
    val satinSectionsState = mutableStateOf<Map<Int, List<ManualSatinSection>>>(emptyMap())
    val satinTouchedState = mutableStateOf<Set<Int>>(emptySet())
    val satinSectionIndexState = mutableIntStateOf(0)
    var pickerLaunched = false
}

private fun imageStagePlaceholder(
    photo: RasterImagePixels,
    hoop: HoopProfile,
    widthMm: Float
): EmbroideryDesign {
    val heightMm = ImageHoopSizing.heightMm(photo.width, photo.height, widthMm)
    val halfW = (widthMm * 5f).roundToInt()
    val halfH = (heightMm * 5f).roundToInt()
    // Empty! The original EmbroideryCanvas draws the real original hoop,
    // while its new optional overlay draws IMAGE pixels, not stitch commands.
    return EmbroideryDesign(
        fileName = "preview-arte-sem-matriz",
        format = "QA_ARTWORK",
        label = "Imagem preparada • não digitalizada",
        points = emptyList(),
        bounds = EmbroideryBounds(-halfW, halfW, -halfH, halfH),
        stitchCount = 0, jumpCount = 0, colorChanges = 0,
        endFound = false, sourceBytes = ByteArray(0), hoopProfile = hoop
    )
}

@Composable
fun SimpleImageToEmbroideryScreen(
    onBack: () -> Unit,
    onSimulate: (EmbroideryDesign, HoopProfile, EmbroideryDisplayMode, Boolean) -> Unit,
    session: SimpleImageSession
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photo by session.photoState
    var candidates by session.candidatesState
    var selectedId by session.selectedIdState
    var crop by session.cropState
    var prepared by session.preparedState
    var revision by session.revisionState
    var hoop by session.hoopState
    var fill by session.fillState
    var displayMode by session.displayModeState
    var showConnections by session.showConnectionsState
    var satinEditorOpen by session.satinEditorOpenState
    var satinParts by session.satinPartsState
    var satinPartIndex by session.satinPartIndexState
    var satinSections by session.satinSectionsState
    var satinTouched by session.satinTouchedState
    var satinSectionIndex by session.satinSectionIndexState

    var choosing by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var simulating by remember { mutableStateOf(false) }
    var showHoops by remember { mutableStateOf(false) }
    var showDisplay by remember { mutableStateOf(false) }
    var showCrop by remember { mutableStateOf(false) }
    var proposingRails by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            revision++
            val request = revision
            photo = null
            candidates = emptyList()
            prepared = null
            crop = RasterCropBounds(0f, 0f, 1f, 1f)
            selectedId = 0
            satinEditorOpen = false
            satinParts = emptyList()
            satinSections = emptyMap()
            satinTouched = emptySet()
            satinPartIndex = 0
            satinSectionIndex = 0
            showCrop = false
            choosing = true
            message = ""
            scope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    RasterImageDecoder.decode(context.contentResolver, uri)
                }
                val image = loaded.getOrNull()
                val matches = image?.let {
                    withContext(Dispatchers.Default) { RasterArtworkCandidates.detect(it) }
                } ?: emptyList()
                val detectedBounds = image?.let {
                    withContext(Dispatchers.Default) { RasterArtworkAutoCrop.detect(it) }
                }
                if (revision == request) {
                    photo = image
                    candidates = matches
                    crop = detectedBounds ?: RasterCropBounds(0f, 0f, 1f, 1f)
                    choosing = false
                    message = loaded.exceptionOrNull()?.message.orEmpty()
                }
            }
        }
    }

    LaunchedEffect(session) {
        if (!session.pickerLaunched) {
            session.pickerLaunched = true
            picker.launch(arrayOf("image/*"))
        }
    }

    // No "Preparar imagem" intermediate page. Every import/selection change
    // automatically prepares the image for immediate original-hoop display.
    LaunchedEffect(photo, crop, revision) {
        val input = photo ?: return@LaunchedEffect
        // Preserve the validated source/crop and manual rails when returning
        // from the ORIGINAL simulator; choose()/picker invalidate explicitly.
        if (prepared != null) return@LaunchedEffect
        preparing = true
        prepared = null
        message = ""
        try {
            val newArt = withContext(Dispatchers.Default) {
                val hasAlpha = input.argb.any { (it ushr 24) < 128 }
                RasterPhotoPreparation.prepare(
                    original = input,
                    bounds = crop,
                    removeEdgeBackground = !hasAlpha,
                    tolerance = 55
                )
            }
            newArt.fold(
                onSuccess = { prepared = it },
                onFailure = { message = it.message ?: "Não foi possível separar a arte." }
            )
        } finally {
            preparing = false
        }
    }

    fun choose(part: RasterCropBounds, id: Int) {
        revision++
        selectedId = id
        prepared = null
        satinEditorOpen = false
        satinParts = emptyList()
        satinSections = emptyMap()
        satinTouched = emptySet()
        satinPartIndex = 0
        satinSectionIndex = 0
        crop = part
        message = ""
    }

    val stageImage = prepared?.image ?: photo
    val maxWidth = stageImage?.let {
        ImageHoopSizing.maximumWidthMm(it.width, it.height, hoop)
    } ?: 0f
    val widthMm = maxWidth * fill
    val heightMm = stageImage?.let {
        ImageHoopSizing.heightMm(it.width, it.height, widthMm.coerceAtLeast(.1f))
    } ?: 0f
    val fitted = stageImage != null &&
        widthMm >= 10f &&
        ImageHoopSizing.fits(stageImage.width, stageImage.height, widthMm, hoop)
    val placeholder = remember(stageImage, hoop, widthMm) {
        stageImage?.let { imageStagePlaceholder(it, hoop, widthMm.coerceAtLeast(.1f)) }
    }

    fun selectSatinPart(index: Int) {
        val image = prepared?.image ?: return
        val item = satinParts.getOrNull(index) ?: return
        if (satinSections[item.id] == null) {
            val initial = RasterSatinObjectEditor.suggest(image, item, widthMm)
            if (initial.isFailure) {
                message = initial.exceptionOrNull()?.message ?: "Não foi possível iniciar esta parte."
                return
            }
            satinSections = satinSections + (item.id to listOf(initial.getOrThrow()))
        }
        satinPartIndex = index
        satinSectionIndex = 0
        message = ""
    }

    if (showHoops) {
        ImageHoopPicker(
            selected = hoop,
            onChoose = { hoop = it; showHoops = false; message = "" },
            onDismiss = { showHoops = false }
        )
    }
    if (showDisplay) {
        DisplaySettingsSheet(
            displayMode = displayMode,
            onDisplayModeChange = { displayMode = it },
            hoop = hoop,
            onHoopChange = { hoop = it },
            showConnections = showConnections,
            onShowConnectionsChange = { showConnections = it },
            onDismiss = { showDisplay = false }
        )
    }

    Column(
        Modifier.fillMaxSize().background(FioBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text("‹ Voltar", color = FioGold) }
            Column {
                Text("Imagem para Bordado", color = FioText, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold)
                Text("Escolha a imagem e confira no bastidor", color = FioTextMuted, fontSize = 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(modifier = Modifier.weight(1f), onClick = { showHoops = true }) {
                Text(hoop.displayName + " ▾", color = FioGold, fontSize = 12.sp)
            }
            OutlinedButton(onClick = { showDisplay = true }) {
                Text("Exibição", color = FioGold, fontSize = 12.sp)
            }
        }

        if (stageImage != null && placeholder != null) {
            // The SAME EmbroideryCanvas as Abrir Matrizes, with a graphic
            // overlay. Empty design: no fake stitch or PES is fabricated.
            EmbroideryCanvas(
                design = placeholder,
                hoop = hoop,
                displayMode = displayMode,
                showConnections = showConnections,
                rasterArtwork = stageImage,
                rasterWidthMm = widthMm,
                modifier = Modifier.fillMaxWidth().height(350.dp)
            )
            Text(
                "Prévia da imagem no bastidor (ainda sem pontos de bordado)",
                color = FioTextMuted, fontSize = 11.sp
            )

            Text(
                "Arte: ${widthMm.roundToInt()} × ${heightMm.roundToInt()} mm • " +
                    "Bastidor: ${hoop.displayName}",
                color = FioText, fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = fill.coerceIn(.2f, 1f),
                onValueChange = { fill = it },
                valueRange = .2f..1f
            )
            Text(
                "Deslize para ajustar a arte no bastidor; ao trocar de bastidor, " +
                    "o tamanho é recalculado automaticamente.",
                color = FioTextMuted, fontSize = 11.sp
            )
            if (candidates.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(onClick = { choose(RasterCropBounds(0f, 0f, 1f, 1f), 0) }) {
                        Text(if (selectedId == 0) "● Logo inteiro" else "Logo inteiro",
                            color = FioGold)
                    }
                    candidates.forEach { region ->
                        OutlinedButton(onClick = { choose(region.bounds, region.id) }) {
                            Text(
                                if (selectedId == region.id) "● Parte ${region.id}"
                                else "Parte ${region.id}",
                                color = FioGold
                            )
                        }
                    }
                }
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showCrop = !showCrop }
            ) {
                Text(if (showCrop) "Concluir recorte" else "Ajustar recorte", color = FioGold)
            }
            if (showCrop) {
                RasterPhotoCropCanvas(
                    photo = photo ?: stageImage, selection = crop,
                    candidates = candidates,
                    onSelection = { choose(it, 0) },
                    onCandidateTap = { id ->
                        candidates.firstOrNull { it.id == id }?.let {
                            choose(it.bounds, id)
                        }
                    },
                    canvasHeight = 250.dp
                )
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !preparing && !simulating && prepared != null,
                onClick = {
                    if (satinEditorOpen) {
                        satinEditorOpen = false
                    } else if (satinParts.isNotEmpty()) {
                        // Reopen without erasing rail edits made for this photo.
                        satinEditorOpen = true
                        selectSatinPart(satinPartIndex)
                    } else {
                        val image = prepared?.image ?: return@OutlinedButton
                        val revisionAtStart = revision
                        preparing = true
                        message = ""
                        scope.launch {
                            val result = withContext(Dispatchers.Default) {
                                RasterSatinObjectEditor.detect(image)
                            }
                            if (revisionAtStart == revision) {
                                preparing = false
                                result.fold(
                                    onSuccess = { pieces ->
                                        satinParts = pieces
                                        satinSections = emptyMap()
                                        satinTouched = emptySet()
                                        satinPartIndex = 0
                                        satinSectionIndex = 0
                                        satinEditorOpen = true
                                        selectSatinPart(0)
                                    },
                                    onFailure = {
                                        message = it.message ?: "Não foi possível separar os elementos."
                                    }
                                )
                            }
                        }
                    }
                }
            ) {
                Text(
                    if (satinEditorOpen) "Fechar edição Satin"
                    else "Reconstruir contornos Satin",
                    color = FioGold
                )
            }
            if (satinEditorOpen) {
                val activePart = satinParts.getOrNull(satinPartIndex)
                if (activePart != null) {
                    val activeSections = satinSections[activePart.id].orEmpty()
                    ImageSatinObjectEditorPanel(
                        objects = satinParts,
                        active = satinPartIndex,
                        sections = activeSections,
                        sectionIndex = satinSectionIndex,
                        imageWidth = prepared?.image?.width ?: stageImage.width,
                        reviewedCount = satinTouched.size,
                        touched = activePart.id in satinTouched,
                        onPart = { selectSatinPart(it) },
                        onSection = { satinSectionIndex = it },
                        onChange = { changed ->
                            satinSections = satinSections + (activePart.id to changed)
                            satinTouched = satinTouched + activePart.id
                        },
                        autoBusy = proposingRails,
                        onAuto = {
                            val picture = prepared?.image
                            if (picture == null || proposingRails) {
                                message = "Abra uma imagem antes de sugerir os trilhos."
                            } else {
                                val stamp = revision
                                val partId = activePart.id
                                val currentWidth = widthMm
                                proposingRails = true
                                message = ""
                                scope.launch {
                                    val proposal = withContext(Dispatchers.Default) {
                                        RasterAutoSatinRails.propose(
                                            picture, activePart, currentWidth
                                        )
                                    }
                                    proposingRails = false
                                    if (stamp == revision &&
                                        satinParts.getOrNull(satinPartIndex)?.id == partId
                                    ) {
                                        proposal.fold(
                                            onSuccess = { guide ->
                                                satinSections = satinSections +
                                                    (partId to listOf(guide))
                                                satinTouched = satinTouched - partId
                                                satinSectionIndex = 0
                                                message = "Sugestão criada pelo contorno. " +
                                                    "Confira e ajuste os trilhos antes de simular."
                                            },
                                            onFailure = {
                                                message = it.message ?:
                                                    "Este objeto exige edição manual de Satin."
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        onReset = {
                            prepared?.image?.let { image ->
                                RasterSatinObjectEditor.suggest(
                                    image, activePart, widthMm
                                ).onSuccess { initial ->
                                    satinSections = satinSections +
                                        (activePart.id to listOf(initial))
                                    satinTouched = satinTouched - activePart.id
                                    satinSectionIndex = 0
                                }.onFailure {
                                    message = it.message ?: "Não foi possível reiniciar os trilhos."
                                }
                            }
                        }
                    )
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !preparing && !simulating && fitted && prepared != null,
                colors = ButtonDefaults.buttonColors(containerColor = FioGold),
                onClick = {
                    val selected = prepared?.image ?: return@Button
                    val selectedHoop = hoop
                    val selectedWidth = widthMm
                    val chosenMode = displayMode
                    val chosenConnections = showConnections
                    val currentRevision = revision
                    // Snapshot rail edits: the background operation must not
                    // mix draft changes or silently switch to raster filling.
                    val selectedPart = if (satinEditorOpen) {
                        satinParts.getOrNull(satinPartIndex)
                    } else null
                    val selectedSections = selectedPart?.let {
                        satinSections[it.id].orEmpty()
                    }.orEmpty()
                    val isReviewed = selectedPart?.id in satinTouched
                    val editing = satinEditorOpen
                    simulating = true
                    message = ""
                    scope.launch {
                        val design: Result<EmbroideryDesign> =
                            withContext(Dispatchers.Default) {
                                if (editing) {
                                    if (selectedPart == null || !isReviewed ||
                                        selectedSections.isEmpty()
                                    ) {
                                        Result.failure(
                                            IllegalArgumentException(
                                                "Ajuste os trilhos desta parte antes de simular."
                                            )
                                        )
                                    } else {
                                        RasterSatinObjectEditor.preview(
                                            selected, selectedPart, selectedSections,
                                            selectedWidth, selectedHoop
                                        )
                                    }
                                } else {
                                    RasterGeometryAwareSatinPreview.create(
                                        selected, selectedWidth, selectedHoop
                                    )
                                }
                            }
                        if (revision == currentRevision) {
                            simulating = false
                            design.fold(
                                onSuccess = {
                                    onSimulate(it, selectedHoop, chosenMode, chosenConnections)
                                },
                                onFailure = {
                                    message = "Simulação não liberada: " +
                                        (design.exceptionOrNull()?.message ?:
                                            "Não foi possível preservar o desenho original.")
                                }
                            )
                        }
                    }
                }
            ) {
                Text("▶ Simular bordado", color = FioBackground,
                    fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { picker.launch(arrayOf("image/*")) }
            ) { Text("Escolher outra imagem", color = FioGold) }
            Text(
                "Simular bordado reproduz os pontos da parte em edição, não o logo " +
                    "completo. O editor exige ajuste dos trilhos e valida cruzamentos " +
                    "de vazios. PES e envio à máquina continuam bloqueados.",
                color = FioTextMuted, fontSize = 11.sp
            )
        } else {
            Text("Abra uma imagem da galeria ou dos arquivos do celular.",
                color = FioText)
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { picker.launch(arrayOf("image/*")) },
                colors = ButtonDefaults.buttonColors(containerColor = FioGold)
            ) { Text("Abrir imagem", color = FioBackground) }
        }
        if (choosing || preparing || simulating) CircularProgressIndicator(color = FioGold)
        if (message.isNotEmpty()) Text(message, color = Color(0xFFFFBE91), fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
    }
}
