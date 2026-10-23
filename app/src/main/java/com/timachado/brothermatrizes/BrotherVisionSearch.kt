package com.timachado.brothermatrizes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.timachado.brothermatrizes.core.vision.ImageSearchTerms
import com.timachado.brothermatrizes.core.vision.AndroidVisualFingerprint
import com.timachado.brothermatrizes.core.vision.VisualReferenceCatalog
import com.timachado.brothermatrizes.core.project.SavedProjectSummary
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Leitura local e voluntária da imagem. Não requer credenciais do Telegram.
 * A pesquisa do Telegram continua sendo aberta no cliente oficial, sem
 * catalogar imagens/mensagens de grupos ou contornar suas permissões.
 */
@Composable
fun BrotherVisionSearch(
    incomingImageUri: Uri?,
    onIncomingImageConsumed: (Uri) -> Unit,
    onRecognizedTerms: (String) -> Unit,
    projects: List<SavedProjectSummary>,
    onOpenSavedProject: (SavedProjectSummary) -> Unit
) {
    val context = LocalContext.current
    var selectedImage by remember { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var fingerprint by remember { mutableStateOf<Long?>(null) }
    var catalogRevision by remember { mutableStateOf(0) }
    var showAddReference by remember { mutableStateOf(false) }
    var showManageReferences by remember { mutableStateOf(false) }
    var chosenProject by remember { mutableStateOf<SavedProjectSummary?>(null) }
    val cataloged = remember(projects, catalogRevision) {
        VisualReferenceCatalog.registeredProjects(context, projects)
    }
    val matches = remember(projects, fingerprint, catalogRevision) {
        fingerprint?.let { VisualReferenceCatalog.findMatches(context, projects, it) }
            ?: emptyList()
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImage = uri
            feedback = null
        }
    }
    LaunchedEffect(incomingImageUri) {
        val uri = incomingImageUri ?: return@LaunchedEffect
        selectedImage = uri
        onIncomingImageConsumed(uri)
    }
    LaunchedEffect(selectedImage) {
        val uri = selectedImage ?: return@LaunchedEffect
        analyzing = true
        preview = null
        feedback = null
        suggestions = emptyList()
        fingerprint = null
        try {
            val bitmap = withContext(Dispatchers.IO) { readSmallBitmap(context, uri) }
            preview = bitmap.asImageBitmap()
            fingerprint = withContext(Dispatchers.Default) {
                AndroidVisualFingerprint.fromBitmap(bitmap)
            }
            val detector = ImageLabeling.getClient(
                ImageLabelerOptions.Builder().setConfidenceThreshold(0.45f).build()
            )
            try {
                val labels = suspendCancellableCoroutine<List<Pair<String, Float>>> { continuation ->
                    detector.process(InputImage.fromBitmap(bitmap, 0))
                        .addOnSuccessListener { found ->
                            if (continuation.isActive) continuation.resume(
                                found.map { it.text to it.confidence }
                            )
                        }
                        .addOnFailureListener { error ->
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                }
                val terms = ImageSearchTerms.fromLabels(labels)
                suggestions = terms
                if (terms.isEmpty()) {
                    feedback = "Não foi possível identificar palavras confiáveis. Digite o nome do desenho abaixo."
                } else {
                    onRecognizedTerms(terms.first())
                    feedback = "Escolha outra sugestão ou edite o campo antes de pesquisar no Telegram."
                }
            } finally {
                detector.close()
            }
        } catch (error: Exception) {
            feedback = "A imagem não pôde ser analisada neste aparelho. Tente outra foto ou pesquise por texto."
        } finally {
            analyzing = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = FioSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Brother Vision Search", color = FioText, fontWeight = FontWeight.Bold)
            Text(
                "Escolha ou compartilhe uma foto do bordado. A leitura acontece no celular e sugere termos para pesquisa.",
                color = FioTextMuted,
                fontSize = 12.sp
            )
            OutlinedButton(
                onClick = { imagePicker.launch("image/*") },
                enabled = !analyzing,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (selectedImage == null) "Pesquisar por imagem" else "Escolher outra imagem") }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                preview?.let { image ->
                    Image(
                        bitmap = image,
                        contentDescription = "Imagem selecionada para reconhecimento",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(10.dp))
                    )
                }
                if (analyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = FioGold,
                        strokeWidth = 2.dp
                    )
                    Text("Analisando imagem…", color = FioTextMuted)
                }
            }
            if (suggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestions.forEach { term ->
                        AssistChip(
                            onClick = { onRecognizedTerms(term) },
                            label = { Text(term) }
                        )
                    }
                }
            }
            feedback?.let { Text(it, color = FioGold, fontSize = 12.sp) }
            Text(
                "Busca por semelhança na sua biblioteca",
                color = FioText, fontWeight = FontWeight.SemiBold
            )
            Text(
                "${cataloged.size} matriz(es) com foto de referência cadastrada. " +
                    "As comparações são aproximadas e feitas no aparelho.",
                color = FioTextMuted, fontSize = 11.sp
            )
            if (fingerprint != null) {
                if (matches.isEmpty()) {
                    Text(
                        if (cataloged.isEmpty()) {
                            "Cadastre uma foto de referência para uma matriz salva antes de pesquisar por semelhança."
                        } else {
                            "Nenhuma correspondência confiável. Tente uma foto parecida com a referência cadastrada."
                        },
                        color = FioTextMuted, fontSize = 12.sp
                    )
                } else {
                    matches.forEach { match ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(match.project.title, color = FioText, fontSize = 12.sp)
                                Text(
                                    "Similaridade aproximada: ${(match.similarity * 100).toInt()}%",
                                    color = FioTextMuted, fontSize = 10.sp
                                )
                            }
                            TextButton(onClick = { onOpenSavedProject(match.project) }) {
                                Text("Abrir", color = FioGold)
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { showAddReference = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Vincular esta foto a uma matriz salva") }
            } else if (selectedImage != null && !analyzing) {
                Text(
                    "A imagem tem poucos detalhes para uma comparação visual confiável.",
                    color = FioTextMuted, fontSize = 11.sp
                )
            }
            if (cataloged.isNotEmpty()) {
                TextButton(onClick = { showManageReferences = true }) {
                    Text("Gerenciar referências locais", color = FioGold)
                }
            }
            Text(
                "Fotos e assinaturas não são enviadas ao Telegram. " +
                    "Para a busca por texto, o Telegram mantém as permissões da sua própria conta.",
                color = FioTextMuted, fontSize = 11.sp
            )
        }
    }

    if (showAddReference) {
        AlertDialog(
            onDismissRequest = { showAddReference = false },
            title = { Text("Escolher matriz de referência") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Vincule somente imagens próprias ou autorizadas. " +
                            "A imagem não será copiada; apenas a assinatura visual ficará no aparelho.",
                        fontSize = 12.sp
                    )
                    if (projects.isEmpty()) {
                        Text("Não há matrizes na sua biblioteca para vincular.")
                    }
                    projects.forEach { project ->
                        TextButton(onClick = {
                            showAddReference = false
                            chosenProject = project
                        }) {
                            Text("${project.title} • ${project.format.uppercase()}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddReference = false }) { Text("Fechar") }
            }
        )
    }
    chosenProject?.let { project ->
        AlertDialog(
            onDismissRequest = { chosenProject = null },
            title = { Text("Confirmar foto de referência") },
            text = {
                Text(
                    "Associar a foto selecionada à matriz “${project.title}”? " +
                        "A referência anterior desta matriz, se existir, será substituída. " +
                        "Use somente uma imagem sua ou autorizada."
                )
            },
            confirmButton = {
                Button(onClick = {
                    val selected = fingerprint
                    val result = if (selected != null) runCatching {
                        VisualReferenceCatalog.save(context, project.id, selected)
                    } else Result.failure(IllegalStateException("Foto ainda não processada."))
                    feedback = if (result.isSuccess) {
                        catalogRevision += 1
                        "Foto de referência vinculada à matriz. Você pode buscar outra imagem semelhante."
                    } else {
                        "Não foi possível salvar a referência."
                    }
                    chosenProject = null
                }) { Text("Vincular") }
            },
            dismissButton = {
                TextButton(onClick = { chosenProject = null }) { Text("Cancelar") }
            }
        )
    }
    if (showManageReferences) {
        AlertDialog(
            onDismissRequest = { showManageReferences = false },
            title = { Text("Referências visuais locais") },
            text = {
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Remover uma referência não exclui a matriz salva.",
                        fontSize = 12.sp
                    )
                    cataloged.forEach { project ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                project.title,
                                modifier = Modifier.weight(1f),
                                color = FioText
                            )
                            TextButton(onClick = {
                                VisualReferenceCatalog.delete(context, project.id)
                                catalogRevision += 1
                            }) { Text("Remover") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManageReferences = false }) { Text("Concluir") }
            }
        )
    }
}

/** Decodifica apenas uma miniatura, evitando carregar fotos enormes na memória. */
private fun readSmallBitmap(context: Context, uri: Uri): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val input = context.contentResolver.openInputStream(uri)
        ?: error("Não foi possível abrir a foto.")
    input.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth in 1..30000 && bounds.outHeight in 1..30000) {
        "Dimensões inválidas da imagem."
    }
    var sample = 1
    while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: error("Formato de foto inválido.")
}
