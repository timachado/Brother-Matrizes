package com.timachado.brothermatrizes.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.embroidery.VectorArtwork

/**
 * Navegador de caminhos SVG com paginação, seleção e microajuste.
 *
 * Não adiciona visibilidade/ordenação persistente nem finge camadas
 * separadas de bordados externos já achatados. A prévia e o histórico
 * existentes continuam sendo a fonte de verdade da edição.
 */
@Composable
internal fun DesktopSvgLayerPanel(
    artwork: VectorArtwork,
    selectedIndex: Int?,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onNudge: (Int, Int) -> Unit
) {
    val pageSize = 8
    val totalPages = (artwork.paths.size + pageSize - 1) / pageSize
    var page by remember(artwork.paths.size) { mutableStateOf(0) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex != null && selectedIndex in artwork.paths.indices) {
            page = selectedIndex / pageSize
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            "Objetos vetoriais — caminhos SVG",
            color = DesktopBrandColors.gold,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "${artwork.paths.size} caminhos. Selecione um para ajustar sem sobrescrever o SVG.",
            color = DesktopBrandColors.muted,
            fontSize = 11.sp
        )
        val firstIndex = page * pageSize
        artwork.paths.drop(firstIndex).take(pageSize).forEachIndexed { position, path ->
            val index = firstIndex + position
            TextButton(
                enabled = enabled,
                onClick = { onSelect(index) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    (if (index == selectedIndex) "● " else "") +
                        "Caminho ${index + 1} · ${path.points.size} nós" +
                        (if (path.closed) " · fechado" else ""),
                    fontSize = 11.sp
                )
            }
        }
        if (totalPages > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    enabled = enabled && page > 0,
                    onClick = { page-- }
                ) { Text("Anterior", fontSize = 11.sp) }
                Text(
                    "${page + 1}/$totalPages",
                    color = DesktopBrandColors.muted, fontSize = 11.sp
                )
                TextButton(
                    enabled = enabled && page < totalPages - 1,
                    onClick = { page++ }
                ) { Text("Próxima", fontSize = 11.sp) }
            }
        }
        if (selectedIndex != null && selectedIndex in artwork.paths.indices) {
            Text(
                "Mover caminho ${selectedIndex + 1} por 1 unidade vetorial",
                color = DesktopBrandColors.muted, fontSize = 11.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(enabled = enabled, onClick = { onNudge(-1, 0) }) {
                    Text("Esquerda", fontSize = 11.sp)
                }
                TextButton(enabled = enabled, onClick = { onNudge(1, 0) }) {
                    Text("Direita", fontSize = 11.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(enabled = enabled, onClick = { onNudge(0, -1) }) {
                    Text("Cima", fontSize = 11.sp)
                }
                TextButton(enabled = enabled, onClick = { onNudge(0, 1) }) {
                    Text("Baixo", fontSize = 11.sp)
                }
            }
        }
        Text(
            "Unidade SVG não significa 1 mm na bordadeira. " +
                "Movimentos entram no histórico Desfazer/Refazer SVG; " +
                "para persistir, salve um novo FLP com .bmvector.",
            color = DesktopBrandColors.muted, fontSize = 10.sp
        )
    }
}
