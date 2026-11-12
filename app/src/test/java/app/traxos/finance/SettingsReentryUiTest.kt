package app.traxos.finance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TraxosApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsReentryUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @After
    fun clean() {
        val app = ApplicationProvider.getApplicationContext<TraxosApplication>()
        app.database.close()
        app.deleteDatabase("traxos.db")
    }

    @Test
    fun freshSettingsEntryDoesNotRestoreProfileAndBackReturnsToPreviousMainPage() {
        kotlinx.coroutines.runBlocking {
            ApplicationProvider.getApplicationContext<TraxosApplication>()
                .repository
                .setTheme("DARK")
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15000) {
                compose
                    .onAllNodes(hasTestTag("center_action") and isEnabled())
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithTag("nav_accounts").performClick()
            compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithTag("setting_Profile").performScrollTo().performClick()
            compose.onNodeWithTag("input_profile_name").assertExists()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("page_settings").assertExists()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("page_accounts").assertExists()
            compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithTag("setting_Profile").performScrollTo().performClick()
            compose.onNodeWithTag("nav_home").performClick()
            compose.onNodeWithTag("nav_settings").performClick()
            compose.mainClock.advanceTimeBy(64)
            compose.onNodeWithTag("page_settings").assertExists()
            compose.onNodeWithTag("input_profile_name").assertDoesNotExist()
            compose.waitForIdle()
            scenario.onActivity { captureHostPreview(it, "settings-dark.png") }
        }
    }
}
