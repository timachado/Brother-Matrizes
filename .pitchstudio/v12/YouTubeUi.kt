package br.com.timachado.pitchstudio

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import java.io.File

object YouTubeUi {
    fun show(
        activity: Activity,
        cacheDir: File,
        onStatus: (String) -> Unit,
        onProgress: (String, Int) -> Unit,
        onDownloaded: (File, String) -> Unit,
        onError: (String) -> Unit
    ) {
        val input = EditText(activity).apply {
            hint = "Nome da música, artista ou link do YouTube"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(false)
        }

        AlertDialog.Builder(activity)
            .setTitle("Buscar ou importar do YouTube")
            .setMessage("Digite a música/artista ou cole o link do YouTube. O áudio será carregado no Pitch Studio.")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Continuar") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isBlank()) return@setPositiveButton

                if (YouTubeImporter.isYouTubeUrl(value)) {
                    importUrl(activity, cacheDir, value, onStatus, onProgress, onDownloaded, onError)
                } else {
                    search(activity, cacheDir, value, onStatus, onProgress, onDownloaded, onError)
                }
            }
            .show()
    }

    private fun search(
        activity: Activity,
        cacheDir: File,
        query: String,
        onStatus: (String) -> Unit,
        onProgress: (String, Int) -> Unit,
        onDownloaded: (File, String) -> Unit,
        onError: (String) -> Unit
    ) {
        onStatus("Pesquisando no YouTube…")

        Thread {
            try {
                val results = YouTubeImporter.search(query)

                activity.runOnUiThread {
                    if (results.isEmpty()) {
                        onError("Nenhum resultado encontrado no YouTube.")
                        return@runOnUiThread
                    }

                    val labels = results.map { result ->
                        val duration = if (result.durationSeconds > 0) {
                            val total = result.durationSeconds
                            val min = total / 60
                            val sec = total % 60
                            " • %02d:%02d".format(min, sec)
                        } else ""

                        if (result.uploader.isBlank()) {
                            result.title + duration
                        } else {
                            result.title + "\n" + result.uploader + duration
                        }
                    }.toTypedArray()

                    AlertDialog.Builder(activity)
                        .setTitle("Resultados do YouTube")
                        .setItems(labels) { _, which ->
                            importUrl(
                                activity,
                                cacheDir,
                                results[which].url,
                                onStatus,
                                onProgress,
                                onDownloaded,
                                onError
                            )
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
            } catch (t: Throwable) {
                activity.runOnUiThread {
                    onError("Falha na busca do YouTube: " + (t.message ?: "erro de extração"))
                }
            }
        }.apply {
            name = "PitchStudio-YouTubeSearch"
            isDaemon = true
            start()
        }
    }

    fun importUrl(
        activity: Activity,
        cacheDir: File,
        url: String,
        onStatus: (String) -> Unit,
        onProgress: (String, Int) -> Unit,
        onDownloaded: (File, String) -> Unit,
        onError: (String) -> Unit
    ) {
        onStatus("Preparando áudio do YouTube…")

        Thread {
            var downloaded: File? = null
            try {
                val result = YouTubeImporter.downloadBestAudio(activity, url) { message, progress ->
                    activity.runOnUiThread { onProgress(message, progress) }
                }
                downloaded = result.file
                activity.runOnUiThread { onDownloaded(result.file, result.displayName) }
                downloaded = null
            } catch (t: Throwable) {
                downloaded?.delete()
                activity.runOnUiThread {
                    onError("Falha ao importar do YouTube: " + (t.message ?: "conteúdo indisponível"))
                }
            }
        }.apply {
            name = "PitchStudio-YouTubeDownload"
            isDaemon = true
            start()
        }
    }
}
