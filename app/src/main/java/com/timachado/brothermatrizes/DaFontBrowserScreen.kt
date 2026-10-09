package com.timachado.brothermatrizes

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.webkit.WebResourceRequest
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
import com.timachado.brothermatrizes.font.FontArchiveImporter
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.Dispatchers
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
    onImported: (Int) -> Unit
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
    fun goBack() {
        val web = browser
        if (web != null && web.canGoBack()) web.goBack() else onBack()
    }
    BackHandler { goBack() }

    DisposableEffect(context, downloadManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id < 0 || !pending.remove(id)) return
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val cursor = downloadManager.query(
                                DownloadManager.Query().setFilterById(id)
                            )
                            val successful = cursor.use { c ->
                                c.moveToFirst() &&
                                    c.getInt(c.getColumnIndexOrThrow(
                                        DownloadManager.COLUMN_STATUS
                                    )) == DownloadManager.STATUS_SUCCESSFUL
                            }
                            check(successful) {
                                "O download não foi concluído. Tente novamente."
                            }
                            val uri = downloadManager.getUriForDownloadedFile(id)
                                ?: error("Arquivo baixado indisponível.")
                            FontArchiveImporter.importZip(context, uri).getOrThrow()
                        }
                    }
                    result.fold(
                        onSuccess = {
                            message = "${it.size} fonte(s) importada(s) com sucesso para Criar Nome."
                            onImported(it.size)
                        },
                        onFailure = { message = it.message ?: "Falha ao importar fontes do ZIP." }
                    )
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
        // DaFont search.php is the query endpoint, not /pt/?q=.
        val url = if (term.isNotEmpty()) {
            "https://www.dafont.com/search.php?q=${Uri.encode(term)}"
        } else {
            "https://www.dafont.com/pt/"
        }
        browser?.loadUrl(url)
        message = if (term.isNotEmpty()) {
            "Resultados para: $term. Confira a licença antes de baixar."
        } else {
            "Explore categorias ou digite o nome da fonte."
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        OutlinedButton(onClick = ::goBack) { Text("‹ Voltar") }
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
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    setInitialScale(0)
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?, request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url ?: return true
                            val allowed = url.scheme == "https" &&
                                (url.host?.lowercase(Locale.ROOT) == "dafont.com" ||
                                 url.host?.lowercase(Locale.ROOT) == "www.dafont.com")
                            if (allowed) return false
                            if (url.scheme == "https") {
                                runCatching {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, url))
                                }
                            }
                            return true
                        }
                    }
                    setDownloadListener { url, _, _, _, _ ->
                        val address = runCatching { Uri.parse(url) }.getOrNull()
                        if (address?.scheme != "https" ||
                            address.host?.lowercase(Locale.ROOT) != "dl.dafont.com") {
                            message = "Por segurança, apenas ZIPs oficiais do DaFont podem ser baixados aqui."
                        } else {
                            runCatching {
                                val request = DownloadManager.Request(address)
                                    .setTitle("Fonte DaFont para Brother Matrizes")
                                    .setDescription("Importação de fonte autorizada pelo usuário")
                                    .setMimeType("application/zip")
                                    .setNotificationVisibility(
                                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                                    )
                                    .setDestinationInExternalFilesDir(
                                        ctx, Environment.DIRECTORY_DOWNLOADS,
                                        "dafont-${System.currentTimeMillis()}.zip"
                                    )
                                val id = downloadManager.enqueue(request)
                                pending.add(id)
                                message = "Baixando fonte; a importação será feita ao concluir."
                            }.onFailure {
                                message = "Não foi possível iniciar download: ${it.message}"
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
