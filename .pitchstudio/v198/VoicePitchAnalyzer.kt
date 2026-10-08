package br.com.timachado.pitchstudio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.log2

/** Detector YIN de notas monofônicas: sem rede, gravação ou classificação vocal clínica. */
internal object VoicePitchAnalyzer {
    data class Note(val hz: Double, val confidence: Double)

    data class Profile(
        val lowHz: Double,
        val medianHz: Double,
        val highHz: Double,
        val voicedWindows: Int
    ) {
        val spreadSemitones: Double get() = 12.0 * log2(highHz / lowHz)
    }

    private const val MIN_PITCH = 80.0
    private const val MAX_PITCH = 950.0

    /**
     * Reduz a amostragem quando necessário para limitar CPU.
     * Diferença cumulativa YIN com refinamento parabólico do período.
     */
    fun estimate(samples: ShortArray, count: Int, sampleRate: Int): Note? {
        if (sampleRate !in 8000..192000 || count < 1024 || count > samples.size) return null
        val stride = max(1, (sampleRate.toDouble() / 15000.0).roundToInt())
        val effectiveRate = sampleRate.toDouble() / stride
        val len = count / stride
        val maxLag = min((effectiveRate / MIN_PITCH).toInt(), len / 3)
        val minLag = max(2, (effectiveRate / MAX_PITCH).toInt())
        if (len < 600 || maxLag <= minLag + 2) return null
        val floats = DoubleArray(len)
        var avg = 0.0
        for (i in 0 until len) {
            val x = samples[i * stride] / 32768.0
            floats[i] = x
            avg += x
        }
        avg /= len
        var energy = 0.0
        for (i in floats.indices) {
            floats[i] -= avg
            energy += floats[i] * floats[i]
        }
        val rms = sqrt(energy / len)
        if (rms < 0.015 || rms > 0.98) return null
        val window = min(800, len - maxLag - 1)
        if (window < 400) return null
        val normalized = DoubleArray(maxLag + 1)
        var cumulative = 0.0
        var bestLag = -1
        var bestValue = Double.POSITIVE_INFINITY
        for (lag in minLag..maxLag) {
            var diff = 0.0
            var pos = 0
            while (pos < window) {
                val d = floats[pos] - floats[pos + lag]
                diff += d * d
                pos += 1
            }
            cumulative += diff
            val cmnd = diff * (lag - minLag + 1) / max(cumulative, 1e-15)
            normalized[lag] = cmnd
            if (cmnd < bestValue) {
                bestValue = cmnd
                bestLag = lag
            }
        }
        // Procura o primeiro vale claro para evitar dividir a frequência por dois.
        var candidate = -1
        for (lag in (minLag + 1) until maxLag) {
            if (normalized[lag] < 0.17 && normalized[lag] <= normalized[lag + 1]) {
                candidate = lag
                break
            }
        }
        if (candidate < 0) {
            if (bestValue > 0.22) return null
            candidate = bestLag
        }
        val v = normalized[candidate]
        if (v > 0.24) return null
        val left = normalized[candidate - 1]
        val right = normalized[candidate + 1]
        val denominator = left - 2.0 * v + right
        val correction = if (abs(denominator) > 1e-10)
            (0.5 * (left - right) / denominator).coerceIn(-0.5, 0.5)
        else 0.0
        val hz = effectiveRate / (candidate + correction)
        if (!hz.isFinite() || hz !in MIN_PITCH..MAX_PITCH) return null
        return Note(hz, (1.0 - v).coerceIn(0.0, 1.0))
    }

    fun profile(pitches: Collection<Note>): Profile? {
        // Janela confiável; ruídos transitórios não entram no cálculo.
        val values = pitches.filter { it.confidence >= 0.78 }
            .map { it.hz }.filter { it in MIN_PITCH..MAX_PITCH }.sorted()
        if (values.size < 7) return null
        fun percentile(p: Double): Double {
            val index = (values.size - 1) * p
            val lower = index.toInt()
            val fraction = index - lower
            return values[lower] * (1.0 - fraction) +
                values[min(lower + 1, values.lastIndex)] * fraction
        }
        // 20–80% evita usar uma desafinação momentânea como limite vocal.
        return Profile(percentile(0.2), percentile(0.5), percentile(0.8), values.size)
    }

    fun noteName(hz: Double): String {
        val midi = (69.0 + 12.0 * log2(hz / 440.0)).roundToInt().coerceIn(0, 127)
        val names = arrayOf("Dó", "Dó♯", "Ré", "Ré♯", "Mi", "Fá", "Fá♯",
            "Sol", "Sol♯", "Lá", "Lá♯", "Si")
        return names[midi % 12] + (midi / 12 - 1)
    }
}
