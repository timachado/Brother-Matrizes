package br.com.timachado.pitchstudio

/**
 * Planejamento independente da inferência MDX-Net.
 * Blocos sobrepostos em 2.048 amostras para evitar emendas abruptas.
 * O áudio processado é mantido limitado a no máximo 18 s em memória.
 */
internal object VocalChunkPlan {
    const val RATE = 44100
    const val OUTPUT_FRAMES = 257024
    const val OVERLAP_FRAMES = 2048
    const val STEP_FRAMES = OUTPUT_FRAMES - OVERLAP_FRAMES
    val OPTIONS_SECONDS = intArrayOf(6, 12, 18)

    fun targetFrames(seconds: Int, sourceFrames: Long, sourceRate: Int): Int {
        require(seconds in OPTIONS_SECONDS) { "Duração não permitida." }
        require(sourceRate in 8000..192000 && sourceFrames > 0L)
        // Integer math avoids overflow for long source files.
        val secondsAvailable = sourceFrames.toDouble() / sourceRate
        return minOf((seconds * RATE).toDouble(),
            secondsAvailable * RATE).toInt().coerceAtLeast(1)
    }

    fun chunkOffsets(targetFrames: Int): IntArray {
        require(targetFrames in 1..(18 * RATE))
        val extra = (targetFrames - OUTPUT_FRAMES).coerceAtLeast(0)
        val additional = (extra + STEP_FRAMES - 1) / STEP_FRAMES
        return IntArray(additional + 1) { it * STEP_FRAMES }
    }

    /** Crossfade the overlapping part without duplicating, skipping or dropping samples. */
    fun mixInto(target: FloatArray, mono: FloatArray, offset: Int) {
        require(offset >= 0 && mono.size == OUTPUT_FRAMES)
        require(offset < target.size)
        val copyFrames = minOf(mono.size, target.size - offset)
        for (i in 0 until copyFrames) {
            val value = mono[i].takeIf { it.isFinite() } ?: 0f
            if (offset != 0 && i < OVERLAP_FRAMES) {
                val w = (i + 1f) / (OVERLAP_FRAMES + 1f)
                target[offset + i] = target[offset + i] * (1f - w) + value * w
            } else {
                target[offset + i] = value
            }
        }
    }
}
