from pathlib import Path
import sys
root=Path(sys.argv[1])
p=root/"app/src/main/java/br/com/timachado/pitchstudio/MainActivity.kt"
s=p.read_text(encoding="utf-8")

if "import kotlin.math.pow" not in s:
    s=s.replace("import kotlin.math.roundToInt","import kotlin.math.roundToInt\nimport kotlin.math.pow")
s=s.replace("    private var processingGeneration = 0","    @Volatile private var processingGeneration = 0\n    private var processedGeneration = -1")
s=s.replace("    private var pendingExport: String? = null","    private var pendingExport: String? = null\n    private var pendingExportProject: AudioProject? = null")
s=s.replace('text("Pesquise como no NewPipe: abra o vídeo para conferir ou baixe o áudio direto para o Pitch Studio.", 12f, false)','text("Pesquise, reproduza o vídeo sobre a própria capa e baixe o áudio direto para o Pitch Studio.", 12f, false)')

old='            setOnSeekBarChangeListener(simpleSeek { setSemitone(it - 12) })'
new='''            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) setSemitone(progress - 12, process = false)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) { scheduleProcess() }
            })'''
assert old in s
s=s.replace(old,new,1)

start=s.index("            setOnSeekBarChangeListener(simpleSeek { cents =")
end=s.index("        }\n        root.addView(centsSeek)",start)
s=s[:start]+'''            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) setCents(progress - 100, process = false)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) { scheduleProcess() }
            })
'''+s[end:]

s=s.replace('root.addView(button("Redefinir tom") { setSemitone(0); centsSeek.progress = 100 }, full(dp(48), top = 4))','root.addView(button("Redefinir tom") { applyPitchPreset(0, 0) }, full(dp(48), top = 4))',1)
s=s.replace('"432 Hz" to { setSemitone(0); centsSeek.progress = 68 },','"432 Hz" to { applyPitchPreset(0, -32) },')
s=s.replace('"½ tom ↓" to { setSemitone(-1); centsSeek.progress = 100 },','"½ tom ↓" to { applyPitchPreset(-1, 0) },')
s=s.replace('"1 tom ↓" to { setSemitone(-2); centsSeek.progress = 100 },','"1 tom ↓" to { applyPitchPreset(-2, 0) },')
s=s.replace('"Guitarra Eb" to { setSemitone(-1); centsSeek.progress = 100 }','"Guitarra Eb" to { applyPitchPreset(-1, 0) }')

start=s.index("            setOnSeekBarChangeListener(simpleSeek {\n                speed =")
end=s.index("        }\n        root.addView(speedSeek)",start)
s=s[:start]+'''            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) setSpeedValue(0.5 + progress / 100.0, process = false)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) { scheduleProcess() }
            })
'''+s[end:]

s=s.replace("player.setProject(p); useProcessed = false; updateAbButton()","player.setProject(p); useProcessed = false; applyPreviewParams(); updateAbButton()")
s=s.replace("                useProcessed = false\n                updateAbButton()","                useProcessed = false\n                applyPreviewParams()\n                updateAbButton()")

p.write_text(s,encoding="utf-8")
