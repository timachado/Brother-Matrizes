package br.com.timachado.pitchstudio

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Exporta PCM original Float32 little-endian para WAV PCM16 interoperável.
 * Exibe só o resultado já pronto ao seletor SAF; sem permissão de armazenamento
 * amplo, sem precisar de rede e sem alterar o áudio da prévia em cache.
 */
internal object StemWavExporter {
    const val MAX_FRAMES = 44100L * 120L

    fun exportToFile(source: File, sampleRate: Int, channels: Int, frames: Long, target: File): Long {
        require(sampleRate in 8000..192000) { "Amostragem WAV inválida." }
        require(channels in 1..2) { "Quantidade de canais inválida." }
        require(frames in 1..MAX_FRAMES) { "Trecho de áudio inválido." }
        val sampleCount = frames * channels
        require(sampleCount <= (Int.MAX_VALUE - 44L) / 2L) { "Trecho WAV muito grande." }
        require(source.isFile && source.length() == sampleCount * 4L) {
            "A faixa vocal temporária está incompleta."
        }
        val dataBytes = (sampleCount * 2L).toInt()
        val partial = File(target.parentFile, target.name + ".part")
        partial.delete()
        try {
            BufferedInputStream(FileInputStream(source), 64 * 1024).use { input ->
                FileOutputStream(partial).use { file ->
                    BufferedOutputStream(file, 64 * 1024).use { output ->
                        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                        header.put("RIFF".toByteArray(Charsets.US_ASCII))
                        header.putInt(36 + dataBytes)
                        header.put("WAVE".toByteArray(Charsets.US_ASCII))
                        header.put("fmt ".toByteArray(Charsets.US_ASCII))
                        header.putInt(16) // formato PCM canônico
                        header.putShort(1) // PCM
                        header.putShort(channels.toShort())
                        header.putInt(sampleRate)
                        header.putInt(sampleRate * channels * 2)
                        header.putShort((channels * 2).toShort())
                        header.putShort(16)
                        header.put("data".toByteArray(Charsets.US_ASCII))
                        header.putInt(dataBytes)
                        output.write(header.array())
                        val pcmBytes = ByteArray(16 * 1024)
                        val wavBytes = ByteArray(pcmBytes.size / 2)
                        var readSamples = 0L
                        while (readSamples < sampleCount) {
                            if (Thread.currentThread().isInterrupted) {
                                throw java.io.InterruptedIOException("Exportação interrompida.")
                            }
                            val batch = minOf(pcmBytes.size / 4L, sampleCount - readSamples).toInt()
                            val wanted = batch * 4
                            var read = 0
                            while (read < wanted) {
                                val n = input.read(pcmBytes, read, wanted - read)
                                if (n <= 0) throw java.io.EOFException("PCM incompleto.")
                                read += n
                            }
                            var p = 0
                            var w = 0
                            while (p < wanted) {
                                val bits = (pcmBytes[p].toInt() and 255) or
                                    ((pcmBytes[p + 1].toInt() and 255) shl 8) or
                                    ((pcmBytes[p + 2].toInt() and 255) shl 16) or
                                    ((pcmBytes[p + 3].toInt() and 255) shl 24)
                                val value = Float.fromBits(bits)
                                val normalized = if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f
                                val sample = (normalized * 32767f).roundToInt()
                                    .coerceIn(-32768, 32767)
                                wavBytes[w++] = sample.toByte()
                                wavBytes[w++] = (sample shr 8).toByte()
                                p += 4
                            }
                            output.write(wavBytes, 0, w)
                            readSamples += batch
                        }
                        output.flush()
                    }
                }
            }
            check(partial.length() == dataBytes.toLong() + 44L) { "WAV incompleto." }
            check(partial.renameTo(target)) { "Não foi possível finalizar o arquivo WAV." }
            return target.length()
        } catch (error: Throwable) {
            partial.delete()
            target.delete()
            throw error
        }
    }
}
