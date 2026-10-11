package com.timachado.brothermatrizes

import android.content.Intent
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.updates.BrotherUpdateClient
import com.timachado.brothermatrizes.core.updates.BrotherUpdateNotificationWorker
import com.timachado.brothermatrizes.core.updates.BrotherFirebasePush
import com.google.firebase.messaging.FirebaseMessaging
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted

/**
 * Preserva o cartão Minha Conta existente e substitui somente a origem da
 * versão (GitHub antigo) pelo manifesto oficial do WordPress T.I. Machado.
 */
@Composable
fun UpdateStatusCard() {
    val context = LocalContext.current
    val endpoint = remember {
        BuildConfig.WORDPRESS_URL.trimEnd('/') +
            "/wp-json/tim-brother-updates/v1/latest?channel=stable"
    }
    val client = remember(endpoint) { BrotherUpdateClient(endpoint) }
    var revision by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<BrotherUpdateClient.Result?>(null) }

    LaunchedEffect(revision, endpoint) {
        state = null
        state = client.check(context.applicationContext)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FioSurface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "Atualizações",
                color = FioText,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                "Versão instalada: " + BuildConfig.VERSION_NAME,
                modifier = Modifier.padding(top = 4.dp),
                color = FioTextMuted,
                fontSize = 10.sp
            )
            Spacer(Modifier.height(10.dp))
            when (val result = state) {
                null -> Text(
                    "Verificando se existe uma nova versão...",
                    color = FioTextMuted,
                    fontSize = 11.sp
                )
                is BrotherUpdateClient.Result.Available -> {
                    Text(
                        "Nova versão disponível: " + result.release.versionName,
                        color = FioGold,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    if (result.critical) {
                        Text(
                            "Atualização importante de segurança ou compatibilidade.",
                            color = FioText,
                            fontSize = 11.sp
                        )
                    }
                    if (result.release.releaseNotes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(result.release.releaseNotes, color = FioTextMuted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(result.release.downloadUrl)
                            ).addCategory(Intent.CATEGORY_BROWSABLE)
                            runCatching { context.startActivity(intent) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FioGold,
                            contentColor = FioBackground
                        )
                    ) {
                        Text("Atualizar agora", fontWeight = FontWeight.Bold)
                    }
                }
                BrotherUpdateClient.Result.UpToDate -> Text(
                    "Seu Brother Matrizes está atualizado.",
                    color = FioTextMuted, fontSize = 11.sp
                )
                BrotherUpdateClient.Result.NotPublished -> Text(
                    "Nenhuma atualização foi publicada neste canal.",
                    color = FioTextMuted, fontSize = 11.sp
                )
                is BrotherUpdateClient.Result.Error -> Text(
                    result.message + " O aplicativo continua funcionando normalmente.",
                    color = FioTextMuted, fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { revision++ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Verificar novamente")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Avisos no celular",
                color = FioText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            val allowed = BrotherUpdateNotificationWorker.notificationsAllowed(context)
            Text(
                if (allowed)
                    "Permitidos. O Android verifica novas versões periodicamente."
                else
                    "Desativados neste aparelho. Permita notificações para receber alertas de novas versões.",
                color = FioTextMuted,
                fontSize = 11.sp
            )
            var pushEnabled by remember { mutableStateOf(BrotherFirebasePush.isEnabled(context)) }
            var diagnosticStatus by remember { mutableStateOf("") }
            Spacer(Modifier.height(8.dp))
            Text(
                if (pushEnabled && allowed)
                    "Firebase Push: ativado. Você receberá avisos das novas versões."
                else
                    "Firebase Push: desativado. Ative para receber avisos enviados pela T.I. Machado.",
                color = FioTextMuted,
                fontSize = 11.sp
            )
            if (allowed) {
                OutlinedButton(
                    onClick = {
                        if (pushEnabled) BrotherFirebasePush.disable(context)
                        else BrotherFirebasePush.enable(context)
                        pushEnabled = !pushEnabled
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (pushEnabled) "Desativar notificações push" else "Ativar notificações push")
                }
            }
            if (allowed && pushEnabled) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Diagnóstico individual Firebase",
                    color = FioText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    "Copie o identificador de teste deste aparelho e salve-o somente no " +
                        "GitHub Actions Secret BROTHER_FCM_TEST_DEVICE_TOKEN. " +
                        "Não publique nem envie esse código pelo chat.",
                    color = FioTextMuted,
                    fontSize = 11.sp
                )
                OutlinedButton(
                    onClick = {
                        diagnosticStatus = "Obtendo identificador seguro do Firebase..."
                        FirebaseMessaging.getInstance().token
                            .addOnSuccessListener { token ->
                                if (token.isNotBlank()) {
                                    val clipboard = context.getSystemService(
                                        Context.CLIPBOARD_SERVICE
                                    ) as ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText("FCM do meu celular", token)
                                    )
                                    diagnosticStatus =
                                        "Identificador copiado. Cole-o diretamente no segredo " +
                                            "BROTHER_FCM_TEST_DEVICE_TOKEN no GitHub e não o compartilhe."
                                } else {
                                    diagnosticStatus = "Firebase ainda não gerou identificador. " +
                                        "Verifique a conexão e tente novamente."
                                }
                            }
                            .addOnFailureListener {
                                diagnosticStatus = "Não foi possível obter o identificador de teste."
                            }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Copiar identificador de teste") }
                if (diagnosticStatus.isNotBlank()) {
                    Text(diagnosticStatus, color = FioTextMuted, fontSize = 11.sp)
                }
            }
            if (!allowed) {
                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 26) {
                            val settings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            runCatching { context.startActivity(settings) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Configurar notificações") }
            }
        }
    }
}