package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.EditTransform
import com.timachado.brothermatrizes.core.embroidery.MatrixEditor
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.desktop.platform.DesktopEmbroideryFiles
import com.timachado.brothermatrizes.desktop.platform.DesktopFlpProjectFiles
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Validação de matrizes binárias reais de referência versionadas com o Android.
 * Não são projetos de clientes. Nunca escreve na árvore app/ ou no original;
 * toda edição é feita numa cópia isolada em diretório temporário do runner.
 */
class DesktopRealEmbroideryFixtureSafetyTest {
    private val sourceFolder = Path.of(
        "..", "app", "src", "test", "resources", "samples"
    ).toAbsolutePath().normalize()

    private fun digest(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    @Test
    fun `PES e JEF originais importam editam e reabrem FLP sem tocar nas fontes`() {
        for ((format, fileName) in listOf(
            "PES" to "random1.pes",
            "JEF" to "random1.jef"
        )) {
            val fixture = sourceFolder.resolve(fileName)
            assertTrue(Files.isRegularFile(fixture), "Fixture Android ausente: $fileName")
            assertTrue(!Files.isSymbolicLink(fixture))
            val originalBytes = Files.readAllBytes(fixture)
            val originalDigest = digest(originalBytes)
            assertTrue(originalBytes.size > 500)
            val work = Files.createTempDirectory("brother-real-${format.lowercase()}-qa-")
            try {
                val backup = work.resolve("backup-$fileName")
                val selected = work.resolve(fileName)
                Files.copy(fixture, backup, StandardCopyOption.COPY_ATTRIBUTES)
                Files.copy(fixture, selected)
                assertContentEquals(originalBytes, Files.readAllBytes(backup))
                assertContentEquals(originalBytes, Files.readAllBytes(selected))

                // Mesmo parser Android original, agora alimentado por bytes
                // de um PES/JEF de referência, não por matriz sintética.
                val parsed = DesktopEmbroideryFiles.open(selected)
                assertEquals(format, parsed.format)
                assertTrue(parsed.endFound, "$format sem END")
                assertTrue(parsed.points.count { it.command == StitchCommand.STITCH } > 0)
                assertTrue(parsed.bounds.widthMm > 0f && parsed.bounds.heightMm > 0f)
                val sourcePoints = parsed.points.toList()

                // Pequena translação pelo motor oficial de edição. A
                // referência e o backup permanecem exatamente iguais.
                val edited = MatrixEditor.apply(
                    parsed, EditTransform(offsetXUnits = 10, offsetYUnits = -5)
                )
                assertEquals(sourcePoints.size, edited.points.size)
                assertNotEquals(sourcePoints, edited.points)
                assertTrue(edited.isModified)
                assertEquals(sourcePoints, parsed.points)

                val flp = work.resolve("edicao-de-copia-$format.flp")
                DesktopFlpProjectFiles.saveNew(edited, flp)
                assertTrue(Files.size(flp) > 100)
                val reopened = DesktopFlpProjectFiles.open(flp)
                assertEquals(edited.points, reopened.points)
                assertEquals(edited.bounds, reopened.bounds)
                assertEquals(edited.format, reopened.format)
                assertEquals(edited.threadColors, reopened.threadColors)

                // CREATE_NEW impede sobrescrita por qualquer edição posterior.
                val flpBytes = Files.readAllBytes(flp)
                assertFailsWith<java.nio.file.FileAlreadyExistsException> {
                    DesktopFlpProjectFiles.saveNew(parsed, flp)
                }
                assertContentEquals(flpBytes, Files.readAllBytes(flp))

                // SHA-256 e backup verificam preservação bit a bit.
                assertContentEquals(originalDigest, digest(Files.readAllBytes(fixture)))
                assertContentEquals(originalBytes, Files.readAllBytes(backup))
                assertContentEquals(originalBytes, Files.readAllBytes(selected))
                println(
                    "BROTHER_WINDOWS_REAL_FIXTURE_${format}_SAFE_FLPRT_OK " +
                        "originalBytes=${originalBytes.size} points=${reopened.points.size}"
                )
            } finally {
                Files.list(work).use { children ->
                    children.forEach { Files.deleteIfExists(it) }
                }
                Files.deleteIfExists(work)
            }
        }
    }
}
