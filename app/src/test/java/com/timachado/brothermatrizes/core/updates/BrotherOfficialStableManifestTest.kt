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
 * A checagem no app usa updates/stable.json, antes do WordPress.
 * Este teste impede que o arquivo ou dados oficiais sumam da release comercial.
 */
class BrotherOfficialStableManifestTest {
    private fun manifest(): Map<String, String> {
        val candidates = listOf(
            File("updates/stable.json"),
            File("../updates/stable.json"),
            File("../../updates/stable.json")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Manifesto oficial updates/stable.json não existe no repositório")
        return Json.parseToJsonElement(file.readText()).jsonObject.mapValues { (_, element) ->
            element.jsonPrimitive.content
        }
    }

    @Test fun manifestoDaVersaoComercialEhValido() {
        val release = manifest()
        assertEquals("com.timachado.brothermatrizes", release["applicationId"])
        assertEquals("stable", release["channel"])
        assertEquals("1.10.8", release["versionName"])
        assertEquals("184", release["versionCode"])
        assertEquals("0", release["minSupportedVersionCode"])
        assertEquals(
            "https://github.com/timachado/Brother-Matrizes/releases/download/v1.10.8/" +
                "Brother-Matrizes-1.10.8-COTAS-NOVIDADES-HOMOLOGACAO.apk",
            release["downloadUrl"]
        )
        assertEquals(
            "67126397ee416386d2991214208a637165e73401aa6c71693495f10d62638819",
            release["sha256"]
        )
        assertTrue(Regex("[a-f0-9]{64}").matches(release["sha256"].orEmpty()))
        assertFalse(UpdateVersionRules.isUpdateAvailable(184L, release["versionCode"]!!.toLong()))
        assertTrue(UpdateVersionRules.isUpdateAvailable(183L, release["versionCode"]!!.toLong()))
    }
}
