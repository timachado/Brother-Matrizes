package com.timachado.brothermatrizes

import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import com.timachado.brothermatrizes.core.embroidery.SimpleSvgParser
import com.timachado.brothermatrizes.core.embroidery.VectorArtwork
import com.timachado.brothermatrizes.core.svg.SvgSilhDownloader
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayOutputStream

/**
 * Catálogo SVG Silh numa WebView isolada. O site fornece busca, categorias e
 * miniaturas; o app recebe downloads SVG explícitos sem extrair páginas HTML.
 */
@Composable
fun SvgSilhCatalogScreen(
    onBack: () -> Unit,
    onUseArtwork: (VectorArtwork) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var browser by remember { mutableStateOf<WebView?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf("Pesquise pelo campo do catálogo ou escolha uma categoria.")
    }
    var lastDownload by remember { mutableStateOf<ByteArray?>(null) }
    var lastName by remember { mutableStateOf<String?>(null) }

    fun backWithinCatalogOrExit() {
        val active = browser
        if (active != null && active.canGoBack()) {
            active.goBack()
        } else {
            onBack()
        }
    }

    // Respeita o histórico do site também quando o usuário pressiona Voltar
    // na navegação do próprio Android.
    BackHandler { backWithinCatalogOrExit() }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/svg+xml")
    ) { uri ->
        val bytes = lastDownload
        if (uri == null) {
            status = "Arquivo pronto. Use Baixar SVG para escolher onde salvar."
        } else if (bytes != null) {
            status = runCatching {
                context.contentResolver.openOutputStream(uri, "w")?.use {
                    it.write(bytes)
                } ?: error("Não foi possível gravar o arquivo.")
                "SVG salvo. Você também pode abrir no editor."
            }.getOrElse { "Erro ao salvar: " + (it.message ?: "erro desconhecido") }
        }
    }

    val localPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = stream.read(buffer)
                                if (n < 0) break
                                require(output.size() + n <= 2_000_000) {
                                    "SVG maior que 2 MB."
                                }
                                output.write(buffer, 0, n)
                            }
                            output.toByteArray()
                        } ?: error("Não foi possível abrir o arquivo.")
                    }
                    val artwork = withContext(Dispatchers.Default) {
                        SimpleSvgParser.parse("SVG baixado", bytes).getOrThrow()
                    }
                    onUseArtwork(artwork)
                } catch (error: Exception) {
                    status = "Falha ao importar: " + (error.message ?: "SVG incompatível")
                } finally {
                    busy = false
                }
            }
        }
    }

    fun startDownload(raw: String) {
        if (busy) return
        if (!SvgSilhDownloader.isDownloadLink(raw)) {
            status = "Somente arquivos SVG do catálogo SVG Silh são permitidos."
            return
        }
        scope.launch {
            busy = true
            status = "Baixando SVG..."
            try {
                val bytes = SvgSilhDownloader.download(raw).getOrThrow()
                val filename = SvgSilhDownloader.fileName(raw)
                lastDownload = bytes
                lastName = filename
                status = "SVG pronto (" + (bytes.size / 1024) + " KB): " + filename
                saveLauncher.launch(filename)
            } catch (error: Exception) {
                status = "Falha no download: " + (error.message ?: "conexão indisponível")
            } finally {
                busy = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            browser?.apply {
                stopLoading()
                destroy()
            }
            browser = null
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = { backWithinCatalogOrExit() },
                modifier = Modifier.testTag("svg_catalog_back")
            ) { Text("‹ Voltar") }
            OutlinedButton(onClick = {
                browser?.loadUrl(SvgSilhDownloader.HOME_URL)
            }) { Text("Início do catálogo") }
        }
        Text(
            "Buscar desenhos SVG", color = FioText,
            fontWeight = FontWeight.Bold, fontSize = 21.sp
        )
        Text(
            "SVG Silh • catálogo com miniaturas e busca • licença CC0 declarada pelo site.",
            color = FioTextMuted, fontSize = 11.sp
        )
        Text(
            "Escolha um desenho e toque em Download SVG na página dele. " +
                "Depois selecione onde salvar pelo Android.",
            color = FioTextMuted, fontSize = 11.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { browser?.loadUrl("https://svgsilh.com/pt/tag/flor-1.html") }
            ) { Text("Flores") }
            OutlinedButton(
                onClick = { browser?.loadUrl("https://svgsilh.com/pt/tag/animal-1.html") }
            ) { Text("Animais") }
            OutlinedButton(
                onClick = {
                    localPicker.launch(arrayOf("image/svg+xml", "application/xml", "text/xml"))
                }
            ) { Text("SVG salvo") }
        }
        if (busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator()
                Text("Carregando arquivo...", color = FioTextMuted, fontSize = 11.sp)
            }
        }
        Text(status, color = FioGold, fontSize = 11.sp,
            modifier = Modifier.testTag("svg_catalog_status"))
        if (lastDownload != null && !busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val bytes = lastDownload ?: return@OutlinedButton
                        if (busy) return@OutlinedButton
                        val name = lastName ?: "Desenho SVG"
                        scope.launch {
                            busy = true
                            try {
                                val parsed = withContext(Dispatchers.Default) {
                                    SimpleSvgParser.parse(name, bytes)
                                }
                                parsed.fold(
                                    onSuccess = onUseArtwork,
                                    onFailure = {
                                        status = "SVG salvo, porém incompatível com o editor: " +
                                            (it.message ?: "geometria não suportada")
                                    }
                                )
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.testTag("svg_catalog_use_in_editor")
                ) { Text("Usar no editor") }
                OutlinedButton(
                    onClick = { lastName?.let { name -> saveLauncher.launch(name) } }
                ) { Text("Baixar SVG") }
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("svg_silh_browser"),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.setSupportMultipleWindows(false)
                    settings.safeBrowsingEnabled = true
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?, request: WebResourceRequest?
                        ): Boolean {
                            if (request == null || !request.isForMainFrame) return false
                            val url = request.url.toString()
                            if (SvgSilhDownloader.isDownloadLink(url)) {
                                startDownload(url)
                                return true
                            }
                            if (SvgSilhDownloader.isTrustedPage(url)) return false
                            if (SvgSilhDownloader.isSafeExternalBrowserLink(url)) {
                                runCatching {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            .addCategory(Intent.CATEGORY_BROWSABLE)
                                    )
                                }.onFailure {
                                    status = "Não foi possível abrir esse link no navegador."
                                }
                            } else {
                                status = "Link bloqueado por segurança: somente páginas HTTPS."
                            }
                            return true
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                status = "Catálogo indisponível. Confira a internet e tente de novo."
                            }
                        }
                    }
                    setDownloadListener { url, _, _, _, _ -> startDownload(url) }
                    browser = this
                    loadUrl(SvgSilhDownloader.HOME_URL)
                }
            }
        )
    }
}
