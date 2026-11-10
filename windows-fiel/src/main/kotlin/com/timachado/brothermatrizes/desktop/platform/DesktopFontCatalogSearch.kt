package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.font.DaFontNavigationRules
import com.timachado.brothermatrizes.font.FontCatalogSource
import com.timachado.brothermatrizes.font.NetFontesNavigationRules
import java.awt.Desktop
import java.net.URI

/**
 * Faithful Windows adapter for the two Android font catalog searches.
 *
 * Android requires a verified Google account before entering either
 * catalog, and server-side reserve/finalize of "dafont_search" for a DaFont
 * text search. NetFontes searches need Google but do not use this quota.
 *
 * Never scrape either website, download files without an explicit user
 * action, import a font, or trust local Pro/entitlement information.
 */
internal interface DesktopFontCatalogAccess {
    suspend fun hasVerifiedGoogleSession(): Boolean
    suspend fun reserveDaFontSearch(): String
    suspend fun finalizeDaFontSearch(requestKey: String, success: Boolean)
}

/** Fail closed until real Windows Google OAuth/cotas are homologated. */
internal object DesktopFontCatalogAccessUnavailable : DesktopFontCatalogAccess {
    override suspend fun hasVerifiedGoogleSession(): Boolean = false
    override suspend fun reserveDaFontSearch(): String =
        error("Pesquisa DaFont exige autorização do Supabase Brother Matrizes.")
    override suspend fun finalizeDaFontSearch(requestKey: String, success: Boolean): Unit =
        error("Não existe cota DaFont autorizada nesta sessão Windows.")
}

internal object DesktopFontCatalogSearch {
    data class Opened(
        val catalog: FontCatalogSource,
        val destination: URI,
        val quotaConfirmationReceived: Boolean
    )

    suspend fun browse(
        source: FontCatalogSource,
        query: String,
        access: DesktopFontCatalogAccess = DesktopFontCatalogAccessUnavailable,
        openBrowser: (URI) -> Unit = ::openSystemBrowser
    ): Opened {
        // Same 90-character trimming used by the Android official catalogs.
        // Do not allow the input to choose arbitrary destinations.
        val search = query.trim().take(90)
        check(access.hasVerifiedGoogleSession()) {
            "Entre com sua conta Google em Minha Conta antes de pesquisar fontes."
        }

        val destination = when (source) {
            FontCatalogSource.DAFONT ->
                if (search.isEmpty()) DaFontNavigationRules.HOME
                else DaFontNavigationRules.searchUrl(search)
            FontCatalogSource.NETFONTES ->
                if (search.isEmpty()) NetFontesNavigationRules.HOME
                else NetFontesNavigationRules.searchUrl(search)
        }
        check(source.isOfficialPage(destination)) {
            "A busca não corresponde a um domínio oficial autorizado."
        }
        val uri = URI(destination)

        // A DaFont text search is a metered operation in Android, but
        // browsing its home page and every NetFontes search are not.
        if (source != FontCatalogSource.DAFONT || search.isEmpty()) {
            openBrowser(uri)
            return Opened(source, uri, quotaConfirmationReceived = true)
        }

        // No browser navigation before the server authorizes the request.
        val requestKey = access.reserveDaFontSearch()
        check(requestKey.matches(
            Regex("(usage|pro)_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        )) { "A reserva de pesquisa do DaFont não foi validada." }
        var opened = false
        try {
            openBrowser(uri)
            opened = true
        } finally {
            // Android sends finalize(success=false) when opening fails.
            // Never auto-retry a reserved operation and double charge.
            access.finalizeDaFontSearch(requestKey, opened)
        }
        return Opened(source, uri, quotaConfirmationReceived = true)
    }

    private fun openSystemBrowser(uri: URI) {
        check(uri.scheme == "https" && Desktop.isDesktopSupported()) {
            "Navegador externo seguro indisponível."
        }
        val desktop = Desktop.getDesktop()
        check(desktop.isSupported(Desktop.Action.BROWSE)) {
            "Abrir navegador não é permitido neste computador."
        }
        desktop.browse(uri)
    }
}
