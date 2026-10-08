from pathlib import Path
import sys

root = Path(sys.argv[1])

# Versão 1.3.0
p = root / "app/build.gradle.kts"
s = p.read_text(encoding="utf-8")
s = s.replace("versionCode = 3", "versionCode = 4")
s = s.replace('versionName = "1.2.0"', 'versionName = "1.3.0"')
p.write_text(s, encoding="utf-8")

# Manifest: receber Compartilhar -> Pitch Studio.
p = root / "app/src/main/AndroidManifest.xml"
s = p.read_text(encoding="utf-8")
if 'android:launchMode="singleTask"' not in s:
    s = s.replace(
        'android:exported="true"\n            android:screenOrientation="unspecified">',
        'android:exported="true"\n            android:launchMode="singleTask"\n            android:screenOrientation="unspecified">'
    )

launcher = '''            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
'''
share = launcher + '''            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
'''
if "android.intent.action.SEND" not in s:
    if launcher not in s:
        raise SystemExit("intent-filter launcher não encontrado")
    s = s.replace(launcher, share, 1)
p.write_text(s, encoding="utf-8")

# MainActivity: abrir o YouTube real e receber a escolha via Compartilhar.
p = root / "app/src/main/java/br/com/timachado/pitchstudio/MainActivity.kt"
s = p.read_text(encoding="utf-8")

old_button = '''online.addView(button("Buscar no YouTube") {
    YouTubeUi.show(
    activity = this,
    cacheDir = cacheDir,
    onStatus = { statusLabel.text = it },
    onProgress = { message, value -> showProgress(message, value) },
    onDownloaded = { file, name -> loadRemoteAudio(file, name) },
    onError = { message -> hideProgress("Falha no YouTube: " + message) }
)
}, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(6) })'''
new_button = '''online.addView(button("Abrir YouTube") { openYouTube() }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(6) })'''
if old_button not in s:
    raise SystemExit("bloco exato do botão YouTube não encontrado")
s = s.replace(old_button, new_button, 1)

s = s.replace(
    'text("YouTube: pesquise ou cole o link; o áudio é baixado temporariamente e carregado no Pitch Studio.", 12f, false)',
    'text("Abra o YouTube, escolha a música e toque em Compartilhar → Pitch Studio. O áudio será carregado automaticamente.", 12f, false)',
    1
)

old_oncreate = '''    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        handler.post(progressTick)
    }
'''
new_oncreate = '''    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        handler.post(progressTick)
        handler.post { handleIncomingShare(intent) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handler.post { handleIncomingShare(intent) }
    }
'''
if old_oncreate not in s:
    raise SystemExit("onCreate não encontrado")
s = s.replace(old_oncreate, new_oncreate, 1)

marker = "    private fun loadRemoteAudio("
if marker not in s:
    raise SystemExit("loadRemoteAudio não encontrado")

methods = r'''    private fun openYouTube() {
        val youtubeHome = Uri.parse("https://www.youtube.com/")
        val appIntent = Intent(Intent.ACTION_VIEW, youtubeHome).apply {
            setPackage("com.google.android.youtube")
        }

        try {
            startActivity(appIntent)
        } catch (_: Throwable) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, youtubeHome))
            } catch (_: Throwable) {
                toast("Não foi possível abrir o YouTube neste aparelho.")
                return
            }
        }

        statusLabel.text = "No YouTube, escolha a música e toque em Compartilhar → Pitch Studio."
    }

    private fun handleIncomingShare(incoming: Intent?) {
        if (incoming?.action != Intent.ACTION_SEND) return

        val shared = incoming.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val url = extractYouTubeUrl(shared)

        // Limpa o Intent para não repetir a importação numa recriação da tela.
        setIntent(Intent())

        if (url == null) {
            toast("O compartilhamento não contém um link do YouTube.")
            return
        }

        statusLabel.text = "Música recebida do YouTube. Preparando download…"

        YouTubeUi.importUrl(
            activity = this,
            cacheDir = cacheDir,
            url = url,
            onStatus = { statusLabel.text = it },
            onProgress = { message, value -> showProgress(message, value) },
            onDownloaded = { file, name -> loadRemoteAudio(file, name) },
            onError = { message -> hideProgress("Falha no YouTube: " + message) }
        )
    }

    private fun extractYouTubeUrl(text: String): String? {
        val regex = Regex(
            """https?://(?:www.)?(?:youtube.com|music.youtube.com|youtu.be)/[^s]+""",
            RegexOption.IGNORE_CASE
        )
        return regex.find(text)?.value?.trimEnd('.', ',', ';', ')', ']', '}')
    }

'''
s = s.replace(marker, methods + marker, 1)
p.write_text(s, encoding="utf-8")

print("Pitch Studio v1.3 aplicado.")
