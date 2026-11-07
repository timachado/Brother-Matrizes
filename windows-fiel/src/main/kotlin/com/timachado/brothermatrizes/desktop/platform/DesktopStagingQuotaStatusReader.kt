package com.timachado.brothermatrizes.desktop.platform

import java.net.HttpURLConnection
import java.net.URL

/**
 * Staging-only diagnostic adapter. Exposes ONLY lookupKnown; no reserve,
 * finalize, rollback, local cleanup or entitlement grant.
 *
 * Although brother-usage transports read-only status over HTTP POST, the
 * payload action is restricted by the delegate's lookupKnown contract to
 * "status" or "status_pro_v2". Never wire this to a commercial import UI.
 */
internal class DesktopStagingQuotaStatusReader(
    target: DesktopQaStagingProject,
    publishableKey: String,
    versionCode: Int,
    connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) : DesktopArchiveQuotaStatusReader {
    private val delegate = DesktopBrotherArchiveUsageGateway(
        publishableKey = publishableKey,
        versionCode = versionCode,
        connectionFactory = connectionFactory,
        projectRef = target.ref
    )

    override suspend fun lookupKnown(
        accessToken: String,
        category: DesktopArchiveInventory.Category,
        requestKey: String
    ): DesktopArchiveQuotaStatus =
        delegate.lookupKnown(accessToken, category, requestKey)

    override fun toString(): String = "DesktopStagingQuotaStatusReader(redacted)"
}
