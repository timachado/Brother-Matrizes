package com.timachado.brothermatrizes.desktop.platform

import java.net.HttpURLConnection
import java.net.URL

/**
 * Existing Brother Matrizes backend, unchanged for Android and Windows.
 * This Windows adapter can LOOK UP a known quota status only.
 * Never reserves, finalizes, creates separate entitlements or clears markers.
 *
 * HTTP uses the server's established action=status/status_pro_v2 POST
 * protocol, but this adapter is NOT wired to the commercial import UI.
 */
internal class DesktopBrotherKnownQuotaStatusReader(
    publishableKey: String,
    versionCode: Int,
    connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) : DesktopArchiveQuotaStatusReader {
    private val gateway = DesktopBrotherArchiveUsageGateway(
        publishableKey = publishableKey,
        versionCode = versionCode,
        connectionFactory = connectionFactory,
        projectRef = DesktopBrotherBackendTarget.OFFICIAL_REF
    )

    override suspend fun lookupKnown(
        accessToken: String,
        category: DesktopArchiveInventory.Category,
        requestKey: String
    ): DesktopArchiveQuotaStatus =
        gateway.lookupKnown(accessToken, category, requestKey)

    override fun toString(): String = "DesktopBrotherKnownQuotaStatusReader(redacted)"
}
