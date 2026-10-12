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

private fun isOfficialGroup(value: String): Boolean {
    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
    return uri.scheme == "https" &&
        uri.host == "chat.whatsapp.com" &&
        uri.pathSegments.size == 1 &&
        uri.pathSegments[0].matches(Regex("[A-Za-z0-9]{16,32}")) &&
        uri.encodedQuery == null &&
        uri.fragment == null
}

/**
 * Consulta somente metadados públicos do WordPress. Falhas de rede não
 * impactam matrizes, pagamentos, licenças nem as atualizações do Android.
 */
private suspend fun getOfficialGroup(): String? = withContext(Dispatchers.IO) {
    // GitHub Raw é a fonte principal. WordPress pode responder com HTML de
    // verificação e não deve tornar o aplicativo dependente desse serviço.
    val sources = listOf(
        "https://raw.githubusercontent.com/timachado/Brother-Matrizes/main/updates/community.json",
        BuildConfig.WORDPRESS_URL.trimEnd('/') + COMMUNITY_API_PATH
    )
    for (source in sources) {
        val value = runCatching {
            val url = URL(source)
            require(url.protocol == "https" && (
                (url.host == "raw.githubusercontent.com" &&
                    url.path == "/timachado/Brother-Matrizes/main/updates/community.json") ||
                (url.host == "timachado.ifree.page" &&
                    url.path == COMMUNITY_API_PATH)
            ))
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 4500
                connection.readTimeout = 4500
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/json")
                if (connection.responseCode != HttpURLConnection.HTTP_OK) null
                else {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                        val buffer = CharArray(4096)
                        val count = it.read(buffer)
                        if (count > 0) String(buffer, 0, count) else ""
                    }
                    val json = JSONObject(body)
                    val product = json.optString("product")
                    if (url.host == "raw.githubusercontent.com" && product != "brother-matrizes") null
                    else json.optString("groupUrl").takeIf(::isOfficialGroup)
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
        if (value != null) return@withContext value
    }
    null
}
@Composable
fun BrotherCommunityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var groupUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        groupUrl = getOfficialGroup()
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
                Text("Grupo oficial de feedbacks", color = FioText,
                    fontWeight = FontWeight.Bold)
                Text("Converse com outros usuários, relate erros e envie sugestões para as próximas versões.",
                    color = FioTextMuted)
                Text("Atenção: membros do grupo podem visualizar seu número de telefone. Não envie dados pessoais, senhas ou arquivos sigilosos.", color = FioTextMuted)
                if (groupUrl != null) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = FioGold, contentColor = FioBackground),
                        onClick = { groupUrl?.let(::openHttps) }
                    ) { Text("Entrar no grupo") }
                } else {
                    Text("O grupo poderá ser acessado aqui assim que o convite estiver disponível. Consulte a central no site.",
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
