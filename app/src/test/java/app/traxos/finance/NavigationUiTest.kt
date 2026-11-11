package app.traxos.finance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.domain.Money
import java.time.LocalDate
import java.util.UUID
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
class NavigationUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app
        get() = ApplicationProvider.getApplicationContext<TraxosApplication>()

    @After
    fun cleanUp() {
        app.database.close()
        app.deleteDatabase("traxos.db")
    }

    @Test
    fun tabsReplaceContentImmediatelyAndShortcutAndMaskSurviveRecreation() {
        runBlocking {
            val r = app.repository
            val day = LocalDate.now().toEpochDay()
            r.saveAccount(
                UUID.randomUUID().toString(),
                "bank",
                "Test bank",
                "BANK",
                "INR",
                10000,
                openingDate = day,
            )
            r.saveRecord(
                UUID.randomUUID().toString(),
                "record",
                "bank",
                "food",
                "EXPENSE",
                2500,
                day,
                "Lunch",
            )
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15000) {
                compose.onAllNodesWithText("TOTAL BALANCE").fetchSemanticsNodes().isNotEmpty()
            }
            // Check early (64ms) in the previous default crossfade: outgoing page must be gone.
            compose.mainClock.autoAdvance = false
            repeat(2) {
                compose.onNodeWithTag("nav_accounts").performClick()
                compose.mainClock.advanceTimeBy(64)
                compose.onNodeWithTag("page_accounts").assertExists()
                compose.onNodeWithTag("page_home").assertDoesNotExist()
                compose.onNodeWithTag("nav_transactions").performClick()
                compose.mainClock.advanceTimeBy(64)
                compose.onNodeWithTag("page_transactions").assertExists()
                compose.onNodeWithTag("page_accounts").assertDoesNotExist()
                compose.onNodeWithTag("nav_home").performClick()
                compose.mainClock.advanceTimeBy(64)
                compose.onNodeWithTag("page_home").assertExists()
                compose.onNodeWithTag("page_transactions").assertDoesNotExist()
            }
            compose.mainClock.autoAdvance = true
            compose.onNodeWithContentDescription("Hide financial amounts").performClick()
            compose.waitUntil(5000) {
                compose
                    .onAllNodesWithContentDescription("Show financial amounts")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose
                .onAllNodesWithText(Money.format(7500, "INR"), substring = true)
                .assertCountEquals(0)
            compose.onNodeWithContentDescription("Open settings").performClick()
            compose.onNodeWithTag("choice_Accounts").performScrollTo().performClick()
            compose.waitUntil(5000) {
                runBlocking {
                    app.repository.dao.preferences().first()!!.centerAction == "ACCOUNTS"
                }
            }
            compose.waitUntil(5000) {
                compose
                    .onAllNodes(hasTestTag("choice_Dark") and isEnabled())
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithTag("choice_Dark").performScrollTo().assertIsEnabled().performClick()
            compose.waitUntil(5000) {
                runBlocking { app.repository.dao.preferences().first()!!.theme == "DARK" }
            }
            compose.onNodeWithContentDescription("Back").performClick()
            scenario.recreate()
            compose.waitUntil(15000) {
                compose
                    .onAllNodesWithContentDescription("Shortcut: Accounts")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithContentDescription("Show financial amounts").assertExists()
            compose.onNodeWithTag("center_action").performClick()
            compose.onNodeWithTag("page_accounts").assertExists()
            compose.onNodeWithTag("page_home").assertDoesNotExist()
            compose.onNodeWithContentDescription("Quick add transaction").performClick()
            compose.onNodeWithTag("page_transaction_form").assertExists()
            runBlocking {
                assertEquals(1L, app.repository.dao.recordCount())
                assertEquals("ACCOUNTS", app.repository.dao.preferences().first()!!.centerAction)
                assertTrue(app.repository.dao.preferences().first()!!.balancesHidden)
            }
        }
    }
}
