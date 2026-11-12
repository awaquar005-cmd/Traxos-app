package app.traxos.finance

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.*
import app.traxos.finance.domain.ActivityScope
import app.traxos.finance.ui.*
import java.time.LocalDate
import java.time.YearMonth
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
class OfflineLedgerToolsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val file = "tools-${UUID.randomUUID()}.db"
    private var db = TraxosDatabase.open(app, file)
    private var repo = LedgerRepository(db)
    private val feb = YearMonth.of(2024, 2)
    private val start = feb.atDay(1).toEpochDay()
    private lateinit var expense: String
    private lateinit var income: String

    private fun op() = UUID.randomUUID().toString()

    private suspend fun seed() {
        expense = repo.dao.categories().first().first { it.type == "EXPENSE" }.id
        income = repo.dao.categories().first().first { it.type == "INCOME" }.id
        for ((id, type, currency) in
            listOf(
                Triple("bank", "BANK", "INR"),
                Triple("cash", "CASH", "INR"),
                Triple("card", "CREDIT_CARD", "INR"),
                Triple("usd", "BANK", "USD"),
            )) repo.saveAccount(op(), id, id, type, currency, 100000, openingDate = start - 400)
    }

    private suspend fun add(
        id: String,
        account: String = "bank",
        type: String = "EXPENSE",
        amount: Long = 1000,
        day: Long = start,
        category: String = if (type == "INCOME") income else expense,
        note: String = id,
    ) {
        repo.saveRecord(op(), id, account, category, type, amount, day, note)
    }

    private suspend fun page(
        search: String = "",
        filter: String = "ALL",
        from: Long = Long.MIN_VALUE,
        through: Long = Long.MAX_VALUE,
        account: String = "",
        category: String = "",
        currency: String = "",
        sort: String = "NEWEST",
        deleted: Boolean = false,
    ): List<RecordRow> {
        val result =
            repo.dao
                .page(search, filter, from, through, account, category, currency, sort, deleted)
                .load(PagingSource.LoadParams.Refresh(null, 40, false))
        assertTrue(result is PagingSource.LoadResult.Page)
        return (result as PagingSource.LoadResult.Page<Int, RecordRow>).data
    }

    @After
    fun close() {
        db.close()
        app.deleteDatabase(file)
    }

    @Test
    fun combinedFiltersIncludeBothBoundariesAndKeepCurrenciesAndKindsSeparate() = runBlocking {
        seed()
        add("first", day = start)
        add("leap", day = feb.atEndOfMonth().toEpochDay())
        add("next", day = feb.plusMonths(1).atDay(1).toEpochDay())
        add("cash", account = "cash")
        add("usd", account = "usd")
        add("income", type = "INCOME")
        repo.transfer(op(), "bank", "cash", 100, start)
        assertEquals(
            setOf("first", "leap"),
            page(
                    filter = "EXPENSES",
                    from = start,
                    through = feb.atEndOfMonth().toEpochDay(),
                    account = "bank",
                    category = expense,
                    currency = "INR",
                )
                .map { it.record.id }
                .toSet(),
        )
        assertTrue(page(account = "bank", currency = "USD").isEmpty())
        assertEquals(
            listOf("income"),
            page(filter = "INCOME", currency = "INR").map { it.record.id },
        )
    }

    @Test
    fun literalSearchAndStableNewestOldestSortWorkWithPagedQuery() = runBlocking {
        seed()
        add("a", note = "100%_match", day = start)
        add("b", note = "100 other match", day = start)
        add("c", day = start + 1)
        assertEquals(listOf("a"), page(search = "100\\%\\_match").map { it.record.id })
        assertEquals(listOf("c", "b", "a"), page().map { it.record.id })
        assertEquals(listOf("a", "b", "c"), page(sort = "OLDEST").map { it.record.id })
    }

    @Test
    fun reportsSeparateIncomeRefundsCurrenciesAndArchivedHistoryAndExcludeMovementsAndTombstones() =
        runBlocking {
            seed()
            add("purchase", account = "card", amount = 5000)
            add("refund", account = "card", type = "REFUND", amount = 8000)
            add("income", type = "INCOME", amount = 20000)
            add("usd", account = "usd", amount = 100)
            add("removed", amount = 9900)
            repo.setDeleted(op(), "removed", 1, true)
            repo.transfer(op(), "bank", "cash", 400, start)
            repo.payCard(op(), "card", "bank", 1000, start)
            val category = repo.dao.category(expense)!!
            repo.saveCategory(
                op(),
                expense,
                "Renamed category",
                category.type,
                archived = true,
                revision = category.revision,
            )
            val account = repo.dao.account("card")!!
            repo.saveAccount(
                op(),
                "card",
                "Archived card",
                account.type,
                account.currency,
                0,
                archived = true,
                expectedRevision = account.revision,
            )
            val rows =
                repo.dao.categoryReport(start, feb.plusMonths(1).atDay(1).toEpochDay()).first()
            val spending = rows.single { it.currency == "INR" && it.categoryId == expense }
            assertEquals(5000L, spending.expenseMinor)
            assertEquals(8000L, spending.refundMinor)
            assertEquals(-3000L, spending.expenseMinor - spending.refundMinor)
            assertEquals("Renamed category", spending.categoryName)
            assertEquals(2L, spending.entries)
            assertEquals(20000L, rows.filter { it.currency == "INR" }.sumOf { it.incomeMinor })
            assertEquals(100L, rows.filter { it.currency == "USD" }.sumOf { it.expenseMinor })
            assertEquals(
                setOf("purchase", "refund"),
                page(
                        filter = "EXPENSES",
                        from = start,
                        through = feb.atEndOfMonth().toEpochDay(),
                        category = expense,
                        currency = "INR",
                    )
                    .map { it.record.id }
                    .toSet(),
            )
        }

    @Test
    fun reportMonthBoundariesHandleLeapDayYearRolloverAndEmptyMonth() = runBlocking {
        seed()
        add("before", day = start - 1)
        add("leap", day = feb.atEndOfMonth().toEpochDay())
        add("after", day = feb.plusMonths(1).atDay(1).toEpochDay())
        assertEquals(
            1L,
            repo.dao
                .categoryReport(start, feb.plusMonths(1).atDay(1).toEpochDay())
                .first()
                .single()
                .entries,
        )
        add("year-end", day = LocalDate.of(2023, 12, 31).toEpochDay())
        add("year-start", day = LocalDate.of(2024, 1, 1).toEpochDay())
        assertEquals(
            1L,
            repo.dao
                .categoryReport(
                    LocalDate.of(2023, 12, 1).toEpochDay(),
                    LocalDate.of(2024, 1, 1).toEpochDay(),
                )
                .first()
                .single()
                .entries,
        )
        assertEquals(
            2L,
            repo.dao
                .categoryReport(LocalDate.of(2024, 1, 1).toEpochDay(), start)
                .first()
                .single()
                .entries,
        )
        assertTrue(
            repo.dao
                .categoryReport(
                    LocalDate.of(2025, 1, 1).toEpochDay(),
                    LocalDate.of(2025, 2, 1).toEpochDay(),
                )
                .first()
                .isEmpty()
        )
    }

    @Test
    fun deletedPairsAppearOnceRestoreAndUndoAtomicallyAndSurviveRestart() = runBlocking {
        seed()
        val transfer = op()
        repo.transfer(transfer, "bank", "cash", 2500, start)
        repo.setDeleted(op(), transfer, 1, true)
        add("ordinary")
        repo.setDeleted(op(), "ordinary", 1, true)
        val deleted = page(deleted = true)
        assertEquals(2, deleted.size)
        assertEquals(1, deleted.count { it.record.linkId != null })
        val restoreOp = op()
        coroutineScope { repeat(8) { launch { repo.setDeleted(restoreOp, transfer, 2, false) } } }
        assertEquals(2, repo.dao.linked(transfer).count { !it.deleted && it.revision == 3L })
        assertEquals(
            97500L,
            repo.dao.accounts().first().first { it.account.id == "bank" }.balanceMinor,
        )
        repo.setDeleted(op(), transfer, 3, true)
        assertEquals(
            100000L,
            repo.dao.accounts().first().first { it.account.id == "bank" }.balanceMinor,
        )
        db.close()
        db = TraxosDatabase.open(app, file)
        repo = LedgerRepository(db)
        assertEquals(2, page(deleted = true).size)
        repo.setDeleted(op(), "ordinary", 2, false)
        assertEquals(1, page(deleted = true).size)
    }

    @Test
    fun staleOrRepeatedRestoreCannotWriteAgainAndArchivedReferencesStayRecoverable() = runBlocking {
        seed()
        add("entry")
        repo.setDeleted(op(), "entry", 1, true)
        val a = repo.dao.account("bank")!!
        repo.saveAccount(
            op(),
            "bank",
            a.name,
            a.type,
            a.currency,
            0,
            archived = true,
            expectedRevision = a.revision,
        )
        repo.setDeleted(op(), "entry", 2, false)
        val count = repo.dao.operationCount()
        try {
            repo.setDeleted(op(), "entry", 2, false)
            fail("stale restore accepted")
        } catch (_: IllegalArgumentException) {}
        assertEquals(count, repo.dao.operationCount())
        assertEquals(
            99000L,
            repo.dao.accounts().first().first { it.account.id == "bank" }.balanceMinor,
        )
    }

    @Test
    fun preferencesValidatePersistIndependentlyAndFilterScopeKeepsSessionState() = runBlocking {
        seed()
        repo.setActivitySort("OLDEST")
        repo.setTheme("DARK")
        repo.setAccent("FOREST")
        try {
            repo.setActivitySort("AMOUNT")
            fail("invalid order")
        } catch (_: IllegalArgumentException) {}
        val scope = ActivityScope("2024-02-01", "2024-02-29", "bank", expense, "INR").validate()
        val saved = SavedStateHandle()
        val models = ViewModelStore()
        val vm = MainViewModel(repo, saved)
        models.put("vm", vm)
        vm.scope(scope)
        vm.recordFilter("EXPENSES")
        vm.search("typed note")
        val restored = MainViewModel(repo, saved)
        models.put("restored", restored)
        assertEquals(scope.saved(), restored.scopeValues.value)
        assertEquals("typed note", restored.search.value)
        restored.clearActivityFilters()
        assertFalse(ActivityScope.read(restored.scopeValues.value).active)
        assertEquals("ALL", restored.recordFilter.value)
        models.clear()
        db.close()
        db = TraxosDatabase.open(app, file)
        repo = LedgerRepository(db)
        val pref = repo.dao.preferences().first()!!
        assertEquals("OLDEST", pref.activitySort)
        assertEquals("DARK", pref.theme)
        assertEquals("FOREST", pref.accent)
    }

    @Test
    fun invalidDateScopesNeverReachTheQuery() {
        assertThrows(IllegalArgumentException::class.java) {
            ActivityScope("2024-03-01", "2024-02-29").validate()
        }
        assertThrows(java.time.DateTimeException::class.java) {
            ActivityScope("2023-02-29").validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            ActivityScope(currency = "XXX").validate()
        }
        assertEquals(Long.MIN_VALUE, ActivityScope().startDay)
        assertEquals(Long.MAX_VALUE, ActivityScope().endDay)
    }

    @Test
    fun deletedCardPaymentsRestoreExactlyOnceWithoutBecomingSpending() = runBlocking {
        seed()
        repo.payCard("0f905dea-0c77-5aec-82ac-022d88be5e9d", "card", "bank", 1000, start)
        val primary =
            repo.dao.linked("0f905dea-0c77-5aec-82ac-022d88be5e9d").first {
                it.type == "PAYMENT_IN"
            }
        repo.setDeleted(op(), primary.id, 1, true)
        val bin = page(deleted = true)
        assertEquals(1, bin.size)
        assertEquals("PAYMENT_OUT", bin.single().record.type)
        repo.setDeleted(op(), bin.single().record.id, 2, false)
        assertEquals(
            -99000L,
            repo.dao.accounts().first().first { it.account.id == "card" }.balanceMinor,
        )
        assertTrue(repo.dao.categoryReport(start, start + 1).first().isEmpty())
    }

    @Test
    fun corruptPairRestoreFailsWithoutAnyPartialWrite() = runBlocking {
        seed()
        repo.transfer("fdb4934c-7d4c-5dcc-b6bc-39e78ff4ca41", "bank", "cash", 500, start)
        repo.setDeleted(op(), "fdb4934c-7d4c-5dcc-b6bc-39e78ff4ca41", 1, true)
        val incoming =
            repo.dao.linked("fdb4934c-7d4c-5dcc-b6bc-39e78ff4ca41").first {
                it.type == "TRANSFER_IN"
            }
        repo.dao.updateRecord(incoming.copy(amountMinor = 501))
        val operations = repo.dao.operationCount()
        try {
            repo.setDeleted(op(), "fdb4934c-7d4c-5dcc-b6bc-39e78ff4ca41", 2, false)
            fail("corrupt pair restored")
        } catch (_: IllegalArgumentException) {}
        assertTrue(
            repo.dao.linked("fdb4934c-7d4c-5dcc-b6bc-39e78ff4ca41").all {
                it.deleted && it.revision == 2L
            }
        )
        assertEquals(operations, repo.dao.operationCount())
    }

    @Test
    fun fiftyThousandRowHistoryStaysPagedAndFilteringRunsInSQLite() = runBlocking {
        seed()
        val sql = db.openHelper.writableDatabase
        sql.beginTransaction()
        try {
            val insert =
                sql.compileStatement(
                    "INSERT INTO transactions (id,accountId,categoryId,type,amountMinor,day,zoneId,note,revision,deleted,profileId) VALUES (?,'bank',?,'EXPENSE',1,?,'UTC','Large fixture',1,0,'local')"
                )
            try {
                for (i in 0 until 50000) {
                    insert.bindString(1, "bulk-${i.toString().padStart(5,'0')}")
                    insert.bindString(2, expense)
                    insert.bindLong(3, start + (i % 28))
                    insert.executeInsert()
                }
            } finally {
                insert.close()
            }
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
        val source = repo.dao.page("", "EXPENSES", start, start, "bank", expense, "INR")
        val first =
            source.load(PagingSource.LoadParams.Refresh(null, 40, false))
                as PagingSource.LoadResult.Page<Int, RecordRow>
        val second =
            source.load(PagingSource.LoadParams.Append(first.nextKey!!, 40, false))
                as PagingSource.LoadResult.Page<Int, RecordRow>
        assertEquals(40, first.data.size)
        assertEquals(40, second.data.size)
        assertTrue((first.data + second.data).all { it.record.day == start })
        assertTrue(
            first.data
                .map { it.record.id }
                .intersect(second.data.map { it.record.id }.toSet())
                .isEmpty()
        )
        assertEquals(50000L, repo.dao.categoryReport(start, start + 28).first().single().entries)
    }
}
