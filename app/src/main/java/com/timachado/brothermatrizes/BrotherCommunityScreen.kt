package com.timachado.brothermatrizes

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val COMMUNITY_API_PATH = "/wp-json/tim-brother-sync/v1/community"
private const val COMMUNITY_FALLBACK_PAGE = "/aplicativos/brother-matrizes/"

private fun isOfficialChannel(value: String): Boolean {
    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
    return uri.scheme == "https" &&
        (uri.host == "whatsapp.com" || uri.host == "www.whatsapp.com") &&
        uri.pathSegments.size == 2 &&
        uri.pathSegments[0] == "channel" &&
        uri.pathSegments[1].matches(Regex("[A-Za-z0-9]+")) &&
        uri.encodedQuery == null &&
        uri.fragment == null
}

/**
 * Consulta somente metadados públicos do WordPress. Falhas de rede não
 * impactam matrizes, pagamentos, licenças nem as atualizações do Android.
 */
private suspend fun getOfficialChannel(): String? = withContext(Dispatchers.IO) {
    runCatching {
        val base = BuildConfig.WORDPRESS_URL.trimEnd('/')
        val url = URL(base + COMMUNITY_API_PATH)
        require(url.protocol == "https" && url.host == "timachado.ifree.page")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            val value = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                JSONObject(it.readText().take(4096)).optString("channelUrl")
            }
            value.takeIf(::isOfficialChannel)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

@Composable
fun BrotherCommunityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var channelUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        channelUrl = getOfficialChannel()
    }

    fun openHttps(url: String) {
        if (!url.startsWith("https://")) return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            // O usuário pode não ter navegador/WhatsApp instalado.
        } catch (_: SecurityException) {
            // Não altera o estado do aplicativo se a atividade externa for bloqueada.
        }
    }

    val productPage = BuildConfig.WORDPRESS_URL.trimEnd('/') + COMMUNITY_FALLBACK_PAGE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = onBack) { Text("Voltar") }
        Text("Comunidade Brother Matrizes", color = FioText,
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        Text("Novidades, tutoriais e sugestões para a evolução do seu aplicativo.",
            color = FioTextMuted)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = FioSurface)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Atualizações oficiais", color = FioText, fontWeight = FontWeight.Bold)
                Text("Versão instalada: " + BuildConfig.VERSION_NAME, color = FioTextMuted)
                Text("Acompanhe as versões publicadas e o que melhorou em cada uma.",
                    color = FioTextMuted)
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { openHttps(productPage) }
                ) { Text("Ver notas e novidades") }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = FioSurface)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Canal oficial no WhatsApp", color = FioText,
                    fontWeight = FontWeight.Bold)
                Text("Receba anúncios, participe de enquetes e acompanhe funcionalidades em desenvolvimento.",
                    color = FioTextMuted)
                if (channelUrl != null) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = FioGold, contentColor = FioBackground),
                        onClick = { channelUrl?.let(::openHttps) }
                    ) { Text("Seguir canal oficial") }
                } else {
                    Text("O canal será disponibilizado aqui quando estiver ativo. Você pode consultar a central no site.",
                        color = FioTextMuted)
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = FioSurface)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Envie seu feedback", color = FioText, fontWeight = FontWeight.Bold)
                Text("Relate um erro ou sugira melhorias. Não inclua dados sensíveis.",
                    color = FioTextMuted)
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = FioGold, contentColor = FioBackground),
                    onClick = { openHttps(productPage + "#brother-feedback") }
                ) { Text("Enviar sugestão ou relatar problema") }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("A compra ou assinatura oferece acesso aos recursos contratados. Seu apoio também contribui com correções, manutenção e evolução contínua.",
            color = FioTextMuted)
    }
}
