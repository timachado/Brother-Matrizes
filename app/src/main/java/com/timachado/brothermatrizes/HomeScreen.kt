package com.timachado.brothermatrizes

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioGoldSoft
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioSurfaceAlt
import com.timachado.brothermatrizes.ui.theme.FioSurfaceHigh
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import java.util.Locale

@Composable
fun HomeScreen(
    recent: EmbroideryDesign?,
    onCreateName: () -> Unit,
    onFonts: () -> Unit,
    onTransfer: () -> Unit,
    onOpen: () -> Unit,
    onRecent: () -> Unit,
    onSimulate: () -> Unit,
    onEdit: () -> Unit,
    onConvert: () -> Unit,
    onClearRecent: () -> Unit,
    onUnavailable: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        contentPadding =
            PaddingValues(
                top = 10.dp,
                bottom = 28.dp
            ),
        horizontalArrangement =
            Arrangement.spacedBy(12.dp),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {
        item(
            span = {
                GridItemSpan(maxLineSpan)
            }
        ) {
            Column(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {
                Image(
                    painter =
                        painterResource(
                            R.drawable.brother_matrizes_brand_logo
                        ),
                    contentDescription =
                        "Brother Matrizes",
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(112.dp),
                    contentScale =
                        ContentScale.Fit
                )

                Text(
                    "Do nome à máquina, direto pelo celular.",
                    color = FioTextMuted,
                    style =
                        MaterialTheme.typography
                            .bodyMedium,
                    textAlign =
                        TextAlign.Center
                )
            }
        }

        item(
            span = {
                GridItemSpan(maxLineSpan)
            }
        ) {
            Surface(
                color = FioSurface,
                shape =
                    MaterialTheme.shapes
                        .extraLarge,
                tonalElevation = 2.dp
            ) {
                Column(
                    Modifier.padding(
                        horizontal = 20.dp,
                        vertical = 18.dp
                    )
                ) {
                    Text(
                        "O que vamos bordar hoje?",
                        color = FioText,
                        style =
                            MaterialTheme.typography
                                .headlineSmall
                    )

                    Spacer(
                        Modifier.height(6.dp)
                    )

                    Text(
                        "Comece pelo nome ou escolha uma ferramenta.",
                        color = FioTextMuted,
                        style =
                            MaterialTheme.typography
                                .bodyMedium
                    )
                }
            }
        }

        item(
            span = {
                GridItemSpan(maxLineSpan)
            }
        ) {
            ExpressiveFeatureCard(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = 132.dp
                        ),
                iconRes =
                    R.drawable
                        .ic_ms_text_fields_rounded,
                title = "Criar nome",
                subtitle =
                    "Crie a matriz e ajuste fonte, tamanho, cor e ponto.",
                enabled = true,
                primary = true,
                onClick =
                    onCreateName
            )
        }

        item(
            span = {
                GridItemSpan(maxLineSpan)
            }
        ) {
            Text(
                "Ferramentas",
                color = FioText,
                style =
                    MaterialTheme.typography
                        .titleLarge,
                modifier =
                    Modifier.padding(
                        top = 4.dp,
                        bottom = 2.dp
                    )
            )
        }

        item {
            ExpressiveFeatureCard(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = 136.dp
                        ),
                iconRes =
                    R.drawable
                        .ic_ms_font_download_rounded,
                title = "Biblioteca de fontes",
                subtitle =
                    "Salve e gerencie TTF/OTF",
                enabled = true,
                onClick =
                    onFonts
            )
        }

        item {
            ExpressiveFeatureCard(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = 136.dp
                        ),
                iconRes =
                    R.drawable
                        .ic_ms_folder_open_rounded,
                title = "Abrir matriz",
                subtitle =
                    "DST, JEF e PES",
                enabled = true,
                onClick =
                    onOpen
            )
        }

        item {
            ExpressiveFeatureCard(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = 136.dp
                        ),
                iconRes =
                    R.drawable
                        .ic_ms_play_arrow_rounded,
                title = "Simulador",
                subtitle =
                    "Veja a sequência real",
                enabled =
                    recent != null
            ) {
                if (
                    recent != null
                ) {
                    onSimulate()
                } else {
                    onUnavailable(
                        "Crie ou abra uma matriz primeiro."
                    )
                }
            }
        }

        item {
            ExpressiveFeatureCard(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = 136.dp
                        ),
                iconRes =
                    R.drawable
                        .ic_ms_edit_rounded,
                title = "Editor",
                subtitle =
                    "Mover, girar e cores",
                enabled =
                    recent != null
            ) {
                if (
                    recent != null
                ) {
                    onEdit()
                } else {
                    onUnavailable(
                        "Crie ou abra uma matriz primeiro."
                    )
                }
            }
        }

        item {
            ExpressiveFeatureCard(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = 136.dp
                        ),
                iconRes =
                    R.drawable
                        .ic_ms_swap_horiz_rounded,
                title = "Converter",
                subtitle =
                    "DST, PES e JEF",
                enabled =
                    recent != null
            ) {
                if (
                    recent != null
                ) {
                    onConvert()
                } else {
                    onUnavailable(
                        "Crie ou abra uma matriz primeiro."
                    )
                }
            }
        }

        item {
            ExpressiveFeatureCard(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = 136.dp
                        ),
                iconRes =
                    R.drawable
                        .ic_ms_upload_rounded,
                title = "Enviar",
                subtitle =
                    "OTG ou Wi-Fi/app",
                enabled =
                    recent != null
            ) {
                if (
                    recent != null
                ) {
                    onTransfer()
                } else {
                    onUnavailable(
                        "Crie ou abra uma matriz primeiro."
                    )
                }
            }
        }

        item(
            span = {
                GridItemSpan(maxLineSpan)
            }
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 6.dp,
                            bottom = 2.dp
                        ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Text(
                    "Recente",
                    modifier =
                        Modifier.weight(
                            1f
                        ),
                    color = FioText,
                    style =
                        MaterialTheme.typography
                            .titleLarge
                )

                if (
                    recent !=
                        null
                ) {
                    TextButton(
                        onClick =
                            onClearRecent
                    ) {
                        Text(
                            "Limpar recentes",
                            color =
                                Color(
                                    0xFFFF9F9A
                                ),
                            style =
                                MaterialTheme
                                    .typography
                                    .labelMedium
                        )
                    }
                }
            }
        }

        item(
            span = {
                GridItemSpan(maxLineSpan)
            }
        ) {
            if (
                recent ==
                    null
            ) {
                Surface(
                    color = FioSurface,
                    shape =
                        MaterialTheme.shapes
                            .large
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Nenhuma matriz nesta sessão.",
                            color = FioTextMuted,
                            style =
                                MaterialTheme.typography
                                    .bodyMedium,
                            textAlign =
                                TextAlign.Center
                        )

                        Spacer(
                            Modifier.height(
                                12.dp
                            )
                        )

                        Button(
                            onClick =
                                onCreateName,
                            colors =
                                ButtonDefaults
                                    .buttonColors(
                                        containerColor =
                                            FioGold,
                                        contentColor =
                                            FioBackground
                                    ),
                            shape =
                                MaterialTheme.shapes
                                    .large
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        R.drawable
                                            .ic_ms_text_fields_rounded
                                    ),
                                contentDescription =
                                    null,
                                modifier =
                                    Modifier.size(
                                        20.dp
                                    )
                            )

                            Spacer(
                                Modifier.size(
                                    8.dp
                                )
                            )

                            Text(
                                "Criar primeiro nome",
                                style =
                                    MaterialTheme
                                        .typography
                                        .labelLarge
                            )
                        }
                    }
                }
            } else {
                ElevatedCard(
                    onClick =
                        onRecent,
                    colors =
                        CardDefaults
                            .elevatedCardColors(
                                containerColor =
                                    FioSurfaceHigh
                            ),
                    elevation =
                        CardDefaults
                            .elevatedCardElevation(
                                defaultElevation =
                                    3.dp
                            ),
                    shape =
                        MaterialTheme.shapes
                            .large
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier =
                                Modifier.size(
                                    48.dp
                                ),
                            shape =
                                CircleShape,
                            color =
                                FioGold.copy(
                                    alpha =
                                        .18f
                                )
                        ) {
                            Box(
                                contentAlignment =
                                    Alignment.Center
                            ) {
                                Icon(
                                    painter =
                                        painterResource(
                                            R.drawable
                                                .ic_ms_folder_open_rounded
                                        ),
                                    contentDescription =
                                        null,
                                    tint = FioGold,
                                    modifier =
                                        Modifier.size(
                                            24.dp
                                        )
                                )
                            }
                        }

                        Column(
                            Modifier
                                .weight(1f)
                                .padding(
                                    start =
                                        14.dp
                                )
                        ) {
                            Text(
                                recent.fileName,
                                color = FioText,
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium
                            )

                            Text(
                                recent.format +
                                    " • " +
                                    formatMm(
                                        recent.bounds
                                            .widthMm
                                    ) +
                                    " × " +
                                    formatMm(
                                        recent.bounds
                                            .heightMm
                                    ) +
                                    " mm • " +
                                    recent.stitchCount +
                                    " pontos",
                                color =
                                    FioTextMuted,
                                style =
                                    MaterialTheme
                                        .typography
                                        .labelMedium
                            )
                        }

                        TextButton(
                            onClick =
                                onClearRecent
                        ) {
                            Text(
                                "✕",
                                color =
                                    Color(
                                        0xFFFF9F9A
                                    ),
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium
                            )
                        }
                    }
                }
            }
        }

        item(
            span = {
                GridItemSpan(maxLineSpan)
            }
        ) {
            Text(
                "Brother Matrizes ${BuildConfig.VERSION_NAME} • Material 3 Expressive",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                color = FioTextMuted,
                style =
                    MaterialTheme.typography
                        .labelMedium,
                textAlign =
                    TextAlign.Center
            )
        }
    }
}

