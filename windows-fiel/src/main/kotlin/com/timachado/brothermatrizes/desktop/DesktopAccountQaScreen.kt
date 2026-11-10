package com.timachado.brothermatrizes.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import com.timachado.brothermatrizes.desktop.platform.DesktopServerAccountSnapshot
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsGoogleQaEnvironment
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsGoogleAccount
import com.timachado.brothermatrizes.desktop.platform.DesktopEntitlementUnavailableException
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsAccountQaOptIn
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsPendingFontQuotaReview
import com.timachado.brothermatrizes.desktop.platform.DesktopPkceLoopbackSelfTest
import com.timachado.brothermatrizes.desktop.platform.DesktopPkceRedirectPreflight
import com.timachado.brothermatrizes.desktop.platform.DesktopRedirectAllowlistReview
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurfaceAlt
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Windows account navigation, modeled after the Android Minha Conta tab.
 *
 * Normal Windows users can explicitly sign in with Google in Minha Conta.
 * QA diagnostics retain their separate opt-in. No browser/network calls
 * occur at startup. Server-only entitlements never grant imports locally.
 */
internal object DesktopAccountPresentation {
    data class VerifiedSummary(
        val planName: String,
        val verifiedOnServer: Boolean,
        val isPaidPlan: Boolean
    )

    fun verified(snapshot: DesktopServerAccountSnapshot): VerifiedSummary {
        // Defense in depth: a stale or inconsistent preview cannot show Pro.
        val paidPlans = setOf(
            "pro_monthly", "pro_yearly", "pro_lifetime",
            "pro_lifetime_launch", "trial"
        )
        val paid = snapshot.hasServerPro &&
            snapshot.subscriptionStatus == "active" &&
            snapshot.planCode in paidPlans
        val planName = when (if (paid) snapshot.planCode else "free") {
            "pro_monthly" -> "Brother Matrizes Pro mensal"
            "pro_yearly" -> "Brother Matrizes Pro anual"
            "pro_lifetime" -> "Brother Matrizes Pro vitalício"
            "pro_lifetime_launch" -> "Brother Matrizes Pro vitalício lançamento"
            "trial" -> "Teste Pro"
            else -> "Plano gratuito"
        }
        return VerifiedSummary(
            planName = planName,
            verifiedOnServer = snapshot.subscriptionStatus == "active",
            isPaidPlan = paid
        )
    }
}

