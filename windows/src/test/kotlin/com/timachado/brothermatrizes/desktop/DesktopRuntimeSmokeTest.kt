package com.timachado.brothermatrizes.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopRuntimeSmokeTest {
    @Test
    fun `diagnostico offline de motor DST e FLP escreve relatorio de sucesso`() {
        val folder = Files.createTempDirectory("brother-windows-smoke-test-")
        val report = folder.resolve("smoke.txt")
        try {
            DesktopRuntimeSmoke.execute(report)
            val text = Files.readString(report)
            assertTrue(text.startsWith(DesktopRuntimeSmoke.SUCCESS_MARKER))
            assertTrue(text.contains("DST: validado"))
            assertTrue(text.contains("FLP: validado"))
            assertTrue(text.contains("Sobrescrita: bloqueada"))
            assertEquals(1L, Files.list(folder).use { it.count() })
            assertFailsWith<IllegalArgumentException> {
                DesktopRuntimeSmoke.execute(report)
            }
            assertEquals(text, Files.readString(report))
        } finally {
            Files.deleteIfExists(report)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun `recusa relatorios fora do formato e nao grava outros arquivos`() {
        val folder = Files.createTempDirectory("brother-windows-smoke-invalid-")
        try {
            assertFailsWith<IllegalArgumentException> {
                DesktopRuntimeSmoke.execute(folder.resolve("diagnostico.exe"))
            }
            assertTrue(Files.list(folder).use { it.count() } == 0L)
        } finally {
            Files.deleteIfExists(folder)
        }
    }
}
