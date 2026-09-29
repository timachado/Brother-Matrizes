package com.timachado.brothermatrizes.core.network

import com.timachado.brothermatrizes.core.storage.SafeInputReader
import java.net.HttpURLConnection
import java.net.URL

object SafeRemoteImage {
    private const val MAX_BYTES =
        4 * 1024 * 1024

    private const val TIMEOUT_MS =
        5_000

    fun load(
        url: String
    ): Result<ByteArray> =
        runCatching {
            require(
                url.startsWith(
                    "https://"
                )
            ) {
                "Imagem remota sem HTTPS."
            }

            val connection =
                (
                    URL(
                        url
                    ).openConnection() as
                        HttpURLConnection
                    ).apply {
                    connectTimeout =
                        TIMEOUT_MS

                    readTimeout =
                        TIMEOUT_MS

                    instanceFollowRedirects =
                        true

                    useCaches =
                        true
                }

            try {
                connection.connect()

                require(
                    connection.responseCode in
                        200..299
                ) {
                    "Não foi possível carregar a imagem."
                }

                val declared =
                    connection.contentLengthLong

                require(
                    declared <=
                        0L ||
                        declared <=
                            MAX_BYTES
                ) {
                    "Imagem remota grande demais."
                }

                connection
                    .inputStream
                    .buffered()
                    .use {
                        SafeInputReader
                            .readBytes(
                                input =
                                    it,
                                maxBytes =
                                    MAX_BYTES
                            )
                    }
            } finally {
                connection.disconnect()
            }
        }
}
