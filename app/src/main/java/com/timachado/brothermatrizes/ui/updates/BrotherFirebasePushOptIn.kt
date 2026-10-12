package com.timachado.brothermatrizes.ui.updates

import android.Manifest
import android.content.Context
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import com.timachado.brothermatrizes.core.updates.BrotherFirebasePush

/**
 * Consentimento independente para FCM: usuários da versão 1.0.5 autorizaram
 * apenas verificações locais. O app NÃO se inscreve em push automaticamente.
 */
@Composable
fun BrotherFirebasePushOptIn() {
    val context = LocalContext.current
    var asked by remember {
        mutableStateOf(BrotherFirebasePush.wasAsked(context))
    }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) BrotherFirebasePush.enable(context)
        else BrotherFirebasePush.decline(context)
        asked = true
    }

    LaunchedEffect(Unit) {
        BrotherFirebasePush.sync(context)
    }

    if (asked) return

    AlertDialog(
        onDismissRequest = {
            BrotherFirebasePush.decline(context)
            asked = true
        },
        title = { Text("Receber novidades do Brother Matrizes?") },
        text = {
            Text(
                "Além da busca automática por atualizações, a T.I. Machado " +
                    "poderá enviar avisos pelo Firebase Cloud Messaging para este " +
                    "celular. Não envolve sua conta, matrizes ou pagamentos. " +
                    "Você poderá desativar os avisos na tela Comunidade."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    BrotherFirebasePush.enable(context)
                    asked = true
                }
            }) { Text("Ativar notificações push") }
        },
        dismissButton = {
            TextButton(onClick = {
                BrotherFirebasePush.decline(context)
                asked = true
            }) { Text("Agora não") }
        }
    )
}
