#!/usr/bin/env python3
"""PitchStudio 1.10.6 — endurecer exportação sem redesenhar o aplicativo."""
from pathlib import Path
import sys

root=Path(sys.argv[1])
package=root/"app/src/main/java/br/com/timachado/pitchstudio"
main=package/"MainActivity.kt"
s=main.read_text(encoding="utf-8")

def once(old,new):
    global s
    n=s.count(old)
    if n!=1:
        raise RuntimeError(f"Marcador de estabilidade {n}x: {old[:90]!r}")
    s=s.replace(old,new,1)

once("import android.content.Intent", """import android.content.ActivityNotFoundException
import android.content.Intent""")

once("""            REQ_EXPORT -> data?.data?.let { exportTo(it) }""",
"""            REQ_EXPORT -> {
                val destination = data?.data
                if (destination != null) exportTo(destination) else {
                    pendingExport = null
                    pendingExportProject = null
                    statusLabel.text = "Nenhum destino de exportação foi selecionado."
                }
            }""")

once("""        if (!p.pcmFile.isFile || p.pcmFile.length() <= 0L) {
            toast("O áudio não está disponível. Importe a música novamente.")
            return
        }
        pendingExportProject = p""",
"""        val bytesPerFrame = p.channels * 4L
        if (p.channels !in 1..2 || p.frames <= 0L ||
            p.frames > Long.MAX_VALUE / bytesPerFrame ||
            !p.pcmFile.isFile || p.pcmFile.length() != p.frames * bytesPerFrame) {
            toast("O áudio está incompleto. Reimporte a música antes de exportar.")
            return
        }
        pendingExportProject = p""")

once("""        startActivityForResult(i, REQ_EXPORT)
    }

    private fun exportTo(uri: Uri)""",
"""        try {
            startActivityForResult(i, REQ_EXPORT)
        } catch (error: ActivityNotFoundException) {
            pendingExport = null
            pendingExportProject = null
            statusLabel.text = "Nenhum gerenciador de arquivos disponível para salvar."
        } catch (error: SecurityException) {
            pendingExport = null
            pendingExportProject = null
            statusLabel.text = "O Android não permitiu abrir o seletor de arquivos."
        }
    }

    private fun exportTo(uri: Uri)""")

main.write_text(s,encoding="utf-8")

gradle=root/"app/build.gradle.kts"
g=gradle.read_text(encoding="utf-8")
for text in ("versionCode = 25", 'versionName = "1.10.5"', 'applicationIdSuffix = ".longstems"'):
    if g.count(text)!=1: raise RuntimeError(f"Versão-base diferente: {text}")
g=g.replace("versionCode = 25","versionCode = 26",1)
g=g.replace('versionName = "1.10.5"','versionName = "1.10.6"',1)
g=g.replace('applicationIdSuffix = ".longstems"',
            'applicationIdSuffix = ".stableqa"',1)
gradle.write_text(g,encoding="utf-8")
print("PitchStudio 1.10.6: SAF resiliente e validação PCM antes de exportar.")
