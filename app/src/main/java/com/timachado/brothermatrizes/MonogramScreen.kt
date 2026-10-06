package com.timachado.brothermatrizes

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryFontPreset
import com.timachado.brothermatrizes.core.embroidery.FabricProfile
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.HoopValidator
import com.timachado.brothermatrizes.core.embroidery.MonogramGenerator
import com.timachado.brothermatrizes.core.embroidery.MonogramOptions
import com.timachado.brothermatrizes.core.embroidery.MonogramStyle
import com.timachado.brothermatrizes.core.embroidery.SatinUnderlayMode
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import java.util.Locale

private val monogramPalette =
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
fun MonogramScreen(
    onBack: () -> Unit,
    onCreate: (EmbroideryDesign) -> Unit,
    onSimulate: (EmbroideryDesign) -> Unit
) {
    var initials by remember {
        mutableStateOf("TM")
    }

    var style by remember {
        mutableStateOf(
            MonogramStyle.CLASSIC
        )
    }

    var heightMm by remember {
        mutableFloatStateOf(22f)
    }

    var spacingMm by remember {
        mutableFloatStateOf(2f)
    }

    var satinWidthMm by remember {
        mutableFloatStateOf(2.6f)
    }

    var satinDensityMm by remember {
        mutableFloatStateOf(0.45f)
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

    var font by remember {
        mutableStateOf(
            EmbroideryFontPreset.LINE
        )
    }

    var hoopProfile by remember {
        mutableStateOf(
            HoopProfile.H100X100
        )
    }

    var fabricProfile by remember {
        mutableStateOf(
            FabricProfile.COTTON
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
            0xE6BE70
        )
    }

    var selectedInitial by remember {
        mutableIntStateOf(0)
    }

    var initialColors by remember {
        mutableStateOf<
            Map<Int, Int>
        >(emptyMap())
    }

    val result =
        MonogramGenerator.generate(
            MonogramOptions(
                initials = initials,
                style = style,
                heightMm = heightMm,
                spacingMm = spacingMm,
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
                color = color,
                initialColors =
                    initials.indices
                        .map {
                                index ->
                            initialColors[
                                index
                            ] ?: color
                        },
                font = font,
                outputFormat =
                    outputFormat,
                hoopProfile =
                    hoopProfile,
                fabricProfile =
                    fabricProfile,
                enforceHoop =
                    false,
            )
        )

    val preview =
        result.getOrNull()

    val hoopFit =
        preview?.let {
            HoopValidator.validate(
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
                Alignment
                    .CenterVertically
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
                Modifier.weight(1f)
            ) {
                Text(
                    "Criar Monograma",
                    color = FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 19.sp
                )

                Text(
                    "1, 2 ou 3 iniciais em Satin",
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
                    "Digite de 1 a 3 iniciais.",
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
                    .weight(1.35f)
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
                    .padding(16.dp)
                    .verticalScroll(
                        rememberScrollState()
                    )
            ) {
                OutlinedTextField(
                    value = initials,
                    onValueChange = {
                            value ->
                        initials =
                            value
                                .filter {
                                    it.isLetter()
                                }
                                .take(3)
                                .uppercase(
                                    Locale
                                        .forLanguageTag(
                                            "pt-BR"
                                        )
                                )

                        selectedInitial =
                            0

                        initialColors =
                            emptyMap()
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    label = {
                        Text(
                            "Iniciais"
                        )
                    },
                    supportingText = {
                        Text(
                            "Ex.: TM, ABC • máximo 3 letras"
                        )
                    },
                    singleLine = true
                )

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

                Text(
                    "Cores das iniciais",
                    color = FioText,
                    fontWeight =
                        FontWeight.SemiBold
                )

                if (
                    initials.isNotEmpty()
                ) {
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
                            Arrangement
                                .spacedBy(
                                    8.dp
                                )
                    ) {
                        initials
                            .forEachIndexed {
                                    index,
                                    initial ->
                                val selected =
                                    selectedInitial ==
                                        index

                                OutlinedButton(
                                    onClick = {
                                        selectedInitial =
                                            index
                                    },
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
                                        (
                                            index +
                                                1
                                            ).toString() +
                                            " • " +
                                            initial
                                    )
                                }
                            }
                    }

                    val selectedInitialColor =
                        initialColors[
                            selectedInitial
                        ] ?: color

                    Row(
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
                            Arrangement
                                .spacedBy(
                                    9.dp
                                )
                    ) {
                        monogramPalette
                            .forEach {
                                    rawColor ->
                                val selected =
                                    rawColor ==
                                        selectedInitialColor

                                Box(
                                    Modifier
                                        .size(
                                            36.dp
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
                                            initialColors =
                                                initialColors +
                                                    (
                                                        selectedInitial to
                                                            rawColor
                                                        )
                                        }
                                )
                            }
                    }

                    if (
                        initialColors
                            .containsKey(
                                selectedInitial
                            )
                    ) {
                        TextButton(
                            onClick = {
                                initialColors =
                                    initialColors -
                                        selectedInitial
                            }
                        ) {
                            Text(
                                "Usar cor geral nesta inicial",
                                color =
                                    FioGold
                            )
                        }
                    }
                }

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

                Text(
                    "Estilo",
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
                        Arrangement
                            .spacedBy(
                                8.dp
                            )
                ) {
                    MonogramStyle
                        .entries
                        .forEach {
                                option ->
                            val selected =
                                style ==
                                    option

                            OutlinedButton(
                                onClick = {
                                    style =
                                        option
                                },
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
                                        "● " +
                                            option
                                                .displayName
                                    } else {
                                        option
                                            .displayName
                                    }
                                )
                            }
                        }
                }

                Text(
                    "Clássico aumenta a inicial central quando houver 3 letras.",
                    color =
                        FioTextMuted,
                    fontSize =
                        10.sp
                )

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

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
                        Arrangement
                            .spacedBy(
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
                                },
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
                                        "● " +
                                            option
                                                .displayName
                                    } else {
                                        option
                                            .displayName
                                    }
                                )
                            }
                        }
                }

                Text(
                    "Área segura: " +
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
                    color =
                        FioTextMuted,
                    fontSize =
                        10.sp
                )

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

                Text(
                    "Tecido",
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
                        Arrangement
                            .spacedBy(
                                8.dp
                            )
                ) {
                    FabricProfile
                        .entries
                        .forEach {
                                option ->
                            val selected =
                                fabricProfile ==
                                    option

                            OutlinedButton(
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
                                },
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
                                        "● " +
                                            option
                                                .displayName
                                    } else {
                                        option
                                            .displayName
                                    }
                                )
                            }
                        }
                }

                Text(
                    fabricProfile
                        .helperText +
                        " • ajustes continuam editáveis",
                    color =
                        FioTextMuted,
                    fontSize =
                        10.sp
                )

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

                Text(
                    "Biblioteca de fontes",
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
                        Arrangement
                            .spacedBy(
                                8.dp
                            )
                ) {
                    EmbroideryFontPreset
                        .entries
                        .forEach {
                                option ->
                            val selected =
                                font ==
                                    option

                            OutlinedButton(
                                onClick = {
                                    font =
                                        option
                                },
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
                                        "● " +
                                            option
                                                .displayName
                                    } else {
                                        option
                                            .displayName
                                    }
                                )
                            }
                        }
                }

                Text(
                    font.description,
                    color =
                        FioTextMuted,
                    fontSize =
                        10.sp
                )

                Spacer(
                    Modifier.height(
                        8.dp
                    )
                )

                Text(
                    "Altura " +
                        mm(heightMm) +
                        " mm",
                    color = FioText,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Slider(
                    value =
                        heightMm,
                    onValueChange = {
                        heightMm = it
                    },
                    valueRange =
                        8f..40f
                )

                Text(
                    "Espaçamento " +
                        mm(spacingMm) +
                        " mm",
                    color = FioText,
                    fontSize = 12.sp
                )

                Slider(
                    value =
                        spacingMm,
                    onValueChange = {
                        spacingMm = it
                    },
                    valueRange =
                        0f..8f
                )

                Text(
                    "Largura Satin " +
                        mm(
                            satinWidthMm
                        ) +
                        " mm",
                    color = FioText,
                    fontSize = 12.sp
                )

                Slider(
                    value =
                        satinWidthMm,
                    onValueChange = {
                        satinWidthMm = it
                    },
                    valueRange =
                        1.2f..5f
                )

                Text(
                    "Densidade " +
                        mm(
                            satinDensityMm
                        ) +
                        " mm",
                    color = FioText,
                    fontSize = 12.sp
                )

                Slider(
                    value =
                        satinDensityMm,
                    onValueChange = {
                        satinDensityMm = it
                    },
                    valueRange =
                        0.35f..0.9f
                )

                Text(
                    "Compensação " +
                        mm(
                            satinPullCompensationMm
                        ) +
                        " mm",
                    color = FioText,
                    fontSize = 12.sp
                )

                Slider(
                    value =
                        satinPullCompensationMm,
                    onValueChange = {
                        satinPullCompensationMm =
                            it
                    },
                    valueRange =
                        0f..0.8f
                )

                OutlinedButton(
                    onClick = {
                        satinShortStitches =
                            !satinShortStitches
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                ) {
                    Text(
                        if (
                            satinShortStitches
                        ) {
                            "✓ Short stitches ativados"
                        } else {
                            "Short stitches desativados"
                        }
                    )
                }

                Spacer(
                    Modifier.height(
                        10.dp
                    )
                )

                Text(
                    "Underlay",
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
                                6.dp
                        ),
                    horizontalArrangement =
                        Arrangement
                            .spacedBy(
                                8.dp
                            )
                ) {
                    SatinUnderlayMode
                        .entries
                        .forEach {
                                option ->
                            val selected =
                                satinUnderlayMode ==
                                    option

                            OutlinedButton(
                                onClick = {
                                    satinUnderlayMode =
                                        option
                                },
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
                                        "● " +
                                            option
                                                .displayName
                                    } else {
                                        option
                                            .displayName
                                    }
                                )
                            }
                        }
                }

                Spacer(
                    Modifier.height(
                        10.dp
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
                        Arrangement
                            .spacedBy(
                                9.dp
                            )
                ) {
                    monogramPalette
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
                    "Formato de saída",
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
                        Arrangement
                            .spacedBy(
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
                                Modifier
                                    .weight(
                                        1f
                                    ),
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
                                    "● $format"
                                } else {
                                    format
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
                            mm(
                                preview
                                    .bounds
                                    .widthMm
                            ) +
                            " × " +
                            mm(
                                preview
                                    .bounds
                                    .heightMm
                            ) +
                            " mm • " +
                            preview
                                .stitchCount +
                            " pontos",
                        color =
                            FioTextMuted,
                        fontSize =
                            11.sp
                    )

                    Spacer(
                        Modifier.height(
                            4.dp
                        )
                    )

                    if (fitsHoop) {
                        Text(
                            "✓ Monograma dentro da área segura",
                            color =
                                Color(
                                    0xFF7ED6A5
                                ),
                            fontSize =
                                11.sp
                        )
                    } else {
                        Text(
                            "⚠ Ultrapassa o bastidor" +
                                (
                                    hoopFit
                                        ?.let {
                                                fit ->
                                            " • excesso: " +
                                                mm(
                                                    fit.widthOverflowMm
                                                ) +
                                                " mm L / " +
                                                mm(
                                                    fit.heightOverflowMm
                                                ) +
                                                " mm A"
                                        }
                                        ?: ""
                                    ),
                            color =
                                Color(
                                    0xFFFF9F9A
                                ),
                            fontSize =
                                11.sp
                        )
                    }
                } else {
                    Text(
                        result
                            .exceptionOrNull()
                            ?.message
                            ?: "Não foi possível gerar a prévia.",
                        color =
                            Color(
                                0xFFFF9F9A
                            ),
                        fontSize =
                            11.sp
                    )
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
                        "Criar monograma",
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
                        "▶ Simular monograma"
                    )
                }
            }
        }
    }
}

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
