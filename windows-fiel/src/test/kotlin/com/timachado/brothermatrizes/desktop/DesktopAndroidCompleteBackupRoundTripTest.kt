package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.core.project.CompleteLibraryBackupCodec
import com.timachado.brothermatrizes.core.project.ProjectBackupCodec
import com.timachado.brothermatrizes.core.project.ProjectCodec
import com.timachado.brothermatrizes.desktop.platform.DesktopAndroidBackupInspector
import com.timachado.brothermatrizes.desktop.platform.DesktopAndroidCompleteBackupExporter
import com.timachado.brothermatrizes.desktop.platform.DesktopAndroidCompleteBackupRecovery
import com.timachado.brothermatrizes.desktop.platform.DesktopProjectCatalog
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exact Android codecs, entirely local test fixtures, no license/network writes. */
class DesktopAndroidCompleteBackupRoundTripTest {
    private fun workspace(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("bm-complete-android-roundtrip-")
        try { block(root) }
        finally {
            Files.walk(root).use { entries ->
                entries.sorted(Comparator.reverseOrder()).forEach {
                    Files.deleteIfExists(it)
                }
            }
        }
    }

    private fun androidFlp(name: String) = ProjectCodec.encode(
        TextLayoutGenerator.generate(
            TextLayoutOptions(
                textOptions = TextMatrixOptions(
                    text = name, heightMm = 12f, style = TextStitchStyle.RUNNING
                )
            )
        ).getOrThrow()
    )

    @Test
    fun exportsAndRestoresAndroidCompleteBackupWithAllProjectAndFontBytes() =
        workspace { root ->
            val projects = Files.createDirectory(root.resolve("projects"))
            val fonts = Files.createDirectory(root.resolve("fonts-source"))
            val recoveredParent = Files.createDirectory(root.resolve("restored"))
            val originalMatrix = androidFlp("Maria")
            val originalFont = byteArrayOf(0, 1, 0, 0, 9, 8, 7, 6)
            Files.write(projects.resolve("Maria.flp"), originalMatrix)
            Files.write(fonts.resolve("TI-Fonte.ttf"), originalFont)

            val destination = root.resolve("Original.brother_matrizes-fullbackup")
            val exported = DesktopAndroidCompleteBackupExporter.export(
                projects, fonts, destination
            )
            assertEquals(1, exported.exportedProjects)
            assertEquals(1, exported.exportedFonts)
            val originalCodec = CompleteLibraryBackupCodec.decode(
                Files.readAllBytes(destination)
            )
            assertContentEquals(originalFont, originalCodec.fonts.single().bytes)
            assertContentEquals(
                originalMatrix,
                ProjectBackupCodec.decode(originalCodec.projectsBackup).single()
            )
            val preview = DesktopAndroidBackupInspector.inspect(destination)
            assertEquals(1, preview.projectCount)
            assertEquals(1, preview.fontCount)

            val recovered = DesktopAndroidCompleteBackupRecovery.restore(
                destination, recoveredParent
            )
            assertEquals(1, recovered.recoveredProjects)
            assertEquals(1, recovered.recoveredFonts)
            assertEquals(1, DesktopProjectCatalog.scan(recovered.folder).projects.size)
            assertContentEquals(
                originalMatrix,
                Files.readAllBytes(recovered.folder.resolve("Android-00001.flp"))
            )
            assertContentEquals(
                originalFont,
                Files.readAllBytes(recovered.folder.resolve("fonts/TI-Fonte.ttf"))
            )
            assertFalse(Files.exists(recoveredParent.resolve("TI-Fonte.ttf")))
            assertTrue(Files.isRegularFile(destination))
            assertFailsWith<IllegalArgumentException> {
                DesktopAndroidCompleteBackupExporter.export(
                    projects, fonts, destination
                )
            }
            assertContentEquals(originalMatrix, Files.readAllBytes(
                projects.resolve("Maria.flp")
            ))
        }

    @Test
    fun restoresFontOnlyAndroidBackupWithoutInventingAnyMatrices() =
        workspace { root ->
            val projects = Files.createDirectory(root.resolve("empty-projects"))
            val fonts = Files.createDirectory(root.resolve("font-only"))
            val parent = Files.createDirectory(root.resolve("restore-here"))
            val font = byteArrayOf(0, 1, 0, 0, 1, 2, 3, 4)
            Files.write(fonts.resolve("Original.ttf"), font)
            val backup = root.resolve("Fonte.brother_matrizes-fullbackup")
            val exported = DesktopAndroidCompleteBackupExporter.export(
                projects, fonts, backup
            )
            assertEquals(0, exported.exportedProjects)
            assertEquals(1, exported.exportedFonts)
            val recovered = DesktopAndroidCompleteBackupRecovery.restore(backup, parent)
            assertEquals(0, recovered.recoveredProjects)
            assertEquals(1, recovered.recoveredFonts)
            assertContentEquals(
                font, Files.readAllBytes(recovered.folder.resolve("fonts/Original.ttf"))
            )
            assertEquals(0, DesktopProjectCatalog.scan(recovered.folder).projects.size)
        }
}
