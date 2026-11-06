package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopMachineTransferServiceTest {
    private val hoop = HoopProfile.H100X100
    private fun matrix() = DesktopMatrixService.createName("QA", 12f)

    @Test
    fun `PES válido é salvo como arquivo novo e conferido por hash`() {
        val root = Files.createTempDirectory("brother-transfer-qa-")
        val dest = root.resolve("QA.pes")
        try {
            val input = matrix()
            val before = input.current.points.toList()
            assertTrue(DesktopMachineTransferService.validate(input, "PES", hoop).ready)
            val receipt = DesktopMachineTransferService.saveNew(
                input, "PES", hoop, dest
            )
            assertEquals(dest, receipt.destination)
            assertEquals("PES", receipt.format)
            val bytes = Files.readAllBytes(dest)
            assertEquals(receipt.bytes, bytes.size)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            assertEquals(hash, receipt.sha256)
            val result = DesktopMatrixService.open(dest.fileName.toString(), bytes)
            assertTrue(result.current.endFound && result.current.stitchCount > 0)
            assertEquals(before, input.current.points)
        } finally {
            Files.deleteIfExists(dest)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun `arquivo existente nunca é sobrescrito`() {
        val root = Files.createTempDirectory("brother-transfer-protect-")
        val dest = root.resolve("protegido.pes")
        try {
            val original = "matriz anterior".toByteArray()
            Files.write(dest, original)
            assertFailsWith<IllegalArgumentException> {
                DesktopMachineTransferService.saveNew(
                    matrix(), "PES", hoop, dest
                )
            }
            assertTrue(original.contentEquals(Files.readAllBytes(dest)))
        } finally {
            Files.deleteIfExists(dest)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun `impede formato desconhecido e extensão divergente`() {
        val root = Files.createTempDirectory("brother-transfer-format-")
        try {
            assertFalse(DesktopMachineTransferService.validate(
                matrix(), "XXX", hoop
            ).ready)
            assertFailsWith<IllegalArgumentException> {
                DesktopMachineTransferService.saveNew(
                    matrix(), "XXX", hoop, root.resolve("falso.xxx")
                )
            }
            assertFailsWith<IllegalArgumentException> {
                DesktopMachineTransferService.saveNew(
                    matrix(), "PES", hoop, root.resolve("falso.dst")
                )
            }
            assertFalse(Files.exists(root.resolve("falso.dst")))
        } finally {
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun `DST gera avisos oficiais sobre cores sem tocar na matriz`() {
        val input = matrix()
        val before = input.current.points.toList()
        val validation = DesktopMachineTransferService.validate(
            input, "DST", hoop
        )
        assertTrue(validation.ready)
        assertTrue(validation.warnings.any { it.message.contains("cores") })
        assertEquals(before, input.current.points)
    }
}
