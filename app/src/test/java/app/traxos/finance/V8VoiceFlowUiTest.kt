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
@Config(sdk = [35], application = TraxosApplication::class, qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V8VoiceFlowUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun typedVoiceReviewKeepsDefaultsAndNeedsOneExplicitFinancialSave() {
        val app = ApplicationProvider.getApplicationContext<TraxosApplication>()
        try {
            runBlocking {
                app.repository.saveAccount(
                    UUID.randomUUID().toString(),
                    "bank",
                    "Default bank",
                    "BANK",
                    "INR",
                    100000,
                    openingDate = LocalDate.now().minusDays(1).toEpochDay(),
                )
                app.repository.setDefaults("bank", "groceries")
            }
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(10000) {
                    compose.onAllNodesWithTag("home_voice").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag("home_voice").performScrollTo().performClick()
                compose.waitUntil(10000) {
                    compose
                        .onAllNodesWithTag("input_voice_phrase")
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                compose
                    .onNodeWithTag("input_voice_phrase")
                    .performScrollTo()
                    .performTextInput("Lunch 150 cash")
                assertEquals(0L, runBlocking { app.database.ledger().recordCount() })
                compose.onNodeWithText("Use").performScrollTo().performClick()
                compose.waitUntil(10000) {
                    compose.onAllNodesWithTag("input_amount").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag("input_amount").assertTextContains("150")
                compose
                    .onNodeWithTag("selector_Account")
                    .assertTextContains("Default bank", substring = true)
                compose
                    .onNodeWithTag("selector_Category")
                    .assertTextContains("Groceries", substring = true)
                assertEquals(0L, runBlocking { app.database.ledger().recordCount() })
                compose.waitUntil(10000) {
                    compose
                        .onAllNodes(hasTestTag("form_save") and isEnabled())
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                val save =
                    compose
                        .onNodeWithTag("form_save")
                        .fetchSemanticsNode()
                        .config[SemanticsActions.OnClick]
                        .action!!
                compose.runOnIdle { repeat(10) { save() } }
                compose.waitUntil(10000) {
                    compose
                        .onAllNodesWithTag("page_transaction_form")
                        .fetchSemanticsNodes()
                        .isEmpty()
                }
                val saved = runBlocking { app.database.ledger().recent().first().single() }
                assertEquals(15000L, saved.record.amountMinor)
                assertEquals("bank", saved.record.accountId)
                assertEquals("groceries", saved.record.categoryId)
                assertEquals(1L, runBlocking { app.database.ledger().recordCount() })
            }
        } finally {
            app.database.close()
            app.deleteDatabase("traxos.db")
        }
    }
}
