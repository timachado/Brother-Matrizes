package com.timachado.brothermatrizes.core.storage

import java.io.ByteArrayInputStream
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeStorageTest {
    @Test
    fun atomicWriteReplacesFileOnlyAfterCompleteWrite() {
        val directory =
            Files
                .createTempDirectory(
                    "brother_matrizes-atomic"
                )
                .toFile()

        val target =
            directory.resolve(
                "project.flp"
            )

        target.writeText(
            "original"
        )

        val result =
            runCatching {
                AtomicFileWriter
                    .write(
                        target
                    ) {
                            output ->
                        output.write(
                            "partial"
                                .toByteArray()
                        )

                        error(
                            "simulated failure"
                        )
                    }
            }

        assertTrue(
            result.isFailure
        )

        assertEquals(
            "original",
            target.readText()
        )
    }

    @Test
    fun atomicWriteCommitsSuccessfulBytes() {
        val directory =
            Files
                .createTempDirectory(
                    "brother_matrizes-atomic-ok"
                )
                .toFile()

        val target =
            directory.resolve(
                "project.flp"
            )

        AtomicFileWriter
            .write(
                target,
                byteArrayOf(
                    1,
                    2,
                    3,
                    4
                )
            )

        assertArrayEquals(
            byteArrayOf(
                1,
                2,
                3,
                4
            ),
            target.readBytes()
        )
    }

    @Test
    fun boundedReaderRejectsOversizedStream() {
        val result =
            runCatching {
                SafeInputReader
                    .readBytes(
                        ByteArrayInputStream(
                            ByteArray(
                                1025
                            )
                        ),
                        1024
                    )
            }

        assertTrue(
            result.isFailure
        )
    }
}
