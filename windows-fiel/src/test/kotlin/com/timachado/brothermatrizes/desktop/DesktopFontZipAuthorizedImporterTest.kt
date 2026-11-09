package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopCommercialFontImportGate
import com.timachado.brothermatrizes.desktop.platform.DesktopFontUsageGateway
import com.timachado.brothermatrizes.desktop.platform.DesktopFontZipAuthorizedImporter
import com.timachado.brothermatrizes.desktop.platform.DesktopFontZipPreview
import com.timachado.brothermatrizes.desktop.platform.DesktopImportedFontLibrary
import com.timachado.brothermatrizes.desktop.platform.DesktopVerifiedFontSession
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Comparator
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** On-disk but offline: synthetic ZIP and a Windows system font only.
 * No Supabase, account, actual quota, production source or customer files.
 */
class DesktopFontZipAuthorizedImporterTest {
    private fun fixture(): Path {
        val base = Path.of(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts")
        return listOf("arial.ttf", "segoeui.ttf", "tahoma.ttf")
            .map(base::resolve)
            .firstOrNull(Files::isRegularFile)
            ?: error("Fonte Windows TTF de QA indisponível.")
    }

    private fun writeZip(destination: Path, font: Path, prefix: String = "fonts/") {
        ZipOutputStream(Files.newOutputStream(destination,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
        )).use { output ->
            output.putNextEntry(ZipEntry(prefix + font.fileName.toString()))
            Files.newInputStream(font).use { input -> input.copyTo(output) }
            output.closeEntry()
        }
    }

    private class FakeQuota : DesktopFontUsageGateway {
        var reserved = 0
        val finalizations = mutableListOf<Boolean>()
        override suspend fun reserveFont(accessToken: String): String {
            assertEquals("synthetic-google-account-token", accessToken)
            reserved++
            return "usage_12345678-1234-1234-1234-123456789abc"
        }
        override suspend fun finalizeFont(
            accessToken: String, requestKey: String, success: Boolean
        ) {
            assertEquals("usage_12345678-1234-1234-1234-123456789abc", requestKey)
            finalizations += success
        }
    }

    @Test
    fun singleSelectedFontImportsThroughExistingAuthorizationGate() = runBlocking {
        val root = Files.createTempDirectory("bm-font-zip-import-")
        try {
            val source = root.resolve("fontes.zip")
            writeZip(source, fixture())
            val preview = DesktopFontZipPreview.inspect(source)
            assertEquals(1, preview.fontCount)
            assertTrue(preview.hasSha256)
            assertNotNull(preview.archiveSha256)

            val quota = FakeQuota()
            val library = DesktopImportedFontLibrary(root.resolve("library"))
            val gate = DesktopCommercialFontImportGate(
                library, DesktopVerifiedFontSession { "synthetic-google-account-token" },
                quota
            )
            val saved = DesktopFontZipAuthorizedImporter.importOne(
                source, preview, preview.fonts.single(), gate
            )
            assertTrue(saved.displayName.isNotBlank())
            assertEquals(1, library.list().size)
            assertEquals(1, quota.reserved)
            assertEquals(listOf(true), quota.finalizations)
            assertTrue(Files.isRegularFile(source))
        } finally {
            Files.walk(root).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach {
                    Files.deleteIfExists(it)
                }
            }
        }
    }

    @Test
    fun replacedZipAndForgedSelectionNeverConsumeQuota() = runBlocking {
        val root = Files.createTempDirectory("bm-font-zip-rejected-")
        try {
            val archive = root.resolve("fontes.zip")
            val font = fixture()
            writeZip(archive, font)
            val preview = DesktopFontZipPreview.inspect(archive)
            val quota = FakeQuota()
            val gate = DesktopCommercialFontImportGate(
                DesktopImportedFontLibrary(root.resolve("library")),
                DesktopVerifiedFontSession { "synthetic-google-account-token" },
                quota
            )
            assertFailsWith<Exception> {
                DesktopFontZipAuthorizedImporter.importOne(
                    archive, preview, preview.fonts.single().copy(index = 999), gate
                )
            }
            // Exact bytes reviewed by the user are immutable for this action.
            writeZip(archive, font, prefix = "alternate/")
            assertFailsWith<Exception> {
                DesktopFontZipAuthorizedImporter.importOne(
                    archive, preview, preview.fonts.single(), gate
                )
            }
            assertEquals(0, quota.reserved)
            assertTrue(quota.finalizations.isEmpty())
        } finally {
            Files.walk(root).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach {
                    Files.deleteIfExists(it)
                }
            }
        }
    }
}
