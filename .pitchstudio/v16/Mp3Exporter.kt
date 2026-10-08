package br.com.timachado.pitchstudio.audio

import net.qiujuer.lame.Lame
import net.qiujuer.lame.LameOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * MP3 320 kbps com LAME embarcado no APK.
 * Não depende mais de MediaCodec/encoder MP3 do fabricante.
 */
object Mp3Exporter {
    fun isSupported(): Boolean = true

    fun export320(
        project: AudioProject,
        input: InputStream,
        output: OutputStream,
        onProgress: (Int) -> Unit = {}
    ) {
        val channels = project.channels.coerceIn(1, 2)
        val framesPerBlock = 4096
        val totalSamples = framesPerBlock * channels

        val lame = Lame(
            project.sampleRate,
            channels,
            project.sampleRate,
            320,
            Lame.LameQuality.NEAR_BEST
        )

        val encoder = LameOutputStream(lame, output, totalSamples)
        val floatBytes = ByteArray(totalSamples * 4)
        val pcm16 = ShortArray(totalSamples)

        var framesDone = 0L

        try {
            while (true) {
                var got = 0

                while (got < floatBytes.size) {
                    val n = input.read(floatBytes, got, floatBytes.size - got)
                    if (n <= 0) break
                    got += n
                }

                if (got <= 0) break

                val usableBytes = got - (got % 4)
                val bb = ByteBuffer
                    .wrap(floatBytes, 0, usableBytes)
                    .order(ByteOrder.LITTLE_ENDIAN)

                var samples = 0
                while (bb.remaining() >= 4 && samples < pcm16.size) {
                    val value = bb.float.coerceIn(-1f, 1f)
                    pcm16[samples++] = (value * 32767f)
                        .toInt()
                        .coerceIn(-32768, 32767)
                        .toShort()
                }

                if (samples > 0) {
                    encoder.write(pcm16, samples)
                    framesDone += samples / channels

                    onProgress(
                        (framesDone * 100L / max(1L, project.frames))
                            .toInt()
                            .coerceIn(0, 99)
                    )
                }

                if (got < floatBytes.size) break
            }

            encoder.flush()
            output.flush()
            onProgress(100)
        } finally {
            try { lame.close() } catch (_: Throwable) {}
        }
    }
}
