package app.traxos.finance

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.*
import app.traxos.finance.ui.*
import java.time.LocalDate
import java.util.UUID
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
class CardDetailsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun maskedFormValidatesAndPersistsEncryptedDetails() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "card-ui-${UUID.randomUUID()}.db"
        val db = TraxosDatabase.open(context, name)
        val repo = CardVaultRepository(db, TestGiftCipher())
        var saved = false
        try {
            runBlocking {
                LedgerRepository(db)
                    .saveAccount(
                        UUID.randomUUID().toString(),
                        "card",
                        "Demo card",
                        "CREDIT_CARD",
                        "INR",
                        1000,
                        openingDate = LocalDate.now().minusDays(1).toEpochDay(),
                    )
            }
            compose.setContent {
                TraxosTheme("DARK") { CardDetailsScreen(repo, "card", {}, { saved = true }) }
            }
            compose.waitUntil(10000) {
                compose
                    .onAllNodes(hasTestTag("save_card_details") and isEnabled())
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithTag("save_card_details").performClick()
            compose.onNodeWithText("Enter a valid card number (13–19 digits)").assertExists()
            compose
                .onNodeWithTag("input_card_number")
                .performScrollTo()
                .performTextInput("4111111111111111")
            compose.onNodeWithTag("input_card_expiry").performScrollTo().performTextInput("12/30")
            compose.onNodeWithTag("input_card_cvv").performScrollTo().performTextInput("123")
            compose.onNodeWithTag("save_card_details").performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithTag("save_card_details").fetchSemanticsNodes()
                saved
            }
            assertEquals("4111111111111111", runBlocking { repo.read("card").first.number })
            assertEquals(0L, runBlocking { db.ledger().recordCount() })
        } finally {
            compose.activity.viewModelStore.clear()
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun compactActionsRemainAccessibleAndGiveImmediateCallbacks() {
        var count = 0
        compose.setContent {
            TraxosTheme("LIGHT") {
                Row {
                    CompactAction("Expenses", { count++ }, Modifier.weight(1f), active = true)
                    CompactAction("Filters", { count++ }, Modifier.weight(1f))
                }
            }
        }
        compose.onNodeWithText("Expenses").performClick()
        compose.onNodeWithText("Filters").performClick()
        assertEquals(2, count)
    }
}
