package com.timachado.brothermatrizes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioText

/**
 * A readable one-column hoop chooser; no off-screen horizontal chips.
 * Uses the exact commercial HoopProfile list, including its dimensions/margins.
 */
@Composable
internal fun ImageHoopPicker(
    selected: HoopProfile,
    onChoose: (HoopProfile) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escolher bastidor", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HoopProfile.entries.forEach { option ->
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onChoose(option) }
                    ) {
                        Text(
                            (if (selected == option) "● " else "○ ") + option.displayName,
                            color = if (selected == option) FioGold else FioText
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Concluir") }
        }
    )
}
