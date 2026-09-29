package com.timachado.brothermatrizes.core.storage

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

data class DurablePendingDocument(
    val fileName: String,
    val bytes: ByteArray,
    val successMessage: String,
    val machineMethod: String? = null,
    val machineFormat: String? = null
)

object PendingDocumentCodec {
    private const val MAGIC =
        "BROTHER_MATRIZES_PENDING_DOCUMENT"

    private val LEGACY_MAGIC =
        "FIO" + "LAB_PENDING_DOCUMENT"

    private const val VERSION =
        1

    private const val MAX_BYTES =
        128 * 1024 * 1024

    fun encode(
        document: DurablePendingDocument
    ): ByteArray {
        val buffer =
            ByteArrayOutputStream()

        write(
            document =
                document,
            output =
                buffer
        )

        return buffer
            .toByteArray()
    }

    fun write(
        document: DurablePendingDocument,
        output: OutputStream
    ) {
        validate(
            document
        )

        val data =
            DataOutputStream(
                output
            )

        data.writeUTF(
            MAGIC
        )

        data.writeInt(
            VERSION
        )

        data.writeUTF(
            document.fileName
                .take(
                    200
                )
        )

        data.writeUTF(
            document.successMessage
                .take(
                    500
                )
        )

        writeNullableString(
            data,
            document.machineMethod
        )

        writeNullableString(
            data,
            document.machineFormat
        )

        data.writeInt(
            document.bytes
                .size
        )

        data.write(
            document.bytes
        )

        data.flush()
    }

    fun decode(
        bytes: ByteArray
    ): DurablePendingDocument =
        ByteArrayInputStream(
            bytes
        ).use {
            decode(
                it
            )
        }

    fun decode(
        input: InputStream
    ): DurablePendingDocument {
        val data =
            DataInputStream(
                input
            )

        val magic =
            data.readUTF()

        require(
            magic ==
                MAGIC ||
                magic ==
                LEGACY_MAGIC
        ) {
            "Documento pendente inválido."
        }

        require(
            data.readInt() ==
                VERSION
        ) {
            "Versão de documento pendente não suportada."
        }

        val fileName =
            data.readUTF()

        val successMessage =
            data.readUTF()

        val machineMethod =
            readNullableString(
                data
            )

        val machineFormat =
            readNullableString(
                data
            )

        val size =
            data.readInt()

        require(
            size in
                1..MAX_BYTES
        ) {
            "Tamanho do documento pendente inválido."
        }

        val payload =
            ByteArray(
                size
            )

        data.readFully(
            payload
        )

        require(
            data.read() ==
                -1
        ) {
            "Documento pendente contém dados extras."
        }

        return DurablePendingDocument(
            fileName =
                fileName,
            bytes =
                payload,
            successMessage =
                successMessage,
            machineMethod =
                machineMethod,
            machineFormat =
                machineFormat
        )
    }

    private fun validate(
        document: DurablePendingDocument
    ) {
        require(
            document.fileName
                .isNotBlank()
        ) {
            "Nome do arquivo pendente inválido."
        }

        require(
            document.bytes
                .isNotEmpty()
        ) {
            "Arquivo pendente vazio."
        }

        require(
            document.bytes
                .size <=
                MAX_BYTES
        ) {
            "Arquivo pendente grande demais."
        }
    }

    private fun writeNullableString(
        output: DataOutputStream,
        value: String?
    ) {
        output.writeBoolean(
            value !=
                null
        )

        if (
            value !=
                null
        ) {
            output.writeUTF(
                value.take(
                    200
                )
            )
        }
    }

    private fun readNullableString(
        input: DataInputStream
    ): String? =
        if (
            input.readBoolean()
        ) {
            input.readUTF()
        } else {
            null
        }
}

object PendingDocumentStore {
    private const val FILE_NAME =
        "brother_matrizes-pending-document.bin"

    fun save(
        context: Context,
        document: DurablePendingDocument
    ): Result<Unit> =
        runCatching {
            AtomicFileWriter.write(
                target =
                    file(
                        context
                    )
            ) {
                    output ->
                PendingDocumentCodec
                    .write(
                        document =
                            document,
                        output =
                            output
                    )
            }
        }

    fun load(
        context: Context
    ): Result<DurablePendingDocument?> =
        runCatching {
            val file =
                file(
                    context
                )

            if (
                !file.exists()
            ) {
                null
            } else {
                file.inputStream()
                    .use {
                        PendingDocumentCodec
                            .decode(
                                it
                            )
                    }
            }
        }

    fun clear(
        context: Context
    ): Result<Unit> =
        runCatching {
            val file =
                file(
                    context
                )

            if (
                file.exists()
            ) {
                check(
                    file.delete()
                ) {
                    "Não foi possível limpar o documento pendente."
                }
            }
        }

    private fun file(
        context: Context
    ): File =
        File(
            context.filesDir,
            FILE_NAME
        )
}
