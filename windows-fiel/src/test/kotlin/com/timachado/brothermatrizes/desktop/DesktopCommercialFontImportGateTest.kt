package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopCommercialFontImportGate
import com.timachado.brothermatrizes.desktop.platform.DesktopFontUsageGateway
import com.timachado.brothermatrizes.desktop.platform.DesktopImportedFontLibrary
import com.timachado.brothermatrizes.desktop.platform.DesktopVerifiedFontSession
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Nenhuma conta, rede, API, chave, Supabase ou projeto de cliente é acessado.
 * Adaptadores em memória verificam o contrato comercial, não o servidor real.
 */
class DesktopCommercialFontImportGateTest {
    private val validReservation = "usage_12345678-1234-1234-1234-123456789abc"

    private class FakeServer : DesktopFontUsageGateway {
        var reserved = 0
        val finalized = mutableListOf<Boolean>()
        var rejectReservation = false
        var failFinalize = false
        var malformedReservation = false

        override suspend fun reserveFont(accessToken: String): String {
            check(accessToken == "token-from-verified-session")
            reserved++
            if (rejectReservation) error("Cota negada pelo servidor")
            return if (malformedReservation) "beta_local_bypass" else
                "usage_12345678-1234-1234-1234-123456789abc"
        }

        override suspend fun finalizeFont(
            accessToken: String, requestKey: String, success: Boolean
        ) {
            check(accessToken == "token-from-verified-session")
            check(requestKey == "usage_12345678-1234-1234-1234-123456789abc")
            if (failFinalize) error("Resposta do servidor indisponível")
            finalized += success
        }
    }

    private val verifiedSession = DesktopVerifiedFontSession {
        "token-from-verified-session"
    }

    private fun fontFixture(): Path {
        val root = Path.of(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts")
        return listOf(
            root.resolve("arial.ttf"),
            root.resolve("segoeui.ttf"),
            Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
        ).firstOrNull { Files.isRegularFile(it) }
            ?: error("Nenhuma fonte TTF de teste disponível no runner.")
    }

    @Test
    fun `sem sessao Windows validada bloqueia antes de ler ou reservar`() = runBlocking {
        val folder = Files.createTempDirectory("bm-gate-deny-")
        val destination = folder.resolve("fonts")
        val fake = FakeServer()
        try {
            val gate = DesktopCommercialFontImportGate(
                DesktopImportedFontLibrary(destination), usage = fake
            )
            val error = assertFailsWith<IllegalStateException> {
                gate.importFont(folder.resolve("inexistente.ttf"))
            }
            assertTrue(error.message.orEmpty().contains("Conta Google"))
            assertEquals(0, fake.reserved)
            assertTrue(!Files.exists(destination))
        } finally {
            Files.deleteIfExists(destination)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun `reserva servidor antes de gravar e confirma apenas importacao nova`() = runBlocking {
        val folder = Files.createTempDirectory("bm-gate-authorized-")
        val destination = folder.resolve("fonts")
        val source = fontFixture()
        val originalBytes = Files.readAllBytes(source)
        val fake = FakeServer()
        try {
            val library = DesktopImportedFontLibrary(destination)
            val gate = DesktopCommercialFontImportGate(library, verifiedSession, fake)
            val first = gate.importFont(source, "Fonte autorizada.ttf")
            val duplicate = gate.importFont(source, "Mesmo conteudo.ttf")
            assertEquals(first.id, duplicate.id)
            assertEquals(2, fake.reserved)
            assertEquals(listOf(true, false), fake.finalized)
            assertEquals(1, library.list().size)
            assertContentEquals(originalBytes, Files.readAllBytes(source))
            assertContentEquals(originalBytes, Files.readAllBytes(Path.of(first.absolutePath)))
        } finally {
            if (Files.isDirectory(destination)) {
                Files.list(destination).use { it.forEach(Files::deleteIfExists) }
                Files.deleteIfExists(destination)
            }
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun `cota negada ou reserva falsa nao cria biblioteca`() = runBlocking {
        for (malformed in listOf(false, true)) {
            val folder = Files.createTempDirectory("bm-gate-reserve-")
            val destination = folder.resolve("fonts")
            val fake = FakeServer().also {
                it.rejectReservation = !malformed
                it.malformedReservation = malformed
            }
            try {
                val gate = DesktopCommercialFontImportGate(
                    DesktopImportedFontLibrary(destination), verifiedSession, fake
                )
                assertFailsWith<IllegalStateException> {
                    gate.importFont(fontFixture())
                }
                assertEquals(1, fake.reserved)
                assertTrue(fake.finalized.isEmpty())
                assertTrue(!Files.exists(destination))
            } finally {
                Files.deleteIfExists(destination)
                Files.deleteIfExists(folder)
            }
        }
    }

    @Test
    fun `falha de formato finaliza com false e nao consome quota`() = runBlocking {
        val folder = Files.createTempDirectory("bm-gate-invalid-")
        val destination = folder.resolve("fonts")
        val bad = folder.resolve("falso.ttf")
        val fake = FakeServer()
        try {
            Files.write(bad, byteArrayOf(1, 2, 3, 4, 5))
            val gate = DesktopCommercialFontImportGate(
                DesktopImportedFontLibrary(destination), verifiedSession, fake
            )
            assertFailsWith<IllegalArgumentException> { gate.importFont(bad) }
            assertEquals(listOf(false), fake.finalized)
            assertTrue(DesktopImportedFontLibrary(destination).list().isEmpty())
        } finally {
            Files.deleteIfExists(bad)
            Files.deleteIfExists(destination)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun `falha ao sincronizar quota retira fonte nova e recusa uso`() = runBlocking {
        val folder = Files.createTempDirectory("bm-gate-sync-")
        val destination = folder.resolve("fonts")
        val fake = FakeServer().also { it.failFinalize = true }
        try {
            val gate = DesktopCommercialFontImportGate(
                DesktopImportedFontLibrary(destination), verifiedSession, fake
            )
            val error = assertFailsWith<IllegalStateException> {
                gate.importFont(fontFixture())
            }
            assertTrue(error.message.orEmpty().contains("sincronizada"))
            assertEquals(1, fake.reserved)
            assertTrue(DesktopImportedFontLibrary(destination).list().isEmpty())
        } finally {
            if (Files.isDirectory(destination)) {
                Files.list(destination).use { it.forEach(Files::deleteIfExists) }
                Files.deleteIfExists(destination)
            }
            Files.deleteIfExists(folder)
        }
    }
}
