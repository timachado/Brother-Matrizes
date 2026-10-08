package br.com.timachado.pitchstudio.audio

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class Mp3ExporterRegressionTest {
    private fun pcm(frames: Int, rate: Int = 44100): ByteArray {
        val bytes = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { frame ->
            bytes.putFloat((.4 * sin(2.0 * PI * 440.0 * frame / rate)).toFloat())
        }
        return bytes.array()
    }
    private fun project(frames: Int): AudioProject =
        AudioProject("Teste MP3", File("unused-input.f32"), 44100, 1, frames.toLong(),
            .4f, floatArrayOf(.4f))

    @Test fun encodesFiniteAudioAndCompletesProgress() {
        val frames = 1152 * 3 + 37
        val out = ByteArrayOutputStream()
        val updates = mutableListOf<Int>()
        Mp3Exporter.export320(project(frames), ByteArrayInputStream(pcm(frames)), out) {
            updates.add(it)
        }
        val encoded=out.toByteArray()
        assertTrue("MP3 vazio", encoded.size > 100)
        assertEquals(100, updates.last())
        // ID3 metadata is disabled; an MPEG audio frame sync must exist.
        assertTrue("Sem frame MPEG", (0 until encoded.size - 1).any { i ->
            (encoded[i].toInt() and 255) == 255 &&
            (encoded[i + 1].toInt() and 0xE0) == 0xE0
        })
    }

    @Test fun truncatedPcmMustFailInsteadOfReportingSuccess() {
        val frames = 1152 * 3 + 37
        val updates = mutableListOf<Int>()
        try {
            Mp3Exporter.export320(project(frames), ByteArrayInputStream(pcm(frames - 20)),
                ByteArrayOutputStream()) { updates.add(it) }
            fail("MP3 não pode finalizar com quadros faltando")
        } catch (_: EOFException) { }
        assertFalse("Não deve informar 100% quando a origem falha", updates.contains(100))
    }

    @Test fun stereoOrEmptyMetadataIsNotSilentlyCorrected() {
        try {
            Mp3Exporter.export320(
                AudioProject("inválido", File("unused"), 44100, 3, 20, 0f, floatArrayOf()),
                ByteArrayInputStream(ByteArray(240)), ByteArrayOutputStream()
            )
            fail("Canais inválidos devem ser recusados")
        } catch (_: IllegalArgumentException) { }
    }
}
