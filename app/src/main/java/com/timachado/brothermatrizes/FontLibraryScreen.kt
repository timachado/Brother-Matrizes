package com.timachado.brothermatrizes

import android.widget.TextView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.timachado.brothermatrizes.core.embroidery.EmbroideryFontPreset
import com.timachado.brothermatrizes.core.embroidery.SatinUnderlayMode
import com.timachado.brothermatrizes.core.embroidery.TextMatrixGenerator
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.font.ImportedFont
import com.timachado.brothermatrizes.font.ImportedFontStore
import com.timachado.brothermatrizes.font.FontArchiveImporter
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioSurfaceAlt
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun FontLibraryScreen(
    onBack: () -> Unit,
    onOpenArchive: (android.net.Uri) -> Unit
) {
    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    var importedFonts by remember {
        mutableStateOf(
            ImportedFontStore
                .list(context)
        )
    }

    var statusMessage by remember {
        mutableStateOf<String?>(
            null
        )
    }

    var browserOpen by remember { mutableStateOf(false) }

    val zipPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onOpenArchive(uri)
    }

    if (browserOpen) {
        DaFontBrowserScreen(
            onBack = {
                importedFonts = ImportedFontStore.list(context)
                browserOpen = false
            },
            onArchiveReady = { uri ->
                browserOpen = false
                onOpenArchive(uri)
            }
        )
        return
    }

    val fontPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts
                .OpenDocument()
        ) { uri ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }

            scope.launch {
                val result =
                    withContext(
                        Dispatchers.IO
                    ) {
                        ImportedFontStore
                            .importFont(
                                context,
                                uri
                            )
                    }

                result.fold(
                    onSuccess = {
                            font ->
                        val alreadySaved =
                            importedFonts.any {
                                it.id ==
                                    font.id
                            }

                        importedFonts =
                            ImportedFontStore
                                .list(context)

                        statusMessage =
                            if (
                                alreadySaved
                            ) {
                                font.displayName +
                                    " já estava salva neste aparelho."
                            } else {
                                font.displayName +
                                    " importada e salva neste aparelho."
                            }
                    },
                    onFailure = {
                            error ->
                        statusMessage =
                            error.message
                                ?: "Não foi possível importar esta fonte."
                    }
                )
            }
        }

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
                )
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
                    "Biblioteca de Fontes",
                    color = FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 19.sp
                )

                Text(
                    "Fontes Brother Matrizes + TTF/OTF deste aparelho",
                    color =
                        FioTextMuted,
                    fontSize =
                        10.sp
                )
            }
        }

        Button(
            onClick = {
                statusMessage =
                    null

                fontPicker.launch(
                    arrayOf(
                        "font/ttf",
                        "font/otf",
                        "application/font-sfnt",
                        "application/x-font-ttf",
                        "application/x-font-opentype",
                        "application/octet-stream"
                    )
                )
            },
            modifier =
                Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults
                    .buttonColors(
                        containerColor =
                            FioGold,
                        contentColor =
                            Color(0xFF111111)
                    )
        ) {
            Text(
                "+ Importar e salvar fonte TTF/OTF",
                fontWeight =
                    FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { browserOpen = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FioSurfaceAlt,
                    contentColor = FioText
                )
            ) { Text("Buscar no DaFont") }
            Button(
                onClick = {
                    zipPicker.launch(arrayOf("application/zip", "application/octet-stream"))
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FioSurfaceAlt,
                    contentColor = FioText
                )
            ) { Text("Importar ZIP") }
        }

        Text(
            "A fonte TTF/OTF fica salva somente neste aparelho e disponível em Criar Nome até você excluir. Ela não é enviada nem sincronizada com sua conta Brother Matrizes. Limite: 12 MB por fonte.",
            modifier =
                Modifier.padding(
                    top = 6.dp,
                    bottom = 8.dp
                ),
            color =
                FioTextMuted,
            fontSize =
                10.sp
        )

        statusMessage
            ?.let {
                    message ->
                Text(
                    message,
                    modifier =
                        Modifier.padding(
                            bottom =
                                8.dp
                        ),
                    color =
                        FioGold,
                    fontSize =
                        11.sp
                )
            }

        LazyColumn(
            modifier =
                Modifier.fillMaxSize(),
            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp
                )
        ) {
            item {
                Text(
                    "Minhas fontes neste aparelho",
                    color = FioText,
                    fontWeight =
                        FontWeight.Bold
                )
            }

            if (
                importedFonts
                    .isNotEmpty()
            ) {
                items(
                    items =
                        importedFonts,
                    key = {
                        it.id
                    }
                ) {
                        font ->
                    ImportedFontCard(
                        font = font,
                        onDelete = {
                            scope.launch {
                                val result =
                                    withContext(
                                        Dispatchers.IO
                                    ) {
                                        ImportedFontStore
                                            .delete(
                                                context,
                                                font
                                            )
                                    }

                                result.fold(
                                    onSuccess = {
                                        importedFonts =
                                            ImportedFontStore
                                                .list(
                                                    context
                                                )

                                        statusMessage =
                                            font.displayName +
                                                " removida."
                                    },
                                    onFailure = {
                                            statusMessage =
                                                "Não foi possível remover a fonte."
                                    }
                                )
                            }
                        }
                    )
                }
            } else {
                item {
                    Text(
                        "Nenhuma fonte TTF/OTF importada neste aparelho.",
                        color = FioTextMuted,
                        fontSize = 10.sp
                    )
                }
            }

            item {
                Spacer(
                    Modifier.height(
                        2.dp
                    )
                )

                Text(
                    "Fontes nativas do Brother Matrizes",
                    color = FioText,
                    fontWeight =
                        FontWeight.Bold
                )
            }

            items(
                EmbroideryFontPreset
                    .entries
            ) {
                    font ->
                val preview =
                    remember(font) {
                        TextMatrixGenerator
                            .generate(
                                TextMatrixOptions(
                                    text = "FIO",
                                    heightMm =
                                        13f,
                                    style =
                                        TextStitchStyle
                                            .SATIN,
                                    satinWidthMm =
                                        2.2f,
                                    satinDensityMm =
                                        0.5f,
                                    satinUnderlayMode =
                                        SatinUnderlayMode
                                            .CENTER,
                                    font =
                                        font
                                )
                            )
                            .getOrNull()
                    }

                Card(
                    colors =
                        CardDefaults
                            .cardColors(
                                containerColor =
                                    FioSurface
                            ),
                    shape =
                        RoundedCornerShape(
                            20.dp
                        )
                ) {
                    Column(
                        Modifier.padding(
                            14.dp
                        )
                    ) {
                        Text(
                            font.displayName,
                            color = FioText,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            font.category
                                .displayName +
                                " • " +
                                font.description,
                            color =
                                FioTextMuted,
                            fontSize =
                                11.sp
                        )

                        Spacer(
                            Modifier.height(
                                10.dp
                            )
                        )

                        if (
                            preview != null
                        ) {
                            EmbroideryCanvas(
                                design =
                                    preview,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(
                                            110.dp
                                        )
                                        .background(
                                            Color(
                                                0xFF071017
                                            ),
                                            RoundedCornerShape(
                                                16.dp
                                            )
                                        )
                            )

                            Spacer(
                                Modifier.height(
                                    6.dp
                                )
                            )

                            Text(
                                preview
                                    .stitchCount
                                    .toString() +
                                    " pontos na amostra",
                                color =
                                    FioTextMuted,
                                fontSize =
                                    10.sp
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "As fontes nativas usam geometrias próprias do Brother Matrizes. As TTF/OTF importadas mantêm o desenho vetorial da fonte e são convertidas em trajetórias de bordado sem substituir as famílias nativas.",
                    modifier =
                        Modifier.padding(
                            bottom =
                                24.dp
                        ),
                    color =
                        FioTextMuted,
                    fontSize =
                        10.sp
                )
            }
        }
    }
}

@Composable
private fun ImportedFontCard(
    font: ImportedFont,
    onDelete: () -> Unit
) {
    val textColor =
        FioText.toArgb()

    val previewTypeface =
        remember(
            font.id
        ) {
            ImportedFontStore
                .loadTypeface(font)
                .getOrNull()
        }

    Card(
        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        FioSurfaceAlt
                ),
        shape =
            RoundedCornerShape(
                20.dp
            )
    ) {
        Column(
            Modifier.padding(
                14.dp
            )
        ) {
            Row(
                Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.weight(1f)
                ) {
                    Text(
                        font.displayName,
                        color = FioText,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        font.extension
                            .uppercase(
                                Locale.ROOT
                            ) +
                            " • importada pelo usuário",
                        color =
                            FioTextMuted,
                        fontSize =
                            10.sp
                    )
                }

                TextButton(
                    onClick =
                        onDelete
                ) {
                    Text(
                        "Excluir",
                        color =
                            FioGold
                    )
                }
            }

            Spacer(
                Modifier.height(
                    8.dp
                )
            )

            AndroidView(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(
                            64.dp
                        ),
                factory = {
                        viewContext ->
                    TextView(
                        viewContext
                    ).apply {
                        text =
                            "Brother Matrizes"
                        textSize =
                            28f
                        setTextColor(
                            textColor
                        )
                        setPadding(
                            8,
                            0,
                            8,
                            0
                        )
                        previewTypeface
                            ?.let {
                                this.typeface =
                                    it
                            }
                    }
                },
                update = {
                        view ->
                    view.text =
                        "Brother Matrizes"

                    previewTypeface
                        ?.let {
                            view.typeface =
                                it
                        }
                }
            )

            Text(
                "Disponível em Criar Nome neste aparelho.",
                color =
                    FioTextMuted,
                fontSize =
                    10.sp
            )
        }
    }
}
