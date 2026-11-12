package app.traxos.finance

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
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
class CustomFormsUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app
        get() = ApplicationProvider.getApplicationContext<TraxosApplication>()

    @After
    fun cleanup() {
        app.database.close()
        app.deleteDatabase("traxos.db")
    }

    @Test
    fun customSelectorsDateAndSaveGatePreserveDeleteUndo() {
        runBlocking {
            app.repository.saveAccount(
                UUID.randomUUID().toString(),
                "bank",
                "Test bank",
                "BANK",
                "INR",
                10000,
                openingDate = LocalDate.now().minusDays(30).toEpochDay(),
            )
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitHome()
            compose.onNodeWithTag("center_action").performClick()
            compose.waitUntil(5000) {
                compose.onAllNodesWithTag("input_amount").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("input_amount").performTextInput("0")
            awaitEnabled("form_save")
            compose.onNodeWithTag("form_save").performClick()
            compose.waitUntil(5000) {
                compose.onAllNodesWithTag("form_error").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("input_amount").performTextReplacement("25.00")
            compose.onNodeWithTag("selector_Category").performScrollTo().performClick()
            compose.onNodeWithTag("choice_Food & drink").performClick()
            compose.onNodeWithTag("traxos_sheet").assertDoesNotExist()
            compose.onNodeWithTag("date_selector").performScrollTo().performClick()
            compose.onNodeWithText("Yesterday").performClick()
            compose.onNodeWithText("Use date").performClick()
            compose.onNodeWithTag("transaction_details").performScrollTo().performClick()
            compose
                .onNodeWithTag("input_note")
                .performScrollTo()
                .performTextInput("UI saved expense")
            compose.onNodeWithTag("input_amount").performScrollTo()
            savePreview(scenario, "transaction-form.png")
            val save =
                compose
                    .onNodeWithTag("form_save")
                    .fetchSemanticsNode()
                    .config[SemanticsActions.OnClick]
                    .action!!
            compose.runOnIdle { repeat(10) { save() } }
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("page_transaction_form").fetchSemanticsNodes().isEmpty()
            }
            assertEquals(1L, runBlocking { app.repository.dao.recordCount() })
            val record = runBlocking { app.repository.dao.recent().first().single().record }
            assertEquals(2500L, record.amountMinor)
            assertEquals(LocalDate.now().minusDays(1).toEpochDay(), record.day)
            compose.onNodeWithTag("nav_transactions").performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithText("UI saved expense").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("UI saved expense").performClick()
            openDelete()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithTag("traxos_dialog").assertDoesNotExist()
            compose.mainClock.advanceTimeBy(64)
            openDelete()
            compose.onNodeWithTag("traxos_dialog").assertExists()
            compose.onNodeWithTag("dialog_confirm").performScrollTo().performClick()
            compose.waitUntil(10000) {
                runBlocking { app.repository.dao.record(record.id)!!.deleted }
            }
            compose.onNodeWithText("Undo").performClick()
            compose.waitUntil(10000) {
                runBlocking { !app.repository.dao.record(record.id)!!.deleted }
            }
            assertEquals(
                7500L,
                runBlocking { app.repository.dao.accounts().first().single().balanceMinor },
            )
        }
    }

    @Test
    fun customAccountCurrencyMenuAndArchiveKeepOpeningBalance() {
        runBlocking { app.repository.setTheme("DARK") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitHome()
            compose.onNodeWithTag("nav_accounts").performClick()
            compose.onNodeWithText("Add account").performClick()
            compose.waitUntil(5000) {
                compose.onAllNodesWithTag("input_account_name").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("selector_Account type").performClick()
            compose.onNodeWithTag("choice_Bank").performClick()
            compose.onNodeWithTag("input_account_name").performTextInput("New wallet")
            compose.onNodeWithTag("selector_Currency").performScrollTo().performClick()
            compose.onNodeWithTag("traxos_menu").assertExists()
            compose.onNodeWithTag("choice_USD").performClick()
            compose.onNodeWithTag("traxos_menu").assertDoesNotExist()
            compose
                .onNodeWithTag("input_opening_amount")
                .performScrollTo()
                .performTextReplacement("100.00")
            compose.onNodeWithTag("input_account_name").performScrollTo()
            savePreview(scenario, "account-form-dark.png")
            awaitEnabled("form_save")
            compose.onNodeWithTag("form_save").performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("page_account_form").fetchSemanticsNodes().isEmpty()
            }
            compose.waitUntil(10000) {
                compose.onAllNodesWithText("New wallet").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("New wallet").performClick()
            compose.waitUntil(5000) {
                compose.onAllNodesWithTag("input_account_name").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("input_account_name").performTextReplacement("Renamed wallet")
            compose.onNodeWithContentDescription("Archive account").performScrollTo().performClick()
            awaitEnabled("form_save")
            compose.onNodeWithTag("form_save").performClick()
            compose.waitUntil(10000) {
                runBlocking { app.repository.dao.accounts().first().single().account.archived }
            }
            val account = runBlocking { app.repository.dao.accounts().first().single().account }
            assertEquals("Renamed wallet", account.name)
            assertEquals("USD", account.currency)
            assertEquals(10000L, account.openingMinor)
        }
    }

    @Test
    fun creatingAnAccountFromTransactionPreservesTheDraft() {
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitHome()
            compose.onNodeWithTag("center_action").performClick()
            compose.waitUntil(5000) {
                compose.onAllNodesWithTag("input_amount").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("input_amount").performTextInput("12.50")
            compose.onNodeWithText("Create account").performScrollTo().performClick()
            compose.waitUntil(5000) {
                compose.onAllNodesWithTag("input_account_name").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("input_account_name").performTextInput("Draft account")
            awaitEnabled("form_save")
            compose.onNodeWithTag("form_save").performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("page_account_form").fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithTag("page_transaction_form").assertExists()
            compose.onNodeWithTag("input_amount").assertTextContains("12.50")
            awaitEnabled("form_save")
            // Exercise the same real button callback via its accessibility action after navigation.
            compose.onNodeWithTag("form_save").assertIsEnabled().performSemanticsAction(
                SemanticsActions.OnClick
            ) {
                it()
            }
            // Wait on the visible completion state so Robolectric pumps Android main continuations.
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("page_transaction_form").fetchSemanticsNodes().isEmpty()
            }
            assertEquals(1L, runBlocking { app.repository.dao.recordCount() })
            assertEquals(
                1250L,
                runBlocking { app.repository.dao.recent().first().single().record.amountMinor },
            )
        }
    }

    private fun awaitEnabled(tag: String) {
        compose.waitUntil(5000) {
            compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openDelete() {
        awaitEnabled("form_delete")
        compose.onNodeWithTag("form_delete").performScrollTo().performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("traxos_dialog").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun savePreview(scenario: ActivityScenario<MainActivity>, name: String) {
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        scenario.onActivity { activity ->
            val view = activity.window.decorView
            view.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
            val bitmap =
                android.graphics.Bitmap.createBitmap(
                    view.width,
                    view.height,
                    android.graphics.Bitmap.Config.ARGB_8888,
                )
            view.draw(android.graphics.Canvas(bitmap))
            view.invalidate()
            view.draw(android.graphics.Canvas(bitmap))
            val output = java.io.File("build/reports/ui-components/$name")
            requireNotNull(output.parentFile).mkdirs()
            output.outputStream().use {
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
    }

    private fun awaitHome() {
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("TOTAL BALANCE").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
