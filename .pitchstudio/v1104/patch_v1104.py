#!/usr/bin/env python3
"""PitchStudio 1.10.4 — salvar voz isolada no aparelho em WAV via SAF."""
from pathlib import Path
import sys
root=Path(sys.argv[1])
pkg=root/"app/src/main/java/br/com/timachado/pitchstudio"
activity=pkg/"TomIdealActivity.kt"
s=activity.read_text(encoding="utf-8")

def one(before, after):
    global s
    n=s.count(before)
    if n!=1: raise SystemExit(f"TomIdeal expected once ({n}): {before[:100]}")
    s=s.replace(before,after,1)

one('import android.Manifest\n', '''import android.Manifest
import android.content.ActivityNotFoundException
import java.io.FileInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.UUID
''')

one('''        private const val MIC_REQUEST = 814''','''        private const val MIC_REQUEST = 814
        private const val EXPORT_WAV_REQUEST = 9024
        private const val WAV_CACHE_STATE = "saved_voice_wav_staging"''')

one('''    private lateinit var isolatedPreviewButton: com.google.android.material.button.MaterialButton''',
'''    private lateinit var isolatedPreviewButton: com.google.android.material.button.MaterialButton
    private lateinit var isolatedSaveButton: com.google.android.material.button.MaterialButton
    private var stagedWav: File? = null
    private var exportInProgress = false''')

one('''        buildUi()
        analyzeLoadedSong()''',
'''        buildUi()
        val restoredPath = savedInstanceState?.getString(WAV_CACHE_STATE)
        if (restoredPath != null) {
            val restored = File(restoredPath)
            if (restored.parentFile?.canonicalFile == cacheDir.canonicalFile &&
                restored.name.startsWith("pitch_voice_export_") &&
                restored.name.endsWith(".wav") && restored.isFile) {
                stagedWav = restored
            }
        }
        analyzeLoadedSong()''')

one('''        isolatedPreviewButton.isEnabled = false
        add(choices, isolatedPreviewButton, 8)
        fixedChoiceChildren = choices.childCount''',
'''        isolatedPreviewButton.isEnabled = false
        add(choices, isolatedPreviewButton, 8)
        isolatedSaveButton = button("Salvar voz isolada · WAV") { startVocalExport() }
        isolatedSaveButton.isEnabled = false
        add(choices, isolatedSaveButton, 8)
        add(choices, text(
            "O arquivo contém somente o trecho vocal isolado (aproximadamente 5,8 segundos). " +
            "Salvar usa a pasta que você escolher no Android.", 12f
        ), 4)
        fixedChoiceChildren = choices.childCount''')

one('''            isolatedPreviewButton.isEnabled = false
            isolatedPreviewButton.text = "Ouvir voz isolada"''',
'''            isolatedPreviewButton.isEnabled = false
            isolatedPreviewButton.text = "Ouvir voz isolada"
            if (::isolatedSaveButton.isInitialized) isolatedSaveButton.isEnabled = false''')

one('''                        isolatedPreviewButton.isEnabled = true
                        isolatedPreviewButton.text = "Ouvir voz isolada"''',
'''                        isolatedPreviewButton.isEnabled = true
                        isolatedPreviewButton.text = "Ouvir voz isolada"
                        isolatedSaveButton.isEnabled = true''')

