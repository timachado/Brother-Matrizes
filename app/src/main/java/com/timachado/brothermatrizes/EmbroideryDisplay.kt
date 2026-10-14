package com.timachado.brothermatrizes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted

enum class EmbroideryDisplayMode(
    val displayName: String,
    val helper: String
) {
    SOLID(
        "Sólida",
        "Linhas contínuas e limpas"
    ),
    POINTS(
        "Pontos",
        "Cada perfuração da agulha"
    ),
    REALISTIC(
        "Realista",
        "Volume e brilho de linha"
    )
}

@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun DisplaySettingsSheet(
    displayMode:
        EmbroideryDisplayMode,
    onDisplayModeChange:
        (EmbroideryDisplayMode) ->
            Unit,
    hoop:
        HoopProfile?,
    onHoopChange:
        (HoopProfile) ->
            Unit,
    showConnections:
        Boolean,
    onShowConnectionsChange:
        (Boolean) ->
            Unit,
    onDismiss:
        () ->
            Unit
) {
    ModalBottomSheet(
        onDismissRequest =
            onDismiss,
        containerColor =
            FioSurface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal =
                        18.dp,
                    vertical =
                        8.dp
                )
        ) {
            Text(
                "Configurações de Exibição",
                color =
                    FioText,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                "Altere apenas a forma de visualizar a matriz.",
                color =
                    FioTextMuted
            )

            Text(
                "Tipo de exibição",
                modifier =
                    Modifier.padding(
                        top =
                            16.dp,
                        bottom =
                            6.dp
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
                        ),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp
                    )
            ) {
                EmbroideryDisplayMode
                    .entries
                    .forEach {
                            mode ->
                        OutlinedButton(
                            onClick = {
                                onDisplayModeChange(
                                    mode
                                )
                            },
                            colors =
                                ButtonDefaults
                                    .outlinedButtonColors(
                                        contentColor =
                                            if (
                                                displayMode ==
                                                    mode
                                            ) {
                                                FioGold
                                            } else {
                                                FioText
                                            }
                                    )
                        ) {
                            Text(
                                if (
                                    displayMode ==
                                        mode
                                ) {
                                    "● " +
                                        mode.displayName
                                } else {
                                    mode.displayName
                                }
                            )
                        }
                    }
            }

            Text(
                displayMode.helper,
                color =
                    FioTextMuted,
                modifier =
                    Modifier.padding(
                        top =
                            4.dp
                    )
            )

            Text(
                "Bastidor de referência",
                modifier =
                    Modifier.padding(
                        top =
                            16.dp,
                        bottom =
                            6.dp
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
                        OutlinedButton(
                            onClick = {
                                onHoopChange(
                                    option
                                )
                            },
                            colors =
                                ButtonDefaults
                                    .outlinedButtonColors(
                                        contentColor =
                                            if (
                                                hoop ==
                                                    option
                                            ) {
                                                FioGold
                                            } else {
                                                FioText
                                            }
                                    )
                        ) {
                            Text(
                                option.displayName
                            )
                        }
                    }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            top =
                                14.dp,
                            bottom =
                                22.dp
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
                        "Traços de conexão",
                        color =
                            FioText,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    Text(
                        "Mostra deslocamentos sem costura entre trechos.",
                        color =
                            FioTextMuted
                    )
                }

                Switch(
                    checked =
                        showConnections,
                    onCheckedChange =
                        onShowConnectionsChange
                )
            }
        }
    }
}