@Composable
internal fun DesktopAccountQaScreen(
    onBack: () -> Unit,
    onPlans: () -> Unit,
    qaEnvironment: DesktopWindowsGoogleQaEnvironment?,
    officialGoogle: DesktopWindowsGoogleAccount? = null,
    onServerAccountVerified: (DesktopServerAccountSnapshot?) -> Unit = {}
) {
    // The same QA environment is owned by FielHomeHost for the whole desktop
    // window, so switching to Fontes cannot close/relock the DPAPI session.
    // No auth network request occurs during composition.

    val scope = rememberCoroutineScope()
    var summary by remember {
        mutableStateOf<DesktopAccountPresentation.VerifiedSummary?>(null)
    }
    var busy by remember { mutableStateOf(false) }
    // Distinguish verified Google identity from optional server plan preview.
    var googleVerified by remember { mutableStateOf(false) }
    var googleProviderStatus by remember { mutableStateOf<String?>(null) }
    var pendingFontQuotaStatus by remember { mutableStateOf<String?>(null) }
    var remoteFontQuotaStatus by remember { mutableStateOf<String?>(null) }
    var loopbackStatus by remember { mutableStateOf<String?>(null) }
    var pastedAllowlist by remember { mutableStateOf("") }
    var allowlistReview by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf("Nenhuma assinatura consultada nesta sessão.") }

    fun runAccountAction(action: suspend () -> DesktopServerAccountSnapshot?) {
        if (busy || (qaEnvironment == null && officialGoogle == null)) return
        busy = true
        scope.launch {
            try {
                remoteFontQuotaStatus = null
                val account = action()
                summary = account?.let(DesktopAccountPresentation::verified)
                onServerAccountVerified(account)
                info = if (summary == null) {
                    "Não existe uma sessão Google validada neste computador."
                } else {
                    "Assinatura consultada no servidor em modo somente leitura."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                summary = null
                onServerAccountVerified(null)
                info = "Não foi possível validar a conta. Faça o login novamente."
            } finally {
                busy = false
            }
        }
    }

    fun runOfficialGoogleAction(mode: String) {
        val client = officialGoogle ?: return
        if (busy) return
        busy = true
        scope.launch {
            try {
                val authenticated = when (mode) {
                    "login" -> client.signInAndVerify()
                    "restore" -> client.restoreAndVerify()
                    else -> client.verifiedGoogleIdentity()
                }
                if (authenticated == null) {
                    googleVerified = false
                    summary = null
                    onServerAccountVerified(null)
                    info = "Nenhuma sessão Google protegida encontrada. Entre com Google."
                    return@launch
                }
                googleVerified = true
                info = "Google autenticado. Consultando sua assinatura no servidor…"
                try {
                    val snapshot = client.accountPreview()
                    summary = DesktopAccountPresentation.verified(snapshot)
                    onServerAccountVerified(snapshot)
                    info = "Google autenticado e assinatura consultada no servidor."
                } catch (temporary: DesktopEntitlementUnavailableException) {
                    // Keep Google session; never invent Free/Pro privileges.
                    summary = null
                    onServerAccountVerified(null)
                    info = "Google autenticado. O serviço de assinaturas está " +
                        "indisponível. Use Atualizar assinatura para consultar novamente."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                googleVerified = false
                summary = null
                onServerAccountVerified(null)
                info = "Não foi possível concluir ou manter a sessão Google. " +
                    "Tente Entrar com Google novamente."
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹ Voltar", color = FioGold) }
        Text("Minha Conta", color = FioText, fontWeight = FontWeight.Bold)
        Text(
            "Brother Matrizes • T.I. Machado Soluções em Tecnologia",
            color = FioTextMuted
        )
        OutlinedButton(
            onClick = onPlans,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Planos e benefícios — versão original Android") }


        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = FioSurfaceAlt)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Identidade e assinatura",
                    color = FioGold,
                    fontWeight = FontWeight.Bold
                )
                if (summary == null) {
                    Text("Conta Google não validada nesta tela.", color = FioText)
                } else {
                    val state = checkNotNull(summary)
                    Text(state.planName, color = FioText, fontWeight = FontWeight.Bold)
                    Text(
                        if (state.verifiedOnServer)
                            "Assinatura consultada no servidor."
                        else
                            "Assinatura não confirmada como ativa.",
                        color = FioTextMuted
                    )
                    Text(
                        if (state.isPaidPlan)
                            "O servidor informou um plano Pro. Operações comerciais " +
                                "continuam protegidas por autorização individual."
                        else
                            "Nenhuma licença Pro ativa foi confirmada nesta consulta.",
                        color = FioTextMuted
                    )
                }
                Text(info, color = FioTextMuted)
            }
        }

        // Technical QA diagnostics are hidden in the normal commercial
        // login experience, while remaining available in the isolated QA UI.
        if (qaEnvironment != null || officialGoogle == null) {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            onClick = {
                scope.launch {
                    busy = true
                    loopbackStatus = null
                    try {
                        val result = DesktopPkceLoopbackSelfTest.verify()
                        loopbackStatus =
                            if (result.localListenerAvailable &&
                                result.forgedStateRejected &&
                                result.validLocalRoundTrip)
                                "Retorno local Windows aprovado: porta loopback, " +
                                    "proteção de estado e callback de teste."
                            else
                                "Retorno local Windows não validado."
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        loopbackStatus =
                            "O retorno local OAuth não pôde ser testado neste PC. " +
                                "Verifique as permissões de rede local."
                    } finally {
                        busy = false
                    }
                }
            }
        ) {
            Text(
                if (busy) "Testando retorno local…" else
                    "Testar retorno local OAuth (sem Google)"
            )
        }
        if (loopbackStatus != null) {
            Text(checkNotNull(loopbackStatus), color = FioText)
        }
        Text(
            "Este diagnóstico utiliza somente 127.0.0.1 e códigos fictícios. " +
                "Não verifica as Redirect URLs no Supabase, " +
                "não faz login nem libera recursos Pro.",
            color = FioTextMuted
        )

        }
        // Manual-only checklist appears exclusively in the opted-in QA screen.
        // It never fetches the private Supabase Redirect URLs, starts Google,
        // changes account state or declares a commercial login ready.
        if (qaEnvironment != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = FioSurfaceAlt)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Conferência manual de Redirect URLs (QA)",
                        color = FioGold,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Copie somente as URLs permitidas em Supabase > " +
                            "Authentication > URL Configuration. Não cole " +
                            "chaves, códigos de acesso ou tokens.",
                        color = FioTextMuted
                    )
                    Text(
                        "Padrão candidato (ainda não homologado): " +
                            DesktopPkceRedirectPreflight.SUPABASE_ALLOWLIST_CANDIDATE,
                        color = FioText
                    )
                    OutlinedTextField(
                        value = pastedAllowlist,
                        onValueChange = {
                            if (it.length <= 8192) {
                                pastedAllowlist = it
                                allowlistReview = null
                            }
                        },
                        label = { Text("URLs copiadas do painel") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5
                    )
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && pastedAllowlist.isNotBlank(),
                        onClick = {
                            val result = DesktopRedirectAllowlistReview.examine(
                                pastedAllowlist
                            )
                            allowlistReview = if (result.candidatePresentInPastedText)
                                "O padrão candidato consta da lista colada. " +
                                    "Isso não confirma configuração ativa no servidor."
                            else
                                "O padrão candidato não foi encontrado na lista " +
                                    "colada. Não houve alteração no Supabase."
                        }
                    ) {
                        Text("Conferir texto localmente (sem login)")
                    }
                    if (allowlistReview != null) {
                        Text(checkNotNull(allowlistReview), color = FioText)
                    }
                    Text(
                        "Esta comparação é local, temporária e não comprova " +
                            "que as URLs estão salvas no Supabase. A " +
                            "homologação exige conferência no painel e login " +
                            "real com conta de testes dedicada.",
                        color = FioTextMuted
                    )
                }
            }
        }

        if (qaEnvironment == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = FioSurfaceAlt)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Entrar com Google — Brother Matrizes",
                        color = FioGold,
                        fontWeight = FontWeight.Bold
                    )
                    if (officialGoogle == null) {
                        Text(
                            "Não foi possível preparar o login no perfil atual do Windows.",
                            color = FioText
                        )
                    } else {
                        Text(
                            "O acesso usa navegador externo, PKCE e sessão Windows " +
                                "protegida. O Supabase precisa aceitar o retorno OAuth. " +
                                "Entrar não consome cotas.",
                            color = FioTextMuted
                        )
                        Text(
                            if (googleVerified) "Conta Google confirmada pelo Supabase."
                            else "Conta Google ainda não confirmada neste acesso.",
                            color = FioTextMuted
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                            onClick = { runOfficialGoogleAction("login") }
                        ) { Text(if (busy) "Verificando…" else "Entrar com Google") }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                            onClick = { runOfficialGoogleAction("restore") }
                        ) { Text("Restaurar sessão protegida") }
                        if (googleVerified) {
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !busy,
                                onClick = { runOfficialGoogleAction("refresh") }
                            ) { Text("Atualizar assinatura") }
                        }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && googleVerified,
                            onClick = {
                                if (!busy) {
                                    busy = true
                                    scope.launch {
                                        try {
                                            officialGoogle.logout()
                                            googleVerified = false
                                            summary = null
                                            onServerAccountVerified(null)
                                            info = "Você saiu da conta Google."
                                        } catch (_: Exception) {
                                            info = "Não foi possível encerrar a sessão."
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                            }
                        ) { Text("Sair da conta") }
                        Text(
                            "DaFont e NetFontes usam a mesma conta verificada. " +
                                "A importação de fontes continua dependente da " +
                                "autorização de cotas Free/Pro no servidor.",
                            color = FioTextMuted
                        )
                    }
                }
            }
        } else {
            // QA-only local journal review; user clicks explicitly.
            // No Google request, quota charge, file mutation or automatic
            // reconciliation. Even an empty journal is not proof of rights.
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        pendingFontQuotaStatus = null
                        try {
                            val report = withContext(Dispatchers.IO) {
                                DesktopWindowsPendingFontQuotaReview.inspectWindowsDefault()
                            }
                            pendingFontQuotaStatus = report.userMessage()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            pendingFontQuotaStatus =
                                "Registro de cotas inválido ou indisponível. " +
                                    "Nenhuma importação deve ser liberada sem revisão."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text("Consultar cotas pendentes de fontes — somente leitura") }
            if (pendingFontQuotaStatus != null) {
                Text(checkNotNull(pendingFontQuotaStatus), color = FioText)
            }

            // Explicit QA-only server status lookup of an EXISTING local
            // import_font reservation. Never reserves, settles or clears it.
            // Disabled on the currently deployed legacy backend; operator
            // must verify staging rollout before enabling the extra QA flag.
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && summary != null &&
                    DesktopWindowsAccountQaOptIn.knownQuotaStatusV2Enabled(
                        System.getenv()
                    ),
                onClick = {
                    scope.launch {
                        busy = true
                        remoteFontQuotaStatus = null
                        try {
                            val review = checkNotNull(
                                DesktopWindowsAccountQaOptIn.remoteFontQuotaReviewForQa(
                                    qaEnvironment
                                )
                            ) { "Diagnóstico remoto não autorizado." }
                            val report = withContext(Dispatchers.IO) {
                                review.inspectWindowsDefault()
                            }
                            remoteFontQuotaStatus = report.userMessage()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            remoteFontQuotaStatus =
                                "Não foi possível confirmar a cota no servidor. " +
                                    "Nenhum registro foi liberado; revisão manual obrigatória."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text("Conferir reserva de fonte no servidor — QA somente leitura") }
            if (remoteFontQuotaStatus != null) {
                Text(checkNotNull(remoteFontQuotaStatus), color = FioText)
            }
            Text(
                if (DesktopWindowsAccountQaOptIn.knownQuotaStatusV2Enabled(
                    System.getenv()
                ))
                    "Apenas homologação V2: nenhuma confirmação remota libera " +
                        "fontes ou descontos de cota automaticamente."
                else
                    "Consulta remota desabilitada: a função brother-usage " +
                        "publicada ainda não possui status individual V2. " +
                        "É necessária homologação separada do backend.",
                color = FioTextMuted
            )

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val state = qaEnvironment.googleProviderReadiness()
                            googleProviderStatus =
                                if (state.googleProviderEnabled)
                                    "Provedor Google ativo no Supabase."
                                else
                                    "Provedor Google desativado no Supabase."
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            googleProviderStatus =
                                "Não foi possível verificar o provedor Google."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text("Verificar provedor Google (sem login)") }

            if (googleProviderStatus != null) {
                Text(checkNotNull(googleProviderStatus), color = FioText)
            }
            Text(
                "A verificação do provedor não confirma a lista de URLs " +
                    "de retorno OAuth nem autoriza importações TTF/OTF.",
                color = FioTextMuted
            )

            Text(
                if (DesktopWindowsAccountQaOptIn.redirectReviewedForQa())
                    "Login Google permitido apenas para teste explícito com " +
                        "conta dedicada de QA. Isso não homologa o retorno OAuth."
                else
                    "Login Google bloqueado: conferir Redirect URLs no painel " +
                        "Supabase e habilitar manualmente " +
                        "BROTHER_WINDOWS_QA_REDIRECT_REVIEWED=enabled em ambiente QA. " +
                        "Não utilizar contas de clientes ou produção.",
                color = FioTextMuted
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy &&
                    DesktopWindowsAccountQaOptIn.redirectReviewedForQa(),
                onClick = {
                    runAccountAction {
                        qaEnvironment.signIn()
                        qaEnvironment.accountPreview()
                    }
                }
            ) {
                Text(if (busy) "Verificando…" else "Entrar com Google — QA")
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy &&
                    DesktopWindowsAccountQaOptIn.redirectReviewedForQa(),
                onClick = {
                    runAccountAction {
                        if (qaEnvironment.restoreAndVerify()) {
                            qaEnvironment.accountPreview()
                        } else null
                    }
                }
            ) { Text("Recuperar e verificar sessão protegida") }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && summary != null,
                onClick = {
                    runAccountAction {
                        qaEnvironment.accountPreview()
                    }
                }
            ) { Text("Consultar assinatura novamente") }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            qaEnvironment.logout()
                            summary = null
                            onServerAccountVerified(null)
                            remoteFontQuotaStatus = null
                            info = "Sessão Windows encerrada; credenciais locais removidas."
                        } catch (_: Exception) {
                            summary = null
                            onServerAccountVerified(null)
                            info = "Não foi possível concluir o encerramento da sessão."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text("Sair da conta — QA") }
        }

        Text(
            "A consulta da assinatura não autoriza importação de fontes, " +
                "cotas comerciais nem distribuição de executáveis.",
            color = FioTextMuted
        )
    }
}
