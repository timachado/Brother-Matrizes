package com.timachado.brothermatrizes.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopBackupServiceTest {
    @Test
    fun `copia FLP recupera pontadas e nao sobrescreve backups existentes`() {
        val folder = Files.createTempDirectory("brother-desktop-backup-")
        try {
            val doc = DesktopMatrixService.createName("ANA", 12f)
            val first = DesktopBackupService.create(doc, folder)
            val original = Files.readAllBytes(first)
            val second = DesktopBackupService.create(doc, folder)
            assertNotEquals(first, second)
            assertTrue(original.contentEquals(Files.readAllBytes(first)))
            assertEquals(listOf(second, first).toSet(), DesktopBackupService.list(folder).toSet())
            val recovered = DesktopBackupService.restore(first)
            assertNull(recovered.letterSession)
            assertEquals(doc.current.points, recovered.document.current.points)
            assertEquals(doc.current.threadColors, recovered.document.current.threadColors)
        } finally {
            removeFiles(folder)
        }
    }

    @Test
    fun `camadas de letras sao vinculadas a copia e restauradas com integridade`() {
        val folder = Files.createTempDirectory("brother-desktop-layer-backup-")
        try {
            val layout = DesktopLetterComposer.start("AB", 14f,
                com.timachado.brothermatrizes.core.embroidery.TextStitchStyle.RUNNING)
            val document = DesktopLetterComposer.render(layout)
            val backup = DesktopBackupService.create(document, folder, layout)
            val sidecar = DesktopLetterProjectBundle.companion(backup)
            assertTrue(Files.isRegularFile(sidecar))
            val restored = DesktopBackupService.restore(backup)
            assertTrue(restored.letterSession != null)
            assertEquals(document.current.points, restored.document.current.points)
            // Alteração do arquivo associado bloqueia o resgate das camadas.
            Files.write(backup, byteArrayOf(8, 7, 6))
            assertFailsWith<Exception> { DesktopBackupService.restore(backup) }
        } finally {
            removeFiles(folder)
        }
    }

    @Test
    fun `nao recupera caminho arbitrario nem backup truncado`() {
        val folder = Files.createTempDirectory("brother-desktop-invalid-backup-")
        try {
            val arbitrary = folder.resolve("outra-matriz.flp")
            Files.write(arbitrary, byteArrayOf(1, 2, 3))
            assertFailsWith<IllegalArgumentException> {
                DesktopBackupService.restore(arbitrary)
            }
            val doc = DesktopMatrixService.createName("BM", 12f)
            val backup = DesktopBackupService.create(doc, folder)
            Files.write(backup, byteArrayOf(1, 2, 3))
            assertFailsWith<Exception> { DesktopBackupService.restore(backup) }
        } finally {
            removeFiles(folder)
        }
    }

    private fun removeFiles(folder: Path) {
        Files.newDirectoryStream(folder).use { entries ->
            entries.forEach { Files.deleteIfExists(it) }
        }
        Files.deleteIfExists(folder)
    }
}
