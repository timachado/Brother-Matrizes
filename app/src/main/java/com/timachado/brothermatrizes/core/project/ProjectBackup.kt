package com.timachado.brothermatrizes.core.project

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ProjectBackupCodec {
    private const val MANIFEST =
        "brother_matrizes-backup.txt"

    private const val MAGIC =
        "BROTHER_MATRIZES_BACKUP"

    private const val VERSION =
        1

    private const val MAX_PROJECTS =
        5000

    private const val MAX_ENTRY_BYTES =
        64 * 1024 * 1024

    private const val MAX_TOTAL_BYTES =
        128 * 1024 * 1024

    fun encode(
        projects: List<ByteArray>
    ): ByteArray {
        require(
            projects.size <=
                MAX_PROJECTS
        ) {
            "Há projetos demais para este backup."
        }

        val total =
            projects.sumOf {
                it.size.toLong()
            }

        require(
            total <=
                MAX_TOTAL_BYTES
        ) {
            "A biblioteca é grande demais para um único backup."
        }

        val buffer =
            ByteArrayOutputStream()

        ZipOutputStream(
            buffer
        ).use {
                zip ->
            zip.putNextEntry(
                ZipEntry(
                    MANIFEST
                )
            )

            zip.write(
                (
                    MAGIC +
                        "\n" +
                        VERSION +
                        "\n" +
                        projects.size +
                        "\n"
                    ).toByteArray(
                        Charsets.UTF_8
                    )
            )

            zip.closeEntry()

            projects.forEachIndexed {
                    index,
                    bytes ->
                require(
                    bytes.size <=
                        MAX_ENTRY_BYTES
                ) {
                    "Um projeto é grande demais para o backup."
                }

                ProjectCodec.decode(
                    bytes
                )

                val name =
                    "projects/" +
                        index
                            .toString()
                            .padStart(
                                5,
                                '0'
                            ) +
                        ".flp"

                zip.putNextEntry(
                    ZipEntry(
                        name
                    )
                )

                zip.write(
                    bytes
                )

                zip.closeEntry()
            }
        }

        return buffer
            .toByteArray()
    }

    fun decode(
        bytes: ByteArray
    ): List<ByteArray> {
        require(
            bytes.isNotEmpty()
        ) {
            "Backup vazio."
        }

        require(
            bytes.size <=
                MAX_TOTAL_BYTES
        ) {
            "Backup grande demais."
        }

        var manifestSeen =
            false

        var expectedCount:
            Int? = null

        var totalRead = 0L

        val projects =
            mutableListOf<
                ByteArray
            >()

        ZipInputStream(
            ByteArrayInputStream(
                bytes
            )
        ).use {
                zip ->
            while (true) {
                val entry =
                    zip.nextEntry
                        ?: break

                val name =
                    entry.name

                require(
                    !name.contains(
                        ".."
                    ) &&
                        !name.startsWith(
                            "/"
                        ) &&
                        !name.startsWith(
                            "\\"
                        )
                ) {
                    "Backup contém caminho inválido."
                }

                if (
                    entry.isDirectory
                ) {
                    zip.closeEntry()

                    continue
                }

                val entryBytes =
                    readEntry(
                        zip
                    )

                totalRead +=
                    entryBytes
                        .size

                require(
                    totalRead <=
                        MAX_TOTAL_BYTES
                ) {
                    "Backup excede o limite permitido."
                }

                when {
                    name ==
                        MANIFEST -> {
                        require(
                            !manifestSeen
                        ) {
                            "Manifesto duplicado."
                        }

                        val lines =
                            entryBytes
                                .toString(
                                    Charsets.UTF_8
                                )
                                .lineSequence()
                                .filter {
                                    it.isNotBlank()
                                }
                                .toList()

                        require(
                            lines.size >=
                                3 &&
                                lines[0] ==
                                    MAGIC
                        ) {
                            "Arquivo não é um backup do Brother Matrizes."
                        }

                        require(
                            lines[1]
                                .toIntOrNull() ==
                                VERSION
                        ) {
                            "Versão de backup ainda não suportada."
                        }

                        expectedCount =
                            lines[2]
                                .toIntOrNull()

                        require(
                            expectedCount in
                                0..MAX_PROJECTS
                        ) {
                            "Quantidade de projetos inválida."
                        }

                        manifestSeen =
                            true
                    }

                    name.startsWith(
                        "projects/"
                    ) &&
                        name.endsWith(
                            ".flp",
                            ignoreCase =
                                true
                        ) -> {
                        require(
                            projects.size <
                                MAX_PROJECTS
                        ) {
                            "Há projetos demais no backup."
                        }

                        ProjectCodec.decode(
                            entryBytes
                        )

                        projects +=
                            entryBytes
                    }

                    else ->
                        Unit
                }

                zip.closeEntry()
            }
        }

        require(
            manifestSeen
        ) {
            "Manifesto do backup não encontrado."
        }

        require(
            expectedCount ==
                projects.size
        ) {
            "Backup incompleto: quantidade de projetos não confere."
        }

        return projects
    }

    private fun readEntry(
        zip: ZipInputStream
    ): ByteArray {
        val buffer =
            ByteArrayOutputStream()

        val chunk =
            ByteArray(
                16 * 1024
            )

        var total = 0

        while (true) {
            val read =
                zip.read(
                    chunk
                )

            if (
                read <=
                    0
            ) {
                break
            }

            total +=
                read

            require(
                total <=
                    MAX_ENTRY_BYTES
            ) {
                "Entrada do backup excede o limite permitido."
            }

            buffer.write(
                chunk,
                0,
                read
            )
        }

        return buffer
            .toByteArray()
    }
}

object ProjectBackupStore {

    fun exportBackup(
        context: Context
    ): Result<ByteArray> =
        runCatching {
            val projects =
                ProjectStore.list(
                    context
                ).getOrThrow()

            require(
                projects.isNotEmpty()
            ) {
                "Não há matrizes salvas para backup."
            }

            val bytes =
                projects.map {
                        project ->
                    ProjectCodec.encode(
                        ProjectStore
                            .load(
                                context,
                                project.id
                            )
                            .getOrThrow()
                    )
                }

            ProjectBackupCodec.encode(
                bytes
            )
        }

    fun restoreBackup(
        context: Context,
        bytes: ByteArray
    ): Result<Int> =
        runCatching {
            val projects =
                ProjectBackupCodec.decode(
                    bytes
                )

            projects.forEach {
                    projectBytes ->
                val design =
                    ProjectCodec.decode(
                        projectBytes
                    )

                ProjectStore.save(
                    context,
                    design
                ).getOrThrow()
            }

            projects.size
        }
}
