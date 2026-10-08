#!/usr/bin/env python3
"""PitchStudio 1.10.5 — seleção 6/12/18s, crossfade e limite de memória."""
from pathlib import Path
import sys
root=Path(sys.argv[1])
pkg=root/"app/src/main/java/br/com/timachado/pitchstudio"
activity=pkg/"TomIdealActivity.kt"
s=activity.read_text(encoding="utf-8")
def once(old, new):
    global s
    hits=s.count(old)
    if hits != 1:
        raise SystemExit(f"Patch 1.10.5: marcador esperado uma vez, encontrado {hits}: {old[:120]}")
    s=s.replace(old,new,1)

once('''    private lateinit var vocalSeparatorButton: com.google.android.material.button.MaterialButton''',
'''    private lateinit var vocalSeparatorButton: com.google.android.material.button.MaterialButton
    private lateinit var vocalDurationButton: com.google.android.material.button.MaterialButton
    private var vocalDurationSeconds = 6''')

once('''        vocalSeparatorButton = button("Isolar voz com IA · trecho") { isolateVocalExcerpt() }''',
'''        vocalDurationButton = button("Duração de isolamento: 6 s") {
            if (vocalSeparatorThread?.isAlive == true) {
                trackStatus.text = "Aguarde a separação atual terminar."
            } else {
                vocalDurationSeconds = when (vocalDurationSeconds) {
                    6 -> 12
                    12 -> 18
                    else -> 6
                }
                vocalDurationButton.text = "Duração de isolamento: ${vocalDurationSeconds} s"
            }
        }
        add(choices, vocalDurationButton, 8)
        vocalSeparatorButton = button("Isolar voz com IA · trecho") { isolateVocalExcerpt() }''')

once('''"Pode demorar e consumir memória; o resultado não é enviado nem salvo. " +''',
'''"Pode demorar e consumir memória; o resultado não é enviado. " +''')

once('''        vocalSeparatorButton.isEnabled = false
        phrase = null''',
'''        vocalSeparatorButton.isEnabled = false
        vocalDurationButton.isEnabled = false
        val requestedDuration = vocalDurationSeconds
        phrase = null''')

once('''temp = VocalStemSeparator.separate(this, project, fraction) { message ->''',
'''temp = VocalStemSeparator.separate(this, project, fraction, requestedDuration) { message ->''')

once('''                    if (!isFinishing && !isDestroyed)
                        vocalSeparatorButton.isEnabled = true''',
'''                    if (!isFinishing && !isDestroyed) {
                        vocalSeparatorButton.isEnabled = true
                        vocalDurationButton.isEnabled = true
                    }''')

once('''"O arquivo contém somente o trecho vocal isolado (aproximadamente 5,8 segundos). " +''',
'''"O arquivo contém somente o trecho vocal isolado na duração selecionada. " +''')

activity.write_text(s, encoding="utf-8")

engine=pkg/"VocalStemSeparator.kt"
assert engine.is_file()
assert "VocalChunkPlan.mixInto" in engine.read_text(encoding="utf-8")

gradle=root/"app/build.gradle.kts"
g=gradle.read_text(encoding="utf-8")
assert g.count("versionCode = 24")==1
assert g.count('versionName = "1.10.4"')==1
assert g.count('applicationIdSuffix = ".stemexport"')==1
g=g.replace("versionCode = 24","versionCode = 25",1)
g=g.replace('versionName = "1.10.4"','versionName = "1.10.5"',1)
g=g.replace('applicationIdSuffix = ".stemexport"',
            'applicationIdSuffix = ".longstems"',1)
gradle.write_text(g,encoding="utf-8")
print("PitchStudio 1.10.5: separação vocal 6/12/18s com UI e crossfade.")
