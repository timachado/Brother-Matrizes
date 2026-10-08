#!/usr/bin/env python3
"""PitchStudio 1.10.0: seleção de trecho dentro do Tom Ideal e proteção contra resultados antigos."""
from pathlib import Path
import sys
root=Path(sys.argv[1])
base=root/"app/src/main/java/br/com/timachado/pitchstudio"
activity=base/"TomIdealActivity.kt"
s=activity.read_text(encoding="utf-8")
def sub(old,new):
    global s
    count=s.count(old)
    if count != 1: raise SystemExit(f"TomIdeal: marcador esperado uma vez ({count}): {old[:100]}")
    s=s.replace(old,new,1)

sub("import android.widget.ProgressBar\n","import android.widget.ProgressBar\nimport android.widget.SeekBar\n")
sub("    private var phraseReading = false","""    private var phraseReading = false
    private var songAnalysisGeneration = 0
    private lateinit var excerptSeek: SeekBar
    private lateinit var excerptLabel: TextView
    private var fixedChoiceChildren = 3""")
sub("""        trackStatus = text("Música: análise ainda não disponível.", 14f)
        add(choices, trackStatus, 12)
        showOptions(null)""",
"""        trackStatus = text("Música: análise ainda não disponível.", 14f)
        add(choices, trackStatus, 12)
        excerptLabel = text("Trecho: início da música", 13f)
        add(choices, excerptLabel, 14)
        excerptSeek = SeekBar(this).apply {
            max = 100
            progress = (intent.getDoubleExtra(EXTRA_AUDIO_FRACTION, 0.0)
                .coerceIn(0.0, 1.0) * 100.0).toInt()
            progressTintList = ColorStateList.valueOf(Color.rgb(43, 219, 230))
            thumbTintList = ColorStateList.valueOf(Color.rgb(43, 219, 230))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    excerptLabel.text = "Início aproximado do trecho: " + progress + "% da faixa"
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        add(choices, excerptSeek, 4)
        excerptLabel.text = "Início aproximado do trecho: " + excerptSeek.progress + "% da faixa"
        add(choices, button("Analisar trecho selecionado") { analyzeLoadedSong() }, 8)
        fixedChoiceChildren = choices.childCount
        showOptions(null)""")
sub("""    private fun analyzeLoadedSong() {
        val path = intent.getStringExtra(EXTRA_AUDIO_FILE)""",
"""    private fun analyzeLoadedSong() {
        val generation = ++songAnalysisGeneration
        phraseReading = true
        phrase = null
        songError = null
        showOptions(voiceProfile)
        val path = intent.getStringExtra(EXTRA_AUDIO_FILE)""")
sub('''            trackStatus.text = "Importe uma música antes de abrir o Tom Ideal para obter uma comparação."
            return''',
'''            phraseReading = false
            trackStatus.text = "Importe uma música antes de abrir o Tom Ideal para obter uma comparação."
            return''')
sub('''            trackStatus.text = "Áudio original indisponível. Reimporte a música no estúdio."
            return''',
'''            phraseReading = false
            trackStatus.text = "Áudio original indisponível. Reimporte a música no estúdio."
            return''')
sub('''        val fraction = intent.getDoubleExtra(EXTRA_AUDIO_FRACTION, 0.0)''',
'''        val fraction = excerptSeek.progress / 100.0''')
sub('''            trackStatus.text = "Formato de música incompatível com esta análise."
            return''',
'''            phraseReading = false
            trackStatus.text = "Formato de música incompatível com esta análise."
            return''')
sub('''                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        phrase = found''',
'''                runOnUiThread {
                    if (!isFinishing && !isDestroyed && generation == songAnalysisGeneration) {
                        phrase = found''')
sub('''                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        phraseReading = false
                        songError = "Falha ao analisar música: "''',
'''                runOnUiThread {
                    if (!isFinishing && !isDestroyed && generation == songAnalysisGeneration) {
                        phraseReading = false
                        songError = "Falha ao analisar música: "''')
sub('''        while (choices.childCount > 3) choices.removeViewAt(choices.childCount - 1)''',
'''        while (choices.childCount > fixedChoiceChildren)
            choices.removeViewAt(choices.childCount - 1)''')
sub('''    override fun onStop() {
        capturing = false
        super.onStop()
    }''',
'''    override fun onStop() {
        capturing = false
        ++songAnalysisGeneration
        super.onStop()
    }''')
activity.write_text(s,encoding="utf-8")

gpath=root/"app/build.gradle.kts"
g=gpath.read_text(encoding="utf-8")
assert g.count("versionCode = 19")==1
assert g.count('versionName = "1.9.9"')==1
assert g.count('applicationIdSuffix = ".voicematch"')==1
g=g.replace("versionCode = 19","versionCode = 20",1)
g=g.replace('versionName = "1.9.9"','versionName = "1.10.0"',1)
g=g.replace('applicationIdSuffix = ".voicematch"','applicationIdSuffix = ".excerpts"',1)
gpath.write_text(g,encoding="utf-8")
print("PitchStudio 1.10.0: seletor de trechos reanalisáveis e proteção de concorrência.")
