package com.timachado.brothermatrizes

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.timachado.brothermatrizes.core.embroidery.ConvertedMatrix
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.PremiumImagePhysicalLab
import com.timachado.brothermatrizes.core.embroidery.PremiumImageVisualStitchPreview
import com.timachado.brothermatrizes.core.embroidery.PremiumImageSatinVisualPreview
import com.timachado.brothermatrizes.core.embroidery.PremiumImageDigitizedFillPreview
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal data class PremiumImageLabPhoto(
    val width: Int, val height: Int,
    val argb: IntArray, val reduced: Boolean
)

/** User-controlled QA modes: independently implemented; not Artspira code. */
internal enum class PremiumImageMode { DIGITIZED_FILL, SATIN_STUDY }

/** Survives navigation to SimulatorScreen without persisting experimental PES. */
internal class PremiumImageLabSession {
    var photo by mutableStateOf<PremiumImageLabPhoto?>(null)
    var widthMm by mutableFloatStateOf(60f)
    var hoop by mutableStateOf(HoopProfile.H100X100)
    var selectedMode by mutableStateOf(PremiumImageMode.DIGITIZED_FILL)
    var colorLimit by mutableIntStateOf(4)
    var prepared by mutableStateOf<EmbroideryDesign?>(null)
    var simulated by mutableStateOf<EmbroideryDesign?>(null)
    var revision: Int = 0

    fun invalidate() {
        revision++
        prepared = null
        simulated = null
    }
}

