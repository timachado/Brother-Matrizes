#!/usr/bin/env python3
"""PitchStudio 1.10.8 — cancelamento explícito da separação vocal."""
from pathlib import Path
import sys
root=Path(sys.argv[1])
pkg=root/"app/src/main/java/br/com/timachado/pitchstudio"
activity=pkg/"TomIdealActivity.kt"
s=activity.read_text(encoding="utf-8")
def once(before,after):
    global s
    n=s.count(before)
    if n!=1: raise SystemExit(f"Marcador único 1.10.8 esperado, encontrado {n}: {before[:110]}")
    s=s.replace(before,after,1)

once('''    private var vocalDurationSeconds = 6''',
'''    private var vocalDurationSeconds = 6
    private lateinit var vocalCancelButton: com.google.android.material.button.MaterialButton''')

once('''        add(choices, vocalSeparatorButton, 8)
        add(choices, text(''',
'''        add(choices, vocalSeparatorButton, 8)
        vocalCancelButton = button("Cancelar isolamento") { cancelVocalIsolation() }
        vocalCancelButton.isEnabled = false
        add(choices, vocalCancelButton, 8)
        add(choices, text(''')

once('''    private fun isolateVocalExcerpt() {''',
'''    private fun cancelVocalIsolation() {
        val running = vocalSeparatorThread
        if (running?.isAlive != true) return
        // Invalidar callbacks ainda enfileirados da inferência cancelada.
        ++songAnalysisGeneration
        vocalCancelButton.isEnabled = false
        running.interrupt()
        phraseReading = false
        phrase = null
        songError = null
        showOptions(voiceProfile)
        trackStatus.text = "Cancelando isolamento vocal e limpando temporários…"
    }

    private fun isolateVocalExcerpt() {''')

once('''        vocalSeparatorButton.isEnabled = false
        vocalDurationButton.isEnabled = false
        val requestedDuration''',
'''        vocalSeparatorButton.isEnabled = false
        vocalDurationButton.isEnabled = false
        vocalCancelButton.isEnabled = true
        val requestedDuration''')

once('''                        vocalSeparatorButton.isEnabled = true
                        vocalDurationButton.isEnabled = true
                    }''',
'''                        vocalSeparatorButton.isEnabled = true
                        vocalDurationButton.isEnabled = true
                        vocalCancelButton.isEnabled = false
                        if (request != songAnalysisGeneration)
                            trackStatus.text = "Isolamento vocal cancelado."
                    }''')

# Ao sair da Activity, impedir reaproveitamento do botão se inferência prosseguir.
once('''        vocalSeparatorThread?.interrupt()
        clearIsolatedPreview()''',
'''        vocalSeparatorThread?.interrupt()
        if (::vocalCancelButton.isInitialized) vocalCancelButton.isEnabled = false
        clearIsolatedPreview()''')

activity.write_text(s,encoding="utf-8")
gradle=root/"app/build.gradle.kts"
g=gradle.read_text(encoding="utf-8")
for v in ("versionCode = 27",'versionName = "1.10.7"','applicationIdSuffix = ".ytqa"'):
    assert g.count(v)==1,f"Base errada: {v}"
g=g.replace("versionCode = 27","versionCode = 28",1)
g=g.replace('versionName = "1.10.7"','versionName = "1.10.8"',1)
g=g.replace('applicationIdSuffix = ".ytqa"','applicationIdSuffix = ".vocalqa"',1)
gradle.write_text(g,encoding="utf-8")
print("PitchStudio 1.10.8: cancelamento explícito de IA preservando MP3/WAV.")
