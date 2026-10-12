package com.timachado.brothermatrizes

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.net.Uri
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.messaging.FirebaseMessaging
import com.timachado.brothermatrizes.core.updates.BrotherFirebasePush
import com.timachado.brothermatrizes.core.updates.BrotherUpdateNotificationWorker
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val COMMUNITY_API_PATH = "/wp-json/tim-brother-sync/v1/community"
private const val COMMUNITY_FALLBACK_PAGE = "/aplicativos/brother-matrizes/"

private fun isOfficialGroup(value: String): Boolean {
    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
    return uri.scheme == "https" &&
        uri.host == "chat.whatsapp.com" &&
        uri.pathSegments.size == 1 &&
        uri.pathSegments[0].matches(Regex("[A-Za-z0-9]{16,32}")) &&
        uri.encodedQuery == null &&
        uri.fragment == null
}

/**
 * Consulta somente metadados públicos do WordPress. Falhas de rede não
 * impactam matrizes, pagamentos, licenças nem as atualizações do Android.
 */
private suspend fun getOfficialGroup(): String? = withContext(Dispatchers.IO) {
    // GitHub Raw é a fonte principal. WordPress pode responder com HTML de
    // verificação e não deve tornar o aplicativo dependente desse serviço.
    val sources = listOf(
        "https://raw.githubusercontent.com/timachado/Brother-Matrizes/main/updates/community.json",
        BuildConfig.WORDPRESS_URL.trimEnd('/') + COMMUNITY_API_PATH
    )
    for (source in sources) {
        val value = runCatching {
            val url = URL(source)
            require(url.protocol == "https" && (
                (url.host == "raw.githubusercontent.com" &&
                    url.path == "/timachado/Brother-Matrizes/main/updates/community.json") ||
                (url.host == "timachado.ifree.page" &&
                    url.path == COMMUNITY_API_PATH)
            ))
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 4500
                connection.readTimeout = 4500
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/json")
                if (connection.responseCode != HttpURLConnection.HTTP_OK) null
                else {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                        val buffer = CharArray(4096)
                        val count = it.read(buffer)
                        if (count > 0) String(buffer, 0, count) else ""
                    }
                    val json = JSONObject(body)
                    val product = json.optString("product")
                    if (url.host == "raw.githubusercontent.com" && product != "brother-matrizes") null
                    else json.optString("groupUrl").takeIf(::isOfficialGroup)
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
        if (value != null) return@withContext value
    }
    null
}
@Composable
fun BrotherCommunityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var groupUrl by remember { mutableStateOf<String?>(null) }
    var updateEnabled by remember { mutableStateOf(BrotherFirebasePush.isEnabled(context)) }
    var communityEnabled by remember { mutableStateOf(BrotherFirebasePush.isCommunityEnabled(context)) }
    var permissionDenied by remember { mutableStateOf(false) }
    var fcmDiagnosticStatus by remember { mutableStateOf<String?>(null) }
    var feedbackEditorOpen by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf("") }
    var feedbackCategory by remember { mutableStateOf("Sugestão") }
    var pendingKind by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            when (pendingKind) {
                "update" -> { BrotherFirebasePush.enable(context); updateEnabled = true }
                "community" -> { BrotherFirebasePush.enableCommunity(context); communityEnabled = true }
            }
            permissionDenied = false
        } else {
            permissionDenied = true
        }
        pendingKind = null
    }

    fun activate(kind: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingKind = kind
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            when (kind) {
                "update" -> { BrotherFirebasePush.enable(context); updateEnabled = true }
                "community" -> { BrotherFirebasePush.enableCommunity(context); communityEnabled = true }
            }
        }
    }

    LaunchedEffect(Unit) {
        groupUrl = getOfficialGroup()
    }

    fun openHttps(url: String) {
        if (!url.startsWith("https://")) return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Não há aplicativo disponível para abrir o link.", Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, "O Android bloqueou a abertura do link.", Toast.LENGTH_LONG).show()
        }
    }

    val productPage = BuildConfig.WORDPRESS_URL.trimEnd('/') + COMMUNITY_FALLBACK_PAGE

    if (feedbackEditorOpen) {
        AlertDialog(
            onDismissRequest = { feedbackEditorOpen = false },
            title = { Text("Seu feedback para o Brother Matrizes") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Escolha o tipo de mensagem e descreva o que aconteceu ou a melhoria desejada."
                    )
                    OutlinedButton(
                        onClick = {
                            feedbackCategory = if (feedbackCategory == "Sugestão") "Problema" else "Sugestão"
                        }
                    ) { Text("Tipo: " + feedbackCategory + " (toque para alterar)") }
                    OutlinedTextField(
                        value = feedbackMessage,
                        onValueChange = { feedbackMessage = it.take(2500) },
                        label = { Text("Mensagem") },
                        placeholder = { Text("Descreva o problema ou sua sugestão...") },
                        minLines = 4,
                        maxLines = 7,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        if (!BrotherFeedbackDelivery.canSend(feedbackMessage))
                            "Escreva seu relato para escolher como enviar."
                        else "${feedbackMessage.length}/2500 caracteres • Mensagem pronta",
                        color = FioTextMuted
                    )
                    Text(
                        "Ao escolher WhatsApp ou e-mail, a mensagem será preenchida para você conferir e enviar.",
                        color = FioTextMuted
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = BrotherFeedbackDelivery.canSend(feedbackMessage),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FioGold,
                            contentColor = FioBackground
                        ),
                        onClick = {
                            val content = BrotherFeedbackDelivery.message(
                                feedbackCategory, feedbackMessage, BuildConfig.VERSION_NAME
                            )
                            val url = "https://wa.me/" +
                                BrotherFeedbackDelivery.WHATSAPP_PHONE +
                                "?text=" + Uri.encode(content)
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                feedbackEditorOpen = false
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(
                                    context, "Não foi possível abrir o WhatsApp.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (_: SecurityException) {
                                Toast.makeText(
                                    context, "O Android bloqueou o acesso ao WhatsApp.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    ) { Text("Enviar pelo WhatsApp") }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = BrotherFeedbackDelivery.canSend(feedbackMessage),
                        onClick = {
                            val content = BrotherFeedbackDelivery.message(
                                feedbackCategory, feedbackMessage, BuildConfig.VERSION_NAME
                            )
                            val mail = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse(
                                    "mailto:" + BrotherFeedbackDelivery.SUPPORT_EMAIL
                                )
                                putExtra(
                                    Intent.EXTRA_EMAIL,
                                    arrayOf(BrotherFeedbackDelivery.SUPPORT_EMAIL)
                                )
                                putExtra(
                                    Intent.EXTRA_SUBJECT,
                                    BrotherFeedbackDelivery.subject(feedbackCategory)
                                )
                                putExtra(Intent.EXTRA_TEXT, content)
                            }
                            try {
                                context.startActivity(
                                    Intent.createChooser(mail, "Enviar feedback por e-mail")
                                )
                                feedbackEditorOpen = false
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(
                                    context, "Nenhum aplicativo de e-mail foi encontrado.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (_: SecurityException) {
                                Toast.makeText(
                                    context, "O Android bloqueou a abertura do e-mail.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    ) { Text("Enviar por e-mail") }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { feedbackEditorOpen = false }
                    ) { Text("Cancelar") }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = onBack) { Text("Voltar") }
        Text("Comunidade Brother Matrizes", color = FioText,
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        Text("Novidades, tutoriais e sugestões para a evolução do seu aplicativo.",
            color = FioTextMuted)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = FioSurface)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Atualizações oficiais", color = FioText, fontWeight = FontWeight.Bold)
                Text("Versão instalada: " + BuildConfig.VERSION_NAME, color = FioTextMuted)
                Text("Acompanhe as versões publicadas e o que melhorou em cada uma.",
                    color = FioTextMuted)
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { openHttps(productPage) }
                ) { Text("Ver notas e novidades") }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = FioSurface)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Grupo oficial de feedbacks", color = FioText,
                    fontWeight = FontWeight.Bold)
                Text("Converse com outros usuários, relate erros e envie sugestões para as próximas versões.",
                    color = FioTextMuted)
                Text("Atenção: membros do grupo podem visualizar seu número de telefone. Não envie dados pessoais, senhas ou arquivos sigilosos.", color = FioTextMuted)
                if (groupUrl != null) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = FioGold, contentColor = FioBackground),
                        onClick = { groupUrl?.let(::openHttps) }
                    ) { Text("Entrar no grupo") }
                } else {
                    Text("O grupo poderá ser acessado aqui assim que o convite estiver disponível. Consulte a central no site.",
                        color = FioTextMuted)
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = FioSurface)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Envie seu feedback", color = FioText, fontWeight = FontWeight.Bold)
                Text("Relate um erro ou sugira melhorias. Não inclua dados sensíveis.",
                    color = FioTextMuted)
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = FioGold, contentColor = FioBackground),
                    onClick = { feedbackEditorOpen = true }
                ) { Text("Enviar sugestão ou relatar problema") }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { openHttps(productPage) }
                ) { Text("Abrir página do Brother Matrizes no site") }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = FioSurface)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Notificações push", color = FioText, fontWeight = FontWeight.Bold)
                Text(
                    "Escolha os avisos enviados pelo Firebase. Os alertas locais de atualização continuam independentes deste ajuste.",
                    color = FioTextMuted
                )
                Text("Push de novas versões: " + (if (updateEnabled) "Ativado" else "Desativado"), color = FioText)
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (updateEnabled) {
                            BrotherFirebasePush.disable(context)
                            updateEnabled = false
                        } else activate("update")
                    }
                ) { Text(if (updateEnabled) "Desativar push de versões" else "Ativar push de versões") }
                Text("Novidades da comunidade: " + if (communityEnabled) "Ativadas" else "Desativadas", color = FioText)
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (communityEnabled) {
                            BrotherFirebasePush.disableCommunity(context)
                            communityEnabled = false
                        } else activate("community")
                    }
                ) { Text(if (communityEnabled) "Desativar avisos da comunidade" else "Ativar avisos da comunidade") }
                if (permissionDenied || !BrotherUpdateNotificationWorker.notificationsAllowed(context)) {
                    Text("O Android bloqueou as notificações. Para recebê-las, permita avisos nas configurações do aplicativo.", color = FioTextMuted)
                }
                Text(
                    "Avisos dependem da conexão, das configurações do Android e da publicação oficial. Nenhuma licença ou projeto é enviado.",
                    color = FioTextMuted
                )
                if (BuildConfig.ENABLE_FCM_DIAGNOSTIC) {
                    Text("Diagnóstico de notificações — somente homologação", color = FioText,
                        fontWeight = FontWeight.Bold)
                    Text(
                        "Copie o identificador deste aparelho somente para cadastrar no segredo do GitHub. " +
                            "Não compartilhe em mensagens, prints ou grupos.",
                        color = FioTextMuted
                    )
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = communityEnabled && BrotherUpdateNotificationWorker.notificationsAllowed(context),
                        onClick = {
                            fcmDiagnosticStatus = "Consultando o Firebase neste aparelho..."
                            FirebaseMessaging.getInstance().token
                                .addOnSuccessListener { token ->
                                    if (token.length !in 60..4096) {
                                        fcmDiagnosticStatus = "Firebase não disponibilizou um identificador válido."
                                    } else {
                                        val clipboard = context.getSystemService(
                                            android.content.Context.CLIPBOARD_SERVICE
                                        ) as ClipboardManager
                                        val clip = ClipData.newPlainText(
                                            "Brother Matrizes — Identificador de teste", token
                                        )
                                        if (Build.VERSION.SDK_INT >= 33) {
                                            clip.description.extras = PersistableBundle().apply {
                                                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                                            }
                                        }
                                        clipboard.setPrimaryClip(clip)
                                        fcmDiagnosticStatus = "Identificador copiado. Cadastre em GitHub Secrets como BROTHER_FCM_TEST_DEVICE_TOKEN."
                                    }
                                }
                                .addOnFailureListener {
                                    fcmDiagnosticStatus = "Não foi possível consultar o Firebase. Verifique a internet."
                                }
                        }
                    ) { Text("Copiar identificador de teste") }
                    if (!communityEnabled) {
                        Text("Ative os avisos da comunidade antes do teste.", color = FioTextMuted)
                    }
                    fcmDiagnosticStatus?.let { status ->
                        Text(status, color = FioTextMuted)
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("A compra ou assinatura oferece acesso aos recursos contratados. Seu apoio também contribui com correções, manutenção e evolução contínua.",
            color = FioTextMuted)
    }
}
