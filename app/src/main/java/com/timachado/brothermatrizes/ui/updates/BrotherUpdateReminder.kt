package com.timachado.brothermatrizes.ui.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.timachado.brothermatrizes.core.updates.BrotherUpdateClient

/**
 * Aviso pós-lançamento: não interrompe o app quando WordPress estiver offline.
 * Consulta no máximo uma vez a cada 12h na abertura; botão manual do cartão
 * faz consulta independente. O usuário pode adiar por 24h.
 */
@Composable
fun BrotherUpdateReminder(endpoint: String) {
    val context = LocalContext.current
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
        onDismissRequest = { postpone() },
        title = { Text("Atualização do Brother Matrizes") },
        text = {
            Text(buildString {
                append("Versão " + result.release.versionName + " disponível.")
                if (result.critical) append("\n\nEsta versão inclui uma atualização importante.")
                if (result.release.releaseNotes.isNotBlank()) {
                    append("\n\n")
                    append(result.release.releaseNotes)
                }
            })
        },
        confirmButton = {
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.release.downloadUrl))
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                runCatching { context.startActivity(intent) }
                available = null
            }) { Text("Atualizar agora") }
        },
        dismissButton = {
            TextButton(onClick = { postpone() }) { Text("Depois") }
        }
    )
}