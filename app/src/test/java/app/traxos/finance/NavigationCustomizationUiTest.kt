package app.traxos.finance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.domain.NavigationLayout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TraxosApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCustomizationUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app
        get() = ApplicationProvider.getApplicationContext<TraxosApplication>()

    @After
    fun cleanup() {
        app.database.close()
        app.deleteDatabase("traxos.db")
    }

    @Test
    fun reorderSwapRecreateAndRestoreKeepQuickActionIndependent() {
        runBlocking { app.repository.setCenterAction("TRANSACTIONS") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15000) {
                compose
                    .onAllNodesWithContentDescription("Shortcut: Transactions")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithContentDescription("Open settings").assertDoesNotExist()
            compose.onNodeWithContentDescription("Quick add transaction").assertDoesNotExist()
            compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithText("Appearance & Navigation").performScrollTo().performClick()
            compose.onNodeWithText("Customize navigation").performScrollTo().performClick()
            compose.onNodeWithText("Arrange your everyday tools").assertExists()
            compose.waitForIdle()
            scenario.onActivity { captureHostPreview(it, "navigation-light.png") }
            compose
                .onNodeWithContentDescription("Move slot 4 earlier")
                .performScrollTo()
                .performClick()
            awaitLayout(listOf("home", "transactions", "settings", "accounts"))
            compose.onNodeWithTag("selector_Slot 1").performScrollTo().performClick()
            compose.onNodeWithTag("choice_Settings").performClick()
            val swapped = listOf("settings", "transactions", "home", "accounts")
            awaitLayout(swapped)
            compose.onAllNodesWithText("Goals").assertCountEquals(0)
            scenario.recreate()
            compose.waitUntil(10000) {
                compose.onNodeWithTag("nav_settings").getUnclippedBoundsInRoot().left <
                    compose.onNodeWithTag("nav_home").getUnclippedBoundsInRoot().left
            }
            compose.onNodeWithTag("nav_settings").assertIsSelected()
            compose.onNodeWithText("Restore default navigation").performScrollTo().performClick()
            compose.onNodeWithTag("traxos_dialog").assertExists()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithTag("traxos_dialog").assertDoesNotExist()
            compose.onNodeWithText("Restore default navigation").performClick()
            compose.onNodeWithTag("dialog_confirm").performClick()
            awaitLayout(NavigationLayout.defaults)
            assertEquals(
                "TRANSACTIONS",
                runBlocking { app.repository.dao.preferences().first()!!.centerAction },
            )
            compose.onNodeWithContentDescription("More actions").performClick()
            compose.onNodeWithText("Add transaction").performClick()
            compose.onNodeWithTag("page_transaction_form").assertExists()
        }
    }

    private fun awaitLayout(expected: List<String>) {
        compose.waitUntil(5000) {
            runBlocking {
                NavigationLayout.decode(
                    app.repository.dao.preferences().first()!!.navigationSlots
                ) == expected
            }
        }
        compose.waitUntil(5000) {
            compose
                .onAllNodes(hasContentDescription("Move slot 2 earlier") and isEnabled())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}
