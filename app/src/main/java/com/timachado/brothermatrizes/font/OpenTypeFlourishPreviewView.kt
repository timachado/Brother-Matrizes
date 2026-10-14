package com.timachado.brothermatrizes.font

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.min

/**
 * A amostra da fonte deve desenhar o MESMO contorno vetorial usado no Satin.
 * Não usa TextView para uma combinação por posição: o Android não suporta
 * habilitar fontFeatureSettings para índices de letras dentro de um TextView.
 */
internal class OpenTypeFlourishPreviewView(context: Context) : View(context) {
    private val painter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 94f
        hinting = Paint.HINTING_OFF
        isSubpixelText = true
    }
    private var path: Path? = null
    private val bounds = RectF()
    private var error: String? = null

    fun show(
        text: String,
        typeface: Typeface?,
        globalTag: String?,
        positioning: OpenTypePositionedStyle,
        colorInt: Int
    ) {
        painter.color = colorInt
        painter.typeface = typeface
        error = null
        path = null
        if (text.isBlank() || typeface == null) {
            error = "Escolha uma fonte."
            invalidate()
            return
        }
        runCatching {
            val output = when {
                positioning.isEnabled -> {
                    OpenTypePositionedPath.compose(painter, text, positioning).path
                }
                else -> {
                    val p = Path()
                    painter.fontFeatureSettings = OpenTypeStyleChoices.fontSettings(globalTag)
                    painter.getTextPath(text, 0, text.length, 0f, 0f, p)
                    p
                }
            }
            require(!output.isEmpty) { "A fonte não possui contorno para este texto." }
            output.computeBounds(bounds, true)
            require(bounds.width() > 0 && bounds.height() > 0)
            path = output
        }.onFailure { error = it.message ?: "Não foi possível renderizar o floreio." }
        painter.fontFeatureSettings = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = path
        if (current == null) {
            painter.fontFeatureSettings = null
            painter.textSize = 13f * resources.displayMetrics.scaledDensity
            canvas.drawText((error ?: "Prévia indisponível").take(58),
                8f, height / 2f, painter)
            painter.textSize = 94f
            return
        }
        val maxWidth = (width - 18f).coerceAtLeast(1f)
        val maxHeight = (height - 18f).coerceAtLeast(1f)
        val scale = min(maxWidth / bounds.width().coerceAtLeast(1f),
            maxHeight / bounds.height().coerceAtLeast(1f))
        if (!scale.isFinite() || scale <= 0f) return
        canvas.save()
        canvas.translate(width / 2f - (bounds.left + bounds.right) * scale / 2f,
            height / 2f - (bounds.top + bounds.bottom) * scale / 2f)
        canvas.scale(scale, scale)
        canvas.drawPath(current, painter)
        canvas.restore()
    }
}
