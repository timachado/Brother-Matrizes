package com.timachado.brothermatrizes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.account.AccountPlanCatalog
import com.timachado.brothermatrizes.core.account.AccountPlanOption
import com.timachado.brothermatrizes.core.account.AccountSnapshot
import com.timachado.brothermatrizes.core.account.BrotherLaunchPriceDisplay
import com.timachado.brothermatrizes.core.account.BrotherPlanBenefits
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import java.text.NumberFormat
import java.util.Locale

/**
 * Vitrine editorial. NUNCA valida pagamentos nem libera recursos PRO.
 * Os direitos reais vêm de AccountSnapshot, verificado no servidor.
 */
@Composable
internal fun BrotherPlansScreen(
    account: AccountSnapshot?,
    onBack: () -> Unit,
    onActivateTrial: () -> Unit,
    onOpenPlan: (String) -> Unit,
    onOpenWebAccount: () -> Unit
) {
    var proOption by remember { mutableStateOf("pro_monthly") }
    var lifeOption by remember { mutableStateOf("pro_lifetime") }
    var compareOpen by remember { mutableStateOf(false) }
    var faqOpen by remember { mutableStateOf(false) }
    var lifeInfoOpen by remember { mutableStateOf(false) }
    var selectedQuestion by remember { mutableStateOf<String?>(null) }
    val plans = AccountPlanCatalog.forDisplay(account?.availablePlans ?: emptyList())
    val planByCode = plans.associateBy { it.code }
    val verified = account?.commercialConfigured == true
    val proActive = verified && account?.hasProAccess == true
    val isLifetimeActive = proActive && account?.isLifetime == true
    val currentCode = if (proActive) account?.planCode else null

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹ Conta", color = FioGold) }
            Column(Modifier.weight(1f)) {
                Text("Planos e benefícios", color = FioText,
                    fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text("Escolha o que faz sentido para você",
                    color = FioTextMuted, fontSize = 10.sp)
            }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (verified && proActive) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = FioGold.copy(alpha = .10f)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("SEU ACESSO", color = FioGold, fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                account?.planCode == "trial" -> "Teste PRO ativo"
                                account?.isLifetime == true -> "PRO Vitalício ativo"
                                else -> "PRO ativo"
                            },
                            color = FioText, fontWeight = FontWeight.Bold, fontSize = 15.sp
                        )
                        Text(
                            "Os recursos incluídos na sua licença já estão disponíveis. " +
                                "Você não precisa contratar novamente o mesmo acesso.",
                            color = FioTextMuted, fontSize = 11.sp
                        )
                    }
                }
            }

            // 3 decisões, não 5 grandes cards. O selecionador preserva todos
            // os códigos e os quatro destinos originais do WooCommerce.
            CompactOfferCard(
                title = "Gratuito", eyebrow = "COMECE A CRIAR",
                price = "Grátis",
                subtitle = "Crie, edite e salve seus projetos.",
                highlights = BrotherPlanBenefits.cardHighlights("free").take(2)
            ) {
                if (verified && account?.trialStatus == "eligible" && !proActive) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onActivateTrial,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FioGold, contentColor = FioBackground
                        )
                    ) { Text("Ativar 7 dias PRO grátis") }
                } else {
                    Text(
                        if (account?.trialStatus == "active") "Seu teste PRO está ativo."
                        else "O teste PRO de 7 dias depende de elegibilidade na conta.",
                        color = FioTextMuted, fontSize = 10.sp
                    )
                }
            }
            CompactOfferCard(
                title = "PRO", eyebrow = "MAIS PRODUTIVIDADE",
                price = planPrice(planByCode[proOption], proOption),
                subtitle = "Importe fontes e matrizes sem a cota do Gratuito.",
                highlights = listOf("Recursos PRO incluídos", "Escolha sua periodicidade")
            ) {
                PlanModeSelector(
                    firstLabel = "Mensal", secondLabel = "Anual",
                    firstSelected = proOption == "pro_monthly",
                    onFirst = { proOption = "pro_monthly" },
                    onSecond = { proOption = "pro_yearly" }
                )
                if (proOption == "pro_yearly") {
                    BrotherPlanBenefits.yearlySavings(
                        effectiveCents(planByCode["pro_monthly"], "pro_monthly"),
                        effectiveCents(planByCode["pro_yearly"], "pro_yearly")
                    )?.let { saving ->
                        Text(
                            "Economia de referência: " + money(saving) + " frente a 12 mensalidades.",
                            color = FioGold, fontSize = 10.sp
                        )
                    }
                }
                if (currentCode == proOption) {
                    Text("Seu plano atual", color = FioGold, fontSize = 11.sp)
                } else if (isLifetimeActive) {
                    Text("Seu Vitalício já inclui os recursos PRO.",
                        color = FioGold, fontSize = 11.sp)
                } else {
                    PlanPurchaseButton("Conferir assinatura no site") {
                        onOpenPlan(proOption)
                    }
                }
            }
            CompactOfferCard(
                title = "PRO Vitalício", eyebrow = "PAGAMENTO ÚNICO",
                price = planPrice(planByCode[lifeOption], lifeOption),
                subtitle = "Recursos PRO cobertos pela licença, sem mensalidades.",
                highlights = listOf("Mesmo acesso PRO das assinaturas", "Pagamento único")
            ) {
                PlanModeSelector(
                    firstLabel = "Vitalício", secondLabel = "Lançamento",
                    firstSelected = lifeOption == "pro_lifetime",
                    onFirst = { lifeOption = "pro_lifetime" },
                    onSecond = { lifeOption = "pro_lifetime_launch" }
                )
                if (lifeOption == "pro_lifetime_launch") {
                    Text(
                        "Oferta promocional limitada. Estoque e preço confirmados no WooCommerce.",
                        color = FioTextMuted, fontSize = 10.sp
                    )
                }
                if (isLifetimeActive) {
                    Text("Sua licença vitalícia já garante esse acesso.",
                        color = FioGold, fontSize = 11.sp)
                } else {
                    PlanPurchaseButton(
                        if (lifeOption == "pro_lifetime_launch")
                            "Conferir oferta no site" else "Conhecer Vitalício"
                    ) { onOpenPlan(lifeOption) }
                }
            }

            Text(
                "Preços informativos. O site confirma preço, estoque, renovação e pagamento. " +
                    "O acesso PRO só é liberado após confirmação segura do servidor.",
                color = FioTextMuted, fontSize = 10.sp
            )

            FoldedSection(
                title = "Comparar funcionalidades",
                subtitle = "Gratuito x PRO",
                open = compareOpen,
                onToggle = { compareOpen = !compareOpen }
            ) {
                BrotherPlanBenefits.comparison.forEach { item ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(item.name, color = FioText, fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp)
                        Text("Gratuito: " + item.free, color = FioTextMuted, fontSize = 10.sp)
                        Text("PRO: " + item.pro, color = FioGold, fontSize = 10.sp)
                    }
                }
            }
            FoldedSection(
                title = "Por que escolher o Vitalício?",
                subtitle = "Sem mensalidades recorrentes",
                open = lifeInfoOpen,
                onToggle = { lifeInfoOpen = !lifeInfoOpen }
            ) {
                Text(
                    "O Vitalício de Lançamento oferece a mesma categoria PRO do convencional. " +
                        "Nenhum deles inclui automaticamente outros aplicativos e serviços separados.",
                    color = FioTextMuted, fontSize = 11.sp
                )
                Text(
                    "Acesso antecipado e testes Beta são possibilidades futuras, com regras " +
                        "elegibilidade e vagas próprias; ainda não são benefícios contratados.",
                    color = FioTextMuted, fontSize = 11.sp
                )
            }
            FoldedSection(
                title = "Dúvidas frequentes",
                subtitle = "Teste, pagamentos, licenças e suporte",
                open = faqOpen,
                onToggle = { faqOpen = !faqOpen }
            ) {
                BrotherPlanBenefits.questions.forEach { (question, answer) ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable {
                                selectedQuestion = if (selectedQuestion == question) null else question
                            }
                            .padding(vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(question + if (selectedQuestion == question) " −" else " +",
                            color = FioText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        if (selectedQuestion == question) {
                            Text(answer, color = FioTextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
            TextButton(onClick = onOpenWebAccount, modifier = Modifier.fillMaxWidth()) {
                Text("Minhas compras e licenças", color = FioGold)
            }
        }
    }
}

@Composable
private fun CompactOfferCard(
    title: String, eyebrow: String, price: String, subtitle: String,
    highlights: List<String>, content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FioSurface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(eyebrow, color = FioGold, fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), color = FioText,
                    fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(price, color = FioText, fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp)
            }
            Text(subtitle, color = FioTextMuted, fontSize = 11.sp)
            highlights.forEach { item ->
                Text("✓ " + item, color = FioTextMuted, fontSize = 11.sp)
            }
            content()
        }
    }
}

