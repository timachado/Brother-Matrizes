package com.timachado.brothermatrizes

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * External Android handoff only. No private storage paths, no RAR SDK,
 * no assumption that RAR returns extracted files to Brother Matrizes.
 */
object RarExternalOpener {
    const val PACKAGE = "com.rarlab.rar"
    private const val MARKET = "market://details?id=com.rarlab.rar"
    private const val STORE = "https://play.google.com/store/apps/details?id=com.rarlab.rar"

    fun installed(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(PACKAGE) != null

    fun launch(context: Context): Result<Unit> = runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE)
            ?: error("O aplicativo RAR não está instalado.")
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun handoff(context: Context, uri: Uri, preferRar: Boolean): Result<Unit> =
        runCatching {
            val name = uri.lastPathSegment.orEmpty().lowercase()
            val type = when {
                name.endsWith(".zip") -> "application/zip"
                name.endsWith(".rar") -> "application/vnd.rar"
                name.endsWith(".7z") -> "application/x-7z-compressed"
                else -> "application/octet-stream"
            }
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, type)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (preferRar && installed(context)) setPackage(PACKAGE)
            }
            val toOpen = if (preferRar && installed(context)) view
                else Intent.createChooser(view, "Escolher descompactador")
            context.startActivity(toOpen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

    fun install(context: Context): Result<Unit> = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(MARKET))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(STORE))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } else {
            context.startActivity(intent)
        }
    }
}
