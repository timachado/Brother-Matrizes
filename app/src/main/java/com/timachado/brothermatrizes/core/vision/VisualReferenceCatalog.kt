package com.timachado.brothermatrizes.core.vision

import android.content.Context
import com.timachado.brothermatrizes.core.project.SavedProjectSummary

data class VisualReferenceHit(
    val project: SavedProjectSummary,
    val similarity: Float
)

/**
 * Índice local e opt-in, limitado a 1 foto de referência por projeto.
 * Armazena somente id do projeto + dHash de 64 bits; não salva fotos,
 * não solicita permissões novas e não envia dados ao Telegram.
 */
object VisualReferenceCatalog {
    private const val PREFS = "brother_vision_references_v1"
    private val idPattern = Regex("[a-f0-9]{24}")
    private const val MIN_SIMILARITY = 0.72f

    fun save(context: Context, projectId: String, fingerprint: Long) {
        require(idPattern.matches(projectId)) { "Identificador do projeto inválido." }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(projectId, java.lang.Long.toUnsignedString(fingerprint, 16)).apply()
    }

    fun delete(context: Context, projectId: String) {
        require(idPattern.matches(projectId)) { "Identificador do projeto inválido." }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(projectId).apply()
    }

    fun list(context: Context, projects: List<SavedProjectSummary>): List<VisualReferenceHit> =
        projects.mapNotNull { project ->
            val fingerprint = read(context, project.id) ?: return@mapNotNull null
            VisualReferenceHit(project, fingerprint.toFloat())
        }

    fun registeredProjects(context: Context, projects: List<SavedProjectSummary>): List<SavedProjectSummary> =
        projects.filter { read(context, it.id) != null }

    fun findMatches(
        context: Context,
        projects: List<SavedProjectSummary>,
        fingerprint: Long
    ): List<VisualReferenceHit> =
        projects.mapNotNull { project ->
            val reference = read(context, project.id) ?: return@mapNotNull null
            val similarity = VisualFingerprint.similarity(fingerprint, reference)
            if (similarity >= MIN_SIMILARITY) VisualReferenceHit(project, similarity)
            else null
        }.sortedWith(
            compareByDescending<VisualReferenceHit> { it.similarity }
                .thenBy { it.project.title }
        ).take(5)

    private fun read(context: Context, projectId: String): Long? {
        if (!idPattern.matches(projectId)) return null
        val hex = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(projectId, null) ?: return null
        return runCatching { java.lang.Long.parseUnsignedLong(hex, 16) }.getOrNull()
    }
}
