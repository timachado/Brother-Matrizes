#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1]);base=root/"app/src/main/java/br/com/timachado/pitchstudio"
main=base/"MainActivity.kt";s=main.read_text(encoding="utf-8")
def sub(a,b):
 global s
 n=s.count(a)
 if n!=1: raise SystemExit("Marcador inválido ("+str(n)+"): "+a[:90])
 s=s.replace(a,b,1)
sub("import android.app.Activity\n","import android.app.Activity\nimport com.google.android.material.dialog.MaterialAlertDialogBuilder\nimport java.text.DateFormat\nimport java.util.Date\n")
sub("    private var lastProcessingError: String? = null","    private var lastProcessingError: String? = null\n    private var libraryOpenGeneration = 0\n    private lateinit var qualitySpinner: Spinner")
sub("        val spinner = Spinner(this)\n        val qLabels","        val spinner = Spinner(this)\n        qualitySpinner = spinner\n        val qLabels")
sub("            panel.addView(column)\n            root.addView(panel,full(wrap(),top=13))",'''            if (kicker == "COMECE POR AQUI") {
                val actions = row()
                actions.addView(button("Salvar projeto") { promptSaveProject() },
                    LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(4) })
                actions.addView(button("Meus projetos") { showSavedProjects() },
                    LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(4) })
                column.addView(actions, full(wrap(), top = 12))
            }
            panel.addView(column)
            root.addView(panel,full(wrap(),top=13))''')
