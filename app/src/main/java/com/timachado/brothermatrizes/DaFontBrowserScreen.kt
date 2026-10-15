package com.timachado.brothermatrizes

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.timachado.brothermatrizes.font.DaFontNavigationRules
import com.timachado.brothermatrizes.font.FontCatalogSource
import com.timachado.brothermatrizes.core.account.BrotherMatrizesAccountService
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Browser for the official DaFont and NetFontes websites, without scraping
 * or undocumented APIs. Files are downloaded only after the user requests
 * them on the selected website, then validated by the existing ZIP importer.
 * Font licenses remain the responsibility of their respective authors.
 */
@Composable
fun DaFontBrowserScreen(
    source: FontCatalogSource = FontCatalogSource.DAFONT,
    onBack: () -> Unit,
    onArchiveReady: (Uri) -> Unit,
    onRequireGoogle: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val downloadManager = remember {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }
    val pending = remember { mutableStateListOf<Long>() }
    var query by remember { mutableStateOf("") }
    var message by remember(source) { mutableStateOf(
        if (source == FontCatalogSource.DAFONT)
            "Pesquise ou navegue por categorias. Confira a licença informada pelo autor."
        else
            "Use a busca do próprio NetFontes ou navegue por categorias. Confira a licença da fonte."
    ) }
    var browser by remember { mutableStateOf<WebView?>(null) }
    var pageLoading by remember { mutableStateOf(false) }
    var displayedPage by remember(source) { mutableStateOf(source.homeUrl) }
    var failedPage by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var meteredSearchUrl by remember { mutableStateOf("") }
    var meteredAt by remember { mutableStateOf(0L) }
    fun goBack() {
        val web = browser
        if (web != null && web.canGoBack()) web.goBack() else onBack()
    }
    BackHandler { goBack() }

    /**
     * O DownloadManager pode não entregar o broadcast a receptores não
     * exportados em algumas versões Android. A checagem adicional de status
     * mantém a importação acessível sem abrir qualquer serviço externo.
     */
    fun inspectDownload(id: Long): Int = runCatching {
        downloadManager.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) DownloadManager.STATUS_FAILED
            else c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        }
    }.getOrDefault(DownloadManager.STATUS_FAILED)

    fun handleCompletedDownload(id: Long) {
        if (!pending.remove(id)) return
        scope.launch {
            val downloaded = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = downloadManager.getUriForDownloadedFile(id)
                        ?: error("O download foi concluído, mas o arquivo não está disponível.")
                    val prefix = ByteArray(4)
                    val count = context.contentResolver.openInputStream(uri)?.use {
                        it.read(prefix)
                    } ?: 0
                    check(count == 4 && DaFontNavigationRules.looksLikeZip(prefix)) {
                        "O ${source.displayName} retornou uma página em vez de um ZIP. " +
                            "Tente novamente ou baixe a fonte no navegador."
                    }
                    uri
                }
            }
            downloaded.fold(
                onSuccess = { onArchiveReady(it) },
                onFailure = { problem ->
                    message = problem.message ?: "Não foi possível abrir a fonte baixada."
                }
            )
        }
    }

    DisposableEffect(context, downloadManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id < 0 || id !in pending) return
                when (inspectDownload(id)) {
                    DownloadManager.STATUS_SUCCESSFUL -> handleCompletedDownload(id)
                    DownloadManager.STATUS_FAILED -> {
                        pending.remove(id)
                        message = "O download da fonte falhou. Tente novamente."
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            browser?.apply {
                stopLoading()
                destroy()
            }
        }
    }

    suspend fun meteredSearch(destination: String) {
        if (searching) return
        searching = true
        try {
            val google = BrotherMatrizesAccountService.ensureGoogleSession()
            if (google.isFailure) {
                message = "Entre com Google em Minha Conta para pesquisar no DaFont."
                onRequireGoogle()
                return
            }
            val permission = BrotherMatrizesAccountService.authorizeTool("dafont_search")
            if (permission.isFailure) {
                message = permission.exceptionOrNull()?.message
                    ?: "Limite de pesquisas DaFont atingido."
                return
            }
            val key = permission.getOrThrow()
            keyboard?.hide()
            focusManager.clearFocus()
            val opened = runCatching {
                val web = browser ?: error("O catálogo ainda está carregando.")
                meteredSearchUrl = destination
                meteredAt = android.os.SystemClock.elapsedRealtime()
                web.loadUrl(destination)
            }.isSuccess
            val receipt = BrotherMatrizesAccountService.finalizeTool(
                "dafont_search", key, opened
            )
            if (opened) {
                pageLoading = true
                failedPage = false
                displayedPage = destination
            }
            message = when {
                !opened -> "Busca não iniciada. Nenhuma pesquisa deve ser descontada."
                receipt.isFailure -> "Busca iniciada, mas houve falha ao confirmar a cota."
                else -> "Busca iniciada no catálogo DaFont."
            }
        } finally {
            searching = false
        }
    }

    fun searchFont() {
        scope.launch { meteredSearch(DaFontNavigationRules.searchUrl(query.trim())) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = ::goBack) { Text("‹ Voltar") }
            TextButton(onClick = {
                browser?.loadUrl(source.homeUrl)
                query = ""
                message = "Navegando pelo catálogo oficial ${source.displayName}."
            }) { Text("Categorias") }
            TextButton(onClick = {
                val target = displayedPage.takeIf(source::isOfficialPage)
                    ?: source.homeUrl
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                }
            }) { Text("Abrir no navegador") }
        }
        if (source == FontCatalogSource.DAFONT) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(90) },
                    label = { Text("Buscar fonte no DaFont") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { searchFont() })
                )
                Button(onClick = ::searchFont) { Text("Buscar") }
            }
        }
        Text(
            if (source == FontCatalogSource.DAFONT)
                "DaFont • Prévia e categorias no site. Licenças pertencem aos autores."
            else
                "NetFontes • Pesquise na barra do site abaixo. Confira as condições de uso.",
            color = FioTextMuted,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        if (pageLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    setInitialScale(0)
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(
                            view: WebView?, url: String?, favicon: android.graphics.Bitmap?
                        ) {
                            pageLoading = true
                            failedPage = false
                            if (url != null && source.isOfficialPage(url)) {
                                displayedPage = url
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            pageLoading = false
                            if (!failedPage && url != null &&
                                source.isOfficialPage(url)) {
                                displayedPage = url
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?, request: WebResourceRequest?, error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                failedPage = true
                                pageLoading = false
                                message = "Não foi possível carregar o ${source.displayName}. " +
                                    "Verifique a conexão ou toque em Abrir no navegador."
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView?, request: WebResourceRequest?,
                            errorResponse: WebResourceResponse?
                        ) {
                            if (request?.isForMainFrame == true &&
                                (errorResponse?.statusCode ?: 200) >= 400) {
                                failedPage = true
                                pageLoading = false
                                message = "O site ${source.displayName} respondeu HTTP " +
                                    (errorResponse?.statusCode ?: 0) +
                                    ". Abra o link no navegador para continuar."
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?, request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url ?: return true
                            if (source.isOfficialPage(url.toString())) {
                                if (source == FontCatalogSource.DAFONT &&
                                    request.isForMainFrame &&
                                    url.path == "/search.php" &&
                                    !url.getQueryParameter("q").isNullOrBlank()) {
                                    val address = url.toString()
                                    val elapsed = android.os.SystemClock.elapsedRealtime() - meteredAt
                                    if (address == meteredSearchUrl && elapsed in 0L..1500L) {
                                        return false
                                    }
                                    if (!searching) scope.launch { meteredSearch(address) }
                                    return true
                                }
                                return false
                            }
                            if (url.scheme.equals("https", ignoreCase = true)) {
                                runCatching {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, url))
                                }
                            }
                            return true
                        }
                    }
                    setDownloadListener { url, userAgent, _, _, _ ->
                        if (!source.isOfficialDownload(url)) {
                            message = "Por segurança, abra downloads de outros sites no navegador."
                        } else {
                            runCatching {
                                val address = Uri.parse(url)
                                val request = DownloadManager.Request(address)
                                    .setTitle("Fonte ${source.displayName} para Brother Matrizes")
                                    .setDescription("Download solicitado pelo usuário")
                                    .setMimeType("application/zip")
                                    .setNotificationVisibility(
                                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                                    )
                                    .setDestinationInExternalFilesDir(
                                        ctx, Environment.DIRECTORY_DOWNLOADS,
                                        source.filePrefix + "-" + System.currentTimeMillis() + ".zip"
                                    )
                                // Preserve browser session on sites requiring a cookie to download.
                                if (userAgent.isNotBlank()) request.addRequestHeader("User-Agent", userAgent)
                                android.webkit.CookieManager.getInstance().getCookie(url)
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { request.addRequestHeader("Cookie", it) }
                                val id = downloadManager.enqueue(request)
                                pending.add(id)
                                message = "Download iniciado. Ao concluir, escolha as fontes " +
                                    "TTF/OTF para importar."
                                scope.launch {
                                    // A consulta abaixo também funciona se o Android não
                                    // disparar o aviso de download para o receptor.
                                    repeat(180) {
                                        if (id !in pending) return@launch
                                        delay(1000L)
                                        val status = withContext(Dispatchers.IO) {
                                            inspectDownload(id)
                                        }
                                        when (status) {
                                            DownloadManager.STATUS_SUCCESSFUL -> {
                                                handleCompletedDownload(id)
                                                return@launch
                                            }
                                            DownloadManager.STATUS_FAILED -> {
                                                pending.remove(id)
                                                message = "Não foi possível baixar o ZIP. " +
                                                    "Tente novamente."
                                                return@launch
                                            }
                                        }
                                    }
                                    if (pending.remove(id)) {
                                        message = "O download está demorando. " +
                                            "Você pode importar o ZIP pela aba Fontes."
                                    }
                                }
                            }.onFailure { problem ->
                                message = "Não foi possível iniciar download: " +
                                    (problem.message ?: "erro desconhecido")
                            }
                        }
                    }
                    loadUrl(source.homeUrl)
                    browser = this
                }
            }
        )
        Spacer(Modifier.height(6.dp))
        Text(message, color = FioGold, modifier = Modifier.padding(bottom = 8.dp))
    }
}
