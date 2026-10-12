package com.timachado.brothermatrizes.core.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrotherCommunityPayloadRulesTest {
    private fun msg(id: String = "novidades_20261012") = mapOf(
        "type" to "community",
        "eventId" to id,
        "title" to "Novidades do Brother",
        "body" to "Confira os novos recursos do aplicativo."
    )

    @Test fun aceitaSomenteEventosIdentificadosDaComunidade() {
        assertTrue(BrotherCommunityPayloadRules.shouldPost(msg()))
        assertFalse(BrotherCommunityPayloadRules.shouldPost(msg().plus("type" to "update")))
        assertFalse(BrotherCommunityPayloadRules.shouldPost(msg("!") ))
        assertFalse(BrotherCommunityPayloadRules.shouldPost(msg("curto")))
        assertFalse(BrotherCommunityPayloadRules.shouldPost(msg().minus("eventId")))
    }

    @Test fun naoMostraMensagemVazia() {
        assertFalse(BrotherCommunityPayloadRules.shouldPost(msg().plus("title" to "  ")))
        assertFalse(BrotherCommunityPayloadRules.shouldPost(msg().plus("body" to "")))
    }

    @Test fun limitaTextosDeNotificacao() {
        assertEquals(90, BrotherCommunityPayloadRules.title(msg().plus("title" to "a".repeat(200))).length)
        assertEquals(240, BrotherCommunityPayloadRules.body(msg().plus("body" to "b".repeat(400))).length)
    }
}
