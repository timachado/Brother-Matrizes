package com.timachado.brothermatrizes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.HoopProfile

/**
 * Desenha SOMENTE as pontadas do nome sobre a moldura original.
 * Ambos usam o mesmo centro, direção e escala: a moldura jamais salta
 * quando o nome é movido e a renderização final não duplica o desenho.
 */
/**
 * Largura visual aproximada da linha bordada (~0,24 mm).
 * No enquadramento de 13x18 cm, 1,35 dp fixos tornam cada pontada
 * várias vezes mais grossa do que a linha real, empelotando "Maria".
 * A linha fica antialiasada mas não artificialmente grossa.
 */
internal fun nameStitchPreviewWidthPx(scale: Float): Float =
    (2.4f * scale.coerceAtLeast(.001f)).coerceIn(.65f, 1.3f)

@Composable
internal fun NameEditorStitchLayer(
    original: EmbroideryDesign,
    result: EmbroideryDesign,
    hoop: HoopProfile,
    modifier: Modifier = Modifier
) {
    val paths = remember(original, result) {
        val firstNewPoint = (original.points.size - 1).coerceAtLeast(0)
        val stitches = result.points.subList(
            firstNewPoint.coerceAtMost(result.points.size),
            result.points.size
        )
        val grouped = linkedMapOf<Int, Path>()
        visitRenderedStitchSegments(stitches) { from, to ->
            val path = grouped.getOrPut(to.colorIndex) { Path() }
            path.moveTo(from.xUnits.toFloat(), from.yUnits.toFloat())
            path.lineTo(to.xUnits.toFloat(), to.yUnits.toFloat())
        }
        grouped.toList()
    }

    Canvas(modifier.fillMaxSize()) {
        val origin = NameEditorPlacement.screenOffset(
            design = original, hoop = hoop,
            xUnits = 0, yUnits = 0,
            canvasWidth = size.width.toInt(),
            canvasHeight = size.height.toInt(),
            paddingPixels = 36.dp.toPx()
        )
        val scale = origin.unitsPerPixel
        val direction = if (original.sourceYAxisDown) 1f else -1f
        withTransform({
            translate(
                left = size.width / 2f + origin.x,
                top = size.height / 2f + origin.y
            )
            scale(scaleX = scale, scaleY = scale * direction, pivot = Offset.Zero)
        }) {
            paths.forEach { (colorIndex, path) ->
                val thread = result.threadColors.getOrNull(colorIndex)
                    ?: 0xB22222
                drawPath(
                    path = path,
                    color = Color(0xFF000000 or thread.toLong()),
                    style = Stroke(
                        width = nameStitchPreviewWidthPx(scale) / scale,
                        cap = StrokeCap.Round
                    )
                )
            }
        }
    }
}
