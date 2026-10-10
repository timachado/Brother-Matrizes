package com.timachado.brothermatrizes

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.archive.ArchiveCategory
import com.timachado.brothermatrizes.core.archive.ArchiveInventory
import com.timachado.brothermatrizes.core.archive.ArchiveItem
import com.timachado.brothermatrizes.core.archive.SafeArchiveExtractor
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryLoadResult
import com.timachado.brothermatrizes.core.embroidery.EmbroideryLoader
import com.timachado.brothermatrizes.core.project.ProjectStore
import com.timachado.brothermatrizes.core.project.SavedProjectSummary
import com.timachado.brothermatrizes.font.ImportedFontStore
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Only selected embroidery/font files are extracted, one at a time, after user consent. */
@Composable
fun ArchiveExtractorScreen(
    archiveUri: Uri,
    onBack: () -> Unit,
    onImported: (List<SavedProjectSummary>, EmbroideryDesign?) -> Unit,
    canImport: suspend (ArchiveCategory) -> String?,
    onImportFinished: suspend (ArchiveCategory, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inventory by remember(archiveUri) { mutableStateOf<ArchiveInventory?>(null) }
    var error by remember(archiveUri) { mutableStateOf<String?>(null) }
    var scanning by remember(archiveUri) { mutableStateOf(true) }
    var importing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var password by remember { mutableStateOf("") }
    val selected = remember(archiveUri) { mutableStateListOf<Int>() }
    var report by remember { mutableStateOf("") }
    var importedItems by remember { mutableStateOf(emptySet<Int>()) }
    BackHandler(enabled = !importing) { onBack() }
    DisposableEffect(inventory) {
        onDispose { inventory?.sourceFile?.delete() }
    }
    LaunchedEffect(archiveUri) {
        scanning = true
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching { SafeArchiveExtractor.stage(context, archiveUri) }
        }
        result.fold(
            onSuccess = { staged ->
                if (isActive) {
                    inventory = staged
                    selected.clear()
                    selected.addAll(staged.entries.map { it.index })
                } else staged.sourceFile.delete()
            },
            onFailure = { error = it.message ?: "Pacote inválido ou protegido." }
        )
        scanning = false
    }
    fun external(packageName: String? = null) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(archiveUri, "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addCategory(Intent.CATEGORY_DEFAULT)
            if (packageName != null) setPackage(packageName)
        }
        runCatching {
            if (packageName == null) {
                context.startActivity(Intent.createChooser(intent, "Abrir com aplicativo externo"))
            } else {
                context.startActivity(intent)
            }
        }.onFailure {
            error = "Nenhum aplicativo compatível disponível para abrir este arquivo."
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextButton(onClick = onBack, enabled = !importing) { Text("‹ Voltar", color = FioGold) }
        Text("Extrator Inteligente", color = FioText, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text("Selecione apenas as matrizes/fontes que deseja importar. " +
            "O pacote original não será alterado.", color = FioTextMuted, fontSize = 12.sp)
        if (scanning) {
            CircularProgressIndicator()
            Text("Verificando segurança e conteúdo…", color = FioTextMuted)
        }
        error?.let { msg ->
            Text(msg, color = FioTextMuted)
            Text("Não foi possível extrair este arquivo internamente. " +
                "Deseja tentar abrir com outro aplicativo?", color = FioText)
            // Only show RAR button if installed and resolvable.
            val rarIntent = remember(archiveUri) {
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(archiveUri, "*/*")
                    setPackage("com.rarlab.rar")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            if (context.packageManager.resolveActivity(rarIntent, 0) != null) {
                OutlinedButton(onClick = { external("com.rarlab.rar") }) {
                    Text("ABRIR COM RAR", color = FioGold)
                }
            }
            OutlinedButton(onClick = { external() }) {
                Text("ESCOLHER OUTRO APLICATIVO", color = FioGold)
            }
            TextButton(onClick = onBack) { Text("CANCELAR", color = FioTextMuted) }
        }
        val archive = inventory
        if (archive != null) {
            Text(
                "${archive.format} • ${archive.entries.count { it.category == ArchiveCategory.MATRIX }} " +
                    "matriz(es) • ${archive.entries.count { it.category == ArchiveCategory.FONT }} " +
                    "fonte(s) • ${archive.incompatibleCount} arquivo(s) ignorado(s)",
                color = FioGold
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.take(128) },
                label = { Text("Senha do pacote (se necessário)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !importing,
                    onClick = {
                        selected.clear()
                        selected.addAll(archive.entries.map { it.index })
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Selecionar todas") }
                OutlinedButton(
                    enabled = !importing,
                    onClick = { selected.clear() },
                    modifier = Modifier.weight(1f)
                ) { Text("Limpar") }
            }
            archive.entries.forEach { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(
                        checked = item.index in selected,
                        onCheckedChange = { checked ->
                            if (checked) selected.add(item.index)
                            else selected.remove(item.index)
                        },
                        enabled = !importing && item.index !in importedItems
                    )
                    Column(Modifier.weight(1f)) {
                        Text(item.name, color = FioText, fontSize = 13.sp)
                        Text(
                            "${item.extension} • ${item.sizeBytes / 1024} KB" +
                                if (item.index in importedItems) " • Importado" else "",
                            color = FioTextMuted, fontSize = 11.sp
                        )
                    }
                }
            }
            if (importing) {
                CircularProgressIndicator()
                Text("Importando $progress de ${selected.size}…", color = FioGold)
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !importing && selected.isNotEmpty(),
                onClick = {
                    importing = true
                    report = ""
                    scope.launch {
                        val originals = selected.toList().filter { it !in importedItems }
                        val saved = mutableListOf<SavedProjectSummary>()
                        var first: EmbroideryDesign? = null
                        var ok = 0
                        var failed = 0
                        for ((position, index) in originals.withIndex()) {
                            progress = position + 1
                            val item = archive.entries.firstOrNull { it.index == index }
                                ?: continue
                            // Authorize one individual import before processing; no
                            // charge occurs merely for browsing/extracting the ZIP.
                            val authorization = runCatching {
                                canImport(item.category)
                            }
                            if (authorization.isFailure) {
                                report = authorization.exceptionOrNull()?.message
                                    ?: "Licenciamento indisponível."
                                failed++
                                break
                            }
                            val reservation = authorization.getOrNull()
                            var success = false
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    val secret = password.toCharArray()
                                    val temp = try {
                                        SafeArchiveExtractor.extract(context, archive, item, secret)
                                    } finally {
                                        secret.fill('\u0000')
                                    }
                                    try {
                                        if (item.category == ArchiveCategory.MATRIX) {
                                            when (val design = EmbroideryLoader.load(
                                                context.contentResolver, Uri.fromFile(temp))) {
                                                is EmbroideryLoadResult.Success -> {
                                                    val project = ProjectStore.save(context, design.design)
                                                        .getOrThrow()
                                                    project to design.design
                                                }
                                                is EmbroideryLoadResult.Error ->
                                                    error(design.userMessage)
                                            }
                                        } else {
                                            ImportedFontStore.importFont(
                                                context, Uri.fromFile(temp)).getOrThrow()
                                            null
                                        }
                                    } finally {
                                        temp.delete()
                                    }
                                }
                                result?.let { (project, design) ->
                                    saved += project
                                    if (first == null) first = design
                                }
                                success = true
                                ok++
                                importedItems = importedItems + index
                            } catch (e: Exception) {
                                failed++
                                report = "Falha em ${item.name}: ${e.message ?: "arquivo inválido"}."
                            } finally {
                                // Exactly one commit per completed import, release on failure.
                                if (reservation != null) runCatching {
                                    onImportFinished(item.category, reservation, success)
                                }.onFailure {
                                    report = "Importação concluída, mas não foi possível sincronizar " +
                                        "a cota. Não repita até recuperar a conexão."
                                }
                            }
                        }
                        if (saved.isNotEmpty()) onImported(saved, first)
                        report = "Importadas: $ok • Falhas: $failed. $report"
                        importing = false
                    }
                }
            ) { Text("IMPORTAR SELECIONADAS (${selected.count { it !in importedItems }})") }
            if (archive.entries.isEmpty()) {
                Text("Nenhum PES, DST, JEF, TTF ou OTF compatível encontrado.",
                    color = FioTextMuted)
            }
            if (report.isNotEmpty()) Text(report, color = FioGold)
        }
    }
}
