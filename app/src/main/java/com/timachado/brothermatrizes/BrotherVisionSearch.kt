package com.timachado.brothermatrizes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
    onRecognizedTerms: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedImage by remember { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }

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
        try {
            val bitmap = withContext(Dispatchers.IO) { readSmallBitmap(context, uri) }
            preview = bitmap.asImageBitmap()
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
                if (terms.isEmpty()) {
                    feedback = "Não foi possível identificar palavras confiáveis. Digite o nome do desenho abaixo."
                } else {
                    onRecognizedTerms(terms.joinToString(" "))
                    feedback = "Sugestões: " + terms.joinToString(", ") +
                        ". Confira os termos antes de pesquisar no Telegram."
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
            feedback?.let { Text(it, color = FioGold, fontSize = 12.sp) }
            Text(
                "Não faz busca reversa automática nos canais do Telegram. A pesquisa usa o aplicativo Telegram e as permissões da sua conta.",
                color = FioTextMuted, fontSize = 11.sp
            )
        }
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
