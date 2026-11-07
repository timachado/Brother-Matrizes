package com.timachado.brothermatrizes.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveInventory
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted

/**
 * Windows QA adaptation of Android's archive *inspection* step only.
 *
 * A list of compatible matrix/font filenames is displayed, but no item can
 * be imported without Android-equivalent per-item authorization and quotas.
 * This deliberately has no inert "Importar" action suggesting success.
 */
@Composable
internal fun DesktopArchivePreviewScreen(
    preview: DesktopArchiveInventory.Preview?,
    busy: Boolean,
    error: String?,
    onChooseZip: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack, enabled = !busy) {
            Text("‹ Voltar", color = FioGold)
        }
        Text(
            "Extrator Inteligente — análise ZIP",
            color = FioText, fontWeight = FontWeight.Bold, fontSize = 22.sp
        )
        Text(
            "Confira os arquivos do pacote antes de importar. " +
                "O original não será alterado.",
            color = FioTextMuted
        )
        Button(
            onClick = onChooseZip,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = FioGold, contentColor = FioBackground
            )
        ) {
            Text("Selecionar arquivo ZIP")
        }
        if (busy) {
            CircularProgressIndicator(color = FioGold)
            Text("Verificando metadados e limites de segurança…", color = FioTextMuted)
        }
        if (error != null) {
            Text("Pacote não analisado: $error", color = FioText)
        }
        if (preview != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = FioSurface)
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        preview.archiveName, color = FioGold,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${preview.matrixCount} matriz(es) • " +
                            "${preview.fontCount} fonte(s) • " +
                            "${preview.incompatibleCount} entrada(s) ignorada(s)",
                        color = FioTextMuted
                    )
                    Text(
                        "${preview.scannedCount} entrada(s) examinada(s); " +
                            "sem extração de arquivos.",
                        color = FioTextMuted, fontSize = 11.sp
                    )
                }
            }
            preview.entries.forEach { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = FioSurface)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.displayName, color = FioText)
                            Text(
                                item.entryName + " • " +
                                    item.extension + " • " +
                                    (item.sizeBytes / 1024L) + " KB",
                                color = FioTextMuted, fontSize = 11.sp
                            )
                        }
                        Text(
                            if (item.category == DesktopArchiveInventory.Category.MATRIX)
                                "Matriz" else "Fonte",
                            color = FioGold, fontSize = 11.sp
                        )
                    }
                }
            }
            if (preview.entries.isEmpty()) {
                Text(
                    "Nenhuma matriz PES/DST/JEF ou fonte TTF/OTF compatível " +
                        "foi identificada.",
                    color = FioTextMuted
                )
            }
        }
        Text(
            "Leitura somente de metadados. ZIP, RAR e 7Z não estão sendo " +
                "importados nesta QA. A importação de matrizes e fontes " +
                "depende de autorização individual e cotas comerciais homologadas.",
            color = FioTextMuted, fontSize = 11.sp
        )
    }
}
