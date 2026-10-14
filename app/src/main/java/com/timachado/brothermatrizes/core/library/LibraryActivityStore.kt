package com.timachado.brothermatrizes.core.library

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class SentMatrixRecord(
    val id: String,
    val fileName: String,
    val format: String,
    val method: String,
    val sentAtMillis: Long
)

@Serializable
private data class RecentProjectRecord(
    val projectId: String,
    val openedAtMillis: Long
)

@Serializable
private data class LibraryActivityState(
    val favoriteProjectIds:
        Set<String> =
        emptySet(),
    val recentProjects:
        List<RecentProjectRecord> =
        emptyList(),
    val sentMatrices:
        List<SentMatrixRecord> =
        emptyList()
)

object LibraryActivityStore {
    private const val PREFS =
        "brother_matrizes_library_activity"

    private const val KEY_STATE =
        "state_v1"

    private const val MAX_RECENT =
        30

    private const val MAX_SENT =
        50

    private val json =
        Json {
            ignoreUnknownKeys =
                true
            encodeDefaults =
                true
        }

    fun favoriteIds(
        context: Context
    ): Set<String> =
        read(
            context
        ).favoriteProjectIds

    fun toggleFavorite(
        context: Context,
        projectId: String
    ): Boolean {
        val current =
            read(
                context
            )

        val favorites =
            current
                .favoriteProjectIds
                .toMutableSet()

        val nowFavorite =
            if (
                projectId in
                    favorites
            ) {
                favorites.remove(
                    projectId
                )
                false
            } else {
                favorites.add(
                    projectId
                )
                true
            }

        write(
            context,
            current.copy(
                favoriteProjectIds =
                    favorites
            )
        )

        return nowFavorite
    }

    fun markRecent(
        context: Context,
        projectId: String
    ) {
        val current =
            read(
                context
            )

        val recent =
            listOf(
                RecentProjectRecord(
                    projectId =
                        projectId,
                    openedAtMillis =
                        System
                            .currentTimeMillis()
                )
            ) +
                current
                    .recentProjects
                    .filter {
                        it.projectId !=
                            projectId
                    }

        write(
            context,
            current.copy(
                recentProjects =
                    recent.take(
                        MAX_RECENT
                    )
            )
        )
    }

    fun recentIds(
        context: Context
    ): List<String> =
        read(
            context
        ).recentProjects
            .sortedByDescending {
                it.openedAtMillis
            }
            .map {
                it.projectId
            }

    fun sentMatrices(
        context: Context
    ): List<SentMatrixRecord> =
        read(
            context
        ).sentMatrices
            .sortedByDescending {
                it.sentAtMillis
            }

    fun recordSent(
        context: Context,
        fileName: String,
        format: String,
        method: String
    ) {
        val current =
            read(
                context
            )

        val record =
            SentMatrixRecord(
                id =
                    UUID
                        .randomUUID()
                        .toString(),
                fileName =
                    fileName,
                format =
                    format
                        .uppercase(),
                method =
                    method,
                sentAtMillis =
                    System
                        .currentTimeMillis()
            )

        write(
            context,
            current.copy(
                sentMatrices =
                    (
                        listOf(
                            record
                        ) +
                            current
                                .sentMatrices
                        ).take(
                        MAX_SENT
                    )
            )
        )
    }

    fun removeSent(
        context: Context,
        recordId: String
    ) {
        val current =
            read(
                context
            )

        write(
            context,
            current.copy(
                sentMatrices =
                    current
                        .sentMatrices
                        .filterNot {
                            it.id ==
                                recordId
                        }
            )
        )
    }

    fun clearSent(
        context: Context
    ) {
        val current =
            read(
                context
            )

        if (
            current.sentMatrices
                .isEmpty()
        ) {
            return
        }

        write(
            context,
            current.copy(
                sentMatrices =
                    emptyList()
            )
        )
    }

    fun removeProjectMetadata(
        context: Context,
        projectId: String
    ) {
        val current =
            read(
                context
            )

        write(
            context,
            current.copy(
                favoriteProjectIds =
                    current
                        .favoriteProjectIds
                        .filterNot {
                            it ==
                                projectId
                        }
                        .toSet(),
                recentProjects =
                    current
                        .recentProjects
                        .filterNot {
                            it.projectId ==
                                projectId
                        }
            )
        )
    }

    private fun read(
        context: Context
    ): LibraryActivityState {
        val raw =
            context
                .getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
                .getString(
                    KEY_STATE,
                    null
                )
                ?: return LibraryActivityState()

        return runCatching {
            json.decodeFromString<
                LibraryActivityState
            >(
                raw
            )
        }.getOrElse {
            LibraryActivityState()
        }
    }

    private fun write(
        context: Context,
        state: LibraryActivityState
    ) {
        val raw =
            json.encodeToString(
                state
            )

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_STATE,
                raw
            )
            .apply()
    }
}
