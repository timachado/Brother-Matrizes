package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.account.WordPressWebStore
import java.awt.Desktop
import java.net.URI

/**
 * Original Android WooCommerce browser handoff on Windows.
 *
 * Uses EXACTLY WordPressWebStore, copied from the commercial Android code.
 * Browser purchase/account pages never mean an in-app license, verified
 * payment or authorization to import: the Supabase server remains sovereign.
 */
internal object DesktopWindowsWordPressHandoff {
    private const val OFFICIAL_SITE = "https://timachado.ifree.page"
    private val permittedCodes = setOf(
        "pro_monthly", "pro_yearly", "pro_lifetime", "pro_lifetime_launch"
    )

    fun account(): URI = strict(URI(WordPressWebStore.accountUrl(OFFICIAL_SITE)))

    fun plan(code: String): URI {
        require(code in permittedCodes) { "Plano comercial não reconhecido." }
        return strict(URI(WordPressWebStore.planUrl(OFFICIAL_SITE, code)))
    }

    private fun strict(uri: URI): URI {
        require(uri.scheme == "https" &&
            uri.host.equals("timachado.ifree.page", ignoreCase = true) &&
            uri.port == -1 && uri.userInfo == null && uri.rawFragment == null &&
            (uri.rawPath == "/minha-conta/meus-aplicativos/" ||
                uri.rawPath == "/minha-conta/" ||
                uri.rawPath == "/") &&
            // Allowlist exact originally authored Android destinations only.
            (uri.toString() == WordPressWebStore.accountUrl(OFFICIAL_SITE) ||
                permittedCodes.any {
                    uri.toString() == WordPressWebStore.planUrl(OFFICIAL_SITE, it)
                })) { "Destino de compra ou conta não autorizado." }
        return uri
    }

    fun openApproved(uri: URI) {
        strict(uri)
        check(Desktop.isDesktopSupported() &&
            Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            "Nenhum navegador padrão está disponível no Windows."
        }
        Desktop.getDesktop().browse(uri)
    }
}