marker='''    private fun clearIsolatedPreview() {'''
if s.count(marker)!=1: raise SystemExit("Missing preview method")
methods=r'''    /** Exportação em duas fases. O seletor SAF chama onStop(), que remove o
     * áudio da prévia. Primeiro geramos WAV em cache independente; só então
     * abrimos o seletor de destino, para não perder dados durante a pausa.
     */
    private fun startVocalExport() {
        if (exportInProgress || stagedWav != null) {
            trackStatus.text = "Finalize ou cancele a exportação atual."
            return
        }
        val clip = isolatedVocal
        if (clip == null || !clip.pcmFile.isFile) {
            trackStatus.text = "Isole a voz antes de salvar."
            return
        }
        exportInProgress = true
        isolatedSaveButton.isEnabled = false
        trackStatus.text = "Preparando WAV da voz isolada…"
        val wav = File(cacheDir, "pitch_voice_export_" + UUID.randomUUID() + ".wav")
        Thread({
            try {
                StemWavExporter.exportToFile(
                    clip.pcmFile, clip.sampleRate, clip.channels, clip.frames, wav
                )
                runOnUiThread {
                    if (isDestroyed || isFinishing) {
                        wav.delete()
                        return@runOnUiThread
                    }
                    exportInProgress = false
                    stagedWav = wav
                    val create = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "audio/wav"
                        putExtra(Intent.EXTRA_TITLE, "PitchStudio_Voz_Isolada.wav")
                    }
                    try {
                        trackStatus.text = "Escolha onde salvar o WAV no Android."
                        startActivityForResult(create, EXPORT_WAV_REQUEST)
                    } catch (error: ActivityNotFoundException) {
                        wav.delete()
                        stagedWav = null
                        isolatedSaveButton.isEnabled = isolatedVocal != null
                        trackStatus.text = "Nenhum gerenciador de arquivos disponível."
                    }
                }
            } catch (error: Throwable) {
                wav.delete()
                runOnUiThread {
                    if (!isDestroyed && !isFinishing) {
                        exportInProgress = false
                        isolatedSaveButton.isEnabled = isolatedVocal != null
                        trackStatus.text = "Não foi possível preparar o WAV: " +
                            (error.message ?: "erro de exportação")
                    }
                }
            }
        }, "PitchStudio-PrepareVocalWav").start()
    }

    @Deprecated("Compatibilidade com seletor de documentos Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != EXPORT_WAV_REQUEST) return
        val prepared = stagedWav
        stagedWav = null
        if (prepared == null) {
            trackStatus.text = "O WAV temporário não está mais disponível."
            return
        }
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) {
            prepared.delete()
            trackStatus.text = "Salvamento cancelado. O áudio original não foi alterado."
            return
        }
        exportInProgress = true
        trackStatus.text = "Salvando WAV no destino selecionado…"
        Thread({
            try {
                val stream = contentResolver.openOutputStream(uri, "w")
                    ?: error("Não foi possível abrir o destino selecionado.")
                stream.use { dest ->
                    BufferedInputStream(FileInputStream(prepared), 64 * 1024).use { source ->
                        BufferedOutputStream(dest, 64 * 1024).use { buffered ->
                            source.copyTo(buffered, 64 * 1024)
                            buffered.flush()
                        }
                    }
                }
                runOnUiThread {
                    if (!isDestroyed && !isFinishing)
                        trackStatus.text = "Voz isolada salva em WAV no local escolhido."
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    if (!isDestroyed && !isFinishing)
                        trackStatus.text = "Erro ao salvar WAV: " +
                            (error.message ?: "destino indisponível")
                }
            } finally {
                prepared.delete()
                runOnUiThread {
                    if (!isDestroyed && !isFinishing) {
                        exportInProgress = false
                        isolatedSaveButton.isEnabled = isolatedVocal != null
                    }
                }
            }
        }, "PitchStudio-SaveVocalWav").start()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        stagedWav?.takeIf { it.isFile }?.let { outState.putString(WAV_CACHE_STATE, it.absolutePath) }
        super.onSaveInstanceState(outState)
    }

'''
s=s.replace(marker,methods+marker,1)
# Prevent any WAV staging file being deleted when the SAF picker opens. The
# short-lived PCM preview can and should still be released by onStop().
activity.write_text(s,encoding="utf-8")
assert (pkg/"StemWavExporter.kt").exists()

gradle=root/"app/build.gradle.kts"
g=gradle.read_text(encoding="utf-8")
assert g.count("versionCode = 23")==1 and g.count('versionName = "1.10.3"')==1
assert g.count('applicationIdSuffix = ".stempreview"')==1
g=g.replace("versionCode = 23", "versionCode = 24", 1)
g=g.replace('versionName = "1.10.3"', 'versionName = "1.10.4"', 1)
g=g.replace('applicationIdSuffix = ".stempreview"',
            'applicationIdSuffix = ".stemexport"', 1)
gradle.write_text(g,encoding="utf-8")
print("PitchStudio 1.10.4: salvar voz isolada via ACTION_CREATE_DOCUMENT com WAV PCM16.")
