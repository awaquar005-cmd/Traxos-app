package app.traxos.finance

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.backup.*
import app.traxos.finance.data.*
import app.traxos.finance.gifts.*
import java.io.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DebtsTest {
    private val context
        get() = ApplicationProvider.getApplicationContext<Application>()

    private lateinit var db: TraxosDatabase
    private lateinit var repo: DebtsRepository

    private fun id() = UUID.randomUUID().toString()

    private val today = LocalDate.now().toEpochDay()

    private fun row(direction: String = "LENT", currency: String = "INR") =
        DebtThread(
            id(),
            "Person",
            direction,
            currency,
            100000,
            100000,
            today,
            null,
            "",
            operationId = id(),
        )

    @Before
    fun setup() {
        db = TraxosDatabase.open(context, "debt-${id()}.db")
        repo = DebtsRepository(db)
    }

    @After
    fun close() {
        val name = db.openHelper.databaseName!!
        db.close()
        context.deleteDatabase(name)
    }

    private fun invalid(block: suspend () -> Unit) {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { block() } }
    }

    @Test
    fun repaymentAndUndoAreExactIdempotentAndNeverCreateLedgerEntries() = runBlocking {
        val d = row()
        repo.save(d, 0)
        repo.save(d, 0)
        val op = id()
        coroutineScope {
            repeat(10) { launch { repo.move(d.id, 1, -30000, "REPAYMENT", today, "Paid", op) } }
        }
        assertEquals(70000L, db.debts().thread(d.id)!!.outstandingMinor)
        assertEquals(1, db.debts().history(d.id).first().size)
        val undo = id()
        repo.undo(d.id, 2, undo)
        repo.undo(d.id, 2, undo)
        assertEquals(100000L, db.debts().thread(d.id)!!.outstandingMinor)
        assertEquals(0L, db.ledger().recordCount())
        assertTrue(db.ledger().accounts().first().isEmpty())
    }

    @Test
    fun validationAndRevisionFailuresLeaveHistoryAndBalanceIntact() = runBlocking {
        val d = row()
        repo.save(d, 0)
        invalid { repo.move(d.id, 1, -100001, "REPAYMENT", today, "", id()) }
        invalid { repo.move(d.id, 1, 30000, "REPAYMENT", today, "", id()) }
        invalid { repo.move(d.id, 1, -1, "REPAYMENT", today - 1, "", id()) }
        invalid { repo.move(d.id, 0, -1, "REPAYMENT", today, "", id()) }
        invalid { repo.archive(d.id, 1, true) }
        invalid { repo.save(d.copy(currency = "USD", operationId = id()), 1) }
        invalid { repo.save(d.copy(person = " ", operationId = id()), 1) }
        invalid { repo.save(d.copy(dueDay = today - 1, operationId = id()), 1) }
        assertEquals(100000L, db.debts().thread(d.id)!!.outstandingMinor)
        assertTrue(db.debts().history(d.id).first().isEmpty())
    }

    @Test
    fun adjustmentsSettlementArchiveAndRestoreKeepDirectionsAndCurrency() = runBlocking {
        val lent = row()
        val borrowed = row("BORROWED", "USD")
        repo.save(lent, 0)
        repo.save(borrowed, 0)
        repo.move(lent.id, 1, 20000, "INCREASE", today, "Correction", id())
        repo.move(lent.id, 2, -10000, "DECREASE", today, "Correction", id())
        repo.move(lent.id, 3, -110000, "REPAYMENT", today, "Settled", id())
        repo.archive(lent.id, 4, true)
        invalid { repo.move(lent.id, 5, 1, "INCREASE", today, "", id()) }
        repo.archive(lent.id, 5, false)
        assertEquals(0L, db.debts().thread(lent.id)!!.outstandingMinor)
        assertEquals(100000L, db.debts().thread(borrowed.id)!!.outstandingMinor)
        assertEquals("BORROWED", db.debts().thread(borrowed.id)!!.direction)
        assertEquals("USD", db.debts().thread(borrowed.id)!!.currency)
        repo.save(db.debts().thread(lent.id)!!.copy(person = "Renamed", operationId = id()), 6)
        assertEquals(3, db.debts().history(lent.id).first().size)
    }

    @Test
    fun cannotReuseOperationForDifferentDebtOrPayload() = runBlocking {
        val a = row()
        val b = row()
        repo.save(a, 0)
        repo.save(b, 0)
        invalid { repo.save(a.copy(person = "Changed"), 0) }
        val op = id()
        repo.move(a.id, 1, -100, "REPAYMENT", today, "", op)
        invalid { repo.move(b.id, 1, -100, "REPAYMENT", today, "", op) }
        invalid { repo.move(a.id, 1, -200, "REPAYMENT", today, "", op) }
        assertEquals(100000L, db.debts().thread(b.id)!!.outstandingMinor)
    }

    @Test
    fun encryptedBackupRestoresThreadsAndHistoryWithoutBankEntries() = runBlocking {
        val d = row()
        repo.save(d, 0)
        repo.move(d.id, 1, -30000, "REPAYMENT", today, "Paid", id())
        val bytes =
            ByteArrayOutputStream()
                .also {
                    PortableBackup(context, db, TestGiftCipher())
                        .write(it, "backup-password".toCharArray())
                }
                .toByteArray()
        val target = TraxosDatabase.open(context, "debt-restore-${id()}.db")
        try {
            val backup = PortableBackup(context, target, TestGiftCipher())
            backup.inspect(ByteArrayInputStream(bytes), "backup-password".toCharArray()).use {
                preview ->
                assertEquals(1L, preview.counts["debt_threads"])
                backup.restore(preview)
            }
            assertEquals(70000L, target.debts().thread(d.id)!!.outstandingMinor)
            assertEquals(1, target.debts().history(d.id).first().size)
            assertEquals(0L, target.ledger().recordCount())
        } finally {
            val name = target.openHelper.databaseName!!
            target.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun shippedSchema11UpgradesWithoutLosingAccountsPreferencesOrGiftTables() = runBlocking {
        val name = "debt-upgrade-${id()}.db"
        val schema =
            JSONObject(
                    javaClass.classLoader!!
                        .getResourceAsStream("app.traxos.finance.data.TraxosDatabase/11.json")!!
                        .bufferedReader()
                        .use { it.readText() }
                )
                .getJSONObject("database")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) old.execSQL(
                    indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)
                )
            }
            val queries = schema.getJSONArray("setupQueries")
            for (i in 0 until queries.length()) old.execSQL(queries.getString(i))
            old.execSQL(
                "INSERT INTO accounts (id,name,type,currency,openingMinor,openingDate,archived,revision,profileId) VALUES ('bank','Keep','BANK','INR',10000,20000,0,1,'local')"
            )
            old.execSQL(
                "INSERT INTO preferences (id,theme,navigationSlots) VALUES (1,'DARK','gifts,home,accounts,more')"
            )
            old.version = 11
        }
        val upgraded = TraxosDatabase.open(context, name)
        try {
            assertEquals(10000L, upgraded.ledger().accounts().first().single().balanceMinor)
            assertTrue(upgraded.debts().threads().first().isEmpty())
            assertEquals("DARK", upgraded.ledger().preferences().first()!!.theme)
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun schema11EncryptedSnapshotStillRestoresOnSchema12() = runBlocking {
        val tables =
            listOf(
                "accounts",
                "categories",
                "entry_labels",
                "transactions",
                "outbox_operations",
                "recurring_rules",
                "budgets",
                "savings_goals",
                "goal_movements",
                "gift_cards",
                "gift_redemptions",
            )
        val bytes =
            ByteArrayOutputStream()
                .also { out ->
                    BackupEnvelope.write(out, "backup-password".toCharArray()) { data ->
                        fun text(value: String) {
                            val b = value.toByteArray(Charsets.UTF_8)
                            data.writeInt(b.size)
                            data.write(b)
                        }
                        data.writeInt(1)
                        data.writeInt(11)
                        data.writeInt(tables.size)
                        tables.forEach { table ->
                            text(table)
                            data.writeLong(if (table == "accounts") 1 else 0)
                            if (table == "accounts")
                                text(
                                    JSONObject()
                                        .put("id", "old-bank")
                                        .put("name", "Previous backup")
                                        .put("type", "BANK")
                                        .put("currency", "INR")
                                        .put("openingMinor", 50000)
                                        .put("openingDate", 20000)
                                        .put("archived", 0)
                                        .put("revision", 1)
                                        .put("profileId", "local")
                                        .put("icon", "wallet")
                                        .put("color", "BLUE")
                                        .toString()
                                )
                        }
                        text("END")
                    }
                }
                .toByteArray()
        val backup = PortableBackup(context, db, TestGiftCipher())
        backup.inspect(ByteArrayInputStream(bytes), "backup-password".toCharArray()).use {
            backup.restore(it)
        }
        assertEquals(50000L, db.ledger().accounts().first().single().balanceMinor)
        assertTrue(repo.threads.first().isEmpty())
    }

    @Test
    fun inconsistentDebtBackupIsRejectedBeforeRestoring() = runBlocking {
        val d = row()
        repo.save(d, 0)
        db.openHelper.writableDatabase.execSQL("UPDATE debt_threads SET outstandingMinor=1")
        val bytes =
            ByteArrayOutputStream()
                .also {
                    PortableBackup(context, db, TestGiftCipher())
                        .write(it, "backup-password".toCharArray())
                }
                .toByteArray()
        invalid {
            PortableBackup(context, db, TestGiftCipher())
                .inspect(ByteArrayInputStream(bytes), "backup-password".toCharArray())
                .close()
        }
    }
}
