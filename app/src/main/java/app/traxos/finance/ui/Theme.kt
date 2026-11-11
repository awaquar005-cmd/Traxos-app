package app.traxos.finance.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Space {
    val tiny = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val gutter = 16.dp
    val section = 20.dp
    val large = 24.dp
}

private val Light =
    lightColorScheme(
        primary = Color(0xFF275AC7),
        onPrimary = Color.White,
        secondary = Color(0xFF111B2F),
        background = Color(0xFFF6F8FB),
        surface = Color(0xFFF6F8FB),
        onSurface = Color(0xFF111B2F),
        surfaceContainer = Color(0xFFE9EEF5),
        error = Color(0xFFAD302C),
    )
private val Dark =
    darkColorScheme(
        primary = Color(0xFFA9C4FF),
        onPrimary = Color(0xFF002D74),
        secondary = Color(0xFFD9E3F6),
        background = Color(0xFF0C1423),
        surface = Color(0xFF0C1423),
        onSurface = Color(0xFFE3E9F4),
        surfaceContainer = Color(0xFF1A263B),
        error = Color(0xFFFFB4AB),
    )

// OS sans keeps Indic/emoji fallback without a font download or bundled font payload.
private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
    )

private val TraxosType =
    Typography(
        headlineMedium = type(30, 38, FontWeight.SemiBold),
        headlineSmall = type(24, 32, FontWeight.SemiBold),
        titleLarge = type(21, 28, FontWeight.SemiBold),
        titleMedium = type(16, 24, FontWeight.Medium),
        bodyLarge = type(16, 24),
        bodyMedium = type(14, 21),
        bodySmall = type(12, 18),
        labelLarge = type(14, 20, FontWeight.Medium),
        labelMedium = type(12, 16, FontWeight.Medium),
        labelSmall = type(11, 16, FontWeight.Medium),
    )

@Composable
fun TraxosTheme(mode: String, content: @Composable () -> Unit) {
    val dark = mode == "DARK" || (mode == "SYSTEM" && isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = TraxosType,
        shapes =
            Shapes(
                small = RoundedCornerShape(8.dp),
                medium = RoundedCornerShape(12.dp),
                large = RoundedCornerShape(16.dp),
            ),
        content = content,
    )
}
