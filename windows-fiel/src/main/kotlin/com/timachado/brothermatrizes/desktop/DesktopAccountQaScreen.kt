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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.timachado.brothermatrizes.desktop.platform.DesktopServerAccountSnapshot
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsAccountQaOptIn
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurfaceAlt
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
        val planName = when (snapshot.planCode) {
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
            isPaidPlan = snapshot.hasServerPro
        )
    }
}

@Composable
internal fun DesktopAccountQaScreen(onBack: () -> Unit) {
    // No browser or network access on composition. If explicit QA opt-in is
    // absent, no runtime, session lock, DPAPI access or account is opened.
    val qaEnvironment = remember {
        runCatching { DesktopWindowsAccountQaOptIn.openWhenOptedIn() }
            .getOrNull()
    }
    DisposableEffect(qaEnvironment) {
        onDispose { qaEnvironment?.close() }
    }

    val scope = rememberCoroutineScope()
    var summary by remember {
        mutableStateOf<DesktopAccountPresentation.VerifiedSummary?>(null)
    }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf("Nenhuma assinatura consultada nesta sessão.") }

    fun runAccountAction(action: suspend () -> DesktopAccountPresentation.VerifiedSummary?) {
        if (busy || qaEnvironment == null) return
        scope.launch {
            busy = true
            try {
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
