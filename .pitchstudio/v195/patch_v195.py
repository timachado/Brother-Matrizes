#!/usr/bin/env python3
"""PitchStudio 1.9.5 — correção de inicialização da pesquisa YouTube."""
from pathlib import Path
import sys
root=Path(sys.argv[1])
screen=root/"app/src/main/java/br/com/timachado/pitchstudio/YouTubeBrowserActivity.kt"
s=screen.read_text(encoding="utf-8")
attach="searchField.addView(queryInput, ViewGroup.LayoutParams(-1, dp(56)))"
radius="searchField.setBoxCornerRadii("
assert s.count(attach)==1 and s.count(radius)==1, "TextInputLayout divergente"
assert s.index(attach)<s.index(radius), "Cantos configurados antes do campo de texto"
assert "private fun previewInCard(" in s and "private fun downloadToPitchStudio(" in s and "private fun saveMusic(" in s
gradle=root/"app/build.gradle.kts"
g=gradle.read_text(encoding="utf-8")
assert g.count("versionCode = 14")==1 and g.count('versionName = "1.9.4"')==1
assert 'applicationIdSuffix = ".expressive"' in g
g=g.replace("versionCode = 14", "versionCode = 15",1)
g=g.replace('versionName = "1.9.4"', 'versionName = "1.9.5"',1)
gradle.write_text(g,encoding="utf-8")
print("PitchStudio 1.9.5: inicialização TextInputLayout corrigida; mídia e projeto preservados.")
