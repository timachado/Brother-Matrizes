#!/usr/bin/env python3
"""Patch incremental e conservador sobre o projeto PitchStudio v1.9.0 montado no CI."""
from pathlib import Path
import sys

root = Path(sys.argv[1])
main = root / "app/src/main/java/br/com/timachado/pitchstudio/MainActivity.kt"
s = main.read_text(encoding="utf-8")

def one(old, new):
    global s
    found = s.count(old)
    if found != 1:
        raise SystemExit(f"MainActivity: esperado 1 marcador, encontrado {found}: {old[:90]}")
    s = s.replace(old, new, 1)

one(
    "        buildUi()\n        handler.post(progressTick)",
    "        buildUi()\n        restoreEditorPreferences()\n        handler.post(progressTick)"
)

one(
    "    override fun onDestroy() {",
    """    override fun onPause() {
        super.onPause()
        saveEditorPreferences()
    }

    override fun onDestroy() {"""
)

one(
    "    private fun createExport(type: String) {",
    """    private fun restoreEditorPreferences() {
        val preferences = getPreferences(MODE_PRIVATE)
        dark = preferences.getBoolean("dark_mode", true)
        semitones = preferences.getInt("semitones", 0).coerceIn(-12, 12)
        cents = preferences.getInt("cents", 0).coerceIn(-100, 100)
        speed = preferences.getFloat("speed", 1.0f).toDouble().coerceIn(0.5, 2.0)
        pitchSeek.progress = semitones + 12
        centsSeek.progress = cents + 100
        speedSeek.progress = ((speed - 0.5) * 100.0).roundToInt()
        speedLabel.text = String.format(java.util.Locale("pt", "BR"), "%.2fx", speed)
        refreshPitchLabels()
        applyPreviewParams()
        applyTheme()
    }

    private fun saveEditorPreferences() {
        getPreferences(MODE_PRIVATE).edit()
            .putBoolean("dark_mode", dark)
            .putInt("semitones", semitones)
            .putInt("cents", cents)
            .putFloat("speed", speed.toFloat())
            .apply()
    }

    private fun createExport(type: String) {"""
)

one(
    """        val p = processed ?: original ?: run { toast("Carregue uma música primeiro."); return }
        if (type == "mp3" && !Mp3Exporter.isSupported()) {
            toast("Este aparelho não possui encoder MP3 nativo. WAV 24-bit continua disponível.")
            return
        }
        pendingExport = type""",
    """        val loaded = original ?: run { toast("Carregue uma música primeiro."); return }
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
        pendingExport = type"""
)

one(
    "        if (resultCode != RESULT_OK) return",
    """        if (resultCode != RESULT_OK) {
            if (requestCode == REQ_EXPORT) {
                pendingExport = null
                pendingExportProject = null
            }
            return
        }"""
)

one(
    """        val type = pendingExport ?: return
        val p = processed ?: original ?: return
        showProgress("Exportando…", 0)""",
    """        val type = pendingExport ?: return
        val p = pendingExportProject ?: return
        pendingExport = null
        pendingExportProject = null
        showProgress("Exportando…", 0)"""
)

one(
    'contentResolver.openOutputStream(uri, "w")!!.use { output ->',
    'requireNotNull(contentResolver.openOutputStream(uri, "w")) { "O armazenamento não permitiu criar o arquivo." }.use { output ->'
)

one(
    "        val generation = ++processingGeneration\n        handler.removeCallbacks(processRunnable)",
    "        val generation = ++processingGeneration\n        processedGeneration = -1\n        handler.removeCallbacks(processRunnable)"
)

one(
    """            processed = source
            val f = player.fraction(); if (useProcessed) player.setProject(source, f)""",
    """            processed = source
            processedGeneration = generation
            val f = player.fraction(); if (useProcessed) player.setProject(source, f)"""
)

one(
    """                    processed = out
                    if (old != null && old !== original""",
    """                    processed = out
                    processedGeneration = generation
                    if (old != null && old !== original"""
)

one(
    """    private fun cleanupProjects() {
        val o = original; val p = processed""",
    """    private fun cleanupProjects() {
        ++processingGeneration
        processedGeneration = -1
        handler.removeCallbacks(processRunnable)
        val o = original; val p = processed"""
)

main.write_text(s, encoding="utf-8")

gradle = root / "app/build.gradle.kts"
g = gradle.read_text(encoding="utf-8")
assert g.count("versionCode = 10") == 1, "Versão base não encontrada"
assert g.count('versionName = "1.9.0"') == 1, "Nome de versão base não encontrado"
g = g.replace("versionCode = 10", "versionCode = 11")
g = g.replace('versionName = "1.9.0"', 'versionName = "1.9.1"')
gradle.write_text(g, encoding="utf-8")

# Ícone conceitual de onda contínua: provisório até a marca definitiva.
res = root / "app/src/main/res"
(res / "drawable").mkdir(parents=True, exist_ok=True)
(res / "mipmap-anydpi-v26").mkdir(parents=True, exist_ok=True)
(res / "values").mkdir(parents=True, exist_ok=True)
(res / "drawable/ic_pitchstudio_mark.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M23,62 C35,62 37,38 51,38 C65,38 65,70 79,70 C84,70 88,66 91,62"
        android:strokeColor="#27D7EA" android:strokeWidth="9"
        android:strokeLineCap="round" android:strokeLineJoin="round"
        android:fillColor="@android:color/transparent"/>
    <path android:pathData="M22,40 C38,40 39,71 53,71 C64,71 71,47 84,47"
        android:strokeColor="#8962F7" android:strokeWidth="5"
        android:strokeLineCap="round" android:strokeLineJoin="round"
        android:fillColor="@android:color/transparent"/>
</vector>
""", encoding="utf-8")
(res / "mipmap-anydpi-v26/ic_launcher.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/pitchstudio_icon_background"/>
    <foreground android:drawable="@drawable/ic_pitchstudio_mark"/>
</adaptive-icon>
""", encoding="utf-8")
(res / "values/pitchstudio_icon_colors.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="pitchstudio_icon_background">#08111D</color>
</resources>
""", encoding="utf-8")
manifest = root / "app/src/main/AndroidManifest.xml"
m = manifest.read_text(encoding="utf-8")
if 'android:icon="' in m:
    import re
    m = re.sub(r'android:icon="[^"]*"', 'android:icon="@mipmap/ic_launcher"', m, count=1)
else:
    assert "<application " in m
    m = m.replace("<application ", '<application android:icon="@mipmap/ic_launcher" ', 1)
if 'android:roundIcon="' not in m:
    m = m.replace("<application ", '<application android:roundIcon="@mipmap/ic_launcher" ', 1)
manifest.write_text(m, encoding="utf-8")
print("PitchStudio 1.9.1: fluxo de exportação protegido, preferências persistidas e ícone conceitual aplicado.")
