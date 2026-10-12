package com.timachado.brothermatrizes.ui.updates

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.net.Uri
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.BuildConfig
import com.timachado.brothermatrizes.core.updates.BrotherApkUpdater
import com.timachado.brothermatrizes.core.updates.BrotherInAppUpdateEvents
import com.timachado.brothermatrizes.core.updates.BrotherUpdateClient
import com.timachado.brothermatrizes.core.updates.UpdateInAppNoticeRules
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.launch
import java.io.File

/**
 * Central de novidades da Home, independente da autorização de notificações.
 * Não exibe card para a própria versão instalada ou para versões antigas.
 * A cada abertura/retorno ao app a consulta é refeita, com os dados oficiais.
 */
@Composable
fun BrotherUpdateHomeCard(refreshRequest: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) {
        context.getSharedPreferences("brother_update_home_cards", Context.MODE_PRIVATE)
    }
    val client = remember {
        BrotherUpdateClient(
            BuildConfig.WORDPRESS_URL.trimEnd('/') +
                "/wp-json/tim-brother-updates/v1/latest?channel=stable"
        )
    }
    var available by remember { mutableStateOf<BrotherUpdateClient.Result.Available?>(null) }
    var dismissedVersion by remember {
        mutableStateOf(prefs.getLong("dismissed_version_code", 0L))
    }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var receivedNotification by remember { mutableStateOf(0) }

    // Mesmo com o aplicativo aberto na tela inicial, uma notificação FCM
    // atualiza imediatamente o cartão. Receiver não exportado para outros apps.
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(source: Context?, intent: Intent?) {
                if (intent?.action == BrotherInAppUpdateEvents.ACTION) {
                    receivedNotification++
                }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(BrotherInAppUpdateEvents.ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    LaunchedEffect(refreshRequest, receivedNotification) {
        val checked = client.check(context.applicationContext)
        available = checked as? BrotherUpdateClient.Result.Available
        // Não impedir o usuário de dispensar a mesma versão após atualização do estado.
        dismissedVersion = prefs.getLong("dismissed_version_code", 0L)
    }

    val result = available
    if (result != null && UpdateInAppNoticeRules.shouldShow(
            BuildConfig.VERSION_CODE.toLong(),
            result.release.versionCode,
            dismissedVersion
        )
    ) {
        val release = result.release
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = FioSurface),
            border = BorderStroke(1.dp, FioGold.copy(alpha = 0.36f)),
            shape = RoundedCornerShape(26.dp)
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.horizontalGradient(
                            listOf(FioGold.copy(alpha = 0.11f), FioSurface, FioSurface)
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "✦  NOVA VERSÃO",
                        color = FioGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "v" + release.versionName,
                        color = FioGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(FioGold.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
                Text(
                    "Seu Brother ficou ainda melhor.",
                    color = FioText,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Uma nova versão está pronta para você.",
                    color = FioTextMuted,
                    fontSize = 12.sp
                )
                val highlights = UpdateInAppNoticeRules.highlights(release.releaseNotes)
                if (expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("O que mudou", color = FioText,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        highlights.forEach { highlight ->
                            Text("•  " + highlight, color = FioTextMuted, fontSize = 12.sp)
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("✦ Mais fluidez", color = FioGold, fontSize = 11.sp)
                        Text("•", color = FioTextMuted, fontSize = 11.sp)
                        Text("✦ Evolução contínua", color = FioGold, fontSize = 11.sp)
                    }
                }
                Button(
                    onClick = {
                        if (!busy) {
                            scope.launch {
                                busy = true
                                status = "Preparando sua atualização…"
                                val outcome = BrotherApkUpdater.download(
                                    context.applicationContext, release
                                )
                                outcome.fold(
                                    onSuccess = { apk ->
                                        downloadedApk = apk
                                        runCatching {
                                            BrotherApkUpdater.launchInstaller(context, apk)
                                        }.onSuccess { opened ->
                                            status = if (opened)
                                                "Tudo pronto! Confirme a instalação no Android."
                                            else
                                                "Autorize a instalação e volte para concluir."
                                        }.onFailure {
                                            status = "Não foi possível abrir a instalação. Tente novamente."
                                        }
                                    },
                                    onFailure = {
                                        status = "Não conseguimos baixar agora. Tente novamente."
                                    }
                                )
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FioGold,
                        contentColor = FioBackground
                    )
                ) {
                    Text(if (busy) "Preparando atualização…" else "Atualizar agora  ↗",
                        fontWeight = FontWeight.Bold)
                }
                if (status != null) {
                    Text(status.orEmpty(), color = FioTextMuted, fontSize = 12.sp)
                }
                if (status != null && downloadedApk == null && !busy &&
                    !status.orEmpty().startsWith("Baixando")) {
                    OutlinedButton(
                        onClick = {
                            val url = release.downloadUrl
                            if (url.startsWith("https://timachado.ifree.page/")) {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        .addCategory(Intent.CATEGORY_BROWSABLE))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Baixar pelo site oficial") }
                }
                if (downloadedApk != null && !busy) {
                    OutlinedButton(onClick = {
                        val apk = downloadedApk ?: return@OutlinedButton
                        runCatching { BrotherApkUpdater.launchInstaller(context, apk) }
                            .onSuccess { opened ->
                                status = if (opened) "Confirme a instalação no Android."
                                else "Autorize esta origem nas configurações e retorne."
                            }
                            .onFailure {
                                status = "Não foi possível abrir a instalação. Tente novamente."
                            }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Concluir instalação")
                    }
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Ocultar novidades" else "Ver novidades",
                        color = FioGold, fontSize = 12.sp)
                }
                TextButton(
                    onClick = {
                        prefs.edit().putLong("dismissed_version_code", release.versionCode).apply()
                        dismissedVersion = release.versionCode
                    }
                ) {
                    Text("Depois", color = FioTextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}
