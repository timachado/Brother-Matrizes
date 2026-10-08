from pathlib import Path
import sys

root = Path(sys.argv[1])
p = root / "app/src/main/java/br/com/timachado/pitchstudio/MainActivity.kt"
s = p.read_text(encoding="utf-8")
i = s.find("YouTubeUi.show")
print("INDEX", i)
if i >= 0:
    print(s[max(0, i-1200):i+2200])
else:
    print(s[:5000])
raise SystemExit("diagnóstico v1.2 concluído")
