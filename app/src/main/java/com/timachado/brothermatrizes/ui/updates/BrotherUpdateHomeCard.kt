package com.timachado.brothermatrizes.ui.updates

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

    LaunchedEffect(refreshRequest) {
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
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "NOVIDADE • BROTHER MATRIZES",
                        color = FioGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "v" + release.versionName,
                        color = FioGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "Uma nova atualização chegou!",
                    color = FioText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Confira o que mudou antes de atualizar seu aplicativo.",
                    color = FioTextMuted,
                    fontSize = 12.sp
                )
                val notes = UpdateInAppNoticeRules.notes(release.releaseNotes)
                if (notes.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text("Novidades desta versão", color = FioText,
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        if (expanded || notes.length <= 300) notes
                        else notes.take(300).trimEnd() + "…",
                        color = FioTextMuted,
                        fontSize = 12.sp
                    )
                    if (notes.length > 300) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Mostrar menos" else "Ler todas as novidades",
                                color = FioGold)
                        }
                    }
                }
                Button(
                    onClick = {
                        if (!busy) {
                            scope.launch {
                                busy = true
                                status = "Baixando e validando APK oficial…"
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
                                                "APK verificado. Confirme a instalação no Android."
                                            else
                                                "Autorize a instalação pelo Brother Matrizes e volte para concluir."
                                        }.onFailure {
                                            status = it.message ?: "Não foi possível abrir o instalador."
                                        }
                                    },
                                    onFailure = {
                                        status = it.message ?: "Falha no download. Tente novamente."
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
                    Text(if (busy) "Preparando atualização…" else "Atualizar agora",
                        fontWeight = FontWeight.Bold)
                }
                if (status != null) {
                    Text(status.orEmpty(), color = FioTextMuted, fontSize = 12.sp)
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
                                status = it.message ?: "Não foi possível abrir o instalador."
                            }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Concluir instalação")
                    }
                }
                TextButton(
                    onClick = {
                        prefs.edit().putLong("dismissed_version_code", release.versionCode).apply()
                        dismissedVersion = release.versionCode
                    }
                ) {
                    Text("Agora não", color = FioTextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}
