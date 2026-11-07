package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.desktop.platform.DesktopFlpBackupExporter
import com.timachado.brothermatrizes.desktop.platform.DesktopFlpProjectFiles
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopFlpBackupExporterTest {
    private fun testDesign() = TextLayoutGenerator.generate(
        TextLayoutOptions(
            textOptions = TextMatrixOptions(
                text = "Maria", heightMm = 12f, style = TextStitchStyle.RUNNING
            )
        )
    ).getOrThrow()

    @Test
    fun exportsMultipleValidProjectsWithoutTouchingTheirBytes() {
        val folder = Files.createTempDirectory("brother-flp-source-")
        val target = Files.createTempFile("brother-flp-destination-", ".zip")
        Files.delete(target)
        val names = listOf("Ana.flp", "Projeto da Maria.flp")
        try {
            val original = names.associateWith { name ->
                val file = folder.resolve(name)
                DesktopFlpProjectFiles.saveNew(testDesign(), file)
                Files.readAllBytes(file)
            }
            Files.writeString(folder.resolve("instrucoes.txt"), "fora do backup")
            val result = DesktopFlpBackupExporter.exportLocalFlp(folder, target)
            assertEquals(2, result.exportedProjects)
            assertEquals(target, result.destination)
            val restored = linkedMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(Files.readAllBytes(target))).use { zip ->
                while (true) {
                    val next = zip.nextEntry ?: break
                    assertFalse(next.name.contains(".."))
                    assertFalse(next.name.contains('/'))
                    assertFalse(next.name.contains('\\'))
                    restored[next.name] = zip.readBytes()
                    zip.closeEntry()
                }
            }
            assertEquals(names.sorted(), restored.keys.sorted())
            for ((name, bytes) in original) {
                assertContentEquals(bytes, Files.readAllBytes(folder.resolve(name)))
                assertContentEquals(bytes, restored.getValue(name))
            }
            assertEquals("fora do backup", Files.readString(folder.resolve("instrucoes.txt")))
        } finally {
            names.forEach { Files.deleteIfExists(folder.resolve(it)) }
            Files.deleteIfExists(folder.resolve("instrucoes.txt"))
            Files.deleteIfExists(target)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun existingZipMustNotBeOverwritten() {
        val folder = Files.createTempDirectory("brother-flp-nooverwrite-")
        val project = folder.resolve("matriz.flp")
        val destination = Files.createTempFile("brother-backup-exists-", ".zip")
        try {
            DesktopFlpProjectFiles.saveNew(testDesign(), project)
            val old = byteArrayOf(7, 6, 5, 4, 3, 2, 1)
            Files.write(destination, old)
            assertFailsWith<java.nio.file.FileAlreadyExistsException> {
                DesktopFlpBackupExporter.exportLocalFlp(folder, destination)
            }
            assertContentEquals(old, Files.readAllBytes(destination))
            assertTrue(Files.size(project) > 10L)
        } finally {
            Files.deleteIfExists(project)
            Files.deleteIfExists(destination)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun refusesMissingInvalidAndEmptyProjectsWithoutCreatingBackup() {
        val folder = Files.createTempDirectory("brother-flp-invalid-")
        val destination = folder.resolve("empty.zip")
        val invalid = folder.resolve("broken.flp")
        try {
            assertFailsWith<IllegalArgumentException> {
                DesktopFlpBackupExporter.exportLocalFlp(folder, destination)
            }
            assertFalse(Files.exists(destination))
            Files.write(invalid, byteArrayOf(1, 2, 3))
            assertFailsWith<IllegalArgumentException> {
                DesktopFlpBackupExporter.exportLocalFlp(folder, destination)
            }
            assertFalse(Files.exists(destination))
        } finally {
            Files.deleteIfExists(invalid)
            Files.deleteIfExists(destination)
            Files.deleteIfExists(folder)
        }
    }
}
