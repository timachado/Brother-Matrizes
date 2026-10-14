package com.timachado.brothermatrizes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.timachado.brothermatrizes.core.account.BrotherMatrizesAccountService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val BROTHER_PRESENCE_INTERVAL_MS = 30_000L

/**
 * Heartbeat somente enquanto o aplicativo está em primeiro plano.
 *
 * - dispara imediatamente ao abrir/retomar;
 * - repete a cada 30 s;
 * - para no background;
 * - não altera sessão, licença ou checkout se a rede falhar.
 */
@Composable
internal fun BrotherPresenceHeartbeat() {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }

    fun stopHeartbeat() {
        job?.cancel()
        job = null
    }

    fun startHeartbeat() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                BrotherMatrizesAccountService.recordPresence(context)
                delay(BROTHER_PRESENCE_INTERVAL_MS)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME -> startHeartbeat()

                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY -> stopHeartbeat()

                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            startHeartbeat()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopHeartbeat()
        }
    }
}
