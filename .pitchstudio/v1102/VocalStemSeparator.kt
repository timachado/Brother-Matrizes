package br.com.timachado.pitchstudio

import android.content.Context
import br.com.timachado.pitchstudio.audio.AudioProject
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Separação vocal ON-DEVICE, para UM trecho selecionado (~5.8 s).
 *
 * Referência: UVR-MDX-NET-9482 convertido para LiteRT por gyoom-sa,
 * sob licença MIT. Modelo NCHW [1,4,2048,256], canais
 * [L_real,L_imaginário,R_real,R_imaginário]. O modelo estima vocais;
 * instrumentos correspondem ao residual da mistura original.
 *
 * A transformação STFT e iSTFT é executada em Kotlin: não se deve
 * chamar simplesmente Interpreter.run() sobre PCM cru.
 * Sem upload, sem tocar no arquivo PCM original, sem gravação persistente.
 */
internal object VocalStemSeparator {
    const val MODEL_ASSET = "UVR_MDXNET_9482.fp16acc.tflite"
    const val MODEL_LICENSE = "UVR MDX-Net (MIT)"
    private const val RATE = 44100
    private const val FFT_SIZE = 4096
    private const val HALF = FFT_SIZE / 2
    private const val HOP = 1024
    private const val FRAMES = 256
    private const val CHUNK = HOP * (FRAMES - 1)
    private const val USABLE = CHUNK - FFT_SIZE // trim 2048 each side
    private const val TENSOR_FLOATS = 4 * HALF * FRAMES

    private val window = DoubleArray(FFT_SIZE) { i ->
        0.5 * (1.0 - cos(2.0 * PI * i / FFT_SIZE))
    }

    data class Separated(val vocals: AudioProject, val sourceName: String)

    private fun index(plane: Int, bin: Int, frame: Int) =
        ((plane * HALF + bin) * FRAMES + frame) * 4

