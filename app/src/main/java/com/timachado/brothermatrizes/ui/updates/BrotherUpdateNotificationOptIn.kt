package com.timachado.brothermatrizes.ui.updates

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.timachado.brothermatrizes.core.updates.BrotherUpdateNotificationWorker

/**
 * Pergunta UMA vez, com explicação, antes da permissão Android 13+.
 * Uma recusa não afeta login, importação, nem o uso do aplicativo.
 */
@Composable
fun BrotherUpdateNotificationOptIn() {
    if (Build.VERSION.SDK_INT < 33) return

    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("brother_updates_permission", Context.MODE_PRIVATE)
    }
    var dismissed by remember {
        mutableStateOf(preferences.getBoolean("asked", false))
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        dismissed = true
    }
    if (dismissed || BrotherUpdateNotificationWorker.notificationsAllowed(context)) return

    AlertDialog(
        onDismissRequest = {
            preferences.edit().putBoolean("asked", true).apply()
            dismissed = true
        },
        title = { Text("Receber avisos de novas versões?") },
        text = {
            Text(
                "O Brother Matrizes pode avisar no seu celular quando houver " +
                    "uma atualização. As verificações acontecem periodicamente, " +
                    "sem acesso às suas matrizes ou à sua assinatura. " +
                    "Você poderá desativar os avisos nas configurações do Android."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                preferences.edit().putBoolean("asked", true).apply()
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }) { Text("Ativar avisos") }
        },
        dismissButton = {
            TextButton(onClick = {
                preferences.edit().putBoolean("asked", true).apply()
                dismissed = true
            }) { Text("Agora não") }
        }
    )
}
