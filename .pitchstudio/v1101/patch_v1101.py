#!/usr/bin/env python3
"""PitchStudio v1.10.1 — detecção espectral conservadora para melodia provável."""
from pathlib import Path
import sys
root=Path(sys.argv[1])
pkg=root/"app/src/main/java/br/com/timachado/pitchstudio"
song=pkg/"SongPhraseAnalyzer.kt"
s=song.read_text(encoding="utf-8")
start=s.index("        val hop = (project.sampleRate * 0.32)")
end=s.index("        if (selected.size < 10) return null",start)
s=s[:start]+'''        // Até 40 janelas de 4096 amostras a aproximadamente 16 kHz.
        // Redução por média previne aliasing forte de pratos/percussão.
        val reduction = max(1, (project.sampleRate / 16000.0).roundToInt())
        val effectiveRate = project.sampleRate / reduction
        val fftSize = 4096
        val readSize = fftSize * reduction
        val hop = (project.sampleRate * 0.32).roundToInt().coerceAtLeast(readSize)
        val channels = project.channels
        val frameBytes = 4 * channels
        val buffer = ByteArray(readSize * frameBytes)
        val pcm = FloatArray(fftSize)
        val selected = ArrayList<VoicePitchAnalyzer.Note>(40)
        RandomAccessFile(project.pcmFile, "r").use { input ->
            var frame = start
            val end = start + durationFrames
            while (frame + readSize <= end && selected.size < 40) {
                if (Thread.currentThread().isInterrupted) return null
                input.seek(frame * frameBytes)
                input.readFully(buffer)
                for (i in 0 until fftSize) {
                    var sum = 0.0
                    for (j in 0 until reduction) {
                        val index = (i * reduction + j) * channels
                        for (channel in 0 until channels) {
                            val offset = (index + channel) * 4
                            val bits = (buffer[offset].toInt() and 255) or
                                ((buffer[offset + 1].toInt() and 255) shl 8) or
                                ((buffer[offset + 2].toInt() and 255) shl 16) or
                                ((buffer[offset + 3].toInt() and 255) shl 24)
                            val sample = Float.fromBits(bits)
                            if (sample.isFinite()) sum += sample / channels
                        }
                    }
                    pcm[i] = (sum / reduction).toFloat().coerceIn(-1f, 1f)
                }
                MelodyFocusEstimator.estimate(pcm, effectiveRate)
                    ?.takeIf { it.confidence >= 0.17 && it.hz in 130.0..880.0 }
                    ?.let {
                        // Compatibilidade com o perfil de notas existente.
                        selected.add(VoicePitchAnalyzer.Note(it.hz, it.confidence))
                    }
                frame += hop
            }
        }
'''+s[end:]
# Evita leitura inconsistente de uma gravação claramente percussiva:
# no mínimo 3 notas distinguíveis precisam aparecer.
old='''        val values = selected.map { it.hz }.sorted()
        fun pct(p: Double): Double {'''
new='''        val values = selected.map { it.hz }.sorted()
        val noteClasses = values.map { (12.0 * log2(it / 440.0)).roundToInt() }
            .distinct()
        if (noteClasses.size < 3) return null
        fun pct(p: Double): Double {'''
assert s.count(old)==1
s=s.replace(old,new,1)
song.write_text(s,encoding="utf-8")
assert (pkg/"MelodyFocusEstimator.kt").is_file()

activity=pkg/"TomIdealActivity.kt"
t=activity.read_text(encoding="utf-8")
a='''"Analisamos as notas predominantes em um trecho de até 11 segundos a partir da posição do player. Em gravações com banda, o instrumento dominante pode ser confundido com a voz."'''
b='''"A análise usa frequências e harmônicos para reduzir interferência do baixo. Ela NÃO separa a voz de outros instrumentos e pode deixar de sugerir um tom quando não houver evidência suficiente."'''
assert t.count(a)==1
t=t.replace(a,b,1)
activity.write_text(t,encoding="utf-8")

