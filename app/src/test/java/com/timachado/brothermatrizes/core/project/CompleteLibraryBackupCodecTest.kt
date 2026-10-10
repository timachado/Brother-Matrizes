package com.timachado.brothermatrizes.core.project

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CompleteLibraryBackupCodecTest {
    private val ttf = BackedUpFont("FonteNova-0123456789.ttf", byteArrayOf(0, 1, 0, 0, 3, 4, 5))
    private val otf = BackedUpFont("FonteNova-9876543210.otf",
        "OTTO".toByteArray(Charsets.US_ASCII) + byteArrayOf(3, 4, 5))

    @Test fun fullBackupKeepsProjectArchiveAndBothFonts() {
        val projects = ProjectBackupCodec.encode(emptyList())
        val data = CompleteLibraryBackupCodec.encode(projects, listOf(ttf, otf))
        assertTrue(CompleteLibraryBackupCodec.isCompleteArchive(data))
        val recovered = CompleteLibraryBackupCodec.decode(data)
        assertArrayEquals(projects, recovered.projectsBackup)
        assertEquals(2, recovered.fonts.size)
        assertArrayEquals(ttf.bytes, recovered.fonts[0].bytes)
        assertArrayEquals(otf.bytes, recovered.fonts[1].bytes)
    }

    @Test fun historicalProjectBackupHasSeparateFormat() {
        val legacy = ProjectBackupCodec.encode(emptyList())
        assertFalse(CompleteLibraryBackupCodec.isCompleteArchive(legacy))
        assertEquals(0, ProjectBackupCodec.decode(legacy).size)
    }

    @Test fun rejectsPathTraversalAndForgedFont() {
        assertThrows(IllegalArgumentException::class.java) {
            CompleteLibraryBackupCodec.validateFont(BackedUpFont("../evil.ttf", ttf.bytes))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CompleteLibraryBackupCodec.validateFont(BackedUpFont("looks-valid.ttf",
                "evilcontent".toByteArray()))
        }
    }

    @Test fun rejectsDuplicateFontNamesAndTruncatedArchive() {
        val legacy = ProjectBackupCodec.encode(emptyList())
        assertThrows(IllegalArgumentException::class.java) {
            CompleteLibraryBackupCodec.encode(legacy, listOf(ttf, ttf))
        }
        val encoded = CompleteLibraryBackupCodec.encode(legacy, listOf(ttf))
        assertThrows(Exception::class.java) {
            CompleteLibraryBackupCodec.decode(encoded.copyOf(encoded.size / 2))
        }
    }
}
