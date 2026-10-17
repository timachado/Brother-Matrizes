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
import com.timachado.brothermatrizes.core.embroidery.RasterImageSamplingEvidence
import com.timachado.brothermatrizes.core.embroidery.RasterImageSamplingAudit
import com.timachado.brothermatrizes.core.embroidery.RasterImagePixels
import com.timachado.brothermatrizes.core.embroidery.RasterGeometryAwareSatinPreview
import com.timachado.brothermatrizes.core.embroidery.RasterAutomaticLogoPreview
import com.timachado.brothermatrizes.core.embroidery.RasterSatinObject
import com.timachado.brothermatrizes.core.embroidery.RasterSatinObjectEditor
import com.timachado.brothermatrizes.core.embroidery.RasterAutoSatinRails
import com.timachado.brothermatrizes.core.embroidery.RasterNeedleRiskAudit
import com.timachado.brothermatrizes.core.embroidery.RasterQaReadinessReportBuilder
import com.timachado.brothermatrizes.core.embroidery.RasterThreadDensityPreflight
import com.timachado.brothermatrizes.core.embroidery.RasterPesTargetCompatibility
import com.timachado.brothermatrizes.core.embroidery.RasterArtworkComplexityAudit
import com.timachado.brothermatrizes.core.embroidery.RasterTechniqueAdvisor
import com.timachado.brothermatrizes.core.embroidery.RasterTechniqueReport
import com.timachado.brothermatrizes.core.embroidery.RasterSatinTopologyPlanner
import com.timachado.brothermatrizes.core.embroidery.ManualSatinSection
import com.timachado.brothermatrizes.core.embroidery.RasterPhotoPreparation
import com.timachado.brothermatrizes.core.embroidery.RasterThreadTextureStudy
import com.timachado.brothermatrizes.core.embroidery.RasterThreadTextureStudyReport
import com.timachado.brothermatrizes.core.embroidery.RasterFlatColorStitchCandidate
import com.timachado.brothermatrizes.core.embroidery.RasterAssistedElementPlanner
import com.timachado.brothermatrizes.core.embroidery.RasterAssistedElementPlan
import com.timachado.brothermatrizes.core.embroidery.RasterAssistedSatinDraftPlanner
import com.timachado.brothermatrizes.core.embroidery.RasterSatinRailGeometryGate
import com.timachado.brothermatrizes.core.embroidery.RasterStitchFidelityAudit
import com.timachado.brothermatrizes.core.embroidery.RasterUncoveredRegionAudit
import com.timachado.brothermatrizes.core.embroidery.RasterUncoveredRegionReport
import com.timachado.brothermatrizes.core.embroidery.RasterUncoveredReviewOverlay
import com.timachado.brothermatrizes.core.embroidery.RasterGapReviewRoute
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
    internal val samplingEvidenceState = mutableStateOf<RasterImageSamplingEvidence?>(null)
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
    internal val techniqueReportState = mutableStateOf<RasterTechniqueReport?>(null)
    internal val qaTechnicalReportState = mutableStateOf<String?>(null)
    internal val qaReportRevisionState = mutableIntStateOf(-1)
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
    var samplingEvidence by session.samplingEvidenceState
    var scopedSamplingReview by remember { mutableStateOf<Pair<RasterCropBounds, RasterImageSamplingEvidence>?>(null) }
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
    var techniqueReport by session.techniqueReportState
    var qaTechnicalReport by session.qaTechnicalReportState
    var qaReportRevision by session.qaReportRevisionState
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
    var analyzingTechniques by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            revision++
            val request = revision
            photo = null
            samplingEvidence = null
            candidates = emptyList()
            prepared = null
            crop = RasterCropBounds(0f, 0f, 1f, 1f)
            selectedId = 0
            satinEditorOpen = false
            techniqueReport = null
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
                    RasterImageDecoder.decodeWithSamplingEvidence(context.contentResolver, uri)
                }
                val image = loaded.getOrNull()?.image
                val matches = image?.let {
                    withContext(Dispatchers.Default) { RasterArtworkCandidates.detect(it) }
                } ?: emptyList()
                val detectedBounds = image?.let {
                    withContext(Dispatchers.Default) { RasterArtworkAutoCrop.detect(it) }
                }
                if (revision == request) {
                    photo = image
                    samplingEvidence = loaded.getOrNull()?.evidence
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

    // Evaluate detail loss for the actual cropped REGION. A lost letter
    // elsewhere in the full logo must not block reviewing an unrelated part.
    LaunchedEffect(samplingEvidence, crop) {
        scopedSamplingReview = null
        val original = samplingEvidence ?: return@LaunchedEffect
        val selectedCrop = crop
        val scoped = withContext(Dispatchers.Default) {
            RasterImageSamplingAudit.inspectRegion(original, selectedCrop)
        }
        scoped.getOrNull()?.let { scopedSamplingReview = selectedCrop to it }
    }
    val regionalSampling = scopedSamplingReview?.takeIf {
        it.first == crop && it.second.sha256 == samplingEvidence?.sha256
    }?.second

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
        techniqueReport = null
        satinParts = emptyList()
        satinSections = emptyMap()
        satinTouched = emptySet()
        satinPartIndex = 0
        satinSectionIndex = 0
        crop = part
        message = ""
    }

    val stageImage = prepared?.image ?: photo
    // Visual comparison only. Never pass flattened preview pixels to the
    // stitch composer, source-fidelity audits, or a PES encoder.
    var flatStudy by remember(stageImage) {
        mutableStateOf<RasterThreadTextureStudyReport?>(null)
    }
    var showFlatStudy by remember(stageImage) { mutableStateOf(false) }
    var studyingTexture by remember(stageImage) { mutableStateOf(false) }
    var testingFlatStitches by remember(stageImage) { mutableStateOf(false) }
    var elementPlan by remember(stageImage) {
        mutableStateOf<RasterAssistedElementPlan?>(null)
    }
    var analyzingElements by remember(stageImage) { mutableStateOf(false) }
    var selectedElementId by remember(stageImage) { mutableIntStateOf(0) }
    var assistedSatinElementId by remember(stageImage) { mutableIntStateOf(0) }
    // These are comparison masks only; none is passed to a stitch generator.
    val elementPicture = remember(elementPlan, selectedElementId) {
        if (selectedElementId == 0) null else
            elementPlan?.isolate(selectedElementId)?.getOrNull()
    }
    val artworkAudit = remember(stageImage) {
        stageImage?.let { RasterArtworkComplexityAudit.examine(it) }
    }
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
    var reviewingGaps by remember(stageImage, satinSections, satinPartIndex, widthMm, hoop) {
        mutableStateOf(false)
    }
    var uncoveredReport by remember(stageImage, satinSections, satinPartIndex, widthMm, hoop) {
        mutableStateOf<RasterUncoveredRegionReport?>(null)
    }
    var showGapOverlay by remember(stageImage) { mutableStateOf(false) }
    var selectedGapId by remember(stageImage) { mutableIntStateOf(0) }
    val gapOverlay = remember(
        uncoveredReport, showGapOverlay, selectedGapId,
        stageImage, satinParts, satinPartIndex
    ) {
        val source = prepared?.image
        val item = satinParts.getOrNull(satinPartIndex)
        val report = uncoveredReport
        if (!showGapOverlay || source == null || item == null || report == null ||
            assistedSatinElementId != item.id
        ) null else RasterUncoveredReviewOverlay.render(
            source, item, report, selectedGapId
        ).getOrNull()
    }
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
            onChoose = {
                hoop = it
                techniqueReport = null
                showHoops = false
                message = ""
            },
            onDismiss = { showHoops = false }
        )
    }
    if (showDisplay) {
        DisplaySettingsSheet(
            displayMode = displayMode,
            onDisplayModeChange = { displayMode = it },
            hoop = hoop,
            onHoopChange = { hoop = it; techniqueReport = null },
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
                rasterArtwork = gapOverlay ?: elementPicture ?: if (showFlatStudy)
                    flatStudy?.flatPreview ?: stageImage else stageImage,
                rasterWidthMm = widthMm,
                modifier = Modifier.fillMaxWidth().height(350.dp)
            )
            Text(
                if (gapOverlay != null)
                    "Cobertura real QA: laranja = faltam pontos; cinza = já coberto. " +
                        "Vazados transparentes preservados. Sem novas agulhadas."
                else if (elementPicture != null)
                    "Máscara isolada para revisão: NÃO é uma matriz nem ponto Satin"
                else if (showFlatStudy && flatStudy != null)
                    "Prévia técnica em cores planas (sem pontos de bordado)"
                else "Prévia da imagem no bastidor (ainda sem pontos de bordado)",
                color = FioTextMuted, fontSize = 11.sp
            )
            regionalSampling?.let { evidence ->
                Text(
                    "Origem: ${evidence.sourceWidth} × ${evidence.sourceHeight} px → " +
                        "prévia: ${evidence.workingWidth} × ${evidence.workingHeight} px. " +
                        "Comparação: ${evidence.referenceWidth} × ${evidence.referenceHeight} px.",
                    color = FioTextMuted, fontSize = 11.sp
                )
                if (evidence.needsDetailReview) {
                    Text(
                        "Foram detectadas diferenças na redução do ${evidence.inspectedRegion}. " +
                            "A textura e os brilhos do bordado ilustrado não são " +
                            "trajetórias de agulha: revise cores, letras e contornos " +
                            "antes da digitalização automática.",
                        color = FioGold, fontSize = 12.sp
                    )
                }
            }
            if (artworkAudit?.requiresMultiTechniqueReview == true) {
                Text(
                    "Arte complexa: tons e detalhes exigem separar letras, " +
                        "contornos e áreas de preenchimento. A imagem original " +
                        "pode aparecer no bastidor, mas não é uma matriz PES. " +
                        "A simulação do logo inteiro fica bloqueada até " +
                        "existirem pontos reais validados por elemento.",
                    color = FioGold, fontSize = 12.sp
                )
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = prepared != null && !preparing &&
                    !simulating && !studyingTexture,
                onClick = {
                    if (flatStudy != null) {
                        selectedElementId = 0
                        showFlatStudy = !showFlatStudy
                    } else {
                        val picture = prepared?.image
                        if (picture == null) {
                            message = "Importe uma imagem para avaliar a textura."
                        } else {
                            studyingTexture = true
                            scope.launch {
                                val result = withContext(Dispatchers.Default) {
                                    RasterThreadTextureStudy.inspect(picture)
                                }
                                if (prepared?.image === picture) {
                                    result.fold(
                                        onSuccess = {
                                            flatStudy = it
                                            showFlatStudy = true
                                            message = ""
                                        },
                                        onFailure = {
                                            message = it.message ?:
                                                "Não foi possível estudar as cores da imagem."
                                        }
                                    )
                                }
                                studyingTexture = false
                            }
                        }
                    }
                }
            ) {
                Text(
                    when {
                        studyingTexture -> "Analisando textura e cores..."
                        flatStudy == null -> "Visualizar cores planas (QA)"
                        showFlatStudy -> "Ver imagem original"
                        else -> "Comparar cores planas (QA)"
                    },
                    color = FioGold
                )
            }
            flatStudy?.let { study ->
                Text(
                    study.message(),
                    color = FioTextMuted,
                    fontSize = 11.sp
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = prepared != null && !preparing &&
                        !analyzingElements && !studyingTexture,
                    onClick = {
                        if (elementPlan != null) {
                            selectedElementId = 0
                            elementPlan = null
                        } else {
                            val photoSnapshot = prepared?.image
                            if (photoSnapshot == null) {
                                message = "A foto precisa estar preparada para separar elementos."
                            } else {
                                val request = revision
                                analyzingElements = true
                                message = ""
                                scope.launch {
                                    val planned = withContext(Dispatchers.Default) {
                                        RasterAssistedElementPlanner.propose(
                                            photoSnapshot, study
                                        )
                                    }
                                    analyzingElements = false
                                    if (request == revision &&
                                        prepared?.image === photoSnapshot &&
                                        flatStudy === study) {
                                        planned.fold(
                                            onSuccess = {
                                                elementPlan = it
                                                selectedElementId = 0
                                                message = ""
                                            },
                                            onFailure = {
                                                message = it.message ?:
                                                    "A separação exige revisão manual."
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        when {
                            analyzingElements -> "Separando silhuetas e vazados..."
                            elementPlan != null -> "Ocultar elementos detectados"
                            else -> "Separar símbolo, letras e vazados (QA)"
                        },
                        color = FioGold
                    )
                }
                elementPlan?.let { plan ->
                    Text(plan.summary(), color = FioTextMuted, fontSize = 11.sp)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(onClick = { selectedElementId = 0 }) {
                            Text("Arte completa", color = FioGold)
                        }
                        plan.elements.take(12).forEach { element ->
                            OutlinedButton(onClick = {
                                selectedElementId = element.id
                                showFlatStudy = false
                            }) {
                                Text(
                                    "Elemento " + element.id,
                                    color = FioGold
                                )
                            }
                        }
                    }
                    plan.elements.firstOrNull {
                        it.id == selectedElementId
                    }?.let { element ->
                        Text(
                            "Elemento " + element.id + ": " +
                                element.pixelCount + " pixels, " +
                                element.enclosedHoles + " vazado(s) interno(s). " +
                                "A máscara é um guia visual, não pontos costurados.",
                            color = FioTextMuted, fontSize = 11.sp
                        )
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = prepared != null && fitted &&
                                !preparing && !simulating && !proposingRails,
                            onClick = {
                                val originalSnapshot = prepared?.image
                                    ?: return@OutlinedButton
                                val planned = elementPlan ?: return@OutlinedButton
                                val widthSnapshot = widthMm
                                val hoopSnapshot = hoop
                                val requested = revision
                                proposingRails = true
                                message = ""
                                scope.launch {
                                    val proposal = withContext(Dispatchers.Default) {
                                        RasterAssistedSatinDraftPlanner.prepare(
                                            originalSnapshot, planned, element.id,
                                            widthSnapshot, hoopSnapshot
                                        )
                                    }
                                    proposingRails = false
                                    if (requested == revision &&
                                        prepared?.image === originalSnapshot &&
                                        elementPlan === planned &&
                                        selectedElementId == element.id &&
                                        widthMm == widthSnapshot &&
                                        hoop == hoopSnapshot) {
                                        proposal.fold(
                                            onSuccess = { draft ->
                                                satinParts = listOf(draft.item)
                                                satinSections = mapOf(
                                                    draft.item.id to draft.sections
                                                )
                                                satinTouched = emptySet()
                                                satinPartIndex = 0
                                                satinSectionIndex = 0
                                                assistedSatinElementId = element.id
                                                satinEditorOpen = true
                                                message =
                                                    "Trilhos Satin propostos para o elemento " +
                                                    element.id + ", com " +
                                                    draft.counterCount +
                                                    " contraforma(s). Edite os trilhos " +
                                                    "manualmente antes da prévia; " +
                                                    "a exportação PES continua bloqueada."
                                            },
                                            onFailure = {
                                                message = it.message ?:
                                                    "A letra exige desenho Satin " +
                                                        "assistido adicional."
                                            }
                                        )
                                    }
                                }
                            }
                        ) {
                            Text(
                                if (proposingRails)
                                    "Verificando os trilhos desta parte..."
                                else "Preparar edição Satin do elemento " + element.id,
                                color = FioGold
                            )
                        }
                    }
                    if (plan.elements.size > 12) {
                        Text(
                            "Exibidos 12 de " + plan.elements.size +
                                " elementos selecionáveis. Os demais " +
                                "continuam no relatório, sem descarte.",
                            color = FioTextMuted, fontSize = 11.sp
                        )
                    }
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = prepared != null && fitted && !preparing &&
                        !simulating && !testingFlatStitches &&
                        !studyingTexture && regionalSampling != null,
                    onClick = {
                        val picture = prepared?.image ?: return@OutlinedButton
                        val chosenHoop = hoop
                        val chosenWidth = widthMm
                        val chosenMode = displayMode
                        val chosenConnections = showConnections
                        val request = revision
                        val evidence = regionalSampling
                        testingFlatStitches = true
                        message = ""
                        scope.launch {
                            val proposal = withContext(Dispatchers.Default) {
                                if (evidence?.needsDetailReview != false) {
                                    Result.failure(
                                        IllegalArgumentException(
                                            "A imagem original ainda apresenta " +
                                                "perdas potenciais neste recorte. " +
                                                "Revise os detalhes antes dos pontos."
                                        )
                                    )
                                } else {
                                    RasterFlatColorStitchCandidate.create(
                                        picture, chosenWidth, chosenHoop
                                    )
                                }
                            }
                            testingFlatStitches = false
                            if (request == revision && prepared?.image === picture &&
                                hoop == chosenHoop && chosenWidth == widthMm) {
                                proposal.fold(
                                    onSuccess = {
                                        qaTechnicalReport = null
                                        qaReportRevision = -1
                                        onSimulate(
                                            it.design, chosenHoop, chosenMode,
                                            chosenConnections
                                        )
                                    },
                                    onFailure = {
                                        message = "Prévia QA de pontos recusada: " +
                                            (it.message ?:
                                                "Revise as regiões da arte e os trilhos.")
                                    }
                                )
                            }
                        }
                    }
                ) {
                    Text(
                        if (testingFlatStitches)
                            "Auditando trajetórias de agulha..."
                        else "Testar pontos reais QA (somente formas simples)",
                        color = FioGold
                    )
                }
                Text(
                    "Usa as cores planas apenas como hipótese; confere " +
                        "as pontadas contra a máscara ORIGINAL e reprova " +
                        "texturas/cores ambíguas. Não cria PES.",
                    color = FioTextMuted, fontSize = 11.sp
                )
            }
            Text(
                "Arte: ${widthMm.roundToInt()} × ${heightMm.roundToInt()} mm • " +
                    "Bastidor: ${hoop.displayName}",
                color = FioText, fontWeight = FontWeight.SemiBold
            )
            val orientedHoop = ImageHoopSizing.usableFrameMm(
                stageImage.width, stageImage.height, hoop
            )
            Text(
                "Área útil na orientação da arte: " +
                    "${orientedHoop.first.roundToInt()} × " +
                    "${orientedHoop.second.roundToInt()} mm " +
                    "(inclui margem de segurança).",
                color = FioTextMuted, fontSize = 11.sp
            )
            Slider(
                value = fill.coerceIn(.2f, 1f),
                onValueChange = { fill = it; techniqueReport = null },
                valueRange = .2f..1f
            )
            Text(
                "Deslize para ajustar a arte no bastidor; ao trocar de bastidor, " +
                    "o tamanho é recalculado automaticamente.",
                color = FioTextMuted, fontSize = 11.sp
            )
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !preparing && !simulating && !analyzingTechniques &&
                    prepared != null && fitted,
                onClick = {
                    val image = prepared?.image ?: return@OutlinedButton
                    val currentWidth = widthMm
                    val chosenHoop = hoop
                    val request = revision
                    analyzingTechniques = true
                    techniqueReport = null
                    scope.launch {
                        val assessment = withContext(Dispatchers.Default) {
                            RasterTechniqueAdvisor.assess(
                                image, currentWidth, chosenHoop
                            )
                        }
                        analyzingTechniques = false
                        if (request == revision && chosenHoop == hoop &&
                            currentWidth == widthMm) {
                            assessment.fold(
                                onSuccess = { techniqueReport = it },
                                onFailure = {
                                    message = it.message ?:
                                        "Não foi possível analisar as técnicas."
                                }
                            )
                        }
                    }
                }
            ) {
                Text(
                    if (analyzingTechniques) "Analisando tipos de ponto..."
                    else "Analisar técnicas de bordado",
                    color = FioGold
                )
            }
            techniqueReport?.let { report ->
                Text(
                    report.brief(),
                    color = if (report.needsDigitizing) FioGold else FioText,
                    fontSize = 12.sp
                )
                report.candidates.take(12).forEach { candidate ->
                    val category = when (candidate.technique) {
                        com.timachado.brothermatrizes.core.embroidery.RasterTechnique.SATIN_QA ->
                            "Satin (QA)"
                        com.timachado.brothermatrizes.core.embroidery.RasterTechnique.RUNNING_QA ->
                            "Ponto corrido (QA)"
                        com.timachado.brothermatrizes.core.embroidery.RasterTechnique.TATAMI_RECT_QA ->
                            "Tatami retangular (QA)"
                        com.timachado.brothermatrizes.core.embroidery.RasterTechnique.TATAMI_SHAPE_QA ->
                            "Tatami com contraformas (QA)"
                        com.timachado.brothermatrizes.core.embroidery.RasterTechnique.FILL_NEEDS_DIGITIZING ->
                            "Preenchimento pendente"
                        com.timachado.brothermatrizes.core.embroidery.RasterTechnique.RUNNING_NEEDS_DIGITIZING ->
                            "Contorno pendente"
                        com.timachado.brothermatrizes.core.embroidery.RasterTechnique.MANUAL_REVIEW ->
                            "Revisão manual"
                    }
                    Text(
                        "Parte ${candidate.partId}: $category — " +
                            candidate.explanation,
                        color = FioTextMuted,
                        fontSize = 11.sp
                    )
                }
                if (report.candidates.size > 12) {
                    Text(
                        "Mostradas 12 de ${report.candidates.size} partes.",
                        color = FioTextMuted, fontSize = 11.sp
                    )
                }
            }
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
                                        assistedSatinElementId = 0
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
                                        val single = RasterAutoSatinRails.propose(
                                            picture, activePart, currentWidth
                                        )
                                        val sections = single.fold(
                                            onSuccess = { Result.success(listOf(it)) },
                                            onFailure = {
                                                RasterSatinTopologyPlanner.propose(
                                                    picture, activePart, currentWidth
                                                )
                                            }
                                        )
                                        sections.mapCatching { draft ->
                                            // Numerical preview gate, not a
                                            // stitch-out guarantee. Refuse
                                            // geometry that cannot pass the
                                            // original editable-rail validator.
                                            if (assistedSatinElementId == partId) {
                                                val holes = elementPlan?.elements
                                                    ?.firstOrNull { it.id == partId }
                                                    ?.enclosedHoles
                                                    ?: error("A origem das contraformas mudou.")
                                                RasterSatinRailGeometryGate.inspect(
                                                    picture, activePart, draft,
                                                    currentWidth, holes
                                                ).getOrThrow()
                                            }
                                            val check = RasterSatinObjectEditor.preview(
                                                picture, activePart, draft,
                                                currentWidth, hoop
                                            ).getOrThrow()
                                            if (assistedSatinElementId == partId) {
                                                RasterAssistedSatinDraftPlanner
                                                    .requireSafeCounterPath(
                                                        picture, activePart,
                                                        check, currentWidth
                                                    ).getOrThrow()
                                            }
                                            draft
                                        }
                                    }
                                    proposingRails = false
                                    if (stamp == revision &&
                                        satinParts.getOrNull(satinPartIndex)?.id == partId
                                    ) {
                                        proposal.fold(
                                            onSuccess = { guides ->
                                                satinSections = satinSections +
                                                    (partId to guides)
                                                satinTouched = satinTouched - partId
                                                satinSectionIndex = 0
                                                message = "Foram propostas ${guides.size} " +
                                                    "seções pelos contornos. Confira e ajuste " +
                                                    "os trilhos antes de simular esta parte."
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
                            if (assistedSatinElementId == activePart.id &&
                                elementPlan?.elements?.any {
                                    it.id == activePart.id &&
                                        it.enclosedHoles > 0
                                } == true
                            ) {
                                message = "Contraforma detectada: reiniciar com um " +
                                    "trilho reto atravessaria o vazado. " +
                                    "Use a proposta de contornos ou refaça a seleção."
                            } else prepared?.image?.let { image ->
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
                    if (assistedSatinElementId == activePart.id) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = prepared != null && fitted && !preparing &&
                                !simulating && !reviewingGaps &&
                                activePart.id in satinTouched &&
                                satinSections[activePart.id].orEmpty().isNotEmpty(),
                            onClick = {
                                val picture = prepared?.image ?: return@OutlinedButton
                                val id = activePart.id
                                val item = activePart
                                val sectionsSnapshot =
                                    satinSections[id].orEmpty().toList()
                                val holes = elementPlan?.elements
                                    ?.firstOrNull { it.id == id }?.enclosedHoles
                                val chosenHoop = hoop
                                val chosenWidth = widthMm
                                val stamp = revision
                                reviewingGaps = true
                                uncoveredReport = null
                                showGapOverlay = false
                                selectedGapId = 0
                                message = ""
                                scope.launch {
                                    val audited = withContext(Dispatchers.Default) {
                                        runCatching {
                                            require(holes != null) {
                                                "A origem dos vazados não foi localizada."
                                            }
                                            RasterSatinRailGeometryGate.inspect(
                                                picture, item, sectionsSnapshot,
                                                chosenWidth, holes
                                            ).getOrThrow()
                                            val stitches = RasterSatinObjectEditor.preview(
                                                picture, item, sectionsSnapshot,
                                                chosenWidth, chosenHoop
                                            ).getOrThrow()
                                            RasterAssistedSatinDraftPlanner.requireSafeCounterPath(
                                                picture, item, stitches, chosenWidth
                                            ).getOrThrow()
                                            val fidelity = RasterStitchFidelityAudit.compare(
                                                picture, item, stitches, chosenWidth
                                            ).getOrThrow()
                                            RasterUncoveredRegionAudit.inspect(
                                                picture, item, fidelity, chosenWidth
                                            ).getOrThrow()
                                        }
                                    }
                                    reviewingGaps = false
                                    if (stamp == revision &&
                                        prepared?.image === picture &&
                                        hoop == chosenHoop && widthMm == chosenWidth &&
                                        satinParts.getOrNull(satinPartIndex)?.id == id &&
                                        satinSections[id] == sectionsSnapshot) {
                                        audited.fold(
                                            onSuccess = {
                                                uncoveredReport = it
                                                showGapOverlay = true
                                                selectedGapId = 0
                                            },
                                            onFailure = {
                                                message = it.message ?:
                                                    "Não foi possível conferir as lacunas."
                                            }
                                        )
                                    }
                                }
                            }
                        ) {
                            Text(
                                if (reviewingGaps)
                                    "Conferindo cobertura das pontadas..."
                                else "Analisar lacunas Satin (QA)",
                                color = FioGold
                            )
                        }
                        uncoveredReport?.let { missing ->
                            Text(
                                missing.summary(), color = FioTextMuted,
                                fontSize = 11.sp
                            )
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { showGapOverlay = !showGapOverlay }
                            ) {
                                Text(
                                    if (showGapOverlay)
                                        "Voltar à imagem original"
                                    else "Mostrar lacunas no bastidor (QA)",
                                    color = FioGold
                                )
                            }
                            if (showGapOverlay && missing.regions.isNotEmpty()) {
                                Row(
                                    Modifier.fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(onClick = { selectedGapId = 0 }) {
                                        Text("Todas as lacunas", color = FioGold)
                                    }
                                    missing.regions.take(8).forEach { region ->
                                        OutlinedButton(onClick = {
                                            selectedGapId = region.id
                                        }) {
                                            Text(
                                                "Região " + region.id,
                                                color = FioGold
                                            )
                                        }
                                    }
                                }
                            }
                            missing.regions.take(8).forEach { region ->
                                val routeName = when (region.suggestedReview) {
                                    RasterGapReviewRoute.SATIN_REVIEW ->
                                        "revisar largura e direção Satin"
                                    RasterGapReviewRoute.RUNNING_REVIEW ->
                                        "revisar ponto corrido"
                                    RasterGapReviewRoute.TATAMI_REVIEW ->
                                        "avaliar preenchimento Tatami"
                                    RasterGapReviewRoute.MANUAL_REVIEW ->
                                        "reconstrução manual de contornos"
                                }
                                Text(
                                    "Região " + region.id + ": " +
                                        region.pixelCount + " px sem costura, " +
                                        "posição (" + region.minX + "," +
                                        region.minY + ") — " + routeName +
                                        ". Nenhum ponto foi gerado.",
                                    color = FioTextMuted, fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !preparing && !simulating && fitted && prepared != null &&
                    (samplingEvidence == null || regionalSampling != null),
                colors = ButtonDefaults.buttonColors(containerColor = FioGold),
                onClick = {
                    val selected = prepared?.image ?: return@Button
                    val selectedHoop = hoop
                    val selectedWidth = widthMm
                    val chosenMode = displayMode
                    val chosenConnections = showConnections
                    val currentRevision = revision
                    val selectedSamplingEvidence = regionalSampling
                    val selectedArtworkComplex = artworkAudit?.requiresMultiTechniqueReview == true
                    // Snapshot rail edits: the background operation must not
                    // mix draft changes or silently switch to raster filling.
                    val selectedPart = if (satinEditorOpen) {
                        satinParts.getOrNull(satinPartIndex)
                    } else null
                    val selectedSections = selectedPart?.let {
                        satinSections[it.id].orEmpty()
                    }.orEmpty()
                    val isReviewed = selectedPart?.id in satinTouched
                    val selectedAssistedId = assistedSatinElementId
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
                                        // Fail as Result instead of throwing out of the
                                        // coroutine and leaving simulating=true.
                                        runCatching {
                                            if (selectedPart.id == selectedAssistedId) {
                                                val holes = elementPlan?.elements
                                                ?.firstOrNull { it.id == selectedPart.id }
                                                ?.enclosedHoles ?: error(
                                                    "Origem da máscara assistida indisponível."
                                                )
                                            RasterSatinRailGeometryGate.inspect(
                                                selected, selectedPart,
                                                selectedSections, selectedWidth,
                                                    holes
                                                ).getOrThrow()
                                            }
                                        }.fold(
                                            onSuccess = {
                                                RasterSatinObjectEditor.preview(
                                            selected, selectedPart, selectedSections,
                                            selectedWidth, selectedHoop
                                        ).mapCatching { proposed ->
                                            if (selectedPart.id == selectedAssistedId) {
                                                RasterAssistedSatinDraftPlanner
                                                    .requireSafeCounterPath(
                                                        selected, selectedPart,
                                                        proposed, selectedWidth
                                                    ).getOrThrow()
                                                val fidelity = RasterStitchFidelityAudit
                                                    .compare(
                                                        selected, selectedPart,
                                                        proposed, selectedWidth
                                                    ).getOrThrow()
                                                val gaps =
                                                    RasterUncoveredRegionAudit.inspect(
                                                        selected, selectedPart,
                                                        fidelity, selectedWidth
                                                    ).getOrThrow()
                                                require(
                                                    fidelity.coverage >= .78 &&
                                                        fidelity.offInkFraction <= .10
                                                ) {
                                                    "Os trilhos deixam " +
                                                        gaps.uncoveredPixels +
                                                        " pixels sem cobertura em " +
                                                        gaps.totalRegions + " regiões. " +
                                                        "Revise Satin, Tatami ou corrido " +
                                                        "antes de simular. Não é PES."
                                                }
                                            }
                                            proposed
                                                }
                                            },
                                            onFailure = { Result.failure(it) }
                                        )
                                    }
                                } else if (selectedArtworkComplex) {
                                    Result.failure(
                                        IllegalArgumentException(
                                            "O arquivo contém texturas ou vários tons de " +
                                                "linha. Separe e revise os elementos " +
                                                "(símbolo, letras e contornos) antes de " +
                                                "gerar trajetórias reais de agulha. " +
                                                "A prévia fotográfica não é uma matriz."
                                        )
                                    )
                                } else if (selectedSamplingEvidence?.needsDetailReview == true) {
                                    Result.failure(
                                        IllegalArgumentException(
                                            "O recorte selecionado pode ter perdido " +
                                                "cores ou pequenos detalhes ao ser " +
                                                "reduzido. Revise a separação das " +
                                                "formas antes de gerar pontos reais."
                                        )
                                    )
                                } else {
                                    // Only the real assembled STITCH/JUMP/TRIM
                                    // paths reach the original SimulatorScreen.
                                    // No raster overlay or fake whole-logo
                                    // interpolation masquerades as stitches.
                                    RasterAutomaticLogoPreview.create(
                                        selected, selectedWidth, selectedHoop
                                    )
                                }
                            }.mapCatching { preview ->
                                // Both edited and automatic images pass the
                                // needle-pitch/hotspot gate before the existing
                                // SimulatorScreen receives their commands.
                                RasterNeedleRiskAudit.inspect(
                                    preview, selectedHoop
                                ).getOrThrow()
                                // Check the same-color thread accumulated
                                // over a tiny patch of fabric, including
                                // manual Satin sections selected for preview.
                                RasterThreadDensityPreflight.inspect(
                                    preview
                                ).getOrThrow()
                                // A valid STITCH sequence cannot claim to be
                                // universally machine-ready just because the
                                // destination understands PES. Explicitly
                                // assess the unknown machine target, without
                                // enabling export or changing original PES.
                                val compatibility =
                                    RasterPesTargetCompatibility.assess(
                                        preview, selectedHoop
                                    ).getOrThrow()
                                require(!compatibility.exportPermitted) {
                                    "Exportação experimental não homologada."
                                }
                                preview
                            }
                        // Produce a review dossier from the actual finalized
                        // QA stitch commands, off the UI thread. This is a
                        // text report, never an encoded embroidery file.
                        val report = design.getOrNull()?.let { preview ->
                            withContext(Dispatchers.Default) {
                                RasterQaReadinessReportBuilder.collect(
                                    design = preview,
                                    hoop = selectedHoop,
                                    sourceImage = if (editing) null else selected,
                                    sourceWidthMm = if (editing) null else selectedWidth,
                                    samplingEvidence = selectedSamplingEvidence
                                ).getOrNull()?.toMarkdown()
                            }
                        }
                        if (revision == currentRevision) {
                            simulating = false
                            design.fold(
                                onSuccess = {
                                    qaTechnicalReport = report
                                    qaReportRevision = currentRevision
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
            if (qaReportRevision == revision &&
                !qaTechnicalReport.isNullOrBlank()) {
                Text(
                    "Relatório técnico QA disponível após a simulação. " +
                        "Não é matriz PES nem autorização de bordado.",
                    color = FioTextMuted, fontSize = 11.sp
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val reportText = qaTechnicalReport.orEmpty()
                        val clip = android.content.ClipData.newPlainText(
                            "Brother Matrizes — Relatório técnico QA",
                            reportText
                        )
                        val clipboard = context.getSystemService(
                            android.content.Context.CLIPBOARD_SERVICE
                        ) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(clip)
                        message = "Relatório técnico QA copiado. " +
                            "Exportação PES experimental permanece bloqueada."
                    }
                ) {
                    Text("Copiar relatório técnico QA", color = FioGold)
                }
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { picker.launch(arrayOf("image/*")) }
            ) { Text("Escolher outra imagem", color = FioGold) }
            Text(
                "Sem editor: Simular bordado tenta digitalizar automaticamente " +
                    "todos os elementos compatíveis e valida cada trajetória. " +
                    "Com editor: simula apenas a parte ajustada. Se algum trecho " +
                    "exigir outra técnica, a simulação completa será recusada. " +
                    "PES é um formato, não um perfil universal de máquina. " +
                    "Compatibilidade com modelos específicos e exportação " +
                    "permanecem bloqueadas até verificação técnica.",
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
