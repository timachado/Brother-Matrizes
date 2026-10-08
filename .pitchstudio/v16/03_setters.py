from pathlib import Path
import sys
root=Path(sys.argv[1])
p=root/"app/src/main/java/br/com/timachado/pitchstudio/MainActivity.kt"
s=p.read_text(encoding="utf-8")

start=s.index("    private fun setSemitone(value: Int) {")
end=s.index("    private fun scheduleProcess",start)

block='''    private fun setSemitone(value: Int, process: Boolean = true) {
        semitones = value.coerceIn(-12, 12)
        pitchSeek.progress = semitones + 12
        refreshPitchLabels()
        useProcessed = true
        updateAbButton()
        applyPreviewParams()
        updateKeyLabel()
        if (process) scheduleProcess()
    }

    private fun setCents(value: Int, process: Boolean = true) {
        cents = value.coerceIn(-100, 100)
        centsSeek.progress = cents + 100
        refreshPitchLabels()
        useProcessed = true
        updateAbButton()
        applyPreviewParams()
        updateKeyLabel()
        if (process) scheduleProcess()
    }

    private fun setSpeedValue(value: Double, process: Boolean = true) {
        speed = value.coerceIn(0.5, 2.0)
        speedSeek.progress = ((speed - 0.5) * 100.0).roundToInt()
        speedLabel.text = String.format(java.util.Locale("pt", "BR"), "%.2fx", speed)
        useProcessed = true
        updateAbButton()
        applyPreviewParams()
        if (process) scheduleProcess()
    }

    private fun applyPitchPreset(semitoneValue: Int, centsValue: Int) {
        semitones = semitoneValue.coerceIn(-12, 12)
        cents = centsValue.coerceIn(-100, 100)
        pitchSeek.progress = semitones + 12
        centsSeek.progress = cents + 100
        refreshPitchLabels()
        useProcessed = true
        updateAbButton()
        applyPreviewParams()
        updateKeyLabel()
        scheduleProcess()
    }

    private fun refreshPitchLabels() {
        pitchLabel.text = when (semitones) {
            0 -> "0 semitons"
            1 -> "+1 semitom"
            -1 -> "−1 semitom"
            else -> (if (semitones > 0) "+" else "") + semitones + " semitons"
        }
        centsLabel.text = "Ajuste fino: " + signed(cents) + " cents"
    }

    private fun applyPreviewParams() {
        val pitch = if (useProcessed) {
            2.0.pow((semitones + cents / 100.0) / 12.0).toFloat()
        } else 1.0f
        val previewSpeed = if (useProcessed) speed.toFloat() else 1.0f
        player.setPreviewParams(pitch, previewSpeed)
    }

'''

s=s[:start]+block+s[end:]
p.write_text(s,encoding="utf-8")
