package app.traxos.finance

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CatalogAccountingTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val file = "catalog-${UUID.randomUUID()}.db"
    private var db = TraxosDatabase.open(app, file)
    private var repo = LedgerRepository(db)

    private fun op() = UUID.randomUUID().toString()

    @After
    fun close() {
        db.close()
        app.deleteDatabase(file)
    }

    @Test
    fun categoriesAndMerchantsPreserveHistoricalReferencesAndRejectDuplicates() = runBlocking {
        repo.saveAccount(op(), "cash", "Cash", "CASH", "INR", 10000, openingDate = 20000)
        repo.saveCategory(op(), "custom", "Coffee", "EXPENSE", "food", "GREEN")
        repo.saveLabel(op(), "merchant", "Corner shop", "MERCHANT")
        repo.saveRecord(
            op(),
            "tx",
            "cash",
            "custom",
            "EXPENSE",
            1000,
            20001,
            "Coffee",
            merchantId = "merchant",
        )
        repo.saveCategory(op(), "custom", "Tea & coffee", "EXPENSE", "food", "VIOLET", true, 1)
        repo.saveLabel(op(), "merchant", "Local cafe", "MERCHANT", true, 1)
        val row = repo.dao.recent().first().single()
        assertEquals("Tea & coffee", row.categoryName)
        assertEquals("Local cafe", row.merchantName)
        assertEquals("VIOLET", row.categoryColor)
        suspend fun fails(block: suspend () -> Unit) {
            try {
                block()
                fail("Expected validation")
            } catch (_: IllegalArgumentException) {}
        }
        fails { repo.saveCategory(op(), "dupe", " tea & COFFEE ", "EXPENSE") }
        fails { repo.saveRecord(op(), "new", "cash", "custom", "EXPENSE", 100, 20001, "") }
        fails { repo.saveLabel(op(), "dupe", " LOCAL  cafe ", "MERCHANT") }
        // Editing an existing reference is valid even after archival.
        repo.saveRecord(op(), "tx", "cash", "custom", "EXPENSE", 1500, 20001, "Edit", 1, "merchant")
        db.close()
        db = TraxosDatabase.open(app, file)
        repo = LedgerRepository(db)
        assertEquals("merchant", repo.dao.record("tx")!!.merchantId)
        assertTrue(repo.dao.category("custom")!!.archived)
    }

    @Test
    fun cardPurchaseRefundAndPairedRepaymentNeverDoubleCount() = runBlocking {
        repo.saveAccount(op(), "bank", "Bank", "BANK", "INR", 100000, openingDate = 20000)
        repo.saveAccount(op(), "card", "Card", "CREDIT_CARD", "INR", 10000, openingDate = 20000)
        repo.saveAccount(op(), "wallet", "Wallet", "WALLET", "INR", 3000, openingDate = 20000)
        repo.saveRecord(op(), "buy", "card", "food", "EXPENSE", 20000, 20001, "")
        repo.saveRecord(op(), "refund", "card", "food", "REFUND", 5000, 20002, "")
        val payment = op()
        coroutineScope {
            repeat(10) { launch { repo.payCard(payment, "card", "bank", 15000, 20003) } }
        }
        val balances = repo.dao.accounts().first().associate { it.account.id to it.balanceMinor }
        assertEquals(-10000L, balances["card"])
        assertEquals(85000L, balances["bank"])
        assertEquals(
            78000L,
            balances.values.sum(),
        ) // 103000 assets less10000 opening debt less15000 spending.
        val summary = repo.dao.summary(20000, 20010).first().single()
        assertEquals(15000L, summary.expenseMinor)
        assertEquals(0L, summary.incomeMinor)
        assertEquals(4L, repo.dao.recordCount())
        repo.setDeleted(op(), payment, 1, true)
        assertEquals(
            -25000L,
            repo.dao.accounts().first().first { it.account.id == "card" }.balanceMinor,
        )
        assertEquals(
            100000L,
            repo.dao.accounts().first().first { it.account.id == "bank" }.balanceMinor,
        )
        repo.setDeleted(op(), payment, 2, false)
        assertEquals(
            85000L,
            repo.dao.accounts().first().first { it.account.id == "bank" }.balanceMinor,
        )
        assertEquals(15000L, repo.dao.summary(20000, 20010).first().single().expenseMinor)
        try {
            repo.saveRecord(op(), "wrong", "card", "salary", "INCOME", 100, 20004, "")
            fail("Card settlement cannot be income")
        } catch (_: IllegalArgumentException) {}
        try {
            repo.payCard(op(), "card", "bank", 20000, 20004)
            fail("Overpayment rejected")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun repeatedCategoryCreationAndCrossCurrencyPaymentAreSafe() = runBlocking {
        val operation = op()
        coroutineScope {
            repeat(10) {
                launch {
                    repo.saveCategory(operation, "new", "Learning", "EXPENSE", "work", "BLUE")
                }
            }
        }
        assertEquals(1, repo.dao.categories().first().count { it.id == "new" })
        repo.saveAccount(op(), "usd", "USD bank", "BANK", "USD", 10000, openingDate = 20000)
        repo.saveAccount(op(), "card", "INR card", "CREDIT_CARD", "INR", 10000, openingDate = 20000)
        try {
            repo.payCard(op(), "card", "usd", 100, 20001)
            fail("No implicit conversion")
        } catch (_: IllegalArgumentException) {}
        assertEquals(0L, repo.dao.recordCount())
        try {
            repo.saveAccount(op(), "dup", " usd BANK ", "BANK", "USD", 0)
            fail("No duplicate names")
        } catch (_: IllegalArgumentException) {}
    }
}
