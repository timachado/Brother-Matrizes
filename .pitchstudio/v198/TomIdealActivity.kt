package br.com.timachado.pitchstudio

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.ProgressBar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlin.math.max

/**
 * Vocalizador privado, sem armazenamento ou envio da gravação.
 * Não presume que extensão de voz isolada define o tom ideal da música.
 */
class TomIdealActivity : Activity() {
    companion object {
        const val EXTRA_CURRENT_SHIFT = "tom_ideal_shift"
        const val EXTRA_CHOSEN_SHIFT = "tom_ideal_chosen_shift"
        private const val MIC_REQUEST = 814
    }

    private val bg = Color.rgb(7, 13, 25)
    private val cyan = Color.rgb(43, 219, 230)
    private val fg = Color.rgb(245, 248, 255)
    @Volatile private var capturing = false
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var recordButton: MaterialButton
    private lateinit var choices: LinearLayout
    private var currentShift = 0

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(if (bold) fg else Color.rgb(178, 192, 215))
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setLineSpacing(dp(2).toFloat(), 1f)
    }
    private fun button(value: String, primary: Boolean = false, action: () -> Unit) =
        MaterialButton(this).apply {
            text = value
            isAllCaps = false
            minHeight = dp(52)
            cornerRadius = dp(20)
            backgroundTintList = ColorStateList.valueOf(
                if (primary) cyan else Color.rgb(40, 54, 79)
            )
            setTextColor(if (primary) bg else fg)
            setOnClickListener { action() }
        }
    private fun add(parent: LinearLayout, child: android.view.View, space: Int = 10) {
        parent.addView(child, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(space)
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentShift = intent.getIntExtra(EXTRA_CURRENT_SHIFT, 0).coerceIn(-12, 12)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(dp(16) + bars.left, dp(10) + bars.top,
                dp(16) + bars.right, dp(26) + bars.bottom)
            insets
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(root)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(button("‹") { finish() },
            LinearLayout.LayoutParams(dp(56), dp(52)))
        header.addView(text("Tom Ideal", 26f, true),
            LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dp(8) })
        add(root, header, 0)
        add(root, text("Conheça as notas da sua voz e compare tons para cantar.", 15f))

        fun panel(heading: String, explanation: String): LinearLayout {
            val card = MaterialCardView(this).apply {
                radius = dp(28).toFloat()
                strokeWidth = dp(1)
                strokeColor = Color.rgb(50, 65, 91)
                cardElevation = 0f
                setCardBackgroundColor(Color.rgb(18, 28, 47))
            }
            val body = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(17), dp(16), dp(18))
            }
            add(body, text(heading, 20f, true), 0)
            add(body, text(explanation, 14f), 8)
            card.addView(body)
            add(root, card, 18)
            return body
        }

        val vocal = panel(
            "Analise seu alcance nesta amostra",
            "Cante sozinho, sem forçar, por até 12 segundos: comece numa nota confortável, passe pelas graves e termine nas agudas."
        )
        status = text("Nenhuma análise iniciada.", 15f)
        add(vocal, status, 16)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(cyan)
            progressBackgroundTintList =
                ColorStateList.valueOf(Color.rgb(45, 58, 80))
        }
        add(vocal, progress, 10)
        recordButton = button("Analisar minha voz", true) { toggleRecord() }
        add(vocal, recordButton, 10)
        add(vocal, text(
            "Privacidade: áudio captado somente na memória enquanto a análise estiver aberta. Não é salvo nem enviado para a internet.",
            12f
        ))

        choices = panel(
            "Compare três versões",
            "A análise de notas não revela a melodia de uma música. As opções abaixo são alternativas para experimentar no player, não um diagnóstico vocal."
        )
        showOptions(null)
        setContentView(scroll)
        ViewCompat.requestApplyInsets(scroll)
    }

    private fun toggleRecord() {
        if (capturing) {
            capturing = false
            recordButton.isEnabled = false
            status.text = "Concluindo análise…"
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MIC_REQUEST)
            return
        }
        startRecord()
    }

    @Deprecated("Compatibilidade com permissões Android")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode != MIC_REQUEST) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startRecord()
        else status.text = "Permissão negada. O editor continuará funcionando sem microfone."
    }

    private fun startRecord() {
        if (capturing) return
        capturing = true
        progress.progress = 0
        recordButton.text = "Concluir análise"
        status.text = "Ouvindo… cante sem acompanhamento musical."
        Thread({
            var rec: AudioRecord? = null
            val measured = mutableListOf<VoicePitchAnalyzer.Note>()
            try {
                val rate = listOf(16000, 44100).firstOrNull {
                    AudioRecord.getMinBufferSize(it, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT) > 0
                } ?: error("Taxa de microfone incompatível.")
                val size = AudioRecord.getMinBufferSize(
                    rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                rec = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(AudioFormat.Builder()
                        .setSampleRate(rate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                    .setBufferSizeInBytes(max(size * 2, 8192 * 2)).build()
                check(rec.state == AudioRecord.STATE_INITIALIZED) {
                    "Não foi possível acessar o microfone."
                }
                rec.startRecording()
                check(rec.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                val buffer = ShortArray(4096)
                val start = SystemClock.elapsedRealtime()
                while (capturing && SystemClock.elapsedRealtime() - start < 12000L) {
                    var received = 0
                    while (capturing && received < buffer.size) {
                        val n = rec.read(buffer, received, buffer.size - received,
                            AudioRecord.READ_BLOCKING)
                        if (n < 0) error("Falha ao ler microfone (" + n + ")")
                        received += n
                    }
                    if (received == buffer.size) {
                        VoicePitchAnalyzer.estimate(buffer, received, rate)?.let(measured::add)
                    }
                    val fraction = ((SystemClock.elapsedRealtime() - start) * 100 / 12000)
                        .toInt().coerceIn(0, 100)
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            progress.progress = fraction
                            status.text = "Analisando… " + fraction + "% · " +
                                measured.size + " notas reconhecidas"
                        }
                    }
                }
                val profile = VoicePitchAnalyzer.profile(measured)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        progress.progress = 100
                        showOptions(profile)
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        status.text = "Falha na análise: " + (error.message ?: "microfone indisponível")
                    }
                }
            } finally {
                try { rec?.stop() } catch (_: Exception) {}
                try { rec?.release() } catch (_: Exception) {}
                capturing = false
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        recordButton.isEnabled = true
                        recordButton.text = "Analisar novamente"
                    }
                }
            }
        }, "PitchStudio-TomIdeal").start()
    }

    private fun showOptions(profile: VoicePitchAnalyzer.Profile?) {
        if (profile != null) {
            status.text = "Notas identificadas: " +
                VoicePitchAnalyzer.noteName(profile.lowHz) + " até " +
                VoicePitchAnalyzer.noteName(profile.highHz) +
                ". Nota central: " + VoicePitchAnalyzer.noteName(profile.medianHz) +
                " (" + profile.voicedWindows + " amostras válidas)."
        } else if (progress.progress > 0) {
            status.text = "Poucas notas estáveis. Tente cantar novamente sem música de fundo."
        }
        // Alternativas relativas ao tom atual — nenhuma falsa recomendação automática.
        val shifts = listOf(-2 to "Mais grave", 0 to "Atual", 2 to "Mais aguda")
        // Somente os três botões e explicação pertencem ao rodapé deste painel.
        while (choices.childCount > 2) choices.removeViewAt(choices.childCount - 1)
        for ((delta, title) in shifts) {
            val value = (currentShift + delta).coerceIn(-12, 12)
            val description = title + " · " +
                (if (value > 0) "+" else "") + value + " semitons"
            add(choices, button(description, delta == 0) { choose(value) }, 8)
        }
        add(choices, text(
            "Escute cada versão no editor e escolha a que for mais confortável. Não force a voz para alcançar notas.",
            12f
        ))
    }

    private fun choose(semitones: Int) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_CHOSEN_SHIFT, semitones))
        finish()
    }

    override fun onStop() {
        capturing = false
        super.onStop()
    }
    override fun onDestroy() {
        capturing = false
        super.onDestroy()
    }
}
