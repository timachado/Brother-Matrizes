package com.timachado.fiolab

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.fiolab.core.embroidery.EmbNativePreview
import com.timachado.fiolab.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

/** EMB aberto localmente, sem converter imagem em pontos nem expor arquivo à internet. */
@Composable
fun EmbNativePreviewScreen(
    document: EmbNativePreview,
    onBack: () -> Unit,
    onOpen: () -> Unit
) {
    val number = remember { NumberFormat.getIntegerInstance(Locale("pt", "BR")) }
    val image = remember(document.sha256) {
        document.imageJpeg?.let { bytes ->
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth !in 1..1024 || bounds.outHeight !in 1..1024) null
                else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Voltar", color = FioGold) }
            Text("Abrir arquivo EMB", color = FioText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = FioSurface),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text(document.fileName, color = FioText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("EMB nativo · Leitura e prévia", color = FioGold, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = "Prévia armazenada na matriz EMB",
                        modifier = Modifier.size(256.dp).align(Alignment.CenterHorizontally),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("Prévia de imagem indisponível neste arquivo.", color = FioTextMuted)
                }
                Spacer(Modifier.height(9.dp))
                Text(
                    "Visualização incorporada no arquivo. Não representa pontos decodificados.",
                    color = FioTextMuted, fontSize = 12.sp
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = FioSurfaceAlt),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Informações do projeto", color = FioText, fontWeight = FontWeight.Bold)
                val rows = listOf(
                    "Pontos declarados" to document.declaredStitches,
                    "Cores" to document.declaredColors,
                    "Objetos" to document.declaredObjects,
                    "Cortes" to document.declaredTrims,
                    "Mudanças de cor" to document.declaredColorChanges,
                    "Paradas" to document.declaredStops
                )
                rows.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, color = FioTextMuted, fontSize = 12.sp)
                        Text(value?.let(number::format) ?: "—", color = FioText,
                            fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
        }
        if (document.declaredThreadSteps.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = FioSurface),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Etapas de fio declaradas", color = FioText, fontWeight = FontWeight.Bold)
                    document.declaredThreadSteps.forEach { step ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(step.order.toString() + ". " + step.colorName,
                                color = FioText, fontSize = 12.sp)
                            Text(number.format(step.declaredStitches) + " pontos",
                                color = FioTextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = FioSurfaceAlt),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Conversão EMB → PES / JEF / DST", color = FioGold,
                    fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "A conversão permanece bloqueada até decodificarmos e validarmos " +
                        "os comandos reais de pontos. Uma prévia não pode ser " +
                        "transformada com fidelidade em matriz de máquina.",
                    color = FioTextMuted, fontSize = 12.sp
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onOpen, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = FioGold,
                contentColor = FioBackground)
        ) {
            Text("Abrir outra matriz", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Voltar ao início")
        }
        Spacer(Modifier.height(25.dp))
    }
}
