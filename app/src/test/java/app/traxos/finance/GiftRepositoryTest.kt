package app.traxos.finance

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.TraxosDatabase
import app.traxos.finance.gifts.*
import java.util.UUID
import javax.crypto.KeyGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** JCE test keys replace only Android Keystore provisioning, never production envelope crypto. */
class TestGiftCipher : GiftCipher {
    val aes = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    val hmac = KeyGenerator.getInstance("HmacSHA256").generateKey()

    override fun seal(id: String, bytes: ByteArray) = GiftEnvelope.seal(aes, id, bytes)

    override fun open(id: String, bytes: ByteArray) = GiftEnvelope.open(aes, id, bytes)

    override fun fingerprint(code: String) =
        javax.crypto.Mac.getInstance("HmacSHA256").run {
            init(hmac)
            doFinal(GiftRules.normalize(code).toByteArray()).joinToString("") { "%02x".format(it) }
        }
}

class TestGiftScheduler : GiftScheduler {
    var reconciles = 0
    val cancelled = mutableListOf<String>()

    override fun reconcile() {
        reconciles++
    }

    override fun cancel(cardId: String) {
        cancelled += cardId
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class GiftRepositoryTest {
    private fun card() =
        GiftDraft(
                merchant = "Amazon",
                name = "Birthday",
                code = "CODE-1234",
                original = "1000",
                remaining = "1000",
                partial = true,
            )
            .card()

    @Test
    fun fullOfflineLifecyclePersistsWithoutChangingLedger() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "gifts-${UUID.randomUUID()}.db"
        val cipher = TestGiftCipher()
        val scheduler = TestGiftScheduler()
        var db = TraxosDatabase.open(context, name)
        try {
            var repo = GiftRepository(db, cipher, scheduler)
            val c =
                repo.save(
                    card(),
                    GiftSecret("CODE-1234", "1234", "Private note", byteArrayOf(1, 2, 3)),
                    0,
                )
            assertFalse(c.secret.toString(Charsets.UTF_8).contains("CODE-1234"))
            assertFalse(c.fingerprint.contains("CODE"))
            val r = repo.redeem(c.id, 1, 30000, "operation")
            assertEquals(r, repo.redeem(c.id, 1, 30000, "operation"))
            assertEquals(70000L, repo.dao.card(c.id)!!.remainingMinor)
            assertEquals(1, repo.dao.history(c.id).first().size)
            assertEquals(0L, db.ledger().recordCount())
            assertEquals(0L, db.ledger().operationCount())
            db.close()
            db = TraxosDatabase.open(context, name)
            repo = GiftRepository(db, cipher, scheduler)
            assertEquals("1234", repo.secret(repo.dao.card(c.id)!!).pin)
            assertArrayEquals(byteArrayOf(1, 2, 3), repo.secret(repo.dao.card(c.id)!!).image)
            repo.reverse(c.id, 2, "undo")
            assertEquals(100000L, repo.dao.card(c.id)!!.remainingMinor)
            assertEquals("UNDO", repo.dao.history(c.id).first().first().kind)
            repo.redeem(c.id, 3, 100000, "all")
            assertTrue(repo.dao.card(c.id)!!.redeemed)
            repo.reverse(c.id, 4, "refund", true)
            assertEquals(100000L, repo.dao.card(c.id)!!.remainingMinor)
            assertEquals("REFUND", repo.dao.history(c.id).first().first().kind)
            repo.archive(c.id, 5, true)
            assertTrue(repo.dao.card(c.id)!!.archived)
            repo.archive(c.id, 6, false)
            repo.delete(c.id, 7)
            assertTrue(repo.dao.all().isEmpty())
            assertTrue(scheduler.cancelled.contains(c.id))
            assertTrue(scheduler.reconciles >= 7)
            assertEquals(0L, db.ledger().recordCount())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun duplicateImportAndStaleMutationsCannotChangeValue() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val db = TraxosDatabase.open(context, "duplicates-${UUID.randomUUID()}.db")
        try {
            val repo = GiftRepository(db, TestGiftCipher())
            val c = repo.save(card(), GiftSecret("CODE-1234"), 0)
            try {
                repo.save(card().copy(id = "second"), GiftSecret(" code 1234 "), 0)
                fail("duplicate saved")
            } catch (_: IllegalArgumentException) {}
            assertEquals(1, repo.dao.all().size)
            repo.redeem(c.id, 1, 100, "first")
            try {
                repo.redeem(c.id, 1, 100, "second")
                fail("stale mutation")
            } catch (_: IllegalArgumentException) {}
            assertEquals(99900L, repo.dao.card(c.id)!!.remainingMinor)
            val old = repo.dao.card(c.id)!!
            try {
                repo.save(old.copy(remainingMinor = 500), GiftSecret("CODE-1234"), 2)
                fail("edited history balance")
            } catch (_: IllegalArgumentException) {}
        } finally {
            val name = db.openHelper.databaseName
            db.close()
            if (name != null) context.deleteDatabase(name)
        }
    }

    @Test
    fun uncertainBalanceDiscountAndPreferencesRemainHonest() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val db = TraxosDatabase.open(context, "unknown-${UUID.randomUUID()}.db")
        try {
            val repo = GiftRepository(db, TestGiftCipher())
            val unknown =
                repo.save(
                    card().copy(originalMinor = null, remainingMinor = null),
                    GiftSecret("UNKNOWN"),
                    0,
                )
            try {
                repo.redeem(unknown.id, 1, 1, "bad")
                fail("unknown spent")
            } catch (_: IllegalArgumentException) {}
            val discount =
                repo.save(
                    card()
                        .copy(
                            id = "discount",
                            kind = "DISCOUNT",
                            originalMinor = null,
                            remainingMinor = null,
                        ),
                    GiftSecret("DISCOUNT"),
                    0,
                )
            repo.redeem(discount.id, 1, null, "discount-use")
            assertTrue(repo.dao.card(discount.id)!!.redeemed)
            repo.updateSettings(
                GiftSettings(
                    intervals = "14,2,0",
                    sort = "MERCHANT",
                    hideCodes = false,
                    textDetection = false,
                )
            )
            assertEquals("14,2,0", repo.dao.settings().first()!!.intervals)
            assertFalse(repo.dao.settings().first()!!.textDetection)
            repo.updateSettings(GiftSettings(autoReview = false))
            assertFalse(repo.dao.settings().first()!!.autoReview)
        } finally {
            val name = db.openHelper.databaseName
            db.close()
            if (name != null) context.deleteDatabase(name)
        }
    }

    @Test
    fun encryptedEnvelopeAuthenticatesRowAndTampering() {
        val cipher = TestGiftCipher()
        val bytes = "secret-code-pin".toByteArray()
        val a = cipher.seal("one", bytes)
        val b = cipher.seal("one", bytes)
        assertFalse(a.contentEquals(b))
        assertArrayEquals(bytes, cipher.open("one", a))
        assertThrows(Exception::class.java) { cipher.open("two", a) }
        a[a.lastIndex] = (a.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { cipher.open("one", a) }
    }

    @Test
    fun editsKeepArchiveAndExpiryStateAndZeroBalanceCanBeCorrected() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "gift-edits-${UUID.randomUUID()}.db"
        val db = TraxosDatabase.open(context, name)
        try {
            val repo = GiftRepository(db, TestGiftCipher())
            val secret = GiftSecret("CODE-1234")
            val c = repo.save(card(), secret, 0)
            repo.archive(c.id, 1, true)
            repo.save(repo.dao.card(c.id)!!.copy(name = "Renamed", remainingMinor = 0), secret, 2)
            assertTrue(repo.dao.card(c.id)!!.archived)
            assertTrue(repo.dao.card(c.id)!!.redeemed)
            repo.save(repo.dao.card(c.id)!!.copy(remainingMinor = 100000), secret, 3)
            assertFalse(repo.dao.card(c.id)!!.redeemed)
            repo.archive(c.id, 4, false)
            repo.markExpired(c.id, 5)
            assertEquals("EXPIRED", GiftRules.state(repo.dao.card(c.id)!!))
            try {
                repo.redeem(c.id, 6, 100, "expired")
                fail("expired spent")
            } catch (_: IllegalArgumentException) {}
            repo.markExpired(c.id, 6, false)
            assertEquals("ACTIVE", GiftRules.state(repo.dao.card(c.id)!!))
            assertTrue(repo.dao.all().single().secret.isEmpty())
            assertEquals("CODE-1234", repo.secret(repo.dao.all().single()).code)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun missingKeysFailWithoutReplacingExistingRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "gift-keys-${UUID.randomUUID()}.db"
        val db = TraxosDatabase.open(context, name)
        try {
            val jce = TestGiftCipher()
            var missing = false
            var seals = 0
            val cipher =
                object : GiftCipher {
                    override fun seal(id: String, bytes: ByteArray): ByteArray {
                        seals++
                        return jce.seal(id, bytes)
                    }

                    override fun open(id: String, bytes: ByteArray) = jce.open(id, bytes)

                    override fun fingerprint(code: String) = jce.fingerprint(code)

                    override fun verifyExisting() {
                        check(!missing)
                    }
                }
            val repo = GiftRepository(db, cipher)
            repo.save(card(), GiftSecret("CODE-1234"), 0)
            missing = true
            try {
                repo.save(card().copy(id = "new"), GiftSecret("ANOTHER-1234"), 0)
                fail("new key accepted")
            } catch (_: IllegalStateException) {}
            assertEquals(1, seals)
            assertEquals(1, repo.dao.all().size)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun purchasesGiftsPromotionsAndRefundsNeverPostCashExpenses() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "gift-accounting-${UUID.randomUUID()}.db"
        val db = TraxosDatabase.open(context, name)
        try {
            app.traxos.finance.data
                .LedgerRepository(db)
                .saveAccount(
                    UUID.randomUUID().toString(),
                    "bank",
                    "Real bank",
                    "BANK",
                    "INR",
                    500000,
                    openingDate = java.time.LocalDate.now().toEpochDay(),
                )
            val initialOperations = db.ledger().operationCount()
            val repo = GiftRepository(db, TestGiftCipher())
            val cards =
                listOf("PURCHASED", "RECEIVED", "PROMOTIONAL").map { source ->
                    repo.save(
                        card().copy(id = UUID.randomUUID().toString(), source = source),
                        GiftSecret("CODE-$source"),
                        0,
                    )
                }
            assertEquals(listOf("PURCHASED", "RECEIVED", "PROMOTIONAL"), cards.map { it.source })
            repo.redeem(cards.first().id, 1, 30000, "spend-gift")
            assertEquals(70000L, repo.dao.card(cards.first().id)!!.remainingMinor)
            repo.reverse(cards.first().id, 2, "merchant-refund", true)
            assertEquals(100000L, repo.dao.card(cards.first().id)!!.remainingMinor)
            assertEquals(500000L, db.ledger().accounts().first().single().balanceMinor)
            assertEquals(0L, db.ledger().recordCount())
            assertEquals(initialOperations, db.ledger().operationCount())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
