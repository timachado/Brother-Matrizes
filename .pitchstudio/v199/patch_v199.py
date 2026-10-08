#!/usr/bin/env python3
"""Evolução incremental Tom Ideal 1.9.9: relaciona voz às notas predominantes da música."""
from pathlib import Path
import sys
root=Path(sys.argv[1]);pkg=root/"app/src/main/java/br/com/timachado/pitchstudio"
main=pkg/"MainActivity.kt"
s=main.read_text(encoding="utf-8")
def one(a,b):
 global s
 n=s.count(a)
 if n!=1: raise SystemExit(f"Marcador único esperado: {n} / {a[:75]}")
 s=s.replace(a,b,1)
one(
'''        val voice = Intent(this, TomIdealActivity::class.java).apply {
            putExtra(TomIdealActivity.EXTRA_CURRENT_SHIFT, semitones)
        }''',
'''        player.pause()
        refreshPlayButton()
        val voice = Intent(this, TomIdealActivity::class.java).apply {
            putExtra(TomIdealActivity.EXTRA_CURRENT_SHIFT, semitones)
            original?.let { source ->
                putExtra(TomIdealActivity.EXTRA_AUDIO_FILE, source.pcmFile.absolutePath)
                putExtra(TomIdealActivity.EXTRA_AUDIO_RATE, source.sampleRate)
                putExtra(TomIdealActivity.EXTRA_AUDIO_CHANNELS, source.channels)
                putExtra(TomIdealActivity.EXTRA_AUDIO_FRAMES, source.frames)
                putExtra(TomIdealActivity.EXTRA_AUDIO_FRACTION, player.fraction())
            }
        }'''
)
main.write_text(s,encoding="utf-8")

activity=pkg/"TomIdealActivity.kt"
s=activity.read_text(encoding="utf-8")
def sub(a,b):
 global s
 n=s.count(a)
 if n!=1: raise SystemExit(f"TomIdeal: marcador esperado 1, encontrado {n}: {a[:80]}")
 s=s.replace(a,b,1)
sub("import kotlin.math.max\n","import kotlin.math.max\nimport java.io.File\nimport br.com.timachado.pitchstudio.audio.AudioProject\n")
sub('        const val EXTRA_CHOSEN_SHIFT = "tom_ideal_chosen_shift"','''        const val EXTRA_CHOSEN_SHIFT = "tom_ideal_chosen_shift"
        const val EXTRA_AUDIO_FILE = "tom_ideal_audio_file"
        const val EXTRA_AUDIO_RATE = "tom_ideal_audio_rate"
        const val EXTRA_AUDIO_CHANNELS = "tom_ideal_audio_channels"
        const val EXTRA_AUDIO_FRAMES = "tom_ideal_audio_frames"
        const val EXTRA_AUDIO_FRACTION = "tom_ideal_audio_fraction"''')
sub("    private var currentShift = 0",'''    private var currentShift = 0
    @Volatile private var phrase: SongPhraseAnalyzer.Phrase? = null
    private var voiceProfile: VoicePitchAnalyzer.Profile? = null
    private var songError: String? = null
    private lateinit var trackStatus: TextView
    private var phraseReading = false''')
sub('''        buildUi()
    }

    private fun buildUi()''','''        buildUi()
        analyzeLoadedSong()
    }

    private fun buildUi()''')
sub('''        choices = panel(
            "Compare três versões",
            "A análise de notas não revela a melodia de uma música. As opções abaixo são alternativas para experimentar no player, não um diagnóstico vocal."
        )
        showOptions(null)''',
'''        choices = panel(
            "Comparar voz e música",
            "Analisamos as notas predominantes em um trecho de até 11 segundos a partir da posição do player. Em gravações com banda, o instrumento dominante pode ser confundido com a voz."
        )
        trackStatus = text("Música: análise ainda não disponível.", 14f)
        add(choices, trackStatus, 12)
        showOptions(null)''')
sub('''                val profile = VoicePitchAnalyzer.profile(measured)
                runOnUiThread {''',
'''                val profile = VoicePitchAnalyzer.profile(measured)
                voiceProfile = profile
                runOnUiThread {''')
