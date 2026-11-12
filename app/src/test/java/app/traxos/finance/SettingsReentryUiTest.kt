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
@Config(sdk = [35], application = TraxosApplication::class, qualifiers = "w393dp-h852dp-xhdpi")
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

    @Test
    fun omittedHomeRemainsReachableAndPreviewCloseNeverRestoresItsChild() {
        val app = ApplicationProvider.getApplicationContext<TraxosApplication>()
        kotlinx.coroutines.runBlocking {
            app.repository.setNavigationSlots(listOf("gifts", "transactions", "accounts", "more"))
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15000) {
                compose
                    .onAllNodes(hasTestTag("nav_more") and isEnabled())
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithTag("nav_more").performClick()
            compose.onNodeWithTag("tool_home").assertExists()
            compose.waitForIdle()
            scenario.onActivity { captureHostPreview(it, "more-light.png") }
            compose.onNodeWithTag("tool_ui_review").performScrollTo().performClick()
            compose.onNodeWithTag("review_catalog").assertExists()
            compose.onNodeWithTag("review_catalog").performScrollToNode(hasTestTag("tool_groups"))
            compose.onNodeWithTag("tool_groups").performClick()
            compose.onNodeWithTag("review_page_groups").assertExists()
            compose.onNodeWithContentDescription("More actions").performClick()
            compose.onNodeWithText("Real dashboard").performClick()
            compose.onNodeWithTag("dashboard_list").assertExists()
            compose.onNodeWithTag("nav_more").performClick()
            compose.onNodeWithTag("tool_ui_review").performScrollTo().performClick()
            compose.onNodeWithTag("review_catalog").assertExists()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("tool_ui_review").assertExists()
            compose.onNodeWithTag("toolbar_home").performClick()
            compose.onNodeWithTag("dashboard_list").assertExists()
            org.junit.Assert.assertEquals(
                0L,
                kotlinx.coroutines.runBlocking { app.database.ledger().recordCount() },
            )
        }
    }
}
