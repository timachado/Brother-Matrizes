package com.timachado.brothermatrizes.desktop.platform

import java.awt.Desktop
import java.net.URI
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** External system browser only: never use a WebView to handle Google credentials. */
internal fun interface DesktopExternalBrowser {
    fun open(url: URI)
}

internal object WindowsExternalBrowser : DesktopExternalBrowser {
    override fun open(url: URI) {
        require(url.scheme == "https" &&
            url.host == "dwpcddiramxlhavdmmyn.supabase.co" &&
            url.path == "/auth/v1/authorize" &&
            url.rawFragment == null) {
            "URL de autorização não autorizada."
        }
        check(Desktop.isDesktopSupported()) {
            "Navegador externo indisponível neste computador."
        }
        val desktop = Desktop.getDesktop()
        check(desktop.isSupported(Desktop.Action.BROWSE)) {
            "O sistema não oferece abertura de links no navegador."
        }
        desktop.browse(url)
    }
}

/**
 * Fluxo preparatório, sem ligação à interface comercial:
 *
 * 1. Reserva 127.0.0.1:porta-efêmera antes de construir redirect.
 * 2. Abre somente a URL HTTPS do Supabase no navegador externo.
 * 3. Aceita apenas callback local e PKCE state/código válidos.
 * 4. Troca o código no endpoint PKCE e retorna sessão TRANSITÓRIA.
 *
 * NÃO ativa quotas, imports, funcionalidades Pro ou sessão persistente;
 * nenhum token é fornecido ao DesktopCommercialFontImportGate.
 * A autorização comercial exige mais etapas de homologação.
 */
internal class DesktopGooglePkceLoginCoordinator(
    private val auth: DesktopSupabaseGooglePkceClient,
    private val browser: DesktopExternalBrowser = WindowsExternalBrowser
) {
    suspend fun signInForIsolatedQa(): DesktopTransientAuthSession =
        withContext(Dispatchers.IO) {
            DesktopPkceLoopbackListener.open().use { callback ->
                val loginUrl = auth.authorizationUrl(callback.handshake)
                browser.open(loginUrl)
                val proof = callback.awaitCallback(Duration.ofMinutes(5))
                auth.exchangeCode(proof)
            }
        }
}