code=r'''    private fun promptSaveProject() {
        val source = original ?: run {
            toast("Importe uma música antes de salvar o projeto.")
            return
        }
        val input = EditText(this).apply {
            setSingleLine(true)
            setText(source.name.substringBeforeLast('.').take(80))
            selectAll()
            hint = "Nome do projeto"
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Salvar projeto")
            .setMessage("Áudio original e ajustes ficam guardados neste aparelho. Ao reabrir, o áudio será processado com os ajustes salvos.")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val title = input.text.toString().trim()
                if (title.isBlank()) {
                    toast("Informe um nome para o projeto.")
                    return@setPositiveButton
                }
                val settings = ProjectLibrary.Settings(
                    title, semitones, cents, speed, quality, loopA, loopB,
                    player.fraction()
                )
                val inputStream = try { FileInputStream(source.pcmFile) }
                catch (t: Exception) {
                    toast("Não foi possível acessar o áudio: " + t.message)
                    return@setPositiveButton
                }
                showProgress("Salvando projeto…", 0)
                executor.execute {
                    try {
                        val entry = ProjectLibrary.save(this, source, inputStream, settings) { value ->
                            handler.post {
                                if (!isDestroyed) showProgress("Salvando projeto… $value%", value)
                            }
                        }
                        handler.post {
                            if (!isDestroyed) {
                                hideProgress("Projeto salvo: " + entry.title)
                                toast("Projeto salvo.")
                            }
                        }
                    } catch (t: Exception) {
                        try { inputStream.close() } catch (_: Exception) {}
                        handler.post {
                            if (!isDestroyed) hideProgress("Falha ao salvar projeto: " + t.message)
                        }
                    }
                }
            }
            .show()
    }

    private fun showSavedProjects() {
        val entries = ProjectLibrary.list(this)
        if (entries.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Meus projetos")
                .setMessage("Nenhum projeto salvo. Importe uma música, ajuste e toque em Salvar projeto.")
                .setPositiveButton("Entendi", null).show()
            return
        }
        val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val labels = entries.map {
            val mb = String.format(java.util.Locale("pt", "BR"), "%.1f MB", it.sizeBytes / 1048576.0)
            it.title + "\n" + formatter.format(Date(it.updatedAt)) + " · " + mb
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Meus projetos (" + entries.size + ")")
            .setItems(labels) { _, index -> showProjectActions(entries[index]) }
            .setNegativeButton("Fechar", null).show()
    }

    private fun showProjectActions(entry: ProjectLibrary.Entry) {
        MaterialAlertDialogBuilder(this)
            .setTitle(entry.title)
            .setItems(arrayOf("Abrir e continuar", "Excluir projeto")) { _, selection ->
                if (selection == 0) openSavedProject(entry) else confirmDeleteProject(entry)
            }
            .setNegativeButton("Voltar") { _, _ -> showSavedProjects() }
            .show()
    }

    private fun confirmDeleteProject(entry: ProjectLibrary.Entry) {
        val current = File(filesDir, "saved_audio_projects/" + entry.id + "/original.f32")
        if (original?.pcmFile?.absolutePath == current.absolutePath) {
            toast("Abra outra música antes de excluir o projeto em uso.")
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Excluir projeto?")
            .setMessage("O áudio e os ajustes salvos de \"" + entry.title + "\" serão apagados definitivamente.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir") { _, _ ->
                try {
                    if (ProjectLibrary.delete(this, entry.id)) {
                        toast("Projeto excluído.")
                        showSavedProjects()
                    } else toast("Não foi possível excluir o projeto.")
                } catch (t: Exception) { toast("Falha ao excluir: " + t.message) }
            }
            .show()
    }

    private fun openSavedProject(entry: ProjectLibrary.Entry) {
        val opening = ++libraryOpenGeneration
        player.pause()
        refreshPlayButton()
        cleanupProjects()
        showProgress("Abrindo projeto…", 0)
        executor.execute {
            try {
                val loaded = ProjectLibrary.open(this, entry.id)
                val estimate = try { KeyEstimator.estimate(loaded.project) }
                               catch (_: Exception) { null }
                handler.post {
                    if (isFinishing || isDestroyed || opening != libraryOpenGeneration) return@post
                    val project = loaded.project
                    val settings = loaded.settings
                    original = project
                    processed = null
                    keyEstimate = estimate
                    fileLabel.text = settings.title
                    semitones = settings.semitones
                    cents = settings.cents
                    speed = settings.speed
                    quality = settings.quality
                    pitchSeek.progress = semitones + 12
                    centsSeek.progress = cents + 100
                    speedSeek.progress = ((speed - 0.5) * 100.0).roundToInt()
                    speedLabel.text = String.format(java.util.Locale("pt", "BR"), "%.2fx", speed)
                    if (::qualitySpinner.isInitialized) qualitySpinner.setSelection(quality.ordinal)
                    refreshPitchLabels()
                    waveform.waveform = project.waveform
                    waveform.progress = settings.position
                    player.setProject(project, settings.position)
                    loopA = settings.loopA
                    loopB = settings.loopB
                    applyLoop()
                    useProcessed = false
                    applyPreviewParams()
                    updateAbButton()
                    updateKeyLabel()
                    hideProgress("Projeto aberto: " + settings.title)
                    scheduleProcess(immediate = true)
                }
            } catch (t: Exception) {
                handler.post {
                    if (!isDestroyed && opening == libraryOpenGeneration)
                        hideProgress("Não foi possível abrir o projeto: " + t.message)
                }
            }
        }
    }

'''
marker="    private fun restoreEditorPreferences() {"
if s.count(marker)!=1: raise SystemExit("local de inserção inválido")
s=s.replace(marker,code+marker,1)
sub("        if (p != null && p !== o) p.pcmFile.delete()\n        o?.pcmFile?.delete()",
"""        if (p != null && p !== o && !ProjectLibrary.isStoredAudio(this, p.pcmFile)) p.pcmFile.delete()
        if (o != null && !ProjectLibrary.isStoredAudio(this, o.pcmFile)) o.pcmFile.delete()""")
sub("                original = p\n                processed = null\n                keyEstimate = estimate",
"""                original = p
                processed = null
                loopA = null
                loopB = null
                applyLoop()
                keyEstimate = estimate""")
sub("                    original = p; processed = null; keyEstimate = estimate",
"""                    original = p; processed = null; keyEstimate = estimate
                    loopA = null; loopB = null; applyLoop()""")
main.write_text(s,encoding="utf-8")
assert (base/"ProjectLibrary.kt").is_file(),"ProjectLibrary.kt ausente"
gfile=root/"app/build.gradle.kts";g=gfile.read_text(encoding="utf-8")
assert g.count("versionCode = 16")==1 and g.count('versionName = "1.9.6"')==1
assert g.count('applicationIdSuffix = ".expressivefix"')==1
g=g.replace("versionCode = 16","versionCode = 17",1)
g=g.replace('versionName = "1.9.6"','versionName = "1.9.7"',1)
g=g.replace('applicationIdSuffix = ".expressivefix"','applicationIdSuffix = ".library"',1)
gfile.write_text(g,encoding="utf-8")
print("PitchStudio 1.9.7: biblioteca offline, botões Expressive e original preservado.")
