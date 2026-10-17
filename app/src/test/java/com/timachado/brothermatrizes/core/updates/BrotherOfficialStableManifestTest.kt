package com.timachado.brothermatrizes.core.updates

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Valida o contrato OTA de qualquer versao COMERCIAL publicada, sem congelar
 * o APK 1.10.8 e sem modificar direitos de acesso ou fluxo de assinatura.
 */
class BrotherOfficialStableManifestTest {
    private fun manifest(): Map<String, String> {
        val file = listOf(
            File("updates/stable.json"),
            File("../updates/stable.json"),
            File("../../updates/stable.json")
        ).firstOrNull { it.isFile }
            ?: error("O manifesto oficial updates/stable.json nao existe")
        return Json.parseToJsonElement(file.readText()).jsonObject.mapValues { (_, value) ->
            value.jsonPrimitive.content
        }
    }

    @Test fun manifestoApontaSempreParaApkComercialDiretoNoGithub() {
        val release = manifest()
        assertEquals("com.timachado.brothermatrizes", release["applicationId"])
        assertEquals("stable", release["channel"])
        val versionName = release["versionName"].orEmpty()
        val code = release["versionCode"]?.toLongOrNull() ?: 0L
        val min = release["minSupportedVersionCode"]?.toLongOrNull() ?: -1L
        assertTrue(Regex("[0-9]+(?:\\.[0-9]+){2}").matches(versionName))
        assertTrue(code >= 184L)
        assertTrue(min >= 0L && min <= code)
        val url = release["downloadUrl"].orEmpty()
        val direct = release["downloadApkUrl"].orEmpty()
        assertEquals(url, direct)
        assertTrue(url.startsWith(
            "https://github.com/timachado/Brother-Matrizes/releases/download/v$versionName/"
        ))
        assertTrue(url.endsWith(".apk"))
        assertEquals(url, BrotherDirectApkSource.binaryFromManifest(direct)?.toString())
        assertTrue(Regex("[0-9a-f]{64}").matches(release["sha256"].orEmpty()))
        assertFalse(UpdateVersionRules.isUpdateAvailable(code, code))
        assertTrue(UpdateVersionRules.isUpdateAvailable(code - 1L, code))
    }
}
