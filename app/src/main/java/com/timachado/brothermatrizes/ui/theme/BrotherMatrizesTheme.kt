package com.timachado.brothermatrizes.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val FioBackground = Color(0xFF08131D)
val FioSurface = Color(0xFF101D28)
val FioSurfaceAlt = Color(0xFF172632)
val FioSurfaceHigh = Color(0xFF20313E)
val FioGold = Color(0xFFE6BE70)
val FioGoldSoft = Color(0xFFF6E7C4)
val FioGoldContainer = Color(0xFF4A3A1D)
val FioText = Color(0xFFF7F4EE)
val FioTextMuted = Color(0xFFA9B3BC)
val FioDanger = Color(0xFFFF8A80)

private val colors = darkColorScheme(
    primary = FioGold,
    onPrimary = Color(0xFF241704),
    primaryContainer = FioGoldContainer,
    onPrimaryContainer = FioGoldSoft,
    secondary = FioGoldSoft,
    onSecondary = Color(0xFF2A210F),
    secondaryContainer = Color(0xFF273541),
    onSecondaryContainer = FioText,
    background = FioBackground,
    onBackground = FioText,
    surface = FioSurface,
    onSurface = FioText,
    surfaceVariant = FioSurfaceAlt,
    onSurfaceVariant = FioTextMuted,
    surfaceContainer = FioSurface,
    surfaceContainerHigh = FioSurfaceHigh,
    error = FioDanger
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
    content: @Composable () -> Unit
) {
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
            colorScheme = colors,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}
