package br.com.timachado.pitchstudio

import org.junit.Assert.*
import org.junit.Test

class YouTubeUrlRulesTest {
    private val id = "dQw4w9WgXcQ"
    private val canonical = "https://www.youtube.com/watch?v=$id"

    @Test fun acceptsCanonicalVideoLinks() {
        for (url in listOf(
            "https://youtu.be/$id",
            "youtu.be/$id?t=42",
            "https://www.youtube.com/watch?v=$id&t=55",
            "https://m.youtube.com/watch?v=$id",
            "https://music.youtube.com/watch?v=$id",
            "https://youtube.com/shorts/$id",
            "https://youtube.com/live/$id",
            "https://www.youtube.com/embed/$id"
        )) {
            assertTrue("Deve reconhecer: $url", YouTubeUrlRules.isYouTubeUrl(url))
            assertEquals("Normalização incorreta para $url", canonical,
                YouTubeUrlRules.normalize(url))
        }
    }

    @Test fun rejectsSpoofedHostsAndInsecureSchemes() {
        for (url in listOf(
            "https://youtube.com.evil.example/watch?v=$id",
            "https://evil.example/youtube.com/watch?v=$id",
            "https://youtube.com@evil.example/watch?v=$id",
            "http://www.youtube.com/watch?v=$id",
            "https://www.youtube.com:444/watch?v=$id",
            "javascript://youtube.com/watch?v=$id",
            "file://youtube.com/watch?v=$id"
        )) {
            assertFalse("Host ou esquema inseguro: $url", YouTubeUrlRules.isYouTubeUrl(url))
            try {
                YouTubeUrlRules.normalize(url)
                fail("A URL insegura foi aceita: $url")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun rejectsInvalidIdsAndAmbiguousQuery() {
        for (url in listOf(
            "https://youtube.com/watch?v=123",
            "https://youtu.be/12345678901/extra",
            "https://youtube.com/watch?v=$id&v=AbCdEfGhI12",
            "https://youtube.com/playlist?list=$id",
            "https://youtube.com/shorts/$id/extra"
        )) {
            try {
                YouTubeUrlRules.normalize(url)
                fail("Deveria rejeitar: $url")
            } catch (_: IllegalArgumentException) { }
        }
    }
}
