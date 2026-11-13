package app.traxos.finance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.domain.Money
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TraxosApplication::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OfflineToolsUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app
        get() = ApplicationProvider.getApplicationContext<TraxosApplication>()

    private lateinit var category: String

    @Before
    fun seed() = runBlocking {
        val repo = app.repository
        repo.setNavigationSlots(listOf("home", "transactions", "accounts", "more"))
        category = repo.dao.categories().first().first { it.type == "EXPENSE" }.id
        repo.saveAccount(
            "f24da58a-7d1a-5ce0-a72f-3fdb9e3b27bf",
            "bank",
            "Demo bank",
            "BANK",
            "INR",
            100000,
            openingDate = LocalDate.now().minusDays(3).toEpochDay(),
        )
        repo.saveAccount(
            "460b1452-7689-56a8-9a6f-c1cb37adaab3",
            "cash",
            "Demo cash",
            "CASH",
            "INR",
            100000,
            openingDate = LocalDate.now().minusDays(3).toEpochDay(),
        )
        repo.saveRecord(
            "35953f54-7ed8-5a81-9173-5c1927748429",
            "entry",
            "bank",
            category,
            "EXPENSE",
            12500,
            LocalDate.now().toEpochDay(),
            "Demo lunch",
        )
    }

    @After
    fun clean() {
        app.database.close()
        app.deleteDatabase("traxos.db")
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openTool(tag: String) {
        awaitTag("nav_more")
        compose.onNodeWithTag("nav_more").performClick()
        compose.onNodeWithTag("tool_$tag").performScrollTo().performClick()
    }

    @Test
    fun reportsDrillToExactActivityAndMonthValidationPreservesCurrentView() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openTool("reports")
            val row = "report_category_INR_$category"
            awaitTag(row)
            compose
                .onNodeWithTag(row)
                .assertTextContains(Money.format(12500, "INR"), substring = true)
            compose.waitForIdle()
            scenario.onActivity { captureHostPreview(it, "reports-light.png") }
            compose.onNodeWithTag("report_month_select").performClick()
            compose.onNodeWithTag("input_report_month").performTextReplacement("2024-13")
            compose.onNodeWithTag("report_month_apply").performClick()
            compose.onNodeWithText("Enter a valid month as YYYY-MM").assertExists()
            compose.onNodeWithContentDescription("Close picker").performClick()
            compose.onNodeWithTag(row).performScrollTo().performClick()
            awaitTag("activity_scope_summary")
            compose
                .onNodeWithTag("activity_scope_summary")
                .assertTextContains("INR", substring = true)
            compose.waitUntil(15000) {
                compose.onAllNodesWithText("Demo lunch").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Demo lunch").assertExists()
            compose.onNodeWithTag("activity_clear_filters").performClick()
            compose.onNodeWithTag("activity_scope_summary").assertDoesNotExist()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithTag("page_reports").assertExists()
        }
    }

    @Test
    fun deletedPairConfirmationRestoreUndoAndBackAreFunctional() {
        runBlocking {
            app.repository.transfer(
                "965f858d-03b8-5ff7-90fe-1eb2f3bd5111",
                "bank",
                "cash",
                2000,
                LocalDate.now().toEpochDay(),
            )
            app.repository.setDeleted(
                "c3a7d62f-3a73-59fa-8c12-cc15165bb5f7",
                "965f858d-03b8-5ff7-90fe-1eb2f3bd5111",
                1,
                true,
            )
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openTool("deleted")
            awaitTag("restore_965f858d-03b8-5ff7-90fe-1eb2f3bd5111")
            compose.waitForIdle()
            scenario.onActivity { captureHostPreview(it, "deleted-light.png") }
            compose
                .onNodeWithTag("restore_965f858d-03b8-5ff7-90fe-1eb2f3bd5111")
                .performScrollTo()
                .performClick()
            compose.onNodeWithText("Restore both sides?").assertExists()
            compose.onNodeWithText("Cancel").performClick()
            assertTrue(
                runBlocking {
                    app.database.ledger().linked("965f858d-03b8-5ff7-90fe-1eb2f3bd5111").all {
                        it.deleted
                    }
                }
            )
            compose.onNodeWithTag("restore_965f858d-03b8-5ff7-90fe-1eb2f3bd5111").performClick()
            compose.onNodeWithText("Restore", useUnmergedTree = true).performClick()
            compose.waitUntil(10000) {
                runBlocking {
                    app.database.ledger().linked("965f858d-03b8-5ff7-90fe-1eb2f3bd5111").all {
                        !it.deleted
                    }
                }
            }
            compose.onNodeWithText("Undo").performClick()
            compose.waitUntil(10000) {
                runBlocking {
                    app.database.ledger().linked("965f858d-03b8-5ff7-90fe-1eb2f3bd5111").all {
                        it.deleted
                    }
                }
            }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("tool_deleted").assertExists()
        }
    }

    @Test
    fun activityAndSettingsShareAPersistedSortPreference() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitTag("nav_transactions")
            compose.onNodeWithTag("nav_transactions").performClick()
            awaitTag("activity_sort")
            compose.onNodeWithTag("activity_sort").performClick()
            compose.onNodeWithTag("choice_Oldest first").performClick()
            compose.waitUntil(10000) {
                runBlocking {
                    app.database.ledger().preferences().first()!!.activitySort == "OLDEST"
                }
            }
            scenario.recreate()
            awaitTag("nav_transactions")
            compose.onNodeWithTag("nav_transactions").performClick()
            compose
                .onNodeWithTag("activity_sort")
                .assert(
                    SemanticsMatcher.expectValue(
                        androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                        "Oldest first",
                    )
                )
            openTool("settings")
            compose.onNodeWithTag("setting_Accounts & Data").performScrollTo().performClick()
            compose.onNodeWithTag("selector_Activity order").performScrollTo().performClick()
            compose.onNodeWithTag("choice_Newest first").performClick()
            compose.waitUntil(10000) {
                runBlocking {
                    app.database.ledger().preferences().first()!!.activitySort == "NEWEST"
                }
            }
            compose.onNodeWithTag("nav_transactions").performClick()
            compose
                .onNodeWithTag("activity_sort")
                .assert(
                    SemanticsMatcher.expectValue(
                        androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                        "Newest first",
                    )
                )
        }
    }

    @Test
    fun darkReportsRespectAmountMaskingAndEmptyMonths() {
        runBlocking {
            app.repository.setTheme("DARK")
            app.repository.setBalancesHidden(true)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openTool("reports")
            awaitTag("report_category_INR_$category")
            compose
                .onAllNodesWithText(Money.format(12500, "INR"), substring = true)
                .assertCountEquals(0)
            compose.waitForIdle()
            scenario.onActivity { captureHostPreview(it, "reports-dark-masked.png") }
            compose.onNodeWithTag("report_month_select").performClick()
            compose.onNodeWithTag("input_report_month").performTextReplacement("2000-01")
            compose.onNodeWithTag("report_month_apply").performClick()
            compose.waitUntil(10000) {
                compose
                    .onAllNodesWithText("No activity this month")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithText("Open activity").performClick()
            compose.onNodeWithTag("page_transactions").assertExists()
        }
    }
}
