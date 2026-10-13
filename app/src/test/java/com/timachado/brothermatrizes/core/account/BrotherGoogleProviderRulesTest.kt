package com.timachado.brothermatrizes.core.account

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrotherGoogleProviderRulesTest {
    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    @Test fun recusaEmailEContaSemProvedor() {
        assertFalse(BrotherGoogleProviderRules.verified(null))
        assertFalse(BrotherGoogleProviderRules.verified(obj("{}")))
        assertFalse(BrotherGoogleProviderRules.verified(obj("{\"provider\":\"email\"}")))
        assertFalse(BrotherGoogleProviderRules.verified(obj("{\"providers\":[\"email\"]}")))
    }

    @Test fun aceitaApenasGoogleDeclaradoEmAppMetadata() {
        assertTrue(BrotherGoogleProviderRules.verified(obj("{\"provider\":\"google\"}")))
        assertTrue(BrotherGoogleProviderRules.verified(obj("{\"providers\":[\"email\",\"google\"]}")))
    }

    @Test fun naoConfundePerfilOuValorMalformadoComProvedorGoogle() {
        assertFalse(BrotherGoogleProviderRules.verified(obj("{\"full_name\":\"Google\",\"provider\":\"email\"}")))
        assertFalse(BrotherGoogleProviderRules.verified(obj("{\"provider\":{\"name\":\"google\"}}")))
        assertFalse(BrotherGoogleProviderRules.verified(obj("{\"providers\":\"google\"}")))
    }
}
