package br.com.timachado.pitchstudio

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StemWavExporterTest {
    private fun buildFloatPcm(file: File, floats: FloatArray) {
        val b = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        floats.forEach { b.putFloat(it) }
        file.writeBytes(b.array())
    }

    @Test fun writesValidMonoWavIncludingSamples() {
        val source=File.createTempFile("stem_export_source_",".f32")
        val target=File.createTempFile("stem_export_target_",".wav")
        try {
            buildFloatPcm(source, floatArrayOf(0f, 0.5f, -1f, 1f))
            val written=StemWavExporter.exportToFile(source, 44100, 1, 4, target)
            assertEquals(52L,written)
            val bytes=target.readBytes()
            assertEquals("RIFF", String(bytes,0,4))
            assertEquals("WAVE", String(bytes,8,4))
            assertEquals("fmt ", String(bytes,12,4))
            assertEquals("data", String(bytes,36,4))
            val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(44,b.getInt(4))
            assertEquals(1,b.getShort(20).toInt())
            assertEquals(1,b.getShort(22).toInt())
            assertEquals(44100,b.getInt(24))
            assertEquals(88200,b.getInt(28))
            assertEquals(8,b.getInt(40))
            assertEquals(0,b.getShort(44).toInt())
            assertEquals(16384,b.getShort(46).toInt())
            assertEquals(-32767,b.getShort(48).toInt())
            assertEquals(32767,b.getShort(50).toInt())
        } finally { source.delete(); target.delete() }
    }

    @Test fun stereoAndInvalidFloatsAreSafe() {
        val source=File.createTempFile("stem_export_stereo_",".f32")
        val target=File.createTempFile("stem_export_stereo_",".wav")
        try {
            buildFloatPcm(source, floatArrayOf(Float.NaN,Float.POSITIVE_INFINITY,
                -0.5f, 0.5f))
            StemWavExporter.exportToFile(source, 16000, 2, 2, target)
            val b=ByteBuffer.wrap(target.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(2,b.getShort(22).toInt())
            assertEquals(64000,b.getInt(28))
            assertEquals(0,b.getShort(44).toInt())
            assertEquals(0,b.getShort(46).toInt())
            assertTrue(b.getShort(48).toInt() < 0)
            assertTrue(b.getShort(50).toInt() > 0)
        } finally { source.delete(); target.delete() }
    }

    @Test fun incompleteSourceDoesNotLeaveFile() {
        val source=File.createTempFile("stem_export_incomplete_",".f32")
        val target=File.createTempFile("stem_export_incomplete_",".wav")
        try {
            buildFloatPcm(source,floatArrayOf(0.1f))
            try {
                StemWavExporter.exportToFile(source,44100,1,2,target)
                fail("Source truncation must be rejected")
            } catch (_: IllegalArgumentException) {}
            assertFalse(File(target.parentFile,target.name+".part").exists())
        } finally { source.delete(); target.delete() }
    }
}
