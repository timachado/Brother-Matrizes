package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.core.project.BackedUpFont
import com.timachado.brothermatrizes.core.project.CompleteLibraryBackupCodec
import com.timachado.brothermatrizes.core.project.ProjectBackupCodec
import com.timachado.brothermatrizes.core.project.ProjectCodec
import com.timachado.brothermatrizes.desktop.platform.DesktopAndroidBackupInspector
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * Android's EXACT pure Kotlin backup codecs, copied at build time,
 * exercise synthetic project and font archives entirely offline.
 */
class DesktopAndroidBackupInspectorTest {
    private fun withFolder(block: (Path) -> Unit) {
        val folder = Files.createTempDirectory("bm-android-backup-inspection-")
        try { block(folder) }
        finally {
            Files.walk(folder).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun syntheticProject(): ByteArray {
        val design = TextLayoutGenerator.generate(
            TextLayoutOptions(
                textOptions = TextMatrixOptions(
                    text = "Ana", heightMm = 12f, style = TextStitchStyle.RUNNING
                )
            )
        ).getOrThrow()
        return ProjectCodec.encode(design)
    }

    @Test
    fun recognizesOriginalAndroidFullBackupWithNestedProjectsAndBothFontTypesReadOnly() =
        withFolder { dir ->
            val projectBytes = syntheticProject()
            val projects = ProjectBackupCodec.encode(listOf(projectBytes))
            val fonts = listOf(
                BackedUpFont("FonteNova-0123456789.ttf", byteArrayOf(0, 1, 0, 0, 4, 6)),
                BackedUpFont("FonteNova-9876543210.otf", "OTTO".toByteArray() + byteArrayOf(6, 4))
            )
            val bytes = CompleteLibraryBackupCodec.encode(projects, fonts)
            val file = dir.resolve("Brother-Matrizes.brother_matrizes-fullbackup")
            Files.write(file, bytes)
            val result = DesktopAndroidBackupInspector.inspect(file)
            assertEquals(DesktopAndroidBackupInspector.Kind.ANDROID_COMPLETE, result.kind)
            assertEquals(1, result.projectCount)
            assertEquals(2, result.fontCount)
            assertEquals(bytes.size.toLong(), result.compressedBytes)
            assertContentEquals(bytes, Files.readAllBytes(file))
            // No imported_fonts directory, project directory or local license.
            Files.list(dir).use { assertEquals(1L, it.count()) }
        }

    @Test
    fun recognizesHistoricalAndroidProjectsOnlyArchiveReadOnly() = withFolder { dir ->
        val project = syntheticProject()
        val bytes = ProjectBackupCodec.encode(listOf(project, project))
        val file = dir.resolve("historico.brother_matrizes-backup")
        Files.write(file, bytes)
        val result = DesktopAndroidBackupInspector.inspect(file)
        assertEquals(DesktopAndroidBackupInspector.Kind.ANDROID_PROJECTS_ONLY, result.kind)
        assertEquals(2, result.projectCount)
        assertEquals(0, result.fontCount)
        assertContentEquals(bytes, Files.readAllBytes(file))
    }

    @Test
    fun invalidZipWrongTypeAndMissingCentralDirectoryAreRejectedWithoutSideEffects() =
        withFolder { dir ->
            val archive = dir.resolve("arquivo.brother_matrizes-fullbackup")
            val bytes = CompleteLibraryBackupCodec.encode(
                ProjectBackupCodec.encode(listOf(syntheticProject())), emptyList()
            )
            Files.write(archive, bytes.copyOf(bytes.size - 22))
            assertFailsWith<Exception> { DesktopAndroidBackupInspector.inspect(archive) }
            Files.write(archive, bytes)
            val badExtension = dir.resolve("arquivo.zip")
            Files.write(badExtension, bytes)
            assertFailsWith<IllegalArgumentException> {
                DesktopAndroidBackupInspector.inspect(badExtension)
            }
            Files.write(archive, byteArrayOf(1, 2, 3))
            assertFailsWith<Exception> { DesktopAndroidBackupInspector.inspect(archive) }
            Files.list(dir).use { assertEquals(2L, it.count()) }
        }

    @Test
    fun historicalProjectBackupRejectsUnexpectedFontEntriesAndNeverImportsThem() =
        withFolder { dir ->
            val legacy = ProjectBackupCodec.encode(listOf(syntheticProject()))
            val original = java.util.zip.ZipInputStream(legacy.inputStream()).use { archive ->
                buildList {
                    while (true) {
                        val entry = archive.nextEntry ?: break
                        add(entry.name to archive.readBytes())
                        archive.closeEntry()
                    }
                }
            }
            val file = dir.resolve("historico.brother_matrizes-backup")
            ZipOutputStream(Files.newOutputStream(file)).use { out ->
                for ((name, bytes) in original +
                    ("fonts/unexpected.ttf" to byteArrayOf(0, 1, 0, 0, 1, 2))) {
                    out.putNextEntry(ZipEntry(name))
                    out.write(bytes)
                    out.closeEntry()
                }
            }
            assertFailsWith<IllegalArgumentException> {
                DesktopAndroidBackupInspector.inspect(file)
            }
            Files.list(dir).use { assertEquals(1L, it.count()) }
        }

    @Test
    fun rejectsUnexpectedNestedEntriesEvenWithValidManifestAndProjectBackup() =
        withFolder { dir ->
            val full = CompleteLibraryBackupCodec.encode(
                ProjectBackupCodec.encode(listOf(syntheticProject())), emptyList()
            )
            val file = dir.resolve("unsafe.brother_matrizes-fullbackup")
            // Deliberately add a new extra ZIP entry not part of Android's
            // official complete-backup contract.
            val original = java.util.zip.ZipInputStream(full.inputStream()).use { archive ->
                buildList {
                    while (true) {
                        val entry = archive.nextEntry ?: break
                        add(entry.name to archive.readBytes())
                        archive.closeEntry()
                    }
                }
            }
            ZipOutputStream(Files.newOutputStream(file)).use { out ->
                for ((name, bytes) in original + ("../traversal.flp" to byteArrayOf(2, 3))) {
                    out.putNextEntry(ZipEntry(name))
                    out.write(bytes)
                    out.closeEntry()
                }
            }
            assertFailsWith<IllegalArgumentException> {
                DesktopAndroidBackupInspector.inspect(file)
            }
            assertFalse(Files.exists(dir.parent.resolve("traversal.flp")))
        }
}
