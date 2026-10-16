package com.timachado.brothermatrizes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.timachado.brothermatrizes.core.embroidery.PreparedRasterPhoto
import com.timachado.brothermatrizes.core.embroidery.RasterArtworkCandidate
import com.timachado.brothermatrizes.core.embroidery.RasterArtworkCandidates
import com.timachado.brothermatrizes.core.embroidery.RasterCropBounds
import com.timachado.brothermatrizes.core.embroidery.RasterImageDecoder
import com.timachado.brothermatrizes.core.embroidery.RasterImagePixels
import com.timachado.brothermatrizes.core.embroidery.RasterPhotoPreparation
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Simple, image-first entrypoint, patterned after Abrir Matrizes.
 * Opens Android's image/files picker immediately; no huge stitch settings or
 * unsafe horizontal raster-to-PES export on the first screen.
 *
 * Reuses existing decoder, artwork proposals and crop/background preparation.
 * Original commercial matrix import and SimulatorScreen are untouched.
 */
@Composable
fun SimpleImageToEmbroideryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<RasterImagePixels?>(null) }
    var candidates by remember { mutableStateOf<List<RasterArtworkCandidate>>(emptyList()) }
    var selectedId by remember { mutableIntStateOf(0) }
    var crop by remember { mutableStateOf(RasterCropBounds(0f, 0f, 1f, 1f)) }
    var prepared by remember { mutableStateOf<PreparedRasterPhoto?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            revision++
            val request = revision
            photo = null
            prepared = null
            candidates = emptyList()
            crop = RasterCropBounds(0f, 0f, 1f, 1f)
            selectedId = 0
            busy = true
            message = ""
            scope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    RasterImageDecoder.decode(context.contentResolver, uri)
                }
                val detected = loaded.getOrNull()?.let {
                    withContext(Dispatchers.Default) {
                        RasterArtworkCandidates.detect(it)
                    }
                } ?: emptyList()
                if (request == revision) {
                    photo = loaded.getOrNull()
                    candidates = detected
                    busy = false
                    message = loaded.exceptionOrNull()?.message.orEmpty()
                }
            }
        }
    }

    // Same native Android document selection flow used by "Abrir Matrizes":
    // Recentes, Imagens, local storage and compatible cloud providers.
    LaunchedEffect(Unit) {
        picker.launch(arrayOf("image/*"))
    }

    fun chooseRegion(region: RasterCropBounds, id: Int) {
        revision++
        crop = region
        selectedId = id
        prepared = null
        message = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FioBackground)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack) {
                Text("‹ Voltar", color = FioGold)
            }
            Column(modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    "Imagem para Bordado",
                    color = FioText,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Escolha uma foto, logo ou desenho",
                    color = FioTextMuted,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        val source = photo
        if (source != null) {
            // The touch-sensitive image stays OUTSIDE the vertically
            // scrollable controls: swipe does not fight the page scrolling.
            RasterPhotoCropCanvas(
                photo = source,
                selection = crop,
                candidates = candidates,
                onSelection = { chooseRegion(it, 0) },
                onCandidateTap = { id ->
                    candidates.firstOrNull { it.id == id }?.let {
                        chooseRegion(it.bounds, id)
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (source == null) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Abra uma imagem da galeria ou dos arquivos do celular.",
                    color = FioText,
                    fontSize = 16.sp
                )
                Button(
                    onClick = { picker.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = FioGold)
                ) {
                    Text("Abrir imagem", color = FioBackground)
                }
            } else {
                Text(
                    source.label,
                    color = FioText,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Toque na parte da arte que deseja preparar primeiro.",
                    color = FioTextMuted,
                    fontSize = 12.sp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { chooseRegion(RasterCropBounds(0f, 0f, 1f, 1f), 0) }
                    ) {
                        Text(
                            if (selectedId == 0) "● Imagem inteira" else "Imagem inteira",
                            color = FioGold
                        )
                    }
                    candidates.forEach { region ->
                        OutlinedButton(
                            onClick = { chooseRegion(region.bounds, region.id) }
                        ) {
                            Text(
                                if (selectedId == region.id)
                                    "● Parte ${region.id}" else "Parte ${region.id}",
                                color = FioGold
                            )
                        }
                    }
                }
                if (candidates.isEmpty()) {
                    Text(
                        "Se necessário, arraste na imagem para marcar a logo.",
                        color = FioTextMuted,
                        fontSize = 12.sp
                    )
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = FioGold),
                    onClick = {
                        val photoAtStart = source
                        val cropAtStart = crop
                        revision++
                        val request = revision
                        prepared = null
                        busy = true
                        message = ""
                        scope.launch {
                            val hasAlpha = photoAtStart.argb.count {
                                (it ushr 24) < 128
                            } > photoAtStart.argb.size / 80
                            val result = withContext(Dispatchers.Default) {
                                RasterPhotoPreparation.prepare(
                                    original = photoAtStart,
                                    bounds = cropAtStart,
                                    removeEdgeBackground = !hasAlpha,
                                    tolerance = 55
                                )
                            }
                            if (request == revision && photo === photoAtStart) {
                                busy = false
                                result.fold(
                                    onSuccess = {
                                        prepared = it
                                    },
                                    onFailure = {
                                        message = it.message ?: "Não foi possível preparar essa foto."
                                    }
                                )
                            }
                        }
                    }
                ) {
                    Text("Preparar imagem", color = FioBackground, fontWeight = FontWeight.Bold)
                }

                prepared?.let { result ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(FioSurface, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Prévia da arte selecionada",
                            color = FioText,
                            fontWeight = FontWeight.SemiBold
                        )
                        RasterPreparedImagePreview(result.image)
                        Text(
                            if (result.removedBackgroundPixels > 0)
                                "Fundo separado: ${result.removedBackgroundPixels} pixels removidos."
                            else "Imagem já recortada ou com fundo transparente.",
                            color = FioTextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { picker.launch(arrayOf("image/*")) }
                ) {
                    Text("Escolher outra imagem", color = FioGold)
                }

                Text(
                    "A geração da matriz PES está temporariamente bloqueada " +
                        "até validar o Satin vertical. Esta tela prepara e mostra " +
                        "a arte; não produz pontos para enviar à máquina.",
                    color = Color(0xFFFFBE91),
                    fontSize = 12.sp
                )
            }

            if (busy) {
                CircularProgressIndicator(color = FioGold)
            }
            if (message.isNotBlank()) {
                Text(message, color = Color(0xFFFFBE91), fontSize = 12.sp)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
