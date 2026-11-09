package com.timachado.brothermatrizes.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.timachado.brothermatrizes.desktop.platform.DesktopBrotherCommunityLinks
import com.timachado.brothermatrizes.desktop.platform.DesktopOfficialCommunityGroupResolver
import com.timachado.brothermatrizes.desktop.platform.DesktopOfficialReleaseNotice
import com.timachado.brothermatrizes.desktop.platform.DesktopOfficialWindowsReleaseNotice
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsLocalNotification
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Android BrotherCommunityScreen adapted to native Windows browser/mail apps. */
@Composable
internal fun DesktopBrotherCommunityScreen(
    onBack: () -> Unit,
    versionName: String = "Windows QA",
    openBrowser: (URI) -> Unit = DesktopBrotherCommunityLinks::openWebsite,
    openMail: (URI) -> Unit = DesktopBrotherCommunityLinks::openEmailDraft,
    automaticAndroidNewsEnabled: Boolean = false,
    allowPublicGroupLookup: Boolean = true,
    onAndroidNewsConsentChange: suspend (Boolean) -> Boolean = { false }
) {
    var feedbackOpen by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Sugestão") }
    var status by remember { mutableStateOf<String?>(null) }
    var officialGroup by remember { mutableStateOf(DesktopBrotherCommunityLinks.group()) }
    val scope = rememberCoroutineScope()
    var androidReleaseNotice by remember {
        mutableStateOf<DesktopOfficialReleaseNotice.Notice?>(null)
    }
    var checkingNews by remember { mutableStateOf(false) }
    var checkingWindowsNews by remember { mutableStateOf(false) }
    var windowsReleaseInfo by remember { mutableStateOf<String?>(null) }

    // Same priority and fallback as the Android Community screen. Public
    // read-only metadata; never opens a browser automatically or joins groups.
    LaunchedEffect(allowPublicGroupLookup) {
        if (allowPublicGroupLookup) {
            officialGroup = DesktopOfficialCommunityGroupResolver().resolve()
        }
    }

    fun browse(uri: URI) {
        try {
            openBrowser(uri)
            status = null
        } catch (_: Exception) {
            status = "Não foi possível abrir o destino. Verifique o navegador padrão do Windows."
        }
    }

    fun sendDraft(useWhatsapp: Boolean) {
        try {
            val destination = if (useWhatsapp)
                DesktopBrotherCommunityLinks.whatsappFeedback(category, message, versionName)
            else DesktopBrotherCommunityLinks.emailFeedback(category, message, versionName)
            if (useWhatsapp) openBrowser(destination) else openMail(destination)
            feedbackOpen = false
            status = "Rascunho aberto para conferência; nada foi enviado automaticamente."
        } catch (_: Exception) {
            status = "Não foi possível abrir o WhatsApp ou aplicativo de e-mail no Windows."
        }
    }

    if (feedbackOpen) {
        AlertDialog(
            onDismissRequest = { feedbackOpen = false },
            title = { Text("Seu feedback para o Brother Matrizes") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Escolha o tipo de mensagem e descreva o que aconteceu ou a melhoria desejada.")
                    OutlinedButton(
                        onClick = {
                            category = if (category == "Sugestão") "Problema" else "Sugestão"
                        }
                    ) { Text("Tipo: $category (clique para alterar)") }
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it.take(2500) },
                        label = { Text("Mensagem") },
                        minLines = 4,
                        maxLines = 7,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        if (message.isBlank()) "Descreva seu relato antes de escolher o envio."
                        else "${message.length}/2500 caracteres · Mensagem pronta",
                        color = FioTextMuted
                    )
                    Text(
                        "WhatsApp ou e-mail serão abertos com um rascunho para você conferir e enviar.",
                        color = FioTextMuted
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        enabled = message.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { sendDraft(true) }
                    ) { Text("Enviar pelo WhatsApp") }
                    OutlinedButton(
                        enabled = message.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { sendDraft(false) }
                    ) { Text("Enviar por e-mail") }
                    TextButton(
                        onClick = { feedbackOpen = false },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Cancelar") }
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹ Voltar", color = FioGold) }
        Text(
            "Comunidade Brother Matrizes",
            color = FioText,
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall
        )
        Text(
            "Novidades, tutoriais e sugestões para a evolução do seu aplicativo.",
            color = FioTextMuted
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = FioSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Atualizações Brother Matrizes Windows",
                    fontWeight = FontWeight.Bold, color = FioText)
                Text(
                    "Este canal é exclusivo do Windows. Nenhum APK Android " +
                        "será oferecido como instalação para PC.",
                    color = FioTextMuted
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !checkingWindowsNews,
                    onClick = {
                        scope.launch {
                            checkingWindowsNews = true
                            windowsReleaseInfo = null
                            try {
                                val published = DesktopOfficialWindowsReleaseNotice()
                                    .checkPublishedWindowsRelease()
                                windowsReleaseInfo = published?.displayText() ?:
                                    "Nenhum manifesto de versão Windows estável " +
                                        "foi publicado neste canal. O executável " +
                                        "continua em homologação."
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                windowsReleaseInfo =
                                    "Não foi possível verificar o canal Windows. " +
                                        "Nenhuma atualização foi instalada."
                            } finally {
                                checkingWindowsNews = false
                            }
                        }
                    }
                ) {
                    Text(if (checkingWindowsNews) "Verificando…" else
                        "Verificar novidades oficiais Windows")
                }
                windowsReleaseInfo?.let { Text(it, color = FioTextMuted) }
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = FioSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Atualizações oficiais Android",
                    fontWeight = FontWeight.Bold, color = FioText)
                Text("Versão instalada: $versionName", color = FioTextMuted)
                Text(
                    "Acompanhe as versões publicadas e o que melhorou em cada uma.",
                    color = FioTextMuted
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { browse(DesktopBrotherCommunityLinks.updates()) }
                ) { Text("Ver notas e novidades") }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !checkingNews,
                    onClick = {
                        scope.launch {
                            checkingNews = true
                            androidReleaseNotice = null
                            try {
                                // Explicit read-only check. This feed currently
                                // publishes ANDROID releases, not Windows EXEs.
                                androidReleaseNotice =
                                    DesktopOfficialReleaseNotice()
                                        .checkAndroidAnnouncements()
                                status = null
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                status = "Não foi possível consultar as novidades oficiais. " +
                                    "O aplicativo continua funcionando normalmente."
                            } finally {
                                checkingNews = false
                            }
                        }
                    }
                ) {
                    Text(if (checkingNews) "Consultando…" else
                        "Consultar avisos oficiais do Android")
                }
                androidReleaseNotice?.let { notice ->
                    Text(notice.displayText(), color = FioTextMuted)
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val shown = runCatching {
                                DesktopWindowsLocalNotification.showAfterUserClick(
                                    title = "Brother Matrizes — novidades Android",
                                    message = "Android ${notice.versionName} publicado. " +
                                        "Versão Windows ainda em homologação."
                                )
                            }.getOrDefault(false)
                            status = if (shown)
                                "Notificação local solicitada ao Windows. " +
                                    "Isso não ativa notificações push."
                            else
                                "O sistema não disponibilizou notificação nativa. " +
                                    "O aviso permanece visível nesta tela."
                        }
                    ) { Text("Mostrar este aviso no Windows") }
                }
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = FioSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Grupo oficial de feedbacks", fontWeight = FontWeight.Bold, color = FioText)
                Text(
                    "Converse com outros usuários, relate erros e envie sugestões para as próximas versões.",
                    color = FioTextMuted
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { browse(officialGroup) }
                ) { Text("Entrar no grupo do WhatsApp") }
                Text(
                    "Outros membros poderão visualizar seu telefone. Não compartilhe senhas ou arquivos sigilosos.",
                    color = FioTextMuted
                )
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = FioSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Envie seu feedback", fontWeight = FontWeight.Bold, color = FioText)
                Text("Relate um erro ou sugira melhorias. Não inclua dados sensíveis.", color = FioTextMuted)
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { feedbackOpen = true }
                ) { Text("Enviar sugestão ou relatar problema") }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { browse(DesktopBrotherCommunityLinks.updates()) }
                ) { Text("Abrir página do Brother Matrizes no site") }
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = FioSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Notificações push", fontWeight = FontWeight.Bold, color = FioText)
                Text(
                    "Avisos automáticos de novidades Android, enquanto este " +
                        "aplicativo Windows estiver aberto. A verificação é " +
                        "feita no máximo a cada 6 horas e cada versão é avisada " +
                        "uma vez. Não instala serviço, não executa downloads " +
                        "e não representa Firebase push ou atualização Windows.",
                    color = FioTextMuted
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !checkingNews,
                    onClick = {
                        scope.launch {
                            checkingNews = true
                            try {
                                val next = !automaticAndroidNewsEnabled
                                val changed = onAndroidNewsConsentChange(next)
                                status = if (changed && next)
                                    "Avisos ativados. A versão Android atual foi " +
                                        "registrada sem notificação antiga. " +
                                        "O recurso para ao fechar o Brother Matrizes."
                                else if (changed)
                                    "Avisos automáticos desativados neste PC."
                                else
                                    "A opção automática não está disponível " +
                                        "nesta execução do aplicativo."
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                status = "Não foi possível salvar a preferência " +
                                    "de avisos ou verificar a versão oficial. " +
                                    "Nenhuma mudança foi ativada."
                            } finally {
                                checkingNews = false
                            }
                        }
                    }
                ) {
                    Text(if (automaticAndroidNewsEnabled)
                        "Desativar avisos automáticos do Android"
                    else "Ativar avisos automáticos do Android")
                }
                Text(
                    "Consentimento: " + if (automaticAndroidNewsEnabled)
                        "ativo para esta conta do Windows (não é assinatura Pro)"
                    else "desativado por padrão",
                    color = FioTextMuted
                )
            }
        }
        status?.let { Text(it, color = FioGold) }
        Text(
            "A assinatura oferece acesso aos recursos contratados. " +
                "Seu apoio também contribui com manutenção e evolução contínua.",
            color = FioTextMuted
        )
    }
}
