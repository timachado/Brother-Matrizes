package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.account.AccountSnapshot
import java.time.Instant

/**
 * READ-ONLY bridge from the identity-bound official brother-entitlement
 * response into the ORIGINAL Android Plans and Benefits presentation.
 *
 * This must NEVER be passed to a quota gateway or treated as authorization
 * to import a font, matrix, activate trial or purchase anything. Only the
 * server may grant those actions after revalidating the bearer.
 */
internal object DesktopWindowsPlansPresentation {
    private val knownPro = setOf(
        "trial", "pro_monthly", "pro_yearly",
        "pro_lifetime", "pro_lifetime_launch"
    )
    private val finite = setOf("trial", "pro_monthly", "pro_yearly")

    fun displayOnly(verified: DesktopServerAccountSnapshot?): AccountSnapshot? {
        if (verified == null || !verified.commercialConfigured) return null
        if (!Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-" +
                "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$").matches(verified.userId) ||
            verified.email.length !in 5..254 ||
            verified.email.count { it == '@' } != 1) return null

        val paidClaim = verified.hasServerPro &&
            verified.subscriptionStatus == "active" &&
            verified.planCode in knownPro
        val expiry = verified.currentPeriodEnd?.let {
            runCatching { Instant.parse(it) }.getOrNull()
        }
        val paid = paidClaim && (
            (verified.planCode in finite && expiry?.isAfter(Instant.now()) == true) ||
                (verified.planCode !in finite && expiry == null)
            )

        return AccountSnapshot(
            userId = verified.userId,
            email = verified.email,
            displayName = verified.email.substringBefore('@'),
            avatarUrl = null,
            planCode = if (paid) verified.planCode else "free",
            subscriptionStatus = if (verified.subscriptionStatus == "active")
                "active" else "unavailable",
            currentPeriodEnd = if (paid && verified.planCode in finite)
                verified.currentPeriodEnd else null,
            trialStatus = verified.trialStatus,
            // Does not persist any token, plan, order, or capability.
            commercialConfigured = true
        )
    }
}