start=s.index('    private fun showOptions(profile: VoicePitchAnalyzer.Profile?) {')
end=s.index('    private fun choose(semitones: Int) {',start)
if start<0 or end<0: raise SystemExit("showOptions não encontrado")
s=s[:start]+r'''    private fun analyzeLoadedSong() {
        val path = intent.getStringExtra(EXTRA_AUDIO_FILE)
        if (path.isNullOrBlank()) {
            trackStatus.text = "Importe uma música antes de abrir o Tom Ideal para obter uma comparação."
            return
        }
        val file = File(path)
        val trusted = try {
            val canonical = file.canonicalPath
            listOf(cacheDir, filesDir).any { directory ->
                canonical.startsWith(directory.canonicalPath + File.separator)
            }
        } catch (_: Exception) { false }
        if (!trusted || !file.isFile) {
            trackStatus.text = "Áudio original indisponível. Reimporte a música no estúdio."
            return
        }
        val rate = intent.getIntExtra(EXTRA_AUDIO_RATE, 0)
        val channels = intent.getIntExtra(EXTRA_AUDIO_CHANNELS, 0)
        val frames = intent.getLongExtra(EXTRA_AUDIO_FRAMES, 0)
        val fraction = intent.getDoubleExtra(EXTRA_AUDIO_FRACTION, 0.0)
        if (rate !in 8000..192000 || channels !in 1..2 || frames < rate * 2L ||
            frames > Long.MAX_VALUE / (channels * 4L) ||
            file.length() != frames * channels * 4L) {
            trackStatus.text = "Formato de música incompatível com esta análise."
            return
        }
        phraseReading = true
        trackStatus.text = "Analisando um trecho da música localmente…"
        Thread({
            try {
                val source = AudioProject("Trecho analisado",file,rate,channels,frames,0f,
                    floatArrayOf(0f))
                val found = SongPhraseAnalyzer.analyze(source, fraction)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        phrase = found
                        phraseReading = false
                        songError = if (found == null)
                            "Notas misturadas ou trecho sem melodia confiável. Tente outro trecho."
                        else null
                        showOptions(voiceProfile)
                    }
                }
            } catch (t: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        phraseReading = false
                        songError = "Falha ao analisar música: " +
                            (t.message ?: "arquivo inacessível")
                        showOptions(voiceProfile)
                    }
                }
            }
        },"PitchStudio-SongPhrase").start()
    }

    private fun showOptions(profile: VoicePitchAnalyzer.Profile?) {
        if (profile != null) {
            status.text = "Notas identificadas na voz: " +
                VoicePitchAnalyzer.noteName(profile.lowHz) + " até " +
                VoicePitchAnalyzer.noteName(profile.highHz) +
                ". Nota central: " + VoicePitchAnalyzer.noteName(profile.medianHz) +
                " (" + profile.voicedWindows + " amostras válidas)."
        } else if (progress.progress > 0) {
            status.text = "Poucas notas vocais estáveis. Cante sozinho e tente novamente."
        }
        while (choices.childCount > 3) choices.removeViewAt(choices.childCount - 1)
        val song = phrase
        val match = if (profile != null && song != null)
            ToneMatchAdvisor.suggest(profile, song)
        else null
        trackStatus.text = when {
            phraseReading -> "Música: análise de trecho em andamento…"
            song != null -> "Notas predominantes do trecho: " +
                VoicePitchAnalyzer.noteName(song.lowHz) + " até " +
                VoicePitchAnalyzer.noteName(song.highHz) +
                " (" + song.reliableWindows + " janelas confiáveis)."
            songError != null -> "Música: " + songError
            else -> "Carregue uma música para comparar a voz ao trecho."
        }
        if (match != null) {
            add(choices, text("Sugestão experimental: " +
                (if (match.recommended > 0) "+" else "") +
                match.recommended + " semitons. " + match.suitability, 14f, true), 12)
            for ((index, shift) in match.alternatives.withIndex()) {
                val label = when(index) {
                    0 -> "Experimentar sugestão"
                    1 -> "Alternativa mais grave"
                    else -> "Alternativa mais aguda"
                }
                val named = label + " · " + (if (shift > 0) "+" else "") +
                    shift + " semitons"
                add(choices, button(named, index == 0) { choose(shift) },8)
            }
        } else {
            add(choices, text(
                "Ainda sem comparação confiável entre voz e música. " +
                "Você pode ouvir estas variações manuais no editor.", 14f), 10)
            val shifts = listOf(-2 to "Mais grave", 0 to "Atual", 2 to "Mais aguda")
            for ((delta, name) in shifts) {
                val value = (currentShift + delta).coerceIn(-12, 12)
                add(choices, button(name + " · " + (if(value > 0) "+" else "") +
                    value + " semitons",delta==0) { choose(value) },8)
            }
        }
        add(choices, text(
            "Notas de instrumentos podem ser confundidas com melodia. " +
            "A indicação é uma hipótese, não garante conforto ou afinação; " +
            "ouça antes de salvar e não force a voz.",12f
        ))
    }

'''+s[end:]
activity.write_text(s,encoding="utf-8")

