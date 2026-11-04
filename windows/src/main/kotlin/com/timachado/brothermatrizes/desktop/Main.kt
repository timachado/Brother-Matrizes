package com.timachado.brothermatrizes.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.nio.file.Files
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val cyan = Color(0xFF4EDAFF)
private val navy = Color(0xFF0A1625)
private val surface = Color(0xFF14273B)
private val muted = Color(0xFFACBDCD)
private val palette = listOf(
    Color(0xFF45D9FD), Color(0xFFF3BB60), Color(0xFFFC6C8A),
    Color(0xFF73D6AE), Color(0xFFC3AAFF), Color(0xFFE4E9F0)
)

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Brother Matrizes Desktop — QA",
        state = rememberWindowState(width = 1180.dp, height = 760.dp)
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = cyan,
                background = navy,
                surface = surface,
                onSurface = Color.White
            )
        ) {
            DesktopScreen()
        }
    }
}

@Composable
private fun DesktopScreen() {
    var preview by remember { mutableStateOf<DstPreview?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = navy) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "BROTHER MATRIZES",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "T.I. Machado  •  Windows Desktop  •  Homologação",
                        color = cyan,
                        fontSize = 13.sp
                    )
                }
                Button(onClick = {
                    val chooser = JFileChooser().apply {
                        dialogTitle = "Abrir matriz DST"
                        fileFilter = FileNameExtensionFilter("Matriz de bordado DST (*.dst)", "dst")
                        isAcceptAllFileFilterUsed = false
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        runCatching {
                            val file = chooser.selectedFile.toPath()
                            require(Files.isRegularFile(file)) { "Selecione um arquivo válido." }
                            require(Files.size(file) <= DstPreviewReader.MAX_FILE_BYTES) {
                                "Arquivo acima de 8 MiB, limite da prévia de homologação."
                            }
                            DstPreviewReader.read(
                                file.fileName.toString(), Files.readAllBytes(file)
                            )
                        }.onSuccess {
                            preview = it
                            message = null
                        }.onFailure {
                            message = it.message ?: "Não foi possível abrir a matriz."
                        }
                    }
                }) {
                    Text("Abrir matriz DST")
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Prévia Windows em desenvolvimento — leitura DST local, sem salvar ou alterar arquivos.",
                fontSize = 13.sp,
                color = muted
            )
            message?.let {
                Spacer(Modifier.height(10.dp))
                Text("Falha ao abrir: $it", color = Color(0xFFFF9DA9), fontSize = 13.sp)
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .background(surface, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (preview == null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Visualização de bordado", color = Color.White, fontSize = 20.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Abra um arquivo .DST do computador para visualizar as pontadas.",
                                color = muted
                            )
                        }
                    } else {
                        StitchPreview(preview!!)
                    }
                }
                Column(
                    modifier = Modifier.width(270.dp).fillMaxHeight()
                        .background(surface, RoundedCornerShape(18.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Detalhes da matriz", color = cyan, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    if (preview == null) {
                        Text("Nenhum arquivo aberto.", color = muted)
                    } else {
                        val d = preview!!
                        Detail("Arquivo", d.fileName)
                        d.label?.let { Detail("Nome", it) }
                        Detail("Pontos de costura", "%,d".format(Locale("pt", "BR"), d.stitchCount))
                        Detail("Saltos", "%,d".format(Locale("pt", "BR"), d.jumpCount))
                        Detail("Trocas de cor", d.colorChanges.toString())
                        Detail("Largura", "%.1f mm".format(Locale("pt", "BR"), d.bounds.widthMm))
                        Detail("Altura", "%.1f mm".format(Locale("pt", "BR"), d.bounds.heightMm))
                        Text(
                            "Imagem técnica das pontadas; não substitui simulação de máquina.",
                            color = muted,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "DST: prévia disponível em QA. PES, JEF, editor, conversão e biblioteca ainda serão portados.",
                        color = muted,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Column {
        Text(label, color = muted, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 15.sp)
    }
}

@Composable
private fun StitchPreview(preview: DstPreview) {
    Canvas(modifier = Modifier.fillMaxSize().padding(28.dp)) {
        renderPreview(preview)
    }
}

private fun DrawScope.renderPreview(design: DstPreview) {
    val width = (design.bounds.maxX.toLong() - design.bounds.minX.toLong()).coerceAtLeast(1L).toFloat()
    val height = (design.bounds.maxY.toLong() - design.bounds.minY.toLong()).coerceAtLeast(1L).toFloat()
    val usableW = (size.width - 36f).coerceAtLeast(1f)
    val usableH = (size.height - 36f).coerceAtLeast(1f)
    val scale = minOf(usableW / width, usableH / height)
    val offsetX = (size.width - width * scale) / 2f
    val offsetY = (size.height - height * scale) / 2f

    fun pointOffset(x: Int, y: Int) = Offset(
        offsetX + (x.toLong() - design.bounds.minX.toLong()).toFloat() * scale,
        offsetY + (y.toLong() - design.bounds.minY.toLong()).toFloat() * scale
    )

    // Prévia otimizada em matrizes grandes: amostragem apenas da visualização.
    // Nunca modifica os pontos do arquivo original.
    val stride = maxOf(1, (design.points.size + 199_999) / 200_000)
    var previousX = 0
    var previousY = 0
    design.points.forEachIndexed { index, p ->
        if (p.command == PreviewCommand.STITCH && index % stride == 0) {
            drawLine(
                color = palette[p.colorIndex % palette.size],
                start = pointOffset(previousX, previousY),
                end = pointOffset(p.x, p.y),
                strokeWidth = 1.3f
            )
        }
        previousX = p.x
        previousY = p.y
    }
}