    /** Returns temporary PCM project. Caller must delete its file after analysis. */
    fun separate(
        context: Context,
        original: AudioProject,
        fraction: Double,
        progress: (String) -> Unit = {}
    ): Separated {
        require(original.channels in 1..2 && original.sampleRate in 8000..192000)
        require(original.frames > original.sampleRate * 2L)
        require(original.pcmFile.isFile)
        require(original.frames < Long.MAX_VALUE / (4L * original.channels))
        require(original.pcmFile.length() == original.frames * 4L * original.channels)
        val file = File.createTempFile("pitch_vocal_stem_", ".f32", context.cacheDir)
        try {
            progress("Preparando o trecho (44,1 kHz)…")
            val (left, right) = readResampled(original, fraction)
            val packed = ByteBuffer.allocateDirect(TENSOR_FLOATS * 4)
                .order(ByteOrder.nativeOrder())
            val inferred = ByteBuffer.allocateDirect(TENSOR_FLOATS * 4)
                .order(ByteOrder.nativeOrder())
            progress("Transformando o áudio em frequências…")
            stft(left, packed, 0)
            stft(right, packed, 2)
            if (Thread.currentThread().isInterrupted) error("Análise cancelada")
            progress("Separando vocal com modelo MDX-Net…")
            context.assets.openFd(MODEL_ASSET).use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                    val mapped = channel.map(FileChannel.MapMode.READ_ONLY,
                        descriptor.startOffset, descriptor.length)
                    Interpreter(mapped, Interpreter.Options().apply {
                        setNumThreads(2)
                    }).use { interpreter ->
                        val shape = interpreter.getInputTensor(0).shape()
                        require(shape.contentEquals(intArrayOf(1, 4, HALF, FRAMES))) {
                            "Modelo de separação incompatível"
                        }
                        packed.rewind()
                        inferred.rewind()
                        interpreter.run(packed, inferred)
                    }
                }
            }
            if (Thread.currentThread().isInterrupted) error("Análise cancelada")
            progress("Reconstruindo a faixa vocal…")
            val vocalLeft = istft(inferred, 0)
            val vocalRight = istft(inferred, 2)
            val count = min(vocalLeft.size, vocalRight.size)
            var peak = 0f
            var energy = 0.0
            val section = FloatArray(120) // waveform placeholder
            FileOutputStream(file).use { out ->
                val bytes = ByteBuffer.allocateDirect(16384).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until count) {
                    val signal = ((vocalLeft[i] + vocalRight[i]) * 0.5f)
                        .takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
                    peak = max(peak, kotlin.math.abs(signal))
                    energy += signal * signal
                    val visual = min(section.lastIndex, (i.toLong() * section.size / count).toInt())
                    section[visual] = max(section[visual], kotlin.math.abs(signal))
                    if (bytes.remaining() < 4) {
                        bytes.flip()
                        while (bytes.hasRemaining()) out.channel.write(bytes)
                        bytes.clear()
                    }
                    bytes.putFloat(signal)
                }
                bytes.flip()
                while (bytes.hasRemaining()) out.channel.write(bytes)
                out.fd.sync()
            }
            // Rejeitar inferência silenciosa/ruim para evitar conclusões fictícias.
            check(energy / count >= 1e-6) {
                "O trecho não contém vocal separado suficientemente audível."
            }
            return Separated(
                AudioProject("Voz isolada (temporária)", file, RATE, 1,
                    count.toLong(), peak, section),
                original.name
            )
        } catch (t: Throwable) {
            file.delete()
            throw t
        }
    }

    /** Read ORIGINAL float32 interleaved PCM and resample linearly to stereo 44.1kHz. */
    private fun readResampled(
        project: AudioProject,
        fraction: Double
    ): Pair<FloatArray, FloatArray> {
        val sourceRate = project.sampleRate
        val channels = project.channels
        val sourceCount = min(project.frames,
            (CHUNK.toDouble() * sourceRate / RATE + 4).toLong()).toInt()
        val start = ((project.frames - sourceCount).coerceAtLeast(0) *
            fraction.coerceIn(0.0, 1.0)).toLong()
        val data = ByteArray(sourceCount * channels * 4)
        RandomAccessFile(project.pcmFile, "r").use { src ->
            src.seek(start * channels * 4L)
            src.readFully(data)
        }
        fun at(frame: Int, channel: Int): Float {
            val clamped = frame.coerceIn(0, sourceCount - 1)
            val off = (clamped * channels + min(channel, channels-1)) * 4
            val bits = (data[off].toInt() and 255) or
                ((data[off + 1].toInt() and 255) shl 8) or
                ((data[off + 2].toInt() and 255) shl 16) or
                ((data[off + 3].toInt() and 255) shl 24)
            return Float.fromBits(bits).takeIf { it.isFinite() } ?: 0f
        }
        val left = FloatArray(CHUNK)
        val right = FloatArray(CHUNK)
        for (i in 0 until CHUNK) {
            val pos = i.toDouble() * sourceRate / RATE
            val before = pos.toInt()
            val blend = (pos - before).toFloat()
            if (before >= sourceCount) continue
            left[i] = at(before, 0) * (1-blend) + at(before+1, 0) * blend
            right[i] = at(before, channels-1) * (1-blend) +
                at(before+1, channels-1) * blend
        }
        return left to right
    }

    private fun reflect(index: Int, length: Int): Int {
        // The reference uses numpy.pad(..., mode="reflect"), not edge-clamp.
        if (length <= 1) return 0
        return when {
            index < 0 -> -index
            index >= length -> 2 * length - 2 - index
            else -> index
        }
    }

    private fun stft(samples: FloatArray, packed: ByteBuffer, plane: Int) {
        val real = DoubleArray(FFT_SIZE)
        val imag = DoubleArray(FFT_SIZE)
        for (frame in 0 until FRAMES) {
            if (Thread.currentThread().isInterrupted) error("Análise cancelada")
            for (i in 0 until FFT_SIZE) {
                val position = reflect(frame * HOP + i - HALF, samples.size)
                real[i] = samples[position] * window[i]
                imag[i] = 0.0
            }
            fft(real, imag, inverse=false)
            for (frequency in 0 until HALF) {
                packed.putFloat(index(plane, frequency, frame), real[frequency].toFloat())
                packed.putFloat(index(plane+1, frequency, frame), imag[frequency].toFloat())
            }
        }
    }

    private fun istft(spec: ByteBuffer, plane: Int): FloatArray {
        val ola = DoubleArray(CHUNK + FFT_SIZE)
        val weight = DoubleArray(CHUNK + FFT_SIZE)
        val real = DoubleArray(FFT_SIZE)
        val imag = DoubleArray(FFT_SIZE)
        for (frame in 0 until FRAMES) {
            if (Thread.currentThread().isInterrupted) error("Análise cancelada")
            real.fill(0.0); imag.fill(0.0)
            for (frequency in 0 until HALF) {
                val value = spec.getFloat(index(plane, frequency, frame))
                val complex = spec.getFloat(index(plane+1, frequency, frame))
                real[frequency] = value.toDouble()
                imag[frequency] = complex.toDouble()
                if (frequency > 0) {
                    real[FFT_SIZE-frequency] = value.toDouble()
                    imag[FFT_SIZE-frequency] = -complex.toDouble()
                }
            }
            fft(real, imag, inverse=true)
            val base = frame * HOP
            for (i in 0 until FFT_SIZE) {
                val w = window[i]
                ola[base+i] += real[i] * w
                weight[base+i] += w * w
            }
        }
        val usable = FloatArray(USABLE)
        for (i in 0 until USABLE) {
            val idx = i + HALF * 2 // trim center; see MDX pipeline
            usable[i] = (ola[idx] / (weight[idx] + 1e-8)).toFloat()
        }
        return usable
    }

    /** In-place radix-2 FFT, including inverse normalization by N. */
    private fun fft(real: DoubleArray, imag: DoubleArray, inverse: Boolean) {
        val n = real.size
        var reverse = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((reverse and bit) != 0) {
                reverse = reverse xor bit
                bit = bit shr 1
            }
            reverse = reverse xor bit
            if (reverse > i) {
                val r = real[i]; real[i] = real[reverse]; real[reverse] = r
                val im = imag[i]; imag[i] = imag[reverse]; imag[reverse] = im
            }
        }
        var width = 2
        while (width <= n) {
            val step = if (inverse) 2 * PI / width else -2 * PI / width
            val wrStep = cos(step)
            val wiStep = sin(step)
            var offset = 0
            while (offset < n) {
                var wr = 1.0
                var wi = 0.0
                val half = width / 2
                for (i in 0 until half) {
                    val even = offset + i
                    val odd = even + half
                    val tr = wr * real[odd] - wi * imag[odd]
                    val ti = wr * imag[odd] + wi * real[odd]
                    real[odd] = real[even] - tr
                    imag[odd] = imag[even] - ti
                    real[even] += tr
                    imag[even] += ti
                    val nwr = wr * wrStep - wi * wiStep
                    wi = wr * wiStep + wi * wrStep
                    wr = nwr
                }
                offset += width
            }
            width = width shl 1
        }
        if (inverse) for (i in 0 until n) {
            real[i] /= n
            imag[i] /= n
        }
    }
}
