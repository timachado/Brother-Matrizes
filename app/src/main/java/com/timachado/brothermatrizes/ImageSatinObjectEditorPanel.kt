package com.timachado.brothermatrizes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.embroidery.ManualSatinSection
import com.timachado.brothermatrizes.core.embroidery.RasterSatinObject
import com.timachado.brothermatrizes.core.embroidery.SatinRailPair
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted

/**
 * Lightweight phone-friendly UI for digitizing one component at a time.
 * This does not modify the original commercial Viewer or Simulator.
 */
@Composable
internal fun ImageSatinObjectEditorPanel(
    objects: List<RasterSatinObject>,
    active: Int,
    sections: List<ManualSatinSection>,
    sectionIndex: Int,
    imageWidth: Int,
    reviewedCount: Int,
    touched: Boolean,
    onPart: (Int) -> Unit,
    onSection: (Int) -> Unit,
    onChange: (List<ManualSatinSection>) -> Unit,
    onAuto: () -> Unit,
    autoBusy: Boolean,
    onReset: () -> Unit
) {
    val item = objects.getOrNull(active) ?: return
    if (sections.isEmpty()) return
    val activeIndex = sectionIndex.coerceIn(0, sections.lastIndex)
    val section = sections[activeIndex]
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Reconstruir contornos Satin", color = FioText,
            fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Text("Parte ${active + 1} de ${objects.size} • ${reviewedCount} com trilhos editados",
            color = FioTextMuted, fontSize = 12.sp)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            objects.forEachIndexed { index, part ->
                OutlinedButton(onClick = { onPart(index) }) {
                    Text(
                        (if (index == active) "● " else "") + "Parte ${index + 1}",
                        color = FioGold, fontSize = 12.sp
                    )
                }
            }
        }
        Text(
            "Linha ciano: contorno vetorial; laranja: furo. " +
                "O guia automático só atende faixas simples, sem furos ou ramos. " +
                "Confira e ajuste os trilhos dourados e azuis antes de simular.",
            color = FioTextMuted, fontSize = 12.sp
        )
        ManualSatinRailCanvas(
            art = item.mask,
            imageWidth = imageWidth,
            colorIndex = 0,
            section = section,
            vectorContours = item.vectorContours,
            onBeginDrag = {},
            onEdit = { edited ->
                onChange(sections.toMutableList().apply { set(activeIndex, edited) })
            },
            modifier = Modifier.fillMaxWidth().height(275.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sections.forEachIndexed { index, _ ->
                OutlinedButton(onClick = { onSection(index) }) {
                    Text(
                        (if (index == activeIndex) "● " else "") + "Seção ${index + 1}",
                        color = FioGold, fontSize = 12.sp
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val rails = section.rails
                    if (rails.size >= 100) return@OutlinedButton
                    val last = rails.last()
                    val prev = rails[rails.lastIndex - 1]
                    fun move(a: Float, b: Float) =
                        (a + (a - b) * .5f).coerceIn(0.01f, 0.99f)
                    val next = SatinRailPair(
                        move(last.ax, prev.ax), move(last.ay, prev.ay),
                        move(last.bx, prev.bx), move(last.by, prev.by)
                    )
                    onChange(sections.toMutableList().apply {
                        set(activeIndex, section.copy(rails = rails + next))
                    })
                }
            ) { Text("+ Par", color = FioGold, fontSize = 12.sp) }
            OutlinedButton(
                onClick = {
                    if (section.rails.size <= 2) return@OutlinedButton
                    onChange(sections.toMutableList().apply {
                        set(activeIndex, section.copy(rails = section.rails.dropLast(1)))
                    })
                }
            ) { Text("− Par", color = FioGold, fontSize = 12.sp) }
            OutlinedButton(onClick = {
                if (sections.size >= 20) return@OutlinedButton
                val fresh = ManualSatinSection(rails = listOf(
                    SatinRailPair(.40f, .45f, .60f, .45f),
                    SatinRailPair(.40f, .55f, .60f, .55f)
                ))
                onChange(sections + fresh)
                onSection(sections.size)
            }) { Text("+ Seção", color = FioGold, fontSize = 12.sp) }
            OutlinedButton(onClick = {
                if (sections.size <= 1) return@OutlinedButton
                onChange(sections.filterIndexed { index, _ -> index != activeIndex })
                onSection((activeIndex - 1).coerceAtLeast(0))
            }) { Text("− Seção", color = FioGold, fontSize = 12.sp) }
        }
        OutlinedButton(
            onClick = onAuto,
            enabled = !autoBusy && item.vectorContours.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (autoBusy) "Analisando geometria…"
                else "Sugerir trilhos pelos contornos",
                color = FioGold, fontSize = 13.sp
            )
        }
        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("Reiniciar guia desta parte", color = FioGold, fontSize = 12.sp)
        }
        Text(
            if (touched) {
                "Trilhos revisados: a simulação continua restrita a esta parte."
            } else {
                "Sugestão não homologada: arraste algum trilho após conferir os contornos."
            },
            color = FioTextMuted, fontSize = 11.sp
        )
    }
}
