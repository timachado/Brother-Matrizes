package com.timachado.brothermatrizes.core.update

import com.timachado.brothermatrizes.BuildConfig
import com.timachado.brothermatrizes.core.storage.SafeInputReader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val updateAvailable: Boolean,
    val releaseUrl: String
)

object AppUpdateChecker {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/timachado/Brother Matrizes/releases/latest"

    private const val RELEASE_PAGE_PREFIX =
        "https://github.com/timachado/Brother Matrizes/releases/"

    private const val TIMEOUT_MS =
        6_000

    private const val MAX_RESPONSE_BYTES =
        256 * 1024

    fun check(): Result<AppUpdateInfo> =
        runCatching {
            val connection =
                (
                    URL(
                        LATEST_RELEASE_URL
                    ).openConnection() as
                        HttpURLConnection
                    ).apply {
                    requestMethod =
                        "GET"
                    connectTimeout =
                        TIMEOUT_MS
                    readTimeout =
                        TIMEOUT_MS
                    instanceFollowRedirects =
                        false
                    useCaches =
                        false
                    setRequestProperty(
                        "Accept",
                        "application/vnd.github+json"
                    )
                    setRequestProperty(
                        "User-Agent",
                        "Brother Matrizes/" +
                            BuildConfig.VERSION_NAME
                    )
                }

            try {
                connection.connect()

                require(
                    connection.responseCode in
                        200..299
                ) {
                    "Não foi possível consultar a versão mais recente."
                }

                val declared =
                    connection.contentLengthLong

                require(
                    declared <= 0L ||
                        declared <= MAX_RESPONSE_BYTES
                ) {
                    "Resposta de atualização grande demais."
                }

                val bytes =
                    connection
                        .inputStream
                        .buffered()
                        .use {
                            SafeInputReader
                                .readBytes(
                                    input =
                                        it,
                                    maxBytes =
                                        MAX_RESPONSE_BYTES
                                )
                        }

                val root =
                    Json
                        .parseToJsonElement(
                            bytes.toString(
                                Charsets.UTF_8
                            )
                        )
                        .jsonObject

                val tag =
                    root[
                        "tag_name"
                    ]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.trim()
                        ?.removePrefix(
                            "v"
                        )
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: error(
                            "Release sem versão válida."
                        )

                val releaseUrl =
                    root[
                        "html_url"
                    ]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.trim()
                        ?.takeIf {
                            it.startsWith(
                                RELEASE_PAGE_PREFIX
                            )
                        }
                        ?: error(
                            "Release sem endereço seguro."
                        )

                AppUpdateInfo(
                    currentVersion =
                        BuildConfig.VERSION_NAME,
                    latestVersion =
                        tag,
                    updateAvailable =
                        isNewer(
                            candidate =
                                tag,
                            current =
                                BuildConfig.VERSION_NAME
                        ),
                    releaseUrl =
                        releaseUrl
                )
            } finally {
                connection.disconnect()
            }
        }

    fun isNewer(
        candidate: String,
        current: String
    ): Boolean {
        val candidateParts =
            numericParts(
                candidate
            )

        val currentParts =
            numericParts(
                current
            )

        val count =
            maxOf(
                candidateParts.size,
                currentParts.size
            )

        for (
            index in
                0 until count
        ) {
            val candidatePart =
                candidateParts
                    .getOrElse(
                        index
                    ) {
                        0
                    }

            val currentPart =
                currentParts
                    .getOrElse(
                        index
                    ) {
                        0
                    }

            if (
                candidatePart !=
                    currentPart
            ) {
                return candidatePart >
                    currentPart
            }
        }

        return false
    }

    private fun numericParts(
        version: String
    ): List<Int> =
        version
            .trim()
            .removePrefix(
                "v"
            )
            .substringBefore(
                '-'
            )
            .split(
                '.'
            )
            .map {
                part ->
                part
                    .toIntOrNull()
                    ?: 0
            }
}
