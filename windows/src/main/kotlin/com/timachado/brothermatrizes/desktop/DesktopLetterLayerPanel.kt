package com.timachado.brothermatrizes.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Inspector de camadas editáveis somente de letras Windows.
 * Não altera ordem de costura, apaga objetos nem finge camadas de DST/PES/JEF.
 * A edição só é efetivada após regeneração e validação do motor oficial.
 */
@Composable
internal fun DesktopLetterLayerPanel(
    layout: DesktopLetterLayout,
    selectedIndex: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onNudge: (Int, Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Camadas individuais de letras",
            color = DesktopBrandColors.gold,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "Objetos desta composição Windows. Clique para selecionar uma letra.",
            color = DesktopBrandColors.muted,
            fontSize = 11.sp
        )
        layout.editableIndices.forEach { index ->
            val letter = layout.objects[index]
            TextButton(
                enabled = enabled,
                onClick = { onSelect(index) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    (if (index == selectedIndex) "● " else "") +
                        "Camada ${index + 1} — ${letter.character} " +
                        "(X ${letter.moveXmm} mm, Y ${letter.moveYmm} mm)",
                    fontSize = 11.sp
                )
            }
        }
        if (selectedIndex in layout.editableIndices) {
            Text(
                "Mover camada selecionada em passos de 0,5 mm",
                fontSize = 11.sp, color = DesktopBrandColors.muted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(enabled = enabled, onClick = { onNudge(-5, 0) }) {
                    Text("Esquerda", fontSize = 11.sp)
                }
                TextButton(enabled = enabled, onClick = { onNudge(5, 0) }) {
                    Text("Direita", fontSize = 11.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(enabled = enabled, onClick = { onNudge(0, -5) }) {
                    Text("Cima", fontSize = 11.sp)
                }
                TextButton(enabled = enabled, onClick = { onNudge(0, 5) }) {
                    Text("Baixo", fontSize = 11.sp)
                }
            }
        }
        Text(
            "Ajustes entram no histórico de letras. Para desfazer/refazer, " +
                "use os controles da área Criar nome. " +
                "O FLP deve ser salvo com o auxiliar .bmlayers para futura reedição.",
            color = DesktopBrandColors.muted, fontSize = 10.sp
        )
    }
}
