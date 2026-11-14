package app.traxos.finance

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import app.traxos.finance.domain.ActivityScope
import app.traxos.finance.entry.ReceiptAttachment
import app.traxos.finance.ui.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TraxosApplication::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TrendsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun categoryDrillAndCurrencyFilterWorkAcrossThemeChanges() {
        val app = ApplicationProvider.getApplicationContext<TraxosApplication>()
        val vm = TrendsViewModel(app, SavedStateHandle())
        var drill: ActivityScope? = null
        var theme by mutableStateOf("DARK")
        try {
            runBlocking {
                val today = LocalDate.now().toEpochDay()
                app.repository.saveAccount(
                    UUID.randomUUID().toString(),
                    "bank",
                    "Bank",
                    "BANK",
                    "INR",
                    10000,
                    openingDate = today - 100,
                )
                app.repository.saveRecord(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    "bank",
                    "food",
                    "EXPENSE",
                    1234,
                    today,
                    "",
                )
            }
            compose.setContent {
                TraxosTheme(theme) { TrendsScreen({ scope, _ -> drill = scope }, vm) }
            }
            compose.waitUntil(15000) {
                compose
                    .onAllNodesWithText("Food & drink · Spending")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithText("Food & drink · Spending").performScrollTo().performClick()
            assertEquals("food", drill!!.categoryId)
            assertEquals("INR", drill!!.currency)
            compose.onNodeWithText("Filter").performScrollTo().performClick()
            compose.onNodeWithTag("selector_Currency").performScrollTo().performClick()
            compose.onNodeWithTag("choice_USD").performScrollTo().performClick()
            compose.onNodeWithTag("trends_apply").performScrollTo().performClick()
            compose.waitUntil(10000) {
                compose
                    .onAllNodesWithText(
                        "No matching activity in either period. Change filters or record a transaction."
                    )
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.runOnIdle { theme = "LIGHT" }
            compose.onNodeWithTag("trends_screen").assertExists()
            assertEquals("USD", vm.filters.value[5])
        } finally {
            vm.viewModelScope.cancel()
            WorkManager.getInstance(app).cancelAllWork().result.get()
            app.database.close()
            app.deleteDatabase("traxos.db")
        }
    }

    @Test
    fun receiptActionsAndConfirmationDisableWhileParentSaves() {
        val app = ApplicationProvider.getApplicationContext<TraxosApplication>()
        val vm = ReceiptsViewModel(app)
        var enabled by mutableStateOf(false)
        val owner = UUID.randomUUID().toString()
        try {
            runBlocking {
                val day = LocalDate.now().toEpochDay()
                app.repository.saveAccount(
                    UUID.randomUUID().toString(),
                    "bank",
                    "Bank",
                    "BANK",
                    "INR",
                    10000,
                    openingDate = day,
                )
                app.repository.saveRecord(
                    UUID.randomUUID().toString(),
                    owner,
                    "bank",
                    "food",
                    "EXPENSE",
                    100,
                    day,
                    "",
                )
                // Metadata-only interaction fixture. Receipt crypto/import is covered separately.
                app.database
                    .extras()
                    .addReceipt(
                        ReceiptAttachment(
                            UUID.randomUUID().toString(),
                            owner,
                            "fixture.pdf",
                            "application/pdf",
                            "a".repeat(64),
                            byteArrayOf(1),
                            0,
                        )
                    )
            }
            compose.setContent {
                TraxosTheme("DARK") { ReceiptControls(owner, false, enabled, {}, {}, vm) }
            }
            compose.waitUntil(10000) {
                compose.onAllNodesWithText("fixture.pdf").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("fixture.pdf").assertIsNotEnabled()
            compose.onNodeWithText("Delete").assertIsNotEnabled()
            compose.runOnIdle { enabled = true }
            compose.onNodeWithText("Delete").assertIsEnabled().performClick()
            compose.onNodeWithTag("dialog_confirm").assertIsEnabled()
            compose.runOnIdle { enabled = false }
            compose.onNodeWithTag("dialog_confirm").assertIsNotEnabled()
        } finally {
            vm.viewModelScope.cancel()
            WorkManager.getInstance(app).cancelAllWork().result.get()
            app.database.close()
            app.deleteDatabase("traxos.db")
        }
    }
}
