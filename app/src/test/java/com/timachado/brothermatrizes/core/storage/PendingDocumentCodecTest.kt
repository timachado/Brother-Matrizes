package com.timachado.brothermatrizes.core.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingDocumentCodecTest {
    @Test
    fun roundTripPreservesPendingSave() {
        val original =
            DurablePendingDocument(
                fileName =
                    "matriz-maquina.pes",
                bytes =
                    byteArrayOf(
                        1,
                        2,
                        3,
                        4
                    ),
                successMessage =
                    "Salvo.",
                machineMethod =
                    "USB / OTG",
                machineFormat =
                    "PES"
            )

        val restored =
            PendingDocumentCodec
                .decode(
                    PendingDocumentCodec
                        .encode(
                            original
                        )
                )

        assertEquals(
            original.fileName,
            restored.fileName
        )

        assertArrayEquals(
            original.bytes,
            restored.bytes
        )

        assertEquals(
            original.machineMethod,
            restored.machineMethod
        )

        assertEquals(
            original.machineFormat,
            restored.machineFormat
        )
    }

    @Test
    fun optionalMachineMetadataCanBeAbsent() {
        val restored =
            PendingDocumentCodec
                .decode(
                    PendingDocumentCodec
                        .encode(
                            DurablePendingDocument(
                                fileName =
                                    "copia.dst",
                                bytes =
                                    byteArrayOf(
                                        9
                                    ),
                                successMessage =
                                    "Cópia salva."
                            )
                        )
                )

        assertNull(
            restored.machineMethod
        )

        assertNull(
            restored.machineFormat
        )
    }
}
