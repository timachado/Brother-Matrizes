package com.timachado.brothermatrizes.desktop.platform

/**
 * Checks official ANDROID news only while the Windows application is running,
 * and only after the user opts in. It is NOT push/FCM and creates no service,
 * scheduled task, autostart entry, Windows update or background process.
 *
 * The previously displayed version is persisted BEFORE displaying a new
 * balloon, so a crash cannot generate repeated duplicate alerts. No download
 * link or executable is consumed from the Android release manifest.
 */
internal class DesktopWindowsNewsAutoCheck(
    private val preferences: DesktopWindowsNewsPreferences,
    private val fetchNews: suspend () -> DesktopOfficialReleaseNotice.Notice = {
        DesktopOfficialReleaseNotice().checkAndroidAnnouncements()
    },
    private val display: (String, String) -> Boolean = { title, message ->
        DesktopWindowsLocalNotification.showAfterUserConsent(title, message)
    }
) {
    enum class Outcome { DISABLED, UNCHANGED, NOTIFIED, SYSTEM_UNAVAILABLE }

    suspend fun checkOnce(): Outcome {
        val before = preferences.load()
        if (!before.enabled) return Outcome.DISABLED
        val notice = fetchNews()
        // A user might disable alerts during the network request; re-check
        // before writing any marker or displaying a native notification.
        val current = preferences.load()
        if (!current.enabled) return Outcome.DISABLED
        if (notice.versionCode <= current.lastNotifiedCode) return Outcome.UNCHANGED
        preferences.save(current.copy(lastNotifiedCode = notice.versionCode))
        return if (display(
            "Brother Matrizes — novidades Android",
            "Versão Android ${notice.versionName} publicada. " +
                "Brother Matrizes Windows continua em homologação."
        )) Outcome.NOTIFIED else Outcome.SYSTEM_UNAVAILABLE
    }

    /** An explicit enable action baselines current news; no old alert. */
    suspend fun setEnabled(enabled: Boolean): Boolean {
        val previous = preferences.load()
        if (!enabled) {
            preferences.save(previous.copy(enabled = false))
            return false
        }
        val latest = fetchNews()
        // Never reset lastNotifiedCode backwards on a stale manifest.
        val next = previous.copy(
            enabled = true,
            lastNotifiedCode = maxOf(previous.lastNotifiedCode, latest.versionCode)
        )
        preferences.save(next)
        return true
    }
}
