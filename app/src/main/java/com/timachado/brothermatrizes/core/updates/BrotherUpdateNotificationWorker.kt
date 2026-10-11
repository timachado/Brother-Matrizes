package com.timachado.brothermatrizes.core.updates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.timachado.brothermatrizes.BuildConfig
import com.timachado.brothermatrizes.MainActivity
import com.timachado.brothermatrizes.R
import java.util.concurrent.TimeUnit

/**
 * Aviso local de atualização disponível, sem FCM ou Supabase.
 * O Android programa a checagem: não há garantia de horário ou envio instantâneo.
 * A publicação deve atualizar o WordPress e o espelho GitHub Raw.
 */
class BrotherUpdateNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!notificationsAllowed(context)) return Result.success()

        val endpoint = BuildConfig.WORDPRESS_URL.trimEnd('/') +
            "/wp-json/tim-brother-updates/v1/latest?channel=stable"
        val response = BrotherUpdateClient(endpoint).check(context)
        return when (response) {
            is BrotherUpdateClient.Result.Available -> {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val published = response.release.versionCode
                if (UpdateNotificationRules.shouldNotify(
                        installedCode = BuildConfig.VERSION_CODE.toLong(),
                        publishedCode = published,
                        lastNotifiedCode = prefs.getLong("last_notified_version_code", 0L)
                    )
                ) {
                    if (postUpdateNotification(context, response)) {
                        prefs.edit().putLong("last_notified_version_code", published).apply()
                    }
                }
                Result.success()
            }
            is BrotherUpdateClient.Result.Error -> Result.success()
            else -> Result.success()
        }
    }

    private fun postUpdateNotification(
        context: Context,
        available: BrotherUpdateClient.Result.Available
    ): Boolean = runCatching {
        if (!notificationsAllowed(context)) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Atualizações do Brother Matrizes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisos de novas versões do aplicativo Brother Matrizes."
            }
        )
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.timachado.brothermatrizes.OPEN_UPDATES"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openApp = PendingIntent.getActivity(
            context, 169, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notice = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_brother_matrizes)
            .setContentTitle("Brother Matrizes: atualização disponível")
            .setContentText(
                "Versão \${available.release.versionName} disponível. Toque para atualizar."
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "A versão \${available.release.versionName} do Brother Matrizes está disponível. " +
                        "Abra o aplicativo para ver as novidades e atualizar com segurança."
                )
            )
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notice)
        true
    }.getOrDefault(false)

    companion object {
        const val CHANNEL_ID = "brother_app_updates"
        const val NOTIFICATION_ID = 32001
        const val PREFS = "brother_updates_notifications"
        private const val UNIQUE_WORK = "brother_check_app_updates"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<BrotherUpdateNotificationWorker>(
                1, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        fun notificationsAllowed(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return false
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
}
