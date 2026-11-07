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
import com.timachado.brothermatrizes.desktop.platform.DesktopAndroidProjectRecovery
import com.timachado.brothermatrizes.desktop.platform.DesktopProjectCatalog
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Uses ONLY synthetic Android FLPs and backups. No client data or network. */
class DesktopAndroidProjectRecoveryTest {
    private fun withWorkspace(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("bm-recover-android-qa-")
        try { block(root) }
        finally {
            Files.walk(root).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun project(name: String): ByteArray {
        val matrix = TextLayoutGenerator.generate(
            TextLayoutOptions(
                textOptions = TextMatrixOptions(
                    text = name, heightMm = 12f, style = TextStitchStyle.RUNNING
                )
            )
        ).getOrThrow()
        return ProjectCodec.encode(matrix)
    }

    @Test
    fun completeAndroidBackupRecoversOnlyOriginalFlpBytesAndNeverFontsOrExistingProjects() =
        withWorkspace { root ->
            val source = root.resolve("Android.brother_matrizes-fullbackup")
            val parent = Files.createDirectory(root.resolve("recuperados"))
            val originals = listOf(project("Ana"), project("Maria"))
            val fonts = listOf(
                BackedUpFont(
                    "Fonte-TI.ttf", byteArrayOf(0, 1, 0, 0, 8, 7, 6)
                ),
                BackedUpFont(
                    "Fonte-TI.otf", "OTTO".toByteArray() + byteArrayOf(7, 8, 9)
                )
            )
            val fullBackup = CompleteLibraryBackupCodec.encode(
                ProjectBackupCodec.encode(originals), fonts
            )
            Files.write(source, fullBackup)
            val existing = parent.resolve("Android-00001.flp")
            val sentinel = byteArrayOf(19, 18, 17)
            Files.write(existing, sentinel)

            val recovered = DesktopAndroidProjectRecovery.restoreProjectsOnly(
                source, parent
            )
            assertEquals(2, recovered.restoredProjects)
            assertEquals(2, recovered.ignoredFonts)
            assertEquals(
                DesktopAndroidBackupInspector.Kind.ANDROID_COMPLETE,
                recovered.sourceKind
            )
            assertEquals(parent, recovered.folder.parent)
            assertTrue(Files.isDirectory(recovered.folder))
            assertEquals(2, DesktopProjectCatalog.scan(recovered.folder).projects.size)
            originals.forEachIndexed { index, bytes ->
                val path = recovered.folder.resolve(
                    "Android-" + (index + 1).toString().padStart(5, '0') + ".flp"
                )
                assertContentEquals(bytes, Files.readAllBytes(path))
            }
            assertContentEquals(sentinel, Files.readAllBytes(existing))
            assertContentEquals(fullBackup, Files.readAllBytes(source))
            assertFalse(Files.exists(recovered.folder.resolve("imported_fonts")))
            assertFalse(Files.exists(parent.resolve("fonts")))
            assertFalse(Files.exists(parent.resolve("licenses")))

            // Repeating a recovery must add another NEW folder, not clobber.
            val again = DesktopAndroidProjectRecovery.restoreProjectsOnly(
                source, parent
            )
            assertNotEquals(recovered.folder, again.folder)
            assertContentEquals(
                originals[0],
                Files.readAllBytes(recovered.folder.resolve("Android-00001.flp"))
            )
        }

    @Test
    fun historicAndroidProjectsOnlyBackupRecoversProjectFilesWithoutFonts() =
        withWorkspace { root ->
            val source = root.resolve("Historico.brother_matrizes-backup")
            val destination = Files.createDirectory(root.resolve("destino"))
            val originals = listOf(project("Bia"), project("Lia"))
            val bytes = ProjectBackupCodec.encode(originals)
            Files.write(source, bytes)
            val recovered = DesktopAndroidProjectRecovery.restoreProjectsOnly(
                source, destination
            )
            assertEquals(2, recovered.restoredProjects)
            assertEquals(0, recovered.ignoredFonts)
            assertEquals(
                DesktopAndroidBackupInspector.Kind.ANDROID_PROJECTS_ONLY,
                recovered.sourceKind
            )
            assertEquals(2, DesktopProjectCatalog.scan(recovered.folder).projects.size)
            assertContentEquals(bytes, Files.readAllBytes(source))
        }

    @Test
    fun refusesMoreThanWindowsLibraryLimitWithoutCreatingAnyFolders() =
        withWorkspace { root ->
            val source = root.resolve("Muitos.brother_matrizes-fullbackup")
            val parent = Files.createDirectory(root.resolve("destino"))
            val one = project("Ana")
            val nested = ProjectBackupCodec.encode(List(201) { one })
            val all = CompleteLibraryBackupCodec.encode(nested, emptyList())
            Files.write(source, all)

            assertFailsWith<IllegalArgumentException> {
                DesktopAndroidProjectRecovery.restoreProjectsOnly(source, parent)
            }
            Files.list(parent).use { assertEquals(0L, it.count()) }
            assertContentEquals(all, Files.readAllBytes(source))
        }

    @Test
    fun rejectsFontOnlyInvalidAndTruncatedAndroidBackupWithoutPublishing() =
        withWorkspace { root ->
            val parent = Files.createDirectory(root.resolve("recuperacao"))
            val noMatrices = root.resolve("Fonte.brother_matrizes-fullbackup")
            val full = CompleteLibraryBackupCodec.encode(
                ProjectBackupCodec.encode(emptyList()),
                listOf(BackedUpFont(
                    "Fonte.ttf", byteArrayOf(0, 1, 0, 0, 3, 4)
                ))
            )
            Files.write(noMatrices, full)
            assertFailsWith<IllegalArgumentException> {
                DesktopAndroidProjectRecovery.restoreProjectsOnly(noMatrices, parent)
            }
            val invalid = root.resolve("Incompleto.brother_matrizes-fullbackup")
            val valid = CompleteLibraryBackupCodec.encode(
                ProjectBackupCodec.encode(listOf(project("Ana"))), emptyList()
            )
            Files.write(invalid, valid.copyOf(valid.size - 22))
            assertFailsWith<Exception> {
                DesktopAndroidProjectRecovery.restoreProjectsOnly(invalid, parent)
            }
            assertFailsWith<IllegalArgumentException> {
                DesktopAndroidProjectRecovery.restoreProjectsOnly(
                    root.resolve("nao-existe.brother_matrizes-backup"), parent
                )
            }
            Files.list(parent).use { assertEquals(0L, it.count()) }
        }
}
