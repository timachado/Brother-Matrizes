package com.timachado.brothermatrizes

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.timachado.brothermatrizes.core.account.BrotherMatrizesAccountService
import com.timachado.brothermatrizes.core.vision.InstalledTelegramSearch
import com.timachado.brothermatrizes.core.vision.TelegramImageAssistAnalyzer
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One-step image-assisted Telegram TEXT search.
 * This screen DOES NOT pretend to reverse-search Telegram group photos.
 * It uses the same installed Telegram client and search entry as commercial UI.
 */
@Composable
fun TelegramNativeImageAssistScreen(
    incomingImageUri: Uri? = null,
    onImageConsumed: (Uri) -> Unit = {},
    onBack: () -> Unit,
    onRequireGoogle: () -> Unit
) {
    val context = LocalContext.current
    var photo by remember { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraFile by rememberSaveable { mutableStateOf<String?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        uri: Uri? -> if (uri != null) photo = uri
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri?.let(Uri::parse)
        if (success && uri != null) {
            photo = uri
        } else {
            pendingCameraFile?.let { File(it).delete() }
        }
        pendingCameraUri = null
        pendingCameraFile = null
    }
    fun capture() {
        runCatching {
            val folder = File(context.cacheDir, "vision_camera").apply { mkdirs() }
            val file = File.createTempFile("vision_", ".jpg", folder)
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            pendingCameraUri = uri.toString()
            pendingCameraFile = file.absolutePath
            camera.launch(uri)
        }.onFailure {
            status = "Não foi possível abrir a câmera. Verifique o aplicativo de câmera."
        }
    }

    LaunchedEffect(incomingImageUri) {
        incomingImageUri?.let {
            photo = it
            onImageConsumed(it)
        }
    }
    LaunchedEffect(photo) {
        val uri = photo ?: return@LaunchedEffect
        busy = true
        status = "Analisando a foto no aparelho…"
        preview = null
        try {
            preview = withContext(Dispatchers.IO) {
                TelegramImageAssistAnalyzer.decodeThumbnail(context, uri).asImageBitmap()
            }
            val query = TelegramImageAssistAnalyzer.identifyTerm(context, uri)
            if (query.isNullOrBlank()) {
                status = "Não foi identificado nome, código ou assunto específico na foto. " +
                    "O Telegram instalado não oferece busca reversa por imagem."
                return@LaunchedEffect
            }
            if (InstalledTelegramSearch.installedPackage(context) == null) {
                status = "O Telegram não foi encontrado neste aparelho."
                return@LaunchedEffect
            }

            // The commercial search quotas remain enforced in release builds.
            if (!BuildConfig.BROTHER_NATIVE_IMAGE_QA) {
                val google = BrotherMatrizesAccountService.ensureGoogleSession()
                if (google.isFailure) {
                    status = "Entre com Google para usar a pesquisa do aplicativo."
                    onRequireGoogle()
                    return@LaunchedEffect
                }
                val permit = BrotherMatrizesAccountService.authorizeTool("telegram_search")
                if (permit.isFailure) {
                    status = permit.exceptionOrNull()?.message ?: "Limite de pesquisas atingido."
                    return@LaunchedEffect
                }
                val opened = InstalledTelegramSearch.openTextSearch(context, query)
                val result = BrotherMatrizesAccountService.finalizeTool(
                    "telegram_search", permit.getOrThrow(), opened
                )
                status = when {
                    !opened -> "O Telegram não pôde abrir a consulta."
                    result.isFailure -> "Pesquisa aberta, mas a cota não foi confirmada."
                    else -> "Telegram aberto com pesquisa por assunto reconhecido na imagem."
                }
            } else {
                // Separate diagnostic package; never bypass quotas in the official app.
                status = if (InstalledTelegramSearch.openTextSearch(context, query)) {
                    "Telegram aberto. Este teste procura por texto/assunto, NÃO por foto."
                } else "Não foi possível abrir o Telegram instalado."
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            status = "Não foi possível analisar a foto ou iniciar a pesquisa."
        } finally {
            busy = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹ Voltar", color = FioGold) }
        Text("Pesquisar usando uma imagem", color = FioText,
            fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Escolha uma foto. O Brother Matrizes reconhece nomes ou assuntos e abre " +
                "automaticamente a busca do Telegram instalado, sem solicitar outro telefone.",
            color = FioTextMuted, fontSize = 13.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                enabled = !busy, onClick = { capture() }, modifier = Modifier.weight(1f)
            ) { Text("Câmera") }
            OutlinedButton(
                enabled = !busy, onClick = { gallery.launch("image/*") },
                modifier = Modifier.weight(1f)
            ) { Text("Galeria") }
        }
        preview?.let {
            Image(
                bitmap = it, contentDescription = "Foto selecionada",
                modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        }
        if (busy) CircularProgressIndicator(color = FioGold)
        status?.let { Text(it, color = FioText, fontSize = 12.sp) }
        Text(
            "Limite técnico: o Telegram não fornece a outros aplicativos uma " +
                "pesquisa automática pelas imagens das suas conversas ou grupos. " +
                "Uma pesquisa por descrição não garante localizar a matriz exata.",
            color = FioTextMuted, fontSize = 11.sp
        )
    }
}
