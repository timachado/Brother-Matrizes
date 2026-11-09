package com.timachado.brothermatrizes.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedTextField
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
import com.timachado.brothermatrizes.desktop.platform.DesktopCommercialFontImportGate
import com.timachado.brothermatrizes.desktop.platform.DesktopReadOnlyFontCatalog
import com.timachado.brothermatrizes.desktop.platform.DesktopReadOnlyFontAnalyzer
import com.timachado.brothermatrizes.desktop.platform.DesktopExternalFontRunningPreflight
import com.timachado.brothermatrizes.desktop.platform.DesktopFontZipPreview
import com.timachado.brothermatrizes.desktop.platform.DesktopFontZipAuthorizedImporter
import com.timachado.brothermatrizes.desktop.platform.DesktopFontCatalogSearch
import com.timachado.brothermatrizes.desktop.platform.DesktopFontCatalogAccess
import com.timachado.brothermatrizes.desktop.platform.DesktopFontCatalogAccessUnavailable
import com.timachado.brothermatrizes.core.embroidery.EmbroideryFontPreset
import com.timachado.brothermatrizes.font.FontCatalogSource
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurfaceAlt
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter
import java.nio.file.Path
import kotlinx.coroutines.withContext

/**
 * Windows Fonts page for offline QA. Built-in fonts are the actual Android
 * embroidery presets. Importing TTF/OTF remains commercial-gated and is not
 * connected here, until verified Google + server reservation is homologated.
 */
