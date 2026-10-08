#!/usr/bin/env python3
"""Hotfix 1.9.6 do PitchStudio; nenhum recurso de mídia alterado."""
from pathlib import Path
import sys
root=Path(sys.argv[1])
activity=root/"app/src/main/java/br/com/timachado/pitchstudio/YouTubeBrowserActivity.kt"
source=activity.read_text(encoding="utf-8")
assert "queryInput = EditText(this)" in source, "Campo seguro não encontrado"
assert "val searchField = TextInputLayout(this)" not in source, "Campo antigo reapareceu"
assert "ViewCompat.setOnApplyWindowInsetsListener(root)" in source
for marker in ("private fun previewInCard(", "private fun downloadToPitchStudio(", "private fun saveMusic(", "private fun search(query: String)"):
    assert marker in source, marker
gradle=root/"app/build.gradle.kts"
g=gradle.read_text(encoding="utf-8")
assert g.count("versionCode = 14")==1 and g.count('versionName = "1.9.4"')==1
assert g.count('applicationIdSuffix = ".expressive"')==1
g=g.replace("versionCode = 14", "versionCode = 16",1)
g=g.replace('versionName = "1.9.4"', 'versionName = "1.9.6"',1)
g=g.replace('applicationIdSuffix = ".expressive"', 'applicationIdSuffix = ".expressivefix"',1)
gradle.write_text(g,encoding="utf-8")
manifest=root/"app/src/main/AndroidManifest.xml"
m=manifest.read_text(encoding="utf-8")
if 'android:label="PitchStudio"' in m:
    m=m.replace('android:label="PitchStudio"', 'android:label="PitchStudio Fix"',1)
manifest.write_text(m,encoding="utf-8")
print("1.9.6: campo de busca simplificado; interface e funções originais mantidas.")
