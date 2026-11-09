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
 * The only real authentication actions are restricted to an explicit QA
 * environment variable opt-in, never enabled in normal app startup.
 * The read-only entitlement snapshot is informational; it cannot unlock
 * commercial imports or act as a device-bound license.
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
    qaEnvironment: DesktopWindowsGoogleQaEnvironment?
) {
    // The same QA environment is owned by FielHomeHost for the whole desktop
    // window, so switching to Fontes cannot close/relock the DPAPI session.
    // No auth network request occurs during composition.

    val scope = rememberCoroutineScope()
    var summary by remember {
        mutableStateOf<DesktopAccountPresentation.VerifiedSummary?>(null)
    }
    var busy by remember { mutableStateOf(false) }
    var googleProviderStatus by remember { mutableStateOf<String?>(null) }
    var pendingFontQuotaStatus by remember { mutableStateOf<String?>(null) }
    var remoteFontQuotaStatus by remember { mutableStateOf<String?>(null) }
    var loopbackStatus by remember { mutableStateOf<String?>(null) }
    var pastedAllowlist by remember { mutableStateOf("") }
    var allowlistReview by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf("Nenhuma assinatura consultada nesta sessão.") }

    fun runAccountAction(action: suspend () -> DesktopAccountPresentation.VerifiedSummary?) {
        if (busy || qaEnvironment == null) return
        scope.launch {
            busy = true
            try {
                remoteFontQuotaStatus = null
                summary = action()
                info = if (summary == null) {
                    "Não existe uma sessão Google validada neste computador."
                } else {
                    "Assinatura consultada no servidor em modo somente leitura (QA)."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                summary = null
                info = "Não foi possível validar a conta. Faça o login novamente."
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

        // Local-only OAuth diagnostics work even when no Supabase key or
        // Google QA session is configured. This button never opens a browser.
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
                        "Google e licenciamento em homologação",
                        color = FioGold,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Esta versão Windows ainda não habilita autenticação comercial. " +
                            "A configuração de retorno OAuth precisa ser verificada antes " +
                            "de permitir login com contas reais.",
                        color = FioText
                    )
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

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                onClick = {
                    runAccountAction {
                        qaEnvironment.signIn()
                        DesktopAccountPresentation.verified(qaEnvironment.accountPreview())
                    }
                }
            ) {
                Text(if (busy) "Verificando…" else "Entrar com Google — QA")
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                onClick = {
                    runAccountAction {
                        if (qaEnvironment.restoreAndVerify()) {
                            DesktopAccountPresentation.verified(
                                qaEnvironment.accountPreview()
                            )
                        } else null
                    }
                }
            ) { Text("Recuperar e verificar sessão protegida") }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && summary != null,
                onClick = {
                    runAccountAction {
                        DesktopAccountPresentation.verified(qaEnvironment.accountPreview())
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
                            remoteFontQuotaStatus = null
                            info = "Sessão Windows encerrada; credenciais locais removidas."
                        } catch (_: Exception) {
                            summary = null
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
