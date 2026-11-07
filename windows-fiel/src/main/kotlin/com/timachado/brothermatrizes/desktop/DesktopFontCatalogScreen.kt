package com.timachado.brothermatrizes.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.timachado.brothermatrizes.desktop.platform.DesktopReadOnlyFontCatalog
import com.timachado.brothermatrizes.desktop.platform.DesktopReadOnlyFontAnalyzer
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurfaceAlt
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.withContext

/**
 * Windows Fonts page for offline QA. Built-in fonts are the actual Android
 * embroidery presets. Importing TTF/OTF remains commercial-gated and is not
 * connected here, until verified Google + server reservation is homologated.
 */
@Composable
internal fun DesktopFontCatalogScreen(
    onBack: () -> Unit,
    onCreateName: () -> Unit
) {
    val reader = remember { DesktopReadOnlyFontCatalog.windowsDefault() }
    var snapshot by remember {
        mutableStateOf<DesktopReadOnlyFontCatalog.Snapshot?>(null)
    }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysis by remember {
        mutableStateOf<DesktopReadOnlyFontAnalyzer.Report?>(null)
    }
    var analysisError by remember { mutableStateOf<String?>(null) }

    fun analyzeExternalFontReadOnly() {
        if (isAnalyzing) return
        val chooser = JFileChooser().apply {
            dialogTitle = "Analisar TTF/OTF sem importar fonte (QA)"
            fileSelectionMode = JFileChooser.FILES_ONLY
            isAcceptAllFileFilterUsed = false
            fileFilter = FileNameExtensionFilter("Fontes TTF/OTF", "ttf", "otf")
        }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
        val selected = chooser.selectedFile.toPath()
        isAnalyzing = true
        analysis = null
        analysisError = null
        scope.launch {
            try {
                analysis = withContext(Dispatchers.IO) {
                    DesktopReadOnlyFontAnalyzer.inspect(selected)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                analysisError =
                    "Fonte recusada ou incompatível. Verifique o formato TTF/OTF " +
                        "e o limite de 12 MB. Nenhum arquivo foi importado."
            } finally {
                isAnalyzing = false
            }
        }
    }

    LaunchedEffect(reader) {
        val result = withContext(Dispatchers.IO) { runCatching { reader.snapshot() } }
        result.onSuccess {
            snapshot = it
            failed = false
        }.onFailure {
            failed = true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            TextButton(onClick = onBack) {
                Text("‹ Voltar", color = FioGold)
            }
            Text(
                "Biblioteca de Fontes",
                color = FioText,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Fontes Brother Matrizes + TTF/OTF deste computador",
                color = FioTextMuted
            )
        }
        item {
            Button(onClick = onCreateName, modifier = Modifier.fillMaxWidth()) {
                Text("Criar Nome com fontes Brother Matrizes")
            }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = FioSurfaceAlt),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Importação TTF/OTF em homologação",
                        color = FioGold,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "A importação de novas fontes exige login Google e " +
                            "reserva de cota confirmada pelo servidor. " +
                            "Essa operação permanece indisponível nesta versão Windows de QA.",
                        color = FioText
                    )
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { analyzeExternalFontReadOnly() },
                enabled = !isAnalyzing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isAnalyzing)
                    "Analisando contornos…" else "Analisar arquivo TTF/OTF (sem importar)")
            }
            Text(
                "Diagnóstico de leitura e glifos pelo Skia; não gera pontadas, " +
                    "não registra fontes e não utiliza cotas comerciais.",
                color = FioTextMuted
            )
        }
        if (analysisError != null) {
            item { Text(checkNotNull(analysisError), color = FioGold) }
        }
        if (analysis != null) {
            val report = checkNotNull(analysis)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = FioSurfaceAlt)
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Análise local — nenhuma importação",
                            color = FioGold,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Família: " + report.familyName, color = FioText)
                        Text("Arquivo: " + report.sourceName, color = FioTextMuted)
                        Text(
                            "Formato: " + report.format +
                                " · " + (report.byteSize / 1024) + " KB",
                            color = FioTextMuted
                        )
                        Text(
                            "Glifos testados: " + report.testedGlyphs +
                                " · contornos visíveis: " + report.visibleOutlines +
                                " · ausentes: " + report.missingGlyphs,
                            color = FioTextMuted
                        )
                        Text(
                            "Leitura de contornos não significa que a geração Satin " +
                                "ou a licença comercial estejam homologadas.",
                            color = FioTextMuted
                        )
                    }
                }
            }
        }
        if (failed) {
            item {
                Text(
                    "Não foi possível ler a biblioteca local. " +
                        "Nenhuma fonte foi importada ou modificada.",
                    color = FioGold
                )
            }
        } else if (snapshot == null) {
            item { Text("Carregando catálogo local…", color = FioTextMuted) }
        } else {
            val loaded = snapshot!!
            item {
                Text(
                    "Fontes de bordado incluídas (${loaded.builtIn.size})",
                    color = FioGold,
                    fontWeight = FontWeight.Bold
                )
            }
            items(loaded.builtIn, key = { "preset_" + it.key }) { font ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = FioSurfaceAlt),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(font.displayName, color = FioText, fontWeight = FontWeight.Bold)
                        Text(font.category + " · " + font.description, color = FioTextMuted)
                    }
                }
            }
            item {
                Text(
                    "Fontes TTF/OTF locais (${loaded.localFontMetadata.size})",
                    color = FioGold,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Itens encontrados no armazenamento local. " +
                        "A geração de pontadas com fontes externas ainda não está homologada.",
                    color = FioTextMuted
                )
            }
            if (loaded.localFontMetadata.isEmpty()) {
                item { Text("Nenhuma fonte externa registrada.", color = FioTextMuted) }
            } else {
                items(loaded.localFontMetadata, key = { "font_" + it.id }) { font ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = FioSurfaceAlt),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(font.displayName, color = FioText, fontWeight = FontWeight.Bold)
                            Text(font.extension.uppercase(), color = FioTextMuted)
                        }
                    }
                }
            }
        }
    }
}
