package com.timachado.brothermatrizes.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URI

/**
 * Porta de entrada pública da Conta Android. O contrato OAuth/Supabase,
 * direitos PRO e checkout não são portados nesta etapa.
 */
@Composable
internal fun DesktopAccountPanel(
    enabled: Boolean,
    onBack: () -> Unit,
    onOpenExternal: (URI) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(enabled = enabled, onClick = onBack) {
            Text("Voltar ao início")
        }
        Text(
            "Conta Brother Matrizes",
            color = DesktopBrandColors.gold,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            "Acesso pelo site oficial da T.I. Machado",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "Status neste aplicativo Windows QA: ${DesktopAccountLinks.accountStatus}. " +
                "Não existe uma sessão autenticada no aplicativo e " +
                "nenhum recurso PRO é liberado por esta tela.",
            color = DesktopBrandColors.muted,
            fontSize = 12.sp
        )
        Button(
            enabled = enabled,
            onClick = { onOpenExternal(DesktopAccountLinks.account()) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Abrir minha conta no site oficial") }
        Text(
            "O navegador faz a autenticação no site. " +
                "Uma sessão WooCommerce no navegador NÃO equivale a " +
                "uma licença validada pelo aplicativo Windows.",
            color = DesktopBrandColors.muted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Conheça os planos",
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Informações gerais do catálogo Android. " +
                "Não exibimos preços, ofertas ativas nem saldo de licença: " +
                "confirme sempre as condições no checkout oficial.",
            fontSize = 11.sp, color = DesktopBrandColors.muted
        )
        DesktopAccountLinks.plans.forEach { plan ->
            Text(
                plan.title,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(plan.headline, color = DesktopBrandColors.muted, fontSize = 12.sp)
            Text(plan.billing, color = DesktopBrandColors.muted, fontSize = 11.sp)
            OutlinedButton(
                enabled = enabled,
                onClick = { onOpenExternal(DesktopAccountLinks.plan(plan.code)) }
            ) { Text("Ver condições no site — ${plan.title}") }
        }
        Text(
            "Funcionalidades não implementadas neste QA: " +
                "login nativo, sincronização da conta, teste PRO, " +
                "validação de assinatura, histórico de pagamentos, " +
                "gerenciamento de dispositivos e renovação automática.",
            color = DesktopBrandColors.muted,
            fontSize = 11.sp
        )
    }
}
