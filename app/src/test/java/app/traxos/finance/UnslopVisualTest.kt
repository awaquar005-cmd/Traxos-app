package app.traxos.finance

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import app.traxos.finance.data.*
import app.traxos.finance.ui.*
import java.time.LocalDate
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UnslopVisualTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun fixture(): ReadState.Ready {
        val day = LocalDate.now().toEpochDay()
        val bank = Account("bank", "Everyday bank", "BANK", "INR", 650000, day)
        val cash = Account("cash", "Pocket cash", "CASH", "INR", 100000, day)
        val row =
            RecordRow(
                LedgerRecord(
                    "lunch",
                    "bank",
                    "food",
                    "EXPENSE",
                    25000,
                    day,
                    "Asia/Kolkata",
                    "Lunch with friends",
                ),
                "Everyday bank",
                "Food & drink",
                "INR",
            )
        return ReadState.Ready(
            listOf(AccountBalance(bank, 625000), AccountBalance(cash, 100000)),
            emptyList(),
            listOf(row),
            listOf(Summary("INR", 25000, 0)),
        )
    }

    @Test
    fun beforeAfterAndDarkComparisonKeepRealStructureAndFunctionalEye() {
        var experimental by mutableStateOf(false)
        var theme by mutableStateOf("LIGHT")
        compose.setContent {
            CompositionLocalProvider(LocalUnslop provides experimental) {
                TraxosTheme(theme) {
                    Scaffold(
                        topBar = { TraxosToolbar("Dashboard") },
                        bottomBar = {
                            TraxosBottomBar("home", CenterShortcut.ADD_TRANSACTION, {}) {}
                        },
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) {
                            HomeScreen(fixture(), {}, {})
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { captureHostPreview(compose.activity, "unslop-before-light.png") }
        compose.runOnIdle { experimental = true }
        compose.onNodeWithText("Total balance").assertExists()
        compose.onNodeWithContentDescription("Hide financial amounts").assertIsDisplayed()
        compose.runOnIdle { captureHostPreview(compose.activity, "unslop-after-light.png") }
        compose.runOnIdle { theme = "DARK" }
        compose.onNodeWithTag("center_action").assertIsDisplayed()
        compose.runOnIdle { captureHostPreview(compose.activity, "unslop-after-dark.png") }
    }

    @Test
    @Config(qualifiers = "w320dp-h720dp-mdpi")
    fun experimentalNarrowLargeFontDashboardKeepsPrimaryActionsAndActivityReachable() {
        compose.setContent {
            CompositionLocalProvider(LocalUnslop provides true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    TraxosTheme("DARK") {
                        Scaffold(
                            bottomBar = {
                                TraxosBottomBar("home", CenterShortcut.ADD_TRANSACTION, {}) {}
                            }
                        ) { padding ->
                            Box(Modifier.fillMaxSize().padding(padding)) {
                                HomeScreen(fixture(), {}, {})
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("center_action").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hide financial amounts").assertIsDisplayed()
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Lunch with friends"))
        compose.onNodeWithText("Lunch with friends").assertIsDisplayed()
    }
}
