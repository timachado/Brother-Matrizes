package br.com.timachado.pitchstudio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Detector de notas predominantes com preferência por região vocal.
 *
 * FFT + energia harmônica não equivale a separação de fonte vocal. O detector
 * rejeita trechos ambíguos, com graves dominantes ou com baixa sustentação.
 * Trabalha somente com amostras PCM locais e não persiste conteúdo da música.
 */
internal object MelodyFocusEstimator {
    data class Pitch(val hz: Double, val confidence: Double)
    private const val N = 4096
    private val NOTES = (48..81).map { midi ->
        440.0 * Math.pow(2.0, (midi - 69) / 12.0)
    }

    fun estimate(samples: FloatArray, sampleRate: Int): Pitch? {
        if (samples.size != N || sampleRate !in 8000..22050) return null

        val real = DoubleArray(N)
        val imag = DoubleArray(N)
        var mean = 0.0
        for (sample in samples) {
            if (!sample.isFinite()) return null
            mean += sample.toDouble()
        }
        mean /= N
        var energy = 0.0
        var peak = 0.0
        for (i in 0 until N) {
            val value = (samples[i] - mean)
            energy += value * value
            peak = max(peak, abs(value))
        }
        val rms = sqrt(energy / N)
        if (rms < 0.010 || peak > 1.05) return null

        // Um passa-altas brando atenua bumbo/subgrave antes da estimativa
        // espectral sem descartar integralmente a região vocal masculina.
        val cutoff = 115.0
        val dt = 1.0 / sampleRate
        val rc = 1.0 / (2.0 * PI * cutoff)
        val alpha = rc / (rc + dt)
        var previousInput = 0.0
        var previousOutput = 0.0
        for (i in 0 until N) {
            val value = samples[i].toDouble() - mean
            val filtered = alpha * (previousOutput + value - previousInput)
            previousInput = value
            previousOutput = filtered
            val window = 0.5 - 0.5 * cos(2.0 * PI * i / (N - 1))
            real[i] = filtered * window
        }
        fft(real, imag)
        val magnitudes = DoubleArray(N / 2) { i -> hypot(real[i], imag[i]) }
        val resolution = sampleRate.toDouble() / N

        fun amplitude(hz: Double): Double {
            val center = (hz / resolution).roundToInt()
            if (center < 2 || center >= magnitudes.size - 2) return 0.0
            var best = magnitudes[center]
            // Picos de notas levemente desafinadas não ficam num único bin.
            for (i in center - 1..center + 1) best = max(best, magnitudes[i])
            return best
        }

        val lowBass = (65..125 step 4).maxOf { amplitude(it.toDouble()) }
        val candidates = NOTES.map { hz ->
            val fundamental = amplitude(hz)
            val second = amplitude(hz * 2.0)
            val third = amplitude(hz * 3.0)
            val score = 0.78 * fundamental + 0.16 * second + 0.06 * third

            // Baixo com 2º/3º harmônicos não pode ser tratado como voz
            // só porque o fundamental está abaixo de 130 Hz.
            val lowerHalf = if (hz > 200.0) amplitude(hz / 2.0) else 0.0
            val lowerThird = if (hz > 270.0) amplitude(hz / 3.0) else 0.0
            val subharmonic = max(lowerHalf, lowerThird)
            val rejected = subharmonic > fundamental * 1.85 &&
                subharmonic > score * 1.35
            Triple(hz, if (rejected) 0.0 else score, fundamental)
        }.sortedByDescending { it.second }

        val leader = candidates.firstOrNull() ?: return null
        if (leader.second < 1e-5 || leader.third <= 0.0) return null

        // Se o baixo tem energia muito superior à frequência escolhida,
        // não há evidência suficiente de que seja a melodia cantada.
        // Aplicar também aos candidatos de 130–190 Hz: harmônicos residuais
        // de um baixo muito forte podem produzir um pico espúrio nessa região.
        if (lowBass > 2.5 * leader.third) return null

        // Notas vizinhas podem compartilhar o mesmo pico FFT: comparar apenas
        // candidatas separadas por >= 1.6 semitom.
        val competitor = candidates.firstOrNull {
            abs(12.0 * log2(it.first / leader.first)) > 1.6
        }?.second ?: 0.0
        val ratio = leader.second / max(competitor, 1e-9)
        if (ratio < 1.12) return null

        val clarity = ((ratio - 1.0) / 0.8).coerceIn(0.0, 1.0)
        if (clarity < 0.17) return null
        return Pitch(leader.first, clarity)
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val r = real[i]; real[i] = real[j]; real[j] = r
                val im = imag[i]; imag[i] = imag[j]; imag[j] = im
            }
        }
        var span = 2
        while (span <= n) {
            val angle = -2.0 * PI / span
            val stepReal = cos(angle)
            val stepImag = sin(angle)
            val half = span / 2
            var begin = 0
            while (begin < n) {
                var wr = 1.0
                var wi = 0.0
                for (k in 0 until half) {
                    val odd = begin + k + half
                    val even = begin + k
                    val xr = wr * real[odd] - wi * imag[odd]
                    val xi = wr * imag[odd] + wi * real[odd]
                    real[odd] = real[even] - xr
                    imag[odd] = imag[even] - xi
                    real[even] += xr
                    imag[even] += xi
                    val newWr = wr * stepReal - wi * stepImag
                    wi = wr * stepImag + wi * stepReal
                    wr = newWr
                }
                begin += span
            }
            span = span shl 1
        }
    }
}
