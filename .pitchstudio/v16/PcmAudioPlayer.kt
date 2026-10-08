package br.com.timachado.pitchstudio.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

class PcmAudioPlayer(private val onEnded: () -> Unit = {}) {
    @Volatile var project: AudioProject? = null
        private set

    @Volatile var positionFrames: Long = 0L
        private set

    @Volatile var volume: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            track?.setVolume(field)
        }

    @Volatile var pitchFactor: Float = 1f
        private set

    @Volatile var speedFactor: Float = 1f
        private set

    @Volatile var loopA: Double? = null
    @Volatile var loopB: Double? = null

    private val playing = AtomicBoolean(false)
    @Volatile private var track: AudioTrack? = null
    @Volatile private var thread: Thread? = null

    fun setProject(p: AudioProject?, fraction: Double = 0.0) {
        val wasPlaying = playing.get()
        stopInternal(reset = false)
        project = p
        positionFrames = if (p == null) 0 else
            (p.frames * fraction.coerceIn(0.0, 1.0)).toLong()

        if (wasPlaying && p != null) play()
    }

    fun setPreviewParams(pitch: Float, speed: Float) {
        pitchFactor = pitch.coerceIn(0.49f, 2.01f)
        speedFactor = speed.coerceIn(0.49f, 2.01f)
        track?.let { applyPlaybackParams(it) }
    }

    fun play() {
        val p = project ?: return
        if (!playing.compareAndSet(false, true)) return

        val channelMask =
            if (p.channels == 1) AudioFormat.CHANNEL_OUT_MONO
            else AudioFormat.CHANNEL_OUT_STEREO

        val minBuffer = AudioTrack.getMinBufferSize(
            p.sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        // Reserva folga para velocidades de até 2x, como recomenda AudioTrack.
        val bufferSize = max(minBuffer * 4, 128 * 1024)

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(p.sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(channelMask)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track = audioTrack
        audioTrack.setVolume(volume)
        applyPlaybackParams(audioTrack)
        audioTrack.play()

        thread = Thread(
            { streamLoop(p, audioTrack) },
            "PitchStudio-Player"
        ).also { it.start() }
    }

    fun pause() {
        stopInternal(reset = false)
    }

    fun toggle() {
        if (playing.get()) pause() else play()
    }

    fun isPlaying(): Boolean = playing.get()

    fun seekFraction(f: Double) {
        val p = project ?: return
        val keep = isPlaying()
        stopInternal(reset = false)
        positionFrames = (p.frames * f.coerceIn(0.0, 1.0)).toLong()
        if (keep) play()
    }

    fun fraction(): Double {
        val p = project ?: return 0.0
        return if (p.frames <= 0) 0.0
        else (positionFrames.toDouble() / p.frames).coerceIn(0.0, 1.0)
    }

    fun release() {
        stopInternal(reset = true)
        project = null
    }

    private fun applyPlaybackParams(audioTrack: AudioTrack) {
        try {
            val params = PlaybackParams()
                .setAudioFallbackMode(PlaybackParams.AUDIO_FALLBACK_MODE_DEFAULT)
                .setPitch(pitchFactor)
                .setSpeed(speedFactor)

            audioTrack.playbackParams = params
        } catch (_: Throwable) {
            // Mantém reprodução normal caso um fabricante rejeite algum fator.
        }
    }

    private fun stopInternal(reset: Boolean) {
        playing.set(false)

        try { track?.pause() } catch (_: Throwable) {}
        try { track?.flush() } catch (_: Throwable) {}
        try { track?.release() } catch (_: Throwable) {}

        track = null
        thread = null

        if (reset) positionFrames = 0
    }

    private fun streamLoop(p: AudioProject, audioTrack: AudioTrack) {
        val channels = p.channels
        val framesPerChunk = 4096
        val bytes = ByteArray(framesPerChunk * channels * 4)
        val floats = FloatArray(framesPerChunk * channels)

        RandomAccessFile(p.pcmFile, "r").use { raf ->
            while (playing.get() && positionFrames < p.frames) {
                val a = loopA
                val b = loopB

                if (a != null && b != null && b > a && fraction() >= b) {
                    positionFrames = (p.frames * a).toLong()
                }

                raf.seek(positionFrames * channels * 4L)

                val wantedFrames =
                    min(framesPerChunk.toLong(), p.frames - positionFrames).toInt()
                val wantedBytes = wantedFrames * channels * 4

                var got = 0
                while (got < wantedBytes) {
                    val n = raf.read(bytes, got, wantedBytes - got)
                    if (n <= 0) break
                    got += n
                }

                if (got <= 0) break

                val fb = ByteBuffer
                    .wrap(bytes, 0, got)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asFloatBuffer()

                val count = fb.remaining()
                fb.get(floats, 0, count)

                val written = audioTrack.write(
                    floats,
                    0,
                    count,
                    AudioTrack.WRITE_BLOCKING
                )

                if (written <= 0) break
                positionFrames += written / channels
            }
        }

        val naturalEnd = positionFrames >= p.frames
        playing.set(false)

        try { audioTrack.stop() } catch (_: Throwable) {}
        try { audioTrack.release() } catch (_: Throwable) {}

        if (track === audioTrack) track = null

        if (naturalEnd) {
            positionFrames = 0
            onEnded()
        }
    }
}
