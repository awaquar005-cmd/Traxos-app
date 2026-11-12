package app.traxos.finance

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.*
import app.traxos.finance.gifts.*
import app.traxos.finance.smart.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SmartAddTest {
    private lateinit var db: TraxosDatabase
    private lateinit var repo: SmartRepository
    private lateinit var gifts: GiftRepository

    @Before
    fun setup() {
        db =
            TraxosDatabase.open(
                ApplicationProvider.getApplicationContext(),
                "smart-${UUID.randomUUID()}.db",
            )
        val ledger = LedgerRepository(db)
        gifts = GiftRepository(db, TestGiftCipher())
        repo = SmartRepository(db, TestGiftCipher(), ledger, gifts)
    }

    @After
    fun cleanup() {
        val name = db.openHelper.databaseName
        db.close()
        ApplicationProvider.getApplicationContext<Application>().deleteDatabase(name!!)
    }

    @Test
    fun reviewIsRequiredAndRepeatedApprovalCreatesOneRecord() = runBlocking {
        val ledger = LedgerRepository(db)
        val day = LocalDate.now()
        ledger.saveAccount(
            UUID.randomUUID().toString(),
            "bank",
            "Bank",
            "BANK",
            "INR",
            100000,
            openingDate = day.minusDays(1).toEpochDay(),
        )
        val text =
            "Your Bank a/c XX1234 debited INR 300.00 at Amazon on $day. Ref ABC123456. Available balance INR 700.00"
        val (s, fresh) = repo.offer(text)!!
        assertTrue(fresh)
        assertEquals(0L, db.ledger().recordCount())
        assertFalse(s.secret.toString(Charsets.UTF_8).contains("Amazon"))
        val d = repo.read(s)
        assertEquals("300.00", d.amount)
        assertEquals("TRANSACTION", d.kind)
        repo.approve(s.id, d, "bank", "other-expense")
        repo.approve(s.id, d, "bank", "other-expense")
        assertEquals(1L, db.ledger().recordCount())
        assertEquals(70000L, db.ledger().accounts().first().single().balanceMinor)
        assertFalse(repo.offer(text)!!.second)
        assertEquals("APPROVED", repo.dao.get(s.id)!!.status)
    }

    @Test
    fun giftAndMessageDuplicatesNeverCreateTwoCards() = runBlocking {
        val (s, _) =
            repo.offer(
                "Amazon Gift Card\nCode: TEST-GIFT-1234\nValue INR 1000.00\nExpiry 31 December 2030"
            )!!
        assertTrue(gifts.dao.all().isEmpty())
        val d = repo.read(s)
        repo.approve(s.id, d, "", "")
        repo.approve(s.id, d, "", "")
        assertEquals(1, gifts.dao.all().size)
        assertEquals(0L, db.ledger().recordCount())
        assertFalse(repo.offer("Amazon voucher code: TEST-GIFT-1234")!!.second)
        assertEquals("TEST-GIFT-1234", gifts.secret(gifts.dao.all().single()).code)
    }

    @Test
    fun rejectAndIgnoreStayRejectedAcrossRetry() = runBlocking {
        val (s, _) = repo.offer("Flipkart Gift card code: IGNORE-1234")!!
        repo.reject(s.id, true)
        assertEquals("IGNORED", repo.offer("Flipkart Gift card code: IGNORE-1234")!!.first.status)
        try {
            repo.approve(s.id, repo.read(s), "", "")
            fail("ignored import posted")
        } catch (_: IllegalArgumentException) {}
        assertTrue(gifts.dao.all().isEmpty())
    }

    @Test
    fun settlementTransferRefundAndOtpCannotPostOrdinaryExpenses() = runBlocking {
        for (text in
            listOf(
                "Bank account debited INR 500 credit card bill payment",
                "Bank a/c XX1234 debited INR 500 self transfer",
                "Bank account credited INR 500 refund",
            )) {
            val (s, _) = repo.offer(text)!!
            val d = repo.read(s)
            assertEquals("REVIEW_ONLY", d.kind)
            try {
                repo.approve(s.id, d.copy(kind = "TRANSACTION"), "bank", "other-expense")
                fail("blocked payment posted")
            } catch (_: IllegalArgumentException) {}
        }
        assertNull(repo.offer("OTP 123456 for Bank payment INR 500"))
        assertNull(repo.offer("Please call me tomorrow https://example.com"))
        assertEquals(0L, db.ledger().recordCount())
    }

    @Test
    fun currencyMismatchRollsBackBothRecordAndApproval() = runBlocking {
        val day = LocalDate.now()
        LedgerRepository(db)
            .saveAccount(
                UUID.randomUUID().toString(),
                "usd",
                "Dollar bank",
                "BANK",
                "USD",
                10000,
                openingDate = day.toEpochDay(),
            )
        val (s, _) = repo.offer("Bank account debited INR 100 at Store on $day Ref CURRENCY123")!!
        try {
            repo.approve(s.id, repo.read(s), "usd", "other-expense")
            fail("currency mismatch")
        } catch (_: IllegalArgumentException) {}
        assertEquals("PENDING", repo.dao.get(s.id)!!.status)
        assertEquals(0L, db.ledger().recordCount())
    }

    @Test
    fun invalidGiftLeavesSuggestionPendingWithoutLedgerMutation() = runBlocking {
        val (s, _) = repo.offer("Myntra gift card worth INR 500")!!
        try {
            repo.approve(s.id, repo.read(s), "", "")
            fail("missing code")
        } catch (_: IllegalArgumentException) {}
        assertEquals("PENDING", repo.dao.get(s.id)!!.status)
        assertTrue(gifts.dao.all().isEmpty())
    }

    @Test
    fun cardPurchasesRequireCardAccountsAndEquivalentReferencesDeduplicate() = runBlocking {
        val ledger = LedgerRepository(db)
        val day = LocalDate.now()
        ledger.saveAccount(UUID.randomUUID().toString(), "bank", "Bank", "BANK", "INR", 10000)
        ledger.saveAccount(UUID.randomUUID().toString(), "card", "Card", "CREDIT_CARD", "INR", 0)
        val (s, _) =
            repo.offer(
                "Your credit card XX4821 spent INR 100 at TEST STORE on $day Ref DEMO123456"
            )!!
        val d = repo.read(s)
        try {
            repo.approve(s.id, d.copy(cardRequired = false), "bank", "food")
            fail("Never post a card purchase to bank")
        } catch (_: IllegalArgumentException) {}
        repo.approve(s.id, d, "card", "food")
        assertEquals(
            -10000L,
            ledger.dao.accounts().first().first { it.account.id == "card" }.balanceMinor,
        )
        assertEquals(
            10000L,
            ledger.dao.accounts().first().first { it.account.id == "bank" }.balanceMinor,
        )
        assertFalse(
            repo.offer("Credit card spent INR 100 at TEST STORE on $day Ref DEMO123456")!!.second
        )
        assertEquals(1L, ledger.dao.recordCount())
    }

    @Test
    fun discountCodesAreScopedToMerchantAndRemainReviewed() = runBlocking {
        val a = repo.offer("Merchant: Test A\nCoupon code: DEMO1234")!!
        val b = repo.offer("Merchant: Test B\nCoupon code: DEMO1234")!!
        assertTrue(a.second)
        assertTrue(b.second)
        assertNotEquals(a.first.id, b.first.id)
        assertFalse(repo.offer("Merchant: Test A\nCoupon code: DEMO1234")!!.second)
        assertEquals(0, db.gifts().all().size)
    }
}
