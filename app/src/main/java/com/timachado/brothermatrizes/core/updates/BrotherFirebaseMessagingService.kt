package com.timachado.brothermatrizes.core.updates

import android.Manifest
import android.app.NotificationChannel
import android.graphics.Color
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
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
        if (!BrotherUpdateNotificationWorker.notificationsAllowed(this)) return
        val data = message.data
        when (data["type"]) {
            "update" -> {
                if (!BrotherFirebasePush.isEnabled(this)) return
                val versionCode = data["versionCode"]?.toLongOrNull() ?: return
                if (!BrotherPushPayloadRules.shouldPost(
                        data, BuildConfig.VERSION_CODE.toLong(), 0L
                    )) return
                if (BrotherUpdateNotificationGate.postOnce(
                        this, BuildConfig.VERSION_CODE.toLong(), versionCode
                    ) {
                        showUpdateNotification(
                            BrotherPushPayloadRules.notificationTitle(data),
                            BrotherPushPayloadRules.notificationBody(data)
                        )
                    }
                ) {
                    BrotherInAppUpdateEvents.announce(applicationContext)
                }
            }
            "community" -> {
                if (!BrotherFirebasePush.isCommunityEnabled(this) ||
                    !BrotherCommunityPayloadRules.shouldPost(data)) return
                val eventId = BrotherCommunityPayloadRules.eventId(data) ?: return
                BrotherCommunityNotificationGate.postOnce(this, eventId) {
                    showCommunityNotification(
                        BrotherCommunityPayloadRules.title(data),
                        BrotherCommunityPayloadRules.body(data)
                    )
                }
            }
            else -> return
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
            .setSmallIcon(R.drawable.ic_notification_brother)
            .setLargeIcon(BrotherNotificationBrand.largeIcon(this))
            .setColor(Color.rgb(234, 192, 110))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()
        // Permissao de runtime exigida no Android 13+; a checagem
        // explicita protege a chamada e e reconhecida pelo Android Lint.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return@runCatching false
        NotificationManagerCompat.from(this).notify(
            BrotherUpdateNotificationWorker.NOTIFICATION_ID, notification
        )
        true
    }.getOrDefault(false)

    private fun showCommunityNotification(title: String, body: String): Boolean = runCatching {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                COMMUNITY_CHANNEL_ID,
                "Comunidade Brother Matrizes",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Novidades, tutoriais e informações do grupo oficial."
            }
        )
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "com.timachado.brothermatrizes.OPEN_COMMUNITY"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 170, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, COMMUNITY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_brother)
            .setLargeIcon(BrotherNotificationBrand.largeIcon(this))
            .setColor(Color.rgb(234, 192, 110))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()
        // Permissao de runtime exigida no Android 13+; a checagem
        // explicita protege a chamada e e reconhecida pelo Android Lint.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return@runCatching false
        NotificationManagerCompat.from(this).notify(COMMUNITY_NOTIFICATION_ID, notification)
        true
    }.getOrDefault(false)

    companion object {
        private const val COMMUNITY_CHANNEL_ID = "brother_community"
        private const val COMMUNITY_NOTIFICATION_ID = 32002
    }
}
