package com.timachado.brothermatrizes.core.account

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

data class LocalDeviceIdentity(
    val deviceId: String,
    val deviceName: String
)

object DeviceIdentity {
    fun current(
        context: Context
    ): LocalDeviceIdentity {
        val androidId =
            Settings.Secure
                .getString(
                    context
                        .contentResolver,
                    Settings.Secure
                        .ANDROID_ID
                )
                .orEmpty()

        val raw =
            listOf(
                context.packageName,
                androidId,
                Build.MANUFACTURER,
                Build.MODEL
            ).joinToString(
                "|"
            )

        val id =
            MessageDigest
                .getInstance(
                    "SHA-256"
                )
                .digest(
                    raw.toByteArray(
                        Charsets.UTF_8
                    )
                )
                .joinToString(
                    separator = ""
                ) {
                    "%02x".format(
                        it
                    )
                }
                .take(
                    32
                )

        val manufacturer =
            Build.MANUFACTURER
                .trim()
                .replaceFirstChar {
                    if (
                        it.isLowerCase()
                    ) {
                        it.titlecase()
                    } else {
                        it.toString()
                    }
                }

        val model =
            Build.MODEL
                .trim()

        return LocalDeviceIdentity(
            deviceId =
                id,
            deviceName =
                listOf(
                    manufacturer,
                    model
                )
                    .filter {
                        it.isNotBlank()
                    }
                    .distinct()
                    .joinToString(
                        " "
                    )
                    .ifBlank {
                        "Android"
                    }
        )
    }
}
