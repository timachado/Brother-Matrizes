package com.timachado.brothermatrizes

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.MachineTransferValidator
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.TransferIssueLevel
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import java.util.Locale

@Composable
fun MachineTransferScreen(
    design: EmbroideryDesign,
    onBack: () -> Unit,
    onUsbOtg: (String) -> Unit,
    onWifi: (String) -> Unit
) {
    val formats =
        MatrixConverter
            .supportedFormats

    var format by remember(
        design.fileName
    ) {
        mutableStateOf(
            design.format
                .uppercase()
                .takeIf {
                    it in
                        formats
                }
                ?: formats.first()
        )
    }

    var hoop by remember(
        design.fileName
    ) {
        mutableStateOf(
            MachineTransferValidator
                .recommendedHoop(
                    design
                )
        )
    }

    val validation =
        MachineTransferValidator
            .validate(
                design =
                    design,
                format =
                    format,
                hoop =
                    hoop
            )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(
                horizontal =
                    18.dp,
                vertical =
                    6.dp
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth(),
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
                    "Enviar para a máquina",
                    color =
                        FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        20.sp
                )

                Text(
                    "Valide a matriz antes de escolher o destino.",
                    color =
                        FioTextMuted,
                    fontSize =
                        11.sp
                )
            }
        }

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        Card(
            modifier =
                Modifier
                    .fillMaxWidth(),
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
                Modifier.padding(
                    18.dp
                )
            ) {
                Text(
                    design.label
                        ?: design.fileName,
                    color =
                        FioText,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Text(
                    design.stitchCount
                        .toString() +
                        " pontos • " +
                        design.colorCount +
                        " bloco(s) • " +
                        oneDecimalTransfer(
                            design.bounds
                                .widthMm
                        ) +
                        " × " +
                        oneDecimalTransfer(
                            design.bounds
                                .heightMm
                        ) +
                        " mm",
                    color =
                        FioTextMuted,
                    fontSize =
                        11.sp
                )

                Spacer(
                    Modifier.height(
                        16.dp
                    )
                )

                Text(
                    "Formato da máquina",
                    color =
                        FioText,
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
                    formats.forEach {
                            option ->
                        val selected =
                            format ==
                                option

                        OutlinedButton(
                            onClick = {
                                format =
                                    option
                            }
                        ) {
                            Text(
                                if (
                                    selected
                                ) {
                                    "● $option"
                                } else {
                                    option
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
                    "Bastidor da máquina",
                    color =
                        FioText,
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
                            top =
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
                            OutlinedButton(
                                onClick = {
                                    hoop =
                                        option
                                }
                            ) {
                                Text(
                                    if (
                                        hoop ==
                                            option
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
                                            hoop ==
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
            }
        }

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        Card(
            modifier =
                Modifier
                    .fillMaxWidth(),
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
                Modifier.padding(
                    18.dp
                )
            ) {
                Text(
                    if (
                        validation.ready
                    ) {
                        "✓ Pronta para enviar"
                    } else {
                        "⚠ Corrija antes de enviar"
                    },
                    color =
                        if (
                            validation.ready
                        ) {
                            FioGold
                        } else {
                            FioText
                        },
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        16.sp
                )

                Text(
                    "Formato $format • bastidor " +
                        hoop.displayName,
                    modifier =
                        Modifier.padding(
                            top =
                                3.dp
                        ),
                    color =
                        FioTextMuted,
                    fontSize =
                        10.sp
                )

                if (
                    validation.issues
                        .isEmpty()
                ) {
                    Text(
                        "Pontos, dimensões, formato e área útil do bastidor passaram na validação.",
                        modifier =
                            Modifier.padding(
                                top =
                                    9.dp
                            ),
                        color =
                            FioTextMuted,
                        fontSize =
                            10.sp
                    )
                } else {
                    validation.issues
                        .forEach {
                                issue ->
                            Text(
                                (
                                    if (
                                        issue.level ==
                                            TransferIssueLevel
                                                .BLOCKING
                                    ) {
                                        "• Erro: "
                                    } else {
                                        "• Aviso: "
                                    }
                                    ) +
                                    issue.message,
                                modifier =
                                    Modifier.padding(
                                        top =
                                            7.dp
                                    ),
                                color =
                                    if (
                                        issue.level ==
                                            TransferIssueLevel
                                                .BLOCKING
                                    ) {
                                        FioText
                                    } else {
                                        FioTextMuted
                                    },
                                fontSize =
                                    10.sp
                            )
                        }
                }
            }
        }

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        Card(
            modifier =
                Modifier
                    .fillMaxWidth(),
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
                Modifier.padding(
                    18.dp
                )
            ) {
                Text(
                    "USB OTG / pendrive",
                    color =
                        FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        17.sp
                )

                Text(
                    "Conecte o pendrive ao celular. O Android abrirá o seletor de arquivos; escolha o armazenamento USB como destino.",
                    color =
                        FioTextMuted,
                    fontSize =
                        11.sp
                )

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

                Button(
                    onClick = {
                        onUsbOtg(
                            format
                        )
                    },
                    enabled =
                        validation.ready,
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
                        "Salvar no pendrive OTG",
                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }
        }

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        Card(
            modifier =
                Modifier
                    .fillMaxWidth(),
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
                Modifier.padding(
                    18.dp
                )
            ) {
                Text(
                    "Wi-Fi / app da máquina",
                    color =
                        FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        17.sp
                )

                Text(
                    "Gera o arquivo validado e abre os apps compatíveis do Android. Quando a bordadeira oferecer protocolo Wi-Fi direto suportado, ele poderá ser conectado aqui sem trocar o fluxo.",
                    color =
                        FioTextMuted,
                    fontSize =
                        11.sp
                )

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

                OutlinedButton(
                    onClick = {
                        onWifi(
                            format
                        )
                    },
                    enabled =
                        validation.ready,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                ) {
                    Text(
                        "Enviar via Wi-Fi / app"
                    )
                }
            }
        }

        Text(
            "O Brother Matrizes não altera silenciosamente o tamanho da matriz para fazê-la caber. Se a validação bloquear o envio, ajuste o desenho ou escolha um bastidor compatível.",
            modifier =
                Modifier.padding(
                    top =
                        12.dp,
                    bottom =
                        24.dp
                ),
            color =
                FioTextMuted,
            fontSize =
                9.sp
        )
    }
}

private fun oneDecimalTransfer(
    value: Float
): String =
    String.format(
        Locale.forLanguageTag(
            "pt-BR"
        ),
        "%.1f",
        value
    )
