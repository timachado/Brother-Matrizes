#!/usr/bin/env python3
"""PitchStudio 1.10.2 — separação vocal MDX-Net local, apenas trecho selecionado."""
from pathlib import Path
import sys
root=Path(sys.argv[1])
pkg=root/"app/src/main/java/br/com/timachado/pitchstudio"
activity=pkg/"TomIdealActivity.kt"
s=activity.read_text(encoding="utf-8")
def sub(a,b):
    global s
    count=s.count(a)
    if count!=1: raise SystemExit(f"TomIdealActivity marcador inesperado {count}: {a[:125]}")
    s=s.replace(a,b,1)

sub("""    private var songAnalysisGeneration = 0""", """    private var songAnalysisGeneration = 0
    private var vocalSeparatorThread: Thread? = null
    private lateinit var vocalSeparatorButton: com.google.android.material.button.MaterialButton""")
sub('''        add(choices, button("Analisar trecho selecionado") { analyzeLoadedSong() }, 8)
        fixedChoiceChildren = choices.childCount''',
'''        add(choices, button("Analisar trecho selecionado") { analyzeLoadedSong() }, 8)
        vocalSeparatorButton = button("Isolar voz com IA · trecho") { isolateVocalExcerpt() }
        add(choices, vocalSeparatorButton, 8)
        add(choices, text(
            "Separação vocal real com MDX-Net, executada neste aparelho. " +
            "Pode demorar e consumir memória; o resultado não é enviado nem salvo. " +
            "Nem toda gravação permite uma voz limpa.", 12f
        ), 6)
        fixedChoiceChildren = choices.childCount''')
needle="    private fun showOptions(profile: VoicePitchAnalyzer.Profile?) {"
assert s.count(needle)==1,"method showOptions missing"
new=r'''    private fun isolateVocalExcerpt() {
        if (vocalSeparatorThread?.isAlive == true) {
            trackStatus.text = "Já existe uma separação vocal em andamento."
            return
        }
        val path = intent.getStringExtra(EXTRA_AUDIO_FILE)
        if (path.isNullOrBlank()) {
            trackStatus.text = "Importe uma música para isolar a voz do trecho."
            return
        }
        val file = java.io.File(path)
        val validPath = try {
            val canonical = file.canonicalPath
            listOf(cacheDir, filesDir).any { home ->
                canonical.startsWith(home.canonicalPath + java.io.File.separator)
            }
        } catch (_: Exception) { false }
        val rate = intent.getIntExtra(EXTRA_AUDIO_RATE, 0)
        val channels = intent.getIntExtra(EXTRA_AUDIO_CHANNELS, 0)
        val frames = intent.getLongExtra(EXTRA_AUDIO_FRAMES, 0L)
        if (!validPath || !file.isFile || rate !in 8000..192000 ||
            channels !in 1..2 || frames < rate*2L ||
            frames > Long.MAX_VALUE / (channels*4L) ||
            file.length() != frames * channels * 4L) {
            trackStatus.text = "Áudio incompatível. Reimporte a música no editor."
            return
        }
        val fraction = excerptSeek.progress / 100.0
        val request = ++songAnalysisGeneration
        vocalSeparatorButton.isEnabled = false
        phrase = null
        phraseReading = true
        songError = null
        showOptions(voiceProfile)
        vocalSeparatorThread = Thread({
            var temp: VocalStemSeparator.Separated? = null
            try {
                val project = AudioProject("Trecho original", file,rate,channels,frames,0f,
                    floatArrayOf(0f))
                temp = VocalStemSeparator.separate(this, project, fraction) { message ->
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && request == songAnalysisGeneration)
                            trackStatus.text = message
                    }
                }
                val result = SongPhraseAnalyzer.analyze(temp.vocals,0.0)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed && request == songAnalysisGeneration) {
                        phraseReading = false
                        phrase = result
                        songError = if (result == null)
                            "A IA separou a faixa vocal, mas não encontrou notas estáveis suficientes."
                        else null
                        showOptions(voiceProfile)
                        if (result != null)
                            trackStatus.text = "Voz estimada por separação neural: " +
                                VoicePitchAnalyzer.noteName(result.lowHz) + " até " +
                                VoicePitchAnalyzer.noteName(result.highHz) +
                                ". Compare ouvindo a faixa original."
                    }
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed && request == songAnalysisGeneration) {
                        phraseReading = false
                        phrase = null
                        songError = "Separação vocal indisponível: " +
                            (t.message ?: "não foi possível executar o modelo.")
                        showOptions(voiceProfile)
                    }
                }
            } finally {
                temp?.vocals?.pcmFile?.delete()
                runOnUiThread {
                    if (!isFinishing && !isDestroyed)
                        vocalSeparatorButton.isEnabled = true
                }
            }
        },"PitchStudio-MDX-VocalStem").apply { start() }
    }

'''
s=s.replace(needle,new+needle,1)
sub('''    override fun onStop() {
        capturing = false
        ++songAnalysisGeneration
        super.onStop()''',
'''    override fun onStop() {
        capturing = false
        ++songAnalysisGeneration
        vocalSeparatorThread?.interrupt()
        super.onStop()''')
activity.write_text(s,encoding="utf-8")

model=pkg/"VocalStemSeparator.kt"
assert model.is_file(), "VocalStemSeparator.kt não copiado"
gradle=root/"app/build.gradle.kts"
g=gradle.read_text(encoding="utf-8")
assert g.count("versionCode = 21")==1 and g.count('versionName = "1.10.1"')==1
assert 'applicationIdSuffix = ".melody"' in g
g=g.replace("versionCode = 21","versionCode = 22",1)
g=g.replace('versionName = "1.10.1"','versionName = "1.10.2"',1)
g=g.replace('applicationIdSuffix = ".melody"','applicationIdSuffix = ".stems"',1)
g += '''
dependencies { implementation("com.microsoft.onnxruntime:onnxruntime-android:1.30.0") }
android { androidResources { noCompress += "onnx" } }
'''
gradle.write_text(g,encoding="utf-8")
print("PitchStudio 1.10.2: separação vocal MDX-Net LiteRT integrada ao Tom Ideal.")
