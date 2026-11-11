package app.traxos.finance

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import app.traxos.finance.data.*
import app.traxos.finance.ui.HomeScreen
import app.traxos.finance.ui.ReadState
import app.traxos.finance.ui.TraxosTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeStartupTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun homeWithSavedAccountAndMonthlyTransactionCanRenderAndScroll() {
        val day = LocalDate.now().toEpochDay()
        val account = Account("bank", "Saved bank", "BANK", "INR", 10000, day)
        val record =
            LedgerRecord(
                "saved-expense",
                "bank",
                "food",
                "EXPENSE",
                2500,
                day,
                "Asia/Kolkata",
                "Saved lunch",
            )
        val state =
            ReadState.Ready(
                accounts = listOf(AccountBalance(account, 7500)),
                categories = listOf(Category("food", "Food & drink", "EXPENSE")),
                recent = listOf(RecordRow(record, "Saved bank", "Food & drink", "INR")),
                summary = listOf(Summary("INR", 2500, 0)),
            )
        compose.setContent { TraxosTheme("LIGHT") { HomeScreen(state, {}, {}) } }
        compose.onNodeWithText("Account balances").performScrollTo().assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Saved lunch"))
        compose.onNodeWithText("Saved lunch").assertIsDisplayed()
    }
}
