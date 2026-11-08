package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.desktop.platform.DesktopCommercialFontImportGate
import com.timachado.brothermatrizes.desktop.platform.DesktopFontUsageGateway
import com.timachado.brothermatrizes.desktop.platform.DesktopImportedFontLibrary
import com.timachado.brothermatrizes.desktop.platform.DesktopImportedFontSatinService
import com.timachado.brothermatrizes.desktop.platform.DesktopVerifiedFontSession
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Offline Windows font fixture; NEVER calls Supabase or charges any quota. */
class DesktopImportedFontSatinServiceTest {
    private fun font(): Path {
        val fonts = Path.of(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts")
        return listOf(
            fonts.resolve("arial.ttf"),
            fonts.resolve("segoeui.ttf"),
            Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
        ).firstOrNull { Files.isRegularFile(it) }
            ?: error("Fonte TTF de QA não encontrada.")
    }

    private val receipt = "usage_12345678-1234-1234-1234-123456789abc"
    private class FakeQuota : DesktopFontUsageGateway {
        var reserve = 0
        val finalized = mutableListOf<Boolean>()
        var denied = false
        override suspend fun reserveFont(accessToken: String): String {
            check(accessToken == "verified-qa")
            reserve++
            if (denied) error("Cota do Supabase recusada")
            return "usage_12345678-1234-1234-1234-123456789abc"
        }
        override suspend fun finalizeFont(
            accessToken: String, requestKey: String, success: Boolean
        ) {
            check(accessToken == "verified-qa")
            check(requestKey == "usage_12345678-1234-1234-1234-123456789abc")
            finalized += success
        }
    }

    private fun options(text: String = "Maria") = TextMatrixOptions(
        text = text, heightMm = 12f, style = TextStitchStyle.SATIN,
        outputFormat = "DST", hoopProfile = null, enforceHoop = false
    )

    private fun cleanup(root: Path) {
        Files.walk(root).use { stream ->
            stream.sorted(java.util.Comparator.reverseOrder())
                .forEach(Files::deleteIfExists)
        }
    }

    @Test
    fun originalAndroidSatinProducesValidEmbroideryAndDstInMemoryAfterFakeServerPermit() =
        runBlocking {
            val root = Files.createTempDirectory("bm-font-satin-allowed-")
            val quota = FakeQuota()
            try {
                val library = DesktopImportedFontLibrary(root.resolve("fonts"))
                val gate = DesktopCommercialFontImportGate(
                    library, DesktopVerifiedFontSession { "verified-qa" }, quota
                )
                val result = DesktopImportedFontSatinService(gate)
                    .importAndGenerate(font(), "Maria", options())
                val design = result.design
                assertEquals("Maria", design.label)
                assertEquals("DST", design.format)
                assertTrue(design.stitchCount > 100)
                assertTrue(design.endFound)
                assertEquals(StitchCommand.END, design.points.last().command)
                assertEquals(design.points.size - 1,
                    design.points.count { it.command != StitchCommand.END })
                assertEquals(1, library.list().size)
                assertTrue(design.bounds.widthMm > 0f && design.bounds.heightMm > 0f)
                assertEquals(1, quota.reserve)
                assertEquals(listOf(true), quota.finalized)
                val converted = MatrixConverter.convert(design, "DST").getOrThrow()
                assertTrue(converted.bytes.size > 512)
                // Repeat must be idempotent and report duplicate (no second charge).
                val again = DesktopImportedFontSatinService(gate)
                    .importAndGenerate(font(), "Maria", options())
                assertEquals(design.points, again.design.points)
                assertEquals(listOf(true, false), quota.finalized)
                assertEquals(2, quota.reserve)
            } finally {
                cleanup(root)
            }
        }

    @Test
    fun noVerifiedSessionBlocksBothImportAndSatinBeforeDiskOrQuota() = runBlocking {
        val root = Files.createTempDirectory("bm-font-satin-denied-")
        val quota = FakeQuota()
        try {
            val gate = DesktopCommercialFontImportGate(
                DesktopImportedFontLibrary(root.resolve("fonts")), usage = quota
            )
            assertFailsWith<IllegalStateException> {
                DesktopImportedFontSatinService(gate)
                    .importAndGenerate(font(), "Maria", options())
            }
            assertEquals(0, quota.reserve)
            assertFalse(Files.exists(root.resolve("fonts")))
        } finally {
            cleanup(root)
        }
    }

    @Test
    fun invalidTextOrUnsupportedTransformRejectedBeforeQuotaReservation() = runBlocking {
        val root = Files.createTempDirectory("bm-font-satin-invalid-")
        val quota = FakeQuota()
        try {
            val gate = DesktopCommercialFontImportGate(
                DesktopImportedFontLibrary(root.resolve("fonts")),
                DesktopVerifiedFontSession { "verified-qa" }, quota
            )
            val service = DesktopImportedFontSatinService(gate)
            for (value in listOf("", " ", "x".repeat(25), "Maria\nTeste")) {
                assertFailsWith<IllegalArgumentException> {
                    service.importAndGenerate(font(), value, options())
                }
            }
            assertFailsWith<IllegalArgumentException> {
                service.importAndGenerate(
                    font(), "Maria", options().copy(rotationDegrees = 45f)
                )
            }
            assertFailsWith<IllegalArgumentException> {
                service.importAndGenerate(
                    font(), "Maria", options().copy(satinDensityMm = 0.0f)
                )
            }
            assertEquals(0, quota.reserve)
            assertFalse(Files.exists(root.resolve("fonts")))
        } finally {
            cleanup(root)
        }
    }

    @Test
    fun deniedServerQuotaNeverAllowsGenerationOrImportsFont() = runBlocking {
        val root = Files.createTempDirectory("bm-font-satin-server-deny-")
        val quota = FakeQuota().also { it.denied = true }
        try {
            val gate = DesktopCommercialFontImportGate(
                DesktopImportedFontLibrary(root.resolve("fonts")),
                DesktopVerifiedFontSession { "verified-qa" }, quota
            )
            assertFailsWith<IllegalStateException> {
                DesktopImportedFontSatinService(gate)
                    .importAndGenerate(font(), "Maria", options())
            }
            assertEquals(1, quota.reserve)
            assertTrue(quota.finalized.isEmpty())
            assertFalse(Files.exists(root.resolve("fonts")))
        } finally {
            cleanup(root)
        }
    }
}
