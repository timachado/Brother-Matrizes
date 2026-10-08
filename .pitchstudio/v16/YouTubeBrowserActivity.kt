package br.com.timachado.pitchstudio

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.inputmethod.EditorInfo
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import okhttp3.OkHttpClient
import okhttp3.Request
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

    private var activePlayer: ExoPlayer? = null
    private var activePlayerView: PlayerView? = null
    private var activeFrame: FrameLayout? = null
    private var activePlayOverlay: View? = null
    private var pendingSave: YouTubeImporter.Downloaded? = null
    private var searchRequest = 0

    private val thumbClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onDestroy() {
        ++searchRequest
        stopActivePreview()
        pendingSave?.file?.delete()
        pendingSave = null
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(7, 13, 25))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                dp(16) + bars.left, dp(12) + bars.top,
                dp(16) + bars.right, dp(16) + bars.bottom
            )
            insets
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(button("‹") { finish() }.apply {
            contentDescription = "Voltar ao estúdio"
            cornerRadius = dp(24)
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(text("EXPLORAR MÚSICAS", 11f, true).apply {
            setTextColor(Color.rgb(43, 219, 230))
            letterSpacing = 0.11f
        })
        heading.addView(text("YouTube", 26f, true))
        header.addView(heading, LinearLayout.LayoutParams(0, wrap(), 1f).apply {
            marginStart = dp(12)
        })
        root.addView(header)

        root.addView(
            text(
                "Encontre sua música, assista à prévia em vídeo e escolha como usar o áudio.",
                14f,
                false
            ),
            full(wrap(), top = 4)
        )

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val searchField = TextInputLayout(this).apply {
            hint = "Música, artista ou link"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_FILLED
            boxBackgroundColor = Color.rgb(23, 35, 55)
            boxStrokeWidth = 0
            boxStrokeWidthFocused = dp(2)
            setBoxStrokeColor(Color.rgb(43, 219, 230))
            setBoxCornerRadii(
                dp(22).toFloat(), dp(22).toFloat(),
                dp(22).toFloat(), dp(22).toFloat()
            )
            endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
        }
        queryInput = TextInputEditText(this).apply {
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(173, 189, 210))
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    submit()
                    true
                } else false
            }
        }
        searchField.addView(queryInput, ViewGroup.LayoutParams(-1, dp(56)))
        searchRow.addView(searchField, LinearLayout.LayoutParams(0, dp(68), 1f).apply {
            marginEnd = dp(8)
        })
        searchRow.addView(
            button("Buscar") { submit() }.apply {
                backgroundTintList = ColorStateList.valueOf(Color.rgb(43, 219, 230))
                setTextColor(Color.rgb(7, 13, 25))
            },
            LinearLayout.LayoutParams(dp(96), dp(54))
        )

        root.addView(searchRow, full(wrap(), top = 14))

        progress = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            visibility = View.GONE
        }

        progress.progressTintList = ColorStateList.valueOf(Color.rgb(43, 219, 230))
        progress.progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(45, 58, 80))
        root.addView(progress, full(dp(5), top = 8))

        status = text(
            "Digite uma música, artista ou cole um link do YouTube.",
            13f,
            false
        )

        status.contentDescription = "Status da busca de música"
        root.addView(status, full(wrap(), top = 12))

        resultsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(resultsContainer)
        }

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f).apply {
                topMargin = dp(8)
            }
        )

        setContentView(root)
        ViewCompat.requestApplyInsets(root)
    }

    private fun submit() {
        val value = queryInput.text.toString().trim()
        if (value.isBlank()) {
            status.text = "Digite uma música, artista ou link para pesquisar."
            return
        }
        queryInput.clearFocus()

        if (YouTubeImporter.isYouTubeUrl(value)) {
            loadSingleResult(value)
        } else {
            search(value)
        }
    }

    private fun loadSingleResult(url: String) {
        stopActivePreview()
        val request = ++searchRequest
        status.text = "Carregando vídeo do YouTube…"
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = true
        resultsContainer.removeAllViews()

        Thread {
            try {
                val result = YouTubeImporter.resultFromUrl(url)

                runOnUiThread {
                    progress.visibility = View.GONE
                    progress.isIndeterminate = false
                    if (request != searchRequest || isFinishing || isDestroyed) return@runOnUiThread
                    status.text = "Vídeo encontrado."
                    addResultCard(result)
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    progress.isIndeterminate = false
                    if (request != searchRequest || isFinishing || isDestroyed) return@runOnUiThread
                    status.text = "Falha ao abrir o vídeo: " +
                        YouTubeImporter.friendlyMessage(t)
                }
            }
        }.apply {
            name = "PitchStudio-YouTubeLink"
            isDaemon = true
            start()
        }
    }

    private fun search(query: String) {
        stopActivePreview()
        val request = ++searchRequest
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

                    if (request != searchRequest || isFinishing || isDestroyed) return@runOnUiThread
                    if (results.isEmpty()) {
                        status.text = "Nenhum resultado encontrado."
                        return@runOnUiThread
                    }

                    status.text =
                        results.size.toString() + " resultados encontrados."

                    results.forEach { addResultCard(it) }
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    progress.isIndeterminate = false
                    if (request != searchRequest || isFinishing || isDestroyed) return@runOnUiThread
                    status.text = "Falha na busca: " +
                        YouTubeImporter.friendlyMessage(t)
                }
            }
        }.apply {
            name = "PitchStudio-YouTubeSearch"
            isDaemon = true
            start()
        }
    }

    private fun addResultCard(result: YouTubeImporter.Result) {
        val card = MaterialCardView(this).apply {
            radius = dp(26).toFloat()
            strokeWidth = dp(1)
            strokeColor = Color.rgb(49, 66, 92)
            cardElevation = 0f
            setCardBackgroundColor(Color.rgb(19, 30, 49))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(13), dp(13), dp(14))
        }
        card.addView(content)

        val mediaFrame = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = dp(18).toFloat()
            }
            clipToOutline = true
            contentDescription = "Prévia em vídeo"
        }

        val thumb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(45, 57, 70))
        }

        mediaFrame.addView(
            thumb,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val playOverlay = button("▶ Ver vídeo") {
            previewInCard(result, mediaFrame, playOverlayRef = null)
        }.apply {
            backgroundTintList = ColorStateList.valueOf(Color.argb(230, 33, 43, 69))
            setTextColor(Color.WHITE)
            contentDescription = "Reproduzir prévia de " + result.title
        }

        val overlayParams = FrameLayout.LayoutParams(
            dp(154),
            dp(52),
            Gravity.CENTER
        )

        mediaFrame.addView(playOverlay, overlayParams)

        // Reatribui o clique para poder esconder exatamente este botão.
        playOverlay.setOnClickListener {
            previewInCard(result, mediaFrame, playOverlay)
        }

        content.addView(mediaFrame, full(dp(188)))

        val title = text(result.title, 18f, true).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        content.addView(title, full(wrap(), top = 12))

        val duration = if (result.durationSeconds > 0) {
            val total = result.durationSeconds
            " • %02d:%02d".format(total / 60, total % 60)
        } else {
            ""
        }

        content.addView(
            text(result.uploader + duration, 13f, false),
            full(wrap(), top = 3)
        )

        content.addView(
            button("Usar no PitchStudio") {
                downloadToPitchStudio(result.url, result.title)
            }.apply {
                backgroundTintList = ColorStateList.valueOf(Color.rgb(43, 219, 230))
                setTextColor(Color.rgb(7, 13, 25))
                contentDescription = "Importar " + result.title + " para o PitchStudio"
            }, full(dp(54), top = 12)
        )
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(
            button("▶ Prévia") {
                previewInCard(result, mediaFrame, playOverlay)
            },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) }
        )
        actions.addView(
            button("Salvar arquivo") {
                saveMusic(result.url, result.title)
            },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(4) }
        )
        content.addView(actions, full(wrap(), top = 8))

        resultsContainer.addView(card, full(wrap(), top = 10))

        if (result.thumbnailUrl.isNotBlank()) {
            loadThumbnail(result.thumbnailUrl, thumb)
        }
    }

    private fun previewInCard(
        result: YouTubeImporter.Result,
        mediaFrame: FrameLayout,
        playOverlayRef: View?
    ) {
        status.text = "Preparando vídeo…"
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = true

        Thread {
            try {
                val preview = YouTubeImporter.resolvePreview(result.url)

                runOnUiThread {
                    stopActivePreview()

                    progress.visibility = View.GONE
                    progress.isIndeterminate = false
                    status.text = "Reproduzindo: " + preview.title

                    val httpFactory = DefaultHttpDataSource.Factory()
                        .setUserAgent(
                            "Mozilla/5.0 (Linux; Android 15) " +
                                "AppleWebKit/537.36 Chrome/140.0 Mobile Safari/537.36"
                        )
                        .setAllowCrossProtocolRedirects(true)
                        .setDefaultRequestProperties(
                            mapOf(
                                "Accept" to "*/*",
                                "Referer" to "https://www.youtube.com/"
                            )
                        )

                    val mediaSourceFactory = DefaultMediaSourceFactory(this)
                        .setDataSourceFactory(httpFactory)

                    val player = ExoPlayer.Builder(this)
                        .setMediaSourceFactory(mediaSourceFactory)
                        .build()

                    val playerView = PlayerView(this).apply {
                        useController = true
                        this.player = player
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        setBackgroundColor(Color.BLACK)
                    }

                    val params = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    mediaFrame.addView(playerView, params)
                    playOverlayRef?.visibility = View.GONE

                    player.setMediaItem(MediaItem.fromUri(preview.url))
                    player.prepare()
                    player.playWhenReady = true

                    activePlayer = player
                    activePlayerView = playerView
                    activeFrame = mediaFrame
                    activePlayOverlay = playOverlayRef
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    progress.isIndeterminate = false
                    status.text = "Falha na prévia: " +
                        YouTubeImporter.friendlyMessage(t)
                }
            }
        }.apply {
            name = "PitchStudio-YouTubePreview"
            isDaemon = true
            start()
        }
    }

    private fun stopActivePreview() {
        val player = activePlayer
        val playerView = activePlayerView
        val frame = activeFrame
        val overlay = activePlayOverlay

        try {
            player?.stop()
            player?.release()
        } catch (_: Throwable) {
        }

        if (playerView != null) {
            try {
                playerView.player = null
                frame?.removeView(playerView)
            } catch (_: Throwable) {
            }
        }

        overlay?.visibility = View.VISIBLE

        activePlayer = null
        activePlayerView = null
        activeFrame = null
        activePlayOverlay = null
    }

    private fun downloadToPitchStudio(
        url: String,
        title: String
    ) {
        stopActivePreview()

        status.text = "Preparando " + title + "…"
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = false
        progress.progress = 2

        Thread {
            try {
                val downloaded = YouTubeImporter.downloadBestAudio(
                    this,
                    url
                ) { message, value ->
                    runOnUiThread {
                        status.text = message
                        progress.progress = value.coerceIn(0, 100)
                    }
                }

                runOnUiThread {
                    progress.progress = 100
                    status.text = "Carregando no Pitch Studio…"

                    val result = Intent().apply {
                        putExtra(
                            EXTRA_FILE_PATH,
                            downloaded.file.absolutePath
                        )
                        putExtra(
                            EXTRA_DISPLAY_NAME,
                            downloaded.displayName
                        )
                    }

                    setResult(RESULT_OK, result)
                    finish()
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    status.text = "Falha ao baixar: " +
                        YouTubeImporter.friendlyMessage(t)
                }
            }
        }.apply {
            name = "PitchStudio-YouTubeDownload"
            isDaemon = true
            start()
        }
    }

    private fun saveMusic(
        url: String,
        title: String
    ) {
        stopActivePreview()

        status.text = "Preparando a música para salvar…"
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = false
        progress.progress = 2

        Thread {
            try {
                val downloaded = YouTubeImporter.downloadBestAudio(
                    this,
                    url
                ) { message, value ->
                    runOnUiThread {
                        status.text = message
                        progress.progress = value.coerceIn(0, 100)
                    }
                }

                runOnUiThread {
                    pendingSave?.file?.delete()
                    pendingSave = downloaded
                    progress.visibility = View.GONE

                    val ext = downloaded.displayName
                        .substringAfterLast('.', "m4a")
                        .lowercase()

                    val mime = when (ext) {
                        "mp3" -> "audio/mpeg"
                        "mp4", "m4a" -> "audio/mp4"
                        "ogg" -> "audio/ogg"
                        "webm" -> "audio/webm"
                        else -> "audio/*"
                    }

                    val intent = Intent(
                        Intent.ACTION_CREATE_DOCUMENT
                    ).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = mime
                        putExtra(
                            Intent.EXTRA_TITLE,
                            downloaded.displayName
                        )
                    }

                    startActivityForResult(intent, REQ_SAVE)
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    status.text = "Falha ao salvar: " +
                        YouTubeImporter.friendlyMessage(t)
                }
            }
        }.apply {
            name = "PitchStudio-YouTubeSave"
            isDaemon = true
            start()
        }
    }

    @Deprecated("Compatibilidade com Android sem Activity Result API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
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
                    requireNotNull(contentResolver.openOutputStream(uri, "w")) {
                        "Não foi possível abrir o destino de salvamento."
                    }.use { output ->
                            input.copyTo(output, 128 * 1024)
                        }
                }

                downloaded.file.delete()

                runOnUiThread {
                    status.text =
                        "Música salva com sucesso no aparelho."
                }
            } catch (_: Throwable) {
                downloaded.file.delete()

                runOnUiThread {
                    status.text =
                        "Falha ao salvar a música no aparelho."
                }
            }
        }.apply {
            name = "PitchStudio-SaveMusic"
            isDaemon = true
            start()
        }
    }

    private fun loadThumbnail(
        url: String,
        target: ImageView
    ) {
        Thread {
            try {
                val response = thumbClient.newCall(
                    Request.Builder()
                        .url(url)
                        .build()
                ).execute()

                response.use {
                    if (!it.isSuccessful) return@use

                    val bytes = it.body?.bytes()
                        ?: return@use

                    val bitmap = BitmapFactory.decodeByteArray(
                        bytes,
                        0,
                        bytes.size
                    ) ?: return@use

                    runOnUiThread {
                        target.setImageBitmap(bitmap)
                    }
                }
            } catch (_: Throwable) {
            }
        }.apply {
            name = "PitchStudio-Thumbnail"
            isDaemon = true
            start()
        }
    }

    private fun button(
        label: String,
        action: () -> Unit
    ): MaterialButton =
        MaterialButton(this).apply {
            text = label
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
            setTextColor(Color.rgb(236, 245, 255))
            backgroundTintList = ColorStateList.valueOf(Color.rgb(42, 55, 80))
            cornerRadius = dp(20)
            insetTop = 0
            insetBottom = 0
            minHeight = dp(48)
            minWidth = 0
            setPadding(dp(9), 0, dp(9), 0)
            setOnClickListener { action() }
        }

    private fun text(
        value: String,
        size: Float,
        bold: Boolean
    ): TextView =
        TextView(this).apply {
            text = value
            textSize = size

            setTextColor(
                if (bold) Color.WHITE
                else Color.rgb(174, 193, 211)
            )

            if (bold) {
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            }
            includeFontPadding = false
            setLineSpacing(dp(2).toFloat(), 1.0f)
        }

    private fun full(
        height: Int,
        top: Int = 0
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            -1,
            height
        ).apply {
            topMargin = dp(top)
        }

    private fun wrap(): Int =
        LinearLayout.LayoutParams.WRAP_CONTENT

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
