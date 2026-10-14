package com.timachado.brothermatrizes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrotherMatrizesAboutTest {
    @Test
    fun developerCreditUsesOfficialBranding() {
        assertEquals(
            "T.I. Machado",
            BrotherMatrizesAbout
                .developerName
        )

        assertTrue(
            BrotherMatrizesAbout
                .developerCredit
                .contains(
                    "Soluções em Tecnologia"
                )
        )
    }

    @Test
    fun developerWebsiteUsesHttps() {
        assertTrue(
            BrotherMatrizesAbout
                .developerWebsite
                .startsWith(
                    "https://"
                )
        )
    }
}
