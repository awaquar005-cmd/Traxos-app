package app.traxos.finance

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.backup.*
import app.traxos.finance.data.*
import app.traxos.finance.gifts.*
import java.io.*
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
class PortableBackupTest {
    private val context
        get() = ApplicationProvider.getApplicationContext<Application>()

    private val databases = mutableListOf<TraxosDatabase>()

    private fun db() =
        TraxosDatabase.open(context, "backup-${UUID.randomUUID()}.db").also { databases += it }

    private fun id() = UUID.randomUUID().toString()

    private val password
        get() = "Fixture password 123!".toCharArray()

    @After
    fun cleanup() {
        databases.forEach {
            val name = it.openHelper.databaseName!!
            it.close()
            context.deleteDatabase(name)
        }
    }

    private suspend fun seed(db: TraxosDatabase): String {
        val day = LocalDate.now().toEpochDay()
        val repo = LedgerRepository(db)
        repo.saveAccount(id(), "bank", "Main bank", "BANK", "INR", 100000, openingDate = day)
        val record = id()
        repo.saveRecord(id(), record, "bank", "food", "EXPENSE", 2500, day, "Lunch")
        return record
    }

    private suspend fun archive(service: PortableBackup): ByteArray =
        ByteArrayOutputStream().also { service.write(it, password) }.toByteArray()

