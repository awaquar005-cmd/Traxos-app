package app.traxos.finance

import android.app.Application
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import app.traxos.finance.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ThemeContrastTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun rootTextLabelsAndBrandButtonsMeetNormalTextContrastInBothThemes() {
        var mode by mutableStateOf("LIGHT")
        var accent by mutableStateOf("COBALT")
        var checks = listOf<Pair<Color, Color>>()
        compose.setContent {
            TraxosTheme(mode, accent) {
                val c = MaterialTheme.colorScheme
                // No Surface wrapper: this catches a black default LocalContentColor in a dark
                // root.
                checks =
                    listOf(
                        LocalContentColor.current to c.background,
                        c.onSurfaceVariant to c.surfaceContainerLowest,
                        c.primary to c.surfaceContainerLowest,
                        Color.White to Ink.cobalt,
                        Color.White to Ink.coral,
                    )
                checks =
                    checks +
                        ButtonTone.entries.flatMap { tone ->
                            val p = buttonPalette(tone, c)
                            listOf(
                                p.foreground to p.background.compositeOver(c.background),
                                p.foreground to p.pressed.compositeOver(c.background),
                            )
                        }
                Text("Readable Traxos")
            }
        }
        for (option in accentOptions) for (theme in listOf("LIGHT", "DARK")) {
            compose.runOnIdle {
                mode = theme
                accent = option.id
            }
            compose.waitForIdle()
            compose.runOnIdle {
                checks.forEach { (fg, bg) ->
                    val ratio =
                        (maxOf(fg.luminance(), bg.luminance()) + .05f) /
                            (minOf(fg.luminance(), bg.luminance()) + .05f)
                    assertTrue("${option.id} $theme contrast $ratio must reach4.5", ratio >= 4.5f)
                }
            }
        }
    }
}