@Composable
internal fun DesktopFontCatalogScreen(
    onBack: () -> Unit,
    onCreateName: () -> Unit,
    onRequireGoogle: () -> Unit,
    access: DesktopFontCatalogAccess = DesktopFontCatalogAccessUnavailable,
    importGate: DesktopCommercialFontImportGate? = null
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
    var inspectedFontFile by remember { mutableStateOf<Path?>(null) }
    var runningSample by remember { mutableStateOf("Maria") }
    var runningBusy by remember { mutableStateOf(false) }
    var runningReport by remember {
        mutableStateOf<DesktopExternalFontRunningPreflight.Report?>(null)
    }
    var runningError by remember { mutableStateOf<String?>(null) }
    var selectedForImport by remember { mutableStateOf<Path?>(null) }
    var selectedImportReport by remember {
        mutableStateOf<DesktopReadOnlyFontAnalyzer.Report?>(null)
    }
    var importBusy by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }

    /** Local validation does not reserve quotas or save font files. */
    fun chooseFontForAuthorizedImport() {
        if (importGate == null || importBusy) return
        val picker = JFileChooser().apply {
            dialogTitle = "Selecionar TTF/OTF para importar — exige cota real"
            fileSelectionMode = JFileChooser.FILES_ONLY
            isAcceptAllFileFilterUsed = false
            fileFilter = FileNameExtensionFilter("Fontes TTF/OTF", "ttf", "otf")
        }
        if (picker.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
        val selected = picker.selectedFile.toPath()
        selectedForImport = null
        selectedImportReport = null
        importStatus = null
        importBusy = true
        scope.launch {
            try {
                // Test magic/signature and Skia before asking for a quota.
                val preview = withContext(Dispatchers.IO) {
                    DesktopReadOnlyFontAnalyzer.inspect(selected)
                }
                selectedImportReport = preview
                selectedForImport = selected
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                importStatus = "Fonte inválida. Nenhuma cota foi solicitada."
            } finally {
                importBusy = false
            }
        }
    }

    fun confirmAuthorizedImport() {
        val gate = importGate ?: return
        val file = selectedForImport ?: return
        if (importBusy || selectedImportReport == null) return
        selectedForImport = null
        selectedImportReport = null
        importBusy = true
        importStatus = "Confirmando conta Google e cota no servidor oficial…"
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { gate.importFont(file) }
                snapshot = withContext(Dispatchers.IO) { reader.snapshot() }
                failed = false
                importStatus = "${saved.displayName}: fonte salva. Disponível em Criar Nome."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                importStatus = "A importação não foi autorizada ou não foi confirmada. " +
                    "Nenhuma fonte pendente foi liberada; consulte Minha Conta."
            } finally {
                importBusy = false
            }
        }
    }

    fun runTemporaryStitchPreflight() {
        if (runningBusy) return
        val selected = inspectedFontFile ?: return
        val sample = runningSample
        runningBusy = true
        runningReport = null
        runningError = null
        scope.launch {
            try {
                runningReport = withContext(Dispatchers.Default) {
                    DesktopExternalFontRunningPreflight.inspect(
                        selected, sample, requestedHeightMm = 18f
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                runningError =
                    "Não foi possível analisar as pontadas temporárias. " +
                        "A fonte não foi importada e nenhuma matriz foi salva."
            } finally {
                runningBusy = false
            }
        }
    }
    var catalogQuery by remember { mutableStateOf("") }
    var catalogBusy by remember { mutableStateOf(false) }
    var catalogStatus by remember { mutableStateOf<String?>(null) }
    var zipBusy by remember { mutableStateOf(false) }
    var zipPreview by remember { mutableStateOf<DesktopFontZipPreview.Result?>(null) }
    var zipSelectedPath by remember { mutableStateOf<Path?>(null) }
    var zipImportBusy by remember { mutableStateOf(false) }
    var zipImportStatus by remember { mutableStateOf<String?>(null) }
    var zipError by remember { mutableStateOf<String?>(null) }

    fun inspectFontZipReadOnly() {
        if (zipBusy) return
        val chooser = JFileChooser().apply {
            dialogTitle = "Inspecionar ZIP de fontes DaFont/NetFontes — sem importar"
            fileSelectionMode = JFileChooser.FILES_ONLY
            isAcceptAllFileFilterUsed = false
            fileFilter = FileNameExtensionFilter("Arquivos ZIP de fontes (*.zip)", "zip")
        }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
        val selected = chooser.selectedFile.toPath()
        zipBusy = true
        zipPreview = null
        zipSelectedPath = null
        zipImportStatus = null
        zipError = null
        scope.launch {
            try {
                val checked = withContext(Dispatchers.IO) {
                    DesktopFontZipPreview.inspect(selected)
                }
                zipPreview = checked
                zipSelectedPath = selected
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                zipError =
                    "Não foi possível analisar o ZIP de fontes com segurança. " +
                        "Nenhum arquivo foi extraído ou importado."
            } finally {
                zipBusy = false
            }
        }
    }

    fun importReviewedZipFont(font: DesktopFontZipPreview.Font) {
        val gate = importGate ?: return
        val source = zipSelectedPath ?: return
        val reviewed = zipPreview ?: return
        if (zipImportBusy || zipBusy || importBusy) return
        val consent = JOptionPane.showConfirmDialog(
            null,
            "Importar a fonte ${font.name} do ZIP analisado?\\n" +
                "Confirme que você possui autorização de uso.\\n" +
                "Esta ação verificará a conta Google no servidor e poderá " +
                "consumir uma cota de importação TTF/OTF.\\n" +
                "Cancelar não consome cota.",
            "Brother Matrizes — confirmar importação de fonte",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )
        if (consent != JOptionPane.YES_OPTION) return
        zipImportBusy = true
        zipImportStatus = "Validando pacote e autorização da conta…"
        scope.launch {
            try {
                val imported = withContext(Dispatchers.IO) {
                    DesktopFontZipAuthorizedImporter.importOne(
                        source, reviewed, font, gate
                    )
                }
                snapshot = withContext(Dispatchers.IO) { reader.snapshot() }
                failed = false
                zipImportStatus =
                    "${imported.displayName}: fonte importada na Biblioteca. " +
                        "Disponível em Criar Nome."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                zipImportStatus =
                    "Importação não confirmada. A fonte não foi liberada; " +
                        "verifique Minha Conta e eventuais cotas pendentes."
            } finally {
                zipImportBusy = false
            }
        }
    }

    fun openOfficialCatalog(source: FontCatalogSource) {
        if (catalogBusy) return
        scope.launch {
            catalogBusy = true
            catalogStatus = null
            try {
                val opened = DesktopFontCatalogSearch.browse(
                    source = source,
                    query = catalogQuery,
                    access = access
                )
                catalogStatus =
                    "${source.displayName} aberto no navegador. Baixe a fonte manualmente " +
                        "e confira sua licença antes de solicitar importação."
                // Does not automatically download, unpack or import.
                check(opened.catalog == source)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                catalogStatus = failure.message ?: "Pesquisa não autorizada nesta sessão."
            } finally {
                catalogBusy = false
            }
        }
    }

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
        inspectedFontFile = null
        runningReport = null
        runningError = null
        scope.launch {
            try {
                analysis = withContext(Dispatchers.IO) {
                    DesktopReadOnlyFontAnalyzer.inspect(selected)
                }
                inspectedFontFile = selected
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
                        if (importGate == null)
                            "A importação de novas fontes exige login Google e " +
                                "reserva de cota confirmada pelo servidor. " +
                                "Operação indisponível na versão Windows padrão."
                        else
                            "Importação disponível somente neste ambiente QA " +
                                "explicitamente autorizado, com confirmação da cota no " +
                                "mesmo servidor utilizado pelo Android.",
                        color = FioText
                    )
                }
                if (importGate != null) {
                    Text(
                        "Modo QA autorizado: esta importação consulta o Google e pode " +
                            "descontar uma cota REAL da mesma conta Brother Matrizes do Android.",
                        color = FioText
                    )
                    OutlinedButton(
                        onClick = { chooseFontForAuthorizedImport() },
                        enabled = !importBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Selecionar TTF/OTF para importação autorizada")
                    }
                    if (selectedForImport != null && selectedImportReport != null) {
                        val localPreview = checkNotNull(selectedImportReport)
                        Text(
                            "Fonte: ${localPreview.familyName} · ${localPreview.format} · " +
                                "${localPreview.byteSize / 1024} KB",
                            color = FioText
                        )
                        Button(
                            onClick = { confirmAuthorizedImport() },
                            enabled = !importBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Confirmar importação e possível desconto da cota") }
                        TextButton(
                            onClick = {
                                selectedForImport = null
                                selectedImportReport = null
                            },
                            enabled = !importBusy
                        ) { Text("Cancelar sem consumir cota") }
                    }
                    importStatus?.let { Text(it, color = FioGold) }
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = FioSurfaceAlt),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Pesquisar fontes — DaFont e NetFontes",
                        color = FioGold,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = catalogQuery,
                        onValueChange = { catalogQuery = it.take(90) },
                        label = { Text("Nome da fonte (opcional para categorias)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { openOfficialCatalog(FontCatalogSource.DAFONT) },
                            enabled = !catalogBusy,
                            modifier = Modifier.weight(1f)
                        ) { Text("Buscar no DaFont") }
                        Button(
                            onClick = { openOfficialCatalog(FontCatalogSource.NETFONTES) },
                            enabled = !catalogBusy,
                            modifier = Modifier.weight(1f)
                        ) { Text("Buscar no NetFontes") }
                    }
                    Text(
                        "Pesquisa DaFont: autenticação Google e reserva de cota " +
                            "confirmada pelo mesmo Supabase do Android. " +
                            "NetFontes: login Google sem cota de pesquisa DaFont. " +
                            "Acesso disponível somente em QA autorizada; " +
                            "downloads e importação de fontes permanecem separados.",
                        color = FioTextMuted
                    )
                    if (catalogStatus != null) {
                        Text(checkNotNull(catalogStatus), color = FioGold)
                    }
                    OutlinedButton(
                        onClick = onRequireGoogle,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Minha Conta · autenticação Google") }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = FioSurfaceAlt),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "ZIP de fontes DaFont / NetFontes",
                        color = FioGold, fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = { inspectFontZipReadOnly() },
                        enabled = !zipBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (zipBusy) "Analisando ZIP…"
                            else "Selecionar ZIP de fontes (sem importar)")
                    }
                    Text(
                        "Analise primeiro o ZIP com as verificações do Android. " +
                            "A seleção não consome cota; para importar uma fonte " +
                            "é necessário login verificado e confirmação explícita.",
                        color = FioTextMuted
                    )
                    if (zipError != null) {
                        Text(checkNotNull(zipError), color = FioGold)
                    }
                    zipImportStatus?.let { Text(it, color = FioGold) }
                    if (zipPreview != null) {
                        val report = checkNotNull(zipPreview)
                        Text(
                            "${report.zipName}: ${report.fontCount} fonte(s) TTF/OTF; " +
                                "${report.skippedItems} item(ns) não selecionáveis.",
                            color = FioText
                        )
                        Text(
                            if (report.fontCount == 0)
                                "Este ZIP não contém fontes compatíveis para análise."
                            else
                                if (importGate == null)
                                    "Prévia abaixo. Entre com a conta validada para importar."
                                else
                                    "Escolha cada fonte para importar com autorização e cota.",
                            color = FioTextMuted
                        )
                    }
                }
            }
        }
        if (zipPreview != null) {
            val zipFonts = checkNotNull(zipPreview).fonts
            items(zipFonts, key = { "zip_${it.index}" }) { font ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = FioSurfaceAlt)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(font.name, color = FioText, fontWeight = FontWeight.Bold)
                        Text(
                            "${font.format} · ${font.sizeBytes / 1024} KB",
                            color = FioTextMuted
                        )
                        if (importGate != null) {
                            OutlinedButton(
                                onClick = { importReviewedZipFont(font) },
                                enabled = !zipBusy && !zipImportBusy,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (zipImportBusy)
                                "Validando importação…" else
                                "Importar com autorização e confirmação da cota")
                            }
                        }
                    }
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
        if (analysis != null && inspectedFontFile != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = FioSurfaceAlt)
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Prévia técnica de pontadas — ponto corrido (QA)",
                            color = FioGold,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = runningSample,
                            onValueChange = { runningSample = it.take(24) },
                            label = { Text("Texto da amostra, até 24 caracteres") },
                            singleLine = true,
                            enabled = !runningBusy,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = { runTemporaryStitchPreflight() },
                            enabled = !runningBusy && runningSample.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (runningBusy) "Calculando contornos…"
                                else "Testar pontadas em memória (sem exportar)")
                        }
                        Text(
                            "Amostra RUNNING calculada pelo motor vetorial do Android. " +
                                "Não substitui o Satin de fontes importadas, " +
                                "não cria DST, não altera a Biblioteca e não usa cotas.",
                            color = FioTextMuted
                        )
                        if (runningError != null) {
                            Text(checkNotNull(runningError), color = FioGold)
                        }
                        if (runningReport != null) {
                            val stats = checkNotNull(runningReport)
                            Text(
                                "Contornos: ${stats.glyphContours} · pontos vetoriais: " +
                                    "${stats.sampledOutlinePoints} · " +
                                    "pontadas: ${stats.stitchCount} · " +
                                    "saltos: ${stats.jumpCount}",
                                color = FioText
                            )
                            Text(
                                "Dimensões temporárias: " +
                                    "${"%.1f".format(stats.widthMm)} × " +
                                    "${"%.1f".format(stats.heightMm)} mm",
                                color = FioTextMuted
                            )
                        }
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
            // Os cartões de bordado, amostras BM/SATIN, famílias e contagem
            // de pontadas vêm literalmente de FontLibraryScreen.kt do Android.
            // A lista usa EmbroideryFontPreset, sem duplicar fontes por nome.
            items(EmbroideryFontPreset.entries, key = { "preset_" + it.name }) { preset ->
                OriginalAndroidNativeFontCard(preset)
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
