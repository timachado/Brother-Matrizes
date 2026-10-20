package com.timachado.brothermatrizes

import androidx.compose.foundation.background
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
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
import com.timachado.brothermatrizes.ui.updates.BrotherUpdateHomeCard
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
    onUnavailable: (String) -> Unit,
    onCommunity: () -> Unit,
    updateRefreshRequest: Int = 0
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
        contentPadding =
            PaddingValues(
                top = 6.dp,
                bottom = 28.dp
            ),
        horizontalArrangement =
            Arrangement.spacedBy(12.dp),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_brother_premium),
                    contentDescription = "Símbolo BM, Brother Matrizes",
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Brother", style = MaterialTheme.typography.titleLarge, color = FioGold)
                    Text("Matrizes", style = MaterialTheme.typography.titleMedium, color = FioGoldSoft)
                    Text(
                        "Matrizes que transformam ideias em grandes projetos.",
                        style = MaterialTheme.typography.labelMedium,
                        color = FioTextMuted,
                        maxLines = 2
                    )
                }
                ElevatedCard(onClick = onCommunity,
                    shape = CircleShape,
                    colors = CardDefaults.elevatedCardColors(containerColor = FioSurfaceHigh)) {
                    Icon(
                        painterResource(R.drawable.ic_notification_brother),
                        contentDescription = "Novidades disponíveis na seção Início",
                        tint = FioGoldSoft,
                        modifier = Modifier.padding(12.dp).size(22.dp)
                    )
                }
            }
        }

        // Banner só aparece para uma versão publicada maior que a instalada.
        // Independente do opt-in FCM: novidades visíveis ao abrir o aplicativo.
        item(span = { GridItemSpan(maxLineSpan) }) {
            BrotherUpdateHomeCard(refreshRequest = updateRefreshRequest)
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            PremiumHeroCard()
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
                            min = 106.dp
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
                            min = 100.dp
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
                            min = 100.dp
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
                            min = 100.dp
                        ),
                iconRes =
                    R.drawable
                        .ic_ms_play_arrow_rounded,
                title = "Simulador",
                subtitle =
                    "Veja a sequência real",
                enabled = true
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
                            min = 100.dp
                        ),
                iconRes =
                    R.drawable
                        .ic_ms_edit_rounded,
                title = "Editor",
                subtitle =
                    "Mover, girar e cores",
                enabled = true
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
                            min = 100.dp
                        ),
                iconRes =
                    R.drawable
                        .ic_ms_swap_horiz_rounded,
                title = "Converter",
                subtitle =
                    "DST, PES e JEF",
                enabled = true
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
                            min = 100.dp
                        ),
                iconRes =
                    R.drawable
                        .ic_ms_upload_rounded,
                title = "Enviar",
                subtitle =
                    "OTG ou Wi-Fi/app",
                enabled = true
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

        item(span = { GridItemSpan(maxLineSpan) }) {
            ElevatedCard(
                onClick = onCommunity,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = FioSurfaceAlt),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text("Comunidade e novidades  ›", color = FioGoldSoft,
                         style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Feedbacks, sugestões e atualizações do Brother Matrizes.",
                         color = FioTextMuted, style = MaterialTheme.typography.labelMedium)
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


/** Mesma imagem do mockup aprovado, integrada ao card — não é banner externo. */
@Composable
private fun PremiumHeroCard() {
    // Usa a fotografia da arte aprovada, removendo apenas a faixa esquerda
    // que contém fragmentos do texto do mockup original.
    val fullPhoto = ImageBitmap.imageResource(R.drawable.brother_home_hero)
    val cropLeft = (fullPhoto.width * 0.35f).toInt()
    val photo = remember(fullPhoto) {
        BitmapPainter(
            image = fullPhoto,
            srcOffset = IntOffset(cropLeft, 0),
            srcSize = IntSize(fullPhoto.width - cropLeft, fullPhoto.height)
        )
    }
    Box(
        modifier = Modifier.fillMaxWidth()
            .height(158.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF091C2D))
    ) {
        Image(
            painter = photo,
            contentDescription = "Agulha de máquina bordando detalhes dourados no tecido",
            contentScale = ContentScale.Crop,
            modifier = Modifier.align(Alignment.CenterEnd)
                .fillMaxWidth(0.64f)
                .fillMaxHeight()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color(0xFF071B2C),
                    0.48f to Color(0xE6081B2C),
                    0.84f to Color(0x44081B2C),
                    1f to Color.Transparent
                )
            )
        )
        Column(
            modifier = Modifier.align(Alignment.CenterStart)
                .fillMaxWidth(0.75f).padding(start = 18.dp, end = 4.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "O que vamos\nbordar hoje?",
                color = Color.White,
                fontSize = 23.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Comece pelo nome ou escolha uma ferramenta.",
                color = Color(0xFFD7E1E9),
                fontSize = 12.sp,
                lineHeight = 17.sp
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
    val dark = FioBackground
    val foreground = if (primary) Color(0xFF091724) else if (enabled) FioText else FioTextMuted
    val surface = if (primary) FioGold else if (enabled) FioSurfaceHigh else FioSurface
    ElevatedCard(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(if (primary) 28.dp else 22.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = surface,
            disabledContainerColor = FioSurface,
            disabledContentColor = FioTextMuted
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (primary) 5.dp else 1.dp)
    ) {
        if (primary) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(
                        listOf(Color(0xFFFFDD91), Color(0xFFE2B461), Color(0xFFD59A3E))
                    ))
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(color = dark, shape = CircleShape, modifier = Modifier.size(56.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(iconRes), null, tint = FioGold,
                             modifier = Modifier.size(28.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                         color = Color(0xFF081724))
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                         color = Color(0xFF3D301B), maxLines = 3)
                }
                Text("›", fontSize = 31.sp, fontWeight = FontWeight.Medium,
                     color = Color(0xFF081724))
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (enabled) FioGold.copy(alpha = 0.14f) else FioSurfaceAlt,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = if (enabled) FioGoldSoft else FioTextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = foreground,
                         fontSize = 12.sp, lineHeight = 16.sp,
                         fontWeight = FontWeight.SemiBold, maxLines = 2,
                         overflow = TextOverflow.Ellipsis)
                    Text(subtitle, color = FioTextMuted,
                         fontSize = 10.sp, lineHeight = 13.sp,
                         maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text("›", fontSize = 20.sp, color = if (enabled) FioGoldSoft else FioTextMuted)
            }
        }
    }
}

private fun formatMm(value: Float): String =
    String.format(Locale.forLanguageTag("pt-BR"), "%.1f", value)
