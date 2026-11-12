package app.traxos.finance

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.*
import app.traxos.finance.gifts.*
import app.traxos.finance.smart.*
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
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmartInboxUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: TraxosDatabase
    private lateinit var repo: SmartRepository
    private lateinit var vm: SmartViewModel

    @Before
    fun setup() {
        db =
            TraxosDatabase.open(
                ApplicationProvider.getApplicationContext(),
                "smart-ui-${UUID.randomUUID()}.db",
            )
        val ledger = LedgerRepository(db)
        repo = SmartRepository(db, TestGiftCipher(), ledger, GiftRepository(db, TestGiftCipher()))
        vm = SmartViewModel(repo, ledger)
    }

    @After
    fun cleanup() {
        val name = db.openHelper.databaseName!!
        db.close()
        ApplicationProvider.getApplicationContext<Application>().deleteDatabase(name)
    }

    @Test
    fun explicitAccountCategoryAndRepeatedApproveCreateOneTransaction() {
        val day = LocalDate.now()
        runBlocking {
            vm.ledger.saveAccount(
                UUID.randomUUID().toString(),
                "bank",
                "Test bank",
                "BANK",
                "INR",
                100000,
                openingDate = day.minusDays(1).toEpochDay(),
            )
        }
        compose.setContent { TraxosTheme("LIGHT") { SmartInboxScreen(vm) } }
        compose
            .onNodeWithTag("input_smart_text")
            .performTextInput("Bank account debited INR 300 at Merchant on $day Ref SMART123456")
        compose.waitUntil(10000) {
            compose
                .onAllNodes(hasText("Find suggestion") and isEnabled())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("Find suggestion").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("smart_approve").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0L, runBlocking { db.ledger().recordCount() })
        compose.onNodeWithTag("selector_Account").performScrollTo().performClick()
        compose.onNodeWithTag("choice_Test bank").performClick()
        compose.onNodeWithTag("selector_Category").performScrollTo().performClick()
        compose.onNodeWithTag("choice_Other expense").performClick()
        val approve =
            compose
                .onNodeWithTag("smart_approve")
                .performScrollTo()
                .fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsActions.OnClick]
                .action!!
        compose.runOnIdle { repeat(10) { approve() } }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("traxos_sheet").fetchSemanticsNodes().isEmpty()
        }
        assertEquals(1L, runBlocking { db.ledger().recordCount() })
    }

    @Test
    fun settlementOffersRejectWithoutExpenseApprovalControl() {
        compose.setContent {
            TraxosTheme("DARK") {
                SmartInboxScreen(
                    vm,
                    incoming = "Bank account debited INR 500 credit card bill payment",
                )
            }
        }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("traxos_sheet").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("smart_approve").assertDoesNotExist()
        compose.onNodeWithText("Reject").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("traxos_sheet").fetchSemanticsNodes().isEmpty()
        }
        assertEquals(0L, runBlocking { db.ledger().recordCount() })
    }
}
