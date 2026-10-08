from pathlib import Path
import sys

root = Path(sys.argv[1])

# Versão 1.5.0
p = root / "app/build.gradle.kts"
s = p.read_text(encoding="utf-8")
s = s.replace("versionCode = 5", "versionCode = 6")
s = s.replace('versionName = "1.4.0"', 'versionName = "1.5.0"')
p.write_text(s, encoding="utf-8")

# Ao mexer em tom/centavos/velocidade, a prévia deve voltar automaticamente
# para o áudio processado. Antes, músicas vindas do YouTube ficavam em
# A/B: Original e a barra mudava sem alterar o som reproduzido.
p = root / "app/src/main/java/br/com/timachado/pitchstudio/MainActivity.kt"
s = p.read_text(encoding="utf-8")

old = '''    private fun scheduleProcess(immediate: Boolean = false) {
        val source = original ?: return
        val generation = ++processingGeneration
'''
new = '''    private fun scheduleProcess(immediate: Boolean = false) {
        val source = original ?: return
        useProcessed = true
        updateAbButton()
        val generation = ++processingGeneration
'''

if old not in s:
    raise SystemExit("scheduleProcess não encontrado")
s = s.replace(old, new, 1)

# Mensagens totalmente em PT-BR para falhas do YouTube.
s = s.replace(
    'hideProgress("Falha no YouTube: " + message)',
    'hideProgress("Falha no YouTube: " + message)'
)

p.write_text(s, encoding="utf-8")
print("Pitch Studio v1.5 aplicado.")
