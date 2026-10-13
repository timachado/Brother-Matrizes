package com.timachado.brothermatrizes.ui.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.BuildConfig
import com.timachado.brothermatrizes.core.updates.BrotherApkUpdater
import com.timachado.brothermatrizes.core.updates.BrotherUpdateClient
import com.timachado.brothermatrizes.core.updates.UpdateVersionRules
import com.timachado.brothermatrizes.ui.theme.FioGold
import kotlinx.coroutines.launch

/**
 * Não fecha ou desinstala o app nem altera contas/licenças.
 * Após o manifesto STABLE oficialmente marcar minSupportedVersionCode maior
 * que a versão instalada, a tela de atualização não pode ser dispensada.
 *
 * Não tornar o bloqueio ativo enquanto não existir APK estável publicado e
 * assinado. Nenhum mecanismo Android pode modificar APKs antigos já instalados.
 */
@Composable
fun BrotherMandatoryUpdateGate(
    resumeRequest: Int,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("brother_mandatory_update_v1", Context.MODE_PRIVATE)
    }
    val checker = remember {
        BrotherUpdateClient(
            BuildConfig.WORDPRESS_URL.trimEnd('/') +
                "/wp-json/tim-brother-updates/v1/latest?channel=stable"
        )
    }
    val cached = remember(context) { verifiedCachedRequirement(context) }
    var required by remember { mutableStateOf(cached) }
    var checking by remember { mutableStateOf(cached == null) }
    var revision by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(resumeRequest, revision) {
        if (required == null) checking = true
        when (val outcome = checker.check(context.applicationContext)) {
            is BrotherUpdateClient.Result.Available -> {
                if (outcome.critical) {
                    required = outcome.release
                    prefs.edit()
                        .putLong("minimum", outcome.release.minSupportedVersionCode)
                        .putLong("version_code", outcome.release.versionCode)
                        .putString("version_name", outcome.release.versionName)
                        .putString("hash", outcome.release.sha256)
                        .putString("download_url", outcome.release.downloadUrl)
                        .putString("notes", outcome.release.releaseNotes)
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
            else -> Unit // Cached confirmed requirement remains locked.
        }
        checking = false
    }

    val release = required
    if (release != null) {
        // Neither system Back nor tapping outside can dismiss the requirement.
        BackHandler(enabled = true) {}
        Column(
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF081B29))
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = androidx.compose.ui.graphics.Color(0xFF152E40),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("BROTHER MATRIZES", color = FioGold,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Atualização obrigatória",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Para continuar usando o aplicativo, instale a versão " +
                            release.versionName +
                            ". Esta atualização é necessária para manter a compatibilidade e a segurança das cotas.",
                        color = androidx.compose.ui.graphics.Color(0xFFC0CDD7),
                        fontSize = 13.sp
                    )
                    Text("Seus projetos e sua licença serão preservados. Não desinstale o aplicativo.",
                        color = androidx.compose.ui.graphics.Color(0xFFC0CDD7), fontSize = 12.sp)
                    if (busy) {
                        CircularProgressIndicator(color = FioGold)
                        Text(
                            progress?.let { "Baixando e verificando APK: $it%" }
                                ?: "Preparando atualização verificada…",
                            color = FioGold, fontSize = 12.sp
                        )
                    }
                    message?.let {
                        Text(it, color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                progress = null
                                message = null
                                val result = BrotherApkUpdater.download(
                                    context.applicationContext, release,
                                    onProgress = { progress = it }
                                )
                                result.fold(
                                    onSuccess = { apk ->
                                        val opened = runCatching {
                                            BrotherApkUpdater.launchInstaller(context, apk)
                                        }.getOrDefault(false)
                                        message = if (opened)
                                            "Confirme a instalação no Android. Esta tela será liberada após atualizar."
                                        else "Habilite a instalação de APKs para Brother Matrizes e tente novamente."
                                    },
                                    onFailure = {
                                        message = "Não foi possível baixar o APK oficial. " +
                                            "Verifique sua conexão e tente novamente."
                                    }
                                )
                                busy = false
                            }
                        },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FioGold,
                            contentColor = androidx.compose.ui.graphics.Color(0xFF081B29)
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(if (busy) "Atualizando…" else "Atualizar agora",
                            fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { revision++ }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Verificar novamente", color = FioGold) }
                    OutlinedButton(
                        onClick = {
                            val page = Uri.parse(
                                "https://timachado.ifree.page/aplicativos/brother-matrizes/"
                            )
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, page)
                                        .addCategory(Intent.CATEGORY_BROWSABLE)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Abrir página oficial", color = FioGold) }
                }
            }
        }
    } else if (checking) {
        Column(
            modifier = Modifier.fillMaxSize()
                .background(androidx.compose.ui.graphics.Color(0xFF081B29)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = FioGold)
            Spacer(Modifier.height(15.dp))
            Text("Verificando versão e compatibilidade…",
                color = androidx.compose.ui.graphics.Color.White)
        }
    } else {
        content()
    }
}

private fun verifiedCachedRequirement(context: Context): BrotherUpdateClient.Release? {
    val prefs = context.getSharedPreferences(
        "brother_mandatory_update_v1", Context.MODE_PRIVATE
    )
    val code = prefs.getLong("version_code", 0L)
    val minimum = prefs.getLong("minimum", 0L)
    val name = prefs.getString("version_name", "").orEmpty()
    val hash = prefs.getString("hash", "").orEmpty()
    val url = prefs.getString("download_url", "").orEmpty()
    if (!UpdateVersionRules.isCritical(BuildConfig.VERSION_CODE.toLong(), minimum) ||
        !UpdateVersionRules.isUpdateAvailable(BuildConfig.VERSION_CODE.toLong(), code) ||
        !hash.matches(Regex("[a-f0-9]{64}")) ||
        !url.startsWith("https://timachado.ifree.page/") ||
        name.isBlank()
    ) return null
    return BrotherUpdateClient.Release(
        versionName = name, versionCode = code, minSupportedVersionCode = minimum,
        downloadUrl = url, sha256 = hash, releaseNotes = prefs.getString("notes", "").orEmpty(),
        publishedAt = ""
    )
}