@Composable
private fun ExpressiveFeatureCard(
    modifier: Modifier,
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    enabled: Boolean,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    val container =
        when {
            primary ->
                FioGold

            enabled ->
                FioSurfaceHigh

            else ->
                FioSurface
        }

    val titleColor =
        if (
            primary
        ) {
            FioBackground
        } else if (
            enabled
        ) {
            FioText
        } else {
            FioTextMuted
        }

    val subtitleColor =
        if (
            primary
        ) {
            Color(
                0xCC241704
            )
        } else {
            FioTextMuted
        }

    ElevatedCard(
        modifier =
            modifier,
        onClick =
            onClick,
        enabled =
            enabled,
        colors =
            CardDefaults
                .elevatedCardColors(
                    containerColor =
                        container,
                    disabledContainerColor =
                        FioSurface,
                    disabledContentColor =
                        FioTextMuted
                ),
        elevation =
            CardDefaults
                .elevatedCardElevation(
                    defaultElevation =
                        if (
                            primary
                        ) {
                            6.dp
                        } else {
                            2.dp
                        },
                    pressedElevation =
                        1.dp
                ),
        shape =
            if (
                primary
            ) {
                MaterialTheme.shapes
                    .extraLarge
            } else {
                MaterialTheme.shapes
                    .large
            }
    ) {
        if (
            primary
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Surface(
                    modifier =
                        Modifier.size(
                            58.dp
                        ),
                    shape =
                        MaterialTheme.shapes
                            .large,
                    color =
                        FioBackground.copy(
                            alpha =
                                .12f
                        )
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    iconRes
                                ),
                            contentDescription =
                                null,
                            tint =
                                FioBackground,
                            modifier =
                                Modifier.size(
                                    30.dp
                                )
                        )
                    }
                }

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(
                                start =
                                    16.dp
                            )
                ) {
                    Text(
                        title,
                        color =
                            titleColor,
                        style =
                            MaterialTheme
                                .typography
                                .titleLarge
                    )

                    Spacer(
                        Modifier.height(
                            4.dp
                        )
                    )

                    Text(
                        subtitle,
                        color =
                            subtitleColor,
                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium
                    )
                }
            }
        } else {
            Column(
                Modifier.padding(
                    16.dp
                )
            ) {
                Surface(
                    modifier =
                        Modifier.size(
                            46.dp
                        ),
                    shape =
                        CircleShape,
                    color =
                        if (
                            enabled
                        ) {
                            FioGold.copy(
                                alpha =
                                    .16f
                            )
                        } else {
                            FioSurfaceAlt
                        }
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    iconRes
                                ),
                            contentDescription =
                                null,
                            tint =
                                if (
                                    enabled
                                ) {
                                    FioGoldSoft
                                } else {
                                    FioTextMuted
                                },
                            modifier =
                                Modifier.size(
                                    24.dp
                                )
                        )
                    }
                }

                Spacer(
                    Modifier.height(
                        16.dp
                    )
                )

                Text(
                    title,
                    color =
                        titleColor,
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )

                Spacer(
                    Modifier.height(
                        4.dp
                    )
                )

                Text(
                    subtitle,
                    color =
                        subtitleColor,
                    style =
                        MaterialTheme
                            .typography
                            .labelMedium
                )
            }
        }
    }
}

private fun formatMm(
    value: Float
): String =
    String.format(
        Locale.forLanguageTag(
            "pt-BR"
        ),
        "%.1f",
        value
    )
