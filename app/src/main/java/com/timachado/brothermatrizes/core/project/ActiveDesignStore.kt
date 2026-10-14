package com.timachado.brothermatrizes.core.project

import android.content.Context
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.storage.AtomicFileWriter
import java.io.File

object ActiveDesignStore {
    private const val FILE_NAME =
        "brother_matrizes-active-project.flp"

    fun save(
        context: Context,
        design: EmbroideryDesign
    ): Result<Unit> =
        runCatching {
            AtomicFileWriter.write(
                target =
                    activeFile(
                        context
                    )
            ) {
                    output ->
                ProjectCodec.encodeTo(
                    design =
                        design,
                    sink =
                        output
                )
            }
        }

    fun load(
        context: Context
    ): Result<EmbroideryDesign?> =
        runCatching {
            val file =
                activeFile(
                    context
                )

            if (
                !file.exists()
            ) {
                null
            } else {
                runCatching {
                    file.inputStream()
                        .buffered(
                            64 * 1024
                        )
                        .use {
                            ProjectCodec.decode(
                                it
                            )
                        }
                }.getOrElse {
                        error ->
                    val quarantine =
                        File(
                            file.parentFile,
                            FILE_NAME +
                                ".corrupt"
                        )

                    runCatching {
                        if (
                            quarantine.exists()
                        ) {
                            quarantine.delete()
                        }

                        file.renameTo(
                            quarantine
                        )
                    }

                    throw IllegalStateException(
                        "O trabalho automático anterior estava corrompido e foi isolado.",
                        error
                    )
                }
            }
        }

    fun clear(
        context: Context
    ): Result<Unit> =
        runCatching {
            val file =
                activeFile(
                    context
                )

            if (
                file.exists()
            ) {
                check(
                    file.delete()
                ) {
                    "Não foi possível limpar o trabalho automático."
                }
            }
        }

    private fun activeFile(
        context: Context
    ): File =
        File(
            context.filesDir,
            FILE_NAME
        )
}