@Composable
private fun PlanModeSelector(
    firstLabel: String, secondLabel: String, firstSelected: Boolean,
    onFirst: () -> Unit, onSecond: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(modifier = Modifier.weight(1f), onClick = onFirst) {
            Text((if (firstSelected) "● " else "") + firstLabel,
                color = if (firstSelected) FioGold else FioTextMuted, fontSize = 11.sp)
        }
        OutlinedButton(modifier = Modifier.weight(1f), onClick = onSecond) {
            Text((if (!firstSelected) "● " else "") + secondLabel,
                color = if (!firstSelected) FioGold else FioTextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PlanPurchaseButton(text: String, onClick: () -> Unit) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = FioGold, contentColor = FioBackground
        ),
        onClick = onClick
    ) { Text(text) }
}

@Composable
private fun FoldedSection(
    title: String, subtitle: String,
    open: Boolean, onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = FioSurface)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = FioText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(subtitle, color = FioTextMuted, fontSize = 10.sp)
                }
                TextButton(onClick = onToggle) {
                    Text(if (open) "Fechar" else "Ver", color = FioGold)
                }
            }
            if (open) content()
        }
    }
}

private fun effectiveCents(plan: AccountPlanOption?, code: String): Int? =
    plan?.priceCents ?: BrotherLaunchPriceDisplay.priceCents(code)

private fun planPrice(plan: AccountPlanOption?, code: String): String {
    val cents = effectiveCents(plan, code) ?: return "Consultar site"
    val suffix = when (code) {
        "pro_monthly" -> "/mês"
        "pro_yearly" -> "/ano"
        else -> ""
    }
    return money(cents) + suffix
}

private fun money(cents: Int): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(cents / 100.0)
