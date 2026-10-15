package com.timachado.brothermatrizes

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.RasterImageDecoder
import com.timachado.brothermatrizes.core.embroidery.RasterImageDigitizer
import com.timachado.brothermatrizes.core.embroidery.RasterImageOptions
import com.timachado.brothermatrizes.core.embroidery.RasterImagePixels
import com.timachado.brothermatrizes.core.embroidery.MultiColorRasterDigitizer
import com.timachado.brothermatrizes.core.embroidery.MultiColorRasterOptions
import com.timachado.brothermatrizes.core.embroidery.ColorLayerEdit
import com.timachado.brothermatrizes.core.embroidery.RasterLayerStitchMode
import com.timachado.brothermatrizes.core.embroidery.ManualSatinRails
import com.timachado.brothermatrizes.core.embroidery.ManualSatinSection
import com.timachado.brothermatrizes.core.embroidery.SatinRailPair
import com.timachado.brothermatrizes.core.project.RasterEditProject
import com.timachado.brothermatrizes.core.project.RasterEditProjectStore
import com.timachado.brothermatrizes.core.project.RasterEditProjectSummary
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.HoopValidator
import com.timachado.brothermatrizes.core.embroidery.SimpleSvgParser
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.core.embroidery.VectorArtwork
import com.timachado.brothermatrizes.core.embroidery.VectorMatrixGenerator
import com.timachado.brothermatrizes.core.embroidery.VectorMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.VectorPath
import com.timachado.brothermatrizes.core.embroidery.VectorPoint
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class DrawingSourceMode(
    val label: String
) {
    SVG("SVG"),
    IMAGE("Imagem"),
    FREEHAND("Desenhar")
}

private val drawingPalette =
    listOf(
        0xE6BE70,
        0xFFFFFF,
        0x111111,
        0xE63946,
        0xF4A261,
        0x2A9D8F,
        0x457B9D,
        0x9B5DE5,
        0xF4A7B9,
        0x6D597A
    )

