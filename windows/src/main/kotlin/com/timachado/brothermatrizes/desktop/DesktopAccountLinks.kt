package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.account.BrotherPlanBenefits
import com.timachado.brothermatrizes.core.account.WordPressWebStore
import java.awt.Desktop
import java.net.URI

/**
 * Etapa pública da Conta para Windows QA.
 *
 * Não faz login, não importa sessões Android/Supabase/WooCommerce,
 * não cria compras ou licenças e NUNCA concede acesso PRO.
 * A autenticação e o catálogo comercial continuam exclusivamente
 * no navegador e no servidor oficial. Toda URL é criada pela rotina
 * WordPressWebStore do Android, não vem de texto de rede/usuário.
 */
internal object DesktopAccountLinks {
    private const val SITE = "https://timachado.ifree.page"

    internal data class Plan(
        val code: String,
        val title: String,
        val headline: String,
        val billing: String
    )

    val plans: List<Plan> = listOf(
        "pro_monthly" to "Pro Mensal",
        "pro_yearly" to "Pro Anual",
        "pro_lifetime" to "Pro Vitalício",
        "pro_lifetime_launch" to "Vitalício Lançamento"
    ).map { (code, title) ->
        Plan(
            code = code,
            title = title,
            headline = BrotherPlanBenefits.headline(code),
            billing = BrotherPlanBenefits.billing(code)
        )
    }

    /** Não equivale a login ou sessão da aplicação desktop. */
    val accountStatus = "Não vinculada no Windows QA"
    val proAccessVerified: Boolean = false

    fun account(): URI = URI.create(WordPressWebStore.accountUrl(SITE))

    fun plan(code: String): URI {
        require(plans.any { it.code == code }) {
            "Plano não pertence ao catálogo oficial autorizado."
        }
        return URI.create(WordPressWebStore.planUrl(SITE, code))
    }

    /** Não aceita links arbitrários, parâmetros novos nem redirect URI. */
    fun isAllowed(url: URI): Boolean {
        if (!url.scheme.equals("https", ignoreCase = true) ||
            !url.host.equals("timachado.ifree.page", ignoreCase = true) ||
            url.port != -1 || url.userInfo != null || url.fragment != null
        ) return false
        val known = listOf(account()) + plans.map { plan(it.code) }
        return known.any { it.toString() == url.toString() }
    }

    /** Clique explícito abre somente a página oficial no navegador externo. */
    fun openByUserAction(url: URI) {
        require(isAllowed(url)) {
            "Acesso externo à Conta não autorizado."
        }
        check(Desktop.isDesktopSupported()) {
            "O ambiente Windows não disponibiliza navegação externa."
        }
        val d = Desktop.getDesktop()
        check(d.isSupported(Desktop.Action.BROWSE)) {
            "Nenhum navegador padrão foi encontrado."
        }
        d.browse(url)
    }
}
