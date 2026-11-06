package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.desktop.platform.DesktopFlpProjectFiles
import com.timachado.brothermatrizes.desktop.platform.DesktopProjectCatalog
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Catálogo local: sem rede, sem symlink e sem mutações no projeto original. */
class AndroidProjectLibraryParityTest {
    private val root = Path.of("").toAbsolutePath().normalize()

    @Test
    fun `Biblioteca mantém cartões Android e isola somente a ponte RAR`() {
        val original = Files.readString(root.resolve(
            "../app/src/main/java/com/timachado/brothermatrizes/ProjectLibraryScreen.kt"
        )).replace("\r\n", "\n")
        val generated = Files.readString(root.resolve(
            "build/generated/android-project-library/kotlin/com/timachado/brothermatrizes/ProjectLibraryScreen.kt"
        )).replace("\r\n", "\n")
        val anchor = "    val recentProjects ="
        assertEquals(original.substringAfter(anchor), generated.substringAfter(anchor),
            "Layout e callbacks da Biblioteca Android foram modificados.")
        assertTrue(generated.contains("fun ProjectLibraryScreen("))
        assertTrue(!generated.contains("RarExternalOpener"))
        assertTrue(!generated.contains("BrotherMatrizesAccountService"))
        assertTrue(generated.contains("Nenhuma operação nem cota será cobrada."))
    }

    @Test
    fun `Catálogo lista FLP real e rejeita projeto inválido sem alterar arquivos`() {
        val temp = Files.createTempDirectory("bm-project-catalog-")
        try {
            val design = TextLayoutGenerator.generate(TextLayoutOptions(
                textOptions = TextMatrixOptions(
                    text = "Maria", heightMm = 12f, style = TextStitchStyle.RUNNING
                )
            )).getOrThrow()
            val originalFile = temp.resolve("cliente.flp")
            DesktopFlpProjectFiles.saveNew(design, originalFile)
            val raw = Files.readAllBytes(originalFile)
            Files.write(temp.resolve("corrompido.flp"), byteArrayOf(0, 1, 2, 3))
            Files.write(temp.resolve("ignorar.exe"), byteArrayOf(4, 5, 6))
            val scan = DesktopProjectCatalog.scan(temp)
            assertEquals(1, scan.projects.size)
            assertEquals(1, scan.skippedInvalid)
            assertEquals("cliente.flp", scan.projects.single().fileName)
            assertEquals(originalFile, scan.pathById[scan.projects.single().id])
            assertTrue(scan.projects.single().stitchCount > 10)
            assertTrue(raw.contentEquals(Files.readAllBytes(originalFile)))
        } finally {
            Files.list(temp).use { it.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(temp)
        }
    }

    @Test
    fun `Catálogo rejeita pasta ausente e symlink quando suportado`() {
        val temp = Files.createTempDirectory("bm-project-link-")
        try {
            assertFailsWith<IllegalArgumentException> {
                DesktopProjectCatalog.scan(temp.resolve("nao-existe"))
            }
            val link = temp.resolve("link")
            val made = runCatching { Files.createSymbolicLink(link, temp) }.isSuccess
            if (made) {
                assertFailsWith<IllegalArgumentException> {
                    DesktopProjectCatalog.scan(link)
                }
            }
        } finally {
            Files.deleteIfExists(temp.resolve("link"))
            Files.deleteIfExists(temp)
        }
    }
}
