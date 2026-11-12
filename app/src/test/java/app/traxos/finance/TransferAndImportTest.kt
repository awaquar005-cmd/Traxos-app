package app.traxos.finance

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.*
import app.traxos.finance.gifts.*
import app.traxos.finance.smart.*
import java.time.LocalDate
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
class TransferAndImportTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val file = "transfers-${UUID.randomUUID()}.db"
    private var db = TraxosDatabase.open(app, file)
    private var repo = LedgerRepository(db)
    private val day = LocalDate.now().toEpochDay()

    private fun op() = UUID.randomUUID().toString()

    @After
    fun close() {
        db.close()
        app.deleteDatabase(file)
    }

    private suspend fun accounts() {
        repo.saveAccount(op(), "a", "Bank A", "BANK", "INR", 100000, openingDate = day - 1)
        repo.saveAccount(op(), "b", "Bank B", "BANK", "INR", 20000, openingDate = day - 1)
    }

    @Test
    fun pairedTransferIsNeutralAndDuplicateRetryDeleteUndoAndRestartAreExact() = runBlocking {
        accounts()
        val operation = op()
        coroutineScope { repeat(10) { launch { repo.transfer(operation, "a", "b", 25000, day) } } }
        val balances = repo.dao.accounts().first().associate { it.account.id to it.balanceMinor }
        assertEquals(75000L, balances["a"])
        assertEquals(45000L, balances["b"])
        assertEquals(120000L, balances.values.sum())
        assertEquals(2L, repo.dao.recordCount())
        val totals = repo.dao.summary(day - 1, day + 1).first().single()
        assertEquals(0L, totals.expenseMinor)
        assertEquals(0L, totals.incomeMinor)
        val transferRows =
            repo.dao
                .page("", "TRANSFERS")
                .load(androidx.paging.PagingSource.LoadParams.Refresh(null, 40, false))
                as androidx.paging.PagingSource.LoadResult.Page<Int, RecordRow>
        assertEquals(2, transferRows.data.size)
        val expenseRows =
            repo.dao
                .page("", "EXPENSES")
                .load(androidx.paging.PagingSource.LoadParams.Refresh(null, 40, false))
                as androidx.paging.PagingSource.LoadResult.Page<Int, RecordRow>
        assertTrue(expenseRows.data.isEmpty())
        repo.setDeleted(op(), operation, 1, true)
        assertEquals(
            100000L,
            repo.dao.accounts().first().first { it.account.id == "a" }.balanceMinor,
        )
        repo.setDeleted(op(), operation, 2, false)
        db.close()
        db = TraxosDatabase.open(app, file)
        repo = LedgerRepository(db)
        assertEquals(
            75000L,
            repo.dao.accounts().first().first { it.account.id == "a" }.balanceMinor,
        )
        assertEquals(
            45000L,
            repo.dao.accounts().first().first { it.account.id == "b" }.balanceMinor,
        )
    }

    @Test
    fun invalidSameAccountCurrencyCardAndInsufficientFundsDoNotCreateLegs() = runBlocking {
        accounts()
        repo.saveAccount(op(), "usd", "USD", "BANK", "USD", 10000, openingDate = day - 1)
        repo.saveAccount(op(), "card", "Card", "CREDIT_CARD", "INR", 0, openingDate = day - 1)
        for ((from, to, amount) in
            listOf(
                Triple("a", "a", 100L),
                Triple("a", "usd", 100L),
                Triple("a", "card", 100L),
                Triple("a", "b", 100001L),
            )) {
            try {
                repo.transfer(op(), from, to, amount, day)
                fail("Expected validation")
            } catch (_: IllegalArgumentException) {}
        }
        assertEquals(0L, repo.dao.recordCount())
    }

    @Test
    fun oppositeSmsTransferLegsHaveOneReviewedIdentityAndNeverBecomeIncome() = runBlocking {
        accounts()
        val gifts = GiftRepository(db, TestGiftCipher())
        val inbox = SmartRepository(db, TestGiftCipher(), repo, gifts)
        val date = LocalDate.now()
        val first =
            inbox.offer(
                "Bank a/c XX1111 debited INR 100 self transfer to own account on $date Ref DEMO123456"
            )!!
        val opposite =
            inbox.offer(
                "Bank a/c XX2222 credited INR 100 self transfer from own account on $date Ref DEMO123456"
            )!!
        assertEquals(first.first.id, opposite.first.id)
        assertFalse(opposite.second)
        val draft = inbox.read(first.first)
        assertEquals("TRANSFER", draft.kind)
        assertEquals(0L, repo.dao.recordCount())
        inbox.approve(first.first.id, draft, "a", "b")
        inbox.approve(first.first.id, draft, "a", "b")
        assertEquals(2L, repo.dao.recordCount())
        assertEquals(0L, repo.dao.summary(day - 1, day + 1).first().single().incomeMinor)
    }

    @Test
    fun giftValuesAreDetectedWithoutConfusingOrderTaxOrCurrencySuffix() {
        val a =
            GiftParser.parse(
                "Amazon gift card code: TEST-AB123 Value INR 1,000 Order ID 123456 Tax INR 18"
            )
        assertEquals("1000", a.amount)
        assertEquals("INR", a.currency)
        val b =
            GiftParser.parse("Merchant: Test store\nGift card code: DEMO1234\nValue 1,250.50 INR")
        assertEquals("1250.50", b.amount)
        assertEquals("INR", b.currency)
        val c = GiftParser.parse("Gift card\nCode: DEMO1234\nValue: 1000\nCurrency: USD")
        assertEquals("1000", c.amount)
        assertEquals("USD", c.currency)
        val ambiguous = GiftParser.parse("Gift card\nValue INR 100\nValue INR 200\nCode: DEMO1234")
        assertEquals("", ambiguous.amount)
        assertEquals("", GiftParser.parse("Gift card\nOrder total INR 1000\nCode: DEMO1234").amount)
    }

    @Test
    fun failedOrReversedOwnTransfersRemainReviewOnly() {
        for (status in
            listOf(
                "failed",
                "declined",
                "unsuccessful",
                "pending",
                "reversed",
                "refund",
                "cashback",
                "requested",
            )) {
            val draft =
                SmartParser.parse(
                    "Bank a/c XX1111 debited INR 100 self transfer to own account $status"
                )!!
            assertEquals(status, "REVIEW_ONLY", draft.kind)
        }
    }
}
