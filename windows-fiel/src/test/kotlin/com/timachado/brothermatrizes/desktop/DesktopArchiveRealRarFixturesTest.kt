package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveInventory
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Genuine upstream RAR4 and RAR5 fixtures from junrar's *public test suite*.
 *
 * This class does not download anything and never extracts payloads.
 * A dedicated Windows CI job downloads from a pinned upstream commit and
 * verifies Git blob SHA-1 before enabling this test. Local developers may
 * run the rest of the suite without a network or any RAR fixture.
 */
class DesktopArchiveRealRarFixturesTest {
    @Test
    fun genuineRar4AndRar5FromUpstreamAreListedWithoutExtracting() {
        val fixtureDir = System.getenv("BROTHER_RAR_FIXTURE_DIR")
        assumeTrue(!fixtureDir.isNullOrBlank(), "Upstream RAR fixture suite requires an explicit CI folder")
        val root = Path.of(checkNotNull(fixtureDir)).toAbsolutePath().normalize()
        for (name in listOf("rar4.rar", "rar5.rar")) {
            val file = root.resolve(name)
            require(Files.isRegularFile(file)) {
                "RAR fixture missing: $name. Do not report this QA as completed."
            }
            val before = Files.readAllBytes(file)
            val preview = DesktopArchiveInventory.inspect(file)
            assertEquals(DesktopArchiveInventory.Format.RAR, preview.format)
            // The pinned junrar fixtures contain two text files of 7 bytes,
            // not embroidery files. Their presence proves RAR parsing; it
            // does NOT imply matrix importing, password support or extraction.
            assertEquals(2, preview.scannedCount, "$name: expected two upstream text entries")
            assertEquals(0, preview.matrixCount)
            assertEquals(0, preview.fontCount)
            assertEquals(2, preview.incompatibleCount)
            assertEquals(14L, preview.uncompressedBytes)
            assertContentEquals(before, Files.readAllBytes(file))
            assertFalse(Files.exists(root.resolve("FILE1.TXT")))
            assertFalse(Files.exists(root.resolve("FILE2.TXT")))
        }
        Files.list(root).use { files ->
            assertEquals(
                setOf("rar4.rar", "rar5.rar"),
                files.map { it.fileName.toString() }.toList().toSet(),
                "The inspection must not create any extracted files."
            )
        }
    }
}
