package com.timachado.brothermatrizes.core.updates

/** Somente eventos oficiais de comunidade, com identificador estável e conteúdo limitado. */
internal object BrotherCommunityPayloadRules {
    private val eventIdPattern = Regex("[A-Za-z0-9_-]{8,64}")

    fun eventId(data: Map<String, String>): String? =
        if (data["type"] == "community") {
            data["eventId"]?.takeIf { eventIdPattern.matches(it) }
        } else null

    fun shouldPost(data: Map<String, String>): Boolean =
        eventId(data) != null &&
            !data["title"].isNullOrBlank() &&
            !data["body"].isNullOrBlank()

    fun title(data: Map<String, String>): String = data["title"].orEmpty().trim().take(90)
    fun body(data: Map<String, String>): String = data["body"].orEmpty().trim().take(240)
}
