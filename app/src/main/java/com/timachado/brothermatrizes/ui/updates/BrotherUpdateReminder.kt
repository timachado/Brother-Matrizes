package com.timachado.brothermatrizes.ui.updates

import android.content.Context
import java.io.File
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.timachado.brothermatrizes.core.updates.BrotherUpdateClient
import com.timachado.brothermatrizes.core.updates.BrotherApkUpdater
import kotlinx.coroutines.launch

/**
 * Aviso pós-lançamento: não interrompe o app quando WordPress estiver offline.
 * Consulta no máximo uma vez a cada 12h na abertura; botão manual do cartão
 * faz consulta independente. O usuário pode adiar por 24h.
 */
@Composable
fun BrotherUpdateReminder(endpoint: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    val prefs = remember(context) {
        context.getSharedPreferences("brother_update_reminder", Context.MODE_PRIVATE)
    }
    var available by remember(endpoint) {
        mutableStateOf<BrotherUpdateClient.Result.Available?>(null)
    }

    LaunchedEffect(endpoint) {
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong("last_check_at", 0L)
        if (lastCheck > 0L && now - lastCheck in 0 until 12L * 60 * 60 * 1000) {
            return@LaunchedEffect
        }
        val result = BrotherUpdateClient(endpoint).check(context.applicationContext)
        if (result !is BrotherUpdateClient.Result.Error) {
            prefs.edit().putLong("last_check_at", now).apply()
        }
        if (result is BrotherUpdateClient.Result.Available) {
            val dismissed = prefs.getLong("dismissed_code", -1L) == result.release.versionCode &&
                now - prefs.getLong("dismissed_at", 0L) in 0 until 24L * 60 * 60 * 1000
            if (!dismissed) available = result
        }
    }

    val result = available ?: return
    fun postpone() {
        prefs.edit()
            .putLong("dismissed_code", result.release.versionCode)
            .putLong("dismissed_at", System.currentTimeMillis())
            .apply()
        available = null
    }

    AlertDialog(
        onDismissRequest = { if (!busy) postpone() },
        title = { Text("Atualização do Brother Matrizes") },
        text = {
            Text(buildString {
                append("Versão " + result.release.versionName + " disponível.")
                if (result.critical) append("\n\nEsta versão inclui uma atualização importante.")
                if (result.release.releaseNotes.isNotBlank()) {
                    append("\n\n")
                    append(result.release.releaseNotes)
                }
                if (busy) {
                    append("\n\n")
                    append(if (progress != null) "Baixando e verificando APK: ${progress}%" else "Preparando download verificado…")
                } else if (message != null) {
                    append("\n\n")
                    append(message)
                }
            })
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    val cached = downloadedApk
                    if (cached != null) {
                        runCatching { BrotherApkUpdater.launchInstaller(context, cached) }
                            .onSuccess { opened ->
                                if (opened) available = null
                                else message = "Autorize a instalação por Brother Matrizes nas configurações e retorne para concluir."
                            }.onFailure {
                                message = "Não foi possível abrir o instalador do Android. Tente novamente."
                            }
                    } else {
                        scope.launch {
                            busy = true
                            progress = null
                            message = null
                            val outcome = BrotherApkUpdater.download(
                                context.applicationContext, result.release,
                                onProgress = { percent -> progress = percent }
                            )
                            outcome.fold(
                                onSuccess = { apk ->
                                    downloadedApk = apk
                                    runCatching { BrotherApkUpdater.launchInstaller(context, apk) }
                                        .onSuccess { opened ->
                                            if (opened) available = null
                                            else message = "Autorize a instalação por Brother Matrizes nas configurações e retorne para concluir."
                                        }.onFailure {
                                            message = "APK conferido. Toque em Concluir instalação para tentar novamente."
                                        }
                                },
                                onFailure = {
                                    message = "Não foi possível baixar o APK verificado. Confira sua conexão e tente novamente."
                                }
                            )
                            busy = false
                        }
                    }
                }
            ) {
                Text(if (busy) "Baixando…" else if (downloadedApk != null) "Concluir instalação" else "Atualizar agora")
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = { postpone() }) { Text("Depois") }
        }
    )
}