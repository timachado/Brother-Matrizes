package com.timachado.brothermatrizes.core.project

import com.timachado.brothermatrizes.core.embroidery.ColorLayerEdit
import com.timachado.brothermatrizes.core.embroidery.ManualSatinSection
import com.timachado.brothermatrizes.core.embroidery.RasterImagePixels
import com.timachado.brothermatrizes.core.embroidery.RasterLayerStitchMode
import com.timachado.brothermatrizes.core.embroidery.SatinRailPair
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterEditSatinBackupTest {
    private fun project(title: String, joined: Boolean = false): RasterEditProject {
        val w = 42
        val h = 42
        val pixels = IntArray(w * h) { 0xFF070707.toInt() }
        for (y in 9..33) for (x in 8..14) {
            pixels[y * w + x] = 0xFFE88A20.toInt()
        }
        val first = ManualSatinSection((0..5).map {
            SatinRailPair(.18f, .15f + it * .07f, .26f, .15f + it * .07f)
        })
        val second = ManualSatinSection(
            rails = first.rails.map { it.copy(ay = it.ay + .40f, by = it.by + .40f) },
            connectToPrevious = joined
        )
        return RasterEditProject(
            title = title,
            image = RasterImagePixels(w, h, pixels, "Arte Satin"),
            widthMm = 42f, maxColors = 2, outputFormat = "PES",
            hoopProfile = null,
            layers = listOf(
                ColorLayerEdit(
                    sourceIndex = 0,
                    stitchMode = RasterLayerStitchMode.MANUAL_SATIN,
                    manualSections = listOf(first, second)
                )
            )
        )
    }

    @Test fun v2PreservesJoinFlagAndBackupRestoresSeparateDocuments() {
        val a = project("Sabrina", joined = true)
        val b = project("Logo")
        val bytes = RasterEditProjectBackupCodec.encode(listOf(a, b))
        val decoded = RasterEditProjectBackupCodec.decode(bytes)
        assertEquals(listOf("Sabrina", "Logo"), decoded.map { it.title })
        assertTrue(decoded[0].layers[0].manualSections[1].connectToPrevious)
        assertFalse(decoded[1].layers[0].manualSections[1].connectToPrevious)
        assertTrue(a.image.argb.contentEquals(decoded[0].image.argb))
        val target = Files.createTempDirectory("satin-restore").toFile()
        try {
            assertEquals(2, RasterEditProjectBackupStore.restoreAll(target, bytes).getOrThrow())
            assertEquals(2, RasterEditFileStore.list(target).getOrThrow().size)
            assertTrue(RasterEditFileStore.list(target).getOrThrow()
                .any { it.title == "Sabrina" })
        } finally {
            target.listFiles()?.forEach { it.delete() }
            target.delete()
        }
    }

    @Test fun restoringNeverOverwritesExistingProject() {
        val target = Files.createTempDirectory("satin-preserve").toFile()
        try {
            val initial = RasterEditFileStore.save(target, project("Original"))
                .getOrThrow()
            val count = RasterEditProjectBackupStore.restoreAll(
                target,
                RasterEditProjectBackupCodec.encode(listOf(project("Importado")))
            ).getOrThrow()
            assertEquals(1, count)
            assertEquals("Original", RasterEditFileStore.load(target, initial)
                .getOrThrow().title)
            assertEquals(2, RasterEditFileStore.list(target).getOrThrow().size)
        } finally {
            target.listFiles()?.forEach { it.delete() }
            target.delete()
        }
    }

    @Test fun maliciousPathDuplicateOrCorruptedZipFailsWithoutWriting() {
        fun archive(vararg namesAndContents: Pair<String, ByteArray>): ByteArray {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                for ((name, bytes) in namesAndContents) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            return out.toByteArray()
        }
        val header = "BROTHER_MATRIZES_SATIN_BACKUP_V1\n1\n".toByteArray()
        val content = RasterEditProjectCodec.encode(project("Teste"))
        val traversal = archive(
            "brother-matrizes-satin-backup.txt" to header,
            "../fora.bmrail" to content
        )
        val corrupt = content.copyOf().apply { this[20] = (this[20].toInt() xor 0x5A).toByte() }
        val tampered = archive(
            "brother-matrizes-satin-backup.txt" to header,
            "projects/0000.bmrail" to corrupt
        )
        val target = Files.createTempDirectory("satin-safety").toFile()
        try {
            assertTrue(RasterEditProjectBackupStore.restoreAll(target, traversal).isFailure)
            assertTrue(RasterEditProjectBackupStore.restoreAll(target, tampered).isFailure)
            assertEquals(0, RasterEditFileStore.list(target).getOrThrow().size)
        } finally {
            target.listFiles()?.forEach { it.delete() }
            target.delete()
        }
    }

    @Test fun v1ProjectsRemainReadableWithConnectionDefaultOff() {
        val old = project("Projeto anterior")
        // Arquivo da fase 7: mesmos campos, mas VERSION=1 e sem boolean da conexão.
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(0x42524D52)
            out.writeInt(1)
            out.writeUTF(old.title)
            out.writeUTF(old.image.label)
            out.writeInt(old.image.width)
            out.writeInt(old.image.height)
            old.image.argb.forEach { out.writeInt(it) }
            out.writeFloat(old.widthMm)
            out.writeInt(old.maxColors)
            out.writeUTF(old.outputFormat)
            out.writeUTF("")
            out.writeInt(old.layers.size)
            for (layer in old.layers) {
                out.writeInt(layer.sourceIndex)
                out.writeBoolean(layer.enabled)
                out.writeBoolean(layer.threadColor != null)
                layer.threadColor?.let { out.writeInt(it) }
                out.writeBoolean(layer.rowSpacingMm != null)
                layer.rowSpacingMm?.let { out.writeFloat(it) }
                out.writeUTF(layer.stitchMode.name)
                out.writeFloat(layer.satinPullCompensationMm)
                out.writeBoolean(layer.satinCenterUnderlay)
                out.writeInt(layer.manualSections.size)
                for (section in layer.manualSections) {
                    out.writeInt(section.rails.size)
                    for (r in section.rails) {
                        out.writeFloat(r.ax)
                        out.writeFloat(r.ay)
                        out.writeFloat(r.bx)
                        out.writeFloat(r.by)
                    }
                }
            }
        }
        val payload = buffer.toByteArray()
        val crc = CRC32().apply { update(payload) }.value
        val bytes = ByteArrayOutputStream().apply {
            write(payload)
            DataOutputStream(this).use { it.writeLong(crc) }
        }.toByteArray()
        val restored = RasterEditProjectCodec.decode(bytes)
        assertEquals(old.title, restored.title)
        assertTrue(restored.layers.single().manualSections.all { !it.connectToPrevious })
        val v2 = RasterEditProjectCodec.decode(RasterEditProjectCodec.encode(restored))
        assertEquals(restored.layers, v2.layers)
    }
}
