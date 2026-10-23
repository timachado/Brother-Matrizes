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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.timachado.brothermatrizes.core.telegramvision.TelegramAuthorizationStage
import com.timachado.brothermatrizes.core.telegramvision.TelegramGroup
import com.timachado.brothermatrizes.core.telegramvision.TelegramGroupSession
import com.timachado.brothermatrizes.core.telegramvision.TelegramGroupVisualSearch
import com.timachado.brothermatrizes.core.telegramvision.TelegramPhotoHit
import com.timachado.brothermatrizes.core.telegramvision.SelectedTelegramImage
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

/** Login Telegram próprio, não usa nem copia credenciais do Telegram instalado. */
@Composable
fun TelegramGroupVisionScreen(
    imageSharedWithApp: Uri? = null,
    onSharedImageConsumed: (Uri) -> Unit = {},
    onBack: () -> Unit,
    onImportFile: () -> Unit
) {
    val context = LocalContext.current
    val client = remember { TelegramGroupSession.get(context) }
    val auth by client.stage.collectAsState()
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var emailCode by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var allGroups by remember { mutableStateOf<List<TelegramGroup>>(emptyList()) }
    var selectedGroupIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var consent by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var photoPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraFile by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var didSearch by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<TelegramPhotoHit>>(emptyList()) }
    var scanned by remember { mutableStateOf(0) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) photo = uri
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val captured = pendingCameraUri?.let(Uri::parse)
        if (success && captured != null) {
            photo = captured
        } else {
            pendingCameraFile?.let { File(it).delete() }
        }
        pendingCameraUri = null
        pendingCameraFile = null
    }

    fun openCamera() {
        runCatching {
            val folder = File(context.cacheDir, "vision_camera").apply { mkdirs() }
            // Delete abandoned captures from older sessions, never users' files.
            folder.listFiles()?.filter {
                it.isFile && it.lastModified() < System.currentTimeMillis() - 24L * 60 * 60 * 1000
            }?.forEach { it.delete() }
            val outputFile = File.createTempFile("lens_", ".jpg", folder)
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", outputFile
            )
            pendingCameraUri = uri.toString()
            pendingCameraFile = outputFile.absolutePath
            takePhoto.launch(uri)
        }.onFailure {
            error = "Não foi possível abrir a câmera: ${it.message ?: "verifique o aplicativo de câmera"}."
        }
    }

    LaunchedEffect(photo) {
        val uri = photo
        photoPreview = if (uri != null) withContext(Dispatchers.IO) {
            runCatching { loadVisionPreview(context, uri) }.getOrNull()
        } else null
    }

    LaunchedEffect(imageSharedWithApp) {
        imageSharedWithApp?.let {
            photo = it
            onSharedImageConsumed(it)
        }
    }
    LaunchedEffect(auth) {
        if (auth == TelegramAuthorizationStage.READY) {
            runCatching { withContext(Dispatchers.IO) { client.authorizedGroups() } }
                .onSuccess { groups ->
                    allGroups = groups
                    selectedGroupIds = groups.take(TelegramGroupVisualSearch.MAX_GROUPS)
                        .map { it.id }.toSet()
                    error = null
                }
                .onFailure { error = it.message ?: "Não foi possível listar os grupos." }
        }
    }
    LaunchedEffect(photo, selectedGroupIds, consent, auth) {
        val selectedPhoto = photo ?: return@LaunchedEffect
        if (!consent || auth != TelegramAuthorizationStage.READY ||
            selectedGroupIds.isEmpty()) return@LaunchedEffect
        searching = true
        didSearch = false
        hits = emptyList()
        error = null
        try {
            val fingerprint = withContext(Dispatchers.IO) {
                SelectedTelegramImage.grayscale9x8(context, selectedPhoto)
            } ?: throw IllegalArgumentException("Foto inválida. Tente outra imagem.")
            val results = withTimeout(120_000L) {
                withContext(Dispatchers.IO) {
                    TelegramGroupVisualSearch(client).search(
                        true, selectedGroupIds, fingerprint
                    )
                }
            }
            scanned = selectedGroupIds.size
            hits = results
            didSearch = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Não foi possível comparar as fotos dos grupos."
        } finally {
            searching = false
        }
    }

    suspend fun sendPhone() {
        runCatching { client.submitPhone(phone.trim()) }
            .onFailure { error = it.message ?: "Falha no Telegram." }
    }
    suspend fun sendEmail() {
        runCatching { client.submitEmail(email.trim()) }
            .onFailure { error = it.message ?: "E-mail não aceito pelo Telegram." }
    }
    suspend fun sendEmailCode() {
        val sentCode = emailCode.trim()
        emailCode = ""
        runCatching { client.submitEmailCode(sentCode) }
            .onFailure { error = it.message ?: "Código de e-mail não aceito." }
    }
    suspend fun sendCode() {
        runCatching { client.submitCode(code.trim()) }
            .onFailure { error = it.message ?: "Código não aceito." }
    }
    suspend fun sendPassword() {
        val sent = password
        password = ""
        runCatching { client.submitPassword(sent) }
            .onFailure { error = it.message ?: "Senha não aceita." }
    }

    // Compose callbacks must call suspend functions from a remembered scope.
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack) { Text("‹ Voltar", color = FioGold) }
                Text(
                    "Buscar matrizes pela imagem",
                    color = FioText, fontWeight = FontWeight.Bold, fontSize = 22.sp
                )
                Text(
                    "A pesquisa consulta somente fotos recentes dos grupos " +
                        "acessíveis à sua conta Telegram. Não examina grupos de terceiros.",
                    color = FioTextMuted, fontSize = 12.sp
                )
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = FioSurface)) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Pesquisar pela foto", color = FioText, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Fotografe o bordado ou escolha uma imagem da galeria. " +
                            "A busca começa quando a conexão e a autorização dos grupos estiverem prontas.",
                        color = FioTextMuted, fontSize = 12.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { openCamera() },
                            modifier = Modifier.weight(1f)
                        ) { Text("Abrir câmera") }
                        OutlinedButton(
                            onClick = { pickImage.launch("image/*") },
                            modifier = Modifier.weight(1f)
                        ) { Text("Galeria") }
                    }
                    photoPreview?.let {
                        Image(
                            bitmap = it,
                            contentDescription = "Foto do bordado selecionada para busca",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(126.dp).clip(RoundedCornerShape(12.dp))
                        )
                    }
                    if (photo != null &&
                        (auth != TelegramAuthorizationStage.READY || !consent || selectedGroupIds.isEmpty())
                    ) {
                        Text(
                            "Foto selecionada. Conecte o Telegram e autorize seus grupos para iniciar a busca.",
                            color = FioGold, fontSize = 11.sp
                        )
                    }
                }
            }
        }
        if (auth != TelegramAuthorizationStage.READY) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = FioSurface)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Conectar minha conta Telegram", color = FioText, fontWeight = FontWeight.Bold)
                        when (auth) {
                            TelegramAuthorizationStage.NOT_CONFIGURED -> Text(
                                "Esta compilação ainda não possui as credenciais oficiais " +
                                    "de desenvolvedor Telegram da T.I. Machado. " +
                                    "A busca nos grupos não está habilitada.",
                                color = FioTextMuted
                            )
                            TelegramAuthorizationStage.INITIALIZING -> CircularProgressIndicator()
                            TelegramAuthorizationStage.PHONE -> {
                                OutlinedTextField(
                                    value = phone, onValueChange = { phone = it.take(20) },
                                    label = { Text("Telefone com DDI (+55...)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(onClick = { scope.launch { sendPhone() } }) { Text("Receber código") }
                            }
                            TelegramAuthorizationStage.EMAIL -> {
                                OutlinedTextField(
                                    value = email, onValueChange = { email = it.take(254) },
                                    label = { Text("E-mail solicitado pelo Telegram") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(onClick = { scope.launch { sendEmail() } }) {
                                    Text("Receber código por e-mail")
                                }
                            }
                            TelegramAuthorizationStage.EMAIL_CODE -> {
                                OutlinedTextField(
                                    value = emailCode,
                                    onValueChange = { emailCode = it.take(12) },
                                    label = { Text("Código enviado por e-mail") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(onClick = { scope.launch { sendEmailCode() } }) {
                                    Text("Confirmar e-mail")
                                }
                            }
                            TelegramAuthorizationStage.CODE -> {
                                OutlinedTextField(
                                    value = code, onValueChange = { code = it.take(10) },
                                    label = { Text("Código enviado pelo Telegram") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(onClick = { scope.launch { sendCode() } }) { Text("Confirmar código") }
                            }
                            TelegramAuthorizationStage.PASSWORD -> {
                                OutlinedTextField(
                                    value = password, onValueChange = { password = it.take(128) },
                                    label = { Text("Senha de verificação em duas etapas") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(onClick = { scope.launch { sendPassword() } }) { Text("Conectar") }
                            }
                            else -> Text(
                                "O Telegram solicitou um método adicional de autenticação. " +
                                    "Esta etapa ainda não é suportada no Brother Matrizes.",
                                color = FioTextMuted
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Text(
                    "Grupos acessíveis: ${allGroups.size}. " +
                        "Até ${TelegramGroupVisualSearch.MAX_GROUPS} grupos e " +
                        "${TelegramGroupVisualSearch.MAX_PHOTOS_PER_GROUP} fotos recentes por grupo nesta consulta.",
                    color = FioTextMuted, fontSize = 12.sp
                )
            }
            items(allGroups, key = { "group-${it.id}" }) { group ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = group.id in selectedGroupIds,
                        onCheckedChange = { checked ->
                            selectedGroupIds = if (checked) {
                                if (selectedGroupIds.size < TelegramGroupVisualSearch.MAX_GROUPS) {
                                    selectedGroupIds + group.id
                                } else selectedGroupIds
                            } else selectedGroupIds - group.id
                        }
                    )
                    Text(group.title, color = FioText)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = consent, onCheckedChange = { consent = it })
                        Text(
                            "Autorizo esta pesquisa pontual nos grupos selecionados.",
                            color = FioTextMuted, fontSize = 12.sp
                        )
                    }
                    if (searching) {
                        CircularProgressIndicator(color = FioGold)
                        Text("Comparando fotos nos meus grupos…", color = FioText)
                    } else if (didSearch) {
                        Text(
                            "${hits.size} possível(is) correspondência(s) em $scanned grupo(s).",
                            color = FioText
                        )
                    }
                }
            }
            items(hits, key = { "${it.chatId}-${it.messageId}" }) { hit ->
                var resultPreview by remember(hit.localPreviewPath) {
                    mutableStateOf<ImageBitmap?>(null)
                }
                LaunchedEffect(hit.localPreviewPath) {
                    val path = hit.localPreviewPath
                    resultPreview = if (path != null) withContext(Dispatchers.IO) {
                        runCatching { loadLocalTelegramPreview(context, path) }.getOrNull()
                    } else null
                }
                Card(colors = CardDefaults.cardColors(containerColor = FioSurface)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        resultPreview?.let { image ->
                            Image(
                                bitmap = image,
                                contentDescription = "Foto semelhante encontrada no Telegram",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(104.dp).clip(RoundedCornerShape(10.dp))
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(hit.groupTitle, color = FioText, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Semelhança aproximada: ${(hit.similarity * 100).toInt()}%",
                                color = FioTextMuted, fontSize = 11.sp
                            )
                            hit.publicPostUrl?.let { url ->
                                TextButton(onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                .addCategory(Intent.CATEGORY_BROWSABLE)
                                        )
                                    }
                                }) { Text("Abrir publicação", color = FioGold) }
                            } ?: Text(
                                "Publicação sem link compartilhável",
                                color = FioTextMuted, fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
        item {
            error?.let { Text(it, color = FioGold, fontSize = 12.sp) }
            OutlinedButton(onClick = onImportFile, modifier = Modifier.fillMaxWidth()) {
                Text("Importar arquivo PES / DST / JEF")
            }
            Text(
                "Comparação matemática de fotos, não Google Lens. " +
                    "Pode não encontrar a matriz original. Nenhum resultado é inventado; " +
                    "grupos protegidos ou sem acesso não são pesquisados.",
                color = FioTextMuted, fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}


/** Preview bounded to avoid decoding multi-megapixel camera photos into Compose memory. */
private fun loadVisionPreview(context: Context, uri: Uri): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    // BitmapFactory's bounds-only probe has no Bitmap return value.
    if (bounds.outWidth !in 1..30000 || bounds.outHeight !in 1..30000) return null
    var sample = 1
    while (bounds.outWidth / sample > 600 || bounds.outHeight / sample > 600) {
        sample *= 2
    }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        })?.asImageBitmap()
    }
}


/** Never interpret a Telegram response as an arbitrary filesystem path. */
private fun loadLocalTelegramPreview(context: Context, filePath: String): ImageBitmap? {
    val file = File(filePath).canonicalFile
    val privateRoot = context.filesDir.canonicalFile.path + File.separator
    if (!file.path.startsWith(privateRoot) || !file.isFile) return null
    return loadVisionPreview(context, Uri.fromFile(file))
}
