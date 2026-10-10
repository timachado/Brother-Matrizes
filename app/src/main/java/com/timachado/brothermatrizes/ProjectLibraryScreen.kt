package com.timachado.brothermatrizes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.library.SentMatrixRecord
import com.timachado.brothermatrizes.core.project.SavedProjectSummary
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class LibrarySection(
    val label: String
) {
    MATRICES(
        "Matrizes"
    ),
    RECENT(
        "Recentes"
    ),
    FAVORITES(
        "Favoritos"
    ),
    SENT(
        "Enviados"
    )
}

@Composable
fun ProjectLibraryScreen(
    projects: List<SavedProjectSummary>,
    favoriteProjectIds: Set<String>,
    recentProjectIds: List<String>,
    sentMatrices: List<SentMatrixRecord>,
    onBack: () -> Unit,
    onOpen: (SavedProjectSummary) -> Unit,
    onTransfer: (SavedProjectSummary) -> Unit,
    onToggleFavorite:
        (SavedProjectSummary) -> Unit,
    onDelete: (SavedProjectSummary) -> Unit,
    onDeleteSent: (SentMatrixRecord) -> Unit,
    onClearSent: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onFonts: () -> Unit,
    onSearchMatrices: () -> Unit
) {
    var section by remember {
        mutableStateOf(
            LibrarySection.MATRICES
        )
    }
    val context = LocalContext.current
    var showRarOptions by remember { mutableStateOf(false) }
    var rarMessage by remember { mutableStateOf<String?>(null) }
    val rarArchivePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val preferred = RarExternalOpener.installed(context)
            val result = RarExternalOpener.handoff(context, uri, preferred)
            rarMessage = if (result.isSuccess) {
                "Extraia o pacote no aplicativo escolhido e volte para abrir a matriz."
            } else {
                "Não foi possível abrir o pacote. Escolha um descompactador instalado."
            }
        }
    }
    if (showRarOptions) {
        AlertDialog(
            onDismissRequest = { showRarOptions = false },
            title = { Text("Abrir com RAR (WinRAR)") },
            text = { Text(
                "Use um descompactador externo para ZIP, RAR ou 7Z. " +
                    "Depois volte ao Brother Matrizes para importar PES, DST ou JEF."
            ) },
            confirmButton = {
                TextButton(onClick = {
                    showRarOptions = false
                    if (RarExternalOpener.installed(context)) {
                        val opened = RarExternalOpener.launch(context)
                        if (opened.isFailure) rarMessage = "Não foi possível abrir o RAR."
                    } else {
                        RarExternalOpener.install(context)
                    }
                }) {
                    Text(
                        if (RarExternalOpener.installed(context)) "ABRIR RAR"
                        else "INSTALAR RAR", color = FioGold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRarOptions = false
                    rarArchivePicker.launch(arrayOf(
                        "application/zip", "application/vnd.rar",
                        "application/x-rar-compressed",
                        "application/x-7z-compressed", "application/octet-stream"
                    ))
                }) { Text("ESCOLHER ARQUIVO", color = FioText) }
            }
        )
    }

    val recentProjects =
        recentProjectIds
            .mapNotNull {
                    id ->
                projects
                    .firstOrNull {
                        it.id ==
                            id
                    }
            }

    val favoriteProjects =
        projects.filter {
            it.id in
                favoriteProjectIds
        }

    val visibleProjects =
        when (
            section
        ) {
            LibrarySection.MATRICES ->
                projects

            LibrarySection.RECENT ->
                recentProjects

            LibrarySection.FAVORITES ->
                favoriteProjects

            LibrarySection.SENT ->
                emptyList()
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
                        6.dp
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
                    "Biblioteca",
                    color =
                        FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        20.sp
                )

                Text(
                    projects.size
                        .toString() +
                        " matriz(es) • " +
                        favoriteProjects.size +
                        " favorito(s)",
                    color =
                        FioTextMuted,
                    fontSize =
                        10.sp
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(
                    rememberScrollState()
                )
                .padding(
                    top =
                        4.dp,
                    bottom =
                        8.dp
                ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {
            LibrarySection
                .entries
                .forEach {
                        option ->
                    OutlinedButton(
                        onClick = {
                            section =
                                option
                        }
                    ) {
                        Text(
                            if (
                                section ==
                                    option
                            ) {
                                "● " +
                                    option.label
                            } else {
                                option.label
                            },
                            color =
                                if (
                                    section ==
                                        option
                                ) {
                                    FioGold
                                } else {
                                    FioText
                                }
                        )
                    }
                }
        }

        Row(
            Modifier
                .fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {
            OutlinedButton(
                onClick =
                    onFonts,
                modifier =
                    Modifier.weight(
                        1f
                    )
            ) {
                Text(
                    "Fontes salvas"
                )
            }

            OutlinedButton(
                onClick =
                    onBackup,
                modifier =
                    Modifier.weight(
                        1f
                    )
            ) {
                Text(
                    "Backup completo",
                    fontSize = 10.sp
                )
            }

            Button(
                onClick =
                    onRestore,
                modifier =
                    Modifier.weight(
                        1f
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
                    "Restaurar"
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { showRarOptions = true },
                modifier = Modifier.weight(1f)
            ) {
                Text("ABRIR COM RAR (WINRAR)", color = FioGold, fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = {
                    rarArchivePicker.launch(arrayOf(
                        "application/zip", "application/vnd.rar",
                        "application/x-rar-compressed",
                        "application/x-7z-compressed", "application/octet-stream"
                    ))
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Enviar pacote ao RAR", color = FioText, fontSize = 11.sp)
            }
        }
        rarMessage?.let {
            Text(it, color = FioTextMuted, fontSize = 11.sp)
        }

        OutlinedButton(
            onClick = onSearchMatrices,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Buscar Matrizes no Telegram", color = FioGold)
        }

        Text(
            "Matrizes e fontes permanecem no aparelho. Recentes, favoritos e envios ajudam a reencontrar rapidamente o que você usou.",
            color =
                FioTextMuted,
            fontSize =
                9.sp,
            modifier =
                Modifier.padding(
                    top =
                        7.dp,
                    bottom =
                        5.dp
                )
        )

        if (
            section ==
                LibrarySection.SENT
        ) {
            SentMatricesList(
                sentMatrices =
                    sentMatrices,
                onDelete = {
                        record ->
                    onDeleteSent(
                        record
                    )
                },
                onClear = {
                    onClearSent()
                }
            )

            return
        }

        if (
            visibleProjects
                .isEmpty()
        ) {
            EmptyLibrarySection(
                section =
                    section
            )

            return
        }

        LazyColumn(
            modifier =
                Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top =
                        12.dp,
                    bottom =
                        30.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp
                )
        ) {
            items(
                visibleProjects,
                key = {
                    it.id
                }
            ) {
                    project ->
                ProjectLibraryCard(
                    project =
                        project,
                    favorite =
                        project.id in
                            favoriteProjectIds,
                    onOpen = {
                        onOpen(
                            project
                        )
                    },
                    onTransfer = {
                        onTransfer(
                            project
                        )
                    },
                    onToggleFavorite = {
                        onToggleFavorite(
                            project
                        )
                    },
                    onDelete = {
                        onDelete(
                            project
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ProjectLibraryCard(
    project: SavedProjectSummary,
    favorite: Boolean,
    onOpen: () -> Unit,
    onTransfer: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
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
                16.dp
            )
        ) {
            Row(
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
                        project.title,
                        color =
                            FioText,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize =
                            17.sp
                    )

                    Text(
                        project.format +
                            " • " +
                            oneDecimalProject(
                                project.widthMm
                            ) +
                            " × " +
                            oneDecimalProject(
                                project.heightMm
                            ) +
                            " mm",
                        color =
                            FioTextMuted,
                        fontSize =
                            11.sp
                    )
                }

                TextButton(
                    onClick =
                        onToggleFavorite
                ) {
                    Text(
                        if (
                            favorite
                        ) {
                            "★ Favorito"
                        } else {
                            "☆ Favoritar"
                        },
                        color =
                            if (
                                favorite
                            ) {
                                FioGold
                            } else {
                                FioTextMuted
                            }
                    )
                }
            }

            Text(
                project.stitchCount
                    .toString() +
                    " pontos • " +
                    project.colorCount +
                    " bloco(s) • " +
                    formattedDate(
                        project
                            .updatedAtMillis
                    ),
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

            Row(
                horizontalArrangement =
                    Arrangement
                        .spacedBy(
                            8.dp
                        )
            ) {
                Button(
                    onClick =
                        onOpen,
                    modifier =
                        Modifier.weight(
                            1f
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
                        "Abrir",
                        fontWeight =
                            FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick =
                        onTransfer,
                    modifier =
                        Modifier.weight(
                            1f
                        )
                ) {
                    Text(
                        "⇧ Máquina"
                    )
                }
            }

            TextButton(
                onClick =
                    onDelete,
                modifier =
                    Modifier
                        .align(
                            Alignment.End
                        )
            ) {
                Text(
                    "Excluir",
                    color =
                        Color(
                            0xFFFF9F9A
                        )
                )
            }
        }
    }
}

@Composable
private fun SentMatricesList(
    sentMatrices:
        List<SentMatrixRecord>,
    onDelete:
        (SentMatrixRecord) -> Unit,
    onClear:
        () -> Unit
) {
    if (
        sentMatrices
            .isEmpty()
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        top =
                            24.dp
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
            Text(
                "Nenhum arquivo entregue à máquina ainda.",
                modifier =
                    Modifier.padding(
                        22.dp
                    ),
                color =
                    FioTextMuted,
                fontSize =
                    11.sp
            )
        }

        return
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    top =
                        6.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Column(
            modifier =
                Modifier.weight(
                    1f
                )
        ) {
            Text(
                "Envios recentes",
                color =
                    FioText,
                fontWeight =
                    FontWeight.SemiBold,
                fontSize =
                    12.sp
            )

            Text(
                "Remover daqui não apaga a matriz nem o arquivo do pendrive.",
                color =
                    FioTextMuted,
                fontSize =
                    9.sp
            )
        }

        TextButton(
            onClick =
                onClear
        ) {
            Text(
                "Limpar recentes",
                color =
                    Color(
                        0xFFFF9F9A
                    ),
                fontSize =
                    10.sp
            )
        }
    }

    LazyColumn(
        modifier =
            Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                top =
                    12.dp,
                bottom =
                    30.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                10.dp
            )
    ) {
        items(
            sentMatrices,
            key = {
                it.id
            }
        ) {
                record ->
            Card(
                colors =
                    CardDefaults
                        .cardColors(
                            containerColor =
                                FioSurface
                        ),
                shape =
                    RoundedCornerShape(
                        18.dp
                    )
            ) {
                Column(
                    Modifier.padding(
                        14.dp
                    )
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Text(
                            record.fileName,
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                            color =
                                FioText,
                            fontWeight =
                                FontWeight.SemiBold,
                            fontSize =
                                13.sp
                        )

                        TextButton(
                            onClick = {
                                onDelete(
                                    record
                                )
                            }
                        ) {
                            Text(
                                "✕",
                                color =
                                    Color(
                                        0xFFFF9F9A
                                    ),
                                fontWeight =
                                    FontWeight.Bold,
                                fontSize =
                                    16.sp
                            )
                        }
                    }

                    Text(
                        record.format +
                            " • " +
                            record.method,
                        color =
                            FioGold,
                        fontSize =
                            10.sp
                    )

                    Text(
                        formattedDate(
                            record.sentAtMillis
                        ),
                        color =
                            FioTextMuted,
                        fontSize =
                            9.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrarySection(
    section: LibrarySection
) {
    val message =
        when (
            section
        ) {
            LibrarySection.MATRICES ->
                "Nenhuma matriz salva ainda. Abra ou crie uma matriz e toque em “Salvar em Minhas Matrizes”."

            LibrarySection.RECENT ->
                "Nenhuma matriz salva foi aberta recentemente."

            LibrarySection.FAVORITES ->
                "Nenhuma matriz favorita ainda. Toque em ☆ Favoritar para guardar as mais usadas."

            LibrarySection.SENT ->
                "Nenhum arquivo enviado ainda."
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    top =
                        24.dp
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
        Text(
            message,
            modifier =
                Modifier.padding(
                    22.dp
                ),
            color =
                FioTextMuted,
            fontSize =
                11.sp
        )
    }
}

private fun formattedDate(
    millis: Long
): String =
    DateFormat
        .getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.SHORT,
            Locale.getDefault()
        )
        .format(
            Date(
                millis
            )
        )

private fun oneDecimalProject(
    value: Float
): String =
    String.format(
        Locale.forLanguageTag(
            "pt-BR"
        ),
        "%.1f",
        value
    )
