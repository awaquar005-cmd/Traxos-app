package app.traxos.finance

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TraxosApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SavedLedgerLaunchTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app
        get() = ApplicationProvider.getApplicationContext<TraxosApplication>()

    @After
    fun cleanUp() {
        app.database.close()
        app.deleteDatabase("traxos.db")
    }

    @Test
    fun launchAndRecreateWithPreviouslySavedLedger() {
        val day = LocalDate.now().toEpochDay()
        // Simulate the previous session: commit to a file, then close that DB before launch.
        runBlocking {
            val previous = TraxosDatabase.open(app)
            try {
                val repository = LedgerRepository(previous)
                repository.saveAccount(
                    UUID.randomUUID().toString(),
                    "bank",
                    "Saved bank",
                    "BANK",
                    "INR",
                    10000,
                    openingDate = day,
                )
                repository.saveRecord(
                    UUID.randomUUID().toString(),
                    "saved-expense",
                    "bank",
                    "food",
                    "EXPENSE",
                    2500,
                    day,
                    "Saved lunch",
                )
                repository.setTheme("DARK")
            } finally {
                previous.close()
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitLedger()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Saved lunch"))
            compose.onNodeWithText("Saved lunch").assertIsDisplayed()
            scenario.recreate()
            awaitLedger()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Saved lunch"))
            compose.onNodeWithText("Saved lunch").assertIsDisplayed()
            runBlocking {
                assertEquals(1L, app.repository.dao.recordCount())
                assertEquals(2L, app.repository.dao.operationCount())
                assertEquals(2500L, app.repository.dao.record("saved-expense")!!.amountMinor)
            }
        }
    }

    private fun awaitLedger() {
        compose.waitUntil(timeoutMillis = 15000) {
            compose.onAllNodesWithText("SPENT THIS MONTH").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Saved lunch").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
