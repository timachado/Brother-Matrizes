package br.com.timachado.pitchstudio.audio

import io.vacco.libmp3lame.Jlame
import io.vacco.libmp3lame.Jlame_global_flags
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * MP3 320 kbps usando JLAME puro Java.
 *
 * Não carrega bibliotecas .so e portanto não depende de ABI,
 * libstdc++, NDK ou codec MP3 do fabricante.
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
        val sampleRate = project.sampleRate

        require(sampleRate in setOf(
            8000, 11025, 12000,
            16000, 22050, 24000,
            32000, 44100, 48000
        )) {
            "A exportação MP3 suporta taxas de 8 kHz a 48 kHz compatíveis com MPEG. Taxa atual: $sampleRate Hz."
        }

        val flags: Jlame_global_flags =
            Jlame.lame_init()
                ?: error("Não foi possível iniciar o encoder MP3.")

        try {
            if (flags.lame_set_num_channels(channels)) {
                error("Quantidade de canais não suportada pelo encoder MP3.")
            }

            check(flags.lame_set_out_samplerate(sampleRate) == 0) {
                "Taxa de amostragem não suportada pelo MP3: $sampleRate Hz."
            }

            flags.lame_set_VBR(Jlame.vbr_off)
            flags.lame_set_brate(320)
            flags.lame_set_quality(2)
            flags.lame_set_write_id3tag_automatic(false)

            check(Jlame.lame_init_params(flags) >= 0) {
                "Falha ao configurar o encoder MP3."
            }

            val framesPerBlock = 1152
            val floatBytes = ByteArray(framesPerBlock * channels * 4)
            val pcm = ShortArray(framesPerBlock * channels)
            val mono = ShortArray(framesPerBlock)
            val mp3Buffer = ByteArray(16 * 1024)

            var framesDone = 0L

            while (true) {
                var got = 0

                while (got < floatBytes.size) {
                    val n = input.read(
                        floatBytes,
                        got,
                        floatBytes.size - got
                    )
                    if (n <= 0) break
                    got += n
                }

                if (got <= 0) break

                val usableBytes = got - (got % 4)
                val bb = ByteBuffer
                    .wrap(floatBytes, 0, usableBytes)
                    .order(ByteOrder.LITTLE_ENDIAN)

                var sampleCount = 0
                while (bb.remaining() >= 4 && sampleCount < pcm.size) {
                    val value = bb.float.coerceIn(-1f, 1f)
                    pcm[sampleCount++] = (value * 32767f)
                        .toInt()
                        .coerceIn(-32768, 32767)
                        .toShort()
                }

                val frames = sampleCount / channels
                if (frames <= 0) break

                val encoded = if (channels == 2) {
                    Jlame.lame_encode_buffer_interleaved(
                        flags,
                        pcm,
                        frames,
                        mp3Buffer,
                        0,
                        mp3Buffer.size
                    )
                } else {
                    for (i in 0 until frames) {
                        mono[i] = pcm[i]
                    }

                    Jlame.lame_encode_buffer(
                        flags,
                        mono,
                        mono,
                        frames,
                        mp3Buffer,
                        0,
                        mp3Buffer.size
                    )
                }

                if (encoded < 0) {
                    error("Falha interna do encoder MP3: código $encoded.")
                }

                if (encoded > 0) {
                    output.write(mp3Buffer, 0, encoded)
                }

                framesDone += frames

                onProgress(
                    (framesDone * 100L / max(1L, project.frames))
                        .toInt()
                        .coerceIn(0, 99)
                )

                if (got < floatBytes.size) break
            }

            val flushed = Jlame.lame_encode_flush(
                flags,
                mp3Buffer,
                mp3Buffer.size
            )

            if (flushed < 0) {
                error("Falha ao finalizar o arquivo MP3: código $flushed.")
            }

            if (flushed > 0) {
                output.write(mp3Buffer, 0, flushed)
            }

            output.flush()
            onProgress(100)
        } finally {
            Jlame.lame_close(flags)
        }
    }
}
