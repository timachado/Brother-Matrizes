package com.timachado.brothermatrizes

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.timachado.brothermatrizes.core.embroidery.RasterCropBounds
import com.timachado.brothermatrizes.core.embroidery.RasterImagePixels
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private data class CropPreviewArea(
    val left: Float, val top: Float, val width: Float, val height: Float
) {
    fun normalized(at: Offset) = Offset(
        ((at.x - left) / width).coerceIn(0f, 1f),
        ((at.y - top) / height).coerceIn(0f, 1f)
    )
}

/** Drag across the visible photo to select the precise area to digitize. */
@Composable
internal fun RasterPhotoCropCanvas(
    photo: RasterImagePixels,
    selection: RasterCropBounds,
    onSelection: (RasterCropBounds) -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(photo) {
        Bitmap.createBitmap(photo.argb, photo.width, photo.height,
            Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    val currentOnSelection by rememberUpdatedState(onSelection)
    val currentSelection by rememberUpdatedState(selection)
    fun area(w: Float, h: Float): CropPreviewArea {
        val scale = min(w / photo.width, h / photo.height)
        val pw = photo.width * scale
        val ph = photo.height * scale
        return CropPreviewArea((w - pw) / 2, (h - ph) / 2, pw, ph)
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(205.dp)
            .pointerInput(photo) {
                var initial: Offset? = null
                detectDragGestures(
                    onDragStart = { at ->
                        val a = area(size.width.toFloat(), size.height.toFloat())
                        initial = a.normalized(at)
                    },
                    onDragEnd = { initial = null },
                    onDragCancel = { initial = null },
                    onDrag = { change, _ ->
                        val begin = initial ?: return@detectDragGestures
                        change.consume()
                        val a = area(size.width.toFloat(), size.height.toFloat())
                        val end = a.normalized(change.position)
                        val left = min(begin.x, end.x)
                        val top = min(begin.y, end.y)
                        val right = max(begin.x, end.x)
                        val bottom = max(begin.y, end.y)
                        if (right - left >= 0.08f && bottom - top >= 0.08f) {
                            currentOnSelection(RasterCropBounds(left, top, right, bottom))
                        }
                    }
                )
            }
    ) {
        val a = area(size.width, size.height)
        drawRect(Color(0xFF14232C))
        drawImage(
            image = bitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(photo.width, photo.height),
            dstOffset = IntOffset(a.left.roundToInt(), a.top.roundToInt()),
            dstSize = IntSize(a.width.roundToInt(), a.height.roundToInt())
        )
        val crop = currentSelection
        val x0 = a.left + crop.left * a.width
        val y0 = a.top + crop.top * a.height
        val x1 = a.left + crop.right * a.width
        val y1 = a.top + crop.bottom * a.height
        val overlay = Color.Black.copy(alpha = .48f)
        drawRect(overlay, Offset(a.left, a.top), Size(a.width, max(0f,y0-a.top)))
        drawRect(overlay, Offset(a.left,y1), Size(a.width, max(0f,a.top+a.height-y1)))
        drawRect(overlay, Offset(a.left,y0), Size(max(0f,x0-a.left), max(0f,y1-y0)))
        drawRect(overlay, Offset(x1,y0), Size(max(0f,a.left+a.width-x1), max(0f,y1-y0)))
        drawRect(
            Color(0xFFE8B879), Offset(x0,y0),
            Size(max(1f,x1-x0),max(1f,y1-y0)),
            style = Stroke(width = 2.dp.toPx())
        )
        for (p in listOf(Offset(x0,y0), Offset(x1,y0), Offset(x0,y1), Offset(x1,y1))) {
            drawCircle(Color(0xFFE8B879), radius = 4.5.dp.toPx(), center = p)
        }
    }
}
