#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1])
p=root/"app/src/main/java/br/com/timachado/pitchstudio/MainActivity.kt"
s=p.read_text(encoding="utf-8")
def one(a,b):
    global s
    n=s.count(a)
    if n!=1: raise SystemExit(f"Marcador inesperado {n}: {a[:85]!r}")
    s=s.replace(a,b,1)

one('        setContentView(scroll)\n', '''        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val top = if (android.os.Build.VERSION.SDK_INT >= 30)
                insets.getInsets(android.view.WindowInsets.Type.statusBars()).top
            else insets.systemWindowInsetTop
            val bottom = if (android.os.Build.VERSION.SDK_INT >= 30)
                insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
            else insets.systemWindowInsetBottom
            view.setPadding(0, top, 0, bottom)
            insets
        }
        setContentView(scroll)
''')

one('    private var pendingExportProject: AudioProject? = null',
'''    private var pendingExportProject: AudioProject? = null
    private var exportWhenReady: String? = null
    private var lastProcessingError: String? = null''')

one('''        val generation = ++processingGeneration
        processedGeneration = -1''','''        val generation = ++processingGeneration
        processedGeneration = -1
        lastProcessingError = null''')

one('''                val r = DspEngine.process(source, file, settings) { p -> handler.post { if (generation == processingGeneration) showProgress("Processando áudio… $p%", p) } }''',
'''                val r = DspEngine.process(source, file, settings) { p ->
                    if (generation != processingGeneration || Thread.currentThread().isInterrupted)
                        throw java.util.concurrent.CancellationException("Processamento substituído por novos ajustes.")
                    handler.post { if (generation == processingGeneration) showProgress("Processando áudio… $p%", p) }
                }''')

one('''            hideProgress("Sem processamento: áudio original.")
            return''',
'''            hideProgress("Sem processamento: áudio original.")
            exportWhenReady?.let { requested ->
                exportWhenReady = null
                startExportPicker(requested, source)
            }
            return''')

one('''                    hideProgress("Processamento concluído.")
                    updateKeyLabel()''',
'''                    hideProgress("Processamento concluído.")
                    updateKeyLabel()
                    exportWhenReady?.let { requested ->
                        exportWhenReady = null
                        startExportPicker(requested, out)
                    }''')

one('''                handler.post { if (generation == processingGeneration) hideProgress("Erro no processamento: ${t.message}") }''',
'''                handler.post {
                    if (generation == processingGeneration) {
                        lastProcessingError = t.message ?: "Falha ao processar arquivo."
                        exportWhenReady = null
                        hideProgress("Falha ao processar áudio: $lastProcessingError. Troque a qualidade e tente novamente.")
                    }
                }''')

one('''    private fun createExport(type: String) {
        val loaded = original ?: run { toast("Carregue uma música primeiro."); return }
        if (processedGeneration != processingGeneration || processed == null) {
            toast("Aguarde o processamento atual terminar antes de exportar.")
            return
        }
        val p = processed ?: loaded
        if (!p.pcmFile.isFile || p.pcmFile.length() <= 0L) {
            toast("O áudio processado não está disponível. Reimporte a música.")
            return
        }
        pendingExportProject = p
        pendingExport = type''',
'''    private fun createExport(type: String) {
        if (original == null) {
            toast("Carregue uma música primeiro.")
            return
        }
        if (processedGeneration != processingGeneration || processed == null) {
            if (lastProcessingError != null) {
                toast("Não foi possível processar a música. Escolha outra qualidade e tente novamente.")
                return
            }
            exportWhenReady = type
            statusLabel.text = "Preparando áudio para exportação. O salvamento abrirá automaticamente ao concluir."
            return
        }
        startExportPicker(type, processed ?: original ?: return)
    }

    private fun startExportPicker(type: String, p: AudioProject) {
        if (!p.pcmFile.isFile || p.pcmFile.length() <= 0L) {
            toast("O áudio não está disponível. Importe a música novamente.")
            return
        }
        pendingExportProject = p
        pendingExport = type''')

one('''        val o = original; val p = processed
        if (p != null && p !== o) p.pcmFile.delete()''',
'''        exportWhenReady = null
        lastProcessingError = null
        val o = original; val p = processed
        if (p != null && p !== o) p.pcmFile.delete()''')
p.write_text(s,encoding="utf-8")

p=root/"app/src/main/java/br/com/timachado/pitchstudio/audio/DspEngine.kt"
s=p.read_text(encoding="utf-8")
start=s.index("    private fun findBestOffset(")
end=s.index("    private fun cubic(",start)
s=s[:start]+'''    private fun findBestOffset(
        reader: PcmReader,
        tail: FloatArray,
        predicted: Long,
        search: Int,
        step: Int,
        overlap: Int,
        channels: Int,
        frames: Long
    ): Long {
        val hardMax = max(0L, frames - overlap - 1L)
        val minPos = (predicted - search).coerceIn(0L, hardMax)
        val maxPos = (predicted + search).coerceIn(minPos, hardMax)
        if (maxPos <= minPos) return minPos

        val coarseStep = max(12, step * 4)
        val coarseSampleStep = if (step == 1) 8 else if (step == 2) 12 else 16
        val fineSampleStep = max(2, coarseSampleStep / 2)
        fun score(at: Long, sampleStep: Int): Double {
            var dot = 0.0
            var aa = 1e-12
            var bb = 1e-12
            var i = 0
            while (i < overlap) {
                var a = 0.0
                var b = 0.0
                for (ch in 0 until channels) {
                    a += tail[i * channels + ch]
                    b += reader.sample(at + i, ch)
                }
                a /= channels
                b /= channels
                dot += a * b
                aa += a * a
                bb += b * b
                i += sampleStep
            }
            return dot / sqrt(aa * bb)
        }
        var best = minPos
        var bestScore = Double.NEGATIVE_INFINITY
        var pos = minPos
        while (pos <= maxPos) {
            val value = score(pos, coarseSampleStep)
            if (value > bestScore) { bestScore = value; best = pos }
            pos += coarseStep.toLong()
        }
        if (best != maxPos && score(maxPos, coarseSampleStep) > bestScore) best = maxPos
        val lower = (best - coarseStep).coerceAtLeast(minPos)
        val upper = (best + coarseStep).coerceAtMost(maxPos)
        var refined = best
        bestScore = Double.NEGATIVE_INFINITY
        pos = lower
        while (pos <= upper) {
            val value = score(pos, fineSampleStep)
            if (value > bestScore) { bestScore = value; refined = pos }
            pos += step.toLong()
        }
        return refined
    }

'''+s[end:]
p.write_text(s,encoding="utf-8")

p=root/"app/build.gradle.kts"
s=p.read_text(encoding="utf-8")
assert s.count("versionCode = 11")==1
assert s.count('versionName = "1.9.1"')==1
s=s.replace("versionCode = 11","versionCode = 12",1)
s=s.replace('versionName = "1.9.1"','versionName = "1.9.2"',1)
p.write_text(s,encoding="utf-8")
print("PitchStudio 1.9.2: insets, DSP otimizado e exportação sem bloqueio.")
