package app.traxos.finance

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import app.traxos.finance.data.*
import java.io.File
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
@Config(sdk = [35], application = TraxosApplication::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoneyModulesUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app
        get() = ApplicationProvider.getApplicationContext<TraxosApplication>()

    private fun id() = UUID.randomUUID().toString()

    private val today = LocalDate.now().toEpochDay()

    @After
    fun cleanup() {
        WorkManager.getInstance(app).cancelAllWork().result.get()
        app.database.close()
        app.deleteDatabase("traxos.db")
    }

    private fun await(tag: String) {
        compose.waitUntil(15000) {
            compose.mainClock.advanceTimeBy(32)
            compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun more(tool: String) {
        await("center_action")
        compose.onNodeWithTag("more_actions").performClick()
        compose.onNodeWithTag("menu_more").performScrollTo().performClick()
        compose.onNodeWithTag("tool_$tool").performScrollTo().performClick()
    }

    private fun save(scenario: ActivityScenario<MainActivity>, name: String) {
        compose.waitForIdle()
        scenario.onActivity { a ->
            val view = a.window.decorView
            view.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            view.invalidate()
            view.draw(Canvas(bitmap))
            val f = File("build/reports/money/$name.png")
            f.parentFile!!.mkdirs()
            f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test
    fun groupCreationAndInlineMemberPreserveExpenseDraftAndProduceBalancedSplit() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            more("groups")
            await("groups_wallet")
            compose.onNodeWithText("Add").performClick()
            await("input_group_name")
            compose.onNodeWithTag("input_group_name").performTextReplacement("Trip fixture")
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(15000) { runBlocking { app.groups.groups.first().size == 1 } }
            compose.onNodeWithText("Trip fixture").performClick()
            await("group_detail")
            compose.onNodeWithText("Expense").performClick()
            await("input_group_title")
            compose.onNodeWithTag("input_group_title").performTextReplacement("Lunch fixture")
            compose.onNodeWithTag("input_group_amount").performTextReplacement("100")
            compose.onNodeWithText("Add member").performScrollTo().performClick()
            compose.onNodeWithTag("input_group_member").performTextReplacement("Friend fixture")
            compose.onNode(hasText("Save") and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(15000) {
                compose.mainClock.advanceTimeBy(32)
                compose.onAllNodes(hasTestTag("input_group_member")).fetchSemanticsNodes().isEmpty()
            }
            compose
                .onNodeWithTag("input_group_title")
                .performScrollTo()
                .assertTextContains("Lunch fixture")
            compose.onNodeWithText("Friend fixture").performScrollTo().performClick()
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(15000) {
                runBlocking {
                    val g = app.groups.groups.first().single()
                    app.database.groups().events(g.id).first().size == 1
                }
            }
            assertEquals(0L, runBlocking { app.repository.dao.recordCount() })
            val summary = runBlocking {
                app.database.ledger().summary(today, today + 1).first().single()
            }
            assertEquals(5000L, summary.expenseMinor)
            save(scenario, "groups-light")
        }
    }

    @Test
    fun newCardStatementPaymentMatchesPairedLedgerAndKeepsPersonalExpenseUnchanged() {
        runBlocking {
            app.repository.setTheme("DARK")
            app.repository.saveAccount(
                id(),
                "bank",
                "Bank fixture",
                "BANK",
                "INR",
                100000,
                openingDate = today - 20,
            )
            app.repository.saveAccount(
                id(),
                "card",
                "Card fixture",
                "CREDIT_CARD",
                "INR",
                0,
                openingDate = today - 20,
            )
            app.repository.saveRecord(
                id(),
                id(),
                "card",
                "food",
                "EXPENSE",
                10000,
                today - 5,
                "Original purchase",
            )
            app.obligations.statement(
                CardStatement(
                    id(),
                    "card",
                    today - 2,
                    today + 10,
                    10000,
                    1000,
                    false,
                    operationId = id(),
                )
            )
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            more("cards")
            await("card_schedules")
            compose.waitUntil(15000) {
                compose.mainClock.advanceTimeBy(32)
                compose.onAllNodes(hasText("Pay") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()
            compose.onNodeWithText("Pay").performScrollTo().performClick()
            compose.onNodeWithTag("selector_From account").performClick()
            compose.onNodeWithText("Bank fixture").performClick()
            compose.onNodeWithTag("input_statement_payment").performTextReplacement("30")
            compose
                .onNode(hasText("Pay") and hasAnyAncestor(isDialog()))
                .performScrollTo()
                .performClick()
            compose.waitUntil(15000) {
                runBlocking { app.database.obligations().allocations().first().size == 1 }
            }
            val summary = runBlocking {
                app.database.ledger().summary(today - 10, today + 1).first().single()
            }
            assertEquals(10000L, summary.expenseMinor)
            assertEquals(0L, summary.incomeMinor)
            save(scenario, "cards-dark")
        }
    }

    @Test
    fun subscriptionFormAndConfirmationAreFunctionalAndDoNotPostUpcomingDates() {
        runBlocking {
            app.repository.saveAccount(
                id(),
                "bank",
                "Bank fixture",
                "BANK",
                "INR",
                100000,
                openingDate = today - 20,
            )
            app.repository.setDefaultAccount("bank")
            app.repository.setDefaultCategory("food")
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            more("subscriptions")
            await("subscription_wallet")
            compose.onNodeWithText("Add").performClick()
            await("input_payment_plan_name")
            compose.onNodeWithTag("input_payment_plan_name").performTextReplacement("Music fixture")
            compose.onNodeWithTag("input_payment_plan_amount").performTextReplacement("99")
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(15000) {
                runBlocking { app.obligations.dao.plans().first().size == 1 }
            }
            assertEquals(0L, runBlocking { app.repository.dao.recordCount() })
            await("subscription_wallet")
            compose.waitUntil(15000) {
                compose
                    .onAllNodes(hasText("Confirm") and isEnabled() and hasClickAction())
                    .fetchSemanticsNodes()
                    .size == 1
            }
            compose.onNodeWithText("Confirm").performScrollTo().performClick()
            compose
                .onNode(hasText("Confirm") and hasAnyAncestor(isDialog()))
                .performScrollTo()
                .performClick()
            compose.waitUntil(15000) {
                runBlocking { app.obligations.dao.payments().first().size == 1 }
            }
            assertEquals(1L, runBlocking { app.repository.dao.recordCount() })
            save(scenario, "subscriptions-light")
        }
    }
}
