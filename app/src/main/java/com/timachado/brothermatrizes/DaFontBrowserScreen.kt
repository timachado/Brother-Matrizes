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
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Browser under DaFont's own pages. There is no undocumented DaFont API,
 * remote scraping, or automatic licensing claim. Downloaded archives are
 * imported locally only after the user taps DaFont's download button.
 */
@Composable
fun DaFontBrowserScreen(
    onBack: () -> Unit,
    onArchiveReady: (Uri) -> Unit
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
    var message by remember { mutableStateOf(
        "Pesquise ou navegue por categorias. Confira a licença informada pelo autor."
    ) }
    var browser by remember { mutableStateOf<WebView?>(null) }
    var pageLoading by remember { mutableStateOf(false) }
    var displayedPage by remember { mutableStateOf(DaFontNavigationRules.HOME) }
    var failedPage by remember { mutableStateOf(false) }
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
                        "O DaFont retornou uma página em vez de um ZIP. " +
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

    fun searchFont() {
        val term = query.trim()
        keyboard?.hide()
        focusManager.clearFocus()
        val destination = DaFontNavigationRules.searchUrl(term)
        browser?.loadUrl(destination)
        pageLoading = true
        failedPage = false
        displayedPage = destination
        message = if (term.isNotEmpty()) {
            "Buscando \"" + term + "\" no catálogo DaFont. " +
                "Se não houver resultados, a fonte pode não estar disponível no site."
        } else {
            "Explore o catálogo oficial ou digite o nome de uma fonte."
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = ::goBack) { Text("‹ Voltar") }
            TextButton(onClick = {
                browser?.loadUrl(DaFontNavigationRules.HOME)
                query = ""
                message = "Navegando pelo catálogo oficial DaFont."
            }) { Text("Categorias") }
            TextButton(onClick = {
                val target = displayedPage.takeIf(DaFontNavigationRules::isOfficialPage)
                    ?: DaFontNavigationRules.HOME
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                }
            }) { Text("Abrir no navegador") }
        }
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
        Text(
            "DaFont • Prévia e categorias no site. Licenças pertencem aos autores.",
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
                            if (url != null && DaFontNavigationRules.isOfficialPage(url)) {
                                displayedPage = url
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            pageLoading = false
                            if (!failedPage && url != null &&
                                DaFontNavigationRules.isOfficialPage(url)) {
                                displayedPage = url
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?, request: WebResourceRequest?, error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                failedPage = true
                                pageLoading = false
                                message = "Não foi possível carregar o DaFont. " +
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
                                message = "O site DaFont respondeu HTTP " +
                                    (errorResponse?.statusCode ?: 0) +
                                    ". Abra o link no navegador para continuar."
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?, request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url ?: return true
                            if (DaFontNavigationRules.isOfficialPage(url.toString())) return false
                            if (url.scheme.equals("https", ignoreCase = true)) {
                                runCatching {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, url))
                                }
                            }
                            return true
                        }
                    }
                    setDownloadListener { url, _, _, _, _ ->
                        if (!DaFontNavigationRules.isOfficialDownload(url)) {
                            message = "Por segurança, abra downloads de outros sites no navegador."
                        } else {
                            runCatching {
                                val address = Uri.parse(url)
                                val request = DownloadManager.Request(address)
                                    .setTitle("Fonte DaFont para Brother Matrizes")
                                    .setDescription("Download solicitado pelo usuário")
                                    .setMimeType("application/zip")
                                    .setNotificationVisibility(
                                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                                    )
                                    .setDestinationInExternalFilesDir(
                                        ctx, Environment.DIRECTORY_DOWNLOADS,
                                        "dafont-" + System.currentTimeMillis() + ".zip"
                                    )
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
                    loadUrl("https://www.dafont.com/pt/")
                    browser = this
                }
            }
        )
        Spacer(Modifier.height(6.dp))
        Text(message, color = FioGold, modifier = Modifier.padding(bottom = 8.dp))
    }
}
