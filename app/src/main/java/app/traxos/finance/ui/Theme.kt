package app.traxos.finance.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
        secondary = Color(0xFF152138),
        background = Color(0xFFF8F9FB),
        surface = Color(0xFFF8F9FB),
        onSurface = Color(0xFF152138),
        onBackground = Color(0xFF152138),
        surfaceContainer = Color(0xFFF1F3F7),
        surfaceContainerLowest = Color.White,
        onSurfaceVariant = Color(0xFF606D83),
        outlineVariant = Color(0xFFE9ECF1),
        error = Color(0xFFAD302C),
    )
private val Dark =
    darkColorScheme(
        primary = Color(0xFFA9C4FF),
        onPrimary = Color(0xFF002D74),
        secondary = Color(0xFFD9E3F6),
        background = Color(0xFF0F1520),
        surface = Color(0xFF0F1520),
        onSurface = Color(0xFFEEF2F9),
        onBackground = Color(0xFFEEF2F9),
        surfaceContainer = Color(0xFF202A39),
        surfaceContainerLowest = Color(0xFF192230),
        onSurfaceVariant = Color(0xFF9BA9BE),
        outlineVariant = Color(0xFF2B3545),
        error = Color(0xFFFFB4AB),
    )

internal data class AccentOption(val id: String, val label: String, val color: Color)

internal val accentOptions =
    listOf(
        AccentOption("COBALT", "Cobalt", Color(0xFF2549C4)),
        AccentOption("NAVY", "Navy", Color(0xFF233C83)),
        AccentOption("PETROL", "Petrol", Color(0xFF005F6B)),
        AccentOption("GRAPHITE", "Graphite", Color(0xFF3F4B5B)),
        AccentOption("FOREST", "Forest", Color(0xFF17613E)),
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
        headlineMedium = type(32, 40, FontWeight.SemiBold),
        headlineSmall = type(24, 32, FontWeight.SemiBold),
        titleLarge = type(20, 27, FontWeight.SemiBold),
        titleMedium = type(15, 22, FontWeight.SemiBold),
        bodyLarge = type(16, 24),
        bodyMedium = type(14, 21),
        bodySmall = type(12, 18),
        labelLarge = type(14, 20, FontWeight.Medium),
        labelMedium = type(12, 16, FontWeight.Medium),
        labelSmall = type(11, 16, FontWeight.Medium),
    )

@Composable
fun TraxosTheme(mode: String, accent: String, content: @Composable () -> Unit) {
    val dark = mode == "DARK" || (mode == "SYSTEM" && isSystemInDarkTheme())
    val ink = accentOptions.firstOrNull { it.id == accent }?.color ?: accentOptions.first().color
    val contrast = LocalDisplayOptions.current.contrast
    val colors =
        (if (dark) Dark else Light).copy(
            primary = if (dark) androidx.compose.ui.graphics.lerp(ink, Color.White, .65f) else ink,
            primaryContainer = ink,
            onPrimaryContainer = Color.White,
            onSurface =
                if (contrast) (if (dark) Color.White else Color.Black)
                else (if (dark) Dark else Light).onSurface,
            onSurfaceVariant =
                if (contrast) (if (dark) Color.White else Color.Black)
                else (if (dark) Dark else Light).onSurfaceVariant,
            onBackground =
                if (contrast) (if (dark) Color.White else Color.Black)
                else (if (dark) Dark else Light).onBackground,
        )
    MaterialTheme(
        colorScheme = colors,
        typography = TraxosType,
        shapes =
            Shapes(
                small = RoundedCornerShape(8.dp),
                medium = RoundedCornerShape(12.dp),
                large = RoundedCornerShape(16.dp),
            ),
        content = {
            CompositionLocalProvider(LocalContentColor provides colors.onSurface) { content() }
        },
    )
}

@Composable
fun TraxosTheme(mode: String, content: @Composable () -> Unit) =
    TraxosTheme(mode, "COBALT", content)
