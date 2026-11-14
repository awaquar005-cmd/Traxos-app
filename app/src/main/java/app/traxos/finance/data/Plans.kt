package app.traxos.finance.data

import androidx.room.*
import app.traxos.finance.domain.Money
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Entity(
    tableName = "budgets",
    foreignKeys =
        [
            ForeignKey(
                entity = Category::class,
                parentColumns = ["id"],
                childColumns = ["categoryId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices = [Index("categoryId"), Index(value = ["month", "currency"])],
)
data class Budget(
    @PrimaryKey val id: String,
    val month: String,
    val currency: String,
    val categoryId: String?,
    val limitMinor: Long,
    val revision: Long = 1,
    val operationId: String,
)

@Entity(tableName = "savings_goals")
data class SavingsGoal(
    @PrimaryKey val id: String,
    val name: String,
    val currency: String,
    val targetMinor: Long,
    val savedMinor: Long = 0,
    val archived: Boolean = false,
    val revision: Long = 1,
    val operationId: String,
)

@Entity(
    tableName = "goal_movements",
    foreignKeys =
        [
            ForeignKey(
                entity = SavingsGoal::class,
                parentColumns = ["id"],
                childColumns = ["goalId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices = [Index(value = ["goalId", "createdAt"]), Index(value = ["undoOf"], unique = true)],
)
data class GoalMovement(
    @PrimaryKey val id: String,
    val goalId: String,
    val deltaMinor: Long,
    val createdAt: Long,
    val undoOf: String? = null,
)

@Dao
interface PlansDao {
    @Query("SELECT * FROM budgets ORDER BY month DESC,currency,id")
    fun budgets(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE id=:id") suspend fun budget(id: String): Budget?

    @Upsert suspend fun put(row: Budget)

    @Query("DELETE FROM budgets WHERE id=:id AND revision=:revision")
    suspend fun removeBudget(id: String, revision: Long): Int

    @Query("SELECT * FROM savings_goals ORDER BY archived,name,id")
    fun goals(): Flow<List<SavingsGoal>>

    @Query("SELECT * FROM savings_goals WHERE id=:id") suspend fun goal(id: String): SavingsGoal?

    @Upsert suspend fun put(row: SavingsGoal)

    @Query("SELECT * FROM goal_movements WHERE goalId=:id ORDER BY createdAt DESC,id DESC LIMIT 50")
    fun movements(id: String): Flow<List<GoalMovement>>

    @Query("SELECT * FROM goal_movements WHERE id=:id")
    suspend fun movement(id: String): GoalMovement?

    @Query("SELECT COUNT(*) FROM goal_movements WHERE goalId=:id")
    suspend fun movementCount(id: String): Int

    @Insert suspend fun insert(row: GoalMovement)

    @Query(
        "SELECT * FROM goal_movements WHERE goalId=:id AND undoOf IS NULL AND NOT EXISTS(SELECT 1 FROM goal_movements u WHERE u.undoOf=goal_movements.id) ORDER BY createdAt DESC,id DESC LIMIT 1"
    )
    suspend fun lastUndoable(id: String): GoalMovement?
}

class PlansRepository(val db: TraxosDatabase) {
    val budgets = db.plans().budgets()
    val goals = db.plans().goals()

    suspend fun budget(
        month: String,
        currency: String,
        category: String?,
        amount: Long,
        expected: Long,
        operation: String,
    ) =
        withContext(Dispatchers.IO) {
            UUID.fromString(operation)
            require(currency in Money.currencies)
            require(amount in 1..Money.MAX_MINOR) { "Enter a positive budget" }
            val parsed =
                runCatching { YearMonth.parse(month) }
                    .getOrElse { throw IllegalArgumentException("Use YYYY-MM for month") }
            require(parsed.year in 1980..2200)
            val id = "$month:$currency:${category?.let { "category:$it" } ?: "all"}"
            db.withTransaction {
                val old = db.plans().budget(id)
                if (old?.operationId == operation) return@withTransaction
                require((old?.revision ?: 0) == expected) {
                    "Budget exists or changed; open Edit before saving"
                }
                if (category != null) {
                    val c = requireNotNull(db.ledger().category(category))
                    require(c.type == "EXPENSE" && (!c.archived || old?.categoryId == category)) {
                        "Choose an active expense category"
                    }
                }
                db.plans()
                    .put(Budget(id, month, currency, category, amount, expected + 1, operation))
            }
        }

    suspend fun deleteBudget(id: String, revision: Long) =
        withContext(Dispatchers.IO) {
            require(db.plans().removeBudget(id, revision) == 1) {
                "Budget changed; refresh before deleting"
            }
        }

    suspend fun goal(
        id: String,
        name: String,
        currency: String,
        target: Long,
        revision: Long,
        operation: String,
    ) =
        withContext(Dispatchers.IO) {
            UUID.fromString(id)
            UUID.fromString(operation)
            require(name.trim().length in 1..80) { "Enter a goal name (up to 80 characters)" }
            require(currency in Money.currencies)
            require(target in 1..Money.MAX_MINOR)
            db.withTransaction {
                val old = db.plans().goal(id)
                if (old?.operationId == operation) return@withTransaction
                require((old?.revision ?: 0) == revision) { "Goal changed; reopen before saving" }
                require(
                    old == null || old.currency == currency || db.plans().movementCount(id) == 0
                ) {
                    "Currency cannot change after progress has been recorded"
                }
                db.plans()
                    .put(
                        SavingsGoal(
                            id,
                            name.trim(),
                            currency,
                            target,
                            old?.savedMinor ?: 0,
                            old?.archived ?: false,
                            revision + 1,
                            operation,
                        )
                    )
            }
        }

    suspend fun archive(id: String, revision: Long, value: Boolean) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val g = requireNotNull(db.plans().goal(id))
                require(g.revision == revision) { "Goal changed; refresh first" }
                db.plans().put(g.copy(archived = value, revision = revision + 1))
            }
        }

    /** Progress is an earmark, never a cash/bank posting. A later real transfer uses Accounts. */
    suspend fun contribute(id: String, revision: Long, delta: Long, operation: String) =
        withContext(Dispatchers.IO) {
            UUID.fromString(operation)
            require(delta != 0L && delta in -Money.MAX_MINOR..Money.MAX_MINOR)
            db.withTransaction {
                db.plans().movement(operation)?.let { previous ->
                    require(
                        previous.goalId == id &&
                            previous.deltaMinor == delta &&
                            previous.undoOf == null
                    ) {
                        "Operation belongs to another goal"
                    }
                    return@withTransaction
                }
                val g = requireNotNull(db.plans().goal(id))
                require(!g.archived && g.revision == revision) {
                    "Goal changed or archived; refresh first"
                }
                val next = Math.addExact(g.savedMinor, delta)
                if (delta < 0)
                    require(
                        next >=
                            db.planning()
                                .reservations()
                                .first()
                                .filter { it.goalId == id }
                                .sumOf { it.amountMinor }
                    ) {
                        "Select the funding account to withdraw linked progress"
                    }
                require(next in 0..Money.MAX_MINOR) {
                    "Progress cannot be negative or exceed the supported limit"
                }
                db.plans().insert(GoalMovement(operation, id, delta, System.currentTimeMillis()))
                db.plans().put(g.copy(savedMinor = next, revision = revision + 1))
            }
        }

    suspend fun undo(id: String, revision: Long, operation: String) =
        withContext(Dispatchers.IO) {
            UUID.fromString(operation)
            db.withTransaction {
                db.plans().movement(operation)?.let { previous ->
                    require(previous.goalId == id && previous.undoOf != null) {
                        "Operation belongs to another goal"
                    }
                    return@withTransaction
                }
                val g = requireNotNull(db.plans().goal(id))
                require(!g.archived && g.revision == revision) { "Goal changed; refresh first" }
                val previous =
                    requireNotNull(db.plans().lastUndoable(id)) { "No progress entry to undo" }
                require(db.planning().goalFunding(previous.id) == null) {
                    "Use linked Undo from Plans"
                }
                val next = Math.subtractExact(g.savedMinor, previous.deltaMinor)
                require(next in 0..Money.MAX_MINOR)
                db.plans()
                    .insert(
                        GoalMovement(
                            operation,
                            id,
                            -previous.deltaMinor,
                            System.currentTimeMillis(),
                            previous.id,
                        )
                    )
                db.plans().put(g.copy(savedMinor = next, revision = revision + 1))
            }
        }
}
