package com.timachado.brothermatrizes.core.updates

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.timachado.brothermatrizes.R

/**
 * Converte o vetor premium em bitmap válido para NotificationCompat.setLargeIcon.
 * O ícone pequeno continua monocromático (ic_notification_brother), conforme Android.
 * Não há dependência de imagens JPG obsoletas geradas pelo Gradle.
 */
internal object BrotherNotificationBrand {
    fun largeIcon(context: Context): Bitmap? = runCatching {
        val icon = requireNotNull(
            ContextCompat.getDrawable(context, R.drawable.ic_launcher_brother_premium)
        ).mutate()
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, size, size)
        icon.draw(Canvas(bitmap))
        bitmap
    }.getOrNull()
}
