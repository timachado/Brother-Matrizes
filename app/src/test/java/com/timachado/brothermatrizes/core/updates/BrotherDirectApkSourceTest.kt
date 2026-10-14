package com.timachado.brothermatrizes.core.updates

import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrotherDirectApkSourceTest {
    @Test fun permitsOfficialSiteStaticApk() {
        val valid = "https://timachado.ifree.page/downloads/brother/Brother-Matrizes-v1.10.1.apk"
        assertEquals(valid, BrotherDirectApkSource.binaryFromManifest(valid).toString())
    }

    @Test fun permitsOnlyExactGithubReleasePath() {
        val valid = "https://github.com/timachado/Brother-Matrizes/releases/download/v1.10.1/Brother-Matrizes-v1.10.1.apk"
        assertEquals(valid, BrotherDirectApkSource.binaryFromManifest(valid).toString())
        assertNull(BrotherDirectApkSource.binaryFromManifest(
            "https://github.com/not-the-owner/Brother-Matrizes/releases/download/v1.10.1/app.apk"
        ))
        assertNull(BrotherDirectApkSource.binaryFromManifest(
            "https://github.com/timachado/Brother-Matrizes/releases/download/v1.10.1/app.exe"
        ))
    }

    @Test fun rejectsDangerousOrigins() {
        listOf(
            "http://timachado.ifree.page/Brother.apk",
            "https://timachado.ifree.page.malicious.example/Brother.apk",
            "https://malicious.example/Brother.apk",
            "https://attacker@github.com/timachado/Brother-Matrizes/releases/download/v1.10.1/app.apk",
            "file:///storage/emulated/0/Download/Brother.apk",
            "https://release-assets.githubusercontent.com/user-content.apk"
        ).forEach { assertNull(BrotherDirectApkSource.binaryFromManifest(it)) }
    }

    @Test fun githubRedirectCannotGoToAnotherWebsite() {
        val base = URL("https://github.com/timachado/Brother-Matrizes/releases/download/v1.10.1/app.apk")
        assertTrue(BrotherDirectApkSource.allowedRedirect(
            base, URL("https://release-assets.githubusercontent.com/secure-object?token=123")
        ))
        assertFalse(BrotherDirectApkSource.allowedRedirect(
            base, URL("https://fake.example/app.apk")
        ))
    }
}
