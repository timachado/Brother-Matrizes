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
import com.timachado.brothermatrizes.core.embroidery.RasterTextureReconstructionGate
import com.timachado.brothermatrizes.core.embroidery.RasterTextureReconstructionReport
import com.timachado.brothermatrizes.core.embroidery.RasterQaRunningStitchDraft
import com.timachado.brothermatrizes.core.embroidery.RasterFlatColorStitchCandidate
import com.timachado.brothermatrizes.core.embroidery.RasterAssistedElementPlanner
import com.timachado.brothermatrizes.core.embroidery.RasterAssistedElementPlan
import com.timachado.brothermatrizes.core.embroidery.RasterAssistedSatinDraftPlanner
import com.timachado.brothermatrizes.core.embroidery.RasterSatinRailGeometryGate
import com.timachado.brothermatrizes.core.embroidery.RasterStitchFidelityAudit
import com.timachado.brothermatrizes.core.embroidery.RasterUncoveredRegionAudit
import com.timachado.brothermatrizes.core.embroidery.RasterUncoveredRegionReport
import com.timachado.brothermatrizes.core.embroidery.RasterUncoveredReviewOverlay
import com.timachado.brothermatrizes.core.embroidery.RasterGapRepairGuidance
import com.timachado.brothermatrizes.core.embroidery.RasterGapRepairPlan
import com.timachado.brothermatrizes.core.embroidery.RasterGapRepairAction
import com.timachado.brothermatrizes.core.embroidery.RasterSatinBoundedRepairTrial
import com.timachado.brothermatrizes.core.embroidery.RasterSatinBoundedRepairResult
import com.timachado.brothermatrizes.core.embroidery.RasterGapReviewRoute
import com.timachado.brothermatrizes.core.embroidery.RasterMixedTechniqueReviewWorkflow
import com.timachado.brothermatrizes.core.embroidery.RasterQaComponentBudget
import com.timachado.brothermatrizes.core.embroidery.RasterAlphaEdgeReview
import com.timachado.brothermatrizes.core.embroidery.RasterSourceTopologyDiagnostics
import com.timachado.brothermatrizes.core.embroidery.RasterSourceTopologyReport
import com.timachado.brothermatrizes.core.embroidery.RasterAlphaThresholdSweep
import com.timachado.brothermatrizes.core.embroidery.RasterAlphaThresholdSweepReport
import com.timachado.brothermatrizes.core.embroidery.RasterQaStructuralAuditReceipt
import com.timachado.brothermatrizes.core.embroidery.RasterQaCounterClosureStudy
import com.timachado.brothermatrizes.core.embroidery.RasterQaCounterClosureProposal
import com.timachado.brothermatrizes.core.embroidery.RasterQaMicrodetailReview
import com.timachado.brothermatrizes.core.embroidery.RasterQaMicrodetailPhysicalGate
import com.timachado.brothermatrizes.core.embroidery.RasterQaMicrodetailIntent
import com.timachado.brothermatrizes.core.embroidery.RasterExactColorComponentDetector
import com.timachado.brothermatrizes.core.embroidery.RasterMixedStitchVisualAudit
import com.timachado.brothermatrizes.core.embroidery.RasterMixedStitchVisualEvidence
import com.timachado.brothermatrizes.core.embroidery.RasterTechniqueKind
import com.timachado.brothermatrizes.core.embroidery.RasterReviewedTechniqueLayer
import com.timachado.brothermatrizes.core.embroidery.RasterMixedTechniqueSequenceReview
import com.timachado.brothermatrizes.core.embroidery.RasterImageQaReleaseReadinessAudit
import com.timachado.brothermatrizes.core.embroidery.RasterImageSimulationRoute
import com.timachado.brothermatrizes.core.embroidery.RasterImageSimulationRoutePlanner
import com.timachado.brothermatrizes.core.embroidery.RasterLetterTopologyReview
import com.timachado.brothermatrizes.core.embroidery.RasterLetterTopologyReport
import com.timachado.brothermatrizes.core.embroidery.RasterLetterEditableSatinReview
import com.timachado.brothermatrizes.core.embroidery.RasterLetterSatinDraft
import com.timachado.brothermatrizes.core.embroidery.RasterLetterRailEdge
import com.timachado.brothermatrizes.core.embroidery.RasterLetterRailAxis
import com.timachado.brothermatrizes.core.embroidery.RasterSatinCurveHotspotReview
import com.timachado.brothermatrizes.core.embroidery.RasterSatinCurveHotspotReport
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
    // A raw color-study preview is visual only. A separately validated and
    // explicitly accepted QA reference may be used for stitch PREVIEWS;
    // the actual imported photo remains unchanged and PES stays blocked.
    var flatStudy by remember(stageImage) {
        mutableStateOf<RasterThreadTextureStudyReport?>(null)
    }
    // The operator explicitly selects a reconstructed QA-only reference;
    // the imported photograph is never overwritten by this preference.
    var flatSourceCandidate by remember(stageImage, flatStudy) {
        mutableStateOf<RasterTextureReconstructionReport?>(null)
    }
    var acceptedFlatSource by remember(stageImage, flatStudy) {
        mutableStateOf<RasterTextureReconstructionReport?>(null)
    }
    var checkingFlatSource by remember(stageImage, flatStudy) {
        mutableStateOf(false)
    }
    val mixedSource = acceptedFlatSource?.flatReference ?: prepared?.image
    var showFlatStudy by remember(stageImage) { mutableStateOf(false) }
    var studyingTexture by remember(stageImage) { mutableStateOf(false) }
    var testingFlatStitches by remember(stageImage) { mutableStateOf(false) }
    var testingRunningOutline by remember(stageImage, widthMm, hoop) {
        mutableStateOf(false)
    }
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
    // Show anti-aliased edge loss risk on the original prepared picture.
    // Choosing a reviewed flat QA reference does not overwrite that picture.
    val alphaSourceReview = remember(prepared?.image) {
        prepared?.image?.let { RasterAlphaEdgeReview.inspect(it).getOrNull() }
    }
    // Run the exact imported crop analysis only on demand, off the UI thread.
    // Changing artwork/crop automatically invalidates prior diagnostics.
    var sourceTopologyBusy by remember(prepared?.image) {
        mutableStateOf(false)
    }
    var sourceTopologyReport by remember(prepared?.image) {
        mutableStateOf<RasterSourceTopologyReport?>(null)
    }
    // Explicit comparison previews of the user's original alpha channel.
    // These masks are NEVER supplied to the stitch generator.
    var alphaSweepBusy by remember(prepared?.image) {
        mutableStateOf(false)
    }
    var alphaSweepReport by remember(prepared?.image) {
        mutableStateOf<RasterAlphaThresholdSweepReport?>(null)
    }
    var selectedAlphaThreshold by remember(prepared?.image) {
        mutableStateOf<Int?>(null)
    }
    var selectedAlphaTransition by remember(prepared?.image) {
        mutableStateOf<Pair<Int,Int>?>(null)
    }
    var counterClosureBusy by remember(prepared?.image) {
        mutableStateOf(false)
    }
    var counterClosureStudy by remember(prepared?.image) {
        mutableStateOf<RasterQaCounterClosureProposal?>(null)
    }
    var showCounterClosure by remember(prepared?.image) {
        mutableStateOf(false)
    }
    var showMicrodetail by remember(prepared?.image) {
        mutableStateOf(false)
    }
    var microdetailJudgment by remember(prepared?.image, counterClosureStudy) {
        mutableStateOf("não classificado")
    }
    val counterClosureOverlay=remember(
        counterClosureStudy,showCounterClosure
    ) {
        if(showCounterClosure) counterClosureStudy?.preview() else null
    }
    val alphaSweepOverlay = remember(
        alphaSweepReport,selectedAlphaThreshold,selectedAlphaTransition
    ) {
        val report=alphaSweepReport
        val level=selectedAlphaThreshold
        val comparison=selectedAlphaTransition
        when {
            report!=null && comparison!=null ->
                report.structuralAudit?.preview(
                    comparison.first,comparison.second
                )?.let { base ->
                    report.counterLeakEvidence?.overlay(
                        comparison.first,comparison.second,base
                    ) ?: base
                } ?: report.componentLineage?.lossPreview(
                    comparison.first,comparison.second
                )
            report!=null && level!=null -> report.silhouettePreview(level)
            else -> null
        }
    }
    val simulationRoute = RasterImageSimulationRoutePlanner.choose(
        complexArtwork = artworkAudit?.requiresMultiTechniqueReview == true,
        partialAlphaEdges = alphaSourceReview?.requiresManualReconstruction == true,
        flatReferenceAccepted = acceptedFlatSource != null,
        manualSatinEditorActive = satinEditorOpen
    )
    val maxWidth = stageImage?.let {
        ImageHoopSizing.maximumWidthMm(it.width, it.height, hoop)
    } ?: 0f
    val widthMm = maxWidth * fill
    val microdetail = remember(
        counterClosureStudy,alphaSweepReport,stageImage,widthMm
    ) {
        val candidate=counterClosureStudy
        val source=alphaSweepReport
        if(candidate!=null && source!=null &&
            candidate.source === source.source &&
            stageImage === candidate.source
        ) {
            RasterQaMicrodetailReview.inspect(
                candidate,
                source.counterLeakEvidence ?: return@remember null,
                widthMm
            ).getOrNull()
        } else null
    }
    val microdetailOverlay = remember(microdetail,showMicrodetail) {
        if(showMicrodetail) microdetail?.zoomPreview() else null
    }
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
    var repairGuidanceBusy by remember(uncoveredReport) { mutableStateOf(false) }
    var repairPlan by remember(uncoveredReport) {
        mutableStateOf<RasterGapRepairPlan?>(null)
    }
    var showGapOverlay by remember(stageImage) { mutableStateOf(false) }
    var trialBusy by remember(uncoveredReport, satinSections, widthMm, hoop) {
        mutableStateOf(false)
    }
    var repairTrial by remember(uncoveredReport, satinSections, widthMm, hoop) {
        mutableStateOf<RasterSatinBoundedRepairResult?>(null)
    }
    var selectedGapId by remember(stageImage) { mutableIntStateOf(0) }
    val gapOverlay = remember(
        uncoveredReport, showGapOverlay, selectedGapId,
        stageImage, satinParts, satinPartIndex, assistedSatinElementId
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
    // Mixed-technique QA is tied to the exact image, scale and hoop.
    // Changing any of these discards candidate paths and confirmations.
    var mixedOpen by remember(stageImage, acceptedFlatSource, hoop, widthMm) { mutableStateOf(false) }
    var mixedParts by remember(stageImage, acceptedFlatSource, hoop, widthMm) {
        mutableStateOf<List<RasterSatinObject>>(emptyList())
    }
    var mixedOrder by remember(stageImage, acceptedFlatSource, hoop, widthMm) {
        mutableStateOf<List<Int>>(emptyList())
    }
    var mixedTechniques by remember(stageImage, acceptedFlatSource, hoop, widthMm) {
        mutableStateOf<Map<Int, RasterTechniqueKind>>(emptyMap())
    }
    var mixedDrafts by remember(stageImage, acceptedFlatSource, hoop, widthMm) {
        mutableStateOf<Map<Int, EmbroideryDesign>>(emptyMap())
    }
    var mixedTopology by remember(stageImage, acceptedFlatSource, hoop, widthMm) {
        mutableStateOf<Map<Int, RasterLetterTopologyReport>>(emptyMap())
    }
    var mixedSatinRails by remember(stageImage, acceptedFlatSource, hoop, widthMm) {
        mutableStateOf<Map<Int, RasterLetterSatinDraft>>(emptyMap())
    }
    var mixedSatinUndo by remember(stageImage, acceptedFlatSource, hoop, widthMm) {
        mutableStateOf<Map<Int, List<RasterLetterSatinDraft>>>(emptyMap())
    }
    // Split proposals are unapproved. Their STITCH must be inspected
    // independently before changing the editor's actual Satin sections.
    var mixedSplitCandidates by remember(stageImage, acceptedFlatSource, hoop, widthMm) {
        mutableStateOf<Map<Int, Triple<RasterLetterSatinDraft,
            RasterLetterSatinDraft, RasterMixedStitchVisualEvidence>>>(
            emptyMap()
        )
    }
    var mixedSplitViewed by remember(stageImage, acceptedFlatSource, hoop, widthMm) {
        mutableStateOf<Set<Int>>(emptySet())
    }
    // A hotspot report is valid for ONE exact Satin draft, never reused
    // after moving a rail, undo or changing the reference image.
    var mixedCurveHotspots by remember(stageImage, acceptedFlatSource, hoop, widthMm) {
        mutableStateOf<Map<Int, Pair<RasterLetterSatinDraft,
            RasterSatinCurveHotspotReport>>>(emptyMap())
    }
    var mixedRailSection by remember(stageImage, acceptedFlatSource, hoop, widthMm) {
        mutableIntStateOf(0)
    }
    var mixedRailPair by remember(stageImage, acceptedFlatSource, hoop, widthMm) {
        mutableIntStateOf(0)
    }
    var mixedRailEdge by remember(stageImage, acceptedFlatSource, hoop, widthMm) {
        mutableStateOf(RasterLetterRailEdge.A)
    }
    var mixedRailAxis by remember(stageImage, acceptedFlatSource, hoop, widthMm) {
        mutableStateOf(RasterLetterRailAxis.X)
    }
    var mixedConfirmed by remember(stageImage, acceptedFlatSource, hoop, widthMm) {
        mutableStateOf<Set<Int>>(emptySet())
    }
    var mixedAudits by remember(stageImage, acceptedFlatSource, hoop, widthMm) { mutableStateOf<Map<Int, RasterMixedStitchVisualEvidence>>(emptyMap()) }
    var mixedViewed by remember(stageImage, acceptedFlatSource, hoop, widthMm) { mutableStateOf<Set<Int>>(emptySet()) }
    // 0 = fonte, 1 = STITCH, 2 = mapa de cobertura,
    // 3 = montagem completa, 4 = preview candidato de divisão Satin.
    var mixedViewMode by remember(stageImage, acceptedFlatSource, hoop, widthMm) { mutableIntStateOf(0) }
    var mixedBusy by remember(stageImage, acceptedFlatSource, hoop, widthMm) { mutableStateOf(false) }
    var mixedSelectedPart by remember(stageImage, acceptedFlatSource, hoop, widthMm) {
        mutableIntStateOf(0)
    }
    // A finished sequence cannot survive a new source, part, technique,
    // stitch draft, color/order change or removed human approval.
    var mixedAssembly by remember(
        stageImage, acceptedFlatSource, hoop, widthMm, mixedParts, mixedOrder,
        mixedTechniques, mixedDrafts, mixedConfirmed, mixedViewed, mixedAudits
    ) {
        mutableStateOf<RasterMixedTechniqueSequenceReview?>(null)
    }
    var mixedAssemblyViewed by remember(mixedAssembly) {
        mutableStateOf(false)
    }
    var mixedCoverageViewed by remember(mixedAssembly) {
        mutableStateOf(false)
    }
    val mixedPartOverlay = remember(mixedParts, mixedSelectedPart, mixedSource) {
        val original = mixedSource
        val item = mixedParts.firstOrNull { it.id == mixedSelectedPart }
        if (original == null || item == null ||
            item.mask.pixels.size != original.argb.size
        ) null else RasterImagePixels(
            original.width, original.height,
            IntArray(original.argb.size) { index ->
                if (item.mask.pixels[index] == 0) original.argb[index] else 0
            }, "Elemento para prévia multitécnica QA"
        )
    }
    val mixedFullShown = if (mixedOpen && mixedViewMode == 3)
        mixedAssembly?.design else null
    val mixedFullCoverage = if (mixedOpen && mixedViewMode == 5)
        mixedAssembly?.fullCanvasReport?.coverageOverlay else null
    val mixedSplitShown = if(mixedOpen && mixedViewMode == 4)
        mixedSplitCandidates[mixedSelectedPart]?.second?.preview else null
    val mixedSplitSource = if(mixedOpen && mixedViewMode == 4)
        mixedSplitCandidates[mixedSelectedPart]?.third?.sourceOverlay else null
    val mixedShown = if (mixedOpen && mixedViewMode == 1)
        mixedDrafts[mixedSelectedPart] else null
    val mixedAuditShown = if (mixedOpen) mixedAudits[mixedSelectedPart] else null
    val mixedCoverShown = if (mixedOpen && mixedViewMode == 2) mixedAuditShown?.coverageOverlay else null
    val mixedSourceShown = if (mixedOpen && mixedViewMode == 1) mixedAuditShown?.sourceOverlay else null
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

    val imageScrollState = rememberScrollState()
    Column(
        Modifier.fillMaxSize().background(FioBackground)
            .verticalScroll(imageScrollState)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text("‹ Voltar", color = FioGold) }
            Column {
                Text("Imagem para Bordado", color = FioText, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold)
                Text("Escolha a imagem e confira no bastidor", color = FioTextMuted, fontSize = 12.sp)
                Text(
                    "APK EXPERIMENTAL: QA V6 — Segurança do Microdetalhe",
                    color = FioGold,fontSize = 11.sp
                )
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
                design = if (alphaSweepOverlay != null ||
                    counterClosureOverlay != null ||
                    microdetailOverlay != null) placeholder
                    else mixedFullShown ?: mixedSplitShown ?: mixedShown ?: placeholder,
                hoop = hoop,
                displayMode = displayMode,
                showConnections = showConnections,
                rasterArtwork = if (microdetailOverlay != null)
                    microdetailOverlay
                    else if (counterClosureOverlay != null)
                    counterClosureOverlay
                    else if (alphaSweepOverlay != null)
                    alphaSweepOverlay
                    else if (mixedFullShown != null) mixedSource
                    else mixedFullCoverage ?: mixedSplitSource ?:
                        mixedCoverShown ?: mixedSourceShown ?: gapOverlay ?:
                        mixedPartOverlay ?: elementPicture ?: if (showFlatStudy)
                    flatStudy?.flatPreview ?: stageImage else stageImage,
                rasterWidthMm = widthMm,
                rasterOpacity = if (alphaSweepOverlay != null ||
                    counterClosureOverlay != null ||
                    microdetailOverlay != null) .99f
                    else if (mixedFullShown != null) .12f
                    else if (mixedShown != null || mixedSplitShown != null)
                        .18f else .99f,
                modifier = Modifier.fillMaxWidth().height(350.dp)
            )
            Text(
                if (microdetailOverlay != null)
                    "MICRODETALHE QA: ampliação em blocos da foto original. " +
                        "Cinza = tinta alfa ≥208; laranja = 180..207; " +
                        "verde = pixel original recuperado; escuro = fundo. " +
                        "A ampliação não modifica a arte nem cria STITCH/PES."
                else if (counterClosureOverlay != null)
                    "PROTEÇÃO LOCALIZADA QA 180→208: cinza = pixels " +
                        "preservados; verde = pixels semitransparentes " +
                        "da imagem original reincluídos apenas na prévia. " +
                        "Não são pontos, trilhos Satin nem PES."
                else if (selectedAlphaTransition != null)
                    "COMPARAÇÃO DE PERDA QA " +
                        selectedAlphaTransition?.first + "→" +
                        selectedAlphaTransition?.second +
                        ": cinza = pixels preservados; laranja = pixels " +
                        "excluídos ao elevar a transparência; magenta = " +
                        "candidatos a ligação entre regiões; ciano = " +
                        "rota de abertura de um vazado interno. " +
                        "Não são pontos nem PES."
                else if (alphaSweepOverlay != null)
                    "LIMIAR DE ALFA QA " + selectedAlphaThreshold +
                        ": cinza = silhueta mantida, laranja = borda perto " +
                        "do corte de transparência. É apenas comparação da " +
                        "imagem original, NÃO são pontos nem matriz PES."
                else if (mixedFullCoverage != null)
                    "Desenho INTEIRO: cinza = tinta coberta por STITCH " +
                        "amostrados; laranja = áreas sem pontos. " +
                        "Cada letra e cor foi conferida sem preencher vazados."
                else if (mixedSplitShown != null)
                    "Divisão Satin CANDIDATA: STITCH/JUMP/TRIM reais. " +
                        "Nenhum trilho original foi substituído; aceite apenas " +
                        "após conferir o corte e a cobertura."
                else if (mixedFullShown != null)
                    "Sequência COMPLETA real em STITCH/JUMP/TRIM sobre a fonte. " +
                        "Revise a ordem de costura antes do Simular Bordado. PES bloqueado."
                else if (mixedShown != null)
                    "Pontadas STITCH/JUMP reais sobre a fonte semitransparente. Não é PES."
                else if (mixedCoverShown != null)
                    "Cobertura real QA: laranja = tinta sem pontos; cinza = tinta coberta."
                else if (mixedPartOverlay != null)
                    "Componente destacado para revisão multitécnica. " +
                        "É a imagem original isolada, NÃO são pontadas."
                else if (gapOverlay != null)
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
            if (alphaSourceReview?.requiresManualReconstruction == true) {
                Text(
                    alphaSourceReview.guidance() +
                        if (acceptedFlatSource == null)
                            " Montagem automática por cores exatas bloqueada."
                        else
                            " Referência plana QA aceita separadamente; " +
                                "confira a fidelidade manualmente.",
                    color = FioGold, fontSize = 12.sp
                )
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = prepared?.image != null && !sourceTopologyBusy &&
                    !preparing && !simulating,
                onClick = {
                    val picture = prepared?.image ?: return@OutlinedButton
                    val stamp = revision
                    sourceTopologyBusy = true
                    message = ""
                    scope.launch {
                        val diagnosis = withContext(Dispatchers.Default) {
                            RasterSourceTopologyDiagnostics.inspect(picture)
                        }
                        if (revision == stamp && prepared?.image === picture) {
                            diagnosis.fold(
                                onSuccess = {
                                    sourceTopologyReport = it
                                    message = "Diagnóstico da imagem pronto. " +
                                        "Confira e copie o relatório QA abaixo."
                                },
                                onFailure = {
                                    message = it.message ?:
                                        "Não foi possível analisar a imagem."
                                }
                            )
                        }
                        sourceTopologyBusy = false
                    }
                }
            ) {
                Text(
                    if (sourceTopologyBusy) "Analisando letras e alfa..."
                    else "Diagnóstico técnico da imagem (QA)",
                    color = FioGold
                )
            }
            sourceTopologyReport?.let { proof ->
                Text(proof.summary(), color = FioTextMuted, fontSize = 11.sp)
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val clipboard = context.getSystemService(
                            android.content.Context.CLIPBOARD_SERVICE
                        ) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText(
                                "Brother Matrizes — Diagnóstico raster QA",
                                proof.summary()
                            )
                        )
                        message = "Diagnóstico técnico copiado. " +
                            "Nenhum pixel nem arquivo da imagem foi exportado."
                    }
                ) {
                    Text("Copiar diagnóstico da imagem (QA)", color = FioGold)
                }
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = prepared?.image != null && !alphaSweepBusy &&
                    !preparing && !simulating,
                onClick = {
                    val source=prepared?.image ?: return@OutlinedButton
                    showCounterClosure=false
                    showMicrodetail=false
                    if(alphaSweepReport!=null) {
                        val closed = selectedAlphaThreshold!=null ||
                            selectedAlphaTransition!=null
                        selectedAlphaTransition=null
                        selectedAlphaThreshold=if(closed) null else 180
                    } else {
                        val stamp=revision
                        alphaSweepBusy=true
                        message=""
                        scope.launch {
                            val result=withContext(Dispatchers.Default) {
                                RasterAlphaThresholdSweep.inspect(source)
                            }
                            if(revision==stamp && prepared?.image===source) {
                                result.fold(
                                    onSuccess = {
                                        alphaSweepReport=it
                                        selectedAlphaThreshold=180
                                        message="Comparação da silhueta pronta. " +
                                            "Revise letras e vazados nos limiares."
                                    },
                                    onFailure = {
                                        message=it.message ?:
                                            "Falha no estudo de transparência."
                                    }
                                )
                            }
                            alphaSweepBusy=false
                        }
                    }
                }
            ) {
                Text(
                    if(alphaSweepBusy) "Comparando níveis de transparência..."
                    else if(selectedAlphaThreshold!=null ||
                        selectedAlphaTransition!=null)
                        "Mostrar imagem original"
                    else "Comparar silhuetas por transparência (QA)",
                    color=FioGold
                )
            }
            alphaSweepReport?.let { alphaReport ->
                Text(alphaReport.summary(), color=FioTextMuted,
                    fontSize=11.sp)
                Text(
                    "Este APK gera o relatório QA-ABERTURA-DE-VAZADOS-V3. " +
                        "O botão abaixo copia as coordenadas do(s) " +
                        "vazado(s) aberto(s), sem repetir tabelas antigas.",
                    color=FioGold,fontSize=12.sp
                )
                OutlinedButton(
                    modifier=Modifier.fillMaxWidth(),
                    enabled=alphaReport.structuralAudit!=null &&
                        alphaReport.counterLeakEvidence!=null,
                    onClick={
                        RasterQaStructuralAuditReceipt.generate(alphaReport).fold(
                            onSuccess={ receipt ->
                                val clipboard=context.getSystemService(
                                    android.content.Context.CLIPBOARD_SERVICE
                                ) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(
                                    android.content.ClipData.newPlainText(
                                        "Brother Matrizes — Abertura Vazados V3",
                                        receipt
                                    )
                                )
                                message="Auditoria QA V3 copiada: inclui " +
                                    "rota de abertura, conexões e fingerprint. " +
                                    "Não contém arquivo de imagem nem PES."
                            },
                            onFailure={
                                message=it.message ?: "Auditoria QA V3 ausente."
                            }
                        )
                    }
                ) {
                    Text("Copiar localização dos vazados QA V3",
                        color=FioGold)
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(
                        rememberScrollState()
                    ),
                    horizontalArrangement=Arrangement.spacedBy(5.dp)
                ) {
                    listOf(128,160,180,208,224,255).forEach { level ->
                        OutlinedButton(
                            onClick = {
                                showCounterClosure=false
                                showMicrodetail=false
                                selectedAlphaTransition=null
                                selectedAlphaThreshold=level
                            }
                        ) {
                            Text(
                                (if(selectedAlphaThreshold==level) "● " else "") +
                                    level,
                                color=FioGold,fontSize=12.sp
                            )
                        }
                    }
                }
                OutlinedButton(
                    modifier=Modifier.fillMaxWidth(),
                    enabled=alphaReport.counterLeakEvidence!=null &&
                        !counterClosureBusy,
                    onClick={
                        val stamp=revision
                        val picture=prepared?.image
                        if(picture==null ||
                            alphaReport.source !== picture) {
                            message="Imagem original mudou. Analise novamente."
                        } else {
                            counterClosureBusy=true
                            scope.launch {
                                val result=withContext(Dispatchers.Default) {
                                    RasterQaCounterClosureStudy.prepare(
                                        alphaReport,180,208
                                    )
                                }
                                if(revision==stamp &&
                                    prepared?.image === picture) {
                                    result.fold(
                                        onSuccess={
                                            counterClosureStudy=it
                                            selectedAlphaTransition=null
                                            selectedAlphaThreshold=null
                                            showCounterClosure=true
                                            showMicrodetail=false
                                            message=if(it.candidateForVisualReview)
                                                "Prévia candidata pronta: " +
                                                    "revisão visual ainda obrigatória."
                                            else
                                                "A rota não restaurou a " +
                                                    "topologia de forma segura."
                                        },
                                        onFailure={
                                            message=it.message ?:
                                                "Proteção localizada não disponível."
                                        }
                                    )
                                }
                                counterClosureBusy=false
                            }
                        }
                    }
                ) {
                    Text(
                        if(counterClosureBusy)
                            "Conferindo proteção do vazado..."
                        else "Prévia de proteção do vazado 180→208 (QA)",
                        color=FioGold
                    )
                }
                counterClosureStudy?.let { candidate ->
                    Text(candidate.summary(),color=FioTextMuted,
                        fontSize=11.sp)
                    OutlinedButton(
                        modifier=Modifier.fillMaxWidth(),
                        onClick={
                            showCounterClosure=!showCounterClosure
                            showMicrodetail=false
                            if(showCounterClosure) {
                                selectedAlphaTransition=null
                                selectedAlphaThreshold=null
                            }
                        }
                    ) {
                        Text(
                            if(showCounterClosure)
                                "Voltar à imagem original (QA)"
                            else "Exibir proteção localizada (QA)",
                            color=FioGold
                        )
                    }
                    microdetail?.let { detail ->
                        val physicalScreen=remember(
                            detail,microdetailJudgment
                        ) {
                            RasterQaMicrodetailPhysicalGate.inspect(
                                detail,
                                RasterQaMicrodetailIntent.fromCaption(
                                    microdetailJudgment
                                )
                            ).getOrNull()
                        }
                        Text(
                            "Revisão física e visual do microdetalhe " +
                                "(não é liberação para bordar):",
                            color=FioGold,fontSize=12.sp
                        )
                        Text(detail.summary(microdetailJudgment),
                            color=FioTextMuted,fontSize=11.sp)
                        Text(
                            physicalScreen?.summary() ?:
                                "Falha na conferência física QA: " +
                                    "microdetalhe bloqueado.",
                            color=FioGold,fontSize=11.sp
                        )
                        OutlinedButton(
                            modifier=Modifier.fillMaxWidth(),
                            onClick={
                                showMicrodetail=!showMicrodetail
                                if(showMicrodetail) {
                                    showCounterClosure=false
                                    selectedAlphaThreshold=null
                                    selectedAlphaTransition=null
                                }
                            }
                        ) {
                            Text(
                                if(showMicrodetail)
                                    "Fechar detalhe ampliado QA"
                                else
                                    "Ampliar vazado crítico 12× (QA)",
                                color=FioGold
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement=Arrangement.spacedBy(5.dp)
                        ) {
                            OutlinedButton(
                                modifier=Modifier.weight(1f),
                                onClick={
                                    microdetailJudgment=
                                        "aparenta ser detalhe intencional"
                                }
                            ) {
                                Text("Detalhe intencional?",
                                    color=FioGold,fontSize=10.sp)
                            }
                            OutlinedButton(
                                modifier=Modifier.weight(1f),
                                onClick={
                                    microdetailJudgment=
                                        "possível artefato de imagem"
                                }
                            ) {
                                Text("Possível ruído?",
                                    color=FioGold,fontSize=10.sp)
                            }
                        }
                        OutlinedButton(
                            modifier=Modifier.fillMaxWidth(),
                            onClick={
                                val textReport=physicalScreen?.summary()
                                if(textReport==null) {
                                    message="Conferência QA V6 inválida. " +
                                        "Nenhuma decisão de bordado liberada."
                                } else {
                                    val clipboard=context.getSystemService(
                                        android.content.Context.CLIPBOARD_SERVICE
                                    ) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(
                                        android.content.ClipData.newPlainText(
                                            "Brother Matrizes — Segurança QA V6",
                                            textReport
                                        )
                                    )
                                    message="Segurança QA V6 copiada: " +
                                        "PES e digitalização ainda bloqueados."
                                }
                            }
                        ) {
                            Text("Copiar segurança do microdetalhe QA V6",
                                color=FioGold)
                        }
                    }
                    OutlinedButton(
                        modifier=Modifier.fillMaxWidth(),
                        onClick={
                            val previous=RasterQaStructuralAuditReceipt
                                .generate(alphaReport).getOrNull()
                            if(previous==null) {
                                message="Auditoria de origem inválida."
                            } else {
                                val payload="QA-PROTECAO-VAZADO-V4 | " +
                                    candidate.summary() + " | " +
                                    previous
                                val clipboard=context.getSystemService(
                                    android.content.Context.CLIPBOARD_SERVICE
                                ) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(
                                    android.content.ClipData.newPlainText(
                                        "Brother Matrizes — Proteção QA V4",
                                        payload
                                    )
                                )
                                message="Prova de proteção QA V4 copiada; " +
                                    "nenhuma imagem nem PES exportados."
                            }
                        }
                    ) {
                        Text("Copiar proteção localizada QA V4",
                            color=FioGold)
                    }
                }
                Text(
                    "A análise destaca conexões alfa em magenta, " +
                        "rotas de abertura dos vazados em ciano. " +
                        "Ela não identifica " +
                        "automaticamente letras nem autoriza bordados.",
                    color=FioTextMuted,fontSize=11.sp
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(
                        rememberScrollState()
                    ),
                    horizontalArrangement=Arrangement.spacedBy(5.dp)
                ) {
                    listOf(160 to 180,180 to 208,208 to 224,224 to 240)
                        .forEach { pair ->
                            OutlinedButton(
                                onClick = {
                                    showCounterClosure=false
                                    showMicrodetail=false
                                    selectedAlphaThreshold=null
                                    selectedAlphaTransition=pair
                                }
                            ) {
                                Text(
                                    (if(selectedAlphaTransition==pair)
                                        "● " else "") +
                                        "Perda " + pair.first + "→" +
                                        pair.second,
                                    color=FioGold,fontSize=12.sp
                                )
                            }
                        }
                }
                OutlinedButton(
                    modifier=Modifier.fillMaxWidth(),
                    onClick = {
                        val clipboard=context.getSystemService(
                            android.content.Context.CLIPBOARD_SERVICE
                        ) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText(
                                "Brother Matrizes — Limiares alfa QA",
                                alphaReport.summary()
                            )
                        )
                        message="Estudo de transparência copiado: " +
                            "apenas contagens, nenhuma imagem foi enviada."
                    }
                ) {
                    Text("Copiar estudo de transparência (QA)",
                        color=FioGold)
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
                    enabled = prepared != null && fitted &&
                        !checkingFlatSource && !preparing && !simulating,
                    onClick = {
                        val original = prepared?.image
                            ?: return@OutlinedButton
                        val stamp = revision
                        checkingFlatSource = true
                        message = ""
                        scope.launch {
                            val checked = withContext(Dispatchers.Default) {
                                RasterTextureReconstructionGate.prepare(
                                    original,study
                                )
                            }
                            checkingFlatSource = false
                            if (revision == stamp &&
                                prepared?.image === original &&
                                flatStudy === study) {
                                checked.fold(
                                    onSuccess = {
                                        flatSourceCandidate = it
                                        showFlatStudy = true
                                        selectedElementId = 0
                                        message = it.message()
                                    },
                                    onFailure = {
                                        flatSourceCandidate = null
                                        message = "A referência simplificada " +
                                            "ainda precisa de reconstrução: " +
                                            (it.message ?:
                                                "reveja as formas e os detalhes.")
                                    }
                                )
                            }
                        }
                    }
                ) {
                    Text(
                        if (checkingFlatSource)
                            "Conferindo fidelidade da silhueta..."
                        else "Verificar referência plana para pontos (QA)",
                        color = FioGold
                    )
                }
                flatSourceCandidate?.let { candidate ->
                    Text(
                        candidate.message(),
                        color = FioTextMuted,
                        fontSize = 11.sp
                    )
                    if (acceptedFlatSource === candidate) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !mixedBusy && !simulating,
                            onClick = {
                                acceptedFlatSource = null
                                message = "Referência QA removida. " +
                                    "As prévias dessa referência serão " +
                                    "descartadas; foto original intacta."
                            }
                        ) {
                            Text(
                                "Voltar a usar foto original nas prévias QA",
                                color = FioGold
                            )
                        }
                    } else {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !mixedBusy && !simulating &&
                                prepared?.image === candidate.original,
                            onClick = {
                                if (prepared?.image === candidate.original &&
                                    flatStudy === study) {
                                    acceptedFlatSource = candidate
                                    mixedOpen = false
                                    mixedSelectedPart = 0
                                    showFlatStudy = true
                                    selectedElementId = 0
                                    message = "Referência plana aceita para " +
                                        "prévia QA. Confirme separadamente " +
                                        "cada componente e suas agulhadas. " +
                                        "A foto original foi preservada."
                                }
                            }
                        ) {
                            Text(
                                "Aceitar cores planas como referência QA",
                                color = FioGold
                            )
                        }
                    }
                }
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
                // Operator-facing QA proof of the independent raster
                // boundary-to-running-needle pipeline. Not a generic fill,
                // and never an exportable image matrix.
                if (BuildConfig.DEBUG) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = prepared?.image != null && fitted &&
                            !preparing && !simulating && !testingFlatStitches &&
                            !testingRunningOutline && !studyingTexture &&
                            regionalSampling?.needsDetailReview == false,
                        onClick = {
                            val source = prepared?.image ?: return@OutlinedButton
                            val snapshotRevision = revision
                            val snapshotWidth = widthMm
                            val snapshotHoop = hoop
                            val snapshotMode = displayMode
                            val snapshotConnections = showConnections
                            testingRunningOutline = true
                            message = ""
                            scope.launch {
                                val draft = withContext(Dispatchers.Default) {
                                    RasterQaRunningStitchDraft.create(
                                        source, snapshotWidth, snapshotHoop
                                    )
                                }
                                if (snapshotRevision == revision &&
                                    prepared?.image === source &&
                                    widthMm == snapshotWidth &&
                                    hoop == snapshotHoop) {
                                    testingRunningOutline = false
                                    draft.fold(
                                        onSuccess = {
                                            qaTechnicalReport =
                                                "QA: contornos em STITCH/JUMP/TRIM; " +
                                                it.outlineCount + " contorno(s), " +
                                                it.counterCount + " vazado(s), " +
                                                it.thinContoursRequiringReview +
                                                " detalhe(s) finos; " +
                                                "bordado físico e PES NÃO aprovados."
                                            qaReportRevision = revision
                                            onSimulate(
                                                it.design, snapshotHoop,
                                                snapshotMode, snapshotConnections
                                            )
                                        },
                                        onFailure = {
                                            message =
                                                "Contorno corrido QA recusado: " +
                                                (it.message ?:
                                                    "Revisão manual necessária.")
                                        }
                                    )
                                }
                            }
                        }
                    ) {
                        Text(
                            if (testingRunningOutline)
                                "Auditando contornos reais..."
                            else "Simular contornos em ponto corrido (QA)",
                            color = FioGold
                        )
                    }
                    Text(
                        "Experimento somente do contorno: preserva vazados, " +
                            "salta entre regiões e recusa trajetos inseguros. " +
                            "Não preenche a imagem nem gera PES.",
                        color = FioTextMuted, fontSize = 11.sp
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
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !preparing && !simulating && !mixedBusy &&
                    prepared != null && fitted,
                onClick = {
                    if (mixedOpen) {
                        mixedOpen = false
                        mixedSelectedPart = 0
                    } else if (mixedParts.isNotEmpty()) {
                        mixedOpen = true
                    } else {
                        val original = mixedSource ?: return@OutlinedButton
                        val chosenHoop = hoop
                        val chosenWidth = widthMm
                        val acceptedSnapshot = acceptedFlatSource
                        val stamp = revision
                        mixedBusy = true
                        message = ""
                        scope.launch {
                            val detected = withContext(Dispatchers.Default) {
                                RasterExactColorComponentDetector.detect(original)
                                    .mapCatching { parts ->
                                        require(parts.size in 2..RasterQaComponentBudget.MAX_COMPONENTS) {
                                            "A montagem QA requer 2 a 24 elementos. " +
                                                "Recorte a arte em partes menores."
                                        }
                                        parts
                                    }
                            }
                            mixedBusy = false
                            if (revision == stamp &&
                                mixedSource === original &&
                                acceptedFlatSource === acceptedSnapshot &&
                                hoop == chosenHoop && widthMm == chosenWidth) {
                                detected.fold(
                                    onSuccess = { parts ->
                                        mixedParts = parts
                                        mixedOrder = parts.map { it.id }
                                        mixedTechniques = parts.associate {
                                            part ->
                                            val px = minOf(
                                                part.mask.maxX-part.mask.minX+1,
                                                part.mask.maxY-part.mask.minY+1
                                            )
                                            val smallMm = px * chosenWidth /
                                                original.width
                                            part.id to when {
                                                smallMm < 1.15f ->
                                                    RasterTechniqueKind.RUNNING
                                                smallMm >= 7f ->
                                                    RasterTechniqueKind.TATAMI
                                                else -> RasterTechniqueKind.SATIN
                                            }
                                        }
                                        mixedDrafts = emptyMap()
                                        mixedAudits = emptyMap()
                                        mixedTopology = emptyMap()
                                        mixedSatinRails = emptyMap()
                                        mixedSatinUndo = emptyMap()
                                        mixedSplitCandidates = emptyMap()
                                        mixedSplitViewed = emptySet()
                                        mixedCurveHotspots = emptyMap()
                                        mixedRailSection = 0
                                        mixedRailPair = 0
                                        mixedViewed = emptySet()
                                        mixedConfirmed = emptySet()
                                        mixedSelectedPart = 0
                                        mixedViewMode = 0
                                        mixedOpen = true
                                    },
                                    onFailure = {
                                        message = it.message ?:
                                            "Não foi possível separar as partes QA."
                                    }
                                )
                            }
                        }
                    }
                }
            ) {
                Text(
                    when {
                        mixedBusy -> "Preparando revisão multitécnica..."
                        mixedOpen -> "Fechar revisão de camadas QA"
                        else -> "Montar Satin, Tatami e corrido (QA)"
                    }, color = FioGold
                )
            }
            if (mixedOpen && mixedParts.isNotEmpty()) {
                Text(
                    (if (acceptedFlatSource != null)
                        "Fonte de trabalho: cores planas explicitamente " +
                            "aceitas para QA. A fotografia com textura " +
                            "permanece original e não foi transformada em pontos. "
                     else
                        "Fonte de trabalho: imagem original com cores exatas. ") +
                        "Escolha técnica e ordem, confira todas as pontadas " +
                        "e confirme cada componente. Não é PES.",
                    color = FioTextMuted, fontSize = 11.sp
                )
                mixedOrder.forEachIndexed { orderIndex, partId ->
                    val part = mixedParts.firstOrNull { it.id == partId }
                    if (part != null) {
                        val chosen = mixedTechniques[part.id] ?:
                            RasterTechniqueKind.SATIN
                        val draft = mixedDrafts[part.id]
                        val confirmed = part.id in mixedConfirmed
                        Text(
                            "Parte " + part.id + " • ordem " + (orderIndex + 1) +
                                " de " + mixedOrder.size +
                                " • " + part.pixelCount + " pixels",
                            color = FioText, fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            Modifier.fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    mixedSelectedPart = part.id
                                    mixedViewMode = 0
                                    mixedRailSection = 0
                                    mixedRailPair = 0
                                }
                            ) { Text("Ver parte", color = FioGold) }
                            OutlinedButton(
                                enabled = orderIndex > 0 && !mixedBusy,
                                onClick = {
                                    val items = mixedOrder.toMutableList()
                                    val t = items[orderIndex - 1]
                                    items[orderIndex - 1] = items[orderIndex]
                                    items[orderIndex] = t
                                    mixedOrder = items
                                }
                            ) { Text("Subir", color = FioGold) }
                            OutlinedButton(
                                enabled = orderIndex < mixedOrder.lastIndex &&
                                    !mixedBusy,
                                onClick = {
                                    val items = mixedOrder.toMutableList()
                                    val t = items[orderIndex + 1]
                                    items[orderIndex + 1] = items[orderIndex]
                                    items[orderIndex] = t
                                    mixedOrder = items
                                }
                            ) { Text("Descer", color = FioGold) }
                            for (kind in RasterTechniqueKind.entries) {
                                OutlinedButton(
                                    enabled = !mixedBusy,
                                    onClick = {
                                        if (mixedTechniques[part.id] != kind) {
                                            mixedTechniques = mixedTechniques +
                                                (part.id to kind)
                                            mixedDrafts = mixedDrafts - part.id
                                            mixedSatinRails = mixedSatinRails - part.id
                                            mixedSatinUndo = mixedSatinUndo - part.id
                                            mixedSplitCandidates =
                                                mixedSplitCandidates - part.id
                                            mixedSplitViewed = mixedSplitViewed - part.id
                                            mixedCurveHotspots = mixedCurveHotspots - part.id
                                            mixedAudits = mixedAudits - part.id
                                            mixedViewed = mixedViewed - part.id
                                            mixedConfirmed = mixedConfirmed - part.id
                                            if (mixedSelectedPart == part.id) mixedViewMode = 0
                                        }
                                    }
                                ) {
                                    val name = when (kind) {
                                        RasterTechniqueKind.SATIN -> "Satin"
                                        RasterTechniqueKind.TATAMI -> "Tatami"
                                        RasterTechniqueKind.RUNNING -> "Corrido"
                                    }
                                    Text(
                                        (if (chosen == kind) "● " else "") + name,
                                        color = FioGold
                                    )
                                }
                            }
                        }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !mixedBusy && !simulating,
                            onClick = {
                                val original = mixedSource
                                    ?: return@OutlinedButton
                                val acceptedSnapshot = acceptedFlatSource
                                val stamp = revision
                                val partSnapshot = part
                                mixedBusy = true
                                message = ""
                                scope.launch {
                                    val result = withContext(Dispatchers.Default) {
                                        RasterLetterTopologyReview.inspect(
                                            original, partSnapshot
                                        )
                                    }
                                    mixedBusy = false
                                    if (revision == stamp &&
                                        acceptedFlatSource === acceptedSnapshot &&
                                        mixedSource === original &&
                                        mixedParts.any { it === partSnapshot }) {
                                        result.fold(
                                            onSuccess = {
                                                mixedTopology = mixedTopology +
                                                    (partSnapshot.id to it)
                                                mixedSelectedPart = partSnapshot.id
                                                mixedViewMode = 0
                                                message = it.guidance()
                                            },
                                            onFailure = {
                                                message = "Contorno não confiável: " +
                                                    (it.message ?: "refaça a forma.")
                                            }
                                        )
                                    }
                                }
                            }
                        ) {
                            Text("Analisar curvas e vazados (QA)", color = FioGold)
                        }
                        mixedTopology[part.id]?.let { topology ->
                            Text(
                                topology.guidance(),
                                color = if (topology.manualGeometryRequired)
                                    FioGold else FioTextMuted,
                                fontSize = 11.sp
                            )
                        }
                        if (chosen == RasterTechniqueKind.SATIN) {
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !mixedBusy && !simulating,
                                onClick = {
                                    val source = mixedSource
                                        ?: return@OutlinedButton
                                    val currentPart = part
                                    val currentReference = acceptedFlatSource
                                    val currentWidth = widthMm
                                    val currentHoop = hoop
                                    val stamp = revision
                                    mixedBusy = true
                                    message = ""
                                    scope.launch {
                                        val result = withContext(Dispatchers.Default) {
                                            RasterLetterEditableSatinReview.propose(
                                                source,currentPart,currentWidth,currentHoop
                                            ).mapCatching { draft ->
                                                draft to RasterMixedStitchVisualAudit
                                                    .inspect(source,currentPart,
                                                        draft.preview,currentWidth,currentHoop)
                                                    .getOrThrow()
                                            }
                                        }
                                        mixedBusy = false
                                        if (revision == stamp &&
                                            mixedSource === source &&
                                            acceptedFlatSource === currentReference &&
                                            mixedParts.any { it === currentPart } &&
                                            mixedTechniques[currentPart.id] ==
                                                RasterTechniqueKind.SATIN &&
                                            widthMm == currentWidth &&
                                            hoop == currentHoop) {
                                            result.fold(
                                                onSuccess = { (draft, audit) ->
                                                    mixedSatinRails = mixedSatinRails +
                                                        (currentPart.id to draft)
                                                    mixedCurveHotspots =
                                                        mixedCurveHotspots - currentPart.id
                                                    mixedSatinUndo = mixedSatinUndo -
                                                        currentPart.id
                                                    mixedSplitCandidates =
                                                        mixedSplitCandidates - currentPart.id
                                                    mixedSplitViewed =
                                                        mixedSplitViewed - currentPart.id
                                                    mixedDrafts = mixedDrafts +
                                                        (currentPart.id to draft.preview)
                                                    mixedAudits = mixedAudits +
                                                        (currentPart.id to audit)
                                                    mixedConfirmed = mixedConfirmed -
                                                        currentPart.id
                                                    mixedViewed = mixedViewed - currentPart.id
                                                    mixedRailSection = 0
                                                    mixedRailPair = 0
                                                    mixedSelectedPart = currentPart.id
                                                    mixedViewMode = 0
                                                    message = draft.summary()
                                                },
                                                onFailure = {
                                                    message = "Satin precisa de " +
                                                        "reconstrução manual: " +
                                                        (it.message ?: "trilhos inadequados.")
                                                }
                                            )
                                        }
                                    }
                                }
                            ) {
                                Text("Propor trilhos Satin editáveis (QA)",
                                    color = FioGold)
                            }
                            mixedSatinRails[part.id]?.let { current ->
                                val sectionId = mixedRailSection.coerceIn(
                                    0,current.sections.lastIndex
                                )
                                val section = current.sections[sectionId]
                                val railId = mixedRailPair.coerceIn(
                                    0,section.rails.lastIndex
                                )
                                Text(
                                    current.summary() +
                                        " Seção " + (sectionId+1) + "/" +
                                        current.sections.size + ", par " +
                                        (railId+1) + "/" + section.rails.size +
                                        ", revisão " + current.revision + ".",
                                    color = FioTextMuted,fontSize = 11.sp
                                )
                                if (current.revision > 0) {
                                    Text(
                                        current.changeSummary(),
                                        color = FioTextMuted,fontSize = 11.sp
                                    )
                                }
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !mixedBusy && !simulating,
                                    onClick = {
                                        val source = mixedSource
                                            ?: return@OutlinedButton
                                        val active = mixedSatinRails[part.id]
                                            ?: return@OutlinedButton
                                        if (active !== current)
                                            return@OutlinedButton
                                        val selectedPart = part
                                        val chosenWidth = widthMm
                                        val chosenHoop = hoop
                                        val acceptedSnapshot = acceptedFlatSource
                                        val stamp = revision
                                        mixedBusy = true
                                        message = ""
                                        scope.launch {
                                            val report = withContext(
                                                Dispatchers.Default
                                            ) {
                                                RasterSatinCurveHotspotReview.inspect(
                                                    source,selectedPart,active,
                                                    chosenWidth,chosenHoop
                                                )
                                            }
                                            mixedBusy = false
                                            if (revision == stamp &&
                                                mixedSource === source &&
                                                acceptedFlatSource === acceptedSnapshot &&
                                                mixedSatinRails[selectedPart.id] === active &&
                                                mixedTechniques[selectedPart.id] ==
                                                    RasterTechniqueKind.SATIN &&
                                                widthMm == chosenWidth &&
                                                hoop == chosenHoop) {
                                                report.fold(
                                                    onSuccess = { checked ->
                                                        mixedCurveHotspots =
                                                            mixedCurveHotspots +
                                                            (selectedPart.id to
                                                                (active to checked))
                                                        message = checked.message()
                                                    },
                                                    onFailure = {
                                                        message =
                                                            "Curvatura não auditada: " +
                                                            (it.message ?:
                                                                "refaça a prévia STITCH.")
                                                    }
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Text("Diagnosticar curvas dos trilhos (QA)",
                                        color = FioGold)
                                }
                                mixedCurveHotspots[part.id]?.takeIf {
                                    it.first === current
                                }?.second?.let { curveReport ->
                                    Text(
                                        curveReport.message(),
                                        color = FioTextMuted,fontSize = 11.sp
                                    )
                                    curveReport.important.take(3).forEach { hot ->
                                        Text(
                                            hot.explanation(),
                                            color = FioTextMuted,fontSize = 11.sp
                                        )
                                    }
                                    curveReport.important.firstOrNull()?.let { critical ->
                                        OutlinedButton(
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = !mixedBusy,
                                            onClick = {
                                                if (mixedSatinRails[part.id] === current) {
                                                    mixedRailSection =
                                                        critical.sectionIndex
                                                    mixedRailPair = critical.pairIndex
                                                    mixedSelectedPart = part.id
                                                    mixedViewMode = 1
                                                }
                                            }
                                        ) {
                                            Text("Ir ao trecho Satin prioritário",
                                                color = FioGold)
                                        }
                                    }
                                }
                                // A division changes stitch topology. It is
                                // an explicit PROPOSAL, not an implicit rail edit.
                                val canTrySplit = section.rails.size >= 12 &&
                                    railId in 5..(section.rails.size-6) &&
                                    current.sections.size < 24
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = canTrySplit && !mixedBusy && !simulating,
                                    onClick = {
                                        val original = mixedSource
                                            ?: return@OutlinedButton
                                        val selectedPart = part
                                        val snapshot = mixedSatinRails[part.id]
                                            ?: return@OutlinedButton
                                        if (snapshot !== current) return@OutlinedButton
                                        val chosenWidth = widthMm
                                        val chosenHoop = hoop
                                        val acceptedSnapshot = acceptedFlatSource
                                        val stamp = revision
                                        mixedBusy = true
                                        message = ""
                                        scope.launch {
                                            val candidate = withContext(
                                                Dispatchers.Default
                                            ) {
                                                RasterLetterEditableSatinReview
                                                    .proposeIndependentSplit(
                                                        original,selectedPart,
                                                        snapshot,sectionId,railId,
                                                        chosenWidth,chosenHoop
                                                    ).mapCatching { draft ->
                                                        draft to
                                                            RasterMixedStitchVisualAudit
                                                                .inspect(
                                                                    original,selectedPart,
                                                                    draft.preview,chosenWidth,
                                                                    chosenHoop
                                                                ).getOrThrow()
                                                    }
                                            }
                                            mixedBusy = false
                                            if (revision == stamp &&
                                                mixedSource === original &&
                                                acceptedFlatSource === acceptedSnapshot &&
                                                mixedSatinRails[selectedPart.id] === snapshot &&
                                                mixedTechniques[selectedPart.id] ==
                                                    RasterTechniqueKind.SATIN &&
                                                widthMm == chosenWidth &&
                                                hoop == chosenHoop) {
                                                candidate.fold(
                                                    onSuccess = { (draft,audit) ->
                                                        mixedSplitCandidates =
                                                            mixedSplitCandidates +
                                                            (selectedPart.id to
                                                                Triple(snapshot,draft,audit))
                                                        mixedSplitViewed =
                                                            mixedSplitViewed - selectedPart.id
                                                        mixedSelectedPart = selectedPart.id
                                                        mixedViewMode = 0
                                                        message = "Divisão candidata: " +
                                                            draft.summary() + " " +
                                                            draft.changeSummary() +
                                                            " Confira STITCH da divisão " +
                                                            "antes de aceitar."
                                                    },
                                                    onFailure = {
                                                        message = "Divisão Satin recusada: " +
                                                            (it.message ?:
                                                                "TRIM, cobertura ou " +
                                                                "densidade incompatíveis.")
                                                    }
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Text("Propor corte na junção selecionada (QA)",
                                        color = FioGold)
                                }
                                mixedSplitCandidates[part.id]?.takeIf {
                                    it.first === current
                                }?.let { candidate ->
                                    Text(
                                        "Prévia candidata com duas seções " +
                                            "independentes. " +
                                            candidate.second.changeSummary(),
                                        color = FioTextMuted,fontSize = 11.sp
                                    )
                                    OutlinedButton(
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !mixedBusy,
                                        onClick = {
                                            if(mixedSatinRails[part.id] === current) {
                                                mixedSelectedPart = part.id
                                                mixedViewMode = 4
                                                mixedSplitViewed =
                                                    mixedSplitViewed + part.id
                                            }
                                        }
                                    ) {
                                        Text("Conferir STITCH do corte proposto",
                                            color = FioGold)
                                    }
                                    Button(
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !mixedBusy && !simulating &&
                                            part.id in mixedSplitViewed,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = FioGold
                                        ),
                                        onClick = {
                                            val active = mixedSplitCandidates[part.id]
                                            if(active === candidate &&
                                                active.first ===
                                                    mixedSatinRails[part.id] &&
                                                part.id in mixedSplitViewed &&
                                                mixedTechniques[part.id] ==
                                                    RasterTechniqueKind.SATIN &&
                                                !active.second.exportPermitted) {
                                                mixedSatinRails =
                                                    mixedSatinRails +
                                                    (part.id to active.second)
                                                // Topology changed; the old
                                                // endpoint undo history no
                                                // longer has the same indices.
                                                mixedSatinUndo =
                                                    mixedSatinUndo - part.id
                                                mixedCurveHotspots =
                                                    mixedCurveHotspots - part.id
                                                mixedDrafts = mixedDrafts +
                                                    (part.id to active.second.preview)
                                                mixedAudits = mixedAudits +
                                                    (part.id to active.third)
                                                mixedConfirmed =
                                                    mixedConfirmed - part.id
                                                mixedViewed = mixedViewed - part.id
                                                mixedSplitCandidates =
                                                    mixedSplitCandidates - part.id
                                                mixedSplitViewed =
                                                    mixedSplitViewed - part.id
                                                mixedRailSection = sectionId
                                                mixedRailPair = 0
                                                mixedSelectedPart = part.id
                                                mixedViewMode = 0
                                                message = "Divisão Satin aplicada " +
                                                    "após revisão QA. Conferir " +
                                                    "STITCH e confirmar esta parte " +
                                                    "novamente antes de simular."
                                            }
                                        }
                                    ) {
                                        Text("Aceitar divisão revisada (QA)",
                                            color = FioBackground,
                                            fontWeight = FontWeight.Bold)
                                    }
                                }
                                Row(
                                    Modifier.fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement =
                                        Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        enabled = sectionId>0 && !mixedBusy,
                                        onClick = {
                                            mixedRailSection = sectionId-1
                                            mixedRailPair = 0
                                        }
                                    ) { Text("Seção −", color = FioGold) }
                                    OutlinedButton(
                                        enabled = sectionId<current.sections.lastIndex &&
                                            !mixedBusy,
                                        onClick = {
                                            mixedRailSection = sectionId+1
                                            mixedRailPair = 0
                                        }
                                    ) { Text("Seção +", color = FioGold) }
                                    OutlinedButton(
                                        enabled = railId>0 && !mixedBusy,
                                        onClick = { mixedRailPair = railId-1 }
                                    ) { Text("Par −", color = FioGold) }
                                    OutlinedButton(
                                        enabled = railId<section.rails.lastIndex &&
                                            !mixedBusy,
                                        onClick = { mixedRailPair = railId+1 }
                                    ) { Text("Par +", color = FioGold) }
                                }
                                Row(
                                    Modifier.fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement =
                                        Arrangement.spacedBy(6.dp)
                                ) {
                                    for (side in RasterLetterRailEdge.entries) {
                                        OutlinedButton(
                                            enabled = !mixedBusy,
                                            onClick = { mixedRailEdge = side }
                                        ) {
                                            Text(
                                                (if(mixedRailEdge == side) "● " else "") +
                                                    "Borda " + side.name,
                                                color = FioGold
                                            )
                                        }
                                    }
                                    for (axis in RasterLetterRailAxis.entries) {
                                        OutlinedButton(
                                            enabled = !mixedBusy,
                                            onClick = { mixedRailAxis = axis }
                                        ) {
                                            Text(
                                                (if(mixedRailAxis == axis) "● " else "") +
                                                    axis.name,
                                                color = FioGold
                                            )
                                        }
                                    }
                                }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement =
                                        Arrangement.spacedBy(8.dp)
                                ) {
                                    for (distance in listOf(-.1f,.1f)) {
                                        OutlinedButton(
                                            modifier = Modifier.weight(1f),
                                            enabled = !mixedBusy && !simulating,
                                            onClick = {
                                                val source = mixedSource
                                                    ?: return@OutlinedButton
                                                val previous = mixedSatinRails[part.id]
                                                    ?: return@OutlinedButton
                                                if(previous !== current)
                                                    return@OutlinedButton
                                                val currentPart = part
                                                val stamp = revision
                                                val currentReference = acceptedFlatSource
                                                val currentWidth = widthMm
                                                val currentHoop = hoop
                                                val edge = mixedRailEdge
                                                val axis = mixedRailAxis
                                                mixedBusy = true
                                                message = ""
                                                scope.launch {
                                                    val result = withContext(
                                                        Dispatchers.Default
                                                    ) {
                                                        RasterLetterEditableSatinReview
                                                            .nudge(
                                                                source,currentPart,
                                                                previous,sectionId,railId,
                                                                edge,axis,distance,
                                                                currentWidth,currentHoop
                                                            ).mapCatching { draft ->
                                                                draft to
                                                                    RasterMixedStitchVisualAudit
                                                                    .inspect(
                                                                        source,currentPart,
                                                                        draft.preview,
                                                                        currentWidth,currentHoop
                                                                    ).getOrThrow()
                                                            }
                                                    }
                                                    mixedBusy = false
                                                    if(revision == stamp &&
                                                        mixedSource === source &&
                                                        acceptedFlatSource ===
                                                            currentReference &&
                                                        mixedSatinRails[currentPart.id] ===
                                                            previous &&
                                                        mixedTechniques[currentPart.id] ==
                                                            RasterTechniqueKind.SATIN &&
                                                        widthMm == currentWidth &&
                                                        hoop == currentHoop) {
                                                        result.fold(
                                                            onSuccess = { (draft,audit) ->
                                                                mixedSatinUndo =
                                                                    mixedSatinUndo +
                                                                    (currentPart.id to
                                                                        (mixedSatinUndo[
                                                                            currentPart.id
                                                                        ].orEmpty()
                                                                            .takeLast(19) +
                                                                            previous))
                                                                mixedSatinRails =
                                                                    mixedSatinRails +
                                                                    (currentPart.id to draft)
                                                                mixedCurveHotspots =
                                                                    mixedCurveHotspots -
                                                                    currentPart.id
                                                                mixedSplitCandidates =
                                                                    mixedSplitCandidates -
                                                                    currentPart.id
                                                                mixedSplitViewed =
                                                                    mixedSplitViewed - currentPart.id
                                                                mixedDrafts = mixedDrafts +
                                                                    (currentPart.id to
                                                                        draft.preview)
                                                                mixedAudits = mixedAudits +
                                                                    (currentPart.id to audit)
                                                                mixedConfirmed =
                                                                    mixedConfirmed -
                                                                    currentPart.id
                                                                mixedViewed = mixedViewed -
                                                                    currentPart.id
                                                                mixedSelectedPart=currentPart.id
                                                                mixedViewMode=0
                                                                message=draft.summary() +
                                                                    " " + draft.changeSummary() +
                                                                    " Reconfira STITCH."
                                                            },
                                                            onFailure = {
                                                                message = "Ajuste recusado: " +
                                                                    (it.message ?:
                                                                        "validação dos pontos.")
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        ) {
                                            Text(
                                                if(distance<0f) "− 0,1 mm" else
                                                    "+ 0,1 mm",
                                                color = FioGold
                                            )
                                        }
                                    }
                                }
                                val previousVersion = mixedSatinUndo[part.id]
                                    .orEmpty().lastOrNull()
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = previousVersion != null &&
                                        !mixedBusy && !simulating,
                                    onClick = {
                                        val previous = mixedSatinUndo[part.id]
                                            .orEmpty().lastOrNull()
                                            ?: return@OutlinedButton
                                        val latest = mixedSatinRails[part.id]
                                            ?: return@OutlinedButton
                                        if(latest !== current)
                                            return@OutlinedButton
                                        val original = mixedSource
                                            ?: return@OutlinedButton
                                        val selectedPart = part
                                        val snapshot = mixedSatinUndo[part.id]
                                            .orEmpty().toList()
                                        val currentReference = acceptedFlatSource
                                        val currentWidth = widthMm
                                        val currentHoop = hoop
                                        val stamp = revision
                                        mixedBusy = true
                                        message = ""
                                        scope.launch {
                                            val restored = withContext(
                                                Dispatchers.Default
                                            ) {
                                                RasterLetterEditableSatinReview
                                                    .restorePrevious(
                                                        original,selectedPart,
                                                        latest,previous,
                                                        currentWidth,currentHoop
                                                    ).mapCatching { draft ->
                                                        draft to RasterMixedStitchVisualAudit
                                                            .inspect(
                                                                original,selectedPart,
                                                                draft.preview,
                                                                currentWidth,currentHoop
                                                            ).getOrThrow()
                                                    }
                                            }
                                            mixedBusy = false
                                            if (revision == stamp &&
                                                mixedSource === original &&
                                                acceptedFlatSource === currentReference &&
                                                mixedSatinRails[selectedPart.id] === latest &&
                                                mixedSatinUndo[selectedPart.id] == snapshot &&
                                                mixedTechniques[selectedPart.id] ==
                                                    RasterTechniqueKind.SATIN &&
                                                widthMm == currentWidth &&
                                                hoop == currentHoop) {
                                                restored.fold(
                                                    onSuccess = { (draft,audit) ->
                                                        mixedSatinUndo = mixedSatinUndo +
                                                            (selectedPart.id to snapshot
                                                                .dropLast(1))
                                                        mixedSatinRails = mixedSatinRails +
                                                            (selectedPart.id to draft)
                                                        mixedCurveHotspots =
                                                            mixedCurveHotspots -
                                                            selectedPart.id
                                                        mixedSplitCandidates =
                                                            mixedSplitCandidates -
                                                            selectedPart.id
                                                        mixedSplitViewed =
                                                            mixedSplitViewed - selectedPart.id
                                                        mixedDrafts = mixedDrafts +
                                                            (selectedPart.id to draft.preview)
                                                        mixedAudits = mixedAudits +
                                                            (selectedPart.id to audit)
                                                        mixedConfirmed = mixedConfirmed -
                                                            selectedPart.id
                                                        mixedViewed = mixedViewed -
                                                            selectedPart.id
                                                        mixedSelectedPart = selectedPart.id
                                                        mixedViewMode = 0
                                                        message = "Último ajuste desfeito. " +
                                                            "Pontos restaurados e auditados. " +
                                                            "Confira STITCH novamente."
                                                    },
                                                    onFailure = {
                                                        message = "Não foi possível " +
                                                            "restaurar: " +
                                                            (it.message ?:
                                                                "fonte ou pontos alterados.")
                                                    }
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Text("Desfazer último ajuste Satin (QA)",
                                        color = FioGold)
                                }
                                Text(
                                    "Ajustes limitados a 0,2 mm por operação, " +
                                        "0,5 mm acumulado por ponta e 8 mm " +
                                        "de deslocamento total. Desfazer também " +
                                        "exige nova inspeção de STITCH e agulha.",
                                    color = FioTextMuted,fontSize = 11.sp
                                )
                            }
                        }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !mixedBusy && !simulating,
                            onClick = {
                                val original = mixedSource
                                    ?: return@OutlinedButton
                                val acceptedSnapshot = acceptedFlatSource
                                val partSnapshot = part
                                val kind = mixedTechniques[part.id]
                                    ?: return@OutlinedButton
                                val chosenWidth = widthMm
                                val chosenHoop = hoop
                                val stamp = revision
                                mixedBusy = true
                                message = ""
                                scope.launch {
                                    val proposal = withContext(Dispatchers.Default) {
                                        RasterMixedTechniqueReviewWorkflow.prepare(
                                            original,partSnapshot,kind,
                                            chosenWidth,chosenHoop
                                        ).mapCatching { design ->
                                            design to RasterMixedStitchVisualAudit.inspect(
                                                original,partSnapshot,design,chosenWidth,chosenHoop
                                            ).getOrThrow()
                                        }
                                    }
                                    mixedBusy = false
                                    if (revision == stamp &&
                                        mixedSource === original &&
                                        acceptedFlatSource === acceptedSnapshot &&
                                        mixedParts.any { it === partSnapshot } &&
                                        mixedTechniques[partSnapshot.id] == kind &&
                                        widthMm == chosenWidth &&
                                        hoop == chosenHoop) {
                                        proposal.fold(
                                            onSuccess = { (design, audit) ->
                                                mixedSatinRails = mixedSatinRails -
                                                    partSnapshot.id
                                                mixedCurveHotspots =
                                                    mixedCurveHotspots - partSnapshot.id
                                                mixedSplitCandidates =
                                                    mixedSplitCandidates - partSnapshot.id
                                                mixedSplitViewed =
                                                    mixedSplitViewed - partSnapshot.id
                                                mixedSatinUndo = mixedSatinUndo -
                                                    partSnapshot.id
                                                mixedDrafts = mixedDrafts +
                                                    (partSnapshot.id to design)
                                                mixedAudits = mixedAudits +
                                                    (partSnapshot.id to audit)
                                                mixedViewed = mixedViewed - partSnapshot.id
                                                mixedConfirmed = mixedConfirmed - partSnapshot.id
                                                mixedSelectedPart = partSnapshot.id
                                                mixedViewMode = 0
                                                message = audit.brief() +
                                                    " Confira STITCH no bastidor antes de confirmar."
                                            },
                                            onFailure = {
                                                message = "Técnica recusada na parte " +
                                                    partSnapshot.id + ": " +
                                                    (it.message ?:
                                                        "revisão manual necessária.")
                                            }
                                        )
                                    }
                                }
                            }
                        ) {
                            Text(
                                if (mixedBusy) "Validando pontos da parte..."
                                else "Gerar prévia de " + chosen.name + " (QA)",
                                color = FioGold
                            )
                        }
                        val evidence = mixedAudits[part.id]
                        if (draft != null && evidence != null) {
                            Text(
                                "Prévia de " + draft.stitchCount +
                                    " STITCH, " + draft.jumpCount +
                                    " JUMP. " + if (confirmed)
                                    "Parte confirmada para montagem QA."
                                    else "Confirmação pendente.",
                                color = FioTextMuted, fontSize = 11.sp
                            )
                            Text(
                                evidence.brief(),
                                color = FioTextMuted, fontSize = 11.sp
                            )
                            Row(
                                Modifier.fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(onClick = {
                                    mixedSelectedPart = part.id
                                    mixedViewMode = 0
                                }) { Text("Fonte", color = FioGold) }
                                OutlinedButton(onClick = {
                                    mixedSelectedPart = part.id
                                    mixedViewMode = 1
                                    mixedViewed = mixedViewed + part.id
                                }) { Text("Conferir STITCH", color = FioGold) }
                                OutlinedButton(onClick = {
                                    mixedSelectedPart = part.id
                                    mixedViewMode = 2
                                }) { Text("Mapa de cobertura", color = FioGold) }
                            }
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !mixedBusy && !confirmed &&
                                    part.id in mixedViewed &&
                                    mixedDrafts[part.id] === draft &&
                                    mixedAudits[part.id] === evidence,
                                onClick = {
                                    if (mixedDrafts[part.id] === draft &&
                                        mixedAudits[part.id] === evidence &&
                                        part.id in mixedViewed
                                    ) mixedConfirmed = mixedConfirmed + part.id
                                }
                            ) {
                                Text(
                                    "Confirmar prévia técnica da parte (QA)",
                                    color = FioGold
                                )
                            }
                        }
                    }
                }
                val allConfirmed = mixedParts.size in 2..RasterQaComponentBudget.MAX_COMPONENTS &&
                    mixedConfirmed.size == mixedParts.size &&
                    mixedOrder.toSet() == mixedConfirmed &&
                    mixedOrder.all { mixedDrafts[it] != null &&
                        mixedAudits[it] != null && it in mixedViewed }
                Text(
                    "Partes confirmadas: " + mixedConfirmed.size + " de " +
                        mixedParts.size + ". Partes incompletas não serão " +
                        "omitidas da composição.",
                    color = FioTextMuted, fontSize = 11.sp
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = allConfirmed && !mixedBusy && !simulating &&
                        (acceptedFlatSource != null ||
                            regionalSampling?.needsDetailReview != true),
                    colors = ButtonDefaults.buttonColors(containerColor = FioGold),
                    onClick = {
                        val original = mixedSource ?: return@Button
                        val acceptedSnapshot = acceptedFlatSource
                        val capturedParts = mixedParts.toList()
                        val orderSnapshot = mixedOrder.toList()
                        val confirmedSnapshot = mixedConfirmed.toSet()
                        val viewedSnapshot = mixedViewed.toSet()
                        val auditsSnapshot = mixedAudits.toMap()
                        val draftSnapshot = mixedDrafts.toMap()
                        val techniqueSnapshot = mixedTechniques.toMap()
                        val hoopSnapshot = hoop
                        val widthSnapshot = widthMm
                        val stamp = revision
                        simulating = true
                        message = ""
                        scope.launch {
                            val built = withContext(Dispatchers.Default) {
                                runCatching {
                                    require(orderSnapshot.toSet() ==
                                        confirmedSnapshot &&
                                        orderSnapshot.toSet() == viewedSnapshot &&
                                        orderSnapshot.toSet() == auditsSnapshot.keys &&
                                        orderSnapshot.size == capturedParts.size
                                    ) { "Faltam confirmações das partes." }
                                    val partsById = capturedParts.associateBy {
                                        it.id
                                    }
                                    val layers = orderSnapshot.mapIndexed {
                                        pos,id ->
                                        RasterReviewedTechniqueLayer(
                                            component = partsById[id] ?:
                                                error("Parte não encontrada."),
                                            design = draftSnapshot[id] ?:
                                                error("Prévia de pontos pendente."),
                                            technique = techniqueSnapshot[id] ?:
                                                error("Técnica não selecionada."),
                                            sewingOrder = pos
                                        )
                                    }
                                    RasterMixedTechniqueReviewWorkflow.composeReviewed(
                                        original,capturedParts,layers,
                                        widthSnapshot,hoopSnapshot
                                    ).getOrThrow()
                                }
                            }
                            if (revision == stamp &&
                                mixedSource === original &&
                                acceptedFlatSource === acceptedSnapshot &&
                                mixedParts == capturedParts &&
                                mixedOrder == orderSnapshot &&
                                mixedConfirmed == confirmedSnapshot &&
                                mixedViewed == viewedSnapshot &&
                                mixedAudits == auditsSnapshot &&
                                mixedDrafts == draftSnapshot &&
                                mixedTechniques == techniqueSnapshot &&
                                hoop == hoopSnapshot &&
                                widthMm == widthSnapshot) {
                                simulating = false
                                built.fold(
                                    onSuccess = {
                                        mixedAssembly = it
                                        mixedViewMode = 0
                                        message = it.message() +
                                            " Abra a prévia completa antes de Simular Bordado."
                                    },
                                    onFailure = {
                                        message = "Sequência multitécnica recusada: " +
                                            (it.message ?:
                                                "revise cores, cortes e cobertura.")
                                    }
                                )
                            } else simulating = false
                        }
                    }
                ) {
                    Text("Validar montagem completa (QA)",
                        color = FioBackground, fontWeight = FontWeight.Bold)
                }
                mixedAssembly?.let { assembly ->
                    val readiness = remember(assembly) {
                        RasterImageQaReleaseReadinessAudit.inspect(
                            assembly
                        )
                    }
                    Text(
                        assembly.message(),
                        color = FioTextMuted, fontSize = 11.sp
                    )
                    Text(
                        readiness.fold(
                            onSuccess = { it.summary() },
                            onFailure = {
                                "Relatório QA inválido: " +
                                    (it.message ?: "revise a sequência.")
                            }
                        ),
                        color = FioTextMuted,fontSize = 11.sp
                    )
                    assembly.fullCanvasReport?.let { whole ->
                        Text(
                            whole.summary(),
                            color = FioTextMuted, fontSize = 11.sp
                        )
                        whole.parts.forEach { coveredPart ->
                            Text(
                                "Parte " + coveredPart.id + ": " +
                                    coveredPart.coveredPixels + "/" +
                                    coveredPart.inkPixels + " px cobertos; " +
                                    coveredPart.uncoveredPixels +
                                    " px pendentes em " +
                                    coveredPart.regionsWithoutStitches +
                                    " região(ões).",
                                color = FioTextMuted,fontSize = 11.sp
                            )
                        }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !simulating,
                            onClick = {
                                if (mixedAssembly === assembly) {
                                    mixedViewMode = 5
                                    mixedCoverageViewed = true
                                }
                            }
                        ) {
                            Text("Conferir lacunas do desenho inteiro (QA)",
                                color = FioGold)
                        }
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            mixedViewMode = 3
                            mixedAssemblyViewed = true
                        }
                    ) {
                        Text("Conferir todas as pontadas no bastidor",
                            color = FioGold)
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = mixedAssemblyViewed && mixedCoverageViewed &&
                            allConfirmed && !mixedBusy && !simulating &&
                            readiness.isSuccess &&
                            assembly.integrityReport != null &&
                            assembly.fullCanvasReport != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FioGold
                        ),
                        onClick = {
                            if (mixedAssembly === assembly &&
                                mixedAssemblyViewed && mixedCoverageViewed &&
                                readiness.isSuccess &&
                                assembly.integrityReport != null &&
                                assembly.fullCanvasReport != null &&
                                !assembly.exportPermitted && allConfirmed) {
                                qaTechnicalReport = assembly.message()
                                qaReportRevision = revision
                                onSimulate(
                                    assembly.design,hoop,
                                    displayMode,showConnections
                                )
                            }
                        }
                    ) {
                        Text("Simular Bordado — montagem revisada (QA)",
                            color = FioBackground, fontWeight = FontWeight.Bold)
                    }
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
                                enabled = !repairGuidanceBusy && !simulating &&
                                    prepared?.image != null,
                                onClick = {
                                    val original = prepared?.image
                                        ?: return@OutlinedButton
                                    val part = satinParts.getOrNull(satinPartIndex)
                                        ?: return@OutlinedButton
                                    val recordedRevision = revision
                                    val reportSnapshot = missing
                                    val widthSnapshot = widthMm
                                    repairGuidanceBusy = true
                                    repairPlan = null
                                    message = ""
                                    scope.launch {
                                        val result = withContext(Dispatchers.Default) {
                                            RasterGapRepairGuidance.propose(
                                                original, part, reportSnapshot,
                                                widthSnapshot
                                            )
                                        }
                                        repairGuidanceBusy = false
                                        if (revision == recordedRevision &&
                                            prepared?.image === original &&
                                            widthMm == widthSnapshot &&
                                            uncoveredReport === reportSnapshot &&
                                            satinParts.getOrNull(satinPartIndex) === part) {
                                            result.fold(
                                                onSuccess = { repairPlan = it },
                                                onFailure = {
                                                    message = it.message
                                                        ?: "Não foi possível orientar a revisão."
                                                }
                                            )
                                        }
                                    }
                                }
                            ) {
                                Text(
                                    if (repairGuidanceBusy)
                                        "Localizando correções seguras..."
                                    else "Priorizar ajustes de pontos (QA)",
                                    color = FioGold
                                )
                            }
                            repairPlan?.let { guided ->
                                Text(
                                    guided.summary(),
                                    color = FioTextMuted, fontSize = 11.sp
                                )
                                guided.advice.take(8).forEach { advice ->
                                    OutlinedButton(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            showGapOverlay = true
                                            selectedGapId = advice.regionId
                                        }
                                    ) {
                                        Text(
                                            advice.guidance(),
                                            color = FioGold, fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                            repairPlan?.let { guided ->
                                val canTrySatin = guided.advice.any {
                                    it.regionId == selectedGapId &&
                                        it.action ==
                                            RasterGapRepairAction.REVIEW_SATIN_RAILS
                                }
                                if (canTrySatin) {
                                    OutlinedButton(
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !trialBusy && !simulating &&
                                            !repairGuidanceBusy && fitted &&
                                            activePart.id in satinTouched,
                                        onClick = {
                                            val original = prepared?.image
                                                ?: return@OutlinedButton
                                            val part = activePart
                                            val regionId = selectedGapId
                                            val startingSections =
                                                satinSections[part.id].orEmpty().toList()
                                            val reportSnapshot = missing
                                            val widthSnapshot = widthMm
                                            val hoopSnapshot = hoop
                                            val stamp = revision
                                            val holes = elementPlan?.elements
                                                ?.firstOrNull { it.id == part.id }
                                                ?.enclosedHoles
                                            trialBusy = true
                                            repairTrial = null
                                            message = ""
                                            scope.launch {
                                                val candidate =
                                                    withContext(Dispatchers.Default) {
                                                        runCatching {
                                                            val sourceHoles = holes
                                                                ?: error(
                                                                    "Não foi possível validar os " +
                                                                        "vazados da imagem."
                                                                )
                                                            RasterSatinBoundedRepairTrial.suggest(
                                                                original, part,
                                                                startingSections,
                                                                reportSnapshot,
                                                                regionId,
                                                                widthSnapshot,
                                                                hoopSnapshot,
                                                                sourceHoles
                                                            ).getOrThrow()
                                                        }
                                                    }
                                                trialBusy = false
                                                if (revision == stamp &&
                                                    prepared?.image === original &&
                                                    satinParts.getOrNull(satinPartIndex) === part &&
                                                    satinSections[part.id] == startingSections &&
                                                    uncoveredReport === reportSnapshot &&
                                                    widthMm == widthSnapshot &&
                                                    hoop == hoopSnapshot &&
                                                    selectedGapId == regionId) {
                                                    candidate.fold(
                                                        onSuccess = { repairTrial = it },
                                                        onFailure = {
                                                            message = it.message ?:
                                                                "Sem ajuste Satin válido. " +
                                                                    "Revise o trecho manualmente."
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    ) {
                                        Text(
                                            if (trialBusy)
                                                "Auditando proposta de deslocamento..."
                                            else "Ensaiar ajuste Satin de até 0,35 mm",
                                            color = FioGold
                                        )
                                    }
                                    repairTrial?.takeIf {
                                        it.regionId == selectedGapId
                                    }?.let { candidate ->
                                        Text(
                                            candidate.message(),
                                            color = FioTextMuted, fontSize = 11.sp
                                        )
                                        OutlinedButton(
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = !trialBusy && !simulating &&
                                                satinSections[activePart.id].orEmpty()
                                                    .isNotEmpty(),
                                            onClick = {
                                                // Only an explicit tap incorporates
                                                // the tentative rails. It clears the
                                                // human-reviewed flag, forcing another
                                                // manual edit/review before simulation.
                                                satinSections = satinSections +
                                                    (activePart.id to
                                                        candidate.candidateSections)
                                                satinTouched = satinTouched - activePart.id
                                                satinSectionIndex = candidate.sectionIndex
                                                    .coerceIn(0,
                                                        candidate.candidateSections.lastIndex)
                                                showGapOverlay = false
                                                message =
                                                    "Trilhos incorporados como rascunho QA. " +
                                                        "Revise os pontos no editor Satin " +
                                                        "antes de simular. PES bloqueado."
                                            }
                                        ) {
                                            Text(
                                                "Adotar proposta para editar (QA)",
                                                color = FioGold
                                            )
                                        }
                                    }
                                }
                            }
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { showGapOverlay = !showGapOverlay }
                            ) {
                                Text(
                                    if (showGapOverlay)
                                        "Ocultar destaques de lacunas"
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
            if (simulationRoute != RasterImageSimulationRoute.DIRECT_REVIEW) {
                Text(
                    if (simulationRoute ==
                        RasterImageSimulationRoute.PREPARE_FLAT_SOURCE
                    ) {
                        "Esta foto tem textura, contornos translúcidos ou " +
                            "elementos complexos. Ainda não existem pontos " +
                            "validados para simular o logotipo inteiro. " +
                            "1. Analise as cores. 2. Verifique e aceite uma " +
                            "referência plana fiel. 3. Revise separadamente " +
                            "Satin, Tatami e corrido. Não é PES."
                    } else {
                        "A referência plana foi aceita apenas como base QA. " +
                            "Confirme cada componente, cor, trilho e mapa de " +
                            "cobertura em Montar Satin, Tatami e corrido (QA) " +
                            "antes de usar o simulador. Nenhum PES é gerado."
                    },
                    color = FioGold, fontSize = 12.sp
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !preparing && !studyingTexture &&
                        !simulating && fitted && prepared != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FioGold
                    ),
                    onClick = {
                        val picture = prepared?.image ?: return@Button
                        if (simulationRoute ==
                            RasterImageSimulationRoute.PREPARE_FLAT_SOURCE
                        ) {
                            if (flatStudy != null) {
                                showFlatStudy = true
                                message = "Estudo de cores existente. " +
                                    "Acima, toque Verificar referência plana " +
                                    "para pontos (QA), confira os detalhes e " +
                                    "aceite apenas se a silhueta for fiel."
                                scope.launch { imageScrollState.animateScrollTo(0) }
                            } else {
                                val stamp = revision
                                studyingTexture = true
                                message = ""
                                scope.launch {
                                    val study = withContext(Dispatchers.Default) {
                                        RasterThreadTextureStudy.inspect(picture)
                                    }
                                    if (revision == stamp &&
                                        prepared?.image === picture) {
                                        study.fold(
                                            onSuccess = {
                                                flatStudy = it
                                                showFlatStudy = true
                                                message =
                                                    "Estudo de cores pronto. " +
                                                    "Abra a referência plana acima, " +
                                                    "confira letras, curvas e vazados " +
                                                    "e só então valide sua fidelidade."
                                                imageScrollState.animateScrollTo(0)
                                            },
                                            onFailure = {
                                                message = "Não foi possível " +
                                                    "preparar a referência: " +
                                                    (it.message ?: "revise a foto.")
                                            }
                                        )
                                    }
                                    studyingTexture = false
                                }
                            }
                        } else {
                            message = "Referência QA já aceita. Acima, " +
                                "use Montar Satin, Tatami e corrido (QA), " +
                                "revise todas as partes e abra a " +
                                "simulação da montagem validada."
                            scope.launch { imageScrollState.animateScrollTo(0) }
                        }
                    }
                ) {
                    Text(
                        if (simulationRoute ==
                            RasterImageSimulationRoute.PREPARE_FLAT_SOURCE
                        ) {
                            if (studyingTexture)
                                "Analisando cores e contornos..."
                            else "Preparar cores e contornos (QA)"
                        } else "Revisar camadas para simular (QA)",
                        color = FioBackground, fontWeight = FontWeight.Bold
                    )
                }
            } else {
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
