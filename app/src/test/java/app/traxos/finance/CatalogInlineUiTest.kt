package app.traxos.finance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
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
class CatalogInlineUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app
        get() = ApplicationProvider.getApplicationContext<TraxosApplication>()

    @After
    fun close() {
        app.database.close()
        app.deleteDatabase("traxos.db")
    }

    @Test
    fun inlineCategoryAndAccountCreationSelectImmediatelyWithoutLosingAmount() {
        runBlocking {
            app.repository.saveAccount(
                UUID.randomUUID().toString(),
                "bank",
                "Initial bank",
                "BANK",
                "INR",
                10000,
            )
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(15000) {
                compose
                    .onAllNodes(hasTestTag("center_action") and isEnabled())
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithTag("center_action").performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("input_amount").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("input_amount").performTextInput("12.50")
            compose.onNodeWithTag("selector_Category").performScrollTo().performClick()
            compose.onNodeWithTag("add_new_Category").performClick()
            compose.onNodeWithTag("input_catalog_name").performTextInput("Learning")
            compose.onNodeWithTag("catalog_save").performScrollTo().performClick()
            compose.waitUntil(10000) {
                compose
                    .onAllNodes(
                        hasTestTag("selector_Category") and hasText("Learning", substring = true)
                    )
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithTag("selector_Account").performScrollTo().performClick()
            compose.onNodeWithTag("add_new_Account").performClick()
            compose.onNodeWithTag("input_catalog_name").performTextInput("Pocket money")
            compose.onNodeWithTag("catalog_save").performScrollTo().performClick()
            compose.waitUntil(10000) {
                compose
                    .onAllNodes(
                        hasTestTag("selector_Account") and hasText("Pocket money", substring = true)
                    )
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithTag("input_amount").assertTextContains("12.50")
            compose
                .onNodeWithTag("selector_Category")
                .assertTextContains("Learning", substring = true)
            compose.waitUntil(5000) {
                compose.onAllNodesWithTag("traxos_sheet").fetchSemanticsNodes().isEmpty()
            }
            compose.waitForIdle()
            val action =
                compose
                    .onNodeWithTag("form_save")
                    .assertIsEnabled()
                    .fetchSemanticsNode()
                    .config[androidx.compose.ui.semantics.SemanticsActions.OnClick]
                    .action!!
            compose.runOnIdle { repeat(10) { action() } }
            compose.waitUntil(15000) {
                compose.onAllNodesWithTag("page_transaction_form").fetchSemanticsNodes().isEmpty()
            }
            assertEquals(1L, runBlocking { app.repository.dao.recordCount() })
            val record = runBlocking { app.repository.dao.recent().first().single() }
            assertEquals("Learning", record.categoryName)
            assertEquals("Pocket money", record.accountName)
            assertEquals(1250L, record.record.amountMinor)
        }
    }
}
