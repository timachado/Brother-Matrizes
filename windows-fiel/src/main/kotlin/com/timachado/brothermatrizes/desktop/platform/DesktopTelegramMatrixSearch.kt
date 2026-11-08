package com.timachado.brothermatrizes.desktop.platform

import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Android TelegramMatrixSearchScreen contract on Windows.
 * Telegram itself searches users' private groups; Brother Matrizes does not
 * collect chats or use Telegram credentials. A quota must be reserved and
 * finalized with the official Supabase BEFORE and AFTER the external launch.
 */
internal interface DesktopTelegramSearchAccess {
    suspend fun hasVerifiedGoogleSession(): Boolean
    suspend fun reserveSearch(): String
    suspend fun finalizeSearch(requestKey: String, success: Boolean)
}

internal object DesktopTelegramSearchUnavailable : DesktopTelegramSearchAccess {
    override suspend fun hasVerifiedGoogleSession(): Boolean = false
    override suspend fun reserveSearch(): String =
        error("Entre com sua conta Google validada antes de pesquisar.")
    override suspend fun finalizeSearch(requestKey: String, success: Boolean): Unit =
        error("Nenhuma pesquisa Telegram foi autorizada.")
}

internal object DesktopTelegramMatrixSearch {
    private val receipt = Regex(
        "^(usage|pro)_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    )

    fun destination(raw: String): URI {
        val text = raw.trim().take(100)
        require(text.none { Character.isISOControl(it) }) {
            "A pesquisa contém caracteres inválidos."
        }
        return if (text.isEmpty()) URI("tg://search")
        else URI(
            "tg://search?query=" +
                URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20")
        )
    }

    /** An external-launch success does NOT prove that Telegram found content. */
    suspend fun browse(
        raw: String,
        access: DesktopTelegramSearchAccess = DesktopTelegramSearchUnavailable,
        launch: (URI) -> Unit = ::openNativeTelegram
    ): URI {
        val uri = destination(raw)
        check(access.hasVerifiedGoogleSession()) {
            "Entre com Google em Minha Conta para pesquisar no Telegram."
        }
        val key = access.reserveSearch()
        check(receipt.matches(key)) { "Reserva de pesquisa Telegram inválida." }
        var launched = false
        try {
            launch(uri)
            launched = true
        } finally {
            // Same reserve/finalize sequence as the Android screen.
            // No automatic retries; failure to launch finalizes false.
            access.finalizeSearch(key, launched)
        }
        return uri
    }

    private fun openNativeTelegram(uri: URI) {
        require(uri.scheme == "tg" && uri.host == "search" &&
            uri.fragment == null && uri.rawUserInfo == null &&
            uri.port == -1 && (uri.rawQuery == null ||
                uri.rawQuery.startsWith("query="))) {
            "Deep link Telegram não permitido."
        }
        check(Desktop.isDesktopSupported() &&
            Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            "O Windows não disponibilizou abertura de aplicativos por link."
        }
        // Windows dispatches tg://search to the registered Telegram client;
        // there is no direct access to chat history or user credentials.
        Desktop.getDesktop().browse(uri)
    }
}