@Composable
fun CreateDrawingScreen(
    onBack: () -> Unit,
    onCreate: (EmbroideryDesign) -> Unit,
    onSimulate: (EmbroideryDesign) -> Unit
) {
    val context =
        LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceMode by remember {
        mutableStateOf(
            DrawingSourceMode.SVG
        )
    }

    var importedArtwork by remember {
        mutableStateOf<
            VectorArtwork?
        >(null)
    }

    var importMessage by remember {
        mutableStateOf(
            "Nenhum SVG importado."
        )
    }

    var imagePixels by remember { mutableStateOf<RasterImagePixels?>(null) }
    var imageMessage by remember { mutableStateOf("Nenhuma imagem importada.") }
    var threshold by remember { mutableFloatStateOf(140f) }
    var invertRaster by remember { mutableStateOf(false) }
    var multiColorEnabled by remember { mutableStateOf(false) }
    var maxRasterColors by remember { mutableIntStateOf(2) }
    var layerEdits by remember {
        mutableStateOf<List<ColorLayerEdit>>(emptyList())
    }
    var selectedLayerSource by remember {
        mutableIntStateOf(0)
    }
    var savedRasterProjectId by remember { mutableStateOf<String?>(null) }
    var rasterProjectTitle by remember { mutableStateOf("Meu bordado") }
    var rasterProjectMessage by remember { mutableStateOf("") }
    var savedRasterProjects by remember {
        mutableStateOf<List<RasterEditProjectSummary>>(emptyList())
    }
    var rasterProjectListOpen by remember { mutableStateOf(false) }
    var manualEditEnabled by remember(imagePixels, maxRasterColors, selectedLayerSource) {
        mutableStateOf(false)
    }
    var manualSectionIndex by remember(imagePixels, maxRasterColors, selectedLayerSource) {
        mutableIntStateOf(0)
    }
    var manualEditorMessage by remember(imagePixels, maxRasterColors, selectedLayerSource) {
        mutableStateOf("")
    }
    var manualUndoStack by remember(imagePixels, maxRasterColors, selectedLayerSource) {
        mutableStateOf<List<List<ManualSatinSection>>>(emptyList())
    }

    var freehandPaths by remember {
        mutableStateOf<
            List<List<Offset>>
        >(emptyList())
    }

    var activeStroke by remember {
        mutableStateOf<
            List<Offset>
        >(emptyList())
    }

    var widthMm by remember {
        mutableFloatStateOf(60f)
    }

    var stitchStyle by remember {
        mutableStateOf(
            TextStitchStyle.RUNNING
        )
    }

    var color by remember {
        mutableIntStateOf(
            0xE6BE70
        )
    }

    var outputFormat by remember {
        mutableStateOf(
            MatrixConverter
                .preferredBrotherFormat
        )
    }

    var hoopProfile by remember {
        mutableStateOf(
            HoopProfile.H100X100
        )
    }

    val svgPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts
                .OpenDocument()
        ) {
                uri ->
            if (
                uri ==
                    null
            ) {
                return@rememberLauncherForActivityResult
            }

            val name =
                runCatching {
                    context
                        .contentResolver
                        .query(
                            uri,
                            arrayOf(
                                OpenableColumns
                                    .DISPLAY_NAME
                            ),
                            null,
                            null,
                            null
                        )
                        ?.use {
                                cursor ->
                            val column =
                                cursor.getColumnIndex(
                                    OpenableColumns
                                        .DISPLAY_NAME
                                )

                            if (
                                column >=
                                    0 &&
                                cursor
                                    .moveToFirst()
                            ) {
                                cursor.getString(
                                    column
                                )
                            } else {
                                null
                            }
                        }
                }.getOrNull()
                    ?: "logo.svg"

            val bytes =
                runCatching {
                    context
                        .contentResolver
                        .openInputStream(
                            uri
                        )
                        ?.use {
                            it.readBytes()
                        }
                }.getOrNull()

            if (
                bytes ==
                    null
            ) {
                importedArtwork =
                    null

                importMessage =
                    "Não foi possível ler o SVG."
            } else {
                val parsed =
                    SimpleSvgParser
                        .parse(
                            name,
                            bytes
                        )

                importedArtwork =
                    parsed.getOrNull()

                importMessage =
                    parsed.fold(
                        onSuccess = {
                                artwork ->
                            artwork.label +
                                " • " +
                                artwork.paths
                                    .size +
                                " caminho(s)"
                        },
                        onFailure = {
                                error ->
                            error.message
                                ?: "SVG não suportado."
                        }
                    )
            }
        }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            imagePixels = null
            layerEdits = emptyList()
            selectedLayerSource = 0
            savedRasterProjectId = null
            rasterProjectMessage = ""
            rasterProjectListOpen = false
            imageMessage = "Lendo imagem com segurança..."
            scope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    RasterImageDecoder.decode(context.contentResolver, uri)
                }
                imagePixels = loaded.getOrNull()
                loaded.getOrNull()?.let { rasterProjectTitle = it.label.take(120).ifBlank { "Meu bordado" } }
                imageMessage = loaded.fold(
                    onSuccess = { "${it.label} • ${it.width} × ${it.height} px" },
                    onFailure = { it.message ?: "Não foi possível importar a imagem." }
                )
            }
        }
    }

    val freehandArtwork =
        remember(
            freehandPaths,
            activeStroke
        ) {
            val paths =
                (
                    freehandPaths +
                        listOf(
                            activeStroke
                        )
                    )
                    .filter {
                        it.size >=
                            2
                    }
                    .map {
                            stroke ->
                        VectorPath(
                            points =
                                stroke.map {
                                    point ->
                                    VectorPoint(
                                        point.x,
                                        point.y
                                    )
                                }
                        )
                    }

            if (
                paths.isEmpty()
            ) {
                null
            } else {
                VectorArtwork(
                    paths =
                        paths,
                    label =
                        "Desenho livre"
                )
            }
        }

    val artwork =
        when (
            sourceMode
        ) {
            DrawingSourceMode.SVG ->
                importedArtwork

            DrawingSourceMode.FREEHAND ->
                freehandArtwork

            DrawingSourceMode.IMAGE -> null
        }

    // Separação das cores é preservada ao editar só a paleta ou a ordem:
    // evita reprocessar os pixels da imagem a cada ajuste de camada.
    val separatedRaster = remember(imagePixels, maxRasterColors, multiColorEnabled) {
        imagePixels?.takeIf { multiColorEnabled }?.let { pixels ->
            MultiColorRasterDigitizer.separate(
                pixels, MultiColorRasterOptions(maxColors = maxRasterColors)
            )
        }
    }
    val separatedArtwork = separatedRaster?.getOrNull()
    val visibleLayerEdits = if (
        separatedArtwork != null &&
        layerEdits.size == separatedArtwork.colors.size &&
        layerEdits.map { it.sourceIndex }.toSet() == separatedArtwork.colors.indices.toSet()
    ) layerEdits else separatedArtwork?.colors?.indices?.map {
        ColorLayerEdit(sourceIndex = it)
    } ?: emptyList()

    val rasterResult = remember(
        imagePixels, widthMm, threshold, invertRaster, color,
        outputFormat, hoopProfile, multiColorEnabled, maxRasterColors,
        separatedArtwork, separatedRaster, visibleLayerEdits
    ) {
        imagePixels?.let { pixels ->
            if (multiColorEnabled) {
                if (separatedArtwork == null) {
                    Result.failure(
                        separatedRaster?.exceptionOrNull()
                            ?: IllegalArgumentException("Não foi possível separar as cores.")
                    )
                } else {
                    MultiColorRasterDigitizer.generateSeparated(
                        pixels,
                        separatedArtwork,
                        MultiColorRasterOptions(
                            widthMm = widthMm,
                            maxColors = maxRasterColors,
                            outputFormat = outputFormat,
                            hoopProfile = hoopProfile,
                            layers = visibleLayerEdits
                        )
                    )
                }
            } else {
                RasterImageDigitizer.generate(
                    pixels,
                    RasterImageOptions(
                        widthMm = widthMm,
                        threshold = threshold.toInt(),
                        invert = invertRaster,
                        threadColor = color,
                        outputFormat = outputFormat,
                        hoopProfile = hoopProfile
                    )
                )
            }
        }
    }

    val result = if (sourceMode == DrawingSourceMode.IMAGE) {
        rasterResult
    } else artwork?.let {
            VectorMatrixGenerator
                .generate(
                    it,
                    VectorMatrixOptions(
                        widthMm =
                            widthMm,
                        stitchStyle =
                            stitchStyle,
                        color =
                            color,
                        outputFormat =
                            outputFormat,
                        hoopProfile =
                            hoopProfile,
                        enforceHoop =
                            false
                    )
                )
        }

    val preview =
        result?.getOrNull()

    val hoopFit =
        preview?.let {
            HoopValidator
                .validate(
                    it,
                    hoopProfile
                )
        }

    val fitsHoop =
        hoopFit?.fits ==
            true

    Column(
        Modifier
            .fillMaxSize()
            .padding(
                horizontal =
                    14.dp
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical =
                        4.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack
            ) {
                Text(
                    "‹ Voltar",
                    color = FioGold
                )
            }

            Column(
                Modifier.weight(
                    1f
                )
            ) {
                Text(
                    "Criar desenho/logo",
                    color = FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        19.sp
                )

                Text(
                    "Imagem, SVG ou desenho à mão livre",
                    color =
                        FioTextMuted,
                    fontSize =
                        10.sp
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(.82f)
                    .background(
                        Color(
                            0xFF071017
                        ),
                        RoundedCornerShape(
                            24.dp
                        )
                    )
        ) {
            if (
                sourceMode ==
                    DrawingSourceMode
                        .FREEHAND
            ) {
                FreehandPad(
                    paths =
                        freehandPaths,
                    active =
                        activeStroke,
                    onStart = {
                            point ->
                        activeStroke =
                            listOf(
                                point
                            )
                    },
                    onPoint = {
                            point ->
                        activeStroke =
                            activeStroke +
                                point
                    },
                    onEnd = {
                        if (
                            activeStroke.size >=
                                2
                        ) {
                            freehandPaths =
                                freehandPaths +
                                    listOf(
                                        activeStroke
                                    )
                        }

                        activeStroke =
                            emptyList()
                    },
                    modifier =
                        Modifier
                            .fillMaxSize()
                )
            } else if (
                sourceMode == DrawingSourceMode.IMAGE &&
                multiColorEnabled &&
                manualEditEnabled &&
                imagePixels != null &&
                separatedArtwork != null &&
                visibleLayerEdits.any {
                    it.sourceIndex == selectedLayerSource &&
                        it.stitchMode == RasterLayerStitchMode.MANUAL_SATIN &&
                        manualSectionIndex in it.manualSections.indices
                }
            ) {
                val editedLayer = visibleLayerEdits.first {
                    it.sourceIndex == selectedLayerSource
                }
                ManualSatinRailCanvas(
                    art = separatedArtwork,
                    imageWidth = imagePixels!!.width,
                    colorIndex = selectedLayerSource,
                    section = editedLayer.manualSections[manualSectionIndex],
                    onBeginDrag = {
                        manualUndoStack =
                            (manualUndoStack + listOf(editedLayer.manualSections)).takeLast(25)
                    },
                    onEdit = { changed ->
                        val sectionIndex = manualSectionIndex
                        layerEdits = visibleLayerEdits.map { item ->
                            if (item.sourceIndex != selectedLayerSource) item
                            else item.copy(manualSections =
                                item.manualSections.mapIndexed { at, section ->
                                    if (at == sectionIndex) changed else section
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (
                preview != null
            ) {
                EmbroideryCanvas(
                    design =
                        preview,
                    hoop =
                        hoopProfile,
                    modifier =
                        Modifier
                            .fillMaxSize()
                )
            } else {
                Text(
                    if (sourceMode == DrawingSourceMode.IMAGE)
                        "Importe uma imagem para visualizar os pontos."
                    else "Importe um SVG para visualizar.",
                    modifier =
                        Modifier.align(
                            Alignment.Center
                        ),
                    color =
                        FioTextMuted
                )
            }
        }

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1.38f)
                    .padding(
                        vertical =
                            12.dp
                    ),
            colors =
                CardDefaults
                    .cardColors(
                        containerColor =
                            FioSurface
                    ),
            shape =
                RoundedCornerShape(
                    22.dp
                )
        ) {
            Column(
                Modifier
                    .padding(
                        16.dp
                    )
                    .verticalScroll(
                        rememberScrollState()
                    )
            ) {
                Text(
                    "Origem",
                    color = FioText,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical =
                                8.dp
                        ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    DrawingSourceMode
                        .entries
                        .forEach {
                                option ->
                            val selected =
                                sourceMode ==
                                    option

                            OutlinedButton(
                                onClick = {
                                    sourceMode =
                                        option
                                },
                                modifier =
                                    Modifier.weight(
                                        1f
                                    )
                            ) {
                                Text(
                                    if (
                                        selected
                                    ) {
                                        "● " +
                                            option.label
                                    } else {
                                        option.label
                                    },
                                    color =
                                        if (
                                            selected
                                        ) {
                                            FioGold
                                        } else {
                                            FioText
                                        }
                                )
                            }
                        }
                }

                if (
                    sourceMode ==
                        DrawingSourceMode
                            .SVG
                ) {
                    Button(
                        onClick = {
                            svgPicker.launch(
                                arrayOf(
                                    "image/svg+xml",
                                    "application/xml",
                                    "text/xml"
                                )
                            )
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                        colors =
                            ButtonDefaults
                                .buttonColors(
                                    containerColor =
                                        FioGold,
                                    contentColor =
                                        FioBackground
                                )
                    ) {
                        Text(
                            "Importar arquivo SVG",
                            fontWeight =
                                FontWeight.Bold
                        )
                    }

                    Text(
                        importMessage,
                        color =
                            if (
                                importedArtwork !=
                                    null
                            ) {
                                FioTextMuted
                            } else {
                                Color(
                                    0xFFFFB36B
                                )
                            },
                        fontSize =
                            10.sp,
                        modifier =
                            Modifier.padding(
                                top =
                                    6.dp
                            )
                    )

                    Text(
                        "Suporte inicial: path com linhas/curvas, line, polyline, polygon, rect, circle e ellipse. Arcos SVG e transforms complexos ficam para depois.",
                        color =
                            FioTextMuted,
                        fontSize =
                            10.sp,
                        modifier =
                            Modifier.padding(
                                top =
                                    4.dp
                            )
                    )
                } else if (sourceMode == DrawingSourceMode.IMAGE) {
                    Button(
                        onClick = { imagePicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FioGold,
                            contentColor = FioBackground
                        )
                    ) {
                        Text("Importar PNG, JPG ou WebP", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        imageMessage,
                        color = if (imagePixels != null) FioTextMuted else Color(0xFFFFB36B),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Projetos editáveis Satin (armazenamento local)",
                        color = FioText, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Arquivos editáveis ficam apenas neste aparelho. O backup " +
                            "tradicional da Biblioteca ainda não inclui estes projetos.",
                        color = FioTextMuted,
                        fontSize = 10.sp
                    )
                    if (imagePixels != null && multiColorEnabled) {
                        OutlinedTextField(
                            value = rasterProjectTitle,
                            onValueChange = { rasterProjectTitle = it.take(120) },
                            label = { Text("Nome do projeto") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = imagePixels != null && multiColorEnabled &&
                                separatedArtwork != null && rasterProjectTitle.isNotBlank(),
                            onClick = {
                                val pixels = imagePixels ?: return@OutlinedButton
                                val project = RasterEditProject(
                                    title = rasterProjectTitle.trim(),
                                    image = pixels,
                                    widthMm = widthMm,
                                    maxColors = maxRasterColors,
                                    outputFormat = outputFormat,
                                    hoopProfile = hoopProfile,
                                    layers = visibleLayerEdits
                                )
                                val originalId = savedRasterProjectId
                                scope.launch {
                                    val saved = withContext(Dispatchers.IO) {
                                        RasterEditProjectStore.save(context, project, originalId)
                                    }
                                    saved.fold(
                                        onSuccess = {
                                            savedRasterProjectId = it
                                            rasterProjectMessage = "Projeto editável salvo no aparelho."
                                        },
                                        onFailure = {
                                            rasterProjectMessage =
                                                it.message ?: "Não foi possível salvar o projeto."
                                        }
                                    )
                                }
                            }
                        ) {
                            Text(
                                if (savedRasterProjectId != null) "Salvar alterações"
                                else "Salvar projeto", color = FioGold, fontSize = 11.sp
                            )
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch {
                                    val list = withContext(Dispatchers.IO) {
                                        RasterEditProjectStore.list(context)
                                    }
                                    list.fold(
                                        onSuccess = {
                                            savedRasterProjects = it
                                            rasterProjectListOpen = !rasterProjectListOpen
                                            rasterProjectMessage =
                                                if (it.isEmpty()) "Nenhum projeto Satin salvo."
                                                else ""
                                        },
                                        onFailure = {
                                            rasterProjectMessage =
                                                it.message ?: "Não foi possível listar projetos."
                                        }
                                    )
                                }
                            }
                        ) { Text("Reabrir projetos", color = FioGold, fontSize = 11.sp) }
                    }
                    if (savedRasterProjectId != null) {
                        TextButton(
                            onClick = {
                                savedRasterProjectId = null
                                rasterProjectMessage =
                                    "Próximo salvamento criará outro projeto editável."
                            }
                        ) { Text("Salvar uma cópia como novo projeto", color = FioGold) }
                    }
                    if (rasterProjectListOpen) {
                        Text(
                            "Projetos deste aparelho (30 mais recentes)",
                            color = FioTextMuted, fontSize = 10.sp
                        )
                        savedRasterProjects.take(30).forEach { saved ->
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                onClick = {
                                    scope.launch {
                                        val opened = withContext(Dispatchers.IO) {
                                            RasterEditProjectStore.load(context, saved.id)
                                        }
                                        opened.fold(
                                            onSuccess = { project ->
                                                imagePixels = project.image
                                                imageMessage = "${project.image.label} • projeto reaberto"
                                                maxRasterColors = project.maxColors
                                                multiColorEnabled = true
                                                layerEdits = project.layers
                                                selectedLayerSource =
                                                    project.layers.firstOrNull()?.sourceIndex ?: 0
                                                widthMm = project.widthMm
                                                outputFormat = project.outputFormat
                                                hoopProfile = project.hoopProfile ?: HoopProfile.H100X100
                                                rasterProjectTitle = project.title
                                                savedRasterProjectId = saved.id
                                                rasterProjectListOpen = false
                                                rasterProjectMessage =
                                                    "Trilhos, cores e seções restaurados."
                                            },
                                            onFailure = {
                                                rasterProjectMessage =
                                                    it.message ?: "Falha ao abrir o projeto."
                                            }
                                        )
                                    }
                                }
                            ) {
                                Text(saved.title, color = FioGold, fontSize = 11.sp)
                            }
                        }
                    }
                    if (rasterProjectMessage.isNotBlank()) {
                        Text(
                            rasterProjectMessage,
                            color = FioTextMuted, fontSize = 11.sp
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { multiColorEnabled = !multiColorEnabled },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (multiColorEnabled) "● Multicor (experimental)"
                            else "○ Monocromático — ativar multicor",
                            color = FioGold
                        )
                    }
                    if (multiColorEnabled) {
                        OutlinedButton(
                            onClick = { maxRasterColors = if (maxRasterColors == 4) 2 else maxRasterColors + 1 },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Quantidade máxima: $maxRasterColors cores",
                                color = FioGold
                            )
                        }
                        Text(
                            "Usa a cor predominante das bordas para detectar o fundo. " +
                                "Recorte somente o logo; cada cor recebe sua própria " +
                                "camada de pontos e troca de linha. Para nome cursivo " +
                                "fino, prefira criar como texto com fonte adequada.",
                            color = FioTextMuted,
                            fontSize = 11.sp
                        )
                        if (separatedArtwork != null) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Camadas e sequência de bordado",
                                color = FioText,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "De cima para baixo: ordem de costura. " +
                                    "Escolha uma cor para editar; desative camadas indesejadas.",
                                color = FioTextMuted, fontSize = 10.sp
                            )
                            visibleLayerEdits.forEachIndexed { index, layer ->
                                val colorSample = layer.threadColor
                                    ?: separatedArtwork.colors[layer.sourceIndex]
                                val selected = selectedLayerSource == layer.sourceIndex
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        Modifier.size(24.dp)
                                            .background(
                                                Color(0xFF000000.toInt() or colorSample),
                                                CircleShape
                                            )
                                            .border(
                                                if (selected) 3.dp else 1.dp,
                                                if (selected) FioGold else FioTextMuted,
                                                CircleShape
                                            )
                                            .clickable { selectedLayerSource = layer.sourceIndex }
                                    )
                                    TextButton(
                                        onClick = { selectedLayerSource = layer.sourceIndex },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            "Cor ${layer.sourceIndex + 1}" +
                                                if (!layer.enabled) " (oculta)" else "",
                                            color = if (selected) FioGold else FioText,
                                            fontSize = 11.sp
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            layerEdits = visibleLayerEdits.map {
                                                if (it.sourceIndex == layer.sourceIndex)
                                                    it.copy(enabled = !it.enabled) else it
                                            }
                                        }
                                    ) {
                                        Text(if (layer.enabled) "Ocultar" else "Ativar",
                                            color = FioGold, fontSize = 10.sp)
                                    }
                                    TextButton(
                                        enabled = index > 0,
                                        onClick = {
                                            val current = visibleLayerEdits.toMutableList()
                                            val before = current[index - 1]
                                            current[index - 1] = current[index]
                                            current[index] = before
                                            layerEdits = current
                                        }
                                    ) { Text("↑", color = FioGold) }
                                    TextButton(
                                        enabled = index < visibleLayerEdits.lastIndex,
                                        onClick = {
                                            val current = visibleLayerEdits.toMutableList()
                                            val after = current[index + 1]
                                            current[index + 1] = current[index]
                                            current[index] = after
                                            layerEdits = current
                                        }
                                    ) { Text("↓", color = FioGold) }
                                }
                            }
                            val selectedLayer = visibleLayerEdits.firstOrNull {
                                it.sourceIndex == selectedLayerSource
                            } ?: visibleLayerEdits.firstOrNull()
                            selectedLayer?.let { layer ->
                                val identifiedColor = separatedArtwork.colors[layer.sourceIndex]
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Linha da cor ${layer.sourceIndex + 1}" +
                                        if (layer.threadColor == null) " • detectada" else " • personalizada",
                                    color = FioText, fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    (listOf(identifiedColor) + drawingPalette).distinct()
                                        .forEach { swatch ->
                                            val chosen = swatch ==
                                                (layer.threadColor ?: identifiedColor)
                                            Box(
                                                Modifier.size(32.dp)
                                                    .background(
                                                        Color(0xFF000000.toInt() or swatch),
                                                        CircleShape
                                                    )
                                                    .border(
                                                        if (chosen) 3.dp else 1.dp,
                                                        if (chosen) FioGold else FioTextMuted,
                                                        CircleShape
                                                    )
                                                    .clickable {
                                                        layerEdits = visibleLayerEdits.map {
                                                            if (it.sourceIndex == layer.sourceIndex)
                                                                it.copy(threadColor = swatch) else it
                                                        }
                                                    }
                                            )
                                        }
                                }
                                TextButton(
                                    onClick = {
                                        layerEdits = visibleLayerEdits.map {
                                            if (it.sourceIndex == layer.sourceIndex)
                                                it.copy(threadColor = null) else it
                                        }
                                    }
                                ) {
                                    Text("Restaurar cor detectada", color = FioGold)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Tipo de ponto da camada",
                                    color = FioText, fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            layerEdits = visibleLayerEdits.map {
                                                if (it.sourceIndex == layer.sourceIndex)
                                                    it.copy(stitchMode = RasterLayerStitchMode.FILL)
                                                else it
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            if (layer.stitchMode == RasterLayerStitchMode.FILL)
                                                "● Preenchimento" else "Preenchimento",
                                            color = FioGold, fontSize = 10.sp
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            layerEdits = visibleLayerEdits.map {
                                                if (it.sourceIndex == layer.sourceIndex)
                                                    it.copy(stitchMode =
                                                        RasterLayerStitchMode.SATIN_STRAIGHT)
                                                else it
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            if (layer.stitchMode ==
                                                RasterLayerStitchMode.SATIN_STRAIGHT)
                                                "● Satin reto" else "Satin reto",
                                            color = FioGold, fontSize = 10.sp
                                        )
                                    }
                                }
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        layerEdits = visibleLayerEdits.map {
                                            if (it.sourceIndex == layer.sourceIndex)
                                                it.copy(stitchMode =
                                                    RasterLayerStitchMode.SATIN_CURVED)
                                            else it
                                        }
                                    }
                                ) {
                                    Text(
                                        if (layer.stitchMode == RasterLayerStitchMode.SATIN_CURVED)
                                            "● Satin curvo experimental"
                                        else "Satin curvo experimental",
                                        color = FioGold, fontSize = 11.sp
                                    )
                                }
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        if (layer.manualSections.isEmpty()) {
                                            val suggested = ManualSatinRails.suggest(
                                                separatedArtwork, imagePixels!!.width,
                                                imagePixels!!.height, layer.sourceIndex,
                                                widthMm
                                            )
                                            val first = suggested.getOrNull()
                                            if (first != null) {
                                                layerEdits = visibleLayerEdits.map {
                                                    if (it.sourceIndex == layer.sourceIndex)
                                                        it.copy(
                                                            stitchMode = RasterLayerStitchMode.MANUAL_SATIN,
                                                            manualSections = listOf(first)
                                                        )
                                                    else it
                                                }
                                                manualSectionIndex = 0
                                                manualUndoStack = emptyList()
                                                manualEditEnabled = true
                                                manualEditorMessage = ""
                                            } else {
                                                manualEditorMessage = suggested.exceptionOrNull()
                                                    ?.message ?: "Não foi possível iniciar os trilhos."
                                            }
                                        } else {
                                            layerEdits = visibleLayerEdits.map {
                                                if (it.sourceIndex == layer.sourceIndex)
                                                    it.copy(stitchMode = RasterLayerStitchMode.MANUAL_SATIN)
                                                else it
                                            }
                                            manualEditEnabled = !manualEditEnabled
                                        }
                                    }
                                ) {
                                    Text(
                                        if (layer.stitchMode == RasterLayerStitchMode.MANUAL_SATIN &&
                                            manualEditEnabled) "● Fechar editor de trilhos"
                                        else "Editar trilhos Satin manualmente",
                                        color = FioGold, fontSize = 11.sp
                                    )
                                }
                                if (layer.stitchMode == RasterLayerStitchMode.MANUAL_SATIN) {
                                    Text(
                                        "Mova as bolinhas douradas (trilho A) e azuis (B) " +
                                            "diretamente na área superior. Cada seção é " +
                                            "um traço do bordado; não conecta letras sozinha.",
                                        color = FioTextMuted, fontSize = 10.sp
                                    )
                                    if (manualEditEnabled) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            OutlinedButton(
                                                modifier = Modifier.weight(1f),
                                                enabled = manualUndoStack.isNotEmpty(),
                                                onClick = {
                                                    val previous = manualUndoStack.last()
                                                    manualUndoStack = manualUndoStack.dropLast(1)
                                                    layerEdits = visibleLayerEdits.map {
                                                        if (it.sourceIndex == layer.sourceIndex)
                                                            it.copy(manualSections = previous)
                                                        else it
                                                    }
                                                }
                                            ) { Text("↶ Desfazer", color = FioGold, fontSize = 10.sp) }
                                            OutlinedButton(
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    if (layer.manualSections.size < 80) {
                                                        manualUndoStack = (
                                                            manualUndoStack +
                                                                listOf(layer.manualSections)
                                                        ).takeLast(25)
                                                        val last = layer.manualSections.last()
                                                        layerEdits = visibleLayerEdits.map {
                                                            if (it.sourceIndex == layer.sourceIndex)
                                                                it.copy(manualSections =
                                                                    it.manualSections + last)
                                                            else it
                                                        }
                                                        manualSectionIndex = layer.manualSections.size
                                                        manualEditorMessage =
                                                            "Nova seção sobreposta: mova seus pontos antes de gerar."
                                                    }
                                                }
                                            ) { Text("+ Seção", color = FioGold, fontSize = 10.sp) }
                                        }
                                        Text(
                                            "Seção ${manualSectionIndex + 1} de ${layer.manualSections.size}",
                                            color = FioText, fontSize = 11.sp
                                        )
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            OutlinedButton(
                                                modifier = Modifier.weight(1f),
                                                enabled = manualSectionIndex > 0,
                                                onClick = { manualSectionIndex-- }
                                            ) { Text("‹ Seção", color = FioGold, fontSize = 10.sp) }
                                            OutlinedButton(
                                                modifier = Modifier.weight(1f),
                                                enabled = manualSectionIndex < layer.manualSections.lastIndex,
                                                onClick = { manualSectionIndex++ }
                                            ) { Text("Seção ›", color = FioGold, fontSize = 10.sp) }
                                        }
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            OutlinedButton(
                                                modifier = Modifier.weight(1f),
                                                enabled = manualSectionIndex > 0,
                                                onClick = {
                                                    val index = manualSectionIndex
                                                    manualUndoStack = (
                                                        manualUndoStack + listOf(layer.manualSections)
                                                    ).takeLast(25)
                                                    val order = layer.manualSections.toMutableList()
                                                    val other = order[index - 1]
                                                    order[index - 1] = order[index]
                                                    order[index] = other
                                                    layerEdits = visibleLayerEdits.map { item ->
                                                        if (item.sourceIndex == layer.sourceIndex)
                                                            item.copy(manualSections = order)
                                                        else item
                                                    }
                                                    manualSectionIndex--
                                                }
                                            ) { Text("↑ Ordem", color = FioGold, fontSize = 10.sp) }
                                            OutlinedButton(
                                                modifier = Modifier.weight(1f),
                                                enabled = manualSectionIndex < layer.manualSections.lastIndex,
                                                onClick = {
                                                    val index = manualSectionIndex
                                                    manualUndoStack = (
                                                        manualUndoStack + listOf(layer.manualSections)
                                                    ).takeLast(25)
                                                    val order = layer.manualSections.toMutableList()
                                                    val other = order[index + 1]
                                                    order[index + 1] = order[index]
                                                    order[index] = other
                                                    layerEdits = visibleLayerEdits.map { item ->
                                                        if (item.sourceIndex == layer.sourceIndex)
                                                            item.copy(manualSections = order)
                                                        else item
                                                    }
                                                    manualSectionIndex++
                                                }
                                            ) { Text("↓ Ordem", color = FioGold, fontSize = 10.sp) }
                                            OutlinedButton(
                                                modifier = Modifier.weight(1f),
                                                enabled = layer.manualSections.size > 1,
                                                onClick = {
                                                    manualUndoStack = (
                                                        manualUndoStack + listOf(layer.manualSections)
                                                    ).takeLast(25)
                                                    val remaining =
                                                        layer.manualSections.filterIndexed { index, _ ->
                                                            index != manualSectionIndex
                                                        }
                                                    layerEdits = visibleLayerEdits.map { item ->
                                                        if (item.sourceIndex == layer.sourceIndex)
                                                            item.copy(manualSections = remaining)
                                                        else item
                                                    }
                                                    manualSectionIndex =
                                                        manualSectionIndex.coerceAtMost(remaining.lastIndex)
                                                    manualEditorMessage = "Seção retirada desta matriz."
                                                }
                                            ) { Text("Excluir", color = FioGold, fontSize = 10.sp) }
                                        }
                                        Text(
                                            "Mover seção inteira (1% da arte)",
                                            color = FioTextMuted, fontSize = 10.sp
                                        )
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            listOf(
                                                Triple("←", -0.01f, 0f),
                                                Triple("→", 0.01f, 0f),
                                                Triple("↑", 0f, -0.01f),
                                                Triple("↓", 0f, 0.01f)
                                            ).forEach { (name, dx, dy) ->
                                                OutlinedButton(
                                                    modifier = Modifier.weight(1f),
                                                    onClick = {
                                                        val current = layer.manualSections
                                                            .getOrNull(manualSectionIndex)
                                                        if (current != null) {
                                                            val shifted = current.rails.map { rail ->
                                                                rail.copy(
                                                                    ax = rail.ax + dx, ay = rail.ay + dy,
                                                                    bx = rail.bx + dx, by = rail.by + dy
                                                                )
                                                            }
                                                            if (shifted.all { rail ->
                                                                listOf(
                                                                    rail.ax, rail.ay, rail.bx, rail.by
                                                                ).all { it in 0f..1f }
                                                            }) {
                                                                manualUndoStack = (
                                                                    manualUndoStack +
                                                                        listOf(layer.manualSections)
                                                                ).takeLast(25)
                                                                layerEdits = visibleLayerEdits.map { item ->
                                                                    if (item.sourceIndex != layer.sourceIndex) item
                                                                    else item.copy(
                                                                        manualSections =
                                                                            item.manualSections.mapIndexed { i, section ->
                                                                                if (i == manualSectionIndex)
                                                                                    section.copy(rails = shifted)
                                                                                else section
                                                                            }
                                                                    )
                                                                }
                                                                manualEditorMessage = ""
                                                            } else {
                                                                manualEditorMessage =
                                                                    "O movimento sairia da arte. Ajuste os trilhos."
                                                            }
                                                        }
                                                    }
                                                ) { Text(name, color = FioGold, fontSize = 11.sp) }
                                            }
                                        }
                                        val railList = layer.manualSections
                                            .getOrNull(manualSectionIndex)?.rails ?: emptyList()
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            OutlinedButton(
                                                modifier = Modifier.weight(1f),
                                                enabled = railList.size in 2..99,
                                                onClick = {
                                                    val i = (railList.size - 1) / 2
                                                    val a = railList[i]
                                                    val b = railList[i + 1]
                                                    val mid = SatinRailPair(
                                                        (a.ax + b.ax) / 2f, (a.ay + b.ay) / 2f,
                                                        (a.bx + b.bx) / 2f, (a.by + b.by) / 2f
                                                    )
                                                    manualUndoStack = (
                                                        manualUndoStack +
                                                            listOf(layer.manualSections)
                                                    ).takeLast(25)
                                                    layerEdits = visibleLayerEdits.map { item ->
                                                        if (item.sourceIndex != layer.sourceIndex) item
                                                        else item.copy(
                                                            manualSections =
                                                                item.manualSections.mapIndexed { at, section ->
                                                                    if (at == manualSectionIndex)
                                                                        section.copy(rails =
                                                                            section.rails.toMutableList().apply {
                                                                                add(i + 1, mid)
                                                                            }
                                                                        )
                                                                    else section
                                                                }
                                                        )
                                                    }
                                                }
                                            ) { Text("+ Par de trilhos", color = FioGold, fontSize = 10.sp) }
                                            OutlinedButton(
                                                modifier = Modifier.weight(1f),
                                                enabled = railList.size > 2,
                                                onClick = {
                                                    manualUndoStack = (
                                                        manualUndoStack +
                                                            listOf(layer.manualSections)
                                                    ).takeLast(25)
                                                    layerEdits = visibleLayerEdits.map { item ->
                                                        if (item.sourceIndex != layer.sourceIndex) item
                                                        else item.copy(
                                                            manualSections =
                                                                item.manualSections.mapIndexed { at, section ->
                                                                    if (at == manualSectionIndex)
                                                                        section.copy(rails = section.rails.dropLast(1))
                                                                    else section
                                                                }
                                                        )
                                                    }
                                                }
                                            ) { Text("− Último par", color = FioGold, fontSize = 10.sp) }
                                        }
                                    }
                                    if (manualEditorMessage.isNotBlank()) {
                                        Text(
                                            manualEditorMessage,
                                            color = FioTextMuted, fontSize = 10.sp
                                        )
                                    }
                                }
                                if (layer.stitchMode != RasterLayerStitchMode.FILL) {
                                    Text(
                                        if (layer.stitchMode == RasterLayerStitchMode.SATIN_STRAIGHT)
                                            "Satin reto: faixas de 0,8 a 5,5 mm. " +
                                                "Curvas, furos e ramos não são aceitos."
                                        else if (layer.stitchMode == RasterLayerStitchMode.SATIN_CURVED)
                                            "Satin curvo: faixa suave e contínua de 0,8 a 5,4 mm. " +
                                                "Laços e ramificações exigem edição manual."
                                        else "Satin manual: confira cada rail, evite cruzamentos " +
                                            "e subdivida cada traço; requer validação de bordado.",
                                        color = FioTextMuted, fontSize = 10.sp
                                    )
                                    val pull = layer.satinPullCompensationMm
                                    Text(
                                        "Compensação lateral: " +
                                            String.format(Locale("pt", "BR"), "%.2f mm", pull),
                                        color = FioText, fontSize = 11.sp
                                    )
                                    Slider(
                                        value = pull,
                                        onValueChange = { next ->
                                            layerEdits = visibleLayerEdits.map {
                                                if (it.sourceIndex == layer.sourceIndex)
                                                    it.copy(satinPullCompensationMm = next)
                                                else it
                                            }
                                        },
                                        valueRange = 0f..0.3f
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            layerEdits = visibleLayerEdits.map {
                                                if (it.sourceIndex == layer.sourceIndex)
                                                    it.copy(satinCenterUnderlay =
                                                        !it.satinCenterUnderlay)
                                                else it
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            if (layer.satinCenterUnderlay)
                                                "● Sustentação central ativa"
                                            else "○ Ativar sustentação central",
                                            color = FioGold, fontSize = 11.sp
                                        )
                                    }
                                }
                                val spacing = layer.rowSpacingMm ?: 0.5f
                                Text(
                                    (if (layer.stitchMode != RasterLayerStitchMode.FILL)
                                        "Passo Satin: " else "Espaçamento entre fileiras: ") +
                                        String.format(Locale("pt", "BR"), "%.2f mm", spacing),
                                    color = FioText, fontSize = 11.sp
                                )
                                Slider(
                                    value = spacing,
                                    onValueChange = { next ->
                                        layerEdits = visibleLayerEdits.map {
                                            if (it.sourceIndex == layer.sourceIndex)
                                                it.copy(rowSpacingMm = next) else it
                                        }
                                    },
                                    valueRange = 0.4f..1f
                                )
                                Text(
                                    "Etapa experimental: valores menores aumentam a densidade. " +
                                        "Satin curvo não faz ligação automática entre letras. " +
                                        "Revise a geometria e teste em tecido antes de produzir.",
                                    color = FioTextMuted, fontSize = 10.sp
                                )
                            }
                        } else if (imagePixels != null) {
                            Text(
                                separatedRaster?.exceptionOrNull()?.message
                                    ?: "Não foi possível identificar cores.",
                                color = Color(0xFFFF9F9A),
                                fontSize = 11.sp
                            )
                        }

                    }
                    if (!multiColorEnabled) {
                    Text("Contraste do desenho: ${threshold.toInt()}",
                        color = FioText, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = threshold,
                        onValueChange = { threshold = it },
                        valueRange = 20f..235f
                    )
                    OutlinedButton(
                        onClick = { invertRaster = !invertRaster },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (invertRaster) "Selecionando tons claros"
                            else "Selecionando tons escuros",
                            color = FioGold
                        )
                    }
                    }
                    Text(
                        if (multiColorEnabled)
                            "Multicor experimental com faixas de preenchimento. " +
                                "Ainda exige acabamento Satin, underlay e testes em tecido."
                        else
                            "Digitalização experimental de uma cor com preenchimento corrido. " +
                            "Recorte apenas o logo, preferencialmente com fundo uniforme " +
                            "ou transparente. Não converte fotos complexas em bordado " +
                            "profissional. Confira a prévia, o bastidor e teste em tecido.",
                        color = FioTextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (
                                    freehandPaths
                                        .isNotEmpty()
                                ) {
                                    freehandPaths =
                                        freehandPaths
                                            .dropLast(
                                                1
                                            )
                                }
                            },
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                    ) {
                            Text(
                                "↶ Desfazer"
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                freehandPaths =
                                    emptyList()

                                activeStroke =
                                    emptyList()
                            },
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        ) {
                            Text(
                                "Limpar"
                            )
                        }
                    }

                    Text(
                        "Desenhe diretamente na área acima. Cada traço vira um caminho de bordado.",
                        color =
                            FioTextMuted,
                        fontSize =
                            10.sp,
                        modifier =
                            Modifier.padding(
                                top =
                                    6.dp
                            )
                    )
                }

                Spacer(
                    Modifier.height(
                        14.dp
                    )
                )

                Text(
                    "Largura " +
                        oneDecimal(
                            widthMm
                        ) +
                        " mm",
                    color = FioText,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Slider(
                    value =
                        widthMm,
                    onValueChange = {
                        widthMm = it
                    },
                    valueRange =
                        10f..200f
                )

                if (sourceMode != DrawingSourceMode.IMAGE) {
                Text(
                    "Tipo de ponto",
                    color = FioText,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical =
                                8.dp
                        ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    TextStitchStyle
                        .entries
                        .forEach {
                                option ->
                            val selected =
                                stitchStyle ==
                                    option

                            OutlinedButton(
                                onClick = {
                                    stitchStyle =
                                        option
                                },
                                modifier =
                                    Modifier.weight(
                                        1f
                                    )
                            ) {
                                Text(
                                    if (
                                        selected
                                    ) {
                                        "● " +
                                            option
                                                .displayName
                                    } else {
                                        option
                                            .displayName
                                    },
                                    color =
                                        if (
                                            selected
                                        ) {
                                            FioGold
                                        } else {
                                            FioText
                                        }
                                )
                            }
                        }
                }

                Text(
                    if (
                        stitchStyle ==
                            TextStitchStyle
                                .SATIN
                    ) {
                        "Satin acompanha o traço do desenho; não transforma áreas fechadas em preenchimento automático."
                    } else {
                        "Ponto corrido segue o contorno/caminho importado."
                    },
                    color =
                        FioTextMuted,
                    fontSize =
                        10.sp
                )

                }

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

                if (sourceMode != DrawingSourceMode.IMAGE || !multiColorEnabled) {
                Text(
                    "Cor da linha",
                    color = FioText,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        )
                        .padding(
                            vertical =
                                8.dp
                        ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            9.dp
                        )
                ) {
                    drawingPalette
                        .forEach {
                                rawColor ->
                            val selected =
                                rawColor ==
                                    color

                            Box(
                                Modifier
                                    .size(
                                        38.dp
                                    )
                                    .background(
                                        Color(
                                            0xFF000000 or
                                                rawColor
                                                    .toLong()
                                        ),
                                        CircleShape
                                    )
                                    .border(
                                        if (
                                            selected
                                        ) {
                                            3.dp
                                        } else {
                                            1.dp
                                        },
                                        if (
                                            selected
                                        ) {
                                            FioGold
                                        } else {
                                            Color(
                                                0xFF52616B
                                            )
                                        },
                                        CircleShape
                                    )
                                    .clickable {
                                        color =
                                            rawColor
                                    }
                            )
                        }
                }

                }

                Text(
                    "Bastidor",
                    color = FioText,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        )
                        .padding(
                            vertical =
                                8.dp
                        ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    HoopProfile
                        .entries
                        .forEach {
                                option ->
                            val selected =
                                hoopProfile ==
                                    option

                            OutlinedButton(
                                onClick = {
                                    hoopProfile =
                                        option
                                }
                            ) {
                                Text(
                                    if (
                                        selected
                                    ) {
                                        "● " +
                                            option
                                                .displayName
                                    } else {
                                        option
                                            .displayName
                                    },
                                    color =
                                        if (
                                            selected
                                        ) {
                                            FioGold
                                        } else {
                                            FioText
                                        }
                                )
                            }
                        }
                }

                Text(
                    "Formato",
                    color = FioText,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical =
                                8.dp
                        ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    MatrixConverter
                        .supportedFormats
                        .forEach {
                            format ->
                        val selected =
                            outputFormat ==
                                format

                        OutlinedButton(
                            onClick = {
                                outputFormat =
                                    format
                            },
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        ) {
                            Text(
                                if (
                                    selected
                                ) {
                                    "● $format"
                                } else {
                                    format
                                },
                                color =
                                    if (
                                        selected
                                    ) {
                                        FioGold
                                    } else {
                                        FioText
                                    }
                            )
                        }
                    }
                }

                if (
                    preview != null
                ) {
                    Text(
                        "Prévia: " +
                            oneDecimal(
                                preview
                                    .bounds
                                    .widthMm
                            ) +
                            " × " +
                            oneDecimal(
                                preview
                                    .bounds
                                    .heightMm
                            ) +
                            " mm • " +
                            preview.stitchCount +
                            " pontos",
                        color =
                            FioTextMuted,
                        fontSize =
                            11.sp
                    )

                    Text(
                        if (
                            fitsHoop
                        ) {
                            "✓ Cabe na área segura do bastidor."
                        } else {
                            "⚠ Ultrapassa a área segura do bastidor."
                        },
                        color =
                            if (
                                fitsHoop
                            ) {
                                Color(
                                    0xFF7ED6A5
                                )
                            } else {
                                Color(
                                    0xFFFF9F9A
                                )
                            },
                        fontSize =
                            11.sp
                    )
                } else {
                    result
                        ?.exceptionOrNull()
                        ?.message
                        ?.let {
                            message ->
                            Text(
                                message,
                                color =
                                    Color(
                                        0xFFFF9F9A
                                    ),
                                fontSize =
                                    11.sp
                            )
                        }
                }

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

                Button(
                    onClick = {
                        preview?.let(
                            onCreate
                        )
                    },
                    enabled =
                        preview != null &&
                            fitsHoop,
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    colors =
                        ButtonDefaults
                            .buttonColors(
                                containerColor =
                                    FioGold,
                                contentColor =
                                    FioBackground
                            )
                ) {
                    Text(
                        "Gerar matriz",
                        fontWeight =
                            FontWeight.Bold
                    )
                }

                Spacer(
                    Modifier.height(
                        8.dp
                    )
                )

                OutlinedButton(
                    onClick = {
                        preview?.let(
                            onSimulate
                        )
                    },
                    enabled =
                        preview != null &&
                            fitsHoop,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                ) {
                    Text(
                        "▶ Simular desenho"
                    )
                }
            }
        }
    }
}

