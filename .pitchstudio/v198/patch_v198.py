#!/usr/bin/env python3
"""PitchStudio v1.9.8 Tom Ideal, intervenção mínima sobre a versão 1.9.7."""
from pathlib import Path
import sys,re
root=Path(sys.argv[1])
base=root/"app/src/main/java/br/com/timachado/pitchstudio"
main=base/"MainActivity.kt"
s=main.read_text(encoding="utf-8")
def one(a,b):
    global s
    count=s.count(a)
    if count!=1: raise SystemExit(f"Marcador único esperado, encontrado {count}: {a[:100]}")
    s=s.replace(a,b,1)

one(
    '                column.addView(actions, full(wrap(), top = 12))\n            }\n            panel.addView(column)',
    '''                column.addView(actions, full(wrap(), top = 12))
                column.addView(
                    button("Tom Ideal · Analisar minha voz") { openTomIdeal() },
                    full(dp(52), top = 8)
                )
            }
            panel.addView(column)'''
)
one(
    '    private fun restoreEditorPreferences() {',
    '''    private fun openTomIdeal() {
        val voice = Intent(this, TomIdealActivity::class.java).apply {
            putExtra(TomIdealActivity.EXTRA_CURRENT_SHIFT, semitones)
        }
        startActivityForResult(voice, 9412)
    }

    private fun restoreEditorPreferences() {'''
)
one(
    '''        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) {''',
    '''        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 9412) {
            if (resultCode == RESULT_OK && data != null &&
                data.hasExtra(TomIdealActivity.EXTRA_CHOSEN_SHIFT)) {
                val chosen = data.getIntExtra(TomIdealActivity.EXTRA_CHOSEN_SHIFT, semitones)
                applyPitchPreset(chosen.coerceIn(-12, 12), cents)
                statusLabel.text = "Tom selecionado: " + chosen + " semitons. Ouça e compare no player."
            }
            return
        }
        if (resultCode != RESULT_OK) {'''
)
main.write_text(s,encoding="utf-8")

for name in ("TomIdealActivity.kt","VoicePitchAnalyzer.kt"):
    assert (base/name).is_file(), name+" ausente"

manifest=root/"app/src/main/AndroidManifest.xml"
m=manifest.read_text(encoding="utf-8")
permission='<uses-permission android:name="android.permission.RECORD_AUDIO" />'
if permission not in m:
    opening=re.search(r'<manifest\b[^>]*>',m)
    assert opening, "Tag manifest não encontrada"
    m=m[:opening.end()] + '\n    ' + permission + m[opening.end():]
assert permission in m, "Permissão não foi inserida"
match=re.search(r'<application\b[^>]*>',m)
assert match, "Tag application não localizada"
m=m[:match.end()]+'\n        <activity android:name=".TomIdealActivity" android:exported="false" />'+m[match.end():]
manifest.write_text(m,encoding="utf-8")

gradle=root/"app/build.gradle.kts"
g=gradle.read_text(encoding="utf-8")
assert g.count("versionCode = 17")==1 and g.count('versionName = "1.9.7"')==1
assert g.count('applicationIdSuffix = ".library"')==1
g=g.replace("versionCode = 17","versionCode = 18",1)
g=g.replace('versionName = "1.9.7"','versionName = "1.9.8"',1)
g=g.replace('applicationIdSuffix = ".library"','applicationIdSuffix = ".voice"',1)
g+='\n\ndependencies { testImplementation("junit:junit:4.13.2") }\n'
gradle.write_text(g,encoding="utf-8")

testdir=root/"app/src/test/java/br/com/timachado/pitchstudio"
testdir.mkdir(parents=True,exist_ok=True)
(testdir/"VoicePitchAnalyzerTest.kt").write_text("""package br.com.timachado.pitchstudio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class VoicePitchAnalyzerTest {
    private fun tone(hz: Double, rate: Int = 16000, length: Int = 4096): ShortArray =
        ShortArray(length) {
            (sin(it.toDouble() * 2.0 * PI * hz / rate) * 18000.0).toInt().toShort()
        }

    @Test fun detectsA3() {
        val result = VoicePitchAnalyzer.estimate(tone(220.0), 4096, 16000)
        assertNotNull(result)
        assertEquals(220.0, result!!.hz, 9.0)
    }

    @Test fun detectsA4() {
        val result = VoicePitchAnalyzer.estimate(tone(440.0), 4096, 16000)
        assertNotNull(result)
        assertEquals(440.0, result!!.hz, 12.0)
        assertEquals("Lá4", VoicePitchAnalyzer.noteName(440.0))
    }

    @Test fun rejectsSilence() {
        assertNull(VoicePitchAnalyzer.estimate(ShortArray(4096), 4096, 16000))
    }

    @Test fun profileNeedsEnoughVoicedWindows() {
        assertNull(VoicePitchAnalyzer.profile(
            List(5) { VoicePitchAnalyzer.Note(200.0, 0.9) }
        ))
        val valid=VoicePitchAnalyzer.profile(
            List(12) { VoicePitchAnalyzer.Note(220.0 + it, 0.95) }
        )
        assertNotNull(valid)
        assertEquals(12, valid!!.voicedWindows)
    }
}
""",encoding="utf-8")
print("PitchStudio 1.9.8: Tom Ideal, microfone solicitado sob demanda, testes YIN adicionados.")
