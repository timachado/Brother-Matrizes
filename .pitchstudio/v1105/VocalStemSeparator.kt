package br.com.timachado.pitchstudio

import android.content.Context
import br.com.timachado.pitchstudio.audio.AudioProject
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Separação vocal ON-DEVICE para trechos de 6, 12 ou 18 s com crossfade entre blocos.
 *
 * Referência: UVR-MDX-NET-9482 executado localmente por ONNX Runtime,
 * sob licença MIT. Modelo NCHW [1,4,2048,256], canais
 * [L_real,L_imaginário,R_real,R_imaginário]. O modelo estima vocais;
 * instrumentos correspondem ao residual da mistura original.
 *
 * A transformação STFT e iSTFT é executada em Kotlin: não se deve
 * chamar simplesmente Interpreter.run() sobre PCM cru.
 * Sem upload, sem tocar no arquivo PCM original, sem gravação persistente.
 */
internal object VocalStemSeparator {
    const val MODEL_ASSET = "UVR_MDXNET_9482.onnx"
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

    /**
     * Isola 6, 12 ou 18 segundos em blocos com sobreposição curta.
     * Uma única sessão ONNX é reutilizada; memória e áudio nunca são enviados.
     * Em caso de cancelamento/falha, o PCM temporário é descartado.
     */
    fun separate(
        context: Context,
        original: AudioProject,
        fraction: Double,
        durationSeconds: Int = 6,
        progress: (String) -> Unit = {}
    ): Separated {
        require(original.channels in 1..2 && original.sampleRate in 8000..192000)
        require(original.frames > original.sampleRate * 2L)
        require(original.pcmFile.isFile)
        require(original.frames < Long.MAX_VALUE / (4L * original.channels))
        require(original.pcmFile.length() == original.frames * 4L * original.channels)

        val wantedFrames = VocalChunkPlan.targetFrames(
            durationSeconds, original.frames, original.sampleRate
        )
        val offsets = VocalChunkPlan.chunkOffsets(wantedFrames)
        val sourceSpan = kotlin.math.ceil(
            wantedFrames.toDouble() * original.sampleRate / RATE
        ).toLong()
        val selectionStart = (
            (original.frames - sourceSpan).coerceAtLeast(0).toDouble() *
            fraction.coerceIn(0.0, 1.0)
        ).toLong()
        val file = File.createTempFile("pitch_vocal_stem_", ".f32", context.cacheDir)
        try {
            val samples = FloatArray(wantedFrames)
            // Allocate only one pair of input/output tensors for all blocks.
            val packed = ByteBuffer.allocateDirect(TENSOR_FLOATS * 4)
                .order(ByteOrder.nativeOrder())
            val inferred = ByteBuffer.allocateDirect(TENSOR_FLOATS * 4)
                .order(ByteOrder.nativeOrder())
            val modelFile = cacheModel(context)
            val env = OrtEnvironment.getEnvironment()
            OrtSession.SessionOptions().use { opts ->
                opts.setIntraOpNumThreads(2)
                env.createSession(modelFile.absolutePath, opts).use { session ->
                    val tensorShape = longArrayOf(1L, 4L, HALF.toLong(), FRAMES.toLong())
                    val inputName = session.inputNames.first()
                    for ((block, offset) in offsets.withIndex()) {
                        if (Thread.currentThread().isInterrupted) error("Análise cancelada")
                        progress("Separação vocal por IA: bloco ${block + 1} de ${offsets.size}…")
                        val (left, right) = readResampled(original, selectionStart, offset)
                        packed.clear()
                        inferred.clear()
                        stft(left, packed, 0)
                        stft(right, packed, 2)
                        if (Thread.currentThread().isInterrupted) error("Análise cancelada")
                        packed.rewind()
                        OnnxTensor.createTensor(env, packed.asFloatBuffer(), tensorShape).use { input ->
                            session.run(mapOf(inputName to input)).use { results ->
                                val result = results[0] as OnnxTensor
                                val floats = result.floatBuffer
                                require(floats.remaining() == TENSOR_FLOATS) {
                                    "Formato de resultado ONNX incompatível."
                                }
                                while (floats.hasRemaining()) inferred.putFloat(floats.get())
                            }
                        }
                        if (Thread.currentThread().isInterrupted) error("Análise cancelada")
                        val vocalLeft = istft(inferred, 0)
                        val vocalRight = istft(inferred, 2)
                        val mono = FloatArray(USABLE)
                        for (i in mono.indices) {
                            mono[i] = ((vocalLeft[i] + vocalRight[i]) * 0.5f)
                                .takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
                        }
                        VocalChunkPlan.mixInto(samples, mono, offset)
                    }
                }
            }
            if (Thread.currentThread().isInterrupted) error("Análise cancelada")
            progress("Finalizando voz isolada…")
            var peak = 0f
            var energy = 0.0
            val section = FloatArray(120)
            FileOutputStream(file).use { out ->
                val bytes = ByteBuffer.allocateDirect(16384).order(ByteOrder.LITTLE_ENDIAN)
                for (i in samples.indices) {
                    if ((i and 16383) == 0 && Thread.currentThread().isInterrupted)
                        error("Análise cancelada")
                    val signal = samples[i].coerceIn(-1f, 1f)
                    peak = max(peak, kotlin.math.abs(signal))
                    energy += signal * signal
                    val visual = min(section.lastIndex,
                        (i.toLong() * section.size / samples.size).toInt())
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
            check(energy / samples.size >= 1e-6) {
                "O trecho não contém vocal separado suficientemente audível."
            }
            check(file.length() == samples.size * 4L) { "Áudio vocal incompleto." }
            return Separated(
                AudioProject("Voz isolada (temporária)", file, RATE, 1,
                    samples.size.toLong(), peak, section),
                original.name
            )
        } catch (t: Throwable) {
            file.delete()
            throw t
        }
    }

    /**
     * Modelo já distribuído dentro do APK; cópia local não contém áudio do usuário.
     * Salvamento temporário e substituição atômica evitam sessões com arquivo parcial.
     */
    private fun cacheModel(context: Context): File {
        val stored = File(context.cacheDir, MODEL_ASSET)
        if (stored.length() in 25_000_000..40_000_000) return stored
        val temp = File(context.cacheDir, MODEL_ASSET + ".part")
        try {
            context.assets.open(MODEL_ASSET).use { input ->
                FileOutputStream(temp).use { output ->
                    input.copyTo(output, 256 * 1024)
                    output.fd.sync()
                }
            }
            check(temp.length() in 25_000_000..40_000_000) {
                "O modelo de IA incluído no pacote está incompleto."
            }
            check(temp.renameTo(stored)) { "Não foi possível preparar o modelo local." }
            return stored
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
    }

    /** Read only the required ORIGINAL PCM window, with resampling and 4096-frame context. */
    private fun readResampled(
        project: AudioProject,
        selectionStart: Long,
        offset: Int
    ): Pair<FloatArray, FloatArray> {
        val rate = project.sampleRate
        val channels = project.channels
        val firstReal = selectionStart.toDouble() +
            (offset.toDouble() - FFT_SIZE) * rate / RATE
        val lastReal = selectionStart.toDouble() +
            (offset.toDouble() - FFT_SIZE + CHUNK) * rate / RATE
        val first = kotlin.math.floor(firstReal).toLong()
            .coerceIn(0L, project.frames - 1)
        val last = (kotlin.math.ceil(lastReal).toLong() + 1)
            .coerceIn(first, project.frames - 1)
        val count = (last - first + 1).toInt()
        val data = ByteArray(count * channels * 4)
        RandomAccessFile(project.pcmFile, "r").use { src ->
            src.seek(first * channels * 4L)
            src.readFully(data)
        }
        fun at(frame: Long, channel: Int): Float {
            val rel = (frame.coerceIn(first, last) - first).toInt()
            val p = (rel * channels + min(channel, channels - 1)) * 4
            val bits = (data[p].toInt() and 255) or
                ((data[p + 1].toInt() and 255) shl 8) or
                ((data[p + 2].toInt() and 255) shl 16) or
                ((data[p + 3].toInt() and 255) shl 24)
            return Float.fromBits(bits).takeIf { it.isFinite() } ?: 0f
        }
        val left = FloatArray(CHUNK)
        val right = FloatArray(CHUNK)
        for (i in 0 until CHUNK) {
            if ((i and 8191) == 0 && Thread.currentThread().isInterrupted)
                error("Análise cancelada")
            val pos = selectionStart.toDouble() +
                (offset.toDouble() - FFT_SIZE + i) * rate / RATE
            val before = kotlin.math.floor(pos).toLong()
            val blend = (pos - before).toFloat()
            left[i] = at(before, 0) * (1 - blend) + at(before + 1, 0) * blend
            right[i] = at(before, channels - 1) * (1 - blend) +
                at(before + 1, channels - 1) * blend
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
