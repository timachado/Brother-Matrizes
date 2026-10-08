#!/usr/bin/env python3
"""PitchStudio 1.10.3 — prévia auditiva de voz isolada com ciclo de vida seguro."""
from pathlib import Path
import sys
root=Path(sys.argv[1])
pkg=root/"app/src/main/java/br/com/timachado/pitchstudio"
activity=pkg/"TomIdealActivity.kt"
s=activity.read_text(encoding="utf-8")
def one(a,b):
 global s
 n=s.count(a)
 if n!=1: raise SystemExit(f"TomIdeal v1.10.3: marcador {n}x: {a[:100]}")
 s=s.replace(a,b,1)
one("import br.com.timachado.pitchstudio.audio.AudioProject",
"""import br.com.timachado.pitchstudio.audio.AudioProject
import br.com.timachado.pitchstudio.audio.PcmAudioPlayer""")
one("    private lateinit var vocalSeparatorButton: com.google.android.material.button.MaterialButton",
"""    private lateinit var vocalSeparatorButton: com.google.android.material.button.MaterialButton
    private lateinit var isolatedPreviewButton: com.google.android.material.button.MaterialButton
    private var isolatedVocal: AudioProject? = null
    private val isolatedPlayer = PcmAudioPlayer {
        runOnUiThread {
            if (!isDestroyed && !isFinishing && ::isolatedPreviewButton.isInitialized) {
                isolatedPreviewButton.text = "Ouvir voz isolada"
            }
        }
    }""")
one('''        fixedChoiceChildren = choices.childCount
        showOptions(null)''',
'''        isolatedPreviewButton = button("Ouvir voz isolada") { toggleIsolatedPreview() }
        isolatedPreviewButton.isEnabled = false
        add(choices, isolatedPreviewButton, 8)
        fixedChoiceChildren = choices.childCount
        showOptions(null)''')
# Discard previous preview when user chooses a new segment (prevents stale preview).
one('''    private fun analyzeLoadedSong() {
        val generation = ++songAnalysisGeneration''',
'''    private fun analyzeLoadedSong() {
        if (::isolatedPreviewButton.isInitialized) clearIsolatedPreview()
        val generation = ++songAnalysisGeneration''')
one('''        val fraction = excerptSeek.progress / 100.0
        val request = ++songAnalysisGeneration''',
'''        clearIsolatedPreview()
        val fraction = excerptSeek.progress / 100.0
        val request = ++songAnalysisGeneration''')
start=s.index("                val result = SongPhraseAnalyzer.analyze(temp.vocals,0.0)")
end=s.index("            } catch (t: Throwable) {",start)
if start<0 or end<0: raise SystemExit("Separação de resultado não encontrada")
s=s[:start]+'''                val ready = temp.vocals
                val result = SongPhraseAnalyzer.analyze(ready,0.0)
                // Ownership of the temporary PCM is transferred to the UI callback.
                // The UI explicitly cleans it on leaving/replacing the preview.
                temp = null
                runOnUiThread {
                    if (!isFinishing && !isDestroyed && request == songAnalysisGeneration) {
                        isolatedVocal = ready
                        isolatedPlayer.setProject(ready)
                        isolatedPreviewButton.isEnabled = true
                        isolatedPreviewButton.text = "Ouvir voz isolada"
                        phraseReading = false
                        phrase = result
                        songError = if (result == null)
                            "A voz foi extraída, mas não há notas estáveis suficientes."
                        else null
                        showOptions(voiceProfile)
                        if (result != null)
                            trackStatus.text = "Voz estimada por separação neural: " +
                                VoicePitchAnalyzer.noteName(result.lowHz) + " até " +
                                VoicePitchAnalyzer.noteName(result.highHz) +
                                ". Escute a voz isolada antes de escolher."
                    } else {
                        ready.pcmFile.delete()
                    }
                }
'''+s[end:]
marker='''    private fun isolateVocalExcerpt() {'''
assert s.count(marker)==1
helper='''    private fun clearIsolatedPreview() {
        isolatedPlayer.release()
        isolatedVocal?.pcmFile?.delete()
        isolatedVocal = null
        if (::isolatedPreviewButton.isInitialized) {
            isolatedPreviewButton.isEnabled = false
            isolatedPreviewButton.text = "Ouvir voz isolada"
        }
    }

    private fun toggleIsolatedPreview() {
        val clip = isolatedVocal
        if (clip == null || !clip.pcmFile.isFile) {
            trackStatus.text = "Isole primeiro a voz do trecho selecionado."
            return
        }
        if (isolatedPlayer.isPlaying()) {
            isolatedPlayer.pause()
            isolatedPreviewButton.text = "Continuar prévia vocal"
            trackStatus.text = "Prévia vocal pausada."
        } else {
            isolatedPlayer.play()
            if (isolatedPlayer.isPlaying()) {
                isolatedPreviewButton.text = "Pausar prévia vocal"
                trackStatus.text = "Ouvindo apenas a voz extraída por IA."
            } else {
                isolatedPreviewButton.text = "Ouvir voz isolada"
                trackStatus.text = "Não foi possível reproduzir esta prévia."
            }
        }
    }

'''
s=s.replace(marker,helper+marker,1)
one('''        vocalSeparatorThread?.interrupt()
        super.onStop()''',
'''        vocalSeparatorThread?.interrupt()
        clearIsolatedPreview()
        super.onStop()''')
activity.write_text(s,encoding="utf-8")
gfile=root/"app/build.gradle.kts"
g=gfile.read_text(encoding="utf-8")
assert g.count("versionCode = 22")==1 and g.count('versionName = "1.10.2"')==1
assert g.count('applicationIdSuffix = ".stems"')==1
g=g.replace("versionCode = 22","versionCode = 23",1)
g=g.replace('versionName = "1.10.2"','versionName = "1.10.3"',1)
g=g.replace('applicationIdSuffix = ".stems"','applicationIdSuffix = ".stempreview"',1)
gfile.write_text(g,encoding="utf-8")
print("PitchStudio 1.10.3: prévia vocal Play/Pause integrada, cleanup seguro de PCM temporário.")
