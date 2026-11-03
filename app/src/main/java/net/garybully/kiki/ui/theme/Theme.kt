package net.garybully.kiki.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// KIKI kawaii palette — dark pink accent, single light theme.
val KikiPink = Color(0xFFC2185B)
val KikiPinkDark = Color(0xFFAD1457)
val KikiPinkLight = Color(0xFFFCE4EC)
val KikiViolet = Color(0xFFB39DDB)
val KikiCream = Color(0xFFFFF8F0)
val KikiBubbleMine = Color(0xFFFCE4EC)
val KikiBubbleTheirs = Color(0xFFF1F1F1)

private val KikiLightColors = lightColorScheme(
    primary = KikiPink,
    onPrimary = Color.White,
    primaryContainer = KikiPinkLight,
    onPrimaryContainer = KikiPinkDark,
    secondary = KikiViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE7F6),
    onSecondaryContainer = Color(0xFF4A3B6B),
    background = KikiCream,
    onBackground = Color(0xFF2B2B2B),
    surface = Color.White,
    onSurface = Color(0xFF2B2B2B),
    surfaceVariant = Color(0xFFF6EEF1),
    onSurfaceVariant = Color(0xFF6B5B61),
    outline = Color(0xFFD8C3CC),
)

val KikiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

private val KikiTypography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
)

/** Single theme: light only, no dark scheme, no dynamic color. */
@Composable
fun KikiTheme(content: @Composable () -> Unit) {
    androidx.compose.material3.MaterialTheme(
        colorScheme = KikiLightColors,
        shapes = KikiShapes,
        typography = KikiTypography,
        content = content
    )
}
