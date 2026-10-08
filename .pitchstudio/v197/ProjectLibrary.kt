package br.com.timachado.pitchstudio

import android.content.Context
import br.com.timachado.pitchstudio.audio.AudioProject
import br.com.timachado.pitchstudio.audio.DspQuality
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.FileOutputStream
import java.util.UUID

/**
 * Projetos privados e offline. Salva o PCM ORIGINAL e os ajustes (não a prévia
 * renderizada), para o DSP ser recalculado quando o usuário reabrir.
 * Nenhuma permissão de armazenamento amplo, servidor ou login é necessária.
 */
internal object ProjectLibrary {
    private const val MAX_TRACK_BYTES = 512L * 1024L * 1024L

    data class Settings(
        val title: String,
        val semitones: Int,
        val cents: Int,
        val speed: Double,
        val quality: DspQuality,
        val loopA: Double?,
        val loopB: Double?,
        val position: Double
    )

    data class Entry(
        val id: String,
        val title: String,
        val updatedAt: Long,
        val sizeBytes: Long
    )

    data class Opened(val project: AudioProject, val settings: Settings)

    private fun base(context: Context): File =
        File(context.filesDir, "saved_audio_projects")

    private fun validId(id: String): Boolean =
        Regex("[a-f0-9-]{36}").matches(id)

    private fun directory(context: Context, id: String): File {
        require(validId(id)) { "Identificador inválido." }
        return File(base(context), id)
    }

    private fun metadata(dir: File): File = File(dir, "project.json")
    private fun audio(dir: File): File = File(dir, "original.f32")

