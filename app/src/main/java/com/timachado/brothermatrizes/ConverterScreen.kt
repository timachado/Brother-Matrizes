package com.timachado.brothermatrizes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioSurfaceAlt
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted

@Composable
fun ConverterScreen(
    design: EmbroideryDesign,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
    onShare: (String) -> Unit
) {
    val targets =
        MatrixConverter.supportedFormats
            .filter { it != design.format }

    var selected by remember(
        design.fileName
    ) {
        mutableStateOf(
            targets.firstOrNull() ?: "DST"
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
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

            Text(
                "Converter matriz",
                modifier =
                    Modifier.weight(1f),
                color = FioText,
                fontWeight =
                    FontWeight.Bold,
                fontSize = 19.sp
            )
        }

        Spacer(
            Modifier.height(12.dp)
        )

        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        FioSurface
                ),
            shape =
                RoundedCornerShape(22.dp)
        ) {
            Column(
                Modifier.padding(18.dp)
            ) {
                Text(
                    "Arquivo de origem",
                    color = FioTextMuted,
                    fontSize = 11.sp
                )

                Text(
                    design.fileName,
                    color = FioText,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Text(
                    design.format +
                        " • " +
                        design.stitchCount +
                        " pontos • " +
                        design.colorCount +
                        " blocos",
                    color = FioTextMuted,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(
            Modifier.height(18.dp)
        )

        Text(
            "Converter para",
            color = FioText,
            fontWeight =
                FontWeight.Bold,
            fontSize = 17.sp
        )

        Spacer(
            Modifier.height(10.dp)
        )

        targets.forEach { format ->
            val active =
                selected == format

            Card(
                onClick = {
                    selected = format
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = 5.dp
                        ),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (active) {
                                FioSurfaceAlt
                            } else {
                                FioSurface
                            }
                    ),
                shape =
                    RoundedCornerShape(18.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Column(
                        Modifier.weight(1f)
                    ) {
                        Text(
                            format,
                            color =
                                if (active) {
                                    FioGold
                                } else {
                                    FioText
                                },
                            fontWeight =
                                FontWeight.Bold,
                            fontSize = 18.sp
                        )

                        Text(
                            formatDescription(
                                format
                            ),
                            color =
                                FioTextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Text(
                        if (active) "●"
                        else "○",
                        color = FioGold,
                        fontSize = 20.sp
                    )
                }
            }
        }

        Spacer(
            Modifier.weight(1f)
        )

        Text(
            "A conversão cria um novo arquivo. O original nunca é sobrescrito.",
            color = FioTextMuted,
            fontSize = 11.sp
        )

        Spacer(
            Modifier.height(12.dp)
        )

        Button(
            onClick = {
                onSave(selected)
            },
            modifier =
                Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        FioGold,
                    contentColor =
                        FioBackground
                )
        ) {
            Text(
                "Converter e salvar",
                fontWeight =
                    FontWeight.Bold
            )
        }

        Spacer(
            Modifier.height(8.dp)
        )

        OutlinedButton(
            onClick = {
                onShare(selected)
            },
            modifier =
                Modifier.fillMaxWidth()
        ) {
            Text(
                "Converter e compartilhar"
            )
        }

        Spacer(
            Modifier.height(22.dp)
        )
    }
}

private fun formatDescription(
    format: String
): String =
    when (format) {
        "DST" ->
            "Tajima • amplamente compatível"
        "PES" ->
            "Brother • Baby Lock"
        "JEF" ->
            "Janome • Elna"
        else ->
            "Formato de bordado"
    }
