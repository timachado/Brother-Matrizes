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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URI
import kotlinx.coroutines.launch

/** Conta desktop: validação on-line real e sem persistência de sessão. */
@Composable
internal fun DesktopAccountPanel(
    enabled: Boolean,
    auth: DesktopAccountAuth,
    onBack: () -> Unit,
    onOpenExternal: (URI) -> Unit
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var current by remember { mutableStateOf(auth.current) }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val controlsEnabled = enabled && !working

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(enabled = controlsEnabled, onClick = onBack) {
            Text("Voltar ao início")
        }
        Text(
            "Conta Brother Matrizes",
            color = DesktopBrandColors.gold,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            "Sessão Windows independente do Android",
            style = MaterialTheme.typography.titleMedium
        )
        if (current == null || !auth.isSignedIn) {
            Text(
                "Entre com sua conta Supabase do Brother Matrizes. " +
                    "O servidor verifica a identidade e a assinatura; " +
                    "o login não desbloqueia recursos PRO nesta QA.",
                color = DesktopBrandColors.muted, fontSize = 12.sp
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.take(254) },
                label = { Text("E-mail") },
                singleLine = true,
                enabled = controlsEnabled,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.take(1024) },
                label = { Text("Senha") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = controlsEnabled,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                enabled = controlsEnabled && email.isNotBlank() &&
                    password.length >= 8,
                onClick = {
                    val submittedEmail = email
                    val submittedPassword = password
                    password = ""
                    working = true
                    status = "Conferindo a conta no servidor oficial..."
                    error = false
                    scope.launch {
                        val result = runCatching {
                            auth.login(submittedEmail, submittedPassword)
                        }
                        working = false
                        result.onSuccess { verified ->
                            current = verified
                            email = verified.email
                            status = "Conta verificada no Supabase Auth e no servidor de licenças."
                        }.onFailure {
                            current = null
                            error = true
                            status = "Não foi possível confirmar a conta. " +
                                "Verifique e-mail, senha, conexão e confirmação do cadastro."
                        }
                    }
                }
            ) { Text(if (working) "Validando..." else "Entrar com e-mail e senha") }
        } else {
            val verified = current
            if (verified != null) {
                Text(
                    "Autenticado: ${verified.email}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Plano informado pelo servidor: ${verified.plan}.",
                    color = DesktopBrandColors.muted,
                    fontSize = 12.sp
                )
                Text(
                    if (verified.backendReportsPro)
                        "A assinatura foi confirmada pelo backend. " +
                            "As operações PRO do Windows continuam bloqueadas " +
                            "até implementar cotas e autorização por ação."
                    else
                        "Não há licença PRO ativa confirmada para a sessão. " +
                            "Recursos comerciais permanecem bloqueados.",
                    color = DesktopBrandColors.muted, fontSize = 12.sp
                )
            }
            OutlinedButton(
                enabled = controlsEnabled,
                onClick = {
                    working = true
                    status = "Revalidando sua conta no servidor..."
                    error = false
                    scope.launch {
                        val result = runCatching { auth.revalidate() }
                        working = false
                        result.onSuccess {
                            current = it
                            status = "Direitos atualizados pelo servidor."
                        }.onFailure {
                            current = null
                            error = true
                            status = "Não foi possível revalidar a sessão. " +
                                "Entre novamente para continuar."
                        }
                    }
                }
            ) { Text("Atualizar situação da licença") }
            OutlinedButton(
                enabled = controlsEnabled,
                onClick = {
                    working = true
                    current = null
                    status = ""
                    scope.launch {
                        auth.logout()
                        working = false
                        password = ""
                        status = "Sessão local encerrada; credenciais não foram salvas."
                        error = false
                    }
                }
            ) { Text("Sair da conta") }
        }
        if (status.isNotBlank()) {
            Text(
                status,
                color = if (error) DesktopBrandColors.danger
                    else MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp
            )
        }
        Text(
            "Esta versão não salva tokens, senhas ou sessão em disco. " +
                "Ao fechar o aplicativo ou expirar o token, entre novamente. " +
                "O checkout WordPress e a conta do site são independentes " +
                "da sessão do Windows.",
            color = DesktopBrandColors.muted, fontSize = 11.sp
        )
        Spacer(Modifier.height(4.dp))
        Button(
            enabled = controlsEnabled,
            onClick = { onOpenExternal(DesktopAccountLinks.account()) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Abrir minha conta e compras no site oficial") }
        Text(
            "Conheça os planos (valores e disponibilidade somente no site)",
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        DesktopAccountLinks.plans.forEach { plan ->
            Text(
                plan.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(plan.headline, color = DesktopBrandColors.muted, fontSize = 12.sp)
            Text(plan.billing, color = DesktopBrandColors.muted, fontSize = 11.sp)
            OutlinedButton(
                enabled = controlsEnabled,
                onClick = { onOpenExternal(DesktopAccountLinks.plan(plan.code)) }
            ) { Text("Consultar no site — ${plan.title}") }
        }
        Text(
            "Ainda não disponíveis no Windows QA: login Google, renovação " +
                "automática, recuperação da sessão, aplicação de direitos PRO " +
                "e cotas comerciais, histórico de pagamentos e gestão de aparelhos.",
            color = DesktopBrandColors.muted,
            fontSize = 11.sp
        )
    }
}
