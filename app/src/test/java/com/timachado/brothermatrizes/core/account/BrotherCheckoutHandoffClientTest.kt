package com.timachado.brothermatrizes.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BrotherCheckoutHandoffClientTest {
    private val ticket = "A".repeat(125)

    @Test fun onlyAcceptsExactOfficialCheckoutHostAndOpaqueTicket() {
        val valid = "https://timachado.ifree.page/minha-conta/?bm_checkout=$ticket"
        assertEquals(valid, BrotherCheckoutHandoffClient.validateCheckoutUrl(valid))
    }

    @Test fun rejectsOpenRedirectsOAuthTokensAndHostSpoofing() {
        val invalid = listOf(
            "https://evil.example/minha-conta/?bm_checkout=$ticket",
            "https://timachado.ifree.page.evil.example/minha-conta/?bm_checkout=$ticket",
            "http://timachado.ifree.page/minha-conta/?bm_checkout=$ticket",
            "https://timachado.ifree.page:8443/minha-conta/?bm_checkout=$ticket",
            "https://user@timachado.ifree.page/minha-conta/?bm_checkout=$ticket",
            "https://timachado.ifree.page/checkout/?bm_checkout=$ticket",
            "https://timachado.ifree.page/minha-conta/?access_token=$ticket",
            "https://timachado.ifree.page/minha-conta/?bm_checkout=$ticket&return=https://evil.example",
            "https://timachado.ifree.page/minha-conta/?bm_checkout=$ticket#access_token",
            "https://timachado.ifree.page/minha-conta/?bm_checkout=short"
        )
        invalid.forEach { address ->
            assertThrows(IllegalArgumentException::class.java) {
                BrotherCheckoutHandoffClient.validateCheckoutUrl(address)
            }
        }
    }
}
