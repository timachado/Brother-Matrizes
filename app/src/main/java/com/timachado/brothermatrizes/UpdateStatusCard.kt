package com.timachado.brothermatrizes

import android.content.Intent
import java.io.File
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.timachado.brothermatrizes.core.updates.BrotherApkUpdater
import com.timachado.brothermatrizes.core.updates.UpdateInAppNoticeRules
import com.timachado.brothermatrizes.core.updates.BrotherUpdateNotificationWorker
import com.timachado.brothermatrizes.core.updates.BrotherFirebasePush
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    var downloadBusy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var downloadStatus by remember { mutableStateOf<String?>(null) }
    var stagedUpdate by remember { mutableStateOf<File?>(null) }
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
                        Text(UpdateInAppNoticeRules.notes(result.release.releaseNotes), color = FioTextMuted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (!downloadBusy) {
                                scope.launch {
                                    downloadBusy = true
                                    downloadStatus = "Preparando sua atualização…"
                                    progress = null
                                    val downloaded = BrotherApkUpdater.download(
                                        context.applicationContext, result.release,
                                        onProgress = { percent -> progress = percent }
                                    )
                                    downloaded.fold(
                                        onSuccess = { file ->
                                            stagedUpdate = file
                                            runCatching {
                                                BrotherApkUpdater.launchInstaller(context, file)
                                            }.onSuccess { launched ->
                                                downloadStatus = if (launched) {
                                                    "Tudo pronto! Confirme a instalação no Android."
                                                } else {
                                                    "Permita instalação por Brother Matrizes nas configurações e volte para continuar."
                                                }
                                            }.onFailure { problem ->
                                                downloadStatus = "Não foi possível iniciar a instalação. Tente novamente."
                                            }
                                        },
                                        onFailure = { problem ->
                                            downloadStatus = "Não conseguimos baixar agora. Tente novamente."
                                        }
                                    )
                                    downloadBusy = false
                                }
                            }
                        },
                        enabled = !downloadBusy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FioGold,
                            contentColor = FioBackground
                        )
                    ) {
                        Text(
                            if (downloadBusy) (progress?.let { "Baixando atualização… $it%" } ?: "Preparando atualização…") else "Atualizar agora",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (downloadStatus != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(downloadStatus.orEmpty(), color = FioTextMuted, fontSize = 11.sp)
                    }
                    // A hospedagem pode exibir desafio anti-bot para clientes HTTPS nativos.
                    // Nunca burlar o Play Protect: oferecer a rota oficial no navegador somente
                    // quando o download interno falhar, preservando escolha do usuário.
                    if (downloadStatus != null && stagedUpdate == null && !downloadBusy &&
                        !downloadStatus.orEmpty().startsWith("Baixando")) {
                        OutlinedButton(
                            onClick = {
                                val safeUrl = result.release.downloadUrl
                                if (safeUrl.startsWith("https://timachado.ifree.page/")) {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl))
                                                .addCategory(Intent.CATEGORY_BROWSABLE)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Baixar pelo site oficial") }
                    }
                    val savedApk = stagedUpdate
                    if (savedApk != null && !downloadBusy) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    BrotherApkUpdater.launchInstaller(context, savedApk)
                                }.onSuccess { launched ->
                                    downloadStatus = if (launched) {
                                        "APK verificado. Confirme a instalação no Android."
                                    } else {
                                        "Autorize esta instalação e retorne ao aplicativo."
                                    }
                                }.onFailure { problem ->
                                    downloadStatus = "Não foi possível iniciar a instalação. Tente novamente."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Concluir instalação") }
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
                    "Não conseguimos verificar agora. Tente novamente em instantes.",
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