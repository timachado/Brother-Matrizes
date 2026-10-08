package br.com.timachado.pitchstudio

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.VideoView
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

class YouTubeBrowserActivity : Activity() {
    companion object {
        const val EXTRA_FILE_PATH = "pitchstudio_file_path"
        const val EXTRA_DISPLAY_NAME = "pitchstudio_display_name"
        private const val REQ_SAVE = 8401
    }

    private lateinit var queryInput: EditText
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var resultsContainer: LinearLayout
    private lateinit var previewBox: LinearLayout
    private lateinit var previewTitle: TextView
    private lateinit var videoView: VideoView

    private var pendingSave: YouTubeImporter.Downloaded? = null

    private val thumbClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onDestroy() {
        try { videoView.stopPlayback() } catch (_: Throwable) {}
        pendingSave?.file?.delete()
        pendingSave = null
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.rgb(6, 17, 28))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(button("‹") { finish() }, LinearLayout.LayoutParams(dp(52), dp(48)))
        header.addView(text("YouTube", 24f, true), LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(header)

        root.addView(
            text(
                "Pesquise, pré-visualize e baixe o áudio direto para o Pitch Studio.",
                13f,
                false
            ),
            full(wrap(), top = 4)
        )

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        queryInput = EditText(this).apply {
            hint = "Música, artista ou link do YouTube"
            setHintTextColor(Color.rgb(135, 154, 174))
            setTextColor(Color.WHITE)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setBackgroundColor(Color.rgb(24, 39, 54))
            setPadding(dp(12), 0, dp(12), 0)
        }

        searchRow.addView(queryInput, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
            marginEnd = dp(8)
        })
        searchRow.addView(button("Buscar") { submit() }, LinearLayout.LayoutParams(dp(92), dp(52)))
        root.addView(searchRow, full(wrap(), top = 14))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        root.addView(progress, full(dp(8), top = 10))

        status = text("Digite uma música ou artista para pesquisar.", 13f, false)
        root.addView(status, full(wrap(), top = 8))

        previewBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(Color.rgb(16, 28, 40))
        }

        previewTitle = text("", 15f, true)
        previewBox.addView(previewTitle)

        videoView = VideoView(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        previewBox.addView(videoView, full(dp(220), top = 8))
        root.addView(previewBox, full(wrap(), top = 10))

        resultsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(resultsContainer)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(8)
        })

        setContentView(root)
    }

    private fun submit() {
        val value = queryInput.text.toString().trim()
        if (value.isBlank()) return

        if (YouTubeImporter.isYouTubeUrl(value)) {
            previewByUrl(value, "Prévia do YouTube")
        } else {
            search(value)
        }
    }

    private fun search(query: String) {
        status.text = "Pesquisando no YouTube…"
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = true
        resultsContainer.removeAllViews()

        Thread {
            try {
                val results = YouTubeImporter.search(query, 20)
                runOnUiThread {
                    progress.visibility = View.GONE
                    progress.isIndeterminate = false

                    if (results.isEmpty()) {
                        status.text = "Nenhum resultado encontrado."
                        return@runOnUiThread
                    }

                    status.text = results.size.toString() + " resultados encontrados."
                    results.forEach { addResultCard(it) }
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    progress.isIndeterminate = false
                    status.text = "Falha na busca: " + YouTubeImporter.friendlyMessage(t)
                }
            }
        }.apply {
            name = "PitchStudio-YouTubeSearch"
            isDaemon = true
            start()
        }
    }

    private fun addResultCard(result: YouTubeImporter.Result) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.rgb(25, 39, 53))
        }

        val main = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }

        val thumb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(45, 57, 70))
        }
        main.addView(thumb, LinearLayout.LayoutParams(dp(128), dp(72)).apply {
            marginEnd = dp(12)
        })

        val meta = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        meta.addView(text(result.title, 16f, true))

        val duration = if (result.durationSeconds > 0) {
            val total = result.durationSeconds
            " • %02d:%02d".format(total / 60, total % 60)
        } else ""

        meta.addView(text(result.uploader + duration, 12f, false))
        main.addView(meta, LinearLayout.LayoutParams(0, wrap(), 1f))
        card.addView(main)

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(
            button("Pré-visualizar") { previewResult(result) },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(6) }
        )
        row1.addView(
            button("Baixar para Pitch Studio") { downloadToPitchStudio(result.url, result.title) },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(6) }
        )
        card.addView(row1, full(wrap(), top = 10))

        card.addView(
            button("Salvar música no aparelho") { saveMusic(result.url, result.title) },
            full(dp(46), top = 8)
        )

        resultsContainer.addView(card, full(wrap(), top = 8))

        if (result.thumbnailUrl.isNotBlank()) {
            loadThumbnail(result.thumbnailUrl, thumb)
        }
    }

    private fun previewResult(result: YouTubeImporter.Result) {
        previewByUrl(result.url, result.title)
    }

    private fun previewByUrl(url: String, title: String) {
        status.text = "Preparando prévia…"
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = true

        Thread {
            try {
                val preview = YouTubeImporter.resolvePreview(url)

                runOnUiThread {
                    progress.visibility = View.GONE
                    progress.isIndeterminate = false
                    previewBox.visibility = View.VISIBLE
                    previewTitle.text = preview.title + " • " + preview.resolution
                    status.text = "Prévia pronta."

                    val controller = MediaController(this)
                    controller.setAnchorView(videoView)
                    videoView.setMediaController(controller)

                    val headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/140.0 Mobile Safari/537.36",
                        "Accept" to "*/*"
                    )

                    videoView.setVideoURI(Uri.parse(preview.url), headers)
                    videoView.setOnPreparedListener {
                        it.isLooping = false
                        videoView.start()
                    }
                    videoView.setOnErrorListener { _, _, _ ->
                        status.text = "Não foi possível reproduzir esta prévia."
                        true
                    }
                    videoView.requestFocus()
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    progress.isIndeterminate = false
                    status.text = "Falha na prévia: " + YouTubeImporter.friendlyMessage(t)
                }
            }
        }.apply {
            name = "PitchStudio-YouTubePreview"
            isDaemon = true
            start()
        }
    }

    private fun downloadToPitchStudio(url: String, title: String) {
        try { videoView.pause() } catch (_: Throwable) {}
        status.text = "Preparando " + title + "…"
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = false
        progress.progress = 2

        Thread {
            try {
                val downloaded = YouTubeImporter.downloadBestAudio(this, url) { message, value ->
                    runOnUiThread {
                        status.text = message
                        progress.progress = value.coerceIn(0, 100)
                    }
                }

                runOnUiThread {
                    progress.progress = 100
                    status.text = "Carregando no Pitch Studio…"

                    val result = Intent().apply {
                        putExtra(EXTRA_FILE_PATH, downloaded.file.absolutePath)
                        putExtra(EXTRA_DISPLAY_NAME, downloaded.displayName)
                    }
                    setResult(RESULT_OK, result)
                    finish()
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    status.text = "Falha ao baixar: " + YouTubeImporter.friendlyMessage(t)
                }
            }
        }.apply {
            name = "PitchStudio-YouTubeDownload"
            isDaemon = true
            start()
        }
    }

    private fun saveMusic(url: String, title: String) {
        try { videoView.pause() } catch (_: Throwable) {}
        status.text = "Preparando a música para salvar…"
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = false
        progress.progress = 2

        Thread {
            try {
                val downloaded = YouTubeImporter.downloadBestAudio(this, url) { message, value ->
                    runOnUiThread {
                        status.text = message
                        progress.progress = value.coerceIn(0, 100)
                    }
                }

                runOnUiThread {
                    pendingSave?.file?.delete()
                    pendingSave = downloaded
                    progress.visibility = View.GONE

                    val ext = downloaded.displayName.substringAfterLast('.', "m4a").lowercase()
                    val mime = when (ext) {
                        "mp3" -> "audio/mpeg"
                        "mp4", "m4a" -> "audio/mp4"
                        "ogg" -> "audio/ogg"
                        "webm" -> "audio/webm"
                        else -> "audio/*"
                    }

                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = mime
                        putExtra(Intent.EXTRA_TITLE, downloaded.displayName)
                    }
                    startActivityForResult(intent, REQ_SAVE)
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    status.text = "Falha ao salvar: " + YouTubeImporter.friendlyMessage(t)
                }
            }
        }.apply {
            name = "PitchStudio-YouTubeSave"
            isDaemon = true
            start()
        }
    }

    @Deprecated("Compatibilidade com Android sem Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_SAVE) return

        if (resultCode != RESULT_OK) {
            pendingSave?.file?.delete()
            pendingSave = null
            status.text = "Salvamento cancelado."
            return
        }

        val uri = data?.data ?: return
        val downloaded = pendingSave ?: return
        pendingSave = null

        Thread {
            try {
                FileInputStream(downloaded.file).use { input ->
                    contentResolver.openOutputStream(uri, "w")!!.use { output ->
                        input.copyTo(output, 128 * 1024)
                    }
                }
                downloaded.file.delete()
                runOnUiThread {
                    status.text = "Música salva com sucesso no aparelho."
                }
            } catch (t: Throwable) {
                downloaded.file.delete()
                runOnUiThread {
                    status.text = "Falha ao salvar a música."
                }
            }
        }.apply {
            name = "PitchStudio-SaveMusic"
            isDaemon = true
            start()
        }
    }

    private fun loadThumbnail(url: String, target: ImageView) {
        Thread {
            try {
                val response = thumbClient.newCall(Request.Builder().url(url).build()).execute()
                response.use {
                    if (!it.isSuccessful) return@use
                    val bytes = it.body?.bytes() ?: return@use
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use
                    runOnUiThread { target.setImageBitmap(bitmap) }
                }
            } catch (_: Throwable) {
            }
        }.apply {
            name = "PitchStudio-Thumbnail"
            isDaemon = true
            start()
        }
    }

    private fun button(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.rgb(198, 222, 238))
            setBackgroundColor(Color.rgb(73, 78, 85))
            setOnClickListener { action() }
        }

    private fun text(value: String, size: Float, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(if (bold) Color.WHITE else Color.rgb(174, 193, 211))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun full(height: Int, top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, height).apply { topMargin = dp(top) }

    private fun wrap(): Int = LinearLayout.LayoutParams.WRAP_CONTENT
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