gradle=root/"app/build.gradle.kts"
g=gradle.read_text(encoding="utf-8")
assert g.count("versionCode = 20")==1 and g.count('versionName = "1.10.0"')==1
assert g.count('applicationIdSuffix = ".excerpts"')==1
g=g.replace("versionCode = 20","versionCode = 21",1)
g=g.replace('versionName = "1.10.0"','versionName = "1.10.1"',1)
g=g.replace('applicationIdSuffix = ".excerpts"','applicationIdSuffix = ".melody"',1)
gradle.write_text(g,encoding="utf-8")

testdir=root/"app/src/test/java/br/com/timachado/pitchstudio"
testdir.mkdir(parents=True,exist_ok=True)
(testdir/"MelodyFocusEstimatorTest.kt").write_text(r'''package br.com.timachado.pitchstudio

import org.junit.Assert.*
import org.junit.Test
import br.com.timachado.pitchstudio.audio.AudioProject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

class MelodyFocusEstimatorTest {
    private val sampleRate=16000

    private fun tone(frequency:Double, volume:Double=0.5): FloatArray =
        FloatArray(4096) {
            (volume * sin(it * 2.0 * PI * frequency / sampleRate)).toFloat()
        }

    @Test fun pureSungNoteIsFound() {
        val note=MelodyFocusEstimator.estimate(tone(329.63), sampleRate)
        assertNotNull(note)
        assertEquals(329.63,note!!.hz,15.0)
    }

    @Test fun bassFundamentalAndItsSecondHarmonicAreNotMelody() {
        val bass=FloatArray(4096) { i ->
            (0.72 * sin(i * 2.0 * PI * 110.0 / sampleRate) +
             0.19 * sin(i * 2.0 * PI * 220.0 / sampleRate)).toFloat()
        }
        assertNull(MelodyFocusEstimator.estimate(bass,sampleRate))
    }

    @Test fun weakMelodyCanRemainVisibleAlongsideBass() {
        val mixed=FloatArray(4096) { i ->
            (0.55 * sin(i * 2.0 * PI * 110.0 / sampleRate) +
             0.12 * sin(i * 2.0 * PI * 220.0 / sampleRate) +
             0.36 * sin(i * 2.0 * PI * 329.63 / sampleRate)).toFloat()
        }
        val result=MelodyFocusEstimator.estimate(mixed,sampleRate)
        assertNotNull(result)
        assertEquals(329.63,result!!.hz,17.0)
    }

    @Test fun silenceAndImpulsesDoNotLookLikeStableVocalNote() {
        assertNull(MelodyFocusEstimator.estimate(FloatArray(4096),sampleRate))
        val impulse=FloatArray(4096)
        impulse[170]=0.9f
        assertNull(MelodyFocusEstimator.estimate(impulse,sampleRate))
    }

    @Test fun melodyOfMultipleNotesCanBeReadFromPcm() {
        val file=File.createTempFile("pitch_melody_focus_",".f32")
        val notes=doubleArrayOf(261.63,293.66,329.63,349.23)
        try {
            FileOutputStream(file).use { out ->
                val buf=ByteArray(4)
                for (i in 0 until 16000 * 11) {
                    val fundamental=notes[(i / 16000) % notes.size]
                    val wave=(0.48 * sin(i * 2.0 * PI * fundamental / sampleRate) +
                         0.26 * sin(i * 2.0 * PI * 110.0 / sampleRate)).toFloat()
                    val bits=java.lang.Float.floatToIntBits(wave)
                    buf[0]=bits.toByte()
                    buf[1]=(bits ushr 8).toByte()
                    buf[2]=(bits ushr 16).toByte()
                    buf[3]=(bits ushr 24).toByte()
                    out.write(buf)
                }
            }
            val track=AudioProject("trecho",file,16000,1,176000,0.75f,floatArrayOf(0.3f))
            val phrase=SongPhraseAnalyzer.analyze(track,0.0)
            assertNotNull(phrase)
            assertTrue(phrase!!.reliableWindows >= 10)
            assertTrue(phrase.highHz > phrase.lowHz)
        } finally { file.delete() }
    }
}
''',encoding="utf-8")
print("PitchStudio 1.10.1 — filtro espectral, subharmônicos, testes de baixo+voz e fallback seguro.")
