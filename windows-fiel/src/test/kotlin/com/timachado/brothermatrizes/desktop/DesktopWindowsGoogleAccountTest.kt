package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsGoogleAccount
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsGooglePublicClient
import com.timachado.brothermatrizes.desktop.platform.DesktopExternalBrowser
import java.nio.file.Files
import java.util.Comparator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Windows-only offline QA; no client account, Google or payment API is used. */
class DesktopWindowsGoogleAccountTest {
    @Test
    fun normalWindowsLoginOpensOfflineAndRequiresUserAction() {
        val key = DesktopWindowsGooglePublicClient.key()
        assertTrue(key.startsWith("sb_publishable_"))
        assertFalse(key.startsWith("sb_secret_"))
        val directory = Files.createTempDirectory("brother-google-main-")
        var networkUsed = false
        try {
            DesktopWindowsGoogleAccount.open(
                publishableKey = key,
                folder = directory,
                browser = DesktopExternalBrowser {
                    networkUsed = true
                    error("Browser must never open at construction")
                },
                connectionFactory = {
                    networkUsed = true
                    error("HTTP must never run at startup")
                }
            ).use { account ->
                assertFalse(networkUsed)
                val restored = runBlocking { account.restoreAndPreview() }
                assertTrue(restored == null)
                assertFalse(networkUsed)
                assertFailsWith<IllegalStateException> {
                    runBlocking { account.verifiedToken() }
                }
                assertFalse(networkUsed)
                assertFalse(account.toString().contains(key))
            }
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    @Test
    fun normalLoginRejectsSecretKeysBeforeAnyNetworkCall() {
        val key = DesktopWindowsGooglePublicClient.key()
        assertFailsWith<IllegalArgumentException> {
            DesktopWindowsGoogleAccount.open(
                publishableKey = "sb_secret_" + key.substringAfter("sb_publishable_")
            )
        }
    }
}
