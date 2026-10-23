package com.timachado.brothermatrizes

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.account.BrotherMatrizesAccountService
import com.timachado.brothermatrizes.core.network.SafeRemoteImage
import com.timachado.brothermatrizes.core.vision.AuthorizedMatrixMatch
import com.timachado.brothermatrizes.core.vision.AuthorizedVisionResult
import com.timachado.brothermatrizes.core.vision.AuthorizedVisionSearchClient
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface VisionState {
    data object AwaitingImage : VisionState
    data object Searching : VisionState
    data object NeedsLogin : VisionState
    data class Failed(val message: String) : VisionState
    data class Found(val result: AuthorizedVisionResult) : VisionState
}

/**
 * Lens-like workflow: selecting a photo triggers one real visual query.
 * A match exists ONLY if the authorized remote catalog returns it. Telegram
 * does not support global image-search APIs. No keywords or local dHash.
 */
@Composable
fun VisualTelegramSearchScreen(
    incomingImageUri: Uri?,
    onIncomingConsumed: (Uri) -> Unit,
    onBack: () -> Unit,
    onRequireGoogle: () -> Unit
) {
    val context = LocalContext.current
    var chosenImage by remember { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var state by remember { mutableStateOf<VisionState>(VisionState.AwaitingImage) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        uri: Uri? -> if (uri != null) chosenImage = uri
    }

    LaunchedEffect(incomingImageUri) {
        incomingImageUri?.let {
            chosenImage = it
            onIncomingConsumed(it)
        }
    }

    LaunchedEffect(chosenImage) {
        val uri = chosenImage ?: return@LaunchedEffect
        state = VisionState.Searching
        preview = withContext(Dispatchers.IO) {
            runCatching {
                decodeSmallPreview(context, uri)
            }.getOrNull()
        }
        val endpoint = BuildConfig.BROTHER_VISION_SEARCH_URL
        if (endpoint.isBlank()) {
            state = VisionState.Failed(
                "O catálogo visual autorizado ainda não foi conectado. " +
                    "A busca global por imagem no Telegram não é disponibilizada pela plataforma."
            )
            return@LaunchedEffect
        }
        val token = BrotherMatrizesAccountService.visionSearchAccessToken()
        if (token.isFailure) {
            state = VisionState.NeedsLogin
            return@LaunchedEffect
        }
        state = withContext(Dispatchers.IO) {
            AuthorizedVisionSearchClient.search(
                context, uri, token.getOrThrow(), endpoint
            ).fold(
                onSuccess = { VisionState.Found(it) },
                onFailure = { VisionState.Failed(
                    it.message ?: "Não foi possível pesquisar a imagem."
                ) }
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹ Voltar", color = FioGold) }
        Text("Busca visual de matrizes", color = FioText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Selecione a imagem. A pesquisa começa automaticamente nas matrizes " +
                "cadastradas por distribuidores autorizados.",
            color = FioTextMuted,
            fontSize = 13.sp
        )
        OutlinedButton(
            onClick = { picker.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (chosenImage == null) "Selecionar uma imagem" else "Pesquisar outra imagem")
        }
        preview?.let { image ->
            Image(
                bitmap = image,
                contentDescription = "Imagem selecionada para busca",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(132.dp).clip(RoundedCornerShape(12.dp))
            )
        }
        when (val current = state) {
            VisionState.AwaitingImage -> Text(
                "Aguardando imagem para pesquisar.",
                color = FioTextMuted
            )
            VisionState.Searching -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(color = FioGold, modifier = Modifier.size(24.dp))
                Text("Comparando a imagem com o catálogo autorizado…", color = FioText)
            }
            VisionState.NeedsLogin -> {
                Text("Entre com Google para consultar o catálogo autorizado.", color = FioTextMuted)
                Button(onClick = onRequireGoogle) { Text("Entrar com Google") }
            }
            is VisionState.Failed -> Text(current.message, color = FioGold)
            is VisionState.Found -> {
                Text(
                    "${current.result.matches.size} resultado(s) encontrados",
                    color = FioText, fontWeight = FontWeight.SemiBold
                )
                if (current.result.catalogCount == 0) {
                    Text(
                        "Ainda não há publicações com autorização de busca visual no catálogo. " +
                            "Nenhuma publicação foi inventada.",
                        color = FioTextMuted
                    )
                } else if (current.result.matches.isEmpty()) {
                    Text(
                        "Nenhuma matriz semelhante encontrada entre os itens autorizados.",
                        color = FioTextMuted
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(current.result.matches, key = { it.id }) { result ->
                        VisionResultCard(result)
                    }
                }
            }
        }
        Text(
            "A imagem é enviada apenas ao serviço de busca configurado, " +
                "processada e descartada. Não pesquisamos conversas, grupos " +
                "ou canais do Telegram sem autorização.",
            color = FioTextMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(bottom = 14.dp)
        )
    }
}

@Composable
private fun VisionResultCard(match: AuthorizedMatrixMatch) {
    val context = LocalContext.current
    var image by remember(match.previewUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(match.previewUrl) {
        image = withContext(Dispatchers.IO) {
            SafeRemoteImage.load(match.previewUrl).getOrNull()?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = FioSurface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            image?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Prévia fornecida pelo titular",
                    modifier = Modifier.size(86.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(match.title, color = FioText, fontWeight = FontWeight.SemiBold)
                Text(
                    "${match.format} • Publicação autorizada",
                    color = FioTextMuted, fontSize = 11.sp
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(match.telegramUrl))
                                .addCategory(Intent.CATEGORY_BROWSABLE)
                        )
                    }
                }) { Text("Abrir no Telegram", color = FioGold) }
            }
        }
    }
}

/** Keep the image preview bounded on entry-level Android devices. */
private fun decodeSmallPreview(context: Context, uri: Uri): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    } ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
    }
}
