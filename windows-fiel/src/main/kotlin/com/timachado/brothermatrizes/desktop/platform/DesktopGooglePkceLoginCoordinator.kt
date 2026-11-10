package com.timachado.brothermatrizes.desktop.platform

import java.awt.Desktop
import java.net.URI
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** External system browser only: never use a WebView to handle Google credentials. */
/**
 * Diagnostic stages: no Google code, verifier, JWT, refresh token or private
 * provider error is ever included in a UI message.
 */
internal enum class DesktopGoogleLoginStage(val userMessage: String) {
    PROVIDER("GOOGLE-01: Não foi possível verificar a configuração Google no Supabase."),
    BROWSER("GOOGLE-02: O navegador não pôde abrir a autorização Google."),
    CALLBACK("GOOGLE-03: O retorno local do Google não foi concluído."),
    TOKEN("GOOGLE-04: O Windows não conseguiu trocar o código Google pela sessão Supabase."),
    IDENTITY("GOOGLE-05: A identidade Google não foi confirmada pelo Supabase."),
    STORAGE("GOOGLE-06: Não foi possível proteger a sessão no perfil do Windows.")
}

internal class DesktopGoogleLoginFailure(val stage: DesktopGoogleLoginStage) :
    IllegalStateException(stage.userMessage)

internal fun interface DesktopExternalBrowser {
    fun open(url: URI)
}

internal object WindowsExternalBrowser : DesktopExternalBrowser {
    override fun open(url: URI) {
        require(url.scheme == "https" &&
            url.host == "dwpcddiramxlhavdmmyn.supabase.co" &&
            url.port == -1 &&
            url.rawUserInfo == null &&
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
    private val browser: DesktopExternalBrowser = WindowsExternalBrowser,
    private val callbackTimeout: Duration = Duration.ofMinutes(5)
) {
    suspend fun signInForIsolatedQa(): DesktopTransientAuthSession =
        withContext(Dispatchers.IO) {
            DesktopPkceLoopbackListener.open().use { callback ->
                val loginUrl = auth.authorizationUrl(callback.handshake)
                try {
                    browser.open(loginUrl)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    throw DesktopGoogleLoginFailure(DesktopGoogleLoginStage.BROWSER)
                }
                // The local callback only confirms receipt of the auth code;
                // it does not prove an authenticated Supabase session.
                val context = currentCoroutineContext()
                val proof = try {
                    callback.awaitCallback(callbackTimeout) { context.ensureActive() }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    throw DesktopGoogleLoginFailure(DesktopGoogleLoginStage.CALLBACK)
                }
                context.ensureActive()
                val session = try {
                    auth.exchangeCode(proof)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    throw DesktopGoogleLoginFailure(DesktopGoogleLoginStage.TOKEN)
                }
                context.ensureActive()
                session
            }
        }
}
