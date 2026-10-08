from pathlib import Path
import sys

root = Path(sys.argv[1])

# Versão 1.4.0
p = root / "app/build.gradle.kts"
s = p.read_text(encoding="utf-8")
s = s.replace("versionCode = 4", "versionCode = 5")
s = s.replace('versionName = "1.3.0"', 'versionName = "1.4.0"')
p.write_text(s, encoding="utf-8")

# Registra a tela estilo NewPipe dentro do app.
p = root / "app/src/main/AndroidManifest.xml"
s = p.read_text(encoding="utf-8")
activity_decl = '''        <activity
            android:name=".YouTubeBrowserActivity"
            android:exported="false" />
'''
if 'android:name=".YouTubeBrowserActivity"' not in s:
    s = s.replace("    </application>", activity_decl + "    </application>", 1)
p.write_text(s, encoding="utf-8")

# MainActivity: botão YouTube abre a tela própria, e o resultado volta para o editor.
p = root / "app/src/main/java/br/com/timachado/pitchstudio/MainActivity.kt"
s = p.read_text(encoding="utf-8")

old_button = 'online.addView(button("Abrir YouTube") { openYouTube() }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(6) })'
new_button = 'online.addView(button("YouTube") { openYouTubeBrowser() }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(6) })'
if old_button not in s:
    raise SystemExit("botão Abrir YouTube da v1.3 não encontrado")
s = s.replace(old_button, new_button, 1)

s = s.replace(
    'text("Abra o YouTube, escolha a música e toque em Compartilhar → Pitch Studio. O áudio será carregado automaticamente.", 12f, false)',
    'text("Pesquise como no NewPipe: abra o vídeo para conferir ou baixe o áudio direto para o Pitch Studio.", 12f, false)',
    1
)

# Corrige também o parser legado do compartilhamento, caso o usuário use esse caminho.
s = s.replace(
    '"""https?://(?:www.)?(?:youtube.com|music.youtube.com|youtu.be)/[^s]+"""',
    '"""https?://(?:www\\.)?(?:youtube\\.com|music\\.youtube\\.com|youtu\\.be)/[^\\s]+"""'
)

marker = "    private fun openYouTube() {"
if marker not in s:
    raise SystemExit("método openYouTube da v1.3 não encontrado")

methods = '''    private fun openYouTubeBrowser() {
        startActivityForResult(
            Intent(this, YouTubeBrowserActivity::class.java),
            7301
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != 7301 || resultCode != RESULT_OK) return

        val path = data?.getStringExtra(YouTubeBrowserActivity.EXTRA_FILE_PATH).orEmpty()
        val name = data?.getStringExtra(YouTubeBrowserActivity.EXTRA_DISPLAY_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: "youtube-audio"

        if (path.isBlank()) {
            toast("O YouTube não retornou um arquivo de áudio.")
            return
        }

        val file = File(path)
        if (!file.exists() || file.length() <= 0L) {
            toast("O arquivo baixado não está disponível.")
            return
        }

        statusLabel.text = "Áudio do YouTube recebido. Carregando no Pitch Studio…"
        loadRemoteAudio(file, name)
    }

'''
s = s.replace(marker, methods + marker, 1)
p.write_text(s, encoding="utf-8")

print("Pitch Studio v1.4 aplicado.")
