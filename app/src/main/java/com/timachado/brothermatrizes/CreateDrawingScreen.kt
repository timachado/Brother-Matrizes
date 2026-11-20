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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.HoopValidator
import com.timachado.brothermatrizes.core.embroidery.SimpleSvgParser
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.core.embroidery.VectorArtwork
import com.timachado.brothermatrizes.core.embroidery.VectorMatrixGenerator
import com.timachado.brothermatrizes.core.embroidery.VectorFillMode
import com.timachado.brothermatrizes.core.embroidery.VectorMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.VectorPath
import com.timachado.brothermatrizes.core.embroidery.VectorSvgExporter
import com.timachado.brothermatrizes.core.embroidery.VectorPoint
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import java.io.ByteArrayOutputStream
import java.util.Locale

private enum class DrawingSourceMode(
    val label: String
) {
    SVG("Importar SVG"),
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

    var exportMessage by remember { mutableStateOf<String?>(null) }
    var pendingSvgBytes by remember { mutableStateOf<ByteArray?>(null) }

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

    var fillMode by remember { mutableStateOf(VectorFillMode.NONE) }
    var tatamiRowSpacingMm by remember { mutableFloatStateOf(0.5f) }
    var tatamiContourFinish by remember { mutableStateOf(false) }

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

            // Limita os bytes durante a leitura: não carregar arquivos enormes na memória.
            val readResult = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8_192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        require(output.size() + count <= 2_000_000) {
                            "SVG maior que 2 MB. Reduza o arquivo antes de importar."
                        }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: error("O arquivo selecionado não pôde ser aberto.")
            }
            val bytes = readResult.getOrNull()

            if (
                bytes ==
                    null
            ) {
                importedArtwork =
                    null

                importMessage =
                    readResult.exceptionOrNull()?.message ?: "Não foi possível ler o SVG."
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
                                artwork.paths.size +
                                " caminho(s) • conversão de contornos (sem preenchimento automático)"
                        },
                        onFailure = {
                                error ->
                            error.message
                                ?: "SVG não suportado."
                        }
                    )
            }
        }

    val svgSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/svg+xml")
    ) { uri ->
        val bytes = pendingSvgBytes
        pendingSvgBytes = null
        if (uri == null) {
            exportMessage = "Salvamento cancelado."
        } else if (bytes != null) {
            exportMessage = runCatching {
                context.contentResolver.openOutputStream(uri, "w")?.use { stream ->
                    stream.write(bytes)
                } ?: error("Não foi possível abrir o arquivo de destino.")
                "Desenho SVG salvo com sucesso."
            }.getOrElse { error ->
                "Não foi possível salvar o SVG: ${error.message ?: "erro desconhecido"}"
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
        }

    // Recalcular pontos somente quando o vetor ou seus parâmetros mudarem.
    val result = remember(artwork, widthMm, stitchStyle, fillMode,
        tatamiRowSpacingMm, tatamiContourFinish, color, outputFormat, hoopProfile) {
        artwork?.let {
            VectorMatrixGenerator.generate(
                it,
                VectorMatrixOptions(
                    widthMm = widthMm,
                    stitchStyle = stitchStyle,
                    fillMode = fillMode,
                    tatamiRowSpacingMm = tatamiRowSpacingMm,
                    tatamiContourFinish = tatamiContourFinish,
                    color = color,
                    outputFormat = outputFormat,
                    hoopProfile = hoopProfile,
                    enforceHoop = false
                )
            )
        }
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
                    "SVG simples ou desenho à mão livre",
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
                            .testTag("brother_svg_drawing_pad")
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
                val generationError = result?.exceptionOrNull()?.message
                Text(
                    text = if (generationError != null) {
                        "Não foi possível gerar a prévia:\n" +
                            generationError +
                            "\n\nConfira o SVG ou selecione Ponto corrido."
                    } else {
                        "Importe um SVG para visualizar."
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .testTag("brother_svg_preview_feedback"),
                    color = if (generationError != null) {
                        Color(0xFFFF9F9A)
                    } else {
                        FioTextMuted
                    }
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
                        if (fillMode == VectorFillMode.TATAMI) {
                            "Suporte: formas SVG e transformações 2D. Preenchimento Tatami ativo para regiões fechadas. Arcos SVG, underlay automático e ajustes finais de tecido ainda não são suportados."
                        } else {
                            "Suporte: linhas, curvas, formas SVG e transformações 2D. Ponto corrido e Satin seguem os contornos; o preenchimento de áreas exige selecionar Tatami."
                        },
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
                                stitchStyle == option && fillMode == VectorFillMode.NONE

                            OutlinedButton(
                                onClick = {
                                    stitchStyle = option
                                    fillMode = VectorFillMode.NONE
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

                OutlinedButton(
                    onClick = {
                        fillMode = if (fillMode == VectorFillMode.TATAMI) {
                            VectorFillMode.NONE
                        } else {
                            VectorFillMode.TATAMI
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("brother_svg_tatami_mode")
                ) {
                    Text(
                        if (fillMode == VectorFillMode.TATAMI) {
                            "● Preenchimento Tatami (experimental)"
                        } else {
                            "Preenchimento Tatami (experimental)"
                        },
                        color = if (fillMode == VectorFillMode.TATAMI) FioGold else FioText
                    )
                }

                if (fillMode == VectorFillMode.TATAMI) {
                    // Deixar a ação principal antes do texto técnico, dentro
                    // da área visível após selecionar o Tatami.
                    OutlinedButton(
                        onClick = { tatamiContourFinish = !tatamiContourFinish },
                        modifier = Modifier.fillMaxWidth()
                            .testTag("brother_svg_tatami_detail_finish")
                    ) {
                        Text(
                            (if (tatamiContourFinish) "● " else "") +
                                "Acabamento: contornos e detalhes",
                            color = if (tatamiContourFinish) FioGold else FioText
                        )
                    }
                    Text(
                        if (tatamiContourFinish) {
                            "Ativado: acrescenta ponto corrido sobre os contornos, após o Tatami. " +
                                "Microdetalhes muito pequenos são ignorados para evitar excesso de pontadas."
                        } else {
                            "Desativado: somente preenchimento Tatami, como na versão aprovada."
                        },
                        color = FioTextMuted,
                        fontSize = 10.sp
                    )
                    Text(
                        "Tatami preenche regiões fechadas com uma única cor. " +
                            "Respeita furos com contornos de sentidos opostos (regra SVG non-zero). " +
                            "Microdetalhes menores que 0,5 mm podem ficar sem preenchimento. " +
                            "Não inclui underlay automático nem ajustes de produção. " +
                            "Teste a matriz antes de bordar.",
                        color = FioTextMuted,
                        fontSize = 10.sp
                    )
                    Text(
                        "Espaçamento das linhas Tatami: ${oneDecimal(tatamiRowSpacingMm)} mm",
                        color = FioText,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0.4f, 0.5f, 0.6f).forEach { spacing ->
                            OutlinedButton(
                                onClick = { tatamiRowSpacingMm = spacing },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    (if (tatamiRowSpacingMm == spacing) "● " else "") +
                                        oneDecimal(spacing) + " mm",
                                    color = if (tatamiRowSpacingMm == spacing) FioGold else FioText
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        if (stitchStyle == TextStitchStyle.SATIN) {
                            "Satin segue o traço: não preenche automaticamente áreas fechadas."
                        } else {
                            "Ponto corrido segue o contorno/caminho importado."
                        },
                        color = FioTextMuted,
                        fontSize = 10.sp
                    )
                }

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

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

                OutlinedButton(
                    onClick = {
                        artwork?.let { source ->
                            VectorSvgExporter.export(source, color).fold(
                                onSuccess = { bytes ->
                                    pendingSvgBytes = bytes
                                    exportMessage = null
                                    val filename = source.label
                                        .replace(Regex("[^A-Za-z0-9._-]"), "_")
                                        .trim('_')
                                        .ifBlank { "desenho" }
                                        .take(64)
                                        .removeSuffix(".svg") + ".svg"
                                    svgSaver.launch(filename)
                                },
                                onFailure = { error ->
                                    exportMessage = error.message ?: "Não foi possível gerar o SVG."
                                }
                            )
                        }
                    },
                    enabled = artwork != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Salvar desenho como SVG")
                }

                exportMessage?.let { message ->
                    Text(
                        text = message,
                        color = FioTextMuted,
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

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
