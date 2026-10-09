package com.timachado.brothermatrizes

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.project.SavedProjectSummary
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted

/**
 * Telegram offers its own global search deep link, but Android cannot read a
 * user's private groups merely because the Telegram app is installed.
 * Search is handed off to the authorized Telegram client. User-selected
 * files are imported by Brother's existing validated SAF matrix loader.
 * Full in-app group message search requires a separate TDLib user session.
 */
@Composable
fun TelegramMatrixSearchScreen(
    projects: List<SavedProjectSummary>,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onOpenSavedProject: (SavedProjectSummary) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val matching = remember(projects, query) {
        projects.filter {
            it.title.contains(query.trim(), ignoreCase = true) ||
                it.fileName.contains(query.trim(), ignoreCase = true)
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("‹ Voltar", color = FioGold) }
        }
        Text(
            "Buscar Matrizes",
            color = FioText,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
        Text(
            "Telegram + matrizes salvas no Brother Matrizes",
            color = FioTextMuted,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(100) },
            label = { Text("Nome da matriz ou palavra-chave") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                // Telegram Android supports tg://search?query= (official
                // app's shortcuts.xml). No clipboard or extra manual paste.
                val term = query.trim()
                val uri = Uri.parse(
                    if (term.isBlank()) "tg://search"
                    else "tg://search?query=${Uri.encode(term)}"
                )
                val intent = Intent(Intent.ACTION_VIEW, uri)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                val opened = runCatching {
                    context.startActivity(intent)
                }.isSuccess
                status = if (opened) {
                    if (term.isBlank()) "Pesquisa do Telegram aberta."
                    else "Pesquisa '$term' enviada ao Telegram. Selecione uma matriz e compartilhe com Brother Matrizes."
                } else {
                    "Não foi possível abrir o Telegram. Verifique se o aplicativo está instalado e atualizado."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Pesquisar no Telegram")
        }
        OutlinedButton(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Importar matriz do Telegram / Arquivos")
        }
        Text(
            "A pesquisa em grupos e canais privados ocorre no Telegram, usando sua sessão. " +
                "Para importar, compartilhe um arquivo PES, DST ou JEF com o Brother Matrizes, " +
                "ou selecione-o em Arquivos. Não acessamos suas conversas sem autorização.",
            color = FioTextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(vertical = 9.dp)
        )
        status?.let {
            Text(it, color = FioGold, modifier = Modifier.padding(bottom = 8.dp))
        }
        Text(
            "Matrizes já salvas (${matching.size})",
            color = FioText,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 10.dp)
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(matching, key = { it.id }) { project ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = FioSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(project.title, color = FioText)
                            Text(
                                "${project.format.uppercase()} • ${project.stitchCount} pontos",
                                color = FioTextMuted, fontSize = 11.sp
                            )
                        }
                        TextButton(onClick = { onOpenSavedProject(project) }) {
                            Text("Abrir", color = FioGold)
                        }
                    }
                }
            }
            if (matching.isEmpty()) item {
                Text(
                    "Nenhuma matriz salva corresponde à pesquisa. " +
                        "Use o Telegram para localizar novos arquivos.",
                    color = FioTextMuted
                )
            }
        }
    }
}
