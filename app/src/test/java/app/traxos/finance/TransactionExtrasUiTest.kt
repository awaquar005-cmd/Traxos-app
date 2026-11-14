package app.traxos.finance

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.*
import app.traxos.finance.entry.*
import app.traxos.finance.ui.*
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TransactionExtrasUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun splitSheetEqualCentsAndApplyPreservesParentDraft() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val name = "extras-ui-${UUID.randomUUID()}.db"
        val db = TraxosDatabase.open(app, name)
        try {
            val repo = LedgerRepository(db)
            val state = runBlocking {
                repo.saveAccount(
                    UUID.randomUUID().toString(),
                    "bank",
                    "Bank",
                    "BANK",
                    "INR",
                    10000,
                    openingDate = 20000,
                )
                db.ledger().insertCategory(Category("second", "Travel", "EXPENSE"))
                ReadState.Ready(
                    db.ledger().accounts().first(),
                    db.ledger().categories().first(),
                    emptyList(),
                    emptyList(),
                )
            }
            var fields by
                mutableStateOf(
                    mapOf(
                        "accountId" to "bank",
                        "categoryId" to "food",
                        "type" to "EXPENSE",
                        "amount" to "10.01",
                        "note" to "Keep note",
                        "splits" to
                            SplitDrafts.write(
                                listOf(
                                    SplitDrafts.Row("food", "3.01"),
                                    SplitDrafts.Row("second", "7.00"),
                                )
                            ),
                    )
                )
            compose.setContent {
                TraxosTheme("DARK") {
                    SplitFormControls(fields, state, repo, true) { k, v ->
                        fields = fields + (k to v)
                    }
                }
            }
            compose.onNodeWithTag("transaction_split").performClick()
            compose.onNodeWithText("Equal").performScrollTo().performClick()
            compose.onNodeWithTag("split_apply").performScrollTo().performClick()
            compose.waitForIdle()
            val parts = SplitDrafts.parts(fields["splits"].orEmpty(), "INR")
            assertEquals(1001L, parts.sumOf { it.amountMinor })
            assertEquals(1L, kotlin.math.abs(parts[0].amountMinor - parts[1].amountMinor))
            assertEquals("Keep note", fields["note"])
            assertNull(runBlocking { db.ledger().record("new") })
        } finally {
            db.close()
            app.deleteDatabase(name)
        }
    }
}
