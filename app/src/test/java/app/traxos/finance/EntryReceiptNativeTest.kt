package app.traxos.finance

import android.app.Application
import android.content.Intent
import android.graphics.*
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.backup.*
import app.traxos.finance.data.*
import app.traxos.finance.entry.*
import java.io.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EntryReceiptNativeTest {
    private val context
        get() = ApplicationProvider.getApplicationContext<Application>()

    private lateinit var db: TraxosDatabase
    private val key = TestGiftCipher()

    private fun id() = UUID.randomUUID().toString()

    @Before
    fun setup() {
        db = TraxosDatabase.open(context, "receipt-${id()}.db")
        EntryInbox.pending.value = null
    }

    @After
    fun cleanup() {
        val name = db.openHelper.databaseName!!
        db.close()
        context.deleteDatabase(name)
        EntryInbox.pending.value = null
    }

    private fun uri(bytes: ByteArray): Uri {
        val u = Uri.parse("content://fixture/${id()}")
        Shadows.shadowOf(context.contentResolver)
            .registerInputStream(u, ByteArrayInputStream(bytes))
        return u
    }

    private suspend fun account(): LedgerRepository {
        val r = LedgerRepository(db)
        r.saveAccount(
            id(),
            "bank",
            "Bank",
            "BANK",
            "INR",
            10000,
            openingDate = LocalDate.now().toEpochDay(),
        )
        return r
    }

    @Test
    fun receiptsEncryptAdoptDeduplicateAndRestore() = runBlocking {
        val r = account()
        val owner = id()
        val store = ReceiptStore(context, db, key)
        val payload = "%PDF-1.4\nfixture".toByteArray()
        val receipt = store.add(owner, uri(payload))
        assertFalse(db.extras().draft(receipt)!!.secret.contentEquals(payload))
        assertArrayEquals(payload, store.bytes(receipt).second)
        try {
            store.add(owner, uri(payload))
            fail("duplicate")
        } catch (_: IllegalArgumentException) {}
        r.saveRecord(id(), owner, "bank", "food", "EXPENSE", 100, LocalDate.now().toEpochDay(), "")
        assertNull(db.extras().draft(receipt))
        assertNotNull(db.extras().receipt(receipt))
        store.delete(receipt, 1, true)
        assertTrue(db.extras().receipt(receipt)!!.deleted)
        store.delete(receipt, 2, false)
        assertFalse(db.extras().receipt(receipt)!!.deleted)
        try {
            store.delete(receipt, 1, true)
            fail("stale")
        } catch (_: IllegalArgumentException) {}
        val output = ByteArrayOutputStream()
        PortableBackup(context, db, TestGiftCipher(), key)
            .write(output, "fixture-password".toCharArray())
        val target = TraxosDatabase.open(context, "target-${id()}.db")
        val targetName = target.openHelper.databaseName!!
        try {
            val newKey = TestGiftCipher()
            val backup = PortableBackup(context, target, TestGiftCipher(), newKey)
            backup
                .inspect(
                    ByteArrayInputStream(output.toByteArray()),
                    "fixture-password".toCharArray(),
                )
                .use { backup.restore(it) }
            assertArrayEquals(payload, ReceiptStore(context, target, newKey).bytes(receipt).second)
            assertNotNull(target.ledger().record(owner))
        } finally {
            target.close()
            context.deleteDatabase(targetName)
        }
    }

    @Test
    fun imageInputIsResizedAndInvalidOrExternalDataRejected() = runBlocking {
        val store = ReceiptStore(context, db, key)
        val bitmap =
            Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.BLUE)
            }
        val bytes =
            ByteArrayOutputStream()
                .also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                .toByteArray()
        bitmap.recycle()
        val receipt = store.add(id(), uri(bytes))
        val payload = store.bytes(receipt).second
        ReceiptStore.validate("image/jpeg", payload)
        val decoded = BitmapFactory.decodeByteArray(payload, 0, payload.size)!!
        assertTrue(decoded.width <= 2000)
        assertTrue(decoded.height <= 2000)
        decoded.recycle()
        try {
            store.add(id(), Uri.parse("https://example.com/a.jpg"))
            fail("url")
        } catch (_: IllegalArgumentException) {}
        try {
            store.add(id(), uri("not image".toByteArray()))
            fail("bad")
        } catch (_: IllegalArgumentException) {}
        try {
            ReceiptStore.validate("application/pdf", ByteArray(1_000_001))
            fail("large")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun externalIntentsOnlyCarryReviewedActionAndLocalPresetIdentity() {
        val preset = id()
        val intent =
            EntryInbox.intent(context, "PRESET:$preset")
                .putExtra("amountMinor", 999999L)
                .putExtra("account", "forged")
        assertTrue(EntryInbox.receive(intent))
        assertEquals(EntryRequest("PRESET", preset), EntryInbox.pending.value)
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertFalse(intent.hasExtra("preset"))
        assertFalse(EntryInbox.receive(Intent().setAction(EntryInbox.PREFIX + "POST")))
        assertFalse(
            EntryInbox.receive(
                Intent().setAction(EntryInbox.PREFIX + "PRESET").putExtra("preset", "not-id")
            )
        )
        assertEquals("Favorite", EntryInbox.label("PRESET:$preset"))
    }

    @Test
    fun favoritesRejectStaleTargetsAndRulesPersist() = runBlocking {
        account()
        val repo = TransactionExtrasRepository(db)
        val preset = EntryPreset(id(), "Lunch", "EXPENSE", "INR", 15000, "bank", "food")
        repo.preset(preset, 0)
        assertEquals(1L, repo.dao.preset(preset.id)!!.revision)
        try {
            repo.preset(preset, 0)
            fail("stale")
        } catch (_: IllegalArgumentException) {}
        repo.archivePreset(preset.id, 1, true)
        try {
            repo.validatePreset(repo.dao.preset(preset.id)!!)
            fail("archived")
        } catch (_: IllegalArgumentException) {}
        val rule = MerchantRule(id(), "Fresh--Mart", "PREFIX", "EXPENSE", "food")
        repo.rule(rule, 0)
        assertEquals("fresh mart", repo.suggested("Fresh Mart Delhi", "EXPENSE", "bank")!!.pattern)
        repo.settings(FastEntrySettings(rules = false))
        assertNull(repo.suggested("Fresh Mart Delhi", "EXPENSE", "bank"))
    }

    @Test
    fun jsonOmitsBodiesUnlessExplicitlyChosen() = runBlocking {
        val r = account()
        val owner = id()
        val store = ReceiptStore(context, db, key)
        val payload = "%PDF-1.4 private fixture".toByteArray()
        val receipt = store.add(owner, uri(payload))
        r.saveRecord(id(), owner, "bank", "food", "EXPENSE", 100, LocalDate.now().toEpochDay(), "")
        val exports = ReadableExports(db, TestGiftCipher(), receiptCipher = key)
        val hidden = ByteArrayOutputStream()
        exports.json(hidden)
        assertTrue(hidden.toString("UTF-8").contains("Receipt bodies omitted"))
        assertFalse(
            hidden
                .toString("UTF-8")
                .contains(android.util.Base64.encodeToString(payload, android.util.Base64.NO_WRAP))
        )
        val shown = ByteArrayOutputStream()
        exports.json(shown, receiptPayloads = true)
        assertTrue(shown.toString("UTF-8").contains("portable-receipt-base64"))
        assertTrue(
            shown
                .toString("UTF-8")
                .contains(android.util.Base64.encodeToString(payload, android.util.Base64.NO_WRAP))
        )
        assertArrayEquals(payload, store.bytes(receipt).second)
    }
}
