package br.com.timachado.pitchstudio

import br.com.timachado.pitchstudio.audio.AudioProject
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * Extrai notas dominantes de um trecho do PCM ORIGINAL. Não separa vocal de banda:
 * acompanhamento polifônico pode levar a detecção ao baixo/instrumento. Por isso,
 * a recomendação é experimental e o estimador devolve null com pouca evidência.
 */
internal object SongPhraseAnalyzer {
    data class Phrase(
        val lowHz: Double,
        val medianHz: Double,
        val highHz: Double,
        val reliableWindows: Int
    ) {
        val spread: Double get() = 12.0 * log2(highHz / lowHz)
    }

    /** Até 11 segundos do trecho escolhido no player, offline e com memória limitada. */
    fun analyze(project: AudioProject, startFraction: Double): Phrase? {
        if (project.channels !in 1..2 || project.sampleRate !in 8000..192000 ||
            project.frames < project.sampleRate * 2L || !project.pcmFile.isFile) return null
        val frames = project.frames
        val durationFrames = min(frames, project.sampleRate * 11L)
        val start = ((frames - durationFrames) * startFraction.coerceIn(0.0,1.0)).toLong()
        val hop = (project.sampleRate * 0.32).roundToInt().coerceAtLeast(4096)
        val readSize = 4096
        val channels = project.channels
        val frameBytes = 4 * channels
        val buffer = ByteArray(readSize * frameBytes)
        val pcm = ShortArray(readSize)
        val selected = ArrayList<VoicePitchAnalyzer.Note>(38)
        RandomAccessFile(project.pcmFile, "r").use { input ->
            var frame = start
            val end = start + durationFrames
            while (frame + readSize <= end && selected.size < 40) {
                if (Thread.currentThread().isInterrupted) return null
                input.seek(frame * frameBytes)
                input.readFully(buffer)
                // O áudio original do PitchStudio é little-endian Float32 interleaved.
                for (i in 0 until readSize) {
                    var mono = 0.0f
                    for (channel in 0 until channels) {
                        val offset = (i * channels + channel) * 4
                        val bits = (buffer[offset].toInt() and 255) or
                            ((buffer[offset+1].toInt() and 255) shl 8) or
                            ((buffer[offset+2].toInt() and 255) shl 16) or
                            ((buffer[offset+3].toInt() and 255) shl 24)
                        val sample = Float.fromBits(bits)
                        if (sample.isFinite()) mono += sample / channels
                    }
                    pcm[i] = (mono.coerceIn(-1.0f, 1.0f) * 32767f).toInt().toShort()
                }
                VoicePitchAnalyzer.estimate(pcm, readSize, project.sampleRate)
                    ?.takeIf { it.confidence >= 0.88 && it.hz in 130.0..880.0 }
                    ?.let(selected::add)
                frame += hop
            }
        }
        if (selected.size < 10) return null
        val values = selected.map { it.hz }.sorted()
        fun pct(p: Double): Double {
            val point = (values.size - 1) * p
            val floor = point.toInt()
            val share = point - floor
            return values[floor] * (1.0 - share) +
                values[min(values.lastIndex, floor+1)] * share
        }
        val low = pct(0.20)
        val middle = pct(0.50)
        val high = pct(0.80)
        // Gravações em que uma única nota/instrumento domina não trazem
        // extensão de melodia suficiente para justificar transposição.
        if (12.0 * log2(high / low) < 2.0 || 12.0 * log2(high / low) > 19.0) return null
        return Phrase(low, middle, high, selected.size)
    }
}

/**
 * Calcula transposição pela sobreposição entre as notas predominantes da faixa
 * e a faixa CENTRAL da voz observada. A decisão final é sempre auditiva.
 */
internal object ToneMatchAdvisor {
    data class Result(val recommended: Int, val alternatives: List<Int>, val suitability: String)

    fun suggest(
        voice: VoicePitchAnalyzer.Profile,
        phrase: SongPhraseAnalyzer.Phrase
    ): Result? {
        if (voice.voicedWindows < 7 || phrase.reliableWindows < 10 ||
            voice.lowHz <= 0 || phrase.lowHz <= 0 || voice.highHz <= voice.lowHz ||
            phrase.highHz <= phrase.lowHz) return null
        fun semitones(hz: Double) = 12.0 * log2(hz / 440.0)
        val voiceLow = semitones(voice.lowHz)
        val voiceHigh = semitones(voice.highHz)
        val voiceCenter = semitones(voice.medianHz)
        val melodyLow = semitones(phrase.lowHz)
        val melodyHigh = semitones(phrase.highHz)
        val melodyCenter = semitones(phrase.medianHz)
        if (voiceHigh - voiceLow < 1.5 || melodyHigh - melodyLow < 2.0) return null
        // Evita fabricar certeza a partir de um pico distante em várias oitavas.
        val candidates = (-12..12).sortedWith(compareBy<Int> { shift ->
            val lo = melodyLow + shift
            val hi = melodyHigh + shift
            val below = max(0.0, voiceLow - lo)
            val above = max(0.0, hi - voiceHigh)
            3.5 * (below + above) + 0.38 * abs(melodyCenter + shift - voiceCenter) +
                0.05 * abs(shift)
        }.thenBy { abs(it) })
        val preferred = candidates.first()
        val alternatives = (listOf(preferred, (preferred-2).coerceAtLeast(-12),
            (preferred+2).coerceAtMost(12))).distinct()
        val fit = max(0.0, voiceLow - (melodyLow+preferred)) +
            max(0.0, melodyHigh+preferred-voiceHigh)
        return Result(
            preferred, alternatives,
            if (fit <= 1.5) "Boa sobreposição estimada das notas."
            else "Trecho possivelmente fora da faixa observada; compare ouvindo."
        )
    }
}
