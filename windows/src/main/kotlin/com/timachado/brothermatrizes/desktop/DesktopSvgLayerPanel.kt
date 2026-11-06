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
 * Não finge camadas separadas em bordados já achatados: a ordenação
 * só afeta a próxima matriz gerada a partir dos caminhos SVG da sessão.
 * A prévia e o histórico existentes continuam sendo a fonte de verdade.
 */
@Composable
internal fun DesktopSvgLayerPanel(
    artwork: VectorArtwork,
    selectedIndex: Int?,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onNudge: (Int, Int) -> Unit,
    onTransform: (DesktopSvgObjectAction) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onReorder: (Int) -> Unit,
    selectedNodeIndex: Int,
    onSelectNode: (Int) -> Unit,
    onNodeAction: (DesktopSvgNodeAction) -> Unit
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
                "Gerenciar o objeto SVG selecionado",
                color = DesktopBrandColors.muted, fontSize = 11.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    enabled = enabled && artwork.paths.size < DesktopSvgImport.MAX_PATHS,
                    onClick = onDuplicate
                ) { Text("Duplicar +10 SVG", fontSize = 11.sp) }
                TextButton(
                    enabled = enabled && artwork.paths.size > 1,
                    onClick = onDelete
                ) { Text("Excluir caminho", fontSize = 11.sp) }
            }
            Text(
                "Duplicar cria uma cópia deslocada em 10 unidades SVG " +
                    "por eixo; Excluir remove apenas da sessão (Desfazer disponível).",
                color = DesktopBrandColors.muted, fontSize = 10.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    enabled = enabled && selectedIndex > 0,
                    onClick = { onReorder(-1) }
                ) { Text("Na sequência ↑", fontSize = 11.sp) }
                TextButton(
                    enabled = enabled && selectedIndex < artwork.paths.lastIndex,
                    onClick = { onReorder(1) }
                ) { Text("Na sequência ↓", fontSize = 11.sp) }
            }
            Text(
                "Reordenar muda a sequência dos caminhos na próxima matriz gerada. " +
                    "Não altera a ordem de uma matriz já salva.",
                color = DesktopBrandColors.muted, fontSize = 10.sp
            )
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
            Text(
                "Transformações em torno do centro do caminho",
                color = DesktopBrandColors.muted, fontSize = 11.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    enabled = enabled,
                    onClick = { onTransform(DesktopSvgObjectAction.SCALE_DOWN) }
                ) { Text("Escala −10%", fontSize = 11.sp) }
                TextButton(
                    enabled = enabled,
                    onClick = { onTransform(DesktopSvgObjectAction.SCALE_UP) }
                ) { Text("Escala +10%", fontSize = 11.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    enabled = enabled,
                    onClick = { onTransform(DesktopSvgObjectAction.ROTATE_LEFT) }
                ) { Text("Girar −15°", fontSize = 11.sp) }
                TextButton(
                    enabled = enabled,
                    onClick = { onTransform(DesktopSvgObjectAction.ROTATE_RIGHT) }
                ) { Text("Girar +15°", fontSize = 11.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    enabled = enabled,
                    onClick = { onTransform(DesktopSvgObjectAction.MIRROR_HORIZONTAL) }
                ) { Text("Espelhar H", fontSize = 11.sp) }
                TextButton(
                    enabled = enabled,
                    onClick = { onTransform(DesktopSvgObjectAction.MIRROR_VERTICAL) }
                ) { Text("Espelhar V", fontSize = 11.sp) }
            }
            DesktopSvgNodePanel(
                artwork = artwork,
                pathIndex = selectedIndex,
                selectedNodeIndex = selectedNodeIndex,
                enabled = enabled,
                onSelectNode = onSelectNode,
                onNodeAction = onNodeAction
            )
        }
        Text(
            "Unidade SVG não significa 1 mm na bordadeira. " +
                "Duplicação, exclusão e transformações entram no histórico SVG; " +
                "para persistir, salve um novo FLP com .bmvector.",
            color = DesktopBrandColors.muted, fontSize = 10.sp
        )
    }
}
