package app.traxos.finance.ui

import androidx.compose.runtime.*
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import app.traxos.finance.domain.Money
import app.traxos.finance.regional.DisplayOptions

internal val LocalDisplayOptions = staticCompositionLocalOf { DisplayOptions() }

@Composable
internal fun DisplayEnvironment(options: DisplayOptions, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val native = LocalHapticFeedback.current
    val feedback =
        remember(native, options.haptics) {
            object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                    if (options.haptics) native.performHapticFeedback(hapticFeedbackType)
                }
            }
        }
    CompositionLocalProvider(
        LocalDisplayOptions provides options,
        LocalDensity provides
            Density(density.density, density.fontScale * options.textScale.toFloat()),
        LocalHapticFeedback provides feedback,
        content = content,
    )
}

@Composable
internal fun displayMoney(minor: Long, currency: String): String {
    val opts = LocalDisplayOptions.current
    return Money.format(minor, currency, opts.localeValue(), opts.currencyCode)
}

@Composable internal fun displayDate(day: Long): String = LocalDisplayOptions.current.date(day)
