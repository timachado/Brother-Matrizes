package com.timachado.brothermatrizes

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryFontPreset
import com.timachado.brothermatrizes.core.embroidery.FabricProfile
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.HoopValidator
import com.timachado.brothermatrizes.core.embroidery.SatinUnderlayMode
import com.timachado.brothermatrizes.core.embroidery.SpecialStitchMode
import com.timachado.brothermatrizes.core.embroidery.TextGlyphProvider
import com.timachado.brothermatrizes.core.embroidery.TextHoopAutoFit
import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.TextLayoutMode
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.font.ImportedFontMatrixGenerator
import com.timachado.brothermatrizes.font.ImportedFontStore
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioSurfaceAlt
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val namePalette =
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
fun CreateNameScreen(
    onBack: () -> Unit,
    onCreate:
        (EmbroideryDesign, EmbroideryDisplayMode) ->
            Unit,
    onSimulate:
        (EmbroideryDesign, EmbroideryDisplayMode) ->
            Unit
) {
    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    val createNamePreferences =
        remember(
            context
        ) {
            context.getSharedPreferences(
                "create_name_preferences_v2",
                Context.MODE_PRIVATE
            )
        }

    val importedFonts =
        remember {
            ImportedFontStore
                .list(context)
        }

    var selectedTab by remember {
        mutableStateOf("Texto")
    }

    val initialDisplayMode =
        remember(
            createNamePreferences
        ) {
            val saved =
                createNamePreferences
                    .getString(
                        "display_mode",
                        null
                    )

            EmbroideryDisplayMode
                .entries
                .firstOrNull {
                    it.name ==
                        saved
                }
                ?: EmbroideryDisplayMode.SOLID
        }

    var displayMode by remember {
        /*
         * Primeira execução começa em Sólida. Depois disso, respeita a
         * escolha explícita do usuário entre Sólida / Pontos / Realista.
         */
        mutableStateOf(
            initialDisplayMode
        )
    }

    var showHoopPreview by remember {
        mutableStateOf(true)
    }

    val initialImportedFontId =
        remember(
            importedFonts,
            createNamePreferences
        ) {
            createNamePreferences
                .getString(
                    "imported_font_id",
                    null
                )
                ?.takeIf {
                        savedId ->
                    importedFonts.any {
                        it.id ==
                            savedId
                    }
                }
        }

    var importedFontId by remember {
        mutableStateOf<String?>(
            initialImportedFontId
        )
    }

    var text by remember {
        mutableStateOf("Maria")
    }

    var heightMm by remember {
        mutableFloatStateOf(18f)
    }

    var autoFitToHoop by remember {
        mutableStateOf(true)
    }

    var spacingMm by remember {
        mutableFloatStateOf(0f)
    }

    var stitchLengthMm by remember {
        mutableFloatStateOf(2.5f)
    }

    var stitchStyle by remember {
        mutableStateOf(
            TextStitchStyle.SATIN
        )
    }

    var satinWidthMm by remember {
        mutableFloatStateOf(2.4f)
    }

    var satinDensityMm by remember {
        mutableFloatStateOf(0.4f)
    }

    var satinPullCompensationMm by remember {
        mutableFloatStateOf(0.2f)
    }

    var satinShortStitches by remember {
        mutableStateOf(true)
    }

    var satinUnderlayMode by remember {
        mutableStateOf(
            SatinUnderlayMode.CENTER
        )
    }

    var specialStitchMode by remember {
        mutableStateOf<
            SpecialStitchMode?
        >(
            null
        )
    }

    val initialBuiltInFont =
        remember(
            createNamePreferences
        ) {
            val saved =
                createNamePreferences
                    .getString(
                        "built_in_font",
                        null
                    )

            EmbroideryFontPreset
                .entries
                .firstOrNull {
                    it.name ==
                        saved
                }
                ?: EmbroideryFontPreset.LINE
        }

    var font by remember {
        mutableStateOf(
            initialBuiltInFont
        )
    }

    var outputFormat by remember {
        mutableStateOf(
            MatrixConverter
                .preferredBrotherFormat
        )
    }

    var color by remember {
        mutableIntStateOf(
            0xE63946
        )
    }

    var hoopProfile by remember {
        mutableStateOf(
            HoopProfile.H100X100
        )
    }

    var hoopManuallySelected by remember {
        mutableStateOf(
            false
        )
    }

    var fabricProfile by remember {
        mutableStateOf(
            FabricProfile.COTTON
        )
    }

    var layoutMode by remember {
        mutableStateOf(
            TextLayoutMode.STRAIGHT
        )
    }

    var arcHeightMm by remember {
        mutableFloatStateOf(8f)
    }

    var rotationDegrees by remember {
        mutableFloatStateOf(0f)
    }

    val importedFont =
        importedFonts
            .firstOrNull {
                it.id ==
                    importedFontId
            }

    val referenceAdamyaSimulation =
        importedFont
            ?.displayName
            ?.lowercase(
                Locale.ROOT
            )
            ?.let {
                    name ->
                name.contains(
                    "adamya"
                ) ||
                    name.contains(
                        "ademya"
                    )
            }
            ?: false

    val glyphProvider =
        importedFont
            ?.let {
                    selected ->
                TextGlyphProvider(
                    preserveCase =
                        true,
                    spacingScale =
                        1f,
                    generate = {
                            char,
                            glyphOptions ->
                        ImportedFontMatrixGenerator
                            .generateGlyph(
                                font =
                                    selected,
                                char =
                                    char,
                                options =
                                    glyphOptions
                            )
                    },
                    generateText = {
                            sourceText,
                            textOptions ->
                        ImportedFontMatrixGenerator
                            .generateText(
                                font =
                                    selected,
                                text =
                                    sourceText,
                                options =
                                    textOptions
                            )
                    },
                    preserveWholeTextSequenceForSatin =
                        true
                )
            }

    fun layoutOptionsFor(
        targetHeightMm: Float
    ): TextLayoutOptions =
        TextLayoutOptions(
            textOptions =
                TextMatrixOptions(
                    text = text,
                    heightMm =
                        targetHeightMm,
                    spacingMm =
                        spacingMm,
                    stitchLengthMm =
                        stitchLengthMm,
                    style =
                        stitchStyle,
                    satinWidthMm =
                        satinWidthMm,
                    satinDensityMm =
                        satinDensityMm,
                    satinPullCompensationMm =
                        satinPullCompensationMm,
                    satinShortStitches =
                        satinShortStitches,
                    satinUnderlayMode =
                        satinUnderlayMode,
                    specialStitchMode =
                        specialStitchMode,
                    color =
                        color,
                    font =
                        font,
                    outputFormat =
                        outputFormat,
                    hoopProfile =
                        hoopProfile,
                    fabricProfile =
                        fabricProfile,
                    rotationDegrees =
                        rotationDegrees
                ),
            layoutMode =
                layoutMode,
            arcHeightMm =
                arcHeightMm,
            glyphProvider =
                glyphProvider
        )

    var previewResult by remember {
        mutableStateOf<
            Result<EmbroideryDesign>?
        >(
            null
        )
    }

    var previewUpdating by remember {
        mutableStateOf(false)
    }

    var simulationPreparing by remember {
        mutableStateOf(false)
    }

    val effectiveAutoFitToHoop =
        autoFitToHoop

    val previewHeightKey =
        if (
            effectiveAutoFitToHoop
        ) {
            0f
        } else {
            heightMm
        }

    /*
     * Trocar apenas o bastidor não altera a geometria TTF/OTF importada.
     * Para fontes importadas, o bastidor muda enquadramento/validação sem
     * reiniciar a digitalização Satin inteira.
     */
    val previewHoopKey =
        if (
            importedFont !=
                null
        ) {
            null
        } else {
            hoopProfile
        }

    LaunchedEffect(
        autoFitToHoop,
        previewHeightKey,
        text,
        spacingMm,
        stitchLengthMm,
        stitchStyle,
        satinWidthMm,
        satinDensityMm,
        satinPullCompensationMm,
        satinShortStitches,
        satinUnderlayMode,
        specialStitchMode,
        color,
        font,
        importedFontId,
        outputFormat,
        previewHoopKey,
        fabricProfile,
        layoutMode,
        arcHeightMm,
        rotationDegrees
    ) {
        if (
            text.isBlank()
        ) {
            previewResult =
                Result.failure(
                    IllegalArgumentException(
                        "Digite um nome."
                    )
                )
            previewUpdating =
                false
            return@LaunchedEffect
        }

        previewUpdating =
            true

        /*
         * Mantém a última prévia válida visível enquanto recalcula a nova.
         * Uma atualização normal não deve parecer erro nem piscar o canvas
         * para preto.
         */

        /*
         * Debounce curto: trocar fonte/slider rapidamente não deve iniciar
         * várias digitalizações Satin caras em sequência.
         */
        delay(
            120L
        )

        val requestedHeight =
            heightMm

        val baseOptions =
            layoutOptionsFor(
                requestedHeight
            )

        val generated =
            withContext(
                Dispatchers.Default
            ) {
                fun generateAt(
                    targetHeight: Float
                ): Result<EmbroideryDesign> =
                    TextLayoutGenerator
                        .generate(
                            baseOptions.copy(
                                textOptions =
                                    baseOptions
                                        .textOptions
                                        .copy(
                                            heightMm =
                                                targetHeight
                                        )
                            )
                        )

                if (
                    effectiveAutoFitToHoop &&
                    importedFont !=
                        null
                ) {
                    /*
                     * O MãoDesign preserva a altura solicitada e enquadra o
                     * desenho em um bastidor compatível. Não encolhemos a
                     * fonte importada para o H100X100 antes da simulação.
                     */
                    requestedHeight to
                        generateAt(
                            requestedHeight
                        )
                } else if (
                    effectiveAutoFitToHoop
                ) {
                    val fit =
                        TextHoopAutoFit
                            .fit(
                                hoop =
                                    hoopProfile
                            ) {
                                    candidateHeight ->
                                generateAt(
                                    candidateHeight
                                )
                            }
                            .getOrNull()

                    if (
                        fit !=
                            null
                    ) {
                        fit.heightMm to
                            Result.success(
                                fit.design
                            )
                    } else {
                        requestedHeight to
                            generateAt(
                                requestedHeight
                            )
                    }
                } else {
                    requestedHeight to
                        generateAt(
                            requestedHeight
                        )
                }
            }

        val resolvedHeight =
            generated.first

        val generatedDesign =
            generated.second
                .getOrNull()

        if (
            effectiveAutoFitToHoop &&
            importedFont !=
                null &&
            generatedDesign !=
                null &&
            !hoopManuallySelected
        ) {
            val automaticHoop =
                smallestHoopForEitherOrientation(
                    generatedDesign
                )

            if (
                hoopProfile !=
                    automaticHoop
            ) {
                hoopProfile =
                    automaticHoop
            }
        } else if (
            effectiveAutoFitToHoop &&
            kotlin.math.abs(
                heightMm -
                    resolvedHeight
            ) >=
            0.05f
        ) {
            heightMm =
                resolvedHeight
        }

        previewResult =
            generated.second

        previewUpdating =
            false
    }

    val result =
        previewResult

    val preview =
        result
            ?.getOrNull()

    val hoopFit =
        preview?.let {
            HoopValidator.validate(
                it,
                hoopProfile
            )
        }

    val fitsHoop =
        if (
            preview !=
                null &&
            importedFont !=
                null
        ) {
            fitsHoopEitherOrientation(
                design =
                    preview,
                hoop =
                    hoopProfile
            )
        } else {
            hoopFit?.fits ==
                true
        }

    Column(
        Modifier
            .fillMaxSize()
            .padding(
                horizontal =
                    10.dp
            )
    ) {
        Row(
            modifier =
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
                onClick =
                    onBack
            ) {
                Text(
                    "‹ Voltar",
                    color =
                        FioGold
                )
            }

            Column(
                Modifier.weight(
                    1f
                )
            ) {
                Text(
                    "Criar Nome",
                    color =
                        FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        19.sp
                )

                Text(
                    "Edite por etapas • a prévia atualiza na hora",
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
                    .weight(.92f)
                    .background(
                        Color(
                            0xFF071017
                        ),
                        RoundedCornerShape(
                            22.dp
                        )
                    )
        ) {
            if (
                preview !=
                    null
            ) {
                EmbroideryCanvas(
                    design =
                        preview,
                    hoop =
                        if (
                            showHoopPreview
                        ) {
                            hoopProfile
                        } else {
                            null
                        },
                    displayMode =
                        displayMode,
                    modifier =
                        Modifier.fillMaxSize()
                )

                if (
                    previewUpdating
                ) {
                    Text(
                        "Atualizando prévia…",
                        modifier =
                            Modifier
                                .align(
                                    Alignment.BottomCenter
                                )
                                .padding(
                                    10.dp
                                ),
                        color =
                            FioTextMuted,
                        fontSize =
                            10.sp
                    )
                }
            } else {
                val actualError =
                    result
                        ?.exceptionOrNull()
                        ?.message

                Text(
                    if (
                        previewUpdating
                    ) {
                        "Atualizando prévia…"
                    } else {
                        actualError
                            ?: "Digite um nome para gerar a prévia."
                    },
                    modifier =
                        Modifier.align(
                            Alignment.Center
                        )
                        .padding(
                            18.dp
                        ),
                    color =
                        if (
                            actualError !=
                                null
                        ) {
                            Color(
                                0xFFFF9F9A
                            )
                        } else {
                            FioTextMuted
                        },
                    fontSize =
                        11.sp
                )
            }
        }

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1.25f)
                    .padding(
                        top = 10.dp,
                        bottom = 8.dp
                    ),
            colors =
                CardDefaults.cardColors(
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
                    .fillMaxSize()
                    .padding(
                        12.dp
                    )
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(
                                rememberScrollState()
                            ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            6.dp
                        )
                ) {
                    listOf(
                        "Texto",
                        "Fonte",
                        "Tamanho",
                        "Cor",
                        "Mais"
                    ).forEach {
                            tab ->
                        val selected =
                            selectedTab ==
                                tab

                        Card(
                            modifier =
                                Modifier.clickable {
                                    selectedTab =
                                        tab
                                },
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        if (
                                            selected
                                        ) {
                                            FioGold
                                        } else {
                                            FioSurfaceAlt
                                        }
                                ),
                            shape =
                                RoundedCornerShape(
                                    14.dp
                                )
                        ) {
                            Text(
                                tab,
                                modifier =
                                    Modifier.padding(
                                        horizontal =
                                            14.dp,
                                        vertical =
                                            9.dp
                                    ),
                                color =
                                    if (
                                        selected
                                    ) {
                                        FioBackground
                                    } else {
                                        FioTextMuted
                                    },
                                fontWeight =
                                    if (
                                        selected
                                    ) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Medium
                                    },
                                fontSize =
                                    11.sp
                            )
                        }
                    }
                }

                Spacer(
                    Modifier.height(
                        10.dp
                    )
                )

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .verticalScroll(
                                rememberScrollState()
                            )
                ) {
                    when (
                        selectedTab
                    ) {
                        "Texto" -> {
                            OutlinedTextField(
                                value =
                                    text,
                                onValueChange = {
                                    text =
                                        it.take(
                                            24
                                        )
                                },
                                modifier =
                                    Modifier.fillMaxWidth(),
                                label = {
                                    Text(
                                        "Digite o nome"
                                    )
                                },
                                singleLine =
                                    true
                            )

                            Spacer(
                                Modifier.height(
                                    10.dp
                                )
                            )

                            Text(
                                "Formato do texto",
                                color =
                                    FioText,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(
                                            rememberScrollState()
                                        )
                                        .padding(
                                            vertical =
                                                6.dp
                                        ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        7.dp
                                    )
                            ) {
                                TextLayoutMode
                                    .entries
                                    .forEach {
                                            option ->
                                        ChoiceButton(
                                            text =
                                                option.displayName,
                                            selected =
                                                layoutMode ==
                                                    option,
                                            onClick = {
                                                layoutMode =
                                                    option
                                            }
                                        )
                                    }
                            }

                            if (
                                layoutMode !=
                                    TextLayoutMode.STRAIGHT
                            ) {
                                Text(
                                    "Curvatura " +
                                        mm(
                                            arcHeightMm
                                        ) +
                                        " mm",
                                    color =
                                        FioTextMuted,
                                    fontSize =
                                        11.sp
                                )

                                Slider(
                                    value =
                                        arcHeightMm,
                                    onValueChange = {
                                        arcHeightMm =
                                            it
                                    },
                                    valueRange =
                                        0f..30f
                                )
                            }

                            if (
                                importedFont !=
                                    null
                            ) {
                                Spacer(
                                    Modifier.height(
                                        8.dp
                                    )
                                )

                                Text(
                                    "Rotação do bordado",
                                    color =
                                        FioText,
                                    fontWeight =
                                        FontWeight.SemiBold
                                )

                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(
                                                rememberScrollState()
                                            )
                                            .padding(
                                                vertical =
                                                    6.dp
                                            ),
                                    horizontalArrangement =
                                        Arrangement.spacedBy(
                                            7.dp
                                        )
                                ) {
                                    listOf(
                                        0f to "0°",
                                        90f to "90°",
                                        -90f to "-90°"
                                    ).forEach {
                                            rotation ->
                                        ChoiceButton(
                                            text =
                                                rotation.second,
                                            selected =
                                                rotationDegrees ==
                                                    rotation.first,
                                            onClick = {
                                                rotationDegrees =
                                                    rotation.first
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        "Fonte" -> {
                            Text(
                                "Fontes Brother Matrizes",
                                color =
                                    FioText,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(
                                            rememberScrollState()
                                        )
                                        .padding(
                                            vertical =
                                                6.dp
                                        ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        7.dp
                                    )
                            ) {
                                EmbroideryFontPreset
                                    .entries
                                    .forEach {
                                            option ->
                                        ChoiceButton(
                                            text =
                                                option.displayName,
                                            selected =
                                                importedFontId ==
                                                    null &&
                                                    font ==
                                                        option,
                                            onClick = {
                                                font =
                                                    option
                                                importedFontId =
                                                    null

                                                createNamePreferences
                                                    .edit()
                                                    .putString(
                                                        "built_in_font",
                                                        option.name
                                                    )
                                                    .remove(
                                                        "imported_font_id"
                                                    )
                                                    .apply()

                                                rotationDegrees =
                                                    0f
                                            }
                                        )
                                    }
                            }

                            Spacer(
                                Modifier.height(
                                    8.dp
                                )
                            )

                            Text(
                                "Fontes salvas",
                                color =
                                    FioText,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            if (
                                importedFonts
                                    .isEmpty()
                            ) {
                                Text(
                                    "Nenhuma TTF/OTF salva. Use Biblioteca de fontes na Home para importar e salvar.",
                                    color =
                                        FioTextMuted,
                                    fontSize =
                                        10.sp
                                )
                            } else {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(
                                                rememberScrollState()
                                            )
                                            .padding(
                                                vertical =
                                                    6.dp
                                            ),
                                    horizontalArrangement =
                                        Arrangement.spacedBy(
                                            7.dp
                                        )
                                ) {
                                    importedFonts
                                        .forEach {
                                                imported ->
                                            ChoiceButton(
                                                text =
                                                    imported.displayName,
                                                selected =
                                                    importedFontId ==
                                                        imported.id,
                                                onClick = {
                                                    importedFontId =
                                                        imported.id

                                                    createNamePreferences
                                                        .edit()
                                                        .putString(
                                                            "imported_font_id",
                                                            imported.id
                                                        )
                                                        .apply()

                                                    /*
                                                     * Rotação é opção do layout/pedido, nunca
                                                     * propriedade da fonte.
                                                     */
                                                    rotationDegrees =
                                                        0f
                                                }
                                            )
                                        }
                                }
                            }

                            Text(
                                "Motor Satin por fluxo de traço: preserva o contorno da TTF/OTF, mantém continuidade das colunas e evita saltos/ramificações redundantes.",
                                color =
                                    FioTextMuted,
                                fontSize =
                                    10.sp
                            )
                        }

                        "Tamanho" -> {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Column(
                                    Modifier.weight(
                                        1f
                                    )
                                ) {
                                    Text(
                                        "Ajustar ao bastidor",
                                        color =
                                            FioText,
                                        fontWeight =
                                            FontWeight.SemiBold
                                    )

                                    Text(
                                        "Calcula automaticamente o maior tamanho que cabe na área segura.",
                                        color =
                                            FioTextMuted,
                                        fontSize =
                                            10.sp
                                    )
                                }

                                Switch(
                                    checked =
                                        autoFitToHoop,
                                    onCheckedChange = {
                                        autoFitToHoop =
                                            it
                                    }
                                )
                            }

                            if (
                                autoFitToHoop
                            ) {
                                Text(
                                    "Automático • " +
                                        mm(
                                            heightMm
                                        ) +
                                        " mm • área útil " +
                                        mm(
                                            hoopProfile
                                                .usableWidthMm
                                        ) +
                                        " × " +
                                        mm(
                                            hoopProfile
                                                .usableHeightMm
                                        ) +
                                        " mm",
                                    modifier =
                                        Modifier.padding(
                                            top =
                                                6.dp
                                        ),
                                    color =
                                        FioGold,
                                    fontWeight =
                                        FontWeight.SemiBold,
                                    fontSize =
                                        10.sp
                                )
                            }

                            Spacer(
                                Modifier.height(
                                    8.dp
                                )
                            )

                            Text(
                                "Altura " +
                                    mm(
                                        heightMm
                                    ) +
                                    " mm",
                                color =
                                    FioText,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Slider(
                                value =
                                    heightMm,
                                onValueChange = {
                                    heightMm =
                                        it
                                },
                                enabled =
                                    !autoFitToHoop,
                                valueRange =
                                    TextHoopAutoFit
                                        .MIN_HEIGHT_MM..
                                        TextHoopAutoFit
                                            .MAX_HEIGHT_MM
                            )

                            Text(
                                "Ajuste de espaçamento " +
                                    mm(
                                        spacingMm
                                    ) +
                                    " mm",
                                color =
                                    FioTextMuted,
                                fontSize =
                                    11.sp
                            )

                            Slider(
                                value =
                                    spacingMm,
                                onValueChange = {
                                    spacingMm =
                                        it
                                },
                                valueRange =
                                    0f..8f
                            )

                            Text(
                                "Tipo de ponto",
                                color =
                                    FioText,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            vertical =
                                                6.dp
                                        ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        7.dp
                                    )
                            ) {
                                TextStitchStyle
                                    .entries
                                    .forEach {
                                            option ->
                                        ChoiceButton(
                                            modifier =
                                                Modifier.weight(
                                                    1f
                                                ),
                                            text =
                                                option.displayName,
                                            selected =
                                                stitchStyle ==
                                                    option,
                                            onClick = {
                                                stitchStyle =
                                                    option
                                                specialStitchMode =
                                                    null
                                            }
                                        )
                                    }
                            }

                            if (
                                stitchStyle ==
                                    TextStitchStyle.RUNNING
                            ) {
                                Text(
                                    "Comprimento do ponto " +
                                        mm(
                                            stitchLengthMm
                                        ) +
                                        " mm",
                                    color =
                                        FioTextMuted,
                                    fontSize =
                                        11.sp
                                )

                                Slider(
                                    value =
                                        stitchLengthMm,
                                    onValueChange = {
                                        stitchLengthMm =
                                            it
                                    },
                                    valueRange =
                                        1f..5f
                                )
                            } else {
                                Text(
                                    "Densidade " +
                                        mm(
                                            satinDensityMm
                                        ) +
                                        " mm",
                                    color =
                                        FioTextMuted,
                                    fontSize =
                                        11.sp
                                )

                                Slider(
                                    value =
                                        satinDensityMm,
                                    onValueChange = {
                                        satinDensityMm =
                                            it
                                    },
                                    valueRange =
                                        .3f..1.2f
                                )
                            }
                        }

                        "Cor" -> {
                            Text(
                                "Cor da linha",
                                color =
                                    FioText,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Row(
                                modifier =
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
                                namePalette
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
                                "A prévia e a simulação usam a mesma cor selecionada.",
                                color =
                                    FioTextMuted,
                                fontSize =
                                    10.sp
                            )
                        }

                        else -> {
                            Text(
                                "Exibição",
                                color =
                                    FioText,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(
                                            rememberScrollState()
                                        )
                                        .padding(
                                            vertical =
                                                6.dp
                                        ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        7.dp
                                    )
                            ) {
                                EmbroideryDisplayMode
                                    .entries
                                    .forEach {
                                            mode ->
                                        ChoiceButton(
                                            text =
                                                mode.displayName,
                                            selected =
                                                displayMode ==
                                                    mode,
                                            onClick = {
                                                displayMode =
                                                    mode

                                                createNamePreferences
                                                    .edit()
                                                    .putString(
                                                        "display_mode",
                                                        mode.name
                                                    )
                                                    .apply()
                                            }
                                        )
                                    }
                            }

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            bottom =
                                                8.dp
                                        ),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Column(
                                    Modifier.weight(
                                        1f
                                    )
                                ) {
                                    Text(
                                        "Mostrar bastidor",
                                        color =
                                            FioText,
                                        fontWeight =
                                            FontWeight.SemiBold
                                    )

                                    Text(
                                        "Oculta apenas o bastidor da prévia.",
                                        color =
                                            FioTextMuted,
                                        fontSize =
                                            10.sp
                                    )
                                }

                                Switch(
                                    checked =
                                        showHoopPreview,
                                    onCheckedChange = {
                                        showHoopPreview =
                                            it
                                    }
                                )
                            }

                            Text(
                                "Bastidor",
                                color =
                                    FioText,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(
                                            rememberScrollState()
                                        )
                                        .padding(
                                            vertical =
                                                6.dp
                                        ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        7.dp
                                    )
                            ) {
                                HoopProfile
                                    .entries
                                    .forEach {
                                            option ->
                                        ChoiceButton(
                                            text =
                                                option.displayName,
                                            selected =
                                                hoopProfile ==
                                                    option,
                                            onClick = {
                                                hoopManuallySelected =
                                                    true
                                                hoopProfile =
                                                    option
                                            }
                                        )
                                    }
                            }

                            Text(
                                "Área útil: " +
                                    mm(
                                        hoopProfile
                                            .usableWidthMm
                                    ) +
                                    " × " +
                                    mm(
                                        hoopProfile
                                            .usableHeightMm
                                    ) +
                                    " mm • margem segura " +
                                    mm(
                                        hoopProfile
                                            .safeMarginMm
                                    ) +
                                    " mm" +
                                    if (
                                        autoFitToHoop
                                    ) {
                                        " • tamanho automático ativo"
                                    } else {
                                        ""
                                    },
                                color =
                                    FioTextMuted,
                                fontSize =
                                    10.sp
                            )

                            Text(
                                "Tecido",
                                color =
                                    FioText,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(
                                            rememberScrollState()
                                        )
                                        .padding(
                                            vertical =
                                                6.dp
                                        ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        7.dp
                                    )
                            ) {
                                FabricProfile
                                    .entries
                                    .forEach {
                                            option ->
                                        ChoiceButton(
                                            text =
                                                option.displayName,
                                            selected =
                                                fabricProfile ==
                                                    option,
                                            onClick = {
                                                fabricProfile =
                                                    option
                                                satinDensityMm =
                                                    option
                                                        .satinDensityMm
                                                satinPullCompensationMm =
                                                    option
                                                        .pullCompensationMm
                                                satinUnderlayMode =
                                                    option
                                                        .underlayMode
                                                satinShortStitches =
                                                    option
                                                        .shortStitches
                                            }
                                        )
                                    }
                            }

                            Text(
                                "Formato",
                                color =
                                    FioText,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            vertical =
                                                6.dp
                                        ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        7.dp
                                    )
                            ) {
                                MatrixConverter
                                    .supportedFormats
                                    .forEach {
                                        format ->
                                    ChoiceButton(
                                        modifier =
                                            Modifier.weight(
                                                1f
                                            ),
                                        text =
                                            format,
                                        selected =
                                            outputFormat ==
                                                format,
                                        onClick = {
                                            outputFormat =
                                                format
                                        }
                                    )
                                }
                            }

                            if (
                                stitchStyle ==
                                    TextStitchStyle.SATIN
                            ) {
                                Text(
                                    "Compensação de repuxo " +
                                        mm(
                                            satinPullCompensationMm
                                        ) +
                                        " mm",
                                    color =
                                        FioTextMuted,
                                    fontSize =
                                        11.sp
                                )

                                Slider(
                                    value =
                                        satinPullCompensationMm,
                                    onValueChange = {
                                        satinPullCompensationMm =
                                            it
                                    },
                                    valueRange =
                                        0f..1f
                                )


                            }

                            Text(
                                "Tipo de ponto especial",
                                modifier =
                                    Modifier.padding(
                                        top =
                                            10.dp
                                    ),
                                color =
                                    FioText,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(
                                            rememberScrollState()
                                        )
                                        .padding(
                                            vertical =
                                                6.dp
                                        ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        7.dp
                                    )
                            ) {
                                SpecialStitchMode
                                    .entries
                                    .forEach {
                                            option ->
                                        ChoiceButton(
                                            text =
                                                when (
                                                    option
                                                ) {
                                                    SpecialStitchMode.BEAN ->
                                                        "Feijão"

                                                    SpecialStitchMode.TRIPLE_RUNNING ->
                                                        "Corrido triplo"

                                                    SpecialStitchMode.PROGRAMMED_MOTIF ->
                                                        "Motivo"
                                                },
                                            selected =
                                                specialStitchMode ==
                                                    option,
                                            onClick = {
                                                specialStitchMode =
                                                    if (
                                                        specialStitchMode ==
                                                            option
                                                    ) {
                                                        null
                                                    } else {
                                                        option
                                                    }

                                                if (
                                                    specialStitchMode !=
                                                        null
                                                ) {
                                                    stitchStyle =
                                                        TextStitchStyle.RUNNING
                                                }
                                            }
                                        )
                                    }
                            }

                            specialStitchMode
                                ?.let {
                                        selected ->
                                    Text(
                                        selected.displayName,
                                        color =
                                            FioGold,
                                        fontWeight =
                                            FontWeight.SemiBold,
                                        fontSize =
                                            11.sp
                                    )

                                    Text(
                                        selected.description,
                                        color =
                                            FioTextMuted,
                                        fontSize =
                                            10.sp
                                    )
                                }
                        }
                    }
                }

                Spacer(
                    Modifier.height(
                        8.dp
                    )
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    OutlinedButton(
                        onClick = {
                            val created =
                                preview

                            if (
                                created !=
                                    null
                            ) {
                                /*
                                 * A simulação deve consumir exatamente a mesma
                                 * matriz exibida em Criar Nome.
                                 *
                                 * Antes, Adamya/Ademya era regenerada aqui com
                                 * rotationDegrees=90f, apesar de a criação estar
                                 * em 0°. Depois da troca para stroke-flow, isso
                                 * passou a aplicar uma segunda transformação e
                                 * deixou o nome vertical/invertido na simulação.
                                 *
                                 * O bastidor automático pode mudar, mas os
                                 * pontos, a orientação e a ordem da costura não.
                                 */
                                val simulationDesign =
                                    if (
                                        importedFont !=
                                            null &&
                                        referenceAdamyaSimulation
                                    ) {
                                        created.copy(
                                            hoopProfile =
                                                smallestHoopForEitherOrientation(
                                                    created
                                                )
                                        )
                                    } else {
                                        created
                                    }

                                onSimulate(
                                    simulationDesign,
                                    displayMode
                                )
                            }
                        },
                        enabled =
                            preview !=
                                null &&
                                fitsHoop &&
                                !previewUpdating &&
                                !simulationPreparing,
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {
                        Text(
                            if (
                                simulationPreparing
                            ) {
                                "Preparando…"
                            } else {
                                "▶ Simular"
                            }
                        )
                    }

                    Button(
                        onClick = {
                            preview?.let {
                                onCreate(
                                    it,
                                    displayMode
                                )
                            }
                        },
                        enabled =
                            preview !=
                                null &&
                                fitsHoop &&
                                !previewUpdating,
                        modifier =
                            Modifier.weight(
                                1.3f
                            ),
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
                            "Criar matriz",
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }

                Spacer(
                    Modifier.height(
                        4.dp
                    )
                )

                if (
                    preview !=
                        null
                ) {
                    Text(
                        mm(
                            preview.bounds
                                .widthMm
                        ) +
                            " × " +
                            mm(
                                preview.bounds
                                    .heightMm
                            ) +
                            " mm • " +
                            preview.stitchCount +
                            " pontos" +
                            if (
                                fitsHoop
                            ) {
                                " • ✓ cabe no bastidor"
                            } else {
                                " • ⚠ fora da área segura"
                            },
                        modifier =
                            Modifier.fillMaxWidth(),
                        color =
                            if (
                                fitsHoop
                            ) {
                                FioTextMuted
                            } else {
                                Color(
                                    0xFFFF9F9A
                                )
                            },
                        fontSize =
                            10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick =
            onClick,
        modifier =
            modifier,
        colors =
            ButtonDefaults
                .outlinedButtonColors(
                    contentColor =
                        if (
                            selected
                        ) {
                            FioGold
                        } else {
                            FioText
                        }
                )
    ) {
        Text(
            if (
                selected
            ) {
                "● $text"
            } else {
                text
            },
            fontSize =
                11.sp
        )
    }
}


private fun fitsHoopEitherOrientation(
    design: EmbroideryDesign,
    hoop: HoopProfile
): Boolean {
    val width =
        design.bounds.widthMm
    val height =
        design.bounds.heightMm

    val normal =
        width <=
            hoop.usableWidthMm &&
        height <=
            hoop.usableHeightMm

    val rotated =
        width <=
            hoop.usableHeightMm &&
        height <=
            hoop.usableWidthMm

    return normal ||
        rotated
}

private fun smallestHoopForEitherOrientation(
    design: EmbroideryDesign
): HoopProfile =
    HoopProfile.entries
        .firstOrNull {
                hoop ->
            fitsHoopEitherOrientation(
                design =
                    design,
                hoop =
                    hoop
            )
        }
        ?: HoopProfile.entries
            .last()

private fun mm(
    value: Float
): String =
    String.format(
        Locale.forLanguageTag(
            "pt-BR"
        ),
        "%.1f",
        value
    )