    /** Retorna apenas projetos completos. Registros danificados não quebram a lista. */
    fun list(context: Context): List<Entry> =
        base(context).listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && validId(it.name) }
            ?.mapNotNull { dir ->
                try {
                    val file = audio(dir)
                    val json = JSONObject(metadata(dir).readText(Charsets.UTF_8))
                    if (!file.isFile || file.length() < 4L) null
                    else Entry(
                        id = dir.name,
                        title = json.getString("title"),
                        updatedAt = json.optLong("updatedAt"),
                        sizeBytes = file.length()
                    )
                } catch (_: Exception) {
                    null
                }
            }
            ?.sortedByDescending { it.updatedAt }
            ?.toList()
            ?: emptyList()

    /**
     * A entrada é aberta pelo chamador ANTES de agendar o trabalho, para que a
     * limpeza do cache não apague o original durante uma gravação assíncrona.
     */
    fun save(
        context: Context,
        source: AudioProject,
        input: InputStream,
        settings: Settings,
        onProgress: (Int) -> Unit = {}
    ): Entry {
        val expected = source.pcmFile.length()
        require(expected in 4L..MAX_TRACK_BYTES) {
            "Esta música ultrapassa o limite de 512 MB por projeto ou está vazia."
        }
        require(source.channels in 1..2 && source.sampleRate in 8000..192000)
        val title = settings.title.trim().take(100)
        require(title.isNotBlank()) { "Informe um nome para o projeto." }
        val dir = directory(context, UUID.randomUUID().toString())
        check(dir.mkdirs()) { "Não foi possível criar a pasta do projeto." }
        val tmpPcm = File(dir, "original.f32.tmp")
        val tmpMeta = File(dir, "project.json.tmp")
        try {
            var copied = 0L
            val buffer = ByteArray(256 * 1024)
            input.use { stream ->
                FileOutputStream(tmpPcm).use { output ->
                    while (true) {
                        if (Thread.currentThread().isInterrupted) {
                            throw java.io.InterruptedIOException("Salvamento cancelado.")
                        }
                        val n = stream.read(buffer)
                        if (n < 0) break
                        if (n == 0) continue
                        copied += n
                        require(copied <= MAX_TRACK_BYTES) { "Limite de tamanho excedido." }
                        output.write(buffer, 0, n)
                        onProgress((copied * 100L / expected).toInt().coerceIn(0, 100))
                    }
                    output.fd.sync()
                }
            }
            require(copied == expected) { "Cópia incompleta: $copied de $expected bytes." }
            val obj = JSONObject().apply {
                put("schema", 1)
                put("title", title)
                put("updatedAt", System.currentTimeMillis())
                put("sampleRate", source.sampleRate)
                put("channels", source.channels)
                put("frames", source.frames)
                put("peak", source.peak.toDouble())
                put("waveform", JSONArray().apply {
                    for (sample in source.waveform) put(sample.toDouble())
                })
                put("semitones", settings.semitones.coerceIn(-12, 12))
                put("cents", settings.cents.coerceIn(-100, 100))
                put("speed", settings.speed.coerceIn(0.5, 2.0))
                put("quality", settings.quality.name)
                if (settings.loopA != null) put("loopA", settings.loopA.coerceIn(0.0, 1.0))
                if (settings.loopB != null) put("loopB", settings.loopB.coerceIn(0.0, 1.0))
                put("position", settings.position.coerceIn(0.0, 1.0))
            }
            FileOutputStream(tmpMeta).use {
                it.write(obj.toString().toByteArray(Charsets.UTF_8))
                it.fd.sync()
            }
            check(tmpPcm.renameTo(audio(dir))) { "Falha ao finalizar áudio." }
            check(tmpMeta.renameTo(metadata(dir))) { "Falha ao finalizar metadados." }
            return Entry(dir.name, title, obj.getLong("updatedAt"), copied)
        } catch (error: Exception) {
            dir.deleteRecursively()
            throw error
        }
    }

    fun open(context: Context, id: String): Opened {
        val dir = directory(context, id)
        val pcm = audio(dir)
        val obj = JSONObject(metadata(dir).readText(Charsets.UTF_8))
        require(obj.getInt("schema") == 1) { "Versão de projeto incompatível." }
        val channels = obj.getInt("channels")
        val frames = obj.getLong("frames")
        val rate = obj.getInt("sampleRate")
        val peak = obj.getDouble("peak").toFloat()
        require(channels in 1..2 && frames > 0 && rate in 8000..192000)
        require(pcm.isFile && pcm.length() == frames * channels * 4L) {
            "Os dados de áudio do projeto estão incompletos."
        }
        val wav = obj.getJSONArray("waveform")
        require(wav.length() in 1..5000) { "Forma de onda inválida." }
        val waveform = FloatArray(wav.length()) { index ->
            wav.getDouble(index).toFloat().coerceIn(0f, 1f)
        }
        val title = obj.getString("title")
        val project = AudioProject(title, pcm, rate, channels, frames, peak, waveform)
        val quality = try {
            DspQuality.valueOf(obj.getString("quality"))
        } catch (_: Exception) {
            DspQuality.ALTA
        }
        val a = obj.optDouble("loopA", Double.NaN).takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)
        val b = obj.optDouble("loopB", Double.NaN).takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)
        val settings = Settings(
            title = title,
            semitones = obj.optInt("semitones", 0).coerceIn(-12, 12),
            cents = obj.optInt("cents", 0).coerceIn(-100, 100),
            speed = obj.optDouble("speed", 1.0).coerceIn(0.5, 2.0),
            quality = quality,
            loopA = a,
            loopB = b?.takeIf { a != null && it > a },
            position = obj.optDouble("position", 0.0).coerceIn(0.0, 1.0)
        )
        return Opened(project, settings)
    }

    fun delete(context: Context, id: String): Boolean {
        val dir = directory(context, id)
        return dir.exists() && dir.deleteRecursively()
    }

    fun isStoredAudio(context: Context, file: File): Boolean {
        return try {
            val parent = base(context).canonicalPath + File.separator
            file.canonicalPath.startsWith(parent)
        } catch (_: Exception) {
            false
        }
    }
}
