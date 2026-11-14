package app.traxos.finance

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.backup.*
import app.traxos.finance.data.*
import app.traxos.finance.domain.Money
import app.traxos.finance.gifts.*
import java.io.*
import java.time.*
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
class DebtPlanningExtensionsTest {
    private val context
        get() = ApplicationProvider.getApplicationContext<Application>()

    private lateinit var db: TraxosDatabase
    private lateinit var repo: PlanningExtensionsRepository
    private lateinit var ledger: LedgerRepository

    private fun id() = UUID.randomUUID().toString()

    private val today = LocalDate.now().toEpochDay()

    @Before
    fun setup() = runBlocking {
        db = TraxosDatabase.open(context, "planning-${id()}.db")
        repo = PlanningExtensionsRepository(db)
        ledger = LedgerRepository(db)
        ledger.saveAccount(id(), "bank", "Bank", "BANK", "INR", 500000, openingDate = today - 100)
        ledger.saveAccount(id(), "savings", "Savings", "BANK", "INR", 0, openingDate = today - 100)
        ledger.saveAccount(id(), "card", "Card", "CREDIT_CARD", "INR", 0, openingDate = today - 100)
    }

    @After
    fun cleanup() {
        val name = db.openHelper.databaseName!!
        db.close()
        context.deleteDatabase(name)
    }

