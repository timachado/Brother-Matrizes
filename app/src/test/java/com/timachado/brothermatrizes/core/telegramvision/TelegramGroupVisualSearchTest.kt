package com.timachado.brothermatrizes.core.telegramvision

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramGroupVisualSearchTest {
    private fun signature(reverse: Boolean = false) =
        IntArray(72) { i -> if ((i % 9) % 2 == (if (reverse) 1 else 0)) 230 else 35 }

    private class FakeSource(
        private val groups: List<TelegramGroup>,
        private val data: Map<Long, List<TelegramPhotoCandidate>>
    ) : TelegramGroupMediaSource {
        var scanned = mutableListOf<Long>()
        override suspend fun authorizedGroups() = groups
        override suspend fun recentPhotoThumbnails(groupId: Long, maxCount: Int):
            List<TelegramPhotoCandidate> {
            scanned.add(groupId)
            return data[groupId].orEmpty().take(maxCount)
        }
    }

    @Test
    fun onlyExplicitlySelectedMemberGroupCanBeScanned() {
        runBlocking {
        val g1 = TelegramGroup(10, "Bordados", true)
        val g2 = TelegramGroup(20, "Outro grupo", true)
        val source = FakeSource(
            listOf(g1, g2),
            mapOf(
                10L to listOf(
                    TelegramPhotoCandidate(10, 900, "Borboleta", "https://t.me/bordados/900", signature())
                ),
                20L to listOf(
                    TelegramPhotoCandidate(20, 901, "Outro", "https://t.me/bordados/901", signature())
                )
            )
        )
        val hits = TelegramGroupVisualSearch(source).search(true, setOf(10), signature())
        assertEquals(listOf(10L), source.scanned)
        assertEquals(1, hits.size)
        assertEquals(10L, hits.first().chatId)
        assertEquals(1f, hits.first().similarity, 0.0001f)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun noScanningWithoutConsent() {
        runBlocking {
        val source = FakeSource(listOf(TelegramGroup(10, "A", true)), emptyMap())
        TelegramGroupVisualSearch(source).search(false, setOf(10), signature())
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonMemberGroupIsRejected() {
        runBlocking {
        val source = FakeSource(listOf(TelegramGroup(10, "Privado", false)), emptyMap())
        TelegramGroupVisualSearch(source).search(true, setOf(10), signature())
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun secretChatIsRejected() {
        runBlocking {
        val source = FakeSource(listOf(TelegramGroup(10, "Segredo", true, true)), emptyMap())
        TelegramGroupVisualSearch(source).search(true, setOf(10), signature())
        }
    }

    @Test
    fun lowSimilarityDoesNotInventMatches() {
        runBlocking {
        val source = FakeSource(
            listOf(TelegramGroup(10, "Bordados", true)),
            mapOf(10L to listOf(
                TelegramPhotoCandidate(10, 901, "Teste", null, signature(reverse = true))
            ))
        )
        val hits = TelegramGroupVisualSearch(source).search(true, setOf(10), signature())
        assertTrue(hits.isEmpty())
        }
    }

    @Test
    fun unsafePermalinkIsNotPresented() {
        runBlocking {
        val source = FakeSource(
            listOf(TelegramGroup(10, "Bordados", true)),
            mapOf(10L to listOf(TelegramPhotoCandidate(
                10, 901, "Teste", "https://example.org/phishing", signature()
            )))
        )
        assertEquals(null, TelegramGroupVisualSearch(source)
            .search(true, setOf(10), signature()).first().publicPostUrl)
        }
    }

    @Test
    fun blankPhotoIsRejected() {
        runBlocking {
        val source = FakeSource(listOf(TelegramGroup(10, "A", true)), emptyMap())
        runCatching {
            TelegramGroupVisualSearch(source).search(true, setOf(10), IntArray(72) { 255 })
        }.onSuccess { throw AssertionError("Imagem sem detalhes não deve ser aceita") }
        }
    }
}
