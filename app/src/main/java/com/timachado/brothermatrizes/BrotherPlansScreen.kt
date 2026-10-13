package com.timachado.brothermatrizes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.account.AccountPlanCatalog
import com.timachado.brothermatrizes.core.account.AccountPlanOption
import com.timachado.brothermatrizes.core.account.AccountPresentation
import com.timachado.brothermatrizes.core.account.AccountSnapshot
import com.timachado.brothermatrizes.core.account.BrotherLaunchOfferCatalog
import com.timachado.brothermatrizes.core.account.BrotherLaunchPriceDisplay
import com.timachado.brothermatrizes.core.account.BrotherPlanBenefits
import java.text.NumberFormat
import java.util.Locale

/** Apresentação comercial somente. Nenhuma compra, retorno de navegador ou seleção
 * visual concede acesso: a licença continua verificada no backend original.
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
    var lifetimeOption by remember { mutableStateOf("pro_lifetime") }
    var expandedComparison by remember { mutableStateOf(false) }
    var showFullComparison by remember { mutableStateOf(false) }
    var expandedLifetime by remember { mutableStateOf(false) }
    var faqOpen by remember { mutableStateOf(false) }
    var questionOpen by remember { mutableStateOf<String?>(null) }
    // Keep the pricing scroll offset when switching to the full comparison.
    val pricingScrollState = rememberScrollState()
    val comparisonScrollState = rememberScrollState()
    BackHandler(enabled = showFullComparison) { showFullComparison = false }
    // Public, read-only WooCommerce Store API. Checkout remains authoritative.
    var launchAvailability by remember {
        mutableStateOf<BrotherLaunchOfferCatalog.Status>(
            BrotherLaunchOfferCatalog.Status.Unknown
        )
    }
    LaunchedEffect(Unit) {
        launchAvailability = BrotherLaunchOfferCatalog.fetch()
    }
    LaunchedEffect(launchAvailability) {
        if (launchAvailability is BrotherLaunchOfferCatalog.Status.Unavailable)
            lifetimeOption = "pro_lifetime"
    }
    val plans = AccountPlanCatalog.forDisplay(account?.availablePlans ?: emptyList())
    val byCode = plans.associateBy { it.code }
    val serverConfirmed = account?.commercialConfigured == true
    val accessPro = serverConfirmed && account?.hasProAccess == true
    val lifetime = accessPro && account?.isLifetime == true
    val trial = accessPro && account?.planCode == "trial"
    val current = if (accessPro) account?.planCode else null

    Column(Modifier.fillMaxSize().background(BrotherAccountNavy).padding(horizontal = 17.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = if (showFullComparison) {
                { showFullComparison = false }
            } else onBack) {
                Text("‹ Voltar", color = BrotherAccountGold, fontSize = 13.sp)
            }
        }
        if (showFullComparison) {
            Text("Comparativo completo", color = BrotherAccountWhite,
                fontWeight = FontWeight.Bold, fontSize = 23.sp,
                modifier = Modifier.padding(vertical = 12.dp))
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(comparisonScrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Genuine access differences first, shared tools afterwards.
                BrotherPlanBenefits.comparison.sortedWith(
                    compareByDescending<BrotherPlanBenefits.Comparison> {
                        it.free != it.pro
                    }
                ).forEach { item ->
                    Surface(
                        color = BrotherAccountSurface, shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(13.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.name, color = BrotherAccountWhite,
                                fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("GRATUITO  •  " + item.free,
                                color = BrotherAccountMuted, fontSize = 11.sp)
                            Text("PRO  •  " + item.pro,
                                color = BrotherAccountGold, fontSize = 11.sp)
                        }
                    }
                }
                Text(
                    "A conversão EMB/PES e os programas Beta permanecem em desenvolvimento.",
                    color = BrotherAccountMuted, fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 25.dp)
                )
            }
        } else {
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(pricingScrollState),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Text("BROTHER MATRIZES", color = BrotherAccountGold,
                    letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Planos e benefícios", modifier = Modifier.weight(1f),
                        color = BrotherAccountWhite,
                        fontWeight = FontWeight.Bold, fontSize = 23.sp)
                    BrotherAccountGlyph("crown", Modifier.size(28.dp))
                }
                Text("Escolha como aproveitar o Brother Matrizes.",
                    color = BrotherAccountMuted, fontSize = 12.sp)

                if (serverConfirmed && accessPro) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = BrotherAccountGold.copy(alpha = .12f),
                        shape = RoundedCornerShape(15.dp),
                        border = BorderStroke(1.dp, BrotherAccountGold.copy(alpha = .28f))
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("SEU PLANO ATUAL", color = BrotherAccountGold,
                                    fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                                Text(
                                    if (trial) "Teste PRO ativo"
                                    else if (lifetime) "PRO Vitalício"
                                    else AccountPresentation.planLabel(account?.planCode ?: ""),
                                    color = BrotherAccountWhite, fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text("Ativo", color = BrotherAccountGold, fontSize = 11.sp)
                        }
                    }
                }

                PremiumPlanCard(
                    label = "COMECE A CRIAR",
                    title = "Gratuito",
                    price = "Grátis",
                    description = "Crie e edite, sem precisar assinar.",
                    highlights = listOf("Biblioteca e projetos locais", "Até 3 fontes e 5 matrizes por mês")
                ) {
                    if (serverConfirmed && !accessPro && account?.trialStatus == "eligible") {
                        PremiumPlanAction("Ativar 7 dias grátis", onActivateTrial)
                    } else {
                        Text(
                            if (trial) "Você já está aproveitando o teste PRO."
                            else "Teste PRO por 7 dias disponível para contas elegíveis.",
                            color = BrotherAccountMuted, fontSize = 11.sp
                        )
                    }
                }

                PremiumPlanCard(
                    label = "MAIS PRODUTIVIDADE",
                    title = "PRO",
                    price = marketingPrice(byCode[proOption], proOption),
                    description = "Mais liberdade para suas importações.",
                    highlights = listOf("Recursos PRO incluídos", "Importações sem a cota do Gratuito")
                ) {
                    PremiumPlanSelector(
                        options = listOf("Mensal" to "pro_monthly", "Anual" to "pro_yearly"),
                        selected = proOption,
                        onSelect = { proOption = it }
                    )
                    if (proOption == "pro_yearly") {
                        BrotherPlanBenefits.yearlySavings(
                            listedPrice(byCode["pro_monthly"], "pro_monthly"),
                            listedPrice(byCode["pro_yearly"], "pro_yearly")
                        )?.let { saving ->
                            Text(
                                "Economize " + marketingMoney(saving) + " frente a 12 mensalidades*",
                                color = BrotherAccountGold,
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (current == proOption) {
                        Text("Seu plano atual", color = BrotherAccountGold, fontSize = 12.sp)
                    } else if (lifetime) {
                        Text("Seu Vitalício já inclui esse acesso PRO.",
                            color = BrotherAccountGold, fontSize = 11.sp)
                    } else {
                        PremiumPlanAction(
                            if (proOption == "pro_monthly") "Conhecer PRO Mensal"
                            else "Conhecer PRO Anual"
                        ) { onOpenPlan(proOption) }
                    }
                }

                PremiumPlanCard(
                    label = "SEM MENSALIDADES",
                    title = "PRO Vitalício",
                    price = if (lifetimeOption == "pro_lifetime_launch") {
                        when (val available = launchAvailability) {
                            is BrotherLaunchOfferCatalog.Status.Available ->
                                marketingMoney(available.priceCents)
                            else -> "Confirmar no site"
                        }
                    } else marketingPrice(byCode[lifetimeOption], lifetimeOption),
                    description = "Invista uma vez nos recursos PRO incluídos.",
                    highlights = listOf(
                        "Pagamento único",
                        "Mesma categoria PRO do plano mensal e anual"
                    ),
                    featured = true
                ) {
                    PremiumPlanSelector(
                        options = if (
                            launchAvailability is BrotherLaunchOfferCatalog.Status.Unavailable
                        ) listOf("Vitalício" to "pro_lifetime") else listOf(
                            "Vitalício" to "pro_lifetime",
                            "Lançamento" to "pro_lifetime_launch"
                        ),
                        selected = lifetimeOption,
                        onSelect = { lifetimeOption = it }
                    )
                    when (launchAvailability) {
                        is BrotherLaunchOfferCatalog.Status.Available -> {
                            if (lifetimeOption == "pro_lifetime_launch") {
                                Text(
                                    "Oferta disponível na última consulta. O checkout confirma o estoque final.",
                                    color = BrotherAccountMuted, fontSize = 11.sp
                                )
                            }
                        }
                        BrotherLaunchOfferCatalog.Status.Unavailable -> Text(
                            "A edição de lançamento está indisponível no WooCommerce.",
                            color = BrotherAccountMuted, fontSize = 11.sp
                        )
                        BrotherLaunchOfferCatalog.Status.Unknown -> {
                            if (lifetimeOption == "pro_lifetime_launch") {
                                Text(
                                    "Estoque não confirmado neste aparelho. Consulte a oferta na loja.",
                                    color = BrotherAccountMuted, fontSize = 11.sp
                                )
                            }
                        }
                    }
                    if (lifetime) {
                        Text("Sua licença já oferece esse acesso.",
                            color = BrotherAccountGold, fontSize = 12.sp)
                    } else {
                        PremiumPlanAction(
                            if (lifetimeOption == "pro_lifetime_launch") "Conferir oferta especial"
                            else "Conhecer PRO Vitalício"
                        ) { onOpenPlan(lifetimeOption) }
                    }
                }

                Text(
                    "*Preços informativos. Estoque, renovação e pagamento são confirmados " +
                        "no checkout oficial. Nenhum acesso é liberado sem confirmação do servidor.",
                    color = BrotherAccountMuted, fontSize = 10.sp
                )
                PremiumPlansFold(
                    title = "Comparar funcionalidades",
                    subtitle = "Veja o que muda de Gratuito para PRO",
                    expanded = expandedComparison,
                    onToggle = { expandedComparison = !expandedComparison }
                ) {
                    BrotherPlanBenefits.comparison
                        .filter { it.free != it.pro }
                        .take(4)
                        .forEach { item ->
                        Column(Modifier.padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(item.name, color = BrotherAccountWhite,
                                fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            Text("Gratuito: " + item.free,
                                color = BrotherAccountMuted, fontSize = 10.sp)
                            Text("PRO: " + item.pro,
                                color = BrotherAccountGold, fontSize = 10.sp)
                        }
                    }
                    Text(
                        "Criação, biblioteca e edição continuam disponíveis também no Gratuito.",
                        color = BrotherAccountMuted, fontSize = 11.sp
                    )
                    TextButton(onClick = { showFullComparison = true }) {
                        Text("Ver comparação completa  ›", color = BrotherAccountGold)
                    }
                }
                PremiumPlansFold(
                    title = "Por que escolher o Vitalício?",
                    subtitle = "Recursos PRO sem mensalidades",
                    expanded = expandedLifetime,
                    onToggle = { expandedLifetime = !expandedLifetime }
                ) {
                    Text(
                        "A licença de lançamento oferece os mesmos direitos PRO do " +
                            "Vitalício convencional. Outros aplicativos e serviços pagos " +
                            "não estão incluídos automaticamente.",
                        color = BrotherAccountMuted, fontSize = 11.sp
                    )
                    Text(
                        "Programas Beta e acesso antecipado são possibilidades futuras, " +
                            "ainda sem garantia contratual e sujeitos a regras e vagas.",
                        color = BrotherAccountMuted, fontSize = 11.sp
                    )
                }
                PremiumPlansFold(
                    title = "Perguntas frequentes",
                    subtitle = "Teste grátis, cobranças e licenças",
                    expanded = faqOpen,
                    onToggle = { faqOpen = !faqOpen }
                ) {
                    BrotherPlanBenefits.questions.forEach { (question, answer) ->
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                questionOpen = if (questionOpen == question) null else question
                            }.padding(vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(question + if (questionOpen == question) "  −" else "  +",
                                color = BrotherAccountWhite,
                                fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            if (questionOpen == question) {
                                Text(answer, color = BrotherAccountMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
                TextButton(onClick = onOpenWebAccount, modifier = Modifier.fillMaxWidth()) {
                    Text("Minhas compras e licenças", color = BrotherAccountGold)
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun PremiumPlanCard(
    label: String,
    title: String,
    price: String,
    description: String,
    highlights: List<String>,
    featured: Boolean = false,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BrotherAccountSurface,
        shape = RoundedCornerShape(22.dp),
        border = if (featured) BorderStroke(1.dp, BrotherAccountGold) else null
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, color = BrotherAccountGold, fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = .8.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), color = BrotherAccountWhite,
                    fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(price, color = BrotherAccountGold, fontSize = 16.sp,
                    fontWeight = FontWeight.Bold)
            }
            Text(description, color = BrotherAccountMuted, fontSize = 11.sp)
            highlights.forEach { text ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("✓", color = BrotherAccountGold, fontSize = 12.sp)
                    Text(text, color = BrotherAccountWhite, fontSize = 11.sp)
                }
            }
            content()
        }
    }
}

@Composable
private fun PremiumPlanSelector(
    options: List<Pair<String, String>>, selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(BrotherAccountNavy, RoundedCornerShape(13.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        options.forEach { (label, value) ->
            val active = selected == value
            Box(
                modifier = Modifier.weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) BrotherAccountGold else BrotherAccountNavy)
                    .border(
                        if (active) 0.dp else 1.dp,
                        if (active) BrotherAccountGold else BrotherAccountBorder,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label,
                    color = if (active) BrotherAccountNavy else BrotherAccountMuted,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PremiumPlanAction(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(46.dp),
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = BrotherAccountGold, contentColor = BrotherAccountNavy
        ),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun PremiumPlansFold(
    title: String, subtitle: String, expanded: Boolean, onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BrotherAccountSurface, shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle)
                .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = BrotherAccountWhite,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(subtitle, color = BrotherAccountMuted, fontSize = 11.sp)
                }
                Text(if (expanded) "⌄" else "›", color = BrotherAccountGold, fontSize = 23.sp)
            }
            if (expanded) content()
        }
    }
}

private fun listedPrice(plan: AccountPlanOption?, code: String): Int? =
    plan?.priceCents ?: BrotherLaunchPriceDisplay.priceCents(code)

private fun marketingPrice(plan: AccountPlanOption?, code: String): String {
    val value = listedPrice(plan, code) ?: return "Consultar"
    return marketingMoney(value) + when (code) {
        "pro_monthly" -> "/mês"
        "pro_yearly" -> "/ano"
        else -> ""
    }
}

private fun marketingMoney(cents: Int): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
        .format(cents / 100.0)