@Composable
private fun FreehandPad(
    paths: List<List<Offset>>,
    active: List<Offset>,
    onStart: (Offset) -> Unit,
    onPoint: (Offset) -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier =
        Modifier
) {
    Canvas(
        modifier =
            modifier
                .pointerInput(
                    Unit
                ) {
                    detectDragGestures(
                        onDragStart = {
                            onStart(it)
                        },
                        onDragEnd = {
                            onEnd()
                        },
                        onDragCancel = {
                            onEnd()
                        }
                    ) {
                            change,
                            _ ->
                        change.consume()

                        onPoint(
                            change.position
                        )
                    }
                }
    ) {
        fun drawPath(
            points: List<Offset>
        ) {
            if (
                points.size <
                    2
            ) {
                return
            }

            for (
                index in
                    1 until
                        points.size
            ) {
                drawLine(
                    color =
                        Color(
                            0xFFE6BE70
                        ),
                    start =
                        points[
                            index -
                                1
                        ],
                    end =
                        points[index],
                    strokeWidth =
                        3.dp.toPx(),
                    cap =
                        StrokeCap.Round
                )
            }
        }

        paths.forEach {
            drawPath(it)
        }

        drawPath(
            active
        )
    }
}

private fun oneDecimal(
    value: Float
): String =
    String.format(
        Locale.forLanguageTag(
            "pt-BR"
        ),
        "%.1f",
        value
    )
