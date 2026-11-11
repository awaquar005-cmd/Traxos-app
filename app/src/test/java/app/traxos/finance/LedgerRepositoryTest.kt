package app.traxos.finance

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.*
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
class LedgerRepositoryTest {
    private lateinit var db: TraxosDatabase
    private lateinit var repo: LedgerRepository
    private lateinit var app: Application
    private lateinit var file: String
    private val day = LocalDate.of(2026, 11, 11).toEpochDay()

    private fun uuid() = UUID.randomUUID().toString()

    @Before
    fun before() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        file = "test-${uuid()}.db"
        db = TraxosDatabase.open(app, file)
        repo = LedgerRepository(db)
        repo.saveAccount(uuid(), "bank", "Main bank", "BANK", "INR", 10000, openingDate = day - 365)
    }

    @After
    fun after() {
        db.close()
        app.deleteDatabase(file)
    }

    private suspend fun add(
        op: String = uuid(),
        id: String = uuid(),
        amount: Long = 2500,
        type: String = "EXPENSE",
        account: String = "bank",
        category: String = if (type == "INCOME") "salary" else "food",
    ): String {
        repo.saveRecord(op, id, account, category, type, amount, day, "Lunch")
        return id
    }

    @Test
    fun balancesAndJournalAreAtomic() = runBlocking {
        add()
        add(amount = 5000, type = "INCOME")
        assertEquals(12500L, db.ledger().accounts().first().single().balanceMinor)
        assertEquals(3L, db.ledger().operationCount())
    }

    @Test
    fun tenConcurrentTapsOneIntendedOperation() = runBlocking {
        val op = uuid()
        val id = uuid()
        coroutineScope { repeat(10) { launch(Dispatchers.Default) { add(op, id) } } }
        assertEquals(1L, db.ledger().recordCount())
        assertEquals(2L, db.ledger().operationCount())
    }

    @Test
    fun retryAfterReopenDoesNotDuplicate() = runBlocking {
        val op = uuid()
        val id = add(op)
        db.close()
        db = TraxosDatabase.open(app, file)
        repo = LedgerRepository(db)
        add(op, id)
        assertEquals(1L, db.ledger().recordCount())
        assertEquals(7500L, db.ledger().accounts().first().single().balanceMinor)
    }

    @Test
    fun editDeleteUndoUpdateBalances() = runBlocking {
        val id = add()
        repo.saveRecord(uuid(), id, "bank", "food", "EXPENSE", 3000, day, "Updated", 1)
        assertEquals(7000L, db.ledger().accounts().first().single().balanceMinor)
        repo.setDeleted(uuid(), id, 2, true)
        assertEquals(10000L, db.ledger().accounts().first().single().balanceMinor)
        repo.setDeleted(uuid(), id, 3, false)
        assertEquals(7000L, db.ledger().accounts().first().single().balanceMinor)
        assertEquals(5L, db.ledger().operationCount())
    }

    @Test
    fun staleEditKeepsOriginalAndJournal() = runBlocking {
        val id = add()
        try {
            repo.saveRecord(uuid(), id, "bank", "food", "EXPENSE", 3000, day, "Stale", 7)
            fail("stale revision accepted")
        } catch (_: IllegalArgumentException) {}
        assertEquals(2500L, db.ledger().record(id)!!.amountMinor)
        assertEquals(2L, db.ledger().operationCount())
    }

    @Test
    fun failureWritingOutboxRollsBackLedger() = runBlocking {
        withContext(Dispatchers.IO) {
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_outbox BEFORE INSERT ON outbox_operations BEGIN SELECT RAISE(ABORT, 'test failure'); END"
            )
        }
        try {
            add()
            fail("Expected injected storage failure")
        } catch (_: android.database.sqlite.SQLiteException) {}
        assertEquals(0L, db.ledger().recordCount())
        assertEquals(1L, db.ledger().operationCount())
    }

    @Test
    fun invalidCategoryDoesNotWrite() = runBlocking {
        try {
            add(category = "salary")
            fail("wrong category accepted")
        } catch (_: IllegalArgumentException) {}
        assertEquals(0L, db.ledger().recordCount())
        assertEquals(1L, db.ledger().operationCount())
    }

    @Test
    fun archivedAccountBlocksNewPostsButPreservesHistory() = runBlocking {
        add()
        repo.saveAccount(uuid(), "bank", "Renamed", "BANK", "INR", 0, 1, true)
        try {
            add()
            fail("archived account accepted new transaction")
        } catch (_: IllegalArgumentException) {}
        val a = db.ledger().accounts().first().single()
        assertEquals("Renamed", a.account.name)
        assertEquals(7500L, a.balanceMinor)
        assertTrue(a.account.archived)
    }

    @Test
    fun currenciesStaySeparate() = runBlocking {
        repo.saveAccount(uuid(), "usd", "Dollar bank", "BANK", "USD", 7000, openingDate = day - 1)
        add(account = "usd", amount = 1000)
        val summary = db.ledger().summary(day, day + 1).first().single()
        assertEquals("USD", summary.currency)
        assertEquals(1000L, summary.expenseMinor)
        assertEquals(2, db.ledger().accounts().first().size)
    }

    @Test
    fun currencyChangeRejectedOnEdit() = runBlocking {
        val id = add()
        repo.saveAccount(uuid(), "usd", "Dollar bank", "BANK", "USD", 0, openingDate = day - 1)
        try {
            repo.saveRecord(uuid(), id, "usd", "food", "EXPENSE", 2500, day, "", 1)
            fail("changed original currency")
        } catch (_: IllegalArgumentException) {}
        assertEquals("bank", db.ledger().record(id)!!.accountId)
    }

    @Test
    fun themeAndNotesSurviveDatabaseReopen() = runBlocking {
        val id = add()
        repo.setTheme("DARK")
        db.close()
        db = TraxosDatabase.open(app, file)
        repo = LedgerRepository(db)
        assertEquals("DARK", db.ledger().preferences().first()!!.theme)
        assertEquals("Lunch", db.ledger().record(id)!!.note)
    }

    @Test
    fun datesBeforeOpeningRejected() = runBlocking {
        try {
            repo.saveRecord(uuid(), uuid(), "bank", "food", "EXPENSE", 10, day - 400, "", null)
            fail("date accepted")
        } catch (_: IllegalArgumentException) {}
        assertEquals(0L, db.ledger().recordCount())
    }

    @Test
    fun seededCategoriesAreNotFinancialData() = runBlocking {
        assertEquals(7, db.ledger().categories().first().size)
        assertEquals(0L, db.ledger().recordCount())
    }

    @Test
    fun journalSnapshotRetainsOriginalAfterEdit() = runBlocking {
        val op = uuid()
        val id = add(op)
        val original = db.ledger().operation(op)!!.payload
        repo.saveRecord(uuid(), id, "bank", "food", "EXPENSE", 5000, day, "Changed", 1)
        assertEquals(original, db.ledger().operation(op)!!.payload)
        assertEquals(64, db.ledger().operation(op)!!.payloadHash.length)
    }

    @Test
    fun pagedLiteralSearchUsesDatabase() = runBlocking {
        repo.saveRecord(uuid(), uuid(), "bank", "food", "EXPENSE", 100, day, "Fee 50%_\\literal")
        add()
        val page =
            db.ledger()
                .page("\\%\\_\\\\")
                .load(androidx.paging.PagingSource.LoadParams.Refresh<Int>(null, 40, false))
        assertTrue(page is androidx.paging.PagingSource.LoadResult.Page)
        val rows = (page as androidx.paging.PagingSource.LoadResult.Page).data
        assertEquals(1, rows.size)
        assertEquals("Fee 50%_\\literal", rows.single().record.note)
    }
}
