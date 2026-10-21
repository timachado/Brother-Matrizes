package com.timachado.brothermatrizes.ui.updates

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.BuildConfig
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * "O que mudou" não é o aviso de nova versão ainda não instalada.
 * Exibir uma única vez ao abrir cada NOVA versão instalada, sem bloquear
 * o restante do app, sem consultar dados da conta e sem afetar downloads.
 */
@Composable
fun BrotherPostUpdateNews() {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("brother_post_update_news", Context.MODE_PRIVATE)
    }
    val installed = BuildConfig.VERSION_CODE.toLong()
    var visible by remember(installed) { mutableStateOf(false) }
    var notes by remember(installed) { mutableStateOf(BrotherNewsSource.localFallback()) }

    LaunchedEffect(installed) {
        // Recurso introduzido na 1.10.8; não mostrar a pessoas que ainda
        // usam uma versão antiga por meio de uma composição temporária.
        if (!BrotherPostUpdatePolicy.shouldDisplay(installed, prefs.getLong("shown_version_code", 0L))) {
            return@LaunchedEffect
        }
        notes = BrotherNewsSource.loadCurrentOrFallback()
        visible = true
    }

    if (visible) {
        BrotherNewsDialog(notes = notes) {
            prefs.edit().putLong("shown_version_code", installed).apply()
            visible = false
        }
    }
}

/** Acesso permanente na área "Minha Conta → Sobre e atualizações". */
@Composable
fun BrotherCurrentVersionNews(onDismiss: () -> Unit) {
    var notes by remember { mutableStateOf(BrotherNewsSource.localFallback()) }
    LaunchedEffect(Unit) {
        notes = BrotherNewsSource.loadCurrentOrFallback()
    }
    BrotherNewsDialog(notes = notes, onDismiss = onDismiss)
}

@Composable
private fun BrotherNewsDialog(notes: String, onDismiss: () -> Unit) {
    val items = remember(notes) {
        notes.replace('•', ';')
            .split(';', '\n')
            .map { it.trim().trimStart('-', ' ', '.').trim() }
            .filter { it.isNotBlank() }
            .take(6)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FioSurface,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("BROTHER MATRIZES", color = FioGold, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Novidades da versão " + BuildConfig.VERSION_NAME,
                    color = FioText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Text("Veja o que mudou nesta atualização.",
                    color = FioTextMuted, fontSize = 12.sp)
                items.forEach { note ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("✓", color = FioGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(note, modifier = Modifier.weight(1f),
                            color = FioText, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
                Text("Seus projetos e sua licença continuam preservados.",
                    modifier = Modifier.padding(top = 3.dp),
                    color = FioTextMuted, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Entendi", color = FioGold, fontWeight = FontWeight.Bold)
            }
        }
    )
}

internal object BrotherPostUpdatePolicy {
    /** Exibir uma vez por código de versão; reabrir manualmente não grava nova marca. */
    fun shouldDisplay(installedCode: Long, lastAcknowledgedCode: Long): Boolean =
        installedCode >= 184L && installedCode > lastAcknowledgedCode
}

internal object BrotherNewsSource {
    private const val MIRROR =
        "https://raw.githubusercontent.com/timachado/Brother-Matrizes/main/updates/stable.json"

    /** Notas de homologação para a versão ainda não publicada no manifesto. */
    fun localFallback(): String = when (BuildConfig.VERSION_CODE) {
        187 -> "Novo visual Premium em azul-noite e dourado; " +
            "Nova identidade BM no ícone e cabeçalho; " +
            "Acesso mais rápido a Criar nome, Biblioteca, Simulador, Editor, Converter e Enviar; " +
            "Bordado em destaque na tela inicial; " +
            "Compatibilidade com projetos, login, licenças e arquivos preservada"
        184 -> "Limites do plano Gratuito detalhados em Planos e benefícios; " +
            "Novidades da versão exibidas após atualizar, uma vez por versão; " +
            "Histórico de novidades acessível em Minha Conta; " +
            "Melhorias de apresentação, sem alterações nas licenças e pagamentos"
        else -> "Consulte as melhorias e correções incluídas nesta versão no canal oficial do Brother Matrizes."
    }

    suspend fun loadCurrentOrFallback(): String = withContext(Dispatchers.IO) {
        val fetched = runCatching {
            val url = URL(MIRROR)
            val conn = url.openConnection() as HttpsURLConnection
            try {
                conn.connectTimeout = 6_000
                conn.readTimeout = 6_000
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Cache-Control", "no-cache")
                require(conn.responseCode == 200)
                val jsonText = conn.inputStream.bufferedReader().use { it.readText().take(8193) }
                require(jsonText.length <= 8192)
                val json = JSONObject(jsonText)
                require(json.optString("applicationId") == "com.timachado.brothermatrizes")
                require(json.optString("channel") == "stable")
                // Evitar mostrar novidades de uma versão ainda não instalada.
                require(json.optLong("versionCode") == BuildConfig.VERSION_CODE.toLong())
                require(json.optString("versionName") == BuildConfig.VERSION_NAME)
                json.optString("releaseNotes").take(1800).takeIf { it.isNotBlank() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
        fetched ?: localFallback()
    }
}
