#!/usr/bin/env python3
"""Atualiza apenas o identificador da prévia M3 Expressive e confirma a tela do YouTube."""
from pathlib import Path
import sys
root = Path(sys.argv[1])
build = root / "app/build.gradle.kts"
s = build.read_text(encoding="utf-8")
assert s.count("versionCode = 13") == 1
assert s.count('versionName = "1.9.3"') == 1
assert 'applicationIdSuffix = ".expressive"' in s
s = s.replace("versionCode = 13", "versionCode = 14", 1)
s = s.replace('versionName = "1.9.3"', 'versionName = "1.9.4"', 1)
build.write_text(s, encoding="utf-8")
yt = root / "app/src/main/java/br/com/timachado/pitchstudio/YouTubeBrowserActivity.kt"
source = yt.read_text(encoding="utf-8")
for marker in (
    "MaterialCardView(this)",
    "TextInputLayout(this)",
    "ViewCompat.setOnApplyWindowInsetsListener(root)",
    "Usar no PitchStudio",
    "previewInCard(result, mediaFrame, playOverlay)",
    "downloadToPitchStudio(result.url, result.title)",
    "saveMusic(result.url, result.title)",
):
    assert marker in source, f"Componente/ação ausente: {marker}"
print("PitchStudio v1.9.4: busca YouTube Expressive integrada sem alterar fluxos de mídia.")
