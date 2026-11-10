package com.timachado.brothermatrizes.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.desktop.platform.DesktopMatrixMouseRotation
import kotlin.math.roundToInt

/** Windows-only rotation surface; no Android interface or backend changes. */
@Composable
internal fun DesktopMouseRotatePanel(
    design: EmbroideryDesign,
    hoop: HoopProfile,
    onRotated: (EmbroideryDesign) -> Unit,
    modifier: Modifier = Modifier
) {
    var enabled by remember { mutableStateOf(false) }
    var dragPixels by remember(design) { mutableFloatStateOf(0f) }
    var message by remember(design) { mutableStateOf<String?>(null) }

    fun rotate(degrees: Float) {
        DesktopMatrixMouseRotation.rotate(design, degrees, hoop).fold(
            onSuccess = {
                message = null
                onRotated(it)
            },
            onFailure = {
                message = it.message ?: "Não foi possível girar a matriz."
            }
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { enabled = !enabled; dragPixels = 0f },
                modifier = Modifier.testTag("brother_windows_mouse_rotation_toggle")
            ) {
                Text(if (enabled) "Fechar giro com mouse" else "Girar matriz com mouse")
            }
            if (enabled) {
                OutlinedButton(
                    onClick = { rotate(15f) },
                    modifier = Modifier.testTag("brother_windows_mouse_rotate_left")
                ) { Text("↺ 15°") }
                OutlinedButton(
                    onClick = { rotate(-15f) },
                    modifier = Modifier.testTag("brother_windows_mouse_rotate_right")
                ) { Text("↻ 15°") }
                Text(
                    DesktopMatrixMouseRotation.mouseAngle(
                        dragPixels, design.sourceYAxisDown
                    ).roundToInt().toString() + "°",
                    color = Color(0xFFE6BE70)
                )
            }
        }
        if (enabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(Color(0xFF142734), RoundedCornerShape(10.dp))
                    .pointerInput(design, hoop) {
                        detectDragGestures(
                            onDragStart = { dragPixels = 0f },
                            onDragCancel = { dragPixels = 0f },
                            onDragEnd = {
                                val degrees = DesktopMatrixMouseRotation.mouseAngle(
                                    dragPixels, design.sourceYAxisDown
                                )
                                if (kotlin.math.abs(degrees) >= 0.5f) rotate(degrees)
                                dragPixels = 0f
                            }
                        ) { change, drag ->
                            dragPixels = (dragPixels + drag.x).coerceIn(-360f, 360f)
                            change.consume()
                        }
                    }
                    .testTag("brother_windows_mouse_rotation_drag_pad"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Arraste o mouse ← ou → para girar a matriz de bordado",
                    color = Color.White
                )
            }
        }
        if (message != null) {
            Text(checkNotNull(message), color = Color(0xFFFF9898))
        }
    }
}
