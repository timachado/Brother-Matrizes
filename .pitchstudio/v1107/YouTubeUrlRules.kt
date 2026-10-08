package br.com.timachado.pitchstudio

import java.net.URI

/**
 * Validação isolada de URL do YouTube, testável na JVM sem Android.
 * Aceita somente domínio oficial e HTTPS. Não faz requisições.
 */
internal object YouTubeUrlRules {
    private val hosts = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com",
        "music.youtube.com", "youtu.be", "www.youtu.be"
    )
    private val videoId = Regex("[A-Za-z0-9_-]{11}")

    private fun parse(raw: String): URI? = try {
        val value = raw.trim()
        if (value.isEmpty() || value.any { it.isWhitespace() }) null
        else URI(if ("://" in value) value else "https://$value")
    } catch (_: Exception) { null }

    fun isYouTubeUrl(raw: String): Boolean {
        val uri = parse(raw) ?: return false
        return uri.scheme.equals("https", true) &&
            uri.host?.lowercase() in hosts &&
            uri.rawUserInfo == null && uri.port == -1
    }

    fun normalize(raw: String): String {
        val uri = parse(raw) ?: throw IllegalArgumentException("O endereço do YouTube é inválido.")
        require(isYouTubeUrl(raw)) { "O link deve usar HTTPS e domínio oficial do YouTube." }
        val host = uri.host!!.lowercase()
        val segments = uri.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
        val id = when {
            host == "youtu.be" || host == "www.youtu.be" ->
                segments.singleOrNull()

            segments.firstOrNull() == "watch" && segments.size == 1 ->
                uri.rawQuery?.split('&')?.mapNotNull { part ->
                    val sep = part.indexOf('=')
                    if (sep > 0 && part.substring(0,sep) == "v") part.substring(sep+1) else null
                }?.singleOrNull()

            segments.firstOrNull() in setOf("shorts","live","embed") &&
                segments.size == 2 -> segments[1]

            else -> null
        }?.takeIf { videoId.matches(it) }
            ?: throw IllegalArgumentException("Não reconheci o identificador deste vídeo do YouTube.")

        return "https://www.youtube.com/watch?v=$id"
    }
}