@Composable
internal fun PremiumImageLabScreen(
    session: PremiumImageLabSession,
    onBack: () -> Unit,
    onSimulate: (EmbroideryDesign, HoopProfile) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<ConvertedMatrix?>(null) }

    val save = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val artifact = pending
        pending = null
        if (uri != null && artifact != null) {
            scope.launch {
                val written = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri, "w")
                            ?.use { it.write(artifact.bytes); it.flush() }
                            ?: error("Não foi possível gravar o PES no destino escolhido.")
                    }
                }
                message = written.fold(
                    onSuccess = { "PES experimental salvo. Confira prévia e bastidor na máquina Brother; teste somente em retalho." },
                    onFailure = { "Erro ao salvar: " + (it.message ?: "selecione outro destino") }
                )
            }
        }
    }
    val open = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            session.invalidate()
            session.photo = null
            message = ""
            val requestedRevision = session.revision
            busy = true
            scope.launch {
                val decoded = withContext(Dispatchers.IO) {
                    PremiumImageLabDecoder.decode(
                        context.contentResolver, uri
                    )
                }
                busy = false
                if (requestedRevision == session.revision) {
                    decoded.fold(
                        onSuccess = {
                            session.photo = it
                            message = "Imagem carregada. Gere os pontos e abra o simulador antes de salvar o PES."
                        },
                        onFailure = { message = it.message ?: "Importação recusada." }
                    )
                }
            }
        }
    }
    val source = session.photo
    Column(
        Modifier.fillMaxSize().background(FioBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(onClick = onBack) { Text("‹ Voltar", color = FioGold) }
        Text("Imagem para Bordado",style = MaterialTheme.typography.headlineSmall,color=FioText)
        Text("Laboratório QA • Visual Premium 1.11.x",color=FioGold)
        Text(
            "Importe um PNG e toque em Simular bordado. " +
                "Veja os pontos e a sequência no simulador antes de qualquer teste físico.",
            color=FioTextMuted
        )
        Text(
            "Escolha o modo: preenchimento digitalizado ou estudo Satin direcional. " +
                "Ambos são prévias experimentais, não matrizes físicas homologadas.",
            color=FioGold,style=MaterialTheme.typography.bodyMedium
        )
        Button(onClick = { open.launch(arrayOf("image/png","image/*")) },
            enabled=!busy,
            modifier=Modifier.fillMaxWidth().testTag("premium_qa_import")) {
            Text(if (source == null) "Importar imagem PNG" else "Trocar imagem PNG")
        }
        if (source != null) {
            val preview = remember(source) {
                Bitmap.createBitmap(source.argb,source.width,source.height,Bitmap.Config.ARGB_8888)
                    .asImageBitmap()
            }
            Image(bitmap=preview,contentDescription="Imagem PNG original para prévia QA",
                modifier=Modifier.fillMaxWidth().height(180.dp))
            Text("${source.width} × ${source.height} px" +
                if (source.reduced) " • Redimensionada para QA: confira detalhes e vazados." else "",
                color=FioTextMuted)
            Text("Tipo de bordado",color=FioText)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    enabled=!busy,
                    onClick={
                        session.selectedMode=PremiumImageMode.DIGITIZED_FILL
                        session.invalidate()
                    }
                ) {
                    Text((if(session.selectedMode==PremiumImageMode.DIGITIZED_FILL)
                        "✓ " else "")+"Digitalizado",
                        color=FioGold)
                }
                OutlinedButton(
                    enabled=!busy,
                    onClick={
                        session.selectedMode=PremiumImageMode.SATIN_STUDY
                        session.invalidate()
                    }
                ) {
                    Text((if(session.selectedMode==PremiumImageMode.SATIN_STUDY)
                        "✓ " else "")+"Satin visual",
                        color=FioGold)
                }
            }
            if(session.selectedMode==PremiumImageMode.DIGITIZED_FILL) {
                Text("Quantidade de cores: ${session.colorLimit}",color=FioText)
                Slider(
                    value=session.colorLimit.toFloat(),
                    onValueChange={
                        session.colorLimit=it.roundToInt().coerceIn(2,8)
                        session.invalidate()
                    },
                    valueRange=2f..8f,steps=5,enabled=!busy,
                    modifier=Modifier.testTag("premium_qa_color_limit")
                )
            }
            Text("Largura: ${session.widthMm.roundToInt()} mm",color=FioText)
            Slider(value=session.widthMm,onValueChange = {
                session.widthMm=it;session.invalidate()
            },valueRange=10f..100f,enabled=!busy)
            Text("Bastidor de simulação",color=FioText)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf(HoopProfile.H100X100,HoopProfile.H130X180).forEach { candidate ->
                    OutlinedButton(onClick = {
                        session.hoop=candidate;session.invalidate()
                    }) {
                        Text((if (session.hoop==candidate) "✓ " else "")+
                            candidate.displayName,color=FioGold)
                    }
                }
            }
            // One primary action: generate a safe outline when possible;
            // otherwise show a clearly named, non-exportable scanned stitch
            // STUDY in the official simulator. Never block visualization
            // merely because two isolated glyph pixels touch diagonally.
            Button(
                modifier=Modifier.fillMaxWidth().testTag("premium_qa_simulate"),
                enabled=!busy,
                onClick={
                    val selected=source
                    val stamp=session.revision
                    val width=session.widthMm
                    val hoop=session.hoop
                    val mode=session.selectedMode
                    val numberOfColors=session.colorLimit
                    busy=true
                    message=""
                    scope.launch {
                        val generated=withContext(Dispatchers.Default) {
                            // The visual Satin-style study is the primary
                            // simulator product. Do NOT silently replace it
                            // with exportable contours or horizontal scans.
                            // If a region cannot be visually filled, only
                            // then offer closed outline playback instead.
                            val primary=if(mode==PremiumImageMode.DIGITIZED_FILL) {
                                PremiumImageDigitizedFillPreview.create(
                                    selected.width,selected.height,selected.argb,
                                    width,hoop,numberOfColors
                                )
                            } else {
                                PremiumImageSatinVisualPreview.create(
                                    selected.width,selected.height,selected.argb,
                                    width,hoop
                                )
                            }
                            if(primary.isSuccess) primary else
                                PremiumImageVisualStitchPreview.create(
                                    selected.width,selected.height,
                                    selected.argb,width,hoop
                                )
                        }
                        busy=false
                        if(stamp==session.revision &&
                            selected===session.photo &&
                            width==session.widthMm && hoop==session.hoop &&
                            mode==session.selectedMode &&
                            numberOfColors==session.colorLimit) {
                            generated.fold(
                                onSuccess={ preview ->
                                    session.prepared=preview
                                    session.simulated=preview
                                    message=if(preview.format=="QA_IMAGE_VISUAL") {
                                        "Arte original no fundo com pontos de preenchimento " +
                                            "por cima. Prévia NÃO homologada para PES."
                                    } else {
                                        "Prévia de contorno com pontos reais. " +
                                            "Confira o desenho antes do teste na máquina."
                                    }
                                    onSimulate(preview,hoop)
                                },
                                onFailure={
                                    message="Não foi possível preparar nem a prévia " +
                                        "de pontos: "+(it.message ?:
                                            "verifique o arquivo e o bastidor.")
                                }
                            )
                        }
                    }
                }
            ) {
                Text(if(busy) "Preparando simulação de pontos..."
                    else "Simular bordado")
            }
            val prepared=session.prepared
            if(prepared!=null && session.simulated===prepared) {
                Text("${prepared.stitchCount} pontos de prévia • " +
                    "${prepared.jumpCount} saltos • " +
                    "${prepared.bounds.widthMm.roundToInt()} × " +
                    "${prepared.bounds.heightMm.roundToInt()} mm",
                    color=FioText)
                if(prepared.format=="QA_IMAGE_VISUAL") {
                    Text(
                        "Enchimento ou Satin visual aproximado sobre a imagem original. " +
                            "Não possui underlay, densidade de fio ou compensação " +
                            "de tecido homologados; não exportável para a máquina.",
                        color=FioGold
                    )
                } else {
                    OutlinedButton(
                        onClick={
                            val reviewed=session.simulated
                            if(reviewed !== prepared) {
                                message="Simule primeiro o bordado."
                                return@OutlinedButton
                            }
                            val sample=PremiumImagePhysicalLab.pesForBench(reviewed)
                            sample.fold(
                                onSuccess={
                                    pending=it
                                    save.launch(it.fileName)
                                },
                                onFailure={
                                    message="A amostra física ainda não pode " +
                                        "ser exportada: "+(it.message?:
                                            "requer avaliação do contorno.")
                                }
                            )
                        },
                        enabled=!busy,
                        modifier=Modifier.fillMaxWidth()
                            .testTag("premium_qa_save")
                    ) {
                        Text("Após simular: salvar PES experimental",
                            color=FioGold)
                    }
                }
            }
        }
        if(message.isNotBlank()) Text(message,color=FioGold)
    }
}
