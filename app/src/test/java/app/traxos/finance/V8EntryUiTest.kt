package app.traxos.finance

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.*
import app.traxos.finance.domain.Money
import app.traxos.finance.ui.*
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
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V8EntryUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun plusTapAndHoldReleaseHaveIndependentActions() {
        var taps = 0
        var starts = 0
        var stops = 0
        compose.setContent {
            TraxosTheme("DARK") {
                TraxosBottomBar(
                    "home",
                    CenterShortcut.ADD_TRANSACTION,
                    {},
                    onQuickAddHold = { starts++ },
                    onQuickAddRelease = { stops++ },
                ) {
                    taps++
                }
            }
        }
        compose.onNodeWithTag("center_action").performTouchInput { longClick(durationMillis = 700) }
        assertEquals(1, starts)
        assertEquals(1, stops)
        assertEquals(0, taps)
        compose.onNodeWithTag("center_action").performClick()
        assertEquals(1, taps)
    }

    @Test
    fun plansCreateBudgetAndGoalProgressWithoutPostingBankTransactions() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val name = "plans-ui-${UUID.randomUUID()}.db"
        val db = TraxosDatabase.open(app, name)
        val repo = PlansRepository(db)
        try {
            compose.setContent { TraxosTheme("LIGHT") { PlansScreen(repo, "INR", {}) } }
            compose.waitUntil(10000) {
                compose
                    .onAllNodesWithText("No savings goals yet.")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onAllNodesWithText("New")[1].performScrollTo().performClick()
            compose.onNodeWithTag("input_goal_name").performScrollTo().performTextInput("Test goal")
            compose.onNodeWithTag("input_goal_target").performScrollTo().performTextInput("1000")
            compose.onNodeWithTag("goal_save").performScrollTo().performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithText("Test goal").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Add").performScrollTo().performClick()
            compose.onNodeWithTag("input_goal_progress").performScrollTo().performTextInput("250")
            compose.onNodeWithTag("goal_progress_save").performScrollTo().performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("goal_progress_save").fetchSemanticsNodes().isEmpty()
            }
            assertEquals(25000L, runBlocking { repo.goals.first().single().savedMinor })
            compose.waitUntil(10000) {
                compose
                    .onAllNodesWithText(Money.format(25000, "INR"), substring = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithText("Undo").performScrollTo().performClick()
            compose.waitUntil(10000) {
                runBlocking { repo.goals.first().single().savedMinor == 0L }
            }
            compose.onAllNodesWithText("New")[0].performScrollTo().performClick()
            compose.onNodeWithTag("input_budget_amount").performScrollTo().performTextInput("2000")
            compose.onNodeWithTag("budget_save").performScrollTo().performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("budget_save").fetchSemanticsNodes().isEmpty()
            }
            assertEquals(200000L, runBlocking { repo.budgets.first().single().limitMinor })
            assertEquals(0L, runBlocking { db.ledger().recordCount() })
        } finally {
            db.close()
            app.deleteDatabase(name)
        }
    }
}
