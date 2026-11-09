package com.timachado.brothermatrizes.desktop.platform

/**
 * Optional information-only checks for official Windows releases.
 *
 * Runs ONLY while the original Brother Matrizes Windows app is open and the
 * user has explicitly enabled WINDOWS release alerts. No FCM, service,
 * scheduled task, autostart, installer or executable download. Android
 * news consent is an independent setting in a separate file.
 *
 * Missing manifest (HTTP 404): no Windows release is announced.
 * Untrusted manifest: throws; caller fails closed and keeps the previous
 * notification marker so a false update cannot be displayed.
 */
internal class DesktopWindowsOfficialReleaseAutoCheck(
    private val preferences: DesktopWindowsNewsPreferences,
    private val fetch: suspend () -> DesktopOfficialWindowsReleaseNotice.Notice? = {
        DesktopOfficialWindowsReleaseNotice().checkPublishedWindowsRelease()
    },
    private val notify: (String, String) -> Boolean = { title, message ->
        DesktopWindowsLocalNotification.showAfterUserConsent(title, message)
    }
) {
    enum class Outcome {
        DISABLED, NOT_PUBLISHED, UNCHANGED, NOTIFIED, SYSTEM_UNAVAILABLE
    }

    suspend fun checkOnce(): Outcome {
        val previous = preferences.load()
        if (!previous.enabled) return Outcome.DISABLED

        val published = fetch()
        // Re-check consent following network activity; the user may disable
        // alerts or the process may have reloaded its local preference.
        val latest = preferences.load()
        if (!latest.enabled) return Outcome.DISABLED
        if (published == null) return Outcome.NOT_PUBLISHED

        if (published.versionCode <= latest.lastNotifiedCode) {
            return Outcome.UNCHANGED
        }

        // Write marker before posting so restart does not duplicate a pop-up.
        // A display failure never installs/downloads/tries another URI.
        preferences.save(latest.copy(lastNotifiedCode = published.versionCode))
        val title = "Brother Matrizes — novidades Windows"
        val message = "Versão Windows ${published.versionName} publicada " +
            "no canal oficial. Consulte Comunidade para detalhes."
        return if (notify(title, message)) {
            Outcome.NOTIFIED
        } else {
            Outcome.SYSTEM_UNAVAILABLE
        }
    }

    /**
     * Activation is explicitly initiated by the user. Baseline the latest
     * published version before enabling to avoid replaying old alerts.
     * If the official channel is not yet published (404), keep baseline.
     * On network failure, do NOT persist new consent.
     */
    suspend fun setEnabled(enable: Boolean): Boolean {
        val previous = preferences.load()
        if (!enable) {
            preferences.save(previous.copy(enabled = false))
            return false
        }

        val currentlyPublished = fetch()
        val code = currentlyPublished?.versionCode ?: 0
        preferences.save(previous.copy(
            enabled = true,
            lastNotifiedCode = maxOf(previous.lastNotifiedCode, code)
        ))
        return true
    }
}
