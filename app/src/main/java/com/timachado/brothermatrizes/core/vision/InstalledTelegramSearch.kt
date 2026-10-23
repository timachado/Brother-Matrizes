package com.timachado.brothermatrizes.core.vision

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/**
 * Reuse Android's already logged-in Telegram app, like the existing
 * Brother Matrizes text-search button. It cannot read Telegram chats.
 */
object InstalledTelegramSearch {
    private val supportedPackages = listOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.telegram.messenger.beta"
    )

    @Suppress("DEPRECATION")
    fun installedPackage(context: Context): String? =
        supportedPackages.firstOrNull { packageName ->
            runCatching {
                if (Build.VERSION.SDK_INT >= 33) {
                    context.packageManager.getPackageInfo(
                        packageName, PackageManager.PackageInfoFlags.of(0L)
                    )
                } else context.packageManager.getPackageInfo(packageName, 0)
            }.isSuccess
        }

    fun openTextSearch(context: Context, query: String): Boolean {
        if (query.isBlank()) return false
        val telegram = installedPackage(context) ?: return false
        val url = Uri.parse("tg://search?query=${Uri.encode(query.take(70))}")
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, url)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .setPackage(telegram)
            )
        }.isSuccess
    }
}
