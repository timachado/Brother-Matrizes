package com.timachado.brothermatrizes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.account.AccountPresentation
import com.timachado.brothermatrizes.core.account.AccountSnapshot
import com.timachado.brothermatrizes.core.account.BrotherMatrizesAccountService
import com.timachado.brothermatrizes.core.account.BrotherUsageClient

/**
 * Direitos exibidos de acordo com licença verificada. Esta UI nunca concede
 * acesso, não consulta histórico privado extra e não inventa benefícios Beta.
 */
@Composable
internal fun BrotherMyBenefitsScreen(
    account: AccountSnapshot,
    onBack: () -> Unit,
    onOpenPlans: () -> Unit
) {
    BackHandler(onBack = onBack)
    val pro = account.commercialConfigured && account.hasProAccess
    val lifetime = pro && account.isLifetime
    val isTrial = pro && account.planCode.equals("trial", ignoreCase = true)
    var monthlyUsage by remember(account.userId) {
        mutableStateOf<List<BrotherUsageClient.ToolUsage>?>(null)
    }
    var quotaError by remember(account.userId) { mutableStateOf(false) }
    LaunchedEffect(account.userId, pro) {
        if (!pro && account.commercialConfigured) {
            val result = BrotherMatrizesAccountService.toolUsage()
            monthlyUsage = result.getOrNull()
            quotaError = result.isFailure
        }
    }
    val plan = when {
        !account.commercialConfigured -> "Licença pendente de verificação"
        lifetime -> "PRO Vitalício"
        isTrial -> "Teste PRO"
        pro -> AccountPresentation.planLabel(account.planCode)
        else -> "Gratuito"
    }
    val entitlements = if (pro) listOf(
        "Crie e edite seus trabalhos de bordado",
        "Organize seus projetos na biblioteca local",
        "Importe fontes e matrizes sem a cota mensal do Gratuito",
        "Acompanhe atualizações oficiais e envie feedbacks"
    ) else listOf(
        "Crie e edite seus trabalhos de bordado",
        "Organize seus projetos na biblioteca local",
        "Até 3 importações de fontes por mês",
        "Até 5 importações de matrizes por mês"
    )
    Column(
        Modifier.fillMaxSize().background(BrotherAccountNavy).padding(horizontal = 17.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            TextButton(onClick = onBack) {
                Text("‹ Minha Conta", color = BrotherAccountGold, fontSize = 13.sp)
            }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("BROTHER MATRIZES", color = BrotherAccountGold,
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text("Meus benefícios", color = BrotherAccountWhite,
                fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Surface(
                color = BrotherAccountSurface, shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("ACESSO DA SUA CONTA", color = BrotherAccountGold,
                        fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                    Text(plan, color = BrotherAccountWhite,
                        fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text(
                        when {
                            lifetime -> "Pagamento único • Acesso permanente aos recursos incluídos"
                            isTrial -> "Recursos PRO disponíveis até o término do teste"
                            pro -> "Recursos PRO enquanto a assinatura estiver válida"
                            !account.commercialConfigured -> "Confira sua licença para confirmar direitos PRO"
                            else -> "Recursos gratuitos com limites mensais de importação"
                        },
                        color = BrotherAccountMuted, fontSize = 12.sp
                    )
                }
            }
            Text("O que está incluído", color = BrotherAccountWhite,
                fontWeight = FontWeight.Bold, fontSize = 16.sp)
            entitlements.forEach { text ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("✓", color = BrotherAccountGold, fontSize = 14.sp)
                    Text(text, color = BrotherAccountWhite,
                        modifier = Modifier.weight(1f), fontSize = 12.sp)
                }
            }
            if (!pro && account.commercialConfigured) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = BrotherAccountSurface,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("MINHAS COTAS DE PESQUISA",
                            color = BrotherAccountGold, fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold)
                        val labels = mapOf(
                            "telegram_search" to "Pesquisas no Telegram",
                            "dafont_search" to "Pesquisas no DaFont",
                            "rar_receive" to "Abrir / enviar ao RAR"
                        )
                        val balances = monthlyUsage
                        if (balances != null) {
                            for (balance in balances) {
                                Text(
                                    "${labels[balance.operation] ?: balance.operation}: " +
                                        "${balance.remaining} de ${balance.limit} restantes neste mês",
                                    color = BrotherAccountWhite, fontSize = 11.sp
                                )
                            }
                            if (balances.isEmpty()) {
                                Text("Nenhuma cota disponível para consulta.",
                                    color = BrotherAccountMuted, fontSize = 11.sp)
                            }
                        } else {
                            Text(
                                if (quotaError) "Não foi possível consultar suas cotas. Confira sua conexão e seu login Google."
                                else "Consultando seu saldo de utilizações…",
                                color = BrotherAccountMuted, fontSize = 11.sp
                            )
                        }
                    }
                }
            }
            if (lifetime) {
                Surface(
                    color = BrotherAccountSurface, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Sua licença não tem mensalidades recorrentes. Programas Beta e produtos futuros separados não estão incluídos automaticamente.",
                        modifier = Modifier.padding(14.dp),
                        color = BrotherAccountMuted, fontSize = 11.sp
                    )
                }
            }
            if (!pro) {
                Button(
                    onClick = onOpenPlans,
                    modifier = Modifier.fillMaxWidth().height(47.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrotherAccountGold,
                        contentColor = BrotherAccountNavy
                    )
                ) {
                    Text("Conhecer planos PRO", fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = onOpenPlans, modifier = Modifier.fillMaxWidth()) {
                    Text("Comparar outros planos", color = BrotherAccountGold)
                }
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}
