package com.timachado.brothermatrizes

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.account.BrotherMatrizesAccountService
import com.timachado.brothermatrizes.core.archive.ArchiveCategory
import com.timachado.brothermatrizes.core.archive.ArchiveFormat
import com.timachado.brothermatrizes.core.archive.ArchiveInventory
import com.timachado.brothermatrizes.core.archive.SafeArchiveExtractor
import com.timachado.brothermatrizes.font.ImportedFontStore
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Font-specific ZIP selection. Never presents matrix extraction or consumes matrix quotas. */
@Composable
fun FontZipImportScreen(
    archiveUri: Uri,
    onBack: () -> Unit,
    onLibraryChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inventory by remember(archiveUri) { mutableStateOf<ArchiveInventory?>(null) }
    var scanning by remember(archiveUri) { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0) }
    val selected = remember(archiveUri) { mutableStateListOf<Int>() }
    val completed = remember(archiveUri) { mutableStateListOf<Int>() }
    BackHandler(enabled = !working) { onBack() }
    DisposableEffect(inventory) {
        val staged = inventory?.sourceFile
        onDispose { staged?.delete() }
    }
    LaunchedEffect(archiveUri) {
        scanning = true
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val staged = SafeArchiveExtractor.stage(context, archiveUri)
                if (staged.format != ArchiveFormat.ZIP) {
                    staged.sourceFile.delete()
                    error("Selecione um ZIP contendo fontes TTF ou OTF.")
                }
                staged
            }
        }
        result.fold(onSuccess = { found ->
            inventory = found
            selected.clear()
            selected.addAll(found.entries.filter {
                it.category == ArchiveCategory.FONT
            }.map { it.index })
        }, onFailure = {
            status = it.message ?: "Não foi possível analisar o ZIP de fontes."
        })
        scanning = false
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextButton(onClick = onBack, enabled = !working) { Text("‹ Voltar", color = FioGold) }
        Text("Importar ZIP de fontes", color = FioText, fontWeight = FontWeight.Bold, fontSize = 21.sp)
        Text("Escolha somente fontes TTF/OTF. Suas matrizes e projetos não serão alterados.",
            color = FioTextMuted, fontSize = 12.sp)
        if (scanning) {
            CircularProgressIndicator()
            Text("Examinando ZIP…", color = FioTextMuted)
        }
        val staged = inventory
        if (staged != null) {
            val fonts = staged.entries.filter { it.category == ArchiveCategory.FONT }
            Text("${fonts.size} fonte(s) encontrada(s)", color = FioGold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        selected.clear()
                        selected.addAll(fonts.filter { it.index !in completed }.map { it.index })
                    }, enabled = !working
                ) { Text("Selecionar todas") }
                OutlinedButton(onClick = { selected.clear() }, enabled = !working) {
                    Text("Limpar")
                }
            }
            fonts.forEach { font ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(
                        checked = font.index in selected,
                        onCheckedChange = { enabled ->
                            if (enabled) selected.add(font.index) else selected.remove(font.index)
                        },
                        enabled = !working && font.index !in completed
                    )
                    Column(Modifier.weight(1f)) {
                        Text(font.name, color = FioText)
                        Text("${font.extension} • ${font.sizeBytes / 1024} KB",
                            color = FioTextMuted, fontSize = 11.sp)
                    }
                }
            }
            if (fonts.isEmpty()) Text("Este ZIP não contém fontes TTF/OTF compatíveis.", color = FioTextMuted)
            if (working) {
                CircularProgressIndicator()
                Text("Importando $progress de ${selected.size}…", color = FioGold)
            }
            Button(
                onClick = {
                    working = true
                    status = null
                    scope.launch {
                        var successCount = 0
                        var failures = 0
                        val items = selected.toList().filter { it !in completed }
                        for ((position, index) in items.withIndex()) {
                            progress = position + 1
                            val entry = fonts.firstOrNull { it.index == index } ?: continue
                            // Authorization is per font, before extraction; commit after
                            // successful new import. Never use the matrix operation.
                            val permit = BrotherMatrizesAccountService
                                .authorizeImport(isFont = true)
                            if (permit.isFailure) {
                                status = permit.exceptionOrNull()?.message
                                    ?: "Não foi possível validar a cota de fontes."
                                break
                            }
                            val key = permit.getOrThrow()
                            var created = false
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    val before = ImportedFontStore.list(context)
                                        .map { it.id }.toSet()
                                    val temp = SafeArchiveExtractor.extract(context, staged, entry)
                                    try {
                                        val imported = ImportedFontStore.importFont(
                                            context, Uri.fromFile(temp)
                                        ).getOrThrow()
                                        imported.id !in before
                                    } finally { temp.delete() }
                                }
                                created = result
                                if (created) {
                                    successCount++
                                    completed.add(index)
                                    onLibraryChanged()
                                }
                            } catch (e: Exception) {
                                failures++
                                status = "${entry.name}: ${e.message ?: "fonte inválida"}"
                            } finally {
                                val sync = BrotherMatrizesAccountService.finalizeImport(
                                    isFont = true, requestKey = key, success = created
                                )
                                if (sync.isFailure) {
                                    status = "Fonte processada, mas a cota não foi sincronizada. " +
                                        "Verifique a conexão antes de tentar novamente."
                                    break
                                }
                            }
                        }
                        val summary = "$successCount fonte(s) importada(s), $failures falha(s)."
                        status = if (status == null) summary else "$summary $status"
                        working = false
                    }
                },
                enabled = !working && selected.any { it !in completed },
                modifier = Modifier.fillMaxWidth()
            ) { Text("IMPORTAR FONTES SELECIONADAS") }
        }
        status?.let { Text(it, color = FioGold, fontSize = 12.sp) }
    }
}