    private fun invalid(block: suspend () -> Unit) {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { block() } }
    }

    private fun debt(direction: String = "LENT") =
        DebtThread(
            id(),
            "Person",
            direction,
            "INR",
            100000,
            100000,
            today,
            today + 7,
            "Private reason",
            operationId = id(),
        )

    private suspend fun balance(account: String) =
        db.ledger().accounts().first().first { it.account.id == account }.balanceMinor

    private suspend fun assertNoSpending() {
        assertEquals(0L, db.ledger().summary(today, today + 1).first().sumOf { it.expenseMinor })
        assertEquals(0L, db.ledger().summary(today, today + 1).first().sumOf { it.incomeMinor })
    }

    private suspend fun goal(name: String = "Goal"): SavingsGoal {
        val id = id()
        repo.goal(id, name, "INR", 300000, 0, id(), today + 30, true, "7,1,0")
        return db.plans().goal(id)!!
    }

    @Test
    fun lendingAndRepaymentMoveCashWithoutExpenseOrIncomeAndUndoIsAtomic() = runBlocking {
        val d = debt()
        repo.saveDebt(d, 0, "bank", null, null, true, "7,1,0")
        repo.saveDebt(d, 0, "bank", null, null, true, "7,1,0")
        assertEquals(400000L, balance("bank"))
        assertEquals("DEBT_OUT", db.ledger().recent().first().single().record.type)
        assertNoSpending()
        val op = id()
        coroutineScope {
            repeat(10) { launch { repo.repay(d.id, 1, 30000, today, "Paid", "bank", null, op) } }
        }
        assertEquals(430000L, balance("bank"))
        assertEquals(70000L, db.debts().thread(d.id)!!.outstandingMinor)
        assertEquals(2L, db.ledger().recordCount())
        assertNoSpending()
        val undo = id()
        repo.undoDebt(d.id, 2, undo)
        repo.undoDebt(d.id, 2, undo)
        assertEquals(400000L, balance("bank"))
        assertEquals(100000L, db.debts().thread(d.id)!!.outstandingMinor)
        assertFalse(repo.dao.debtFunding(op)!!.active)
    }

    @Test
    fun borrowingAndItsRepaymentAreLiabilityPrincipalNotIncomeOrSpending() = runBlocking {
        val d = debt("BORROWED")
        repo.saveDebt(d, 0, "bank", null, null, false, "7,1,0")
        assertEquals(600000L, balance("bank"))
        repo.repay(d.id, 1, 20000, today, "Repayment", "bank", null, id())
        assertEquals(580000L, balance("bank"))
        assertEquals(80000L, db.debts().thread(d.id)!!.outstandingMinor)
        assertNoSpending()
    }

    @Test
    fun matchingExistingMovementsNeverDuplicatesCashAndUnlinkRestoresOriginalTreatment() =
        runBlocking {
            val out = id()
            ledger.saveRecord(id(), out, "bank", "food", "EXPENSE", 100000, today, "Loan")
            val d = debt()
            repo.saveDebt(d, 0, "bank", out, null, false, "7,1,0")
            assertEquals(400000L, balance("bank"))
            assertEquals(1L, db.ledger().recordCount())
            assertNoSpending()
            invalid { ledger.setDeleted(id(), out, 1, true) }
            val incoming = id()
            ledger.saveRecord(id(), incoming, "bank", "salary", "INCOME", 30000, today, "Repayment")
            val op = id()
            repo.repay(d.id, 1, 30000, today, "Match", "bank", incoming, op)
            assertEquals(430000L, balance("bank"))
            assertEquals(2L, db.ledger().recordCount())
            assertNoSpending()
            repo.undoDebt(d.id, 2, id())
            assertFalse(db.ledger().record(incoming)!!.deleted)
            assertEquals(30000L, db.ledger().summary(today, today + 1).first().single().incomeMinor)
            repo.unlinkOpening(d.id)
            assertFalse(db.ledger().record(out)!!.deleted)
            assertEquals(
                100000L,
                db.ledger().summary(today, today + 1).first().single().expenseMinor,
            )
        }

    @Test
    fun invalidFundingAndMissingSelectionsRollbackTheDebtCreation() = runBlocking {
        val a = debt()
        invalid { repo.saveDebt(a, 0, "", null, null, false, "7,1,0") }
        assertNull(db.debts().thread(a.id))
        val b = debt()
        invalid { repo.saveDebt(b, 0, "bank", "", null, false, "7,1,0") }
        assertNull(db.debts().thread(b.id))
        val c = debt()
        invalid { repo.saveDebt(c, 0, "card", null, null, false, "7,1,0") }
        assertNull(db.debts().thread(c.id))
        assertEquals(500000L, balance("bank"))
    }

    @Test
    fun repaymentUndoRejectsSpentIncomingFundsWithoutPartialChanges() = runBlocking {
        val d = debt()
        repo.saveDebt(d, 0, null, null, null, false, "7,1,0")
        val op = id()
        repo.repay(d.id, 1, 30000, today, "Paid", "savings", null, op)
        ledger.saveRecord(id(), id(), "savings", "food", "EXPENSE", 25000, today, "Spent")
        invalid { repo.undoDebt(d.id, 2, id()) }
        assertEquals(70000L, db.debts().thread(d.id)!!.outstandingMinor)
        assertTrue(repo.dao.debtFunding(op)!!.active)
        assertEquals(5000L, balance("savings"))
    }

    @Test
    fun generatedDebtRowsCannotBeRestoredIndependentlyAfterUnlink() = runBlocking {
        val d = debt()
        repo.saveDebt(d, 0, "bank", null, null, false, "7,1,0")
        val link = repo.dao.openingFunding(d.id)!!
        repo.unlinkOpening(d.id)
        val record = db.ledger().record(link.recordId)!!
        assertTrue(record.deleted)
        invalid { ledger.setDeleted(id(), record.id, record.revision, false) }
        assertEquals(500000L, balance("bank"))
    }

    @Test
    fun personNormalizationDuplicatesAndDebtPreferencesPersist() = runBlocking {
        val person = id()
        repo.person(person, "A Person", "+91 (999) 888-7777", 0, id())
        assertEquals("+919998887777", repo.dao.person(person)!!.phone)
        invalid { repo.person(id(), "Another alias", "+91 9998887777", 0, id()) }
        invalid { repo.person(id(), "Link", "https://example.com", 0, id()) }
        val d = debt()
        repo.saveDebt(d, 0, null, null, person, true, "7,1,0")
        assertEquals(person, repo.dao.debtPreference(d.id)!!.personId)
        assertTrue(repo.dao.debtPreference(d.id)!!.reminders)
    }

    @Test
    fun earmarksDoNotMoveMoneyAndCannotReserveTheSameAccountFundsTwice() = runBlocking {
        val a = goal()
        val b = goal("Other goal")
        repo.contribution(a.id, 1, 300000, "bank", null, today, id())
        assertEquals(500000L, balance("bank"))
        assertEquals(300000L, db.plans().goal(a.id)!!.savedMinor)
        assertEquals(0L, db.ledger().recordCount())
        invalid { repo.contribution(b.id, 1, 250000, "bank", null, today, id()) }
        assertEquals(0L, db.plans().goal(b.id)!!.savedMinor)
        invalid { PlansRepository(db).contribute(a.id, 2, -100000, id()) }
        repo.contribution(a.id, 2, -100000, "bank", null, today, id())
        assertEquals(200000L, db.plans().goal(a.id)!!.savedMinor)
        assertEquals(500000L, balance("bank"))
        assertNoSpending()
    }

    @Test
    fun savingsTransferIsPairedAndProgressDoesNotInflateMoney() = runBlocking {
        val g = goal()
        val op = id()
        repo.contribution(g.id, 1, 200000, "savings", "bank", today, op)
        repo.contribution(g.id, 1, 200000, "savings", "bank", today, op)
        assertEquals(300000L, balance("bank"))
        assertEquals(200000L, balance("savings"))
        assertEquals(500000L, balance("bank") + balance("savings"))
        assertEquals(200000L, db.plans().goal(g.id)!!.savedMinor)
        assertEquals(2L, db.ledger().recordCount())
        assertNoSpending()
        val transfer = repo.dao.goalFunding(op)!!.transferId!!
        invalid { ledger.setDeleted(id(), transfer, 1, true) }
        val undo = id()
        repo.undoGoal(g.id, 2, undo)
        repo.undoGoal(g.id, 2, undo)
        assertEquals(500000L, balance("bank"))
        assertEquals(0L, balance("savings"))
        assertEquals(0L, db.plans().goal(g.id)!!.savedMinor)
        assertEquals(0L, repo.dao.reservations().first().sumOf { it.amountMinor })
    }

    @Test
    fun spendingTransferredSavingsBlocksFalseUndoButKeepsProgressForReview() = runBlocking {
        val g = goal()
        val op = id()
        repo.contribution(g.id, 1, 200000, "savings", "bank", today, op)
        ledger.saveRecord(id(), id(), "savings", "food", "EXPENSE", 180000, today, "Spent")
        invalid { repo.undoGoal(g.id, 2, id()) }
        assertEquals(200000L, db.plans().goal(g.id)!!.savedMinor)
        assertTrue(repo.dao.goalFunding(op)!!.transferActive)
        assertEquals(20000L, balance("savings"))
    }

    @Test
    fun accountWithdrawalAndItsUndoConserveReservationsAndTransferBalances() = runBlocking {
        val g = goal()
        repo.contribution(g.id, 1, 100000, "bank", null, today, id())
        repo.contribution(g.id, 2, -50000, "bank", "savings", today, id())
        assertEquals(450000L, balance("bank"))
        assertEquals(50000L, balance("savings"))
        assertEquals(50000L, db.plans().goal(g.id)!!.savedMinor)
        repo.undoGoal(g.id, 3, id())
        assertEquals(500000L, balance("bank"))
        assertEquals(0L, balance("savings"))
        assertEquals(100000L, db.plans().goal(g.id)!!.savedMinor)
        assertEquals(100000L, repo.dao.reservations().first().sumOf { it.amountMinor })
        assertNoSpending()
    }

    @Test
    fun undoingAnUnreserveCannotReallocateMoneySpentElsewhere() = runBlocking {
        val g = goal()
        repo.contribution(g.id, 1, 400000, "bank", null, today, id())
        repo.contribution(g.id, 2, -300000, "bank", null, today, id())
        ledger.saveRecord(id(), id(), "bank", "food", "EXPENSE", 300000, today, "Spent")
        invalid { repo.undoGoal(g.id, 3, id()) }
        assertEquals(100000L, db.plans().goal(g.id)!!.savedMinor)
    }

    @Test
    fun salaryCyclesClampMonthEndAndReturnToChosenDay() {
        val row =
            PeriodBudget(
                id(),
                "Salary",
                "SALARY",
                "INR",
                null,
                LocalDate.of(2024, 1, 31).toEpochDay(),
                null,
                31,
                10000,
                scopeKey = "",
                operationId = id(),
            )
        assertEquals(
            BudgetPeriods.Window(
                LocalDate.of(2024, 1, 31).toEpochDay(),
                LocalDate.of(2024, 2, 28).toEpochDay(),
            ),
            BudgetPeriods.window(row, LocalDate.of(2024, 2, 15).toEpochDay()),
        )
        assertEquals(
            BudgetPeriods.Window(
                LocalDate.of(2024, 2, 29).toEpochDay(),
                LocalDate.of(2024, 3, 30).toEpochDay(),
            ),
            BudgetPeriods.window(row, LocalDate.of(2024, 2, 29).toEpochDay()),
        )
        assertEquals(
            LocalDate.of(2024, 3, 31).toEpochDay(),
            BudgetPeriods.window(row, LocalDate.of(2024, 4, 15).toEpochDay()).start,
        )
    }

    @Test
    fun customBudgetsValidateRangesAndThresholdsWithoutWritingTransactions() = runBlocking {
        val b =
            PeriodBudget(
                id(),
                "Trip",
                "CUSTOM",
                "INR",
                null,
                today,
                today + 10,
                0,
                10000,
                scopeKey = "",
                operationId = id(),
            )
        repo.period(b, 0)
        repo.period(b, 0)
        assertEquals(BudgetPeriods.Window(today, today + 10), BudgetPeriods.window(b, today + 100))
        assertEquals(0L, db.ledger().recordCount())
        invalid { repo.period(b.copy(id = id(), operationId = id()), 0) }
        invalid { repo.period(b.copy(endDay = today - 1, operationId = id()), 1) }
        invalid { repo.period(b.copy(thresholds = "80,80", operationId = id()), 1) }
        assertTrue(BudgetPeriods.reached(Long.MAX_VALUE, Money.MAX_MINOR, 100))
        assertFalse(BudgetPeriods.reached(-10000, 10000, 80))
        assertTrue(BudgetPeriods.reached(8000, 10000, 80))
        assertFalse(BudgetPeriods.reached(7999, 10000, 80))
    }

    @Test
    fun newFundingCannotHijackAGroupOrSubscriptionRecord() = runBlocking {
        val tx = id()
        ledger.saveRecord(id(), tx, "bank", "food", "EXPENSE", 100000, today, "Loan")
        val d = debt()
        repo.saveDebt(d, 0, "bank", tx, null, false, "7,1,0")
        val g = id()
        GroupsRepository(db).create(g, "Group", "INR", "Me", id())
        val group = db.groups().group(g)!!
        invalid {
            GroupsRepository(db)
                .expense(
                    g,
                    group.selfId,
                    100000,
                    "EQUAL",
                    mapOf(group.selfId to "1"),
                    today,
                    "Wrong",
                    tx,
                    id(),
                )
        }
        val p =
            PaymentPlan(
                id(),
                "BILL",
                "Wrong",
                "bank",
                "food",
                "INR",
                100000,
                "MONTHLY",
                30,
                1,
                today,
                null,
                null,
                operationId = id(),
            )
        ObligationsRepository(db).plan(p, 0)
        invalid { ObligationsRepository(db).confirm(p.id, 1, 100000, today, tx, 0, id()) }
    }

    @Test
    fun encryptedRecoveryRestoresContactsFundingPeriodsAndGoalDeadlines() = runBlocking {
        val person = id()
        repo.person(person, "Private person", "+919999999999", 0, id())
        val d = debt()
        repo.saveDebt(d, 0, "bank", null, person, true, "7,1,0")
        repo.repay(d.id, 1, 10000, today, "Paid", "bank", null, id())
        val g = goal()
        repo.contribution(g.id, 1, 100000, "savings", "bank", today, id())
        repo.period(
            PeriodBudget(
                id(),
                "Custom",
                "CUSTOM",
                "INR",
                null,
                today,
                today + 15,
                0,
                10000,
                scopeKey = "",
                operationId = id(),
            ),
            0,
        )
        val bytes =
            ByteArrayOutputStream()
                .also {
                    PortableBackup(context, db, TestGiftCipher())
                        .write(it, "password-1234".toCharArray())
                }
                .toByteArray()
        assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("Private person"))
        val target = TraxosDatabase.open(context, "restore14-${id()}.db")
        try {
            val backup = PortableBackup(context, target, TestGiftCipher())
            backup.inspect(ByteArrayInputStream(bytes), "password-1234".toCharArray()).use {
                backup.restore(it)
            }
            assertEquals(1, target.planning().people().first().size)
            assertEquals(90000L, target.debts().thread(d.id)!!.outstandingMinor)
            assertEquals(100000L, target.plans().goal(g.id)!!.savedMinor)
            assertEquals(today + 30, target.planning().goalPreference(g.id)!!.deadlineDay)
            assertEquals(1, target.planning().budgets().first().size)
            backup.inspect(ByteArrayInputStream(bytes), "password-1234".toCharArray()).use {
                assertEquals(0L, backup.restore(it))
            }
        } finally {
            val name = target.openHelper.databaseName!!
            target.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration13PreservesExistingBalancesAndAddsEmptyExtensions() = runBlocking {
        val name = "upgrade13-${id()}.db"
        val schema =
            JSONObject(
                    javaClass.classLoader!!
                        .getResourceAsStream("app.traxos.finance.data.TraxosDatabase/13.json")!!
                        .bufferedReader()
                        .use { it.readText() }
                )
                .getJSONObject("database")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                old.execSQL(
                    e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName"))
                )
                val indices = e.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) old.execSQL(
                    indices
                        .getJSONObject(j)
                        .getString("createSql")
                        .replace("\${TABLE_NAME}", e.getString("tableName"))
                )
            }
            val queries = schema.getJSONArray("setupQueries")
            for (i in 0 until queries.length()) old.execSQL(queries.getString(i))
            old.execSQL(
                "INSERT INTO accounts (id,name,type,currency,openingMinor,openingDate,archived,revision,profileId) VALUES ('keep','Keep','BANK','INR',12345,20000,0,1,'local')"
            )
            old.version = 13
        }
        val upgraded = TraxosDatabase.open(context, name)
        try {
            assertEquals(12345L, upgraded.ledger().accounts().first().single().balanceMinor)
            assertTrue(upgraded.planning().people().first().isEmpty())
            assertTrue(upgraded.planning().budgets().first().isEmpty())
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun periodReportsIncludeOwnGroupSharesAndRefundsButExcludeLoansAndOtherCurrencies() =
        runBlocking {
            ledger.saveRecord(id(), id(), "bank", "food", "EXPENSE", 8000, today, "Start")
            ledger.saveRecord(id(), id(), "bank", "food", "EXPENSE", 2000, today + 2, "End")
            ledger.saveRecord(id(), id(), "bank", "food", "EXPENSE", 9000, today + 3, "Outside")
            ledger.saveRecord(id(), id(), "card", "food", "REFUND", 1000, today, "Refund")
            ledger.saveRecord(id(), id(), "bank", "salary", "INCOME", 3000, today, "Income")
            val loan = debt().copy(originalMinor = 50000, outstandingMinor = 50000)
            repo.saveDebt(loan, 0, "bank", null, null, false, "7,1,0")
            ledger.saveAccount(id(), "usd", "USD", "BANK", "USD", 100000, openingDate = today - 100)
            ledger.saveRecord(id(), id(), "usd", "food", "EXPENSE", 50000, today, "Other currency")
            val group = id()
            GroupsRepository(db).create(group, "Group", "INR", "Me", id())
            val g = db.groups().group(group)!!
            val friend = id()
            GroupsRepository(db).member(GroupMember(friend, group, "Friend", operationId = id()), 0)
            GroupsRepository(db)
                .expense(
                    group,
                    friend,
                    6000,
                    "EQUAL",
                    mapOf(g.selfId to "1", friend to "1"),
                    today,
                    "Dinner",
                    null,
                    id(),
                    "food",
                )
            val budget =
                PeriodBudget(
                    id(),
                    "Range",
                    "CUSTOM",
                    "INR",
                    "food",
                    today,
                    today + 2,
                    0,
                    10000,
                    scopeKey = "",
                    operationId = id(),
                )
            repo.period(budget, 0)
            val range = BudgetPeriods.window(budget, today)
            val spent =
                db.ledger()
                    .categoryReport(range.start, range.end + 1)
                    .first()
                    .filter { it.currency == "INR" && it.categoryId == "food" }
                    .sumOf { it.expenseMinor - it.refundMinor }
            assertEquals(12000L, spent)
        }

    @Test
    fun archivedGoalsKeepEarmarksUntilExplicitWithdrawalAndDeadlinesRemainEditable() = runBlocking {
        val a = goal()
        repo.contribution(a.id, 1, 100000, "bank", null, today, id())
        PlansRepository(db).archive(a.id, 2, true)
        val b = goal("Another")
        invalid { repo.contribution(b.id, 1, 450000, "bank", null, today, id()) }
        assertEquals(100000L, repo.dao.reservations().first().sumOf { it.amountMinor })
        PlansRepository(db).archive(a.id, 3, false)
        repo.goal(a.id, "Renamed", "INR", 400000, 4, id(), today + 60, false, "1,0")
        assertEquals(today + 60, repo.dao.goalPreference(a.id)!!.deadlineDay)
        assertEquals(100000L, db.plans().goal(a.id)!!.savedMinor)
    }
}
