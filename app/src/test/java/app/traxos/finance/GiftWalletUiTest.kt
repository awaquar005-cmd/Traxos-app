package app.traxos.finance

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.TraxosDatabase
import app.traxos.finance.gifts.*
import app.traxos.finance.ui.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GiftWalletUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: TraxosDatabase
    private lateinit var vm: GiftViewModel

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = TraxosDatabase.open(app, "gift-ui-${UUID.randomUUID()}.db")
        vm = GiftViewModel(GiftRepository(db, TestGiftCipher()))
    }

    @After
    fun cleanup() {
        val name = db.openHelper.databaseName
        db.close()
        if (name != null)
            ApplicationProvider.getApplicationContext<Application>().deleteDatabase(name)
    }

    @Test
    fun manualFormUsesCustomControlsValidatesAndGuardsRepeatedSaves() {
        vm.begin()
        var saved by mutableStateOf(false)
        compose.setContent {
            TraxosTheme("LIGHT") {
                Surface(Modifier.fillMaxSize()) {
                    if (!saved) GiftFormScreen(vm, "new", {}, { saved = true })
                    else GiftWalletScreen(vm, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithTag("gift_save").performClick()
        compose.waitUntil(5000) {
            compose
                .onAllNodesWithText("Enter a merchant name (up to 80 characters)")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertEquals(0, runBlocking { db.gifts().all().size })
        compose.onNodeWithTag("input_gift_merchant").performScrollTo().performTextInput("Amazon")
        compose.onNodeWithText("More details").performScrollTo().performClick()
        compose.onNodeWithTag("input_gift_name").performScrollTo().performTextInput("Birthday card")
        compose.onNodeWithTag("input_gift_code").performScrollTo().performTextInput("UI-CODE-123")
        compose.onNodeWithTag("input_gift_original").performScrollTo().performTextInput("1000")
        compose.onNodeWithTag("input_gift_remaining").performScrollTo().performTextInput("700")
        val save =
            compose
                .onNodeWithTag("gift_save")
                .fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsActions.OnClick]
                .action!!
        compose.runOnIdle { repeat(10) { save() } }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("gift_form").fetchSemanticsNodes().isEmpty()
        }
        val cards = runBlocking { db.gifts().all() }
        assertEquals(1, cards.size)
        assertEquals(70000L, cards.single().remainingMinor)
        assertEquals(0L, runBlocking { db.ledger().recordCount() })
    }

    @Test
    fun importRejectDoesNotSaveAndReviewKeepsMissingValuesBlank() {
        var review = false
        compose.setContent {
            TraxosTheme("DARK") { GiftImportScreen(vm, null, {}, {}, { review = true }) }
        }
        compose
            .onNodeWithTag("input_gift_import_text")
            .performTextInput("Amazon gift card code: IMPORT-1234\nValue INR 1000.00")
        compose.onNodeWithText("Detect gift card").performScrollTo().performClick()
        compose.onNodeWithTag("traxos_sheet").assertExists()
        compose.onNodeWithText("Reject").performScrollTo().performClick()
        assertEquals(0, runBlocking { db.gifts().all().size })
        compose.onNodeWithText("Detect gift card").performScrollTo().performClick()
        compose.onNodeWithText("More details before saving").performScrollTo().performClick()
        compose.waitUntil(5000) { review }
        assertEquals("Amazon", vm.draft.value!!.merchant)
        assertEquals("IMPORT-1234", vm.draft.value!!.code)
        assertEquals("1000.00", vm.draft.value!!.original)
        assertEquals("", vm.draft.value!!.remaining)
        assertEquals(0, runBlocking { db.gifts().all().size })
    }

    @Test
    fun walletSearchFiltersAndMaskedDetailPreserveUndo() {
        val c = runBlocking {
            vm.repository.save(
                GiftDraft(
                        merchant = "Amazon",
                        name = "Test card",
                        code = "MASKED-1234",
                        original = "1000",
                        remaining = "1000",
                        partial = true,
                    )
                    .card(),
                GiftSecret("MASKED-1234", "7654"),
                0,
            )
        }
        var detail by mutableStateOf(false)
        compose.setContent {
            TraxosTheme("LIGHT") {
                if (detail) GiftDetailScreen(vm, c.id, {}, { detail = false })
                else GiftWalletScreen(vm, { detail = true }, {}, {}, {})
            }
        }
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Test card").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("MASKED-1234").assertDoesNotExist()
        compose.onNodeWithText("Open card").performScrollTo().performClick()
        compose.onNodeWithText("MASKED-1234").assertDoesNotExist()
        compose.onNodeWithText("Reveal").performScrollTo().performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("MASKED-1234").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Hide").performScrollTo().performClick()
        compose.onNodeWithText("MASKED-1234").assertDoesNotExist()
        compose.onNodeWithText("Record redemption").performScrollTo().performClick()
        compose.onNodeWithTag("input_gift_redeem_amount").performTextReplacement("300")
        compose.onNodeWithText("Confirm redemption").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("traxos_sheet").fetchSemanticsNodes().isEmpty()
        }
        assertEquals(70000L, runBlocking { db.gifts().card(c.id)!!.remainingMinor })
        compose.onNodeWithText("Undo last redemption").performScrollTo().performClick()
        compose.onNodeWithTag("dialog_confirm").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("traxos_dialog").fetchSemanticsNodes().isEmpty()
        }
        assertEquals(100000L, runBlocking { db.gifts().card(c.id)!!.remainingMinor })
    }

    @Test
    fun automaticConfirmationPreferenceLeavesSharedResultsUnsaved() {
        runBlocking { vm.repository.updateSettings(GiftSettings(autoReview = false)) }
        compose.setContent {
            TraxosTheme("LIGHT") {
                GiftImportScreen(
                    vm,
                    GiftIncoming.Text("Amazon gift card code: SHARED-1234"),
                    {},
                    {},
                    {},
                )
            }
        }
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Review detected card").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("traxos_sheet").assertDoesNotExist()
        assertTrue(runBlocking { db.gifts().all().isEmpty() })
        compose.onNodeWithText("Review detected card").performScrollTo().performClick()
        compose.onNodeWithTag("traxos_sheet").assertExists()
        assertTrue(runBlocking { db.gifts().all().isEmpty() })
    }
}
