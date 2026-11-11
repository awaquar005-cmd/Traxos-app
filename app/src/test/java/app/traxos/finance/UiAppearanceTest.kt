package app.traxos.finance

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import app.traxos.finance.data.*
import app.traxos.finance.ui.*
import java.io.File
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiAppearanceTest {
    @get:Rule val compose = createComposeRule()

    private fun render(mode: String, hidden: Boolean = false, fontScale: Float = 1f) {
        val day = LocalDate.now().toEpochDay()
        val bank = Account("bank", "Everyday bank", "BANK", "INR", 650000, day)
        val cash = Account("cash", "Pocket cash", "CASH", "INR", 100000, day)
        val record =
            LedgerRecord(
                "lunch",
                "bank",
                "food",
                "EXPENSE",
                25000,
                day,
                "Asia/Kolkata",
                "Lunch with friends",
            )
        val state =
            ReadState.Ready(
                listOf(AccountBalance(bank, 625000), AccountBalance(cash, 100000)),
                emptyList(),
                listOf(RecordRow(record, "Everyday bank", "Food & drink", "INR")),
                listOf(Summary("INR", 25000, 0)),
            )
        compose.setContent {
            ScaledTheme(mode, fontScale) {
                Scaffold(
                    topBar = { TraxosToolbar("Dashboard", settings = {}, add = {}) },
                    bottomBar = { TraxosBottomBar("home", CenterShortcut.ADD_TRANSACTION, {}) {} },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        HomeScreen(state, {}, {}, hidden = hidden)
                    }
                }
            }
        }
    }

    @Test
    fun lightDashboardHasVisibleControlsAndScreenshot() {
        render("LIGHT")
        compose.onNodeWithContentDescription("Hide financial amounts").assertIsDisplayed()
        compose.onNodeWithTag("center_action").assertIsDisplayed()
        save("dashboard-light.png")
    }

    @Test
    fun darkDashboardHasVisibleControlsAndScreenshot() {
        render("DARK")
        compose
            .onNodeWithText(app.traxos.finance.domain.Money.format(725000, "INR"))
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Hide financial amounts").assertIsDisplayed()
        compose.onNodeWithTag("center_action").assertIsDisplayed()
        save("dashboard-dark.png")
    }

    @Test
    @Config(qualifiers = "w320dp-h720dp-mdpi")
    fun narrowDashboardKeepsPrimaryControlsReachable() {
        render("LIGHT", true, 2f)
        compose.onNodeWithTag("center_action").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show financial amounts").assertIsDisplayed()
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Recent activity"))
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Lunch with friends"))
        compose.onNodeWithText("Lunch with friends").assertIsDisplayed()
    }

    private fun save(name: String) {
        val output = File("build/reports/ui-preview/$name")
        requireNotNull(output.parentFile).mkdirs()
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        compose.runOnIdle {
            val activity =
                ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .single()
            val view = activity.window.decorView
            view.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            view.invalidate()
            view.draw(Canvas(bitmap))
            output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }
}

@Composable
private fun ScaledTheme(mode: String, scale: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
        TraxosTheme(mode, content)
    }
}
