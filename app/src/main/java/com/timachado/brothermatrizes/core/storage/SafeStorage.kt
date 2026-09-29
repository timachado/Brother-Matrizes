package com.timachado.brothermatrizes.core.storage

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object AtomicFileWriter {
    fun write(
        target: File,
        bytes: ByteArray
    ) {
        write(
            target
        ) {
            it.write(
                bytes
            )
        }
    }

    fun write(
        target: File,
        producer:
            (OutputStream) -> Unit
    ) {
        val parent =
            target.parentFile
                ?: error(
                    "Destino sem diretório pai."
                )

        if (
            !parent.exists()
        ) {
            check(
                parent.mkdirs()
            ) {
                "Não foi possível criar o diretório de armazenamento."
            }
        }

        val temporary =
            File(
                parent,
                target.name +
                    ".tmp"
            )

        runCatching {
            FileOutputStream(
                temporary
            ).use {
                    output ->
                producer(
                    output
                )

                output.flush()
                output.fd.sync()
            }

            runCatching {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption
                        .REPLACE_EXISTING,
                    StandardCopyOption
                        .ATOMIC_MOVE
                )
            }.getOrElse {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption
                        .REPLACE_EXISTING
                )
            }
        }.onFailure {
            temporary.delete()
        }.getOrThrow()
    }
}

object SafeInputReader {
    fun readBytes(
        input: InputStream,
        maxBytes: Int
    ): ByteArray {
        require(
            maxBytes >
                0
        ) {
            "Limite de leitura inválido."
        }

        val output =
            ByteArrayOutputStream(
                minOf(
                    16 * 1024,
                    maxBytes
                )
            )

        val buffer =
            ByteArray(
                16 * 1024
            )

        var total =
            0

        while (
            true
        ) {
            val read =
                input.read(
                    buffer
                )

            if (
                read <
                    0
            ) {
                break
            }

            total +=
                read

            require(
                total <=
                    maxBytes
            ) {
                "Arquivo excede o limite seguro."
            }

            output.write(
                buffer,
                0,
                read
            )
        }

        return output
            .toByteArray()
    }
}
