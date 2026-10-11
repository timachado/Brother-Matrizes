package com.timachado.brothermatrizes.core.updates

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.timachado.brothermatrizes.BuildConfig
import com.timachado.brothermatrizes.MainActivity
import com.timachado.brothermatrizes.R

/**
 * Recebe push FCM data-only. O payload inclui type=update, versionCode e versionName.
 * Mensagens sem versão válida ou de versões já instaladas são ignoradas.
 */
class BrotherFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Nunca registrar ou expor tokens nos logs, WordPress ou Supabase.
        BrotherFirebasePush.sync(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (!BrotherFirebasePush.isEnabled(this) ||
            !BrotherUpdateNotificationWorker.notificationsAllowed(this)) return
        val data = message.data
        val prefs = getSharedPreferences(
            BrotherUpdateNotificationWorker.PREFS, Context.MODE_PRIVATE
        )
        val lastCode = prefs.getLong("last_notified_version_code", 0L)
        if (!BrotherPushPayloadRules.shouldPost(
                data, BuildConfig.VERSION_CODE.toLong(), lastCode
            )) return

        val versionCode = data["versionCode"]?.toLongOrNull() ?: return
        if (showUpdateNotification(
                BrotherPushPayloadRules.notificationTitle(data),
                BrotherPushPayloadRules.notificationBody(data)
            )) {
            prefs.edit().putLong("last_notified_version_code", versionCode).apply()
        }
    }

    private fun showUpdateNotification(title: String, body: String): Boolean = runCatching {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                BrotherUpdateNotificationWorker.CHANNEL_ID,
                "Atualizações do Brother Matrizes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações sobre novas versões do aplicativo."
            }
        )
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "com.timachado.brothermatrizes.OPEN_UPDATES"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 169, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(
            this, BrotherUpdateNotificationWorker.CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_launcher_brother_matrizes)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(this).notify(
            BrotherUpdateNotificationWorker.NOTIFICATION_ID, notification
        )
        true
    }.getOrDefault(false)
}
