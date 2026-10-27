package com.timachado.brothermatrizes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameQaImportPolicyTest {
    @Test fun localImportWithoutAccountOnlyInDedicatedDebugPackage() {
        assertTrue(
            NameQaImportPolicy.allowLocalImportWithoutAccount(
                debugBuild = true,
                applicationId = "com.timachado.brothermatrizes.nameqa"
            )
        )
    }

    @Test fun neverSkipAuthInCommercialOrAnyRelease() {
        for (id in listOf(
            "com.timachado.brothermatrizes",
            "com.timachado.brothermatrizes.debug",
            "com.timachado.brothermatrizes.nameqa.fake",
            "com.example.app"
        )) {
            assertFalse(
                NameQaImportPolicy.allowLocalImportWithoutAccount(
                    debugBuild = true, applicationId = id
                )
            )
        }
        assertFalse(
            NameQaImportPolicy.allowLocalImportWithoutAccount(
                debugBuild = false,
                applicationId = "com.timachado.brothermatrizes.nameqa"
            )
        )
    }
}
