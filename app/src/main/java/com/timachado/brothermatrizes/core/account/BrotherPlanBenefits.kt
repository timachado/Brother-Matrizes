package com.timachado.brothermatrizes.core.account

/**
 * Texto de apresentação, NUNCA autoridade de licenciamento.
 *
 * Conferido contra os endpoints brother-entitlement / brother-usage, telas
 * Android e catálogo comercial público WooCommerce em 13/10/2026.
 * A contratação e a liberação de acesso continuam no servidor existente.
 */
internal object BrotherPlanBenefits {
    data class Comparison(
        val name: String,
        val free: String,
        val pro: String
    )

    /** Economia editorial; preços precisam vir do catálogo consultado e do checkout. */
    fun yearlySavings(monthlyCents: Int?, yearlyCents: Int?): Int? {
        if (monthlyCents == null || yearlyCents == null ||
            monthlyCents <= 0 || yearlyCents <= 0) return null
        val savings = monthlyCents.toLong() * 12 - yearlyCents.toLong()
        return savings.takeIf { it > 0 && it <= Int.MAX_VALUE }?.toInt()
    }

    fun headline(code: String): String = when (code) {
        "free" -> "Comece a criar"
        "pro_monthly" -> "Potencialize seu trabalho"
        "pro_yearly" -> "Mais economia e produtividade"
        "pro_lifetime" -> "Liberdade sem mensalidades"
        "pro_lifetime_launch" -> "Condição especial de lançamento"
        else -> "Conheça os recursos incluídos"
    }

    fun audience(code: String): String = when (code) {
        "free" -> "Indicado para conhecer o aplicativo e realizar trabalhos ocasionais."
        "pro_monthly" -> "Para quem usa o Brother Matrizes com frequência e prefere contratação mensal."
        "pro_yearly" -> "Para quem trabalha o ano inteiro e busca economia na contratação."
        "pro_lifetime", "pro_lifetime_launch" ->
            "Para quem deseja usar os recursos PRO cobertos pela licença sem mensalidades recorrentes."
        else -> "Consulte as condições do plano."
    }

    fun billing(code: String): String = when (code) {
        "free" -> "Sem cobrança. Teste PRO de sete dias quando elegível, mediante ativação."
        "pro_monthly" -> "Assinatura mensal; renovação, cancelamento e meios de pagamento no site."
        "pro_yearly" -> "Assinatura anual; confirme renovação e pagamento no checkout."
        "pro_lifetime", "pro_lifetime_launch" ->
            "Pagamento único. Sem renovação mensal desta licença; produtos e serviços separados não estão incluídos automaticamente."
        else -> "Confira as condições de cobrança no checkout."
    }

    fun benefits(code: String): List<String> {
        val base = listOf(
            "Criação e edição de matrizes no aplicativo",
            "Biblioteca e projetos salvos localmente",
            "Importação de fontes e matrizes dentro das condições do plano",
            "Atualizações oficiais e canal de feedback"
        )
        if (code == "free") return base + listOf(
            "Até 3 importações de fontes por mês na conta gratuita",
            "Até 5 importações de matrizes por mês na conta gratuita",
            "7 dias de teste PRO, uma única vez quando elegível"
        )
        val paid = base + listOf(
            "Importações de fontes e matrizes sem a cota mensal do plano gratuito",
            "Recursos PRO ativos enquanto houver licença confirmada pelo servidor"
        )
        return if (code == "pro_lifetime" || code == "pro_lifetime_launch") {
            paid + "Acesso PRO coberto pela licença, sem mensalidade recorrente"
        } else paid
    }

    fun caution(code: String): String = when (code) {
        "free" -> "O acesso PRO de teste é temporário. Ao terminar, projetos locais permanecem salvos."
        "pro_lifetime_launch" ->
            "Mesma categoria de acesso PRO do Vitalício convencional. Oferta e estoque devem ser confirmados no WooCommerce."
        "pro_lifetime" ->
            "Não inclui automaticamente aplicativos independentes, serviços externos ou futuros produtos pagos."
        else -> "O acesso depende de uma licença válida e verificada no servidor."
    }

    val comparison: List<Comparison> = listOf(
        Comparison("Criar e editar trabalhos", "Disponível", "Disponível"),
        Comparison("Biblioteca local de projetos", "Disponível", "Disponível"),
        Comparison("Importação de fontes", "Até 3/mês", "Sem cota mensal do Gratuito"),
        Comparison("Importação de matrizes", "Até 5/mês", "Sem cota mensal do Gratuito"),
        Comparison("Abrir arquivos e utilizar ferramentas de edição", "Recursos básicos do app", "Recursos do app + acesso PRO validado"),
        Comparison("Atualizações publicadas", "Disponíveis", "Disponíveis"),
        Comparison("Conversão EMB ↔ PES", "Em desenvolvimento", "Em desenvolvimento")
    )

    val questions: List<Pair<String, String>> = listOf(
        "O aplicativo é gratuito?" to
            "O download e o plano Gratuito são gratuitos. Algumas operações têm cotas mensais.",
        "Como funcionam os sete dias grátis?" to
            "A conta elegível pode ativar uma única vez o teste PRO, sem cartão e sem cobrança automática. O prazo é calculado pelo servidor.",
        "O que acontece depois do teste?" to
            "A conta retorna ao acesso Gratuito, mantendo os projetos armazenados localmente.",
        "Mensal e Anual têm os mesmos recursos?" to
            "Ambos recebem a categoria de acesso PRO validada pelo servidor. A principal diferença é a periodicidade de pagamento.",
        "O vitalício tem mensalidade?" to
            "Não. A licença contratada é de pagamento único. Seu alcance é o conjunto de recursos coberto pelo produto adquirido.",
        "Como funcionam atualizações e testes Beta?" to
            "Atualizações oficiais estão disponíveis pelo canal do aplicativo. Acesso antecipado e Beta são possibilidades futuras, sujeitas a regras e vagas; não são direitos contratados hoje.",
        "Como cancelar ou recuperar uma compra?" to
            "Acesse Minhas compras e licenças no site ou fale com o suporte. A licença deve ser confirmada pelo servidor, usando a conta vinculada à compra.",
        "Quais são as formas de pagamento?" to
            "As opções efetivas de pagamento, renovação e parcelamento são apresentadas no checkout oficial."
    )
}
