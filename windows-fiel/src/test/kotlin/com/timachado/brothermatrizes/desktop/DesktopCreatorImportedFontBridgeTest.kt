package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.desktop.platform.DesktopCommercialFontImportGate
import com.timachado.brothermatrizes.desktop.platform.DesktopFontUsageGateway
import com.timachado.brothermatrizes.desktop.platform.DesktopImportedFontLibrary
import com.timachado.brothermatrizes.desktop.platform.DesktopSavedFontBridge
import com.timachado.brothermatrizes.desktop.platform.DesktopVerifiedFontSession
import com.timachado.brothermatrizes.desktop.platform.DesktopImportedFont
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Comparator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** No account, real network or client data: fake permit then original Satin. */
class DesktopCreatorImportedFontBridgeTest {
    private val receipt = "usage_12345678-1234-1234-1234-123456789abc"
    private fun fixtureFont(): Path {
        val folder = Path.of(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts")
        return listOf(
            folder.resolve("arial.ttf"), folder.resolve("segoeui.ttf"),
            Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
        ).firstOrNull { Files.isRegularFile(it) }
            ?: error("Fonte de QA do runner não encontrada.")
    }

    private fun options() = TextMatrixOptions(
        text = "Maria", heightMm = 12f, style = TextStitchStyle.SATIN,
        outputFormat = "DST", hoopProfile = null, enforceHoop = false
    )

    private fun clean(root: Path) {
        // Windows Skia/HarfBuzz can keep a test font mapped until the JVM
        // exits, even after closing typefaces. FileSystemException in fixture
        // cleanup must not hide the real test assertions. Cleanup remains
        // strictly scoped to our fresh temporary QA directory.
        Files.walk(root).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { target ->
                try {
                    Files.deleteIfExists(target)
                } catch (locked: java.nio.file.FileSystemException) {
                    // No business data: a generated synthetic fixture.
                    // The JVM will retry after native font handles are closed.
                    target.toFile().deleteOnExit()
                }
            }
        }
    }

    @Test
    fun onlyPreviouslyAuthorizedAndIntactLocalFontAppearsInAndroidCreator() = runBlocking {
        val root = Files.createTempDirectory("brother-imported-font-bridge-")
        val fonts = root.resolve("fonts")
        var reserved = 0
        var finalized = 0
        val usage = object : DesktopFontUsageGateway {
            override suspend fun reserveFont(accessToken: String): String {
                check(accessToken == "verified-fixture")
                reserved++
                return receipt
            }
            override suspend fun finalizeFont(
                accessToken: String, requestKey: String, success: Boolean
            ) {
                check(requestKey == receipt && success)
                finalized++
            }
        }
        try {
            // Before any commercial gate, neither listing nor generation may
            // create a local font or accept an invented filename.
            assertTrue(DesktopSavedFontBridge.list(fonts).isEmpty())
            assertFalse(Files.exists(fonts))
            val invented = DesktopImportedFont(
                "Inventada-aaaaaaaaaa.ttf", "Inventada"
            )
            assertTrue(DesktopSavedFontBridge.generate(
                invented, "Maria", options(), "nome", fonts
            ).isFailure)
            assertFalse(Files.exists(fonts))

            // Same server-verified import_font reservation pattern as Android.
            val gate = DesktopCommercialFontImportGate(
                DesktopImportedFontLibrary(fonts),
                DesktopVerifiedFontSession { "verified-fixture" }, usage
            )
            val saved = gate.importFont(fixtureFont())
            assertEquals(1, reserved)
            assertEquals(1, finalized)
            val listed = DesktopSavedFontBridge.list(fonts)
            assertEquals(listOf(saved.id), listed.map { it.id })
            assertEquals(saved.displayName, listed.single().displayName)
            val design = DesktopSavedFontBridge.generate(
                listed.single(), "Maria", options(), "nome", fonts
            ).getOrThrow()
            assertTrue(design.stitchCount > 100)
            assertTrue(design.endFound)
            assertEquals(1, reserved) // Using an EXISTING font never imports again.
            assertEquals(1, finalized)

            // Any substitution after registration is rejected by SHA-256.
            Files.write(
                Path.of(saved.absolutePath), byteArrayOf(0, 1, 0, 0),
                StandardOpenOption.TRUNCATE_EXISTING
            )
            assertTrue(DesktopSavedFontBridge.list(fonts).isEmpty())
            assertTrue(DesktopSavedFontBridge.generate(
                listed.single(), "Maria", options(), "nome", fonts
            ).isFailure)
        } finally {
            clean(root)
        }
    }

    @Test
    fun importedRunningOrInvalidTextCannotBypassSatinEngine() {
        val root = Files.createTempDirectory("brother-imported-font-block-")
        try {
            val forged = DesktopImportedFont("fonte-aaaaaaaaaa.ttf", "Fonte")
            assertTrue(DesktopSavedFontBridge.generate(
                forged, "Maria", options().copy(style = TextStitchStyle.RUNNING),
                "nome", root
            ).isFailure)
            assertTrue(DesktopSavedFontBridge.generate(
                forged, "Maria\nTeste", options(), "nome", root
            ).isFailure)
        } finally {
            clean(root)
        }
    }
}