    @Test
    fun encryptedRoundTripRestoresLedgerAndPortableGiftsWithDifferentDeviceKeys() =
        runBlocking<Unit> {
            val source = db()
            seed(source)
            val sourceKey = TestGiftCipher()
            val gift =
                GiftRepository(source, sourceKey, TestGiftScheduler())
                    .save(
                        GiftDraft(
                                merchant = "Amazon",
                                name = "Fixture",
                                code = "FIXTURE-SECRET-1234",
                                original = "100",
                                remaining = "100",
                                partial = true,
                            )
                            .card(),
                        GiftSecret("FIXTURE-SECRET-1234", "4567", "Private", byteArrayOf(1, 2, 3)),
                        0,
                    )
            val bytes = archive(PortableBackup(context, source, sourceKey))
            assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("FIXTURE-SECRET"))
            val target = db()
            val targetKey = TestGiftCipher()
            val restore = PortableBackup(context, target, targetKey)
            restore.inspect(ByteArrayInputStream(bytes), password).use { preview ->
                assertEquals(1L, preview.counts["transactions"])
                restore.restore(preview)
                assertEquals(0L, restore.restore(preview))
            }
            assertEquals(97500L, target.ledger().accounts().first().single().balanceMinor)
            assertEquals(source.ledger().operationCount(), target.ledger().operationCount())
            val portable = target.gifts().card(gift.id)!!
            val secret = GiftSecret.read(targetKey.open(gift.id, portable.secret))
            assertEquals("4567", secret.pin)
            assertArrayEquals(byteArrayOf(1, 2, 3), secret.image)
            assertFalse(source.gifts().card(gift.id)!!.secret.contentEquals(portable.secret))
        }

    @Test
    fun wrongPasswordAndTamperedArchiveNeverWriteToLiveDatabase() =
        runBlocking<Unit> {
            val source = db()
            seed(source)
            val bytes = archive(PortableBackup(context, source, TestGiftCipher()))
            val target = db()
            val service = PortableBackup(context, target, TestGiftCipher())
            for (data in
                listOf(
                    bytes,
                    bytes.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() },
                    bytes.copyOf(bytes.size - 9),
                )) {
                assertThrows(Exception::class.java) {
                    runBlocking {
                        service
                            .inspect(
                                ByteArrayInputStream(data),
                                if (data === bytes) "Wrong password 123".toCharArray() else password,
                            )
                            .close()
                    }
                }
                assertEquals(0L, target.ledger().recordCount())
            }
        }

    @Test
    fun sameIdConflictRollsBackAllInsertedRows() =
        runBlocking<Unit> {
            val source = db()
            val record = seed(source)
            val cipher = TestGiftCipher()
            val bytes = archive(PortableBackup(context, source, cipher))
            LedgerRepository(source)
                .saveRecord(
                    id(),
                    record,
                    "bank",
                    "food",
                    "EXPENSE",
                    3000,
                    LocalDate.now().toEpochDay(),
                    "Changed",
                    1,
                )
            val service = PortableBackup(context, source, cipher)
            service.inspect(ByteArrayInputStream(bytes), password).use { preview ->
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { service.restore(preview) }
                }
            }
            assertEquals(3000L, source.ledger().record(record)!!.amountMinor)
            assertEquals(1L, source.ledger().recordCount())
        }

    @Test
    fun vaultSmsConsentAndAppSettingsAreNotExported() =
        runBlocking<Unit> {
            val source = db()
            seed(source)
            withContext(Dispatchers.IO) {
                source.openHelper.writableDatabase.execSQL(
                    "UPDATE preferences SET displayName='SECRET_PROFILE',smsOptIn=1"
                )
                source.openHelper.writableDatabase.execSQL(
                    "INSERT INTO account_card_secrets(accountId,payload,revision,operationId) VALUES ('bank',X'010203',1,'fixture-op')"
                )
            }
            val target = db()
            val service = PortableBackup(context, target, TestGiftCipher())
            service
                .inspect(
                    ByteArrayInputStream(
                        archive(PortableBackup(context, source, TestGiftCipher()))
                    ),
                    password,
                )
                .use { service.restore(it) }
            withContext(Dispatchers.IO) {
                target.openHelper.writableDatabase
                    .query("SELECT COUNT(*) FROM account_card_secrets")
                    .use {
                        it.moveToFirst()
                        assertEquals(0, it.getInt(0))
                    }
            }
            assertFalse(target.ledger().preferences().first()!!.smsOptIn)
            assertEquals("", target.ledger().preferences().first()!!.displayName)
        }

    @Test
    fun renamedSeedCategoriesRestoreOnlyIntoPristineDatabase() =
        runBlocking<Unit> {
            val source = db()
            seed(source)
            withContext(Dispatchers.IO) {
                source.openHelper.writableDatabase.execSQL(
                    "UPDATE categories SET name='Lunch',revision=2 WHERE id='food'"
                )
            }
            val target = db()
            val service = PortableBackup(context, target, TestGiftCipher())
            service
                .inspect(
                    ByteArrayInputStream(
                        archive(PortableBackup(context, source, TestGiftCipher()))
                    ),
                    password,
                )
                .use { service.restore(it) }
            assertEquals("Lunch", target.ledger().category("food")!!.name)
        }

    @Test
    fun malformedFinancialArchiveIsRejectedBeforeRestore() =
        runBlocking<Unit> {
            val source = db()
            val record = seed(source)
            withContext(Dispatchers.IO) {
                source.openHelper.writableDatabase.execSQL(
                    "UPDATE transactions SET amountMinor=-1 WHERE id=?",
                    arrayOf(record),
                )
            }
            val target = db()
            val service = PortableBackup(context, target, TestGiftCipher())
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    service
                        .inspect(
                            ByteArrayInputStream(
                                archive(PortableBackup(context, source, TestGiftCipher()))
                            ),
                            password,
                        )
                        .close()
                }
            }
            assertEquals(0L, target.ledger().recordCount())
        }

    @Test
    fun deletedGiftAndReusedCodeRemainDistinctAfterRestore() =
        runBlocking<Unit> {
            val source = db()
            val key = TestGiftCipher()
            val gifts = GiftRepository(source, key, TestGiftScheduler())
            val draft =
                GiftDraft(
                    merchant = "Amazon",
                    name = "Fixture",
                    code = "REUSED-1234",
                    original = "100",
                    remaining = "100",
                    partial = true,
                )
            val first = gifts.save(draft.card(), GiftSecret("REUSED-1234"), 0)
            gifts.delete(first.id, 1)
            gifts.save(draft.copy(id = id()).card(), GiftSecret("REUSED-1234"), 0)
            val target = db()
            val service = PortableBackup(context, target, TestGiftCipher())
            service
                .inspect(
                    ByteArrayInputStream(archive(PortableBackup(context, source, key))),
                    password,
                )
                .use { service.restore(it) }
            assertEquals(2L, target.gifts().count())
            assertEquals(1, target.gifts().all().size)
        }

    @Test
    fun transfersAndJournalRestoreWithoutBecomingExpenses() =
        runBlocking<Unit> {
            val source = db()
            seed(source)
            val ledger = LedgerRepository(source)
            val day = LocalDate.now().toEpochDay()
            ledger.saveAccount(id(), "cash", "Cash", "CASH", "INR", 0, openingDate = day)
            ledger.transfer(id(), "bank", "cash", 10000, day)
            val target = db()
            val service = PortableBackup(context, target, TestGiftCipher())
            service
                .inspect(
                    ByteArrayInputStream(
                        archive(PortableBackup(context, source, TestGiftCipher()))
                    ),
                    password,
                )
                .use { service.restore(it) }
            val accounts =
                target.ledger().accounts().first().associate { it.account.id to it.balanceMinor }
            assertEquals(87500L, accounts["bank"])
            assertEquals(10000L, accounts["cash"])
            assertEquals(3L, target.ledger().recordCount())
        }

    @Test
    fun shortPasswordAndInterruptedWritesDoNotProduceValidBackups() =
        runBlocking<Unit> {
            val source = db()
            seed(source)
            val service = PortableBackup(context, source, TestGiftCipher())
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { service.write(ByteArrayOutputStream(), "short".toCharArray()) }
            }
            val broken =
                object : OutputStream() {
                    override fun write(value: Int) {
                        throw IOException("Fixture failure")
                    }
                }
            assertThrows(IOException::class.java) {
                runBlocking { service.write(broken, password) }
            }
            assertEquals(1L, source.ledger().recordCount())
        }

    @Test
    fun exportEscapesFormulasQuotesAndKeepsCurrencyTypeAndDeletedStatus() =
        runBlocking<Unit> {
            assertEquals("\"'=SUM(A1)\"", LedgerCsv.cell("=SUM(A1)"))
            assertEquals("\"a\"\"b\"", LedgerCsv.cell("a\"b"))
            val source = db()
            val record = seed(source)
            LedgerRepository(source).setDeleted(id(), record, 1, true)
            val output = ByteArrayOutputStream()
            LedgerCsv.write(source, output)
            val csv = output.toString("UTF-8")
            assertTrue(csv.contains("\"25.00\",\"INR\""))
            assertTrue(csv.contains("\"EXPENSE\""))
            assertTrue(csv.contains("\"1\"\r\n"))
            assertFalse(csv.contains("CVV"))
        }
}
