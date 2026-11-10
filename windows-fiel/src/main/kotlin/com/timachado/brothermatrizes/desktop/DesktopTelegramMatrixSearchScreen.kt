package com.timachado.brothermatrizes.desktop

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.project.SavedProjectSummary
import com.timachado.brothermatrizes.desktop.platform.DesktopTelegramMatrixSearch
import com.timachado.brothermatrizes.desktop.platform.DesktopTelegramSearchAccess
import com.timachado.brothermatrizes.desktop.platform.DesktopTelegramSearchUnavailable
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Same Android Telegram + local saved matrices workflow, adapted to the
 * Windows Telegram protocol handler and existing FLP catalog.
 *
 * Importing selected Telegram files INTO the commercial library is NOT
 * authorized here: an official import_matrix server reservation, checked
 * persistence and finalize are still pending for the desktop file chooser.
 * The local file action only opens a project for review, never labels it
 * 'imported', invents a quota, or scans private chats.
 */
@Composable
internal fun DesktopTelegramMatrixSearchScreen(
    projects: List<SavedProjectSummary>,
    onBack: () -> Unit,
    onOpenLocalFile: () -> Unit,
    onImportLocalFile: (() -> Unit)? = null,
    onRequireGoogle: () -> Unit,
    onOpenSavedProject: (SavedProjectSummary) -> Unit,
    access: DesktopTelegramSearchAccess = DesktopTelegramSearchUnavailable
) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val matching = remember(projects, query) {
        projects.filter {
            it.title.contains(query.trim(), ignoreCase = true) ||
                it.fileName.contains(query.trim(), ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TextButton(onClick = onBack) { Text("‹ Voltar", color = FioGold) }
        Text("Buscar Matrizes", color = FioText,
            fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(
            "Telegram + matrizes salvas no Brother Matrizes",
            color = FioTextMuted, modifier = Modifier.padding(bottom = 12.dp)
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(100) },
            label = { Text("Nome da matriz ou palavra-chave") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = !checking,
            onClick = {
                scope.launch {
                    checking = true
                    status = null
                    try {
                        if (!access.hasVerifiedGoogleSession()) {
                            status = "Entre com Google em Minha Conta para pesquisar no Telegram."
                            onRequireGoogle()
                            return@launch
                        }
                        DesktopTelegramMatrixSearch.browse(query, access)
                        status =
                            "Pesquisa iniciada no Telegram. Selecione o arquivo desejado para abri-lo."
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        status = "Não foi possível confirmar a pesquisa no Telegram. " +
                            "Confira o aplicativo Telegram, o login e as cotas da conta."
                    } finally {
                        checking = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (checking) "Verificando cota…" else "Pesquisar no Telegram") }
        OutlinedButton(
            onClick = onOpenLocalFile, modifier = Modifier.fillMaxWidth()
        ) { Text("Abrir arquivo do Telegram / Arquivos") }
        if (onImportLocalFile != null) {
            OutlinedButton(
                onClick = onImportLocalFile,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Importar matriz PES/DST/JEF com conta e cota") }
        }
        Text(
            "A pesquisa em grupos privados ocorre no próprio Telegram. " +
                "O Windows não acessa suas mensagens. Abrir um arquivo permite visualizar " +
                "a matriz; importar para a Biblioteca exige conta verificada, " +
                "permissão comercial e cota do servidor.",
            color = FioTextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(vertical = 9.dp)
        )
        status?.let { Text(it, color = FioGold, modifier = Modifier.padding(bottom = 8.dp)) }
        Text(
            "Matrizes já salvas (${matching.size})",
            color = FioText, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 10.dp)
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(matching, key = { it.id }) { project ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = FioSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
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
