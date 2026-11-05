package com.timachado.brothermatrizes.desktop

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopQaFixturePackTest {
    @Test
    fun `gera matrizes sintéticas em pasta nova com hashes válidos`() {
        val parent = Files.createTempDirectory("bm-windows-fixture-test-")
        val folder = parent.resolve("amostras")
        try {
            val files = DesktopQaFixturePack.generateNew(folder)
            assertEquals(9, files.size)
            assertEquals(
                DesktopQaFixturePack.matrixNames.toSet() +
                    setOf("LEIA-ME-QA.txt", "SHA256SUMS.txt"),
                files.map { it.fileName.toString() }.toSet()
            )
            for (entry in DesktopQaFixturePack.matrixNames) {
                assertTrue(Files.size(folder.resolve(entry)) > 0L)
            }
            for (name in DesktopQaFixturePack.matrixNames.filter { it.endsWith(".dst") ||
                    it.endsWith(".pes") || it.endsWith(".jef") }) {
                val reopened = DesktopMatrixService.openFromFile(folder.resolve(name))
                assertTrue(reopened.current.endFound && reopened.current.stitchCount > 0)
            }
            assertTrue(DesktopProjectService.open(folder.resolve("QA_PROJETO_ANDROID.flp"))
                .current.stitchCount > 0)
            assertTrue(DesktopLetterProjectBundle.open(
                folder.resolve("QA_LETRAS_CAMADAS.flp")
            ).session.current.editableIndices.isNotEmpty())
            val lines = Files.readAllLines(folder.resolve("SHA256SUMS.txt"))
            assertEquals(8, lines.size)
            for (line in lines) {
                assertTrue(line.matches(Regex("[a-f0-9]{64}  [a-zA-Z0-9_.-]+")))
                val digest = line.substringBefore("  ")
                val fileName = line.substringAfter("  ")
                val computed = MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(folder.resolve(fileName)))
                    .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
                assertEquals(digest, computed)
            }
            assertFailsWith<FileAlreadyExistsException> {
                DesktopQaFixturePack.generateNew(folder)
            }
            assertEquals(9L, Files.list(folder).use { it.count() })
        } finally {
            if (Files.exists(folder)) {
                Files.list(folder).use { entries ->
                    entries.forEach { Files.deleteIfExists(it) }
                }
                Files.deleteIfExists(folder)
            }
            Files.deleteIfExists(parent)
        }
    }
}
