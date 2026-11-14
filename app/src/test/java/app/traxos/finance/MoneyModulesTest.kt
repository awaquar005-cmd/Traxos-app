package app.traxos.finance

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.backup.*
import app.traxos.finance.data.*
import app.traxos.finance.gifts.*
import app.traxos.finance.obligations.FinanceReminderPlan
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
class MoneyModulesTest {
    private val context
        get() = ApplicationProvider.getApplicationContext<Application>()

    private lateinit var db: TraxosDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var groups: GroupsRepository
    private lateinit var plans: ObligationsRepository

    private fun id() = UUID.randomUUID().toString()

    private val today = LocalDate.now().toEpochDay()

    @Before
    fun setup() = runBlocking {
        db = TraxosDatabase.open(context, "money-${id()}.db")
        ledger = LedgerRepository(db)
        groups = GroupsRepository(db)
        plans = ObligationsRepository(db)
        ledger.saveAccount(id(), "bank", "Bank", "BANK", "INR", 5000000, openingDate = today - 2000)
        ledger.saveAccount(
            id(),
            "card",
            "Card",
            "CREDIT_CARD",
            "INR",
            0,
            openingDate = today - 2000,
        )
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

    private suspend fun expense(
        amount: Long,
        account: String = "card",
        day: Long = today,
        note: String = "Purchase",
    ): String {
        val record = id()
        ledger.saveRecord(id(), record, account, "food", "EXPENSE", amount, day, note)
        return record
    }

    private suspend fun group(): ExpenseGroup {
        val group = id()
        groups.create(group, "Trip", "INR", "Me", id())
        val g = db.groups().group(group)!!
        repeat(3) { groups.member(GroupMember(id(), group, "Person $it", operationId = id()), 0) }
        return g
    }

    private fun paymentPlan(
        name: String = "Plan",
        kind: String = "SUBSCRIPTION",
        account: String = "card",
        next: Long = today,
        purchase: String? = null,
        principal: Long = 0,
        installments: Int = 0,
    ) =
        PaymentPlan(
            id(),
            kind,
            name,
            account,
            "food",
            "INR",
            if (kind == "EMI") principal / installments else 10000,
            "MONTHLY",
            30,
            LocalDate.ofEpochDay(next).dayOfMonth,
            next,
            null,
            null,
            purchaseId = purchase,
            principalMinor = principal,
            installments = installments,
            operationId = id(),
        )

    @Test
    fun allSplitModesConserveMoneyAndStableRemainders() {
        val names = mapOf("c" to "1", "a" to "1", "b" to "1")
        assertEquals(
            mapOf("a" to 34L, "b" to 34L, "c" to 33L),
            GroupMath.split(101, "EQUAL", names, "INR"),
        )
        assertEquals(
            mapOf("a" to 2500L, "b" to 7500L),
            GroupMath.split(10000, "PERCENT", mapOf("a" to "25", "b" to "75"), "INR"),
        )
        assertEquals(
            mapOf("a" to 2500L, "b" to 7500L),
            GroupMath.split(10000, "SHARES", mapOf("a" to "1", "b" to "3"), "INR"),
        )
        assertEquals(
            mapOf("a" to 100L, "b" to 900L),
            GroupMath.split(1000, "EXACT", mapOf("a" to "1", "b" to "9"), "INR"),
        )
        for (total in 1L..1000) assertEquals(
            total,
            GroupMath.split(total, "SHARES", names, "JPY").values.sum(),
        )
    }

    @Test
    fun invalidSplitsAreRejected() {
        invalid { GroupMath.split(1000, "PERCENT", mapOf("a" to "33.333", "b" to "66.667"), "INR") }
        invalid { GroupMath.split(1000, "PERCENT", mapOf("a" to "50", "b" to "40"), "INR") }
        invalid { GroupMath.split(1000, "EXACT", mapOf("a" to "1"), "INR") }
        invalid { GroupMath.split(1000, "SHARES", mapOf("a" to "0"), "INR") }
    }

    @Test
    fun linkedGroupExpenseAndReimbursementsAffectCashOnceAndReportOnlyOwnShare() = runBlocking {
        val g = group()
        val members = db.groups().members(g.id).first()
        val amounts = members.associate { it.id to "1" }
        val source = expense(120000, "bank")
        val op = id()
        groups.expense(g.id, g.selfId, 120000, "EQUAL", amounts, today, "Dinner", source, op)
        groups.expense(g.id, g.selfId, 120000, "EQUAL", amounts, today, "Dinner", source, op)
        assertEquals(
            4880000L,
            db.ledger().accounts().first().first { it.account.id == "bank" }.balanceMinor,
        )
        assertEquals(30000L, db.ledger().summary(today, today + 1).first().single().expenseMinor)
        invalid {
            ledger.saveRecord(id(), source, "bank", "food", "EXPENSE", 120000, today, "Changed", 1)
        }
        invalid { ledger.setDeleted(id(), source, 1, true) }
        for (m in members.filter { it.id != g.selfId }) {
            val record = id()
            ledger.saveRecord(
                id(),
                record,
                "bank",
                "salary",
                "INCOME",
                30000,
                today,
                "Reimbursement",
            )
            groups.settle(g.id, m.id, g.selfId, 30000, today, "Paid", record, id())
        }
        val summary = db.ledger().summary(today, today + 1).first().single()
        assertEquals(0L, summary.incomeMinor)
        assertEquals(30000L, summary.expenseMinor)
        assertEquals(
            4970000L,
            db.ledger().accounts().first().first { it.account.id == "bank" }.balanceMinor,
        )
        assertTrue(
            GroupMath.balances(
                    members,
                    db.groups().events(g.id).first(),
                    db.groups().shares(g.id).first(),
                )
                .values
                .all { it == 0L }
        )
    }

    @Test
    fun partialSettlementLimitsReversalAndArchivedMembersPreserveHistory() = runBlocking {
        val g = group()
        val members = db.groups().members(g.id).first()
        val debtor = members.first { it.id != g.selfId }
        val op = id()
        groups.expense(
            g.id,
            g.selfId,
            10000,
            "EQUAL",
            mapOf(g.selfId to "1", debtor.id to "1"),
            today,
            "Lunch",
            null,
            op,
        )
        val settle = id()
        groups.settle(g.id, debtor.id, g.selfId, 2000, today, "Part", null, settle)
        groups.settle(g.id, debtor.id, g.selfId, 2000, today, "Part", null, settle)
        invalid { groups.settle(g.id, debtor.id, g.selfId, 4000, today, "Too much", null, id()) }
        invalid { groups.archive(g.id, 1, true) }
        val reverse = id()
        groups.reverse(settle, reverse)
        groups.reverse(settle, reverse)
        assertEquals(
            -5000L,
            GroupMath.balances(
                    members,
                    db.groups().events(g.id).first(),
                    db.groups().shares(g.id).first(),
                )[debtor.id],
        )
        groups.reverse(op, id())
        groups.archive(g.id, 1, true)
        assertTrue(db.groups().group(g.id)!!.archived)
    }

    @Test
    fun groupCurrencyAndForeignMembersAndOperationCollisionsAreRejected() = runBlocking {
        val a = group()
        val b = group()
        val foreign = db.groups().members(b.id).first().first()
        val source = expense(1000, "bank")
        invalid {
            groups.expense(
                a.id,
                a.selfId,
                1000,
                "EQUAL",
                mapOf(foreign.id to "1"),
                today,
                "Wrong",
                null,
                id(),
            )
        }
        val op = id()
        groups.expense(
            a.id,
            a.selfId,
            1000,
            "EQUAL",
            mapOf(a.selfId to "1"),
            today,
            "One",
            source,
            op,
        )
        invalid {
            groups.expense(
                a.id,
                a.selfId,
                2000,
                "EQUAL",
                mapOf(a.selfId to "1"),
                today,
                "Other",
                null,
                op,
            )
        }
        invalid {
            groups.expense(
                a.id,
                a.selfId,
                1000,
                "EQUAL",
                mapOf(a.selfId to "1"),
                today,
                "Duplicate source",
                source,
                id(),
            )
        }
    }

    @Test
    fun monthEndQuarterlyYearlyAndCustomDatesAreDeterministic() {
        val jan = LocalDate.of(2024, 1, 31).toEpochDay()
        val feb = PlanDates.next(jan, "MONTHLY", 31)
        assertEquals(LocalDate.of(2024, 2, 29).toEpochDay(), feb)
        assertEquals(LocalDate.of(2024, 3, 31).toEpochDay(), PlanDates.next(feb, "MONTHLY", 31))
        assertEquals(LocalDate.of(2024, 4, 30).toEpochDay(), PlanDates.next(jan, "QUARTERLY", 31))
        var leap = LocalDate.of(2024, 2, 29).toEpochDay()
        repeat(4) { leap = PlanDates.next(leap, "YEARLY", 29) }
        assertEquals(LocalDate.of(2028, 2, 29).toEpochDay(), leap)
        assertEquals(jan + 45, PlanDates.next(jan, "CUSTOM", 31, 45))
    }

    @Test
    fun statementPaymentsArePartialIdempotentAndNotExpenses() = runBlocking {
        expense(100000, day = today - 5)
        val s =
            CardStatement(
                id(),
                "card",
                today - 2,
                today + 18,
                100000,
                10000,
                false,
                operationId = id(),
            )
        plans.statement(s)
        val op = id()
        plans.pay(s.id, "bank", 30000, today, op)
        plans.pay(s.id, "bank", 30000, today, op)
        assertEquals(70000L, PlanDates.remaining(s, plans.dao.paid(s.id)))
        assertEquals(0L, PlanDates.minimumRemaining(s, plans.dao.paid(s.id)))
        assertEquals(
            -70000L,
            db.ledger().accounts().first().first { it.account.id == "card" }.balanceMinor,
        )
        assertEquals(
            100000L,
            db.ledger().summary(today - 10, today + 1).first().single().expenseMinor,
        )
        invalid { plans.pay(s.id, "bank", 80000, today, id()) }
        invalid { plans.voidStatement(s.id, 1) }
    }

    @Test
    fun matchingCardPaymentCannotBeAllocatedTwiceAndDeletedPaymentReopensDue() = runBlocking {
        expense(100000, day = today - 5)
        val s =
            CardStatement(
                id(),
                "card",
                today - 2,
                today + 18,
                100000,
                5000,
                true,
                operationId = id(),
            )
        plans.statement(s)
        val payment = id()
        ledger.payCard(payment, "card", "bank", 20000, today)
        plans.allocate(s.id, payment, 20000, id())
        invalid { plans.allocate(s.id, payment, 1, id()) }
        ledger.setDeleted(id(), payment, 1, true)
        assertEquals(0L, plans.dao.paid(s.id))
        assertEquals(100000L, PlanDates.remaining(s, plans.dao.paid(s.id)))
    }

    @Test
    fun cycleSettingsAndHistoricalEstimateNeverInventStatementOrLimitMoney() = runBlocking {
        expense(10000, day = today - 2)
        val cycle = CardCycle("card", 31, 20, 100000, 1000, "bank", operationId = id())
        plans.cycle(cycle, 0)
        plans.cycle(cycle, 0)
        assertEquals(10000L, plans.estimate("card", today))
        assertEquals(0L, plans.estimate("card", today - 3))
        assertTrue(plans.dao.statements().first().isEmpty())
        invalid { plans.cycle(cycle.copy(closingDay = 32, operationId = id()), 1) }
    }

    @Test
    fun fixedAndVariablePlansOnlyPostAfterConfirmationAndRetryIsIdempotent() = runBlocking {
        val p = paymentPlan()
        plans.plan(p, 0)
        assertEquals(0L, db.ledger().recordCount())
        val op = id()
        coroutineScope {
            repeat(10) { launch { plans.confirm(p.id, 1, 10000, today, null, 0, op) } }
        }
        assertEquals(1L, db.ledger().recordCount())
        assertEquals(1, plans.dao.payments().first().size)
        assertEquals(
            -10000L,
            db.ledger().accounts().first().first { it.account.id == "card" }.balanceMinor,
        )
        val variable = paymentPlan("Variable").copy(amountMinor = null)
        plans.plan(variable, 0)
        plans.confirm(variable.id, 1, 12345, today, null, 0, id())
        assertEquals(22345L, db.ledger().summary(today, today + 1).first().single().expenseMinor)
    }

    @Test
    fun matchingSubscriptionExpenseNeverAddsAnotherExpenseAndUndoPreservesMatchedRecord() =
        runBlocking {
            val existing = expense(10000)
            val p = paymentPlan()
            plans.plan(p, 0)
            val op = id()
            plans.confirm(p.id, 1, 10000, today, existing, 0, op)
            assertEquals(1L, db.ledger().recordCount())
            plans.undoPayment(op, 2)
            assertFalse(db.ledger().record(existing)!!.deleted)
            assertEquals(today, plans.dao.plan(p.id)!!.nextDay)
            assertTrue(plans.dao.payment(op)!!.voided)
            plans.confirm(p.id, 3, 10000, today, existing, 0, id())
            assertEquals(1L, db.ledger().recordCount())
        }

    @Test
    fun undoNewPaymentTombstonesExpenseAndAllowsCorrectedOccurrence() = runBlocking {
        val p = paymentPlan()
        plans.plan(p, 0)
        val op = id()
        plans.confirm(p.id, 1, 10000, today, null, 0, op)
        val record = plans.dao.payment(op)!!.recordId!!
        plans.undoPayment(op, 2)
        plans.undoPayment(op, 2)
        assertTrue(db.ledger().record(record)!!.deleted)
        plans.confirm(p.id, 3, 9000, today, null, 0, id())
        assertEquals(9000L, db.ledger().summary(today, today + 1).first().single().expenseMinor)
    }

    @Test
    fun pauseCancelFuturePaymentsAndDuplicatePlansAreHandled() = runBlocking {
        val p = paymentPlan()
        plans.plan(p, 0)
        invalid { plans.plan(paymentPlan(), 0) }
        plans.status(p.id, 1, "PAUSED")
        invalid { plans.confirm(p.id, 2, 10000, today, null, 0, id()) }
        plans.status(p.id, 2, "ACTIVE")
        invalid { plans.confirm(p.id, 3, 10000, today + 1, null, 0, id()) }
        plans.status(p.id, 3, "CANCELLED")
        assertEquals(0L, db.ledger().recordCount())
    }

    @Test
    fun emiPrincipalIsNeverBookedTwiceAndFeesAreSeparate() = runBlocking {
        val purchase = expense(10001)
        val p = paymentPlan("EMI", "EMI", purchase = purchase, principal = 10001, installments = 3)
        plans.plan(p, 0)
        assertEquals(listOf(3333L, 3333L, 3335L), (0..2).map { PlanDates.installment(p, it) })
        plans.confirm(p.id, 1, 3333, today, null, 100, id())
        plans.confirm(p.id, 2, 3333, today, null, 0, id())
        plans.confirm(p.id, 3, 3335, today, null, 0, id())
        assertEquals("ENDED", plans.dao.plan(p.id)!!.status)
        assertEquals(10101L, db.ledger().summary(today, today + 1).first().single().expenseMinor)
        assertEquals(
            -10101L,
            db.ledger().accounts().first().first { it.account.id == "card" }.balanceMinor,
        )
        invalid {
            plans.plan(
                paymentPlan(
                    "Duplicate EMI",
                    "EMI",
                    purchase = purchase,
                    principal = 10001,
                    installments = 3,
                ),
                0,
            )
        }
    }

    @Test
    fun repeatedHistorySuggestionsRequireEvidenceAndNeverPostAutomatically() {
        val base = LocalDate.of(2026, 1, 15)
        val records =
            (0..2).map {
                LedgerRecord(
                    id(),
                    "bank",
                    "food",
                    "EXPENSE",
                    19900,
                    base.plusMonths(it.toLong()).toEpochDay(),
                    "UTC",
                    "Music",
                )
            }
        val account = AccountBalance(Account("bank", "Bank", "BANK", "INR", 0, 0), 0)
        val found = PlanDates.detect(records, listOf(account))
        assertEquals(1, found.size)
        assertEquals("MONTHLY", found.single().frequency)
        assertTrue(PlanDates.detect(records.take(2), listOf(account)).isEmpty())
    }

    @Test
    fun reminderPlanningHasStableTokensQuietHoursAndOverdueDedupe() {
        val today = 20000L
        val due = FinanceReminderPlan.due("plan:1", today + 7, "7,1,0", today)
        assertEquals(1, due.size)
        assertEquals(due, FinanceReminderPlan.due("plan:1", today + 7, "7,1,0", today))
        assertEquals(1, FinanceReminderPlan.due("plan:1", today - 1, "7,1,0", today).size)
        assertTrue(FinanceReminderPlan.quiet(FinanceReminderSettings(), 23))
        assertTrue(FinanceReminderPlan.quiet(FinanceReminderSettings(), 7))
        assertFalse(FinanceReminderPlan.quiet(FinanceReminderSettings(), 12))
        assertFalse(
            FinanceReminderPlan.quiet(FinanceReminderSettings(quietStart = 0, quietEnd = 0), 0)
        )
    }

    @Test
    fun allNewModulesRoundTripThroughEncryptedSnapshot() = runBlocking {
        val g = group()
        groups.expense(
            g.id,
            g.selfId,
            1000,
            "EQUAL",
            mapOf(g.selfId to "1"),
            today,
            "Own",
            null,
            id(),
        )
        expense(20000, day = today - 2)
        plans.cycle(CardCycle("card", 1, 20, 100000, 0, "bank", operationId = id()), 0)
        plans.statement(
            CardStatement(id(), "card", today - 1, today + 19, 20000, 0, false, operationId = id())
        )
        val p = paymentPlan()
        plans.plan(p, 0)
        plans.confirm(p.id, 1, 10000, today, null, 0, id())
        val bytes =
            ByteArrayOutputStream()
                .also {
                    PortableBackup(context, db, TestGiftCipher())
                        .write(it, "password-1234".toCharArray())
                }
                .toByteArray()
        val target = TraxosDatabase.open(context, "money-restore-${id()}.db")
        try {
            val backup = PortableBackup(context, target, TestGiftCipher())
            backup.inspect(ByteArrayInputStream(bytes), "password-1234".toCharArray()).use {
                backup.restore(it)
            }
            assertEquals(1, target.groups().groups().first().size)
            assertEquals(1, target.obligations().plans().first().size)
            assertEquals(1, target.obligations().statements().first().size)
            assertEquals(1, target.obligations().payments().first().size)
        } finally {
            val name = target.openHelper.databaseName!!
            target.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun schema12MigrationPreservesExistingTransactionsAndAddsEmptyModules() = runBlocking {
        val name = "upgrade12-${id()}.db"
        val schema =
            JSONObject(
                    javaClass.classLoader!!
                        .getResourceAsStream("app.traxos.finance.data.TraxosDatabase/12.json")!!
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
            val q = schema.getJSONArray("setupQueries")
            for (i in 0 until q.length()) old.execSQL(q.getString(i))
            old.execSQL(
                "INSERT INTO accounts (id,name,type,currency,openingMinor,openingDate,archived,revision,profileId) VALUES ('keep','Bank','BANK','INR',12345,20000,0,1,'local')"
            )
            old.version = 12
        }
        val upgraded = TraxosDatabase.open(context, name)
        try {
            assertEquals(12345L, upgraded.ledger().accounts().first().single().balanceMinor)
            assertTrue(upgraded.groups().groups().first().isEmpty())
            assertTrue(upgraded.obligations().plans().first().isEmpty())
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun carriedForwardStatementsAreOverlappingSnapshotsNotDoubleDebts() = runBlocking {
        expense(10000, day = today - 35)
        expense(10000, day = today - 5)
        val first =
            CardStatement(
                id(),
                "card",
                today - 30,
                today - 10,
                10000,
                1000,
                false,
                operationId = id(),
            )
        val second =
            CardStatement(
                id(),
                "card",
                today - 2,
                today + 18,
                20000,
                2000,
                false,
                operationId = id(),
            )
        plans.statement(first)
        plans.statement(second)
        val payment = id()
        ledger.payCard(payment, "card", "bank", 10000, today)
        assertEquals(0L, PlanDates.remaining(first, plans.dao.paid(first.id)))
        assertEquals(10000L, PlanDates.remaining(second, plans.dao.paid(second.id)))
        plans.allocate(first.id, payment, 10000, id())
        assertEquals(10000L, PlanDates.remaining(second, plans.dao.paid(second.id)))
    }

    @Test
    fun mixedPayersAndNetSettlementDoNotUndercountPersonalSpending() = runBlocking {
        val g = group()
        val other = db.groups().members(g.id).first().first { it.id != g.selfId }
        val people = mapOf(g.selfId to "1", other.id to "1")
        val source = expense(10000, "bank")
        groups.expense(g.id, g.selfId, 10000, "EQUAL", people, today, "I paid", source, id())
        groups.expense(g.id, other.id, 20000, "EQUAL", people, today, "Friend paid", null, id())
        val settlement = expense(5000, "bank")
        groups.settle(g.id, g.selfId, other.id, 5000, today, "Net repayment", settlement, id())
        assertEquals(15000L, db.ledger().summary(today, today + 1).first().single().expenseMinor)
        assertEquals(
            4985000L,
            db.ledger().accounts().first().first { it.account.id == "bank" }.balanceMinor,
        )
        assertTrue(
            GroupMath.balances(
                    db.groups().members(g.id).first(),
                    db.groups().events(g.id).first(),
                    db.groups().shares(g.id).first(),
                )
                .values
                .all { it == 0L }
        )
    }

    @Test
    fun bankConfirmedStatementAdjustmentsPreserveOriginalFiguresAndDoNotWriteMoney() = runBlocking {
        expense(10000, day = today - 5)
        val statement =
            CardStatement(
                id(),
                "card",
                today - 2,
                today + 18,
                10000,
                1000,
                true,
                operationId = id(),
            )
        plans.statement(statement)
        val operation = id()
        plans.adjustStatement(
            statement.id,
            1,
            7000,
            500,
            today + 19,
            "Bank refund adjustment",
            operation,
        )
        plans.adjustStatement(
            statement.id,
            1,
            7000,
            500,
            today + 19,
            "Bank refund adjustment",
            operation,
        )
        assertEquals(7000L, plans.dao.statement(statement.id)!!.amountMinor)
        assertFalse(plans.dao.statement(statement.id)!!.estimated)
        assertEquals(10000L, plans.dao.revisions().first().single().beforeAmount)
        assertEquals(1L, db.ledger().recordCount())
    }

    @Test
    fun unmatchingStatementDoesNotUndoActualCardPayment() = runBlocking {
        expense(10000, day = today - 5)
        val s =
            CardStatement(id(), "card", today - 2, today + 18, 10000, 0, false, operationId = id())
        plans.statement(s)
        val payment = id()
        plans.pay(s.id, "bank", 3000, today, payment)
        plans.unmatch(payment)
        plans.unmatch(payment)
        assertEquals(3000L, plans.dao.paid(s.id))
        assertEquals(7000L, PlanDates.remaining(s, plans.dao.paid(s.id)))
        assertFalse(db.ledger().record(payment)!!.deleted)
    }

    @Test
    fun trialEndAndEndDateAndReminderSettingsPersist() = runBlocking {
        val p = paymentPlan().copy(trialEnd = today, endDay = today)
        plans.plan(p, 0)
        plans.confirm(p.id, 1, 10000, today, null, 0, id())
        assertEquals("ENDED", plans.dao.plan(p.id)!!.status)
        val settings = FinanceReminderSettings(offsets = "30,7,1,0", quietStart = 23, quietEnd = 7)
        plans.reminders(settings)
        assertEquals(settings, plans.dao.reminderSettings().first())
        invalid { plans.reminders(settings.copy(offsets = "7,7")) }
        invalid { plans.reminders(settings.copy(quietStart = 24)) }
    }

    @Test
    fun financialRecordReferencesSurviveRestartAndLinkedGroupCanBeReversed() = runBlocking {
        val g = group()
        val tx = expense(10000, "bank")
        val event = id()
        groups.expense(
            g.id,
            g.selfId,
            10000,
            "EQUAL",
            mapOf(g.selfId to "1"),
            today,
            "Linked",
            tx,
            event,
        )
        val name = db.openHelper.databaseName!!
        db.close()
        db = TraxosDatabase.open(context, name)
        ledger = LedgerRepository(db)
        groups = GroupsRepository(db)
        assertNotNull(db.groups().link(tx))
        groups.reverse(event, id())
        assertNull(db.groups().link(tx))
        ledger.setDeleted(id(), tx, 1, true)
        assertEquals(0L, db.ledger().summary(today, today + 1).first().sumOf { it.expenseMinor })
    }

    @Test
    fun schema12EncryptedDebtArchiveRestoresOnSchema13AndIsIdempotent() = runBlocking {
        val thread = id()
        val movement = id()
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
                "debt_threads",
                "debt_movements",
                "gift_cards",
                "gift_redemptions",
            )
        val archive =
            ByteArrayOutputStream()
                .also { out ->
                    BackupEnvelope.write(out, "password-1234".toCharArray()) { data ->
                        fun text(value: String) {
                            val bytes = value.toByteArray(Charsets.UTF_8)
                            data.writeInt(bytes.size)
                            data.write(bytes)
                        }
                        data.writeInt(1)
                        data.writeInt(12)
                        data.writeInt(tables.size)
                        tables.forEach { table ->
                            text(table)
                            data.writeLong(
                                if (table in listOf("debt_threads", "debt_movements")) 1 else 0
                            )
                            if (table == "debt_threads")
                                text(
                                    JSONObject()
                                        .put("id", thread)
                                        .put("person", "Old debt")
                                        .put("direction", "LENT")
                                        .put("currency", "INR")
                                        .put("originalMinor", 10000)
                                        .put("outstandingMinor", 7000)
                                        .put("day", 20000)
                                        .put("dueDay", JSONObject.NULL)
                                        .put("note", "")
                                        .put("archived", 0)
                                        .put("revision", 2)
                                        .put("operationId", id())
                                        .toString()
                                )
                            if (table == "debt_movements")
                                text(
                                    JSONObject()
                                        .put("id", movement)
                                        .put("debtId", thread)
                                        .put("deltaMinor", -3000)
                                        .put("kind", "REPAYMENT")
                                        .put("day", 20001)
                                        .put("note", "Old payment")
                                        .put("createdAt", 1)
                                        .put("undoOf", JSONObject.NULL)
                                        .toString()
                                )
                        }
                        text("END")
                    }
                }
                .toByteArray()
        val target = TraxosDatabase.open(context, "old12-${id()}.db")
        try {
            val backup = PortableBackup(context, target, TestGiftCipher())
            backup.inspect(ByteArrayInputStream(archive), "password-1234".toCharArray()).use {
                backup.restore(it)
            }
            assertEquals(7000L, target.debts().thread(thread)!!.outstandingMinor)
            assertTrue(target.groups().groups().first().isEmpty())
            backup.inspect(ByteArrayInputStream(archive), "password-1234".toCharArray()).use {
                assertEquals(0L, backup.restore(it))
            }
        } finally {
            val name = target.openHelper.databaseName!!
            target.close()
            context.deleteDatabase(name)
        }
    }
}
