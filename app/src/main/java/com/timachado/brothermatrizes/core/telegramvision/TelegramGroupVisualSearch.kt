package com.timachado.brothermatrizes.core.telegramvision

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Grupos que pertencem à sessão Telegram aberta e autorizada pelo usuário. */
data class TelegramGroup(
    val id: Long,
    val title: String,
    val isMember: Boolean,
    val isSecret: Boolean = false
)

/**
 * Somente metadados e miniatura calculada em memória.
 * O provedor precisa excluir mensagens temporárias, protegidas e sem direito de acesso.
 * A miniatura nunca é persistida em banco de dados nem enviada a um servidor.
 */
data class TelegramPhotoCandidate(
    val chatId: Long,
    val messageId: Long,
    val title: String,
    val publicPostUrl: String?,
    val grayscale9x8: IntArray,
    /** TDLib's app-private cached preview. Never a remote URL or upload. */
    val localPreviewPath: String? = null
)

data class TelegramPhotoHit(
    val chatId: Long,
    val messageId: Long,
    val groupTitle: String,
    val publicPostUrl: String?,
    val localPreviewPath: String? = null,
    /** Similaridade heurística, não probabilidade de ser a mesma matriz. */
    val similarity: Float
)

/**
 * Contrato para o cliente oficial TDLib (a ser conectado após autenticação).
 * A conta Google do Brother Matrizes não autoriza o acesso aos grupos Telegram.
 */
interface TelegramGroupMediaSource {
    suspend fun authorizedGroups(): List<TelegramGroup>
    suspend fun recentPhotoThumbnails(groupId: Long, maxCount: Int): List<TelegramPhotoCandidate>
}

object EphemeralImageFingerprint {
    fun fromGray9x8(gray: IntArray): Long {
        require(gray.size == 72 && gray.all { it in 0..255 }) { "Miniatura 9x8 inválida." }
        var fingerprint = 0L
        for (row in 0..7) {
            for (column in 0..7) {
                if (gray[row * 9 + column] > gray[row * 9 + column + 1]) {
                    fingerprint = fingerprint or (1L shl (row * 8 + column))
                }
            }
        }
        return fingerprint
    }

    fun hasDetail(gray: IntArray): Boolean {
        if (gray.size != 72 || gray.any { it !in 0..255 }) return false
        if ((gray.maxOrNull() ?: 0) - (gray.minOrNull() ?: 0) < 30) return false
        return (0..7).sumOf { row ->
            (0..7).count { col ->
                kotlin.math.abs(gray[row * 9 + col] - gray[row * 9 + col + 1]) > 14
            }
        } >= 6
    }

    fun similarity(a: Long, b: Long): Float =
        (64 - java.lang.Long.bitCount(a xor b)) / 64.0f
}

/**
 * Pesquisa pontual, não indexação permanente e não IA. Executada APENAS após
 * ação afirmativa do usuário e sobre grupos visíveis à sua sessão.
 */
class TelegramGroupVisualSearch(
    private val media: TelegramGroupMediaSource
) {
    companion object {
        const val MAX_GROUPS = 10
        const val MAX_PHOTOS_PER_GROUP = 25
        const val MAX_RESULTS = 8
        const val MIN_SIMILARITY = 0.80f
    }

    suspend fun search(
        userAcceptedThisScan: Boolean,
        selectedGroupIds: Set<Long>,
        selectedPhotoGray9x8: IntArray
    ): List<TelegramPhotoHit> {
        require(userAcceptedThisScan) { "Confirme a pesquisa nos grupos selecionados." }
        require(selectedGroupIds.isNotEmpty() && selectedGroupIds.size <= MAX_GROUPS) {
            "Selecione de 1 a $MAX_GROUPS grupos."
        }
        require(EphemeralImageFingerprint.hasDetail(selectedPhotoGray9x8)) {
            "A foto não possui detalhes suficientes para comparar com segurança."
        }
        val allowed = media.authorizedGroups()
            .filter { it.isMember && !it.isSecret }
            .associateBy { it.id }
        require(selectedGroupIds.all { it in allowed }) {
            "Um dos grupos não pertence à sessão Telegram autorizada."
        }
        val wanted = EphemeralImageFingerprint.fromGray9x8(selectedPhotoGray9x8)
        val hits = ArrayList<TelegramPhotoHit>()
        for (groupId in selectedGroupIds) {
            coroutineContext.ensureActive()
            val title = allowed.getValue(groupId).title
            val candidates = media.recentPhotoThumbnails(groupId, MAX_PHOTOS_PER_GROUP)
                .take(MAX_PHOTOS_PER_GROUP)
            for (candidate in candidates) {
                coroutineContext.ensureActive()
                if (candidate.chatId != groupId ||
                    candidate.messageId <= 0 ||
                    !EphemeralImageFingerprint.hasDetail(candidate.grayscale9x8)
                ) continue
                val score = EphemeralImageFingerprint.similarity(
                    wanted, EphemeralImageFingerprint.fromGray9x8(candidate.grayscale9x8)
                )
                if (score >= MIN_SIMILARITY) {
                    hits.add(
                        TelegramPhotoHit(
                            chatId = groupId,
                            messageId = candidate.messageId,
                            groupTitle = title,
                            publicPostUrl = candidate.publicPostUrl
                                ?.takeIf { isSafeTelegramPostUrl(it) },
                            similarity = score,
                            localPreviewPath = candidate.localPreviewPath
                        )
                    )
                }
            }
        }
        return hits.sortedWith(
            compareByDescending<TelegramPhotoHit> { it.similarity }
                .thenBy { it.groupTitle }
                .thenBy { it.messageId }
        ).distinctBy { it.chatId to it.messageId }.take(MAX_RESULTS)
    }

    /** Never manufacture a permalink for a private group. */
    private fun isSafeTelegramPostUrl(input: String): Boolean =
        input.matches(Regex("https://t\\.me/[A-Za-z][A-Za-z0-9_]{4,31}/[1-9][0-9]*")) ||
            input.matches(Regex("https://t\\.me/c/[1-9][0-9]*/[1-9][0-9]*"))
}
