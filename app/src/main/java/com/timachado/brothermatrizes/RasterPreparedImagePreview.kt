package com.timachado.brothermatrizes

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.timachado.brothermatrizes.core.embroidery.RasterImagePixels
import kotlin.math.min
import kotlin.math.roundToInt

/** Read-only result preview with a chessboard underneath truly transparent pixels. */
@Composable
internal fun RasterPreparedImagePreview(
    image: RasterImagePixels,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(image) {
        Bitmap.createBitmap(
            image.argb, image.width, image.height,
            Bitmap.Config.ARGB_8888
        ).asImageBitmap()
    }
    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        drawRect(Color(0xFF15242B))
        val ratio = min(size.width / image.width, size.height / image.height)
        val pw = image.width * ratio
        val ph = image.height * ratio
        val x0 = (size.width - pw) / 2f
        val y0 = (size.height - ph) / 2f
        val tile = 12.dp.toPx()
        val columns = kotlin.math.ceil(pw / tile).toInt().coerceAtLeast(1)
        val rows = kotlin.math.ceil(ph / tile).toInt().coerceAtLeast(1)
        for (row in 0 until rows) for (column in 0 until columns) {
            val x = x0 + column * tile
            val y = y0 + row * tile
            drawRect(
                color = if ((row + column) % 2 == 0)
                    Color(0xFFEEEEEE) else Color(0xFFBABABA),
                topLeft = Offset(x, y),
                size = Size(
                    min(tile, x0 + pw - x),
                    min(tile, y0 + ph - y)
                )
            )
        }
        drawImage(
            image = bitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(x0.roundToInt(), y0.roundToInt()),
            dstSize = IntSize(pw.roundToInt().coerceAtLeast(1), ph.roundToInt().coerceAtLeast(1))
        )
    }
}
