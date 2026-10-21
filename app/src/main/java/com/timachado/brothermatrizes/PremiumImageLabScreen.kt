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

/** Survives navigation to SimulatorScreen without persisting experimental PES. */
internal class PremiumImageLabSession {
    var photo by mutableStateOf<PremiumImageLabPhoto?>(null)
    var widthMm by mutableFloatStateOf(60f)
    var hoop by mutableStateOf(HoopProfile.H100X100)
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
            "Importe PNG transparente, gere pontos corridos reais, simule no bastidor do aplicativo e só depois salve o PES de ensaio.",
            color=FioTextMuted
        )
        Text(
            "ATENÇÃO: apenas contorno experimental. Não há preenchimento Satin/Tatami nem aprovação de máquina. Confira a prévia e use apenas retalho.",
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
            Button(
                modifier=Modifier.fillMaxWidth().testTag("premium_qa_generate"),
                enabled=!busy,onClick={
                    val selected=source
                    val stamp=session.revision
                    val width=session.widthMm
                    val hoop=session.hoop
                    busy=true
                    scope.launch {
                        val result=withContext(Dispatchers.Default) {
                            PremiumImagePhysicalLab.propose(
                                selected.width,selected.height,selected.argb,width,hoop)
                        }
                        busy=false
                        if(stamp==session.revision) result.fold(
                            onSuccess = {
                                session.prepared=it
                                session.simulated=null
                                message="Pontos gerados: ${it.stitchCount} pontos. Toque em SIMULAR."
                            },
                            onFailure = { message="Prévia recusada: "+(it.message?:"simplifique o desenho PNG") }
                        )
                    }
                }
            ) { Text(if (busy) "Analisando pontos..." else "1. Gerar pontos de contorno") }
            val prepared=session.prepared
            if(prepared!=null) {
                Text("${prepared.stitchCount} pontos • ${prepared.jumpCount} deslocamentos • " +
                    "${prepared.bounds.widthMm.roundToInt()} × ${prepared.bounds.heightMm.roundToInt()} mm",
                    color=FioText)
                Button(
                    onClick = {
                        session.simulated=prepared
                        onSimulate(prepared,session.hoop)
                    },
                    enabled=!busy,
                    modifier=Modifier.fillMaxWidth().testTag("premium_qa_simulate")
                ) { Text("2. Simular pontos no bastidor") }
                OutlinedButton(
                    onClick = {
                        val reviewed=session.simulated
                        if(reviewed !== prepared) {
                            message="Abra primeiro o simulador do Brother Matrizes."
                            return@OutlinedButton
                        }
                        val sample=PremiumImagePhysicalLab.pesForBench(reviewed)
                        sample.fold(onSuccess={
                            pending=it
                            save.launch(it.fileName)
                        },onFailure={
                            message="PES de ensaio recusado: "+(it.message?:"auditoria não concluída")
                        })
                    },
                    enabled=!busy && session.simulated === prepared,
                    modifier=Modifier.fillMaxWidth().testTag("premium_qa_save")
                ) { Text("3. Salvar PES de teste físico (não homologado)",color=FioGold) }
            }
        }
        if(message.isNotBlank()) Text(message,color=FioGold)
    }
}
