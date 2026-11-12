package app.traxos.finance

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.*
import app.traxos.finance.gifts.*
import app.traxos.finance.smart.SmartParser
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
@Config(sdk = [28], application = Application::class)
class CompactCardTest {
    @Test
    fun payCodeMessageRecognizesAlphanumericPinAndNeverInventsMissingPrice() {
        val raw =
            "Use Amazon Pay Code: 9876543210987654 Pin: TEST647XDEMO99 TnC: https://example.invalid/rules - Test issuer"
        val result = GiftParser.parse(raw)
        assertEquals("Amazon", result.merchant)
        assertEquals("9876543210987654", result.code)
        assertEquals("TEST647XDEMO99", result.pin)
        assertEquals("", result.amount)
        assertEquals("GIFT", SmartParser.parse(raw)!!.kind)
        assertEquals("750.50", GiftParser.parse(raw + "\nValue INR 750.50").amount)
        assertNull(SmartParser.parse("OTP 123456 verification code"))
    }

    @Test
    fun newGiftOriginalDefaultsRemainingButNeverOverridesExplicitOrHistoricalBalances() {
        assertEquals(
            100000L,
            GiftDraft(merchant = "Test", code = "TEST-CODE-123", original = "1000")
                .card()
                .remainingMinor,
        )
        assertEquals(
            0L,
            GiftDraft(merchant = "Test", code = "TEST-CODE-123", original = "1000", remaining = "0")
                .card()
                .remainingMinor,
        )
        assertNull(
            GiftDraft(
                    merchant = "Test",
                    code = "TEST-CODE-123",
                    original = "1000",
                    revision = 1,
                    historyLocked = true,
                )
                .card()
                .remainingMinor
        )
        assertNull(
            GiftDraft(
                    merchant = "Test",
                    code = "TEST-CODE-123",
                    kind = "DISCOUNT",
                    original = "1000",
                )
                .card()
                .remainingMinor
        )
    }

    @Test
    fun cardRulesValidateWithoutLoggingValuesAndKeepExpiredReferences() {
        assertEquals(
            "4111111111111111",
            CardSecret("4111 1111 1111 1111", "123", "12/30").validated().number,
        )
        assertEquals("", CardSecret("4111111111111111", "", "01/2020").validated().cvv)
        assertTrue(CardSecret("4111111111111111", "123", "01/20").validated().expired())
        for (value in
            listOf(
                CardSecret("4111111111111112", "123", "12/30"),
                CardSecret("4111111111111111", "12", "12/30"),
                CardSecret("4111111111111111", "123", "13/30"),
            )) {
            assertThrows(IllegalArgumentException::class.java) { value.validated() }
        }
    }

    @Test
    fun encryptedCardDetailsAreIdempotentAndPreserveDebtAccountingAcrossRestart() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "card-${UUID.randomUUID()}.db"
        var db = TraxosDatabase.open(context, name)
        val cipher = TestGiftCipher()
        try {
            var ledger = LedgerRepository(db)
            var vault = CardVaultRepository(db, cipher)
            val day = LocalDate.now().toEpochDay()
            ledger.saveAccount(
                UUID.randomUUID().toString(),
                "card",
                "Demo card",
                "CREDIT_CARD",
                "INR",
                1000,
                openingDate = day - 1,
            )
            val secret = CardSecret("4111111111111111", "123", "12/30")
            val operation = UUID.randomUUID().toString()
            val count = db.ledger().operationCount()
            vault.save("card", secret, 0, operation)
            vault.save("card", secret, 0, operation)
            assertEquals(1L, db.cardReferences().get("card")!!.revision)
            assertFalse(
                db.cardReferences()
                    .get("card")!!
                    .payload
                    .toString(Charsets.UTF_8)
                    .contains(secret.number)
            )
            assertEquals(count, db.ledger().operationCount())
            assertEquals(-1000L, db.ledger().accounts().first().single().balanceMinor)
            try {
                vault.save("card", secret, 0, UUID.randomUUID().toString())
                fail("stale update accepted")
            } catch (_: IllegalArgumentException) {}
            val category = db.ledger().categories().first().first { it.type == "EXPENSE" }.id
            ledger.saveRecord(
                UUID.randomUUID().toString(),
                "purchase",
                "card",
                category,
                "EXPENSE",
                500,
                day,
                "Demo purchase",
            )
            db.close()
            db = TraxosDatabase.open(context, name)
            vault = CardVaultRepository(db, cipher)
            ledger = LedgerRepository(db)
            assertEquals(secret, vault.read("card").first)
            assertEquals(-1500L, ledger.dao.accounts().first().single().balanceMinor)
            vault.remove("card", 1)
            assertNull(db.cardReferences().get("card"))
            assertEquals(1L, ledger.dao.recordCount())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
