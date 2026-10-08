#!/usr/bin/env python3
"""PitchStudio 1.10.7: validar URLs e corrigir callbacks/lifecycle sem redesenhar."""
from pathlib import Path
import sys
root=Path(sys.argv[1])
pkg=root/"app/src/main/java/br/com/timachado/pitchstudio"

importer=pkg/"YouTubeImporter.kt"
s=importer.read_text(encoding="utf-8")
a=s.index("    fun isYouTubeUrl(value: String): Boolean {")
b=s.index("    fun search(query: String",a)
s=s[:a]+'''    fun isYouTubeUrl(value: String): Boolean =
        YouTubeUrlRules.isYouTubeUrl(value)

'''+s[b:]
a=s.index("    fun normalizeYouTubeUrl(value: String): String {")
b=s.index("    fun friendlyMessage(",a)
s=s[:a]+'''    fun normalizeYouTubeUrl(value: String): String =
        YouTubeUrlRules.normalize(value)

'''+s[b:]
importer.write_text(s,encoding="utf-8")

browser=pkg/"YouTubeBrowserActivity.kt"
t=browser.read_text(encoding="utf-8")
def replace_once(old,new):
    global t
    count=t.count(old)
    if count!=1:
        raise SystemExit(f"Browser marcador {count}x: {old[:120]}")
    t=t.replace(old,new,1)

replace_once("import android.content.Intent\n",
    "import android.content.Intent\nimport android.content.ActivityNotFoundException\n")

replace_once("    override fun onDestroy() {\n",
'''    override fun onStop() {
        // Não continuar tocando áudio/vídeo após sair da tela.
        stopActivePreview()
        super.onStop()
    }

    override fun onDestroy() {
''')

replace_once('''                runOnUiThread {
                    stopActivePreview()

                    progress.visibility = View.GONE''',
'''                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    stopActivePreview()

                    progress.visibility = View.GONE''')

replace_once('''                runOnUiThread {
                    progress.visibility = View.GONE
                    progress.isIndeterminate = false
                    status.text = "Falha na prévia: "''',
'''                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    progress.visibility = View.GONE
                    progress.isIndeterminate = false
                    status.text = "Falha na prévia: "''')

replace_once('''                runOnUiThread {
                    progress.progress = 100
                    status.text = "Carregando no Pitch Studio…"''',
'''                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        downloaded.file.delete()
                        return@runOnUiThread
                    }
                    progress.progress = 100
                    status.text = "Carregando no Pitch Studio…"''')

replace_once('''                runOnUiThread {
                    pendingSave?.file?.delete()
                    pendingSave = downloaded''',
'''                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        downloaded.file.delete()
                        return@runOnUiThread
                    }
                    pendingSave?.file?.delete()
                    pendingSave = downloaded''')

replace_once('''                    startActivityForResult(intent, REQ_SAVE)
                }
            } catch (t: Throwable) {''',
'''                    try {
                        startActivityForResult(intent, REQ_SAVE)
                    } catch (error: ActivityNotFoundException) {
                        pendingSave?.file?.delete()
                        pendingSave = null
                        status.text = "Nenhum gerenciador de arquivos disponível."
                    } catch (error: SecurityException) {
                        pendingSave?.file?.delete()
                        pendingSave = null
                        status.text = "O Android não permitiu abrir o seletor."
                    }
                }
            } catch (t: Throwable) {''')

replace_once('''        if (resultCode != RESULT_OK) {
            pendingSave?.file?.delete()
            pendingSave = null
            status.text = "Salvamento cancelado."
            return
        }

        val uri = data?.data ?: return
        val downloaded = pendingSave ?: return''',
'''        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) {
            pendingSave?.file?.delete()
            pendingSave = null
            status.text = "Salvamento cancelado; arquivo temporário descartado."
            return
        }

        val downloaded = pendingSave ?: return''')

replace_once('''                    runOnUiThread {
                        target.setImageBitmap(bitmap)
                    }''',
'''                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) target.setImageBitmap(bitmap)
                    }''')

browser.write_text(t,encoding="utf-8")

gradle=root/"app/build.gradle.kts"
g=gradle.read_text(encoding="utf-8")
for value in ("versionCode = 26", 'versionName = "1.10.6"', 'applicationIdSuffix = ".stableqa"'):
    if g.count(value)!=1: raise SystemExit(f"Base inesperada: {value}")
g=g.replace("versionCode = 26","versionCode = 27",1)
g=g.replace('versionName = "1.10.6"','versionName = "1.10.7"',1)
g=g.replace('applicationIdSuffix = ".stableqa"', 'applicationIdSuffix = ".ytqa"',1)
gradle.write_text(g,encoding="utf-8")
print("PitchStudio 1.10.7: validação URL e fechamento seguro de player/arquivos.")
