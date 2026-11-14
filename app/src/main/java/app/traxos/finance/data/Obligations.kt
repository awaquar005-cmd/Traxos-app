package app.traxos.finance.data

import androidx.room.*
import app.traxos.finance.domain.Money
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Entity(
    tableName = "card_cycles",
    foreignKeys =
        [
            ForeignKey(
                entity = Account::class,
                parentColumns = ["id"],
                childColumns = ["accountId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
)
data class CardCycle(
    @PrimaryKey val accountId: String,
    val closingDay: Int,
    val dueDays: Int,
    val limitMinor: Long?,
    val minimumMinor: Long,
    val fundingId: String?,
    val reminders: Boolean = true,
    val offsets: String = "7,1,0",
    val revision: Long = 1,
    val operationId: String,
)

@Entity(
    tableName = "card_statements",
    foreignKeys =
        [
            ForeignKey(
                entity = Account::class,
                parentColumns = ["id"],
                childColumns = ["accountId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices = [Index(value = ["accountId", "closingDay"])],
)
data class CardStatement(
    @PrimaryKey val id: String,
    val accountId: String,
    val closingDay: Long,
    val dueDay: Long,
    val amountMinor: Long,
    val minimumMinor: Long,
    val estimated: Boolean,
    val voided: Boolean = false,
    val revision: Long = 1,
    val operationId: String,
)

@Entity(
    tableName = "statement_revisions",
    foreignKeys =
        [
            ForeignKey(
                entity = CardStatement::class,
                parentColumns = ["id"],
                childColumns = ["statementId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices = [Index("statementId")],
)
data class StatementRevision(
    @PrimaryKey val id: String,
    val statementId: String,
    val beforeAmount: Long,
    val beforeMinimum: Long,
    val beforeDue: Long,
    val beforeEstimated: Boolean,
    val reason: String,
    val createdAt: Long,
)

@Entity(
    tableName = "statement_payments",
    foreignKeys =
        [
            ForeignKey(
                entity = CardStatement::class,
                parentColumns = ["id"],
                childColumns = ["statementId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = LedgerRecord::class,
                parentColumns = ["id"],
                childColumns = ["recordId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices = [Index("statementId"), Index("recordId")],
)
data class StatementPayment(
    @PrimaryKey val id: String,
    val statementId: String,
    val recordId: String,
    val amountMinor: Long,
    val createdAt: Long,
    val active: Boolean = true,
)

@Entity(
    tableName = "payment_plans",
    foreignKeys =
        [
            ForeignKey(
                entity = Account::class,
                parentColumns = ["id"],
                childColumns = ["accountId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = Category::class,
                parentColumns = ["id"],
                childColumns = ["categoryId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices = [Index("accountId"), Index("categoryId"), Index(value = ["status", "nextDay"])],
)
data class PaymentPlan(
    @PrimaryKey val id: String,
    val kind: String,
    val name: String,
    val accountId: String,
    val categoryId: String,
    val currency: String,
    val amountMinor: Long?,
    val frequency: String,
    val customDays: Int,
    val anchorDay: Int,
    val nextDay: Long,
    val trialEnd: Long?,
    val endDay: Long?,
    val status: String = "ACTIVE",
    val reminders: Boolean = true,
    val offsets: String = "7,1,0",
    val purchaseId: String? = null,
    val installments: Int = 0,
    val principalMinor: Long = 0,
    val revision: Long = 1,
    val operationId: String,
)

@Entity(
    tableName = "plan_payments",
    foreignKeys =
        [
            ForeignKey(
                entity = PaymentPlan::class,
                parentColumns = ["id"],
                childColumns = ["planId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = LedgerRecord::class,
                parentColumns = ["id"],
                childColumns = ["recordId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices =
        [
            Index(value = ["planId", "dueDay"]),
            Index(value = ["occurrenceKey"], unique = true),
            Index(value = ["recordId"]),
        ],
)
data class PlanPayment(
    @PrimaryKey val id: String,
    val planId: String,
    val dueDay: Long,
    val paidDay: Long,
    val amountMinor: Long,
    val feeMinor: Long,
    val recordId: String?,
    val createdAt: Long,
    val occurrenceKey: String,
    val createdRecord: Boolean,
    val voided: Boolean = false,
)

@Entity(tableName = "plan_ignored") data class IgnoredPlan(@PrimaryKey val key: String)

@Entity(tableName = "finance_reminder_settings")
data class FinanceReminderSettings(
    @PrimaryKey val id: Int = 1,
    val enabled: Boolean = true,
    val offsets: String = "7,1,0",
    val quietStart: Int = 22,
    val quietEnd: Int = 8,
)

@Entity(tableName = "finance_reminder_receipts")
data class FinanceReminderReceipt(@PrimaryKey val token: String, val sentAt: Long)

data class StatementPaid(val id: String, val paidMinor: Long)

data class LocalPaymentPlan(
    @Embedded val plan: PaymentPlan,
    @Relation(parentColumn = "id", entityColumn = "planId") val payments: List<PlanPayment>,
)

@Dao
interface ObligationsDao {
    @Transaction
    @Query("SELECT * FROM payment_plans ORDER BY nextDay,name,id")
    fun planSnapshots(): kotlinx.coroutines.flow.Flow<List<LocalPaymentPlan>>

    @Query("SELECT * FROM card_cycles") fun cycles(): kotlinx.coroutines.flow.Flow<List<CardCycle>>

    @Query("SELECT * FROM card_cycles WHERE accountId=:id")
    suspend fun cycle(id: String): CardCycle?

    @Upsert suspend fun put(row: CardCycle)

    @Query("SELECT * FROM card_statements ORDER BY dueDay DESC,id")
    fun statements(): kotlinx.coroutines.flow.Flow<List<CardStatement>>

    @Query("SELECT * FROM card_statements WHERE id=:id")
    suspend fun statement(id: String): CardStatement?

    @Upsert suspend fun put(row: CardStatement)

    @Query(
        "SELECT s.id, COALESCE(SUM(t.amountMinor),0) AS paidMinor FROM card_statements s LEFT JOIN transactions t ON t.accountId=s.accountId AND t.type='PAYMENT_IN' AND t.deleted=0 AND t.day>s.closingDay AND t.day<=:today GROUP BY s.id"
    )
    fun statementPaid(
        today: Long = LocalDate.now().toEpochDay()
    ): kotlinx.coroutines.flow.Flow<List<StatementPaid>>

    @Query("SELECT * FROM statement_revisions ORDER BY createdAt DESC,id DESC")
    fun revisions(): kotlinx.coroutines.flow.Flow<List<StatementRevision>>

    @Query("SELECT * FROM statement_revisions WHERE id=:id")
    suspend fun revision(id: String): StatementRevision?

    @Insert suspend fun insert(row: StatementRevision)

    @Query("SELECT * FROM statement_payments")
    fun allocations(): kotlinx.coroutines.flow.Flow<List<StatementPayment>>

    @Query("SELECT * FROM statement_payments WHERE id=:id")
    suspend fun allocation(id: String): StatementPayment?

    @Insert suspend fun insert(row: StatementPayment)

    @Update suspend fun update(row: StatementPayment)

    @Query(
        "SELECT COALESCE(SUM(t.amountMinor),0) FROM card_statements s JOIN transactions t ON t.accountId=s.accountId WHERE s.id=:id AND t.type='PAYMENT_IN' AND t.deleted=0 AND t.day>s.closingDay AND t.day<=:today"
    )
    suspend fun paid(id: String, today: Long = LocalDate.now().toEpochDay()): Long

    @Query(
        "SELECT COALESCE(SUM(amountMinor),0) FROM statement_payments WHERE recordId=:id AND active=1"
    )
    suspend fun allocated(id: String): Long

    @Query("SELECT * FROM payment_plans ORDER BY nextDay,name,id")
    fun plans(): kotlinx.coroutines.flow.Flow<List<PaymentPlan>>

    @Query("SELECT * FROM payment_plans WHERE id=:id") suspend fun plan(id: String): PaymentPlan?

    @Upsert suspend fun put(row: PaymentPlan)

    @Query("SELECT * FROM plan_payments ORDER BY createdAt DESC,id DESC")
    fun payments(): kotlinx.coroutines.flow.Flow<List<PlanPayment>>

    @Query("SELECT * FROM plan_payments WHERE id=:id") suspend fun payment(id: String): PlanPayment?

    @Query("SELECT * FROM plan_payments WHERE recordId=:id AND voided=0")
    suspend fun paymentForRecord(id: String): PlanPayment?

    @Insert suspend fun insert(row: PlanPayment)

    @Update suspend fun update(row: PlanPayment)

    @Query("SELECT * FROM plan_ignored")
    fun ignored(): kotlinx.coroutines.flow.Flow<List<IgnoredPlan>>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun ignore(row: IgnoredPlan)

    @Query("SELECT * FROM transactions WHERE deleted=0 ORDER BY day DESC,id DESC LIMIT 5000")
    fun records(): kotlinx.coroutines.flow.Flow<List<LedgerRecord>>

    @Query("SELECT * FROM finance_reminder_settings WHERE id=1")
    fun reminderSettings(): kotlinx.coroutines.flow.Flow<FinanceReminderSettings?>

    @Upsert suspend fun put(row: FinanceReminderSettings)

    @Query("SELECT * FROM finance_reminder_receipts WHERE token=:id")
    suspend fun receipt(id: String): FinanceReminderReceipt?

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(row: FinanceReminderReceipt)
}

object PlanDates {
    val frequencies = listOf("MONTHLY", "QUARTERLY", "YEARLY", "WEEKLY", "DAILY", "CUSTOM")

    fun next(day: Long, frequency: String, anchor: Int, custom: Int = 1): Long {
        require(anchor in 1..31 && custom in 1..3650)
        val date = LocalDate.ofEpochDay(day)
        val next =
            when (frequency) {
                "DAILY" -> date.plusDays(1)
                "WEEKLY" -> date.plusWeeks(1)
                "CUSTOM" -> date.plusDays(custom.toLong())
                "MONTHLY",
                "QUARTERLY",
                "YEARLY" ->
                    date
                        .withDayOfMonth(1)
                        .plusMonths(
                            when (frequency) {
                                "MONTHLY" -> 1
                                "QUARTERLY" -> 3
                                else -> 12
                            }
                        )
                        .let { it.withDayOfMonth(minOf(anchor, it.lengthOfMonth())) }
                else -> throw IllegalArgumentException("Choose a schedule")
            }
        require(next.year <= 2200) { "Schedule exceeds supported date range" }
        return next.toEpochDay()
    }

    fun closing(month: java.time.YearMonth, day: Int) =
        month.atDay(minOf(day, month.lengthOfMonth())).toEpochDay()

    fun offsets(value: String): List<Int> {
        val parsed =
            value.split(',').map {
                it.trim().toIntOrNull()
                    ?: throw IllegalArgumentException("Use comma-separated whole reminder days")
            }
        require(
            parsed.isNotEmpty() &&
                parsed.size <= 8 &&
                parsed.all { it in 0..90 } &&
                parsed.distinct().size == parsed.size
        ) {
            "Use up to eight different reminder days (0–90)"
        }
        return parsed.sortedDescending()
    }

    fun remaining(statement: CardStatement, paid: Long) = maxOf(0, statement.amountMinor - paid)

    fun minimumRemaining(statement: CardStatement, paid: Long) =
        maxOf(0, statement.minimumMinor - paid).coerceAtMost(remaining(statement, paid))

    fun installment(plan: PaymentPlan, completed: Int): Long {
        require(plan.kind == "EMI" && completed in 0 until plan.installments)
        val base = plan.principalMinor / plan.installments
        return base +
            if (completed == plan.installments - 1) plan.principalMinor % plan.installments else 0
    }

    data class Suggestion(
        val key: String,
        val name: String,
        val accountId: String,
        val categoryId: String,
        val currency: String,
        val amount: Long?,
        val frequency: String,
        val nextDay: Long,
        val evidence: Int,
    )

    fun detect(records: List<LedgerRecord>, accounts: List<AccountBalance>): List<Suggestion> {
        val currencies = accounts.associate { it.account.id to it.account.currency }
        return records
            .filter {
                it.type == "EXPENSE" &&
                    !it.deleted &&
                    (it.merchantId != null || it.note.trim().length >= 3)
            }
            .groupBy {
                "${it.accountId}:${it.categoryId}:${it.merchantId?:it.note.trim().lowercase()}"
            }
            .mapNotNull { (key, rows) ->
                val latest = rows.sortedByDescending { it.day }.distinctBy { it.day }.take(4)
                if (latest.size < 3) return@mapNotNull null
                val intervals = latest.zipWithNext { a, b -> a.day - b.day }
                val frequency =
                    when {
                        intervals.all { it in 26..35 } -> "MONTHLY"
                        intervals.all { it in 85..96 } -> "QUARTERLY"
                        intervals.all { it in 360..370 } -> "YEARLY"
                        else -> return@mapNotNull null
                    }
                val last = latest.first()
                val currency = currencies[last.accountId] ?: return@mapNotNull null
                Suggestion(
                    UUID.nameUUIDFromBytes("subscription:$key".toByteArray()).toString(),
                    last.note.trim().ifBlank { "Recurring merchant" }.take(80),
                    last.accountId,
                    last.categoryId,
                    currency,
                    latest.map { it.amountMinor }.distinct().singleOrNull(),
                    frequency,
                    next(last.day, frequency, LocalDate.ofEpochDay(last.day).dayOfMonth),
                    latest.size,
                )
            }
    }
}

class ObligationsRepository(val db: TraxosDatabase) {
    private val ledger = LedgerRepository(db)
    val dao = db.obligations()

    private fun id(value: String) {
        UUID.fromString(value)
    }

    private fun day(value: Long) {
        require(LocalDate.ofEpochDay(value).year in 1980..2200) {
            "Use a date between 1980 and 2200"
        }
    }

    suspend fun cycle(row: CardCycle, expected: Long) =
        withContext(Dispatchers.IO) {
            id(row.operationId)
            require(row.closingDay in 1..31 && row.dueDays in 1..60)
            require(row.limitMinor == null || row.limitMinor in 1..Money.MAX_MINOR)
            require(row.minimumMinor in 0..Money.MAX_MINOR)
            PlanDates.offsets(row.offsets)
            db.withTransaction {
                val a = requireNotNull(db.ledger().account(row.accountId))
                require(a.type == "CREDIT_CARD" && !a.archived)
                row.fundingId?.let {
                    val source = requireNotNull(db.ledger().account(it))
                    require(
                        !source.archived &&
                            source.type != "CREDIT_CARD" &&
                            source.currency == a.currency
                    )
                }
                val old = dao.cycle(row.accountId)
                if (old?.operationId == row.operationId) {
                    require(old.copy(revision = row.revision) == row)
                    return@withTransaction
                }
                require((old?.revision ?: 0) == expected) { "Card cycle changed; reopen" }
                dao.put(row.copy(revision = expected + 1))
            }
        }

    suspend fun statement(row: CardStatement) =
        withContext(Dispatchers.IO) {
            id(row.id)
            id(row.operationId)
            day(row.closingDay)
            day(row.dueDay)
            require(
                row.dueDay >= row.closingDay &&
                    row.amountMinor in 0..Money.MAX_MINOR &&
                    row.minimumMinor in 0..row.amountMinor
            )
            db.withTransaction {
                val account = requireNotNull(db.ledger().account(row.accountId))
                require(account.type == "CREDIT_CARD" && !account.archived)
                val old = dao.statement(row.id)
                if (old != null) {
                    require(old == row) { "Statement already exists" }
                    return@withTransaction
                }
                require(
                    dao.statements().first().none {
                        it.accountId == row.accountId &&
                            it.closingDay == row.closingDay &&
                            !it.voided
                    }
                ) {
                    "This closing date already has a statement"
                }
                dao.put(row)
            }
        }

    suspend fun estimate(accountId: String, close: Long): Long =
        withContext(Dispatchers.IO) {
            day(close)
            val a = requireNotNull(db.ledger().account(accountId))
            require(a.type == "CREDIT_CARD" && close >= a.openingDate)
            val balance =
                db.openHelper.readableDatabase
                    .query(
                        "SELECT ?+COALESCE(SUM(CASE WHEN type IN ('INCOME','REFUND','PAYMENT_IN','TRANSFER_IN') THEN amountMinor ELSE -amountMinor END),0) FROM transactions WHERE accountId=? AND deleted=0 AND day<=?",
                        arrayOf<Any>(a.openingMinor, accountId, close),
                    )
                    .use {
                        it.moveToFirst()
                        it.getLong(0)
                    }
            maxOf(0, -balance)
        }

    suspend fun allocate(statementId: String, recordId: String, amount: Long, operation: String) =
        withContext(Dispatchers.IO) {
            id(operation)
            require(amount in 1..Money.MAX_MINOR)
            db.withTransaction {
                dao.allocation(operation)?.let {
                    require(
                        it.statementId == statementId &&
                            it.recordId == recordId &&
                            it.amountMinor == amount
                    )
                    return@withTransaction
                }
                val s = requireNotNull(dao.statement(statementId))
                val r = requireNotNull(db.ledger().record(recordId))
                require(
                    !s.voided &&
                        !r.deleted &&
                        r.type == "PAYMENT_IN" &&
                        r.accountId == s.accountId &&
                        r.day > s.closingDay &&
                        r.day <= LocalDate.now().toEpochDay()
                ) {
                    "Choose a card payment after statement close"
                }
                require(
                    amount <= r.amountMinor - dao.allocated(recordId) &&
                        amount <=
                            s.amountMinor -
                                dao.allocations()
                                    .first()
                                    .filter { it.statementId == s.id && it.active }
                                    .sumOf { it.amountMinor }
                ) {
                    "Allocation exceeds payment or statement amount"
                }
                dao.insert(
                    StatementPayment(operation, s.id, recordId, amount, System.currentTimeMillis())
                )
            }
        }

    suspend fun pay(
        statementId: String,
        sourceId: String,
        amount: Long,
        paidDay: Long,
        operation: String,
    ) =
        withContext(Dispatchers.IO) {
            id(operation)
            day(paidDay)
            db.withTransaction {
                dao.allocation(operation)?.let {
                    require(
                        it.statementId == statementId &&
                            it.amountMinor == amount &&
                            db.ledger().record(it.recordId)?.let { r ->
                                r.day == paidDay &&
                                    db.ledger().linked(r.linkId!!).any { p ->
                                        p.accountId == sourceId
                                    }
                            } == true
                    )
                    return@withTransaction
                }
                val s = requireNotNull(dao.statement(statementId))
                require(
                    !s.voided &&
                        paidDay > s.closingDay &&
                        paidDay <= LocalDate.now().toEpochDay() &&
                        amount <= PlanDates.remaining(s, dao.paid(s.id))
                )
                ledger.payCard(operation, s.accountId, sourceId, amount, paidDay)
                allocate(s.id, operation, amount, operation)
            }
        }

    suspend fun adjustStatement(
        id: String,
        expected: Long,
        amount: Long,
        minimum: Long,
        due: Long,
        reason: String,
        operation: String,
    ) =
        withContext(Dispatchers.IO) {
            UUID.fromString(operation)
            day(due)
            require(
                amount in 0..Money.MAX_MINOR &&
                    minimum in 0..amount &&
                    reason.trim().length in 1..200
            )
            db.withTransaction {
                val old = requireNotNull(dao.statement(id))
                dao.revision(operation)?.let {
                    require(
                        it.statementId == id &&
                            old.operationId == operation &&
                            old.amountMinor == amount &&
                            old.minimumMinor == minimum &&
                            old.dueDay == due
                    )
                    return@withTransaction
                }
                require(!old.voided && old.revision == expected && due >= old.closingDay) {
                    "Statement changed; reopen"
                }
                dao.insert(
                    StatementRevision(
                        operation,
                        id,
                        old.amountMinor,
                        old.minimumMinor,
                        old.dueDay,
                        old.estimated,
                        reason.trim(),
                        System.currentTimeMillis(),
                    )
                )
                dao.put(
                    old.copy(
                        amountMinor = amount,
                        minimumMinor = minimum,
                        dueDay = due,
                        estimated = false,
                        revision = old.revision + 1,
                        operationId = operation,
                    )
                )
            }
        }

    suspend fun unmatch(id: String) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val allocation = requireNotNull(dao.allocation(id))
                dao.update(allocation.copy(active = false))
            }
        }

    suspend fun voidStatement(id: String, expected: Long) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val s = requireNotNull(dao.statement(id))
                require(s.revision == expected && dao.paid(id) == 0L) {
                    "A statement with payments cannot be voided"
                }
                dao.put(s.copy(voided = true, revision = expected + 1))
            }
        }

    suspend fun plan(row: PaymentPlan, expected: Long) =
        withContext(Dispatchers.IO) {
            id(row.id)
            id(row.operationId)
            day(row.nextDay)
            require(
                row.name.trim().length in 1..80 && row.kind in listOf("SUBSCRIPTION", "BILL", "EMI")
            )
            require(row.status in listOf("ACTIVE", "PAUSED", "CANCELLED", "ENDED"))
            require(
                row.frequency in PlanDates.frequencies &&
                    row.anchorDay in 1..31 &&
                    row.customDays in 1..3650
            )
            require(row.amountMinor == null || row.amountMinor in 1..Money.MAX_MINOR)
            PlanDates.offsets(row.offsets)
            row.trialEnd?.let {
                day(it)
                require(it <= row.nextDay) { "Trial ends on or before the next billing date" }
            }
            row.endDay?.let {
                day(it)
                require(it >= row.nextDay || row.status == "ENDED")
            }
            db.withTransaction {
                val a = requireNotNull(db.ledger().account(row.accountId))
                val category = requireNotNull(db.ledger().category(row.categoryId))
                require(
                    a.currency == row.currency &&
                        row.currency in Money.currencies &&
                        !a.archived &&
                        category.type == "EXPENSE" &&
                        !category.archived
                )
                val old = dao.plan(row.id)
                if (old?.operationId == row.operationId) {
                    require(old.copy(revision = row.revision) == row)
                    return@withTransaction
                }
                require((old?.revision ?: 0) == expected) { "Plan changed; reopen" }
                require(
                    dao.plans().first().none {
                        it.id != row.id &&
                            it.status != "CANCELLED" &&
                            it.accountId == row.accountId &&
                            it.name.equals(row.name.trim(), true)
                    }
                ) {
                    "A plan with this name and account already exists"
                }
                val history = dao.payments().first().filter { it.planId == row.id && !it.voided }
                require(
                    old == null ||
                        history.isEmpty() ||
                        old.currency == row.currency &&
                            old.kind == row.kind &&
                            old.accountId == row.accountId &&
                            old.purchaseId == row.purchaseId &&
                            old.principalMinor == row.principalMinor &&
                            old.installments == row.installments
                ) {
                    "Funding/currency/EMI terms cannot change after payment history"
                }
                if (row.kind == "EMI") {
                    val purchase =
                        requireNotNull(row.purchaseId?.let { db.ledger().record(it) }) {
                            "Choose the original card purchase"
                        }
                    require(
                        !purchase.deleted &&
                            purchase.type == "EXPENSE" &&
                            purchase.accountId == a.id &&
                            a.type == "CREDIT_CARD" &&
                            row.principalMinor == purchase.amountMinor &&
                            row.installments in 2..120 &&
                            row.principalMinor >= row.installments &&
                            row.amountMinor == row.principalMinor / row.installments &&
                            row.frequency == "MONTHLY"
                    )
                    require(
                        dao.plans().first().none {
                            it.id != row.id && it.purchaseId == row.purchaseId
                        }
                    ) {
                        "This purchase already has an EMI plan"
                    }
                    require(row.nextDay >= purchase.day)
                } else
                    require(
                        row.purchaseId == null && row.installments == 0 && row.principalMinor == 0L
                    )
                dao.put(row.copy(name = row.name.trim(), revision = expected + 1))
            }
        }

    suspend fun status(id: String, expected: Long, value: String) =
        withContext(Dispatchers.IO) {
            require(value in listOf("ACTIVE", "PAUSED", "CANCELLED"))
            db.withTransaction {
                val p = requireNotNull(dao.plan(id))
                require(p.revision == expected && p.status != "ENDED")
                dao.put(p.copy(status = value, revision = expected + 1))
            }
        }

    suspend fun confirm(
        planId: String,
        expected: Long,
        amount: Long,
        paidDay: Long,
        existingRecord: String?,
        fee: Long,
        operation: String,
    ) =
        withContext(Dispatchers.IO) {
            id(operation)
            day(paidDay)
            require(amount in 1..Money.MAX_MINOR && fee in 0..Money.MAX_MINOR)
            db.withTransaction {
                dao.payment(operation)?.let {
                    require(
                        it.planId == planId &&
                            it.amountMinor == amount &&
                            it.paidDay == paidDay &&
                            it.feeMinor == fee &&
                            (existingRecord == null || existingRecord == it.recordId)
                    )
                    return@withTransaction
                }
                val p = requireNotNull(dao.plan(planId))
                require(p.revision == expected && p.status == "ACTIVE") {
                    "Plan changed or inactive; reopen"
                }
                require(paidDay <= LocalDate.now().toEpochDay()) {
                    "Future obligations are not paid expenses"
                }
                val a = requireNotNull(db.ledger().account(p.accountId))
                require(!a.archived && paidDay >= a.openingDate)
                val history = dao.payments().first().filter { it.planId == p.id && !it.voided }
                var recordId: String? = null
                if (p.kind == "EMI") {
                    require(
                        existingRecord == null && amount == PlanDates.installment(p, history.size)
                    ) {
                        "Use the scheduled principal amount; fees are separate"
                    }
                    if (fee > 0) {
                        recordId =
                            UUID.nameUUIDFromBytes("emi-fee:$operation".toByteArray()).toString()
                        ledger.saveRecord(
                            operation,
                            recordId,
                            p.accountId,
                            p.categoryId,
                            "EXPENSE",
                            fee,
                            paidDay,
                            "${p.name} · installment fee",
                        )
                    }
                } else {
                    require(fee == 0L)
                    recordId = existingRecord
                    if (recordId != null) {
                        val r = requireNotNull(db.ledger().record(recordId))
                        require(
                            !r.deleted &&
                                r.type == "EXPENSE" &&
                                r.accountId == p.accountId &&
                                r.amountMinor == amount &&
                                r.day == paidDay &&
                                r.linkId == null &&
                                db.groups().link(r.id) == null &&
                                db.planning().debtRecord(r.id) == null &&
                                dao.paymentForRecord(r.id) == null
                        ) {
                            "Choose an unused matching expense, amount, account and date"
                        }
                    } else {
                        recordId =
                            UUID.nameUUIDFromBytes("plan-payment:$operation".toByteArray())
                                .toString()
                        require(db.ledger().record(recordId) == null) {
                            "This occurrence already has an entry; match the existing payment"
                        }
                        ledger.saveRecord(
                            operation,
                            recordId,
                            p.accountId,
                            p.categoryId,
                            "EXPENSE",
                            amount,
                            paidDay,
                            p.name,
                        )
                    }
                }
                dao.insert(
                    PlanPayment(
                        operation,
                        p.id,
                        p.nextDay,
                        paidDay,
                        amount,
                        fee,
                        recordId,
                        System.currentTimeMillis(),
                        "${p.id}:${p.nextDay}",
                        existingRecord == null && recordId != null,
                    )
                )
                val next = PlanDates.next(p.nextDay, p.frequency, p.anchorDay, p.customDays)
                val ended =
                    (p.endDay != null && next > p.endDay) ||
                        (p.kind == "EMI" && history.size + 1 >= p.installments)
                dao.put(
                    p.copy(
                        nextDay = next,
                        status = if (ended) "ENDED" else p.status,
                        trialEnd = null,
                        revision = p.revision + 1,
                    )
                )
            }
        }

    suspend fun undoPayment(paymentId: String, expected: Long) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val payment = requireNotNull(dao.payment(paymentId))
                if (payment.voided) return@withTransaction
                val plan = requireNotNull(dao.plan(payment.planId))
                require(plan.revision == expected) { "Plan changed; reopen" }
                val latest =
                    dao.payments()
                        .first()
                        .filter { it.planId == plan.id && !it.voided }
                        .maxByOrNull { it.dueDay }
                require(
                    latest?.id == paymentId &&
                        plan.nextDay ==
                            PlanDates.next(
                                payment.dueDay,
                                plan.frequency,
                                plan.anchorDay,
                                plan.customDays,
                            )
                ) {
                    "Only the latest unchanged occurrence can be undone"
                }
                dao.update(payment.copy(voided = true, occurrenceKey = "void:${payment.id}"))
                if (payment.createdRecord && payment.recordId != null) {
                    val row = requireNotNull(db.ledger().record(payment.recordId))
                    if (!row.deleted)
                        ledger.setDeleted(
                            UUID.nameUUIDFromBytes("plan-undo:${payment.id}".toByteArray())
                                .toString(),
                            row.id,
                            row.revision,
                            true,
                        )
                }
                dao.put(
                    plan.copy(
                        nextDay = payment.dueDay,
                        status = "ACTIVE",
                        revision = plan.revision + 1,
                    )
                )
            }
        }

    suspend fun reminders(settings: FinanceReminderSettings) =
        withContext(Dispatchers.IO) {
            PlanDates.offsets(settings.offsets)
            require(settings.quietStart in 0..23 && settings.quietEnd in 0..23)
            dao.put(settings)
        }

    suspend fun ignore(key: String) =
        withContext(Dispatchers.IO) {
            id(key)
            dao.ignore(IgnoredPlan(key))
        }
}
