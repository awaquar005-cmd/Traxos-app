package app.traxos.finance

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.*
import app.traxos.finance.ui.*
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
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecurringPanelUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun scheduleEditorCreatesPausedRuleThenPostsDueAndDeletesOnlySchedule() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val name = "recurring-ui-${UUID.randomUUID()}.db"
        val db = TraxosDatabase.open(app, name)
        val repo = RecurringRepository(db)
        try {
            runBlocking {
                LedgerRepository(db)
                    .saveAccount(
                        UUID.randomUUID().toString(),
                        "bank",
                        "Demo bank",
                        "BANK",
                        "INR",
                        100000,
                        openingDate = LocalDate.now().minusDays(1).toEpochDay(),
                    )
            }
            compose.setContent { TraxosTheme("DARK") { RecurringPanel(repo, {}, {}) } }
            compose.onNodeWithText("Add schedule").performClick()
            compose.onNodeWithTag("selector_Account").performScrollTo().performClick()
            compose.onNodeWithTag("choice_Demo bank · INR").performClick()
            compose.onNodeWithTag("selector_Category").performScrollTo().performClick()
            compose.onNodeWithTag("choice_Food & drink").performClick()
            compose
                .onNodeWithTag("input_recurring_description")
                .performScrollTo()
                .performTextInput("Regular lunch")
            compose.onNodeWithTag("input_recurring_amount").performScrollTo().performTextInput("50")
            compose.onNodeWithText("Save schedule").performScrollTo().performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithText("Add schedule").fetchSemanticsNodes().isNotEmpty()
            }
            val saved = runBlocking { repo.rules.first().single() }
            assertFalse(saved.enabled)
            assertEquals(5000L, saved.amountMinor)
            compose.onNodeWithText("Resume").performScrollTo().performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithText("Pause").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Post due now").performScrollTo().performClick()
            compose.waitUntil(10000) {
                compose
                    .onAllNodesWithText(
                        "Due-entry check complete; review any schedule errors below"
                    )
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            assertEquals(1L, runBlocking { db.ledger().recordCount() })
            compose.onNodeWithText("Delete").performScrollTo().performClick()
            compose.onNodeWithTag("dialog_confirm").performClick()
            compose.waitUntil(10000) {
                compose
                    .onAllNodesWithText("No recurring transactions. Add a schedule to get started.")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            assertEquals(1L, runBlocking { db.ledger().recordCount() })
        } finally {
            db.close()
            app.deleteDatabase(name)
        }
    }

    @Test
    fun confirmationActionsAreSideBySideAndFormFrameIsStable() {
        var loading by mutableStateOf(true)
        var dialog by mutableStateOf(false)
        compose.setContent {
            TraxosTheme("LIGHT") {
                if (dialog)
                    TraxosDialog(
                        "Confirm action",
                        "No financial record is changed in this fixture.",
                        "Confirm",
                        {},
                        {},
                    )
                TraxosFormPanel {
                    androidx.compose.foundation.layout.Column(
                        androidx.compose.ui.Modifier.fillMaxSize()
                    ) {
                        androidx.compose.material3.Text(if (loading) "Loading" else "Fields ready")
                    }
                }
            }
        }
        val before = compose.onNodeWithTag("traxos_form_panel").getUnclippedBoundsInRoot()
        compose.runOnIdle { loading = false }
        assertEquals(before, compose.onNodeWithTag("traxos_form_panel").getUnclippedBoundsInRoot())
        compose.runOnIdle { dialog = true }
        val cancel = compose.onNodeWithText("Cancel").getUnclippedBoundsInRoot()
        val confirm = compose.onNodeWithTag("dialog_confirm").getUnclippedBoundsInRoot()
        assertTrue(cancel.right <= confirm.left)
        assertTrue(cancel.top < confirm.bottom && confirm.top < cancel.bottom)
    }
}
