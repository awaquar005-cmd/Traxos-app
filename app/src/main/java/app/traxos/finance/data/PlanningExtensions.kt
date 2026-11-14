package app.traxos.finance.data

import androidx.room.*
import app.traxos.finance.domain.Money
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Entity(tableName = "saved_people", indices = [Index(value = ["identityKey"], unique = true)])
data class SavedPerson(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val identityKey: String,
    val revision: Long = 1,
    val operationId: String,
)

@Entity(
    tableName = "debt_preferences",
    foreignKeys =
        [
            ForeignKey(
                entity = DebtThread::class,
                parentColumns = ["id"],
                childColumns = ["debtId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = SavedPerson::class,
                parentColumns = ["id"],
                childColumns = ["personId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices = [Index("personId")],
)
data class DebtPreferences(
    @PrimaryKey val debtId: String,
    val personId: String?,
    val reminders: Boolean,
    val offsets: String,
    val revision: Long = 1,
    val operationId: String,
)

@Entity(
    tableName = "debt_funding",
    foreignKeys =
        [
            ForeignKey(
                entity = DebtThread::class,
                parentColumns = ["id"],
                childColumns = ["debtId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = DebtMovement::class,
                parentColumns = ["id"],
                childColumns = ["movementId"],
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
            Index("debtId"),
            Index("movementId"),
            Index("recordId"),
            Index(value = ["activeRecordKey"], unique = true),
            Index(value = ["eventKey"], unique = true),
        ],
)
data class DebtFunding(
    @PrimaryKey val id: String,
    val debtId: String,
    val movementId: String?,
    val recordId: String,
    val createdRecord: Boolean,
    val active: Boolean,
    val activeRecordKey: String,
    val eventKey: String,
)

@Entity(
    tableName = "period_budgets",
    foreignKeys =
        [
            ForeignKey(
                entity = Category::class,
                parentColumns = ["id"],
                childColumns = ["categoryId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices = [Index("categoryId"), Index(value = ["scopeKey"], unique = true)],
)
data class PeriodBudget(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val currency: String,
    val categoryId: String?,
    val startDay: Long,
    val endDay: Long?,
    val salaryDay: Int,
    val limitMinor: Long,
    val alerts: Boolean = true,
    val thresholds: String = "80,100",
    val archived: Boolean = false,
    val scopeKey: String,
    val revision: Long = 1,
    val operationId: String,
)

@Entity(
    tableName = "budget_alert_rules",
    foreignKeys =
        [
            ForeignKey(
                entity = Budget::class,
                parentColumns = ["id"],
                childColumns = ["budgetId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class BudgetAlertRule(
    @PrimaryKey val budgetId: String,
    val enabled: Boolean = true,
    val thresholds: String = "80,100",
)

@Entity(
    tableName = "goal_preferences",
    foreignKeys =
        [
            ForeignKey(
                entity = SavingsGoal::class,
                parentColumns = ["id"],
                childColumns = ["goalId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
)
data class GoalPreferences(
    @PrimaryKey val goalId: String,
    val deadlineDay: Long?,
    val reminders: Boolean = true,
    val offsets: String = "7,1,0",
    val revision: Long = 1,
    val operationId: String,
)

@Entity(
    tableName = "goal_funding",
    foreignKeys =
        [
            ForeignKey(
                entity = GoalMovement::class,
                parentColumns = ["id"],
                childColumns = ["movementId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = Account::class,
                parentColumns = ["id"],
                childColumns = ["accountId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = LedgerRecord::class,
                parentColumns = ["id"],
                childColumns = ["transferId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices = [Index("accountId"), Index("transferId")],
)
data class GoalFunding(
    @PrimaryKey val movementId: String,
    val accountId: String,
    val transferId: String?,
    val transferActive: Boolean = true,
)

@Entity(tableName = "planning_settings")
data class PlanningSettings(
    @PrimaryKey val id: Int = 1,
    val budgetAlerts: Boolean = true,
    val thresholds: String = "80,100",
)

data class AccountReservation(val accountId: String, val goalId: String, val amountMinor: Long)

@Dao
interface PlanningDao {
    @Query("SELECT * FROM saved_people ORDER BY name COLLATE NOCASE,id")
    fun people(): Flow<List<SavedPerson>>

    @Query("SELECT * FROM saved_people WHERE id=:id") suspend fun person(id: String): SavedPerson?

    @Upsert suspend fun put(row: SavedPerson)

    @Query("SELECT * FROM debt_preferences") fun debtPreferences(): Flow<List<DebtPreferences>>

    @Query("SELECT * FROM debt_preferences WHERE debtId=:id")
    suspend fun debtPreference(id: String): DebtPreferences?

    @Upsert suspend fun put(row: DebtPreferences)

    @Query("SELECT * FROM debt_funding ORDER BY id") fun debtFunding(): Flow<List<DebtFunding>>

    @Query("SELECT * FROM debt_funding WHERE id=:id")
    suspend fun debtFunding(id: String): DebtFunding?

    @Query("SELECT * FROM debt_funding WHERE recordId=:id AND active=1")
    suspend fun debtRecord(id: String): DebtFunding?

    @Query("SELECT * FROM debt_funding WHERE debtId=:id AND movementId IS NULL AND active=1")
    suspend fun openingFunding(id: String): DebtFunding?

    @Query("SELECT * FROM debt_funding WHERE movementId=:id AND active=1")
    suspend fun movementFunding(id: String): DebtFunding?

    @Insert suspend fun insert(row: DebtFunding)

    @Update suspend fun update(row: DebtFunding)

    @Query("SELECT * FROM period_budgets ORDER BY archived,name,id")
    fun budgets(): Flow<List<PeriodBudget>>

    @Query("SELECT * FROM period_budgets WHERE id=:id")
    suspend fun budget(id: String): PeriodBudget?

    @Upsert suspend fun put(row: PeriodBudget)

    @Query("SELECT * FROM budget_alert_rules") fun budgetRules(): Flow<List<BudgetAlertRule>>

    @Upsert suspend fun put(row: BudgetAlertRule)

    @Query("SELECT * FROM goal_preferences") fun goalPreferences(): Flow<List<GoalPreferences>>

    @Query("SELECT * FROM goal_preferences WHERE goalId=:id")
    suspend fun goalPreference(id: String): GoalPreferences?

    @Upsert suspend fun put(row: GoalPreferences)

    @Query("SELECT * FROM goal_funding") fun goalFunding(): Flow<List<GoalFunding>>

    @Query("SELECT * FROM goal_funding WHERE movementId=:id")
    suspend fun goalFunding(id: String): GoalFunding?

    @Query(
        "SELECT * FROM goal_funding WHERE transferActive=1 AND transferId IN (SELECT id FROM transactions WHERE id=:id OR linkId=(SELECT linkId FROM transactions WHERE id=:id)) LIMIT 1"
    )
    suspend fun transferFunding(id: String): GoalFunding?

    @Insert suspend fun insert(row: GoalFunding)

    @Update suspend fun update(row: GoalFunding)

    @Query(
        "SELECT f.accountId,m.goalId,SUM(m.deltaMinor) AS amountMinor FROM goal_funding f JOIN goal_movements m ON m.id=f.movementId GROUP BY f.accountId,m.goalId"
    )
    fun reservations(): Flow<List<AccountReservation>>

    @Query("SELECT * FROM planning_settings WHERE id=1") fun settings(): Flow<PlanningSettings?>

    @Upsert suspend fun put(row: PlanningSettings)
}

object BudgetPeriods {
    data class Window(val start: Long, val end: Long)

    fun window(row: PeriodBudget, asOf: Long): Window {
        if (row.kind == "CUSTOM") return Window(row.startDay, requireNotNull(row.endDay))
        require(row.kind == "SALARY" && row.salaryDay in 1..31)
        val date = LocalDate.ofEpochDay(maxOf(asOf, row.startDay))
        var month = YearMonth.from(date)
        fun close(m: YearMonth) = m.atDay(minOf(row.salaryDay, m.lengthOfMonth())).toEpochDay()
        if (date.toEpochDay() < close(month)) month = month.minusMonths(1)
        val start = close(month)
        val end = close(month.plusMonths(1)) - 1
        return Window(maxOf(start, row.startDay), end)
    }

    fun thresholds(value: String): List<Int> {
        val parsed =
            value.split(',').map {
                it.trim().toIntOrNull()
                    ?: throw IllegalArgumentException("Use whole threshold percentages")
            }
        require(
            parsed.size in 1..5 &&
                parsed.distinct().size == parsed.size &&
                parsed.all { it in 1..100 }
        ) {
            "Use up to five different percentages (1–100)"
        }
        return parsed.sorted()
    }

    fun reached(spent: Long, limit: Long, threshold: Int): Boolean {
        require(limit > 0 && threshold in 1..100)
        return BigInteger.valueOf(maxOf(0, spent)).multiply(BigInteger.valueOf(100)) >=
            BigInteger.valueOf(limit).multiply(BigInteger.valueOf(threshold.toLong()))
    }

    fun scope(row: PeriodBudget) =
        "${row.kind}:${row.currency}:${row.categoryId?:"all"}:${row.startDay}:${row.endDay?:"rolling"}:${row.salaryDay}"

    fun phone(value: String): String {
        require(value.length <= 40 && value.all { it.isDigit() || it in "+ -()." }) {
            "Use a phone number without letters or links"
        }
        val digits = value.filter { it.isDigit() }
        require(digits.isEmpty() || digits.length in 5..20) { "Check the phone number" }
        return if (value.trim().startsWith('+')) "+$digits" else digits
    }
}

class PlanningExtensionsRepository(val db: TraxosDatabase) {
    val dao = db.planning()
    private val ledger = LedgerRepository(db)
    private val debts = DebtsRepository(db)
    private val plans = PlansRepository(db)

    private fun uuid(id: String) {
        UUID.fromString(id)
    }

    private fun date(day: Long) {
        require(LocalDate.ofEpochDay(day).year in 1980..2200) { "Use a date between 1980 and 2200" }
    }

    suspend fun person(id: String, name: String, phone: String, expected: Long, operation: String) =
        withContext(Dispatchers.IO) {
            uuid(id)
            uuid(operation)
            require(name.trim().length in 1..80)
            val normalized = BudgetPeriods.phone(phone)
            val key = "${LedgerRepository.nameKey(name)}:$normalized"
            db.withTransaction {
                val old = dao.person(id)
                if (old?.operationId == operation) {
                    require(old.name == name.trim() && old.phone == normalized)
                    return@withTransaction
                }
                require((old?.revision ?: 0) == expected) { "Person changed; reopen" }
                require(
                    dao.people().first().none {
                        it.id != id &&
                            (it.identityKey == key ||
                                normalized.isNotEmpty() && it.phone == normalized)
                    }
                ) {
                    "This person or phone is already saved; choose the existing person"
                }
                dao.put(SavedPerson(id, name.trim(), normalized, key, expected + 1, operation))
            }
        }

    suspend fun debtPreferences(row: DebtPreferences, expected: Long) =
        withContext(Dispatchers.IO) {
            uuid(row.operationId)
            PlanDates.offsets(row.offsets)
            db.withTransaction {
                val debt = requireNotNull(db.debts().thread(row.debtId))
                require(!row.reminders || debt.dueDay != null) {
                    "Set a due date before enabling reminders"
                }
                row.personId?.let { requireNotNull(dao.person(it)) }
                val old = dao.debtPreference(row.debtId)
                if (old?.operationId == row.operationId) {
                    require(old.copy(revision = row.revision) == row)
                    return@withTransaction
                }
                require((old?.revision ?: 0) == expected)
                dao.put(row.copy(revision = expected + 1))
            }
        }

    suspend fun saveDebt(
        row: DebtThread,
        expected: Long,
        accountId: String?,
        existingRecord: String?,
        personId: String?,
        reminders: Boolean,
        offsets: String,
    ) =
        withContext(Dispatchers.IO) {
            require(accountId == null || accountId.isNotBlank()) {
                "Choose an account for this movement"
            }
            require(existingRecord == null || existingRecord.isNotBlank()) {
                "Choose a transaction to match"
            }
            db.withTransaction {
                val old = dao.debtPreference(row.id)
                debts.save(row, expected)
                debtPreferences(
                    DebtPreferences(
                        row.id,
                        personId,
                        reminders,
                        offsets,
                        operationId = row.operationId,
                    ),
                    old?.revision ?: 0,
                )
                if (!accountId.isNullOrBlank())
                    linkDebt(row.id, null, accountId, existingRecord, row.operationId)
            }
        }

    suspend fun linkDebt(
        debtId: String,
        movementId: String?,
        accountId: String,
        existingRecord: String?,
        operation: String,
    ) =
        withContext(Dispatchers.IO) {
            uuid(operation)
            require(existingRecord == null || existingRecord.isNotBlank()) {
                "Choose a transaction to match"
            }
            db.withTransaction {
                val d = requireNotNull(db.debts().thread(debtId))
                require(!d.archived)
                val m = movementId?.let { requireNotNull(db.debts().movement(it)) }
                require(
                    m == null ||
                        m.debtId == debtId &&
                            m.kind == "REPAYMENT" &&
                            db.debts().movement(m.id)?.undoOf == null
                )
                if (m != null)
                    require(
                        db.openHelper.readableDatabase
                            .query("SELECT id FROM debt_movements WHERE undoOf=?", arrayOf(m.id))
                            .use { !it.moveToFirst() }
                    ) {
                        "This repayment was already undone"
                    }
                val incoming = if (m == null) d.direction == "BORROWED" else d.direction == "LENT"
                val amount = if (m == null) d.originalMinor else -m.deltaMinor
                val day = m?.day ?: d.day
                val eventKey = if (m == null) "opening:$debtId" else "movement:${m.id}"
                dao.debtFunding(operation)?.let {
                    require(
                        it.debtId == debtId &&
                            it.movementId == movementId &&
                            db.ledger().record(it.recordId)?.accountId == accountId &&
                            (existingRecord == null || it.recordId == existingRecord)
                    )
                    return@withTransaction
                }
                require(
                    (if (m == null) dao.openingFunding(debtId) else dao.movementFunding(m.id)) ==
                        null
                ) {
                    "This opening or repayment is already linked"
                }
                val account = requireNotNull(db.ledger().account(accountId))
                require(
                    !account.archived &&
                        account.type != "CREDIT_CARD" &&
                        account.currency == d.currency
                ) {
                    "Choose an active cash, bank or wallet account in ${d.currency}"
                }
                require(day >= account.openingDate && day <= LocalDate.now().toEpochDay()) {
                    "Use a real movement date on or after account opening"
                }
                val recordId =
                    if (!existingRecord.isNullOrBlank()) {
                        val r = requireNotNull(db.ledger().record(existingRecord))
                        require(db.extras().splits(r.id).isEmpty()) {
                            "Remove category splits before matching loan principal"
                        }
                        require(
                            !r.deleted &&
                                r.linkId == null &&
                                r.accountId == accountId &&
                                r.amountMinor == amount &&
                                r.day == day &&
                                r.type == if (incoming) "INCOME" else "EXPENSE"
                        ) {
                            "Match the same account, amount, date and direction"
                        }
                        require(
                            dao.debtRecord(r.id) == null &&
                                db.groups().link(r.id) == null &&
                                db.obligations().paymentForRecord(r.id) == null &&
                                db.obligations().plans().first().none { it.purchaseId == r.id }
                        ) {
                            "Transaction is already linked to another feature"
                        }
                        r.id
                    } else {
                        val id =
                            UUID.nameUUIDFromBytes("debt-post:$operation".toByteArray()).toString()
                        ledger.postDebt(
                            id,
                            accountId,
                            amount,
                            day,
                            incoming,
                            if (m == null) "${d.person} · loan opening"
                            else "${d.person} · repayment",
                        )
                        id
                    }
                dao.insert(
                    DebtFunding(
                        operation,
                        debtId,
                        movementId,
                        recordId,
                        existingRecord.isNullOrBlank(),
                        true,
                        recordId,
                        eventKey,
                    )
                )
            }
        }

    suspend fun repay(
        id: String,
        expected: Long,
        amount: Long,
        day: Long,
        note: String,
        accountId: String?,
        recordId: String?,
        operation: String,
    ) =
        withContext(Dispatchers.IO) {
            require(accountId == null || accountId.isNotBlank()) {
                "Choose an account for this repayment"
            }
            require(recordId == null || recordId.isNotBlank()) { "Choose a transaction to match" }
            db.withTransaction {
                debts.move(id, expected, -amount, "REPAYMENT", day, note, operation)
                if (!accountId.isNullOrBlank())
                    linkDebt(id, operation, accountId, recordId, operation)
            }
        }

    private suspend fun releaseFunding(link: DebtFunding) {
        dao.update(
            link.copy(
                active = false,
                activeRecordKey = "void:${link.id}",
                eventKey = "void:${link.id}",
            )
        )
        if (link.createdRecord) {
            val r = requireNotNull(db.ledger().record(link.recordId))
            if (!r.deleted) {
                if (r.type == "DEBT_IN")
                    require(
                        db.ledger()
                            .accounts()
                            .first()
                            .first { it.account.id == r.accountId }
                            .balanceMinor >= r.amountMinor
                    ) {
                        "Incoming loan funds have been spent; keep the record and make a correcting movement"
                    }
                ledger.setDeleted(
                    UUID.nameUUIDFromBytes("debt-release:${link.id}".toByteArray()).toString(),
                    r.id,
                    r.revision,
                    true,
                )
            }
        }
    }

    suspend fun unlinkOpening(id: String) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val link = requireNotNull(dao.openingFunding(id)) { "No opening movement linked" }
                releaseFunding(link)
            }
        }

    suspend fun undoDebt(id: String, expected: Long, operation: String) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                db.debts().movement(operation)?.let {
                    require(it.debtId == id && it.undoOf != null)
                    return@withTransaction
                }
                val last = requireNotNull(db.debts().last(id)) { "Nothing to undo" }
                dao.movementFunding(last.id)?.let { releaseFunding(it) }
                debts.undo(id, expected, operation)
            }
        }

    suspend fun period(row: PeriodBudget, expected: Long) =
        withContext(Dispatchers.IO) {
            uuid(row.id)
            uuid(row.operationId)
            require(
                row.name.trim().length in 1..80 &&
                    row.kind in listOf("CUSTOM", "SALARY") &&
                    row.currency in Money.currencies &&
                    row.limitMinor in 1..Money.MAX_MINOR
            )
            date(row.startDay)
            BudgetPeriods.thresholds(row.thresholds)
            if (row.kind == "CUSTOM") {
                val end = requireNotNull(row.endDay)
                date(end)
                require(end >= row.startDay && end - row.startDay <= 3660)
                require(row.salaryDay == 0)
            } else {
                require(row.endDay == null && row.salaryDay in 1..31)
                val start = LocalDate.ofEpochDay(row.startDay)
                require(start.dayOfMonth == minOf(row.salaryDay, start.lengthOfMonth())) {
                    "Start on the salary day, clamped to month-end"
                }
            }
            db.withTransaction {
                row.categoryId?.let {
                    val c = requireNotNull(db.ledger().category(it))
                    require(c.type == "EXPENSE" && !c.archived)
                }
                val old = dao.budget(row.id)
                val key = BudgetPeriods.scope(row)
                if (old?.operationId == row.operationId) {
                    require(old.copy(revision = row.revision) == row.copy(scopeKey = key))
                    return@withTransaction
                }
                require((old?.revision ?: 0) == expected) { "Budget changed; reopen" }
                require(dao.budgets().first().none { it.id != row.id && it.scopeKey == key }) {
                    "This budget range/category already exists"
                }
                dao.put(row.copy(name = row.name.trim(), scopeKey = key, revision = expected + 1))
            }
        }

    suspend fun archiveBudget(id: String, expected: Long, value: Boolean) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val old = requireNotNull(dao.budget(id))
                require(old.revision == expected)
                dao.put(old.copy(archived = value, revision = expected + 1))
            }
        }

    suspend fun monthly(
        month: String,
        currency: String,
        category: String?,
        amount: Long,
        expected: Long,
        operation: String,
        alerts: Boolean,
        thresholds: String,
    ) =
        withContext(Dispatchers.IO) {
            BudgetPeriods.thresholds(thresholds)
            db.withTransaction {
                plans.budget(month, currency, category, amount, expected, operation)
                val id = "$month:$currency:${category?.let{"category:$it"}?:"all"}"
                dao.put(BudgetAlertRule(id, alerts, thresholds))
            }
        }

    suspend fun settings(row: PlanningSettings) =
        withContext(Dispatchers.IO) {
            BudgetPeriods.thresholds(row.thresholds)
            dao.put(row)
        }

    suspend fun goal(
        id: String,
        name: String,
        currency: String,
        target: Long,
        expected: Long,
        operation: String,
        deadline: Long?,
        reminders: Boolean,
        offsets: String,
    ) =
        withContext(Dispatchers.IO) {
            deadline?.let { date(it) }
            PlanDates.offsets(offsets)
            db.withTransaction {
                plans.goal(id, name, currency, target, expected, operation)
                val old = dao.goalPreference(id)
                if (old?.operationId == operation) {
                    require(
                        old.deadlineDay == deadline &&
                            old.reminders == reminders &&
                            old.offsets == offsets
                    )
                    return@withTransaction
                }
                dao.put(
                    GoalPreferences(
                        id,
                        deadline,
                        reminders,
                        offsets,
                        (old?.revision ?: 0) + 1,
                        operation,
                    )
                )
            }
        }

    suspend fun contribution(
        goalId: String,
        expected: Long,
        delta: Long,
        accountId: String?,
        otherAccountId: String?,
        day: Long,
        operation: String,
    ) =
        withContext(Dispatchers.IO) {
            uuid(operation)
            date(day)
            require(delta != 0L && delta in -Money.MAX_MINOR..Money.MAX_MINOR)
            db.withTransaction {
                db.plans().movement(operation)?.let {
                    require(
                        it.goalId == goalId &&
                            it.deltaMinor == delta &&
                            it.undoOf == null &&
                            dao.goalFunding(operation)?.accountId == accountId
                    )
                    return@withTransaction
                }
                val g = requireNotNull(db.plans().goal(goalId))
                require(!g.archived && g.revision == expected) { "Goal changed; reopen" }
                val next = Math.addExact(g.savedMinor, delta)
                require(next in 0..Money.MAX_MINOR)
                val reservations = dao.reservations().first()
                val linked = reservations.filter { it.goalId == goalId }.sumOf { it.amountMinor }
                if (accountId == null) {
                    require(otherAccountId == null)
                    require(next >= linked) {
                        "Choose the funding account when withdrawing account-backed progress"
                    }
                }
                var transfer: String? = null
                if (accountId != null) {
                    val a =
                        requireNotNull(
                            db.ledger().accounts().first().firstOrNull {
                                it.account.id == accountId
                            }
                        )
                    require(
                        !a.account.archived &&
                            a.account.type != "CREDIT_CARD" &&
                            a.account.currency == g.currency
                    ) {
                        "Use an active money account in ${g.currency}"
                    }
                    val held =
                        reservations.filter { it.accountId == accountId }.sumOf { it.amountMinor }
                    val goalHeld =
                        reservations
                            .filter { it.accountId == accountId && it.goalId == goalId }
                            .sumOf { it.amountMinor }
                    if (delta < 0)
                        require(-delta <= goalHeld) {
                            "Withdrawal exceeds this goal’s allocation in that account"
                        }
                    if (otherAccountId != null) {
                        require(day <= LocalDate.now().toEpochDay())
                        transfer =
                            UUID.nameUUIDFromBytes("goal-transfer:$operation".toByteArray())
                                .toString()
                        if (delta > 0) {
                            val source =
                                requireNotNull(
                                    db.ledger().accounts().first().firstOrNull {
                                        it.account.id == otherAccountId
                                    }
                                )
                            val otherHeld =
                                reservations
                                    .filter { it.accountId == otherAccountId }
                                    .sumOf { it.amountMinor }
                            require(source.balanceMinor - otherHeld >= delta) {
                                "Source funds are already earmarked"
                            }
                            ledger.transfer(transfer, otherAccountId, accountId, delta, day)
                        } else {
                            require(a.balanceMinor - (held - goalHeld) >= -delta) {
                                "Account cannot cover this transfer while preserving other goals"
                            }
                            ledger.transfer(transfer, accountId, otherAccountId, -delta, day)
                        }
                    }
                    val balance =
                        db.ledger()
                            .accounts()
                            .first()
                            .first { it.account.id == accountId }
                            .balanceMinor
                    if (delta > 0)
                        require(balance - held >= delta) {
                            "This account’s available funds are already allocated or insufficient"
                        }
                }
                val time =
                    maxOf(
                        System.currentTimeMillis(),
                        db.openHelper.readableDatabase
                            .query(
                                "SELECT COALESCE(MAX(createdAt),0)+1 FROM goal_movements WHERE goalId=?",
                                arrayOf(goalId),
                            )
                            .use {
                                it.moveToFirst()
                                it.getLong(0)
                            },
                    )
                db.plans().insert(GoalMovement(operation, goalId, delta, time))
                accountId?.let { dao.insert(GoalFunding(operation, it, transfer)) }
                db.plans().put(g.copy(savedMinor = next, revision = g.revision + 1))
            }
        }

    suspend fun undoGoal(id: String, expected: Long, operation: String) =
        withContext(Dispatchers.IO) {
            uuid(operation)
            db.withTransaction {
                db.plans().movement(operation)?.let {
                    require(it.goalId == id && it.undoOf != null)
                    return@withTransaction
                }
                val g = requireNotNull(db.plans().goal(id))
                require(!g.archived && g.revision == expected)
                val last = requireNotNull(db.plans().lastUndoable(id)) { "Nothing to undo" }
                val link = dao.goalFunding(last.id)
                val next = Math.subtractExact(g.savedMinor, last.deltaMinor)
                require(next in 0..Money.MAX_MINOR)
                if (link?.transferId != null && link.transferActive) {
                    val pair = db.ledger().linked(link.transferId)
                    val incoming = requireNotNull(pair.firstOrNull { it.type == "TRANSFER_IN" })
                    require(
                        db.ledger()
                            .accounts()
                            .first()
                            .first { it.account.id == incoming.accountId }
                            .balanceMinor >= incoming.amountMinor
                    ) {
                        "Transferred funds have been spent; keep history and record a withdrawal instead"
                    }
                    dao.update(link.copy(transferActive = false))
                    val row = requireNotNull(db.ledger().record(link.transferId))
                    ledger.setDeleted(
                        UUID.nameUUIDFromBytes("goal-undo:$operation".toByteArray()).toString(),
                        row.id,
                        row.revision,
                        true,
                    )
                }
                if (link != null && last.deltaMinor < 0) {
                    val held =
                        dao.reservations()
                            .first()
                            .filter { it.accountId == link.accountId }
                            .sumOf { it.amountMinor }
                    val balance =
                        db.ledger()
                            .accounts()
                            .first()
                            .first { it.account.id == link.accountId }
                            .balanceMinor
                    require(balance - held >= -last.deltaMinor) {
                        "Cannot re-reserve funds that are no longer available"
                    }
                }
                val time = maxOf(System.currentTimeMillis(), last.createdAt + 1)
                db.plans().insert(GoalMovement(operation, id, -last.deltaMinor, time, last.id))
                if (link != null) dao.insert(GoalFunding(operation, link.accountId, null, false))
                db.plans().put(g.copy(savedMinor = next, revision = g.revision + 1))
            }
        }
}