gradle=root/"app/build.gradle.kts"
g=gradle.read_text(encoding="utf-8")
assert g.count("versionCode = 18")==1 and g.count('versionName = "1.9.8"')==1
assert 'applicationIdSuffix = ".voice"' in g
g=g.replace("versionCode = 18","versionCode = 19",1)
g=g.replace('versionName = "1.9.8"','versionName = "1.9.9"',1)
g=g.replace('applicationIdSuffix = ".voice"','applicationIdSuffix = ".voicematch"',1)
gradle.write_text(g,encoding="utf-8")

tests=root/"app/src/test/java/br/com/timachado/pitchstudio/SongPhraseAnalyzerTest.kt"
tests.parent.mkdir(parents=True,exist_ok=True)
tests.write_text(r'''package br.com.timachado.pitchstudio

import org.junit.Assert.*
import org.junit.Test
import br.com.timachado.pitchstudio.audio.AudioProject
import java.io.File
import java.io.DataOutputStream
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

class SongPhraseAnalyzerTest {
    @Test fun melodyRangeFromOriginalPcm() {
        val file = File.createTempFile("pitch_song_test_",".f32")
        try {
            FileOutputStream(file).use { out ->
                val buffer = ByteArray(4)
                val rate = 16000
                for (i in 0 until rate * 11) {
                    val freq = when ((i / rate) % 4) {
                        0 -> 220.0
                        1 -> 261.63
                        2 -> 329.63
                        else -> 293.66
                    }
                    val wave = (0.5 * sin(2.0 * PI * freq * i / rate)).toFloat()
                    val bits = java.lang.Float.floatToIntBits(wave)
                    buffer[0] = bits.toByte()
                    buffer[1] = (bits ushr 8).toByte()
                    buffer[2] = (bits ushr 16).toByte()
                    buffer[3] = (bits ushr 24).toByte()
                    out.write(buffer)
                }
            }
            val track=AudioProject("sintético",file,16000,1,176000,0.5f,floatArrayOf(0.5f))
            val phrase=SongPhraseAnalyzer.analyze(track,0.0)
            assertNotNull(phrase)
            assertTrue(phrase!!.reliableWindows >= 10)
            assertTrue(phrase.highHz > phrase.lowHz)
        } finally { file.delete() }
    }

    @Test fun silenceCannotProduceMelody() {
        val file = File.createTempFile("pitch_silence_",".f32")
        try {
            FileOutputStream(file).use { it.write(ByteArray(16000*4*5)) }
            val track=AudioProject("silêncio",file,16000,1,80000,0f,floatArrayOf(0f))
            assertNull(SongPhraseAnalyzer.analyze(track,0.0))
        } finally { file.delete() }
    }

    @Test fun matchBasedOnMeasuredRanges() {
        val voice=VoicePitchAnalyzer.Profile(165.0,220.0,294.0,24)
        val song=SongPhraseAnalyzer.Phrase(220.0,294.0,392.0,24)
        val result=ToneMatchAdvisor.suggest(voice,song)
        assertNotNull(result)
        assertTrue(result!!.recommended < 0)
        assertEquals(result.recommended,result.alternatives.first())
    }
}
''',encoding="utf-8")
print("PitchStudio 1.9.9: comparação com áudio original e testes de trecho PCM adicionados.")
