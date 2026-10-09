package com.timachado.brothermatrizes.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Reactive brand palette: existing screens read Fio* colors during Compose,
// so switching modes updates their existing styles without a UI redesign.
private object BrotherPalette {
    var dark = mutableStateOf(true)
}

val FioBackground: Color get() = if (BrotherPalette.dark.value) Color(0xFF08131D) else Color(0xFFF7F5F0)
val FioSurface: Color get() = if (BrotherPalette.dark.value) Color(0xFF101D28) else Color(0xFFFFFFFF)
val FioSurfaceAlt: Color get() = if (BrotherPalette.dark.value) Color(0xFF172632) else Color(0xFFEAEFF0)
val FioSurfaceHigh: Color get() = if (BrotherPalette.dark.value) Color(0xFF20313E) else Color(0xFFDFE8EA)
val FioGold: Color get() = if (BrotherPalette.dark.value) Color(0xFFE6BE70) else Color(0xFF936616)
val FioGoldSoft: Color get() = if (BrotherPalette.dark.value) Color(0xFFF6E7C4) else Color(0xFF675018)
val FioGoldContainer: Color get() = if (BrotherPalette.dark.value) Color(0xFF4A3A1D) else Color(0xFFF5E6C5)
val FioText: Color get() = if (BrotherPalette.dark.value) Color(0xFFF7F4EE) else Color(0xFF17242F)
val FioTextMuted: Color get() = if (BrotherPalette.dark.value) Color(0xFFA9B3BC) else Color(0xFF546471)
val FioDanger: Color get() = if (BrotherPalette.dark.value) Color(0xFFFF8A80) else Color(0xFFB43546)

private val darkColors = darkColorScheme(
    primary = Color(0xFFE6BE70),
    onPrimary = Color(0xFF241704),
    primaryContainer = Color(0xFF4A3A1D),
    onPrimaryContainer = Color(0xFFF6E7C4),
    secondary = Color(0xFFF6E7C4),
    onSecondary = Color(0xFF2A210F),
    secondaryContainer = Color(0xFF273541),
    onSecondaryContainer = Color(0xFFF7F4EE),
    background = Color(0xFF08131D),
    onBackground = Color(0xFFF7F4EE),
    surface = Color(0xFF101D28),
    onSurface = Color(0xFFF7F4EE),
    surfaceVariant = Color(0xFF172632),
    onSurfaceVariant = Color(0xFFA9B3BC),
    surfaceContainer = Color(0xFF101D28),
    surfaceContainerHigh = Color(0xFF20313E),
    error = Color(0xFFFF8A80)
)

private val lightColors = lightColorScheme(
    primary = Color(0xFF936616),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF5E6C5),
    onPrimaryContainer = Color(0xFF46300C),
    secondary = Color(0xFF675018),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7E5DD),
    onSecondaryContainer = Color(0xFF17242F),
    background = Color(0xFFF7F5F0),
    onBackground = Color(0xFF17242F),
    surface = Color.White,
    onSurface = Color(0xFF17242F),
    surfaceVariant = Color(0xFFEAEFF0),
    onSurfaceVariant = Color(0xFF546471),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFDFE8EA),
    error = Color(0xFFB43546)
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(38.dp)
)

private val typography = Typography(
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun BrotherMatrizesTheme(
    textScaleMultiplier: Float = 1f,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    // Apply before rendering children; snapshot reads of Fio* values
    // schedule recomposition for all surfaces when the mode changes.
    if (BrotherPalette.dark.value != darkTheme) {
        BrotherPalette.dark.value = darkTheme
    }
    val currentDensity =
        LocalDensity.current

    val safeMultiplier =
        textScaleMultiplier
            .coerceIn(
                0.90f,
                1.30f
            )

    CompositionLocalProvider(
        LocalDensity provides
            Density(
                density =
                    currentDensity.density,
                fontScale =
                    currentDensity.fontScale *
                        safeMultiplier
            )
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkColors else lightColors,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}
