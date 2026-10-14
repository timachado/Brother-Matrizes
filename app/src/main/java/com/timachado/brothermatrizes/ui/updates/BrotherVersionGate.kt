package com.timachado.brothermatrizes.ui.updates

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.timachado.brothermatrizes.BuildConfig
import com.timachado.brothermatrizes.core.updates.BrotherApkUpdater
import com.timachado.brothermatrizes.core.updates.BrotherUpdateClient
import kotlinx.coroutines.launch

/**
 * Somente versões compiladas com este componente podem ser bloqueadas remotamente.
 * O APK 1.0.0 original não possui este gate e não pode ser alterado remotamente.
 * Nunca ativar minSupportedVersionCode sem APK estável assinado, hash válido e teste de upgrade.
 */
@Composable
fun BrotherVersionGate(resumeRequest: Int, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("brother_verified_version_gate", Context.MODE_PRIVATE)
    }
    val checker = remember {
        BrotherUpdateClient(
            BuildConfig.WORDPRESS_URL.trimEnd('/') +
                "/wp-json/tim-brother-updates/v1/latest?channel=stable"
        )
    }
    val previousMinimum = remember(context) { prefs.getLong("minimum", 0L) }
    var required by remember {
        mutableStateOf(
            if (previousMinimum > BuildConfig.VERSION_CODE.toLong()) {
                cachedCriticalRelease(context)
            } else null
        )
    }
    var checking by remember { mutableStateOf(true) }
    var retry by remember { mutableStateOf(0) }
    var installing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(resumeRequest, retry) {
        // Ao voltar do seletor do Google Drive, apenas consultar atualizações.
        // Reativar "checking" desmontava BrotherMatrizesApp e destruía o
        // ActivityResultLauncher antes que o arquivo escolhido fosse entregue.
        // O loading de tela inteira pertence SOMENTE à primeira inicialização.
        when (val answer = checker.check(context.applicationContext)) {
            is BrotherUpdateClient.Result.Available -> {
                if (answer.critical) {
                    required = answer.release
                    prefs.edit()
                        .putLong("minimum", answer.release.minSupportedVersionCode)
                        .putLong("code", answer.release.versionCode)
                        .putString("name", answer.release.versionName)
                        .putString("url", answer.release.downloadUrl)
                        .putString("sha", answer.release.sha256)
                        .putString("notes", answer.release.releaseNotes)
                        .putString("published", answer.release.publishedAt)
                        .apply()
                } else {
                    required = null
                    prefs.edit().clear().apply()
                }
            }
            BrotherUpdateClient.Result.UpToDate -> {
                required = null
                prefs.edit().clear().apply()
            }
            else -> {
                // Falha de rede não inventa bloqueio novo; mantém bloqueio
                // previamente confirmado no manifesto oficial.
                message = "Não foi possível verificar atualizações. Confira sua conexão."
            }
        }
        checking = false
    }

    val release = required
    BackHandler(enabled = release != null) {}
    if (release != null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("BROTHER MATRIZES")
            Text("Atualização obrigatória")
            Text("Para manter a segurança e a compatibilidade, atualize para " +
                release.versionName + ". Seus projetos e sua licença serão preservados. " +
                "Não desinstale o aplicativo.")
            message?.let { Text(it) }
            if (installing) {
                CircularProgressIndicator()
                progress?.let { Text("Baixando atualização: $it%") }
            }
            Button(
                enabled = !installing,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                onClick = {
                    scope.launch {
                        installing = true
                        message = null
                        try {
                            val result = BrotherApkUpdater.download(
                                context.applicationContext, release,
                                onProgress = { progress = it }
                            )
                            result.fold(
                                onSuccess = { apk ->
                                    val opened = runCatching {
                                        BrotherApkUpdater.launchInstaller(context, apk)
                                    }.getOrDefault(false)
                                    message = if (opened) {
                                        "Confirme a instalação pelo Android."
                                    } else "Autorize a instalação de APK para o Brother Matrizes e tente novamente."
                                },
                                onFailure = {
                                    message = "Falha no download verificado. Tente novamente."
                                }
                            )
                        } finally {
                            installing = false
                        }
                    }
                }
            ) { Text("Atualizar agora") }
            OutlinedButton(
                enabled = !installing,
                onClick = { retry++ },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Verificar novamente") }
        }
    } else if (checking) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Text("Verificando compatibilidade da versão…")
        }
    } else content()
}

private fun cachedCriticalRelease(context: Context): BrotherUpdateClient.Release? {
    val prefs = context.getSharedPreferences("brother_verified_version_gate", Context.MODE_PRIVATE)
    return runCatching {
        val min = prefs.getLong("minimum", 0L)
        val code = prefs.getLong("code", 0L)
        val sha = prefs.getString("sha", "").orEmpty()
        val url = prefs.getString("url", "").orEmpty()
        val name = prefs.getString("name", "").orEmpty()
        require(min > BuildConfig.VERSION_CODE.toLong() && code >= min)
        require(sha.matches(Regex("[a-fA-F0-9]{64}")) && url.startsWith("https://"))
        BrotherUpdateClient.Release(
            versionName = name,
            versionCode = code,
            minSupportedVersionCode = min,
            downloadUrl = url,
            sha256 = sha,
            releaseNotes = prefs.getString("notes", "").orEmpty(),
            publishedAt = prefs.getString("published", "").orEmpty()
        )
    }.getOrNull()
}
