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
class RecurringTest {
    private lateinit var db: TraxosDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var recurring: RecurringRepository
    private val day = LocalDate.of(2024, 1, 31).toEpochDay()

    private fun id() = UUID.randomUUID().toString()

    @Before
    fun setup() = runBlocking {
        db = TraxosDatabase.open(ApplicationProvider.getApplicationContext(), "repeat-${id()}.db")
        ledger = LedgerRepository(db)
        recurring = RecurringRepository(db)
        ledger.saveAccount(id(), "bank", "Test bank", "BANK", "INR", 100000, openingDate = day - 1)
        ledger.saveAccount(
            id(),
            "card",
            "Test card",
            "CREDIT_CARD",
            "INR",
            0,
            openingDate = day - 1,
        )
    }

    @After
    fun cleanup() {
        val name = db.openHelper.databaseName!!
        db.close()
        ApplicationProvider.getApplicationContext<Application>().deleteDatabase(name)
    }

    private fun rule(frequency: String = "MONTHLY", enabled: Boolean = true) =
        RecurringRule(
            id(),
            "bank",
            "bills",
            "EXPENSE",
            1000,
            "Test recurring",
            frequency,
            31,
            day,
            enabled = enabled,
            operationId = id(),
        )

    @Test
    fun monthEndCatchupIsAtomicIdempotentAndRestartSafe() = runBlocking {
        val rule = rule()
        recurring.save(rule, 0, day)
        assertFalse(recurring.runDue(LocalDate.of(2024, 3, 31).toEpochDay()))
        val dates = db.ledger().recent().first().map { it.record.day }.sorted()
        assertEquals(
            listOf(
                day,
                LocalDate.of(2024, 2, 29).toEpochDay(),
                LocalDate.of(2024, 3, 31).toEpochDay(),
            ),
            dates,
        )
        assertEquals(LocalDate.of(2024, 4, 30).toEpochDay(), db.recurring().get(rule.id)!!.nextDay)
        coroutineScope {
            repeat(8) { launch { recurring.runDue(LocalDate.of(2024, 3, 31).toEpochDay()) } }
        }
        assertEquals(3L, db.ledger().recordCount())
        val name = db.openHelper.databaseName!!
        db.close()
        db = TraxosDatabase.open(ApplicationProvider.getApplicationContext(), name)
        recurring = RecurringRepository(db)
        recurring.runDue(LocalDate.of(2024, 3, 31).toEpochDay())
        assertEquals(3L, db.ledger().recordCount())
        assertEquals(
            97000L,
            db.ledger().accounts().first().first { it.account.id == "bank" }.balanceMinor,
        )
    }

    @Test
    fun boundedDailyBatchesResumeAndPauseDeleteKeepPostedHistory() = runBlocking {
        val rule = rule("DAILY")
        recurring.save(rule, 0, day)
        assertTrue(recurring.runDue(day + 4, 2))
        assertEquals(2L, db.ledger().recordCount())
        var latest = db.recurring().get(rule.id)!!
        recurring.state(rule.id, latest.revision, false)
        recurring.runDue(day + 4)
        assertEquals(2L, db.ledger().recordCount())
        latest = db.recurring().get(rule.id)!!
        recurring.state(rule.id, latest.revision, true)
        assertFalse(recurring.runDue(day + 4))
        assertEquals(5L, db.ledger().recordCount())
        latest = db.recurring().get(rule.id)!!
        recurring.state(rule.id, latest.revision, false, true)
        recurring.runDue(day + 40)
        assertEquals(5L, db.ledger().recordCount())
        assertTrue(recurring.rules.first().isEmpty())
    }

    @Test
    fun badReferencesNeverAdvanceAndCorrectionCanRetry() = runBlocking {
        val rule = rule()
        recurring.save(rule, 0, day)
        val bank = db.ledger().account("bank")!!
        ledger.saveAccount(
            id(),
            bank.id,
            bank.name,
            bank.type,
            bank.currency,
            bank.openingMinor,
            expectedRevision = bank.revision,
            archived = true,
            openingDate = bank.openingDate,
        )
        recurring.runDue(day)
        var latest = db.recurring().get(rule.id)!!
        assertEquals(day, latest.nextDay)
        assertTrue(latest.lastError.isNotBlank())
        assertEquals(0L, db.ledger().recordCount())
        val paused = db.ledger().account("bank")!!
        ledger.saveAccount(
            id(),
            paused.id,
            paused.name,
            paused.type,
            paused.currency,
            paused.openingMinor,
            expectedRevision = paused.revision,
            archived = false,
            openingDate = paused.openingDate,
        )
        recurring.save(latest.copy(operationId = id()), latest.revision, day)
        recurring.runDue(day)
        assertEquals(1L, db.ledger().recordCount())
    }

    @Test
    fun invalidRulesAndStaleEditsCannotPost() = runBlocking {
        val rule = rule()
        recurring.save(rule, 0, day)
        recurring.save(rule, 0, day)
        assertEquals(1, recurring.rules.first().size)
        for (bad in
            listOf(
                rule.copy(id = id(), amountMinor = 0, operationId = id()),
                rule.copy(
                    id = id(),
                    accountId = "card",
                    type = "INCOME",
                    categoryId = "salary",
                    operationId = id(),
                ),
                rule.copy(id = id(), nextDay = day - 1, operationId = id()),
                rule.copy(id = id(), categoryId = "salary", operationId = id()),
            )) {
            try {
                recurring.save(bad, 0, day)
                fail("invalid schedule accepted")
            } catch (_: IllegalArgumentException) {}
        }
        try {
            recurring.save(rule.copy(note = "Changed", operationId = id()), 0, day)
            fail("stale revision accepted")
        } catch (_: IllegalArgumentException) {}
        assertEquals(0L, db.ledger().recordCount())
    }

    @Test
    fun cardPurchasesAndIncomeUseExistingFinancialSigns() = runBlocking {
        val card = rule().copy(accountId = "card")
        recurring.save(card, 0, day)
        val income = rule().copy(type = "INCOME", categoryId = "salary", amountMinor = 5000)
        recurring.save(income, 0, day)
        recurring.runDue(day)
        val rows = db.ledger().accounts().first()
        assertEquals(-1000L, rows.first { it.account.id == "card" }.balanceMinor)
        assertEquals(105000L, rows.first { it.account.id == "bank" }.balanceMinor)
    }

    @Test
    fun anchorsLeapDaysWeeklyAndPausedCreationRemainPredictable() = runBlocking {
        assertEquals(
            LocalDate.of(2025, 2, 28).toEpochDay(),
            RecurringDates.next(LocalDate.of(2025, 1, 31).toEpochDay(), "MONTHLY", 31),
        )
        assertEquals(day + 7, RecurringDates.next(day, "WEEKLY", 31))
        val paused = rule(enabled = false)
        recurring.save(paused, 0, day)
        recurring.runDue(day)
        assertEquals(0L, db.ledger().recordCount())
    }
}
