package com.timachado.brothermatrizes.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
 * Inspetor de nós com paginação para caminhos SVG já validados.
 * Coordenadas exibidas/alteradas são SVG (não mm nem pontos de máquina).
 */
@Composable
internal fun DesktopSvgNodePanel(
    artwork: VectorArtwork,
    pathIndex: Int,
    selectedNodeIndex: Int,
    enabled: Boolean,
    onSelectNode: (Int) -> Unit,
    onNodeAction: (DesktopSvgNodeAction) -> Unit
) {
    if (pathIndex !in artwork.paths.indices) return
    val path = artwork.paths[pathIndex]
    val nodes = path.points
    if (nodes.isEmpty()) return
    val pageSize = 8
    val pages = (nodes.size + pageSize - 1) / pageSize
    val currentNode = selectedNodeIndex.coerceIn(nodes.indices)
    var page by remember(pathIndex) { mutableStateOf(0) }
    LaunchedEffect(pathIndex, currentNode) {
        page = currentNode / pageSize
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            "Nós do caminho ${pathIndex + 1} (unidades SVG)",
            color = DesktopBrandColors.goldSoft,
            fontSize = 12.sp
        )
        nodes.drop(page * pageSize).take(pageSize).forEachIndexed { slot, point ->
            val index = page * pageSize + slot
            TextButton(
                enabled = enabled,
                onClick = { onSelectNode(index) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    (if (currentNode == index) "● " else "") +
                        "Nó ${index + 1}: X=${point.x}, Y=${point.y}",
                    fontSize = 11.sp
                )
            }
        }
        if (pages > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(enabled = enabled && page > 0, onClick = { page-- }) {
                    Text("Nós anteriores", fontSize = 11.sp)
                }
                Text("${page + 1}/$pages", color = DesktopBrandColors.muted, fontSize = 11.sp)
                TextButton(enabled = enabled && page < pages - 1, onClick = { page++ }) {
                    Text("Próximos nós", fontSize = 11.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(enabled = enabled, onClick = {
                onNodeAction(DesktopSvgNodeAction.LEFT)
            }) { Text("Nó ←", fontSize = 11.sp) }
            TextButton(enabled = enabled, onClick = {
                onNodeAction(DesktopSvgNodeAction.RIGHT)
            }) { Text("Nó →", fontSize = 11.sp) }
            TextButton(enabled = enabled, onClick = {
                onNodeAction(DesktopSvgNodeAction.UP)
            }) { Text("Nó ↑", fontSize = 11.sp) }
            TextButton(enabled = enabled, onClick = {
                onNodeAction(DesktopSvgNodeAction.DOWN)
            }) { Text("Nó ↓", fontSize = 11.sp) }
        }
        val mayInsert = nodes.size < DesktopSvgImport.MAX_POINTS_PER_PATH &&
            artwork.paths.sumOf { it.points.size } < DesktopSvgImport.MAX_POINTS &&
            (path.closed || currentNode < nodes.lastIndex)
        val mayDelete = nodes.size > if (path.closed) 3 else 2
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(enabled = enabled && mayInsert, onClick = {
                onNodeAction(DesktopSvgNodeAction.INSERT_AFTER)
            }) { Text("Inserir ponto médio", fontSize = 11.sp) }
            TextButton(enabled = enabled && mayDelete, onClick = {
                onNodeAction(DesktopSvgNodeAction.DELETE)
            }) { Text("Remover nó", fontSize = 11.sp) }
        }
        Text(
            "A edição de nós usa passos de 1 unidade SVG. " +
                "Inserir cria um nó no meio do próximo segmento; " +
                "o original permanece intacto, com Desfazer/Refazer SVG.",
            color = DesktopBrandColors.muted, fontSize = 10.sp
        )
    }
}
