package app.traxos.finance.data

import androidx.room.*
import app.traxos.finance.domain.Money
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Private IOUs only: positive outstanding is owed in the thread's fixed direction. No synthetic
 * cash expense/income or extra credit-card asset is created.
 */
@Entity(tableName = "debt_threads")
data class DebtThread(
    @PrimaryKey val id: String,
    val person: String,
    val direction: String,
    val currency: String,
    val originalMinor: Long,
    val outstandingMinor: Long,
    val day: Long,
    val dueDay: Long?,
    val note: String,
    val archived: Boolean = false,
    val revision: Long = 1,
    val operationId: String,
)

@Entity(
    tableName = "debt_movements",
    foreignKeys =
        [
            ForeignKey(
                entity = DebtThread::class,
                parentColumns = ["id"],
                childColumns = ["debtId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices = [Index(value = ["debtId", "createdAt"]), Index(value = ["undoOf"], unique = true)],
)
data class DebtMovement(
    @PrimaryKey val id: String,
    val debtId: String,
    val deltaMinor: Long,
    val kind: String,
    val day: Long,
    val note: String,
    val createdAt: Long,
    val undoOf: String? = null,
)

@Dao
interface DebtsDao {
    @Query("SELECT * FROM debt_threads ORDER BY archived,person,id")
    fun threads(): kotlinx.coroutines.flow.Flow<List<DebtThread>>

    @Query("SELECT * FROM debt_threads WHERE id=:id") suspend fun thread(id: String): DebtThread?

    @Query(
        "SELECT * FROM debt_movements WHERE debtId=:id AND undoOf IS NULL AND NOT EXISTS(SELECT 1 FROM debt_movements u WHERE u.undoOf=debt_movements.id) ORDER BY createdAt DESC,id DESC LIMIT 1"
    )
    fun undoable(id: String): kotlinx.coroutines.flow.Flow<DebtMovement?>

    @Query("SELECT COUNT(*) FROM debt_threads") suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(createdAt),0) FROM debt_movements WHERE debtId=:id")
    suspend fun latestTime(id: String): Long

    @Upsert suspend fun put(row: DebtThread)

    @Insert suspend fun insert(row: DebtMovement)

    @Query("SELECT * FROM debt_movements WHERE id=:id")
    suspend fun movement(id: String): DebtMovement?

    @Query(
        "SELECT * FROM debt_movements WHERE debtId=:id ORDER BY createdAt DESC,id DESC LIMIT 100"
    )
    fun history(id: String): kotlinx.coroutines.flow.Flow<List<DebtMovement>>

    @Query(
        "SELECT * FROM debt_movements WHERE debtId=:id AND undoOf IS NULL AND NOT EXISTS(SELECT 1 FROM debt_movements u WHERE u.undoOf=debt_movements.id) ORDER BY createdAt DESC,id DESC LIMIT 1"
    )
    suspend fun last(id: String): DebtMovement?
}

class DebtsRepository(val db: TraxosDatabase) {
    val threads = db.debts().threads()

    private fun uuid(value: String) {
        UUID.fromString(value)
    }

    private fun date(day: Long) {
        require(LocalDate.ofEpochDay(day).year in 1980..2200) { "Use a date between 1980 and 2200" }
    }

    suspend fun save(row: DebtThread, expected: Long) =
        withContext(Dispatchers.IO) {
            uuid(row.id)
            uuid(row.operationId)
            require(row.person.trim().length in 1..80) {
                "Enter a person name (up to 80 characters)"
            }
            require(row.direction in listOf("LENT", "BORROWED") && row.currency in Money.currencies)
            require(row.originalMinor in 1..Money.MAX_MINOR) { "Enter a positive opening amount" }
            require(row.note.length <= 500)
            date(row.day)
            row.dueDay?.let {
                date(it)
                require(it >= row.day) { "Due date must follow the opening date" }
            }
            db.withTransaction {
                val old = db.debts().thread(row.id)
                require(old != null || db.debts().count() < 10000) { "Supported IOU limit reached" }
                if (old?.operationId == row.operationId) {
                    require(
                        old.person == row.person.trim() &&
                            old.note == row.note &&
                            old.dueDay == row.dueDay &&
                            old.originalMinor == row.originalMinor &&
                            old.currency == row.currency &&
                            old.direction == row.direction &&
                            old.day == row.day
                    ) {
                        "Operation already used for different details"
                    }
                    return@withTransaction
                }
                require((old?.revision ?: 0) == expected) { "Debt changed; reopen before saving" }
                require(
                    old == null ||
                        old.direction == row.direction &&
                            old.currency == row.currency &&
                            old.originalMinor == row.originalMinor &&
                            old.day == row.day
                ) {
                    "Opening amount, currency and direction cannot change; use an adjustment"
                }
                db.debts()
                    .put(
                        row.copy(
                            person = row.person.trim(),
                            outstandingMinor = old?.outstandingMinor ?: row.originalMinor,
                            archived = old?.archived ?: false,
                            revision = expected + 1,
                        )
                    )
            }
        }

    suspend fun move(
        id: String,
        expected: Long,
        delta: Long,
        kind: String,
        day: Long,
        note: String,
        operation: String,
    ) =
        withContext(Dispatchers.IO) {
            uuid(operation)
            date(day)
            require(kind in listOf("REPAYMENT", "INCREASE", "DECREASE"))
            require(delta != 0L && delta in -Money.MAX_MINOR..Money.MAX_MINOR)
            require((kind == "INCREASE" && delta > 0) || (kind != "INCREASE" && delta < 0))
            require(note.length <= 500)
            db.withTransaction {
                db.debts().movement(operation)?.let {
                    require(
                        it.debtId == id &&
                            it.deltaMinor == delta &&
                            it.kind == kind &&
                            it.day == day &&
                            it.note == note
                    ) {
                        "Operation already used"
                    }
                    return@withTransaction
                }
                val old = requireNotNull(db.debts().thread(id))
                require(!old.archived && old.revision == expected) {
                    "Debt changed or archived; reopen it"
                }
                require(day >= old.day) { "Movement cannot precede the opening date" }
                val next = Math.addExact(old.outstandingMinor, delta)
                require(next in 0..Money.MAX_MINOR) {
                    "Amount exceeds the outstanding balance or supported limit"
                }
                db.debts()
                    .insert(
                        DebtMovement(
                            operation,
                            id,
                            delta,
                            kind,
                            day,
                            note,
                            maxOf(
                                System.currentTimeMillis(),
                                Math.addExact(db.debts().latestTime(id), 1L),
                            ),
                        )
                    )
                db.debts().put(old.copy(outstandingMinor = next, revision = expected + 1))
            }
        }

    suspend fun undo(id: String, expected: Long, operation: String) =
        withContext(Dispatchers.IO) {
            uuid(operation)
            db.withTransaction {
                db.debts().movement(operation)?.let {
                    require(it.debtId == id && it.undoOf != null)
                    return@withTransaction
                }
                val old = requireNotNull(db.debts().thread(id))
                require(!old.archived && old.revision == expected) {
                    "Debt changed or archived; reopen it"
                }
                val last = requireNotNull(db.debts().last(id)) { "Nothing to undo" }
                val next = Math.subtractExact(old.outstandingMinor, last.deltaMinor)
                require(next in 0..Money.MAX_MINOR)
                db.debts()
                    .insert(
                        DebtMovement(
                            operation,
                            id,
                            -last.deltaMinor,
                            "UNDO",
                            LocalDate.now().toEpochDay(),
                            "",
                            maxOf(
                                System.currentTimeMillis(),
                                Math.addExact(db.debts().latestTime(id), 1L),
                            ),
                            last.id,
                        )
                    )
                db.debts().put(old.copy(outstandingMinor = next, revision = expected + 1))
            }
        }

    suspend fun archive(id: String, expected: Long, archived: Boolean) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val old = requireNotNull(db.debts().thread(id))
                require(old.revision == expected) { "Debt changed; refresh first" }
                require(!archived || old.outstandingMinor == 0L) {
                    "Settle the balance before archiving"
                }
                db.debts().put(old.copy(archived = archived, revision = expected + 1))
            }
        }
}
