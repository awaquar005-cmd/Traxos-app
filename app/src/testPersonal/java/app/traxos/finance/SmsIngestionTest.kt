package app.traxos.finance

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.*
import app.traxos.finance.gifts.*
import app.traxos.finance.smart.*
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SmsIngestionTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val name = "sms-${UUID.randomUUID()}.db"
    private val db = TraxosDatabase.open(app, name)
    private val ledger = LedgerRepository(db)
    private val gifts = GiftRepository(db, TestGiftCipher())
    private val smart = SmartRepository(db, TestGiftCipher(), ledger, gifts)
    private var permission = false
    private val receive = SmsIngestion(ledger, smart) { permission }

    @After
    fun close() {
        db.close()
        app.deleteDatabase(name)
    }

    @Test
    fun consentPermissionRevocationAndDuplicateGatesNeverAutoPost() = runBlocking {
        val text =
            "Test Bank a/c XX1234 debited INR 125.00 at Test cafe on 31-Dec-2025 Ref DEMO123456"
        assertNull(receive.receive("AX-TSTBNK", text))
        permission = true
        assertNull(receive.receive("AX-TSTBNK", text))
        ledger.setSmsOptIn(true)
        val offered = receive.receive("AX-TSTBNK", text)!!
        assertTrue(offered.second)
        assertEquals("2025-12-31", smart.read(offered.first).day)
        assertFalse(receive.receive("AX-TSTBNK", text)!!.second)
        permission = false
        assertNull(receive.receive("AX-TSTBNK", text.replace("DEMO123456", "DEMO123457")))
        permission = true
        ledger.setSmartEnabled(false)
        assertNull(receive.receive("AX-TSTBNK", text.replace("DEMO123456", "DEMO123457")))
        assertEquals(0L, ledger.dao.recordCount())
        assertEquals(0, db.gifts().all().size)
    }

    @Test
    fun otpConversationsAndLinksAreNotStoredButGiftSuggestionsRequireReview() = runBlocking {
        permission = true
        ledger.setSmsOptIn(true)
        assertNull(receive.receive("AX-TSTBNK", "OTP 123456 for INR 100 bank payment"))
        assertNull(receive.receive("+919876543210", "Bank account debited INR 100"))
        assertNull(receive.receive("AX-STORE", "See https://example.invalid/claim"))
        assertNull(receive.receive("AX-STORE", "Meet me for lunch tomorrow"))
        val gift =
            receive.receive(
                "AX-STORE",
                "Merchant: Test Store\nGift card code: TEST-ABC123\nValue INR 500",
                "2025-12-30",
            )!!
        assertEquals("GIFT", gift.first.kind)
        assertEquals("PENDING", gift.first.status)
        assertEquals(0, db.gifts().all().size)
        assertEquals(1, db.smart().observe("ALL", 200).first().size)
    }
}
