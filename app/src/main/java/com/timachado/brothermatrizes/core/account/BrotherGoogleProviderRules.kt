package com.timachado.brothermatrizes.core.account

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Verifica somente app_metadata da autenticação, nunca user_metadata
 * (que pode ser editado pelo próprio usuário).
 */
internal object BrotherGoogleProviderRules {
    fun verified(metadata: JsonObject?): Boolean {
        if (metadata == null) return false
        val provider = runCatching {
            metadata["provider"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        if (provider == "google") return true
        val providers = metadata["providers"] as? JsonArray ?: return false
        return providers.any {
            runCatching { it.jsonPrimitive.contentOrNull == "google" }.getOrDefault(false)
        }
    }
}
