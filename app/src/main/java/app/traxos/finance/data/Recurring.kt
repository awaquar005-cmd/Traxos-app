package app.traxos.finance.data

import androidx.room.*
import app.traxos.finance.domain.Money
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Entity(
    tableName = "recurring_rules",
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
    indices =
        [Index("accountId"), Index("categoryId"), Index(value = ["enabled", "deleted", "nextDay"])],
)
data class RecurringRule(
    @PrimaryKey val id: String,
    val accountId: String,
    val categoryId: String,
    val type: String,
    val amountMinor: Long,
    val note: String,
    val frequency: String,
    val anchorDay: Int,
    val nextDay: Long,
    val enabled: Boolean = true,
    val deleted: Boolean = false,
    val revision: Long = 1,
    val lastError: String = "",
    val operationId: String,
)

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_rules WHERE deleted=0 ORDER BY nextDay,id")
    fun observe(): Flow<List<RecurringRule>>

    @Query("SELECT * FROM recurring_rules WHERE id=:id") suspend fun get(id: String): RecurringRule?

    @Query(
        "SELECT * FROM recurring_rules WHERE enabled=1 AND deleted=0 AND lastError='' AND nextDay<=:day ORDER BY nextDay,id LIMIT 100"
    )
    suspend fun due(day: Long): List<RecurringRule>

    @Upsert suspend fun put(rule: RecurringRule)

    @Query("UPDATE recurring_rules SET lastError=:message WHERE id=:id")
    suspend fun error(id: String, message: String)
}

object RecurringDates {
    val frequencies = listOf("DAILY", "WEEKLY", "MONTHLY")

    fun next(day: Long, frequency: String, anchor: Int): Long {
        val date = LocalDate.ofEpochDay(day)
        return when (frequency) {
            "DAILY" -> date.plusDays(1)
            "WEEKLY" -> date.plusWeeks(1)
            "MONTHLY" ->
                date.withDayOfMonth(1).plusMonths(1).let {
                    it.withDayOfMonth(minOf(anchor, it.lengthOfMonth()))
                }
            else -> throw IllegalArgumentException("Choose daily, weekly or monthly")
        }.toEpochDay()
    }

    fun occurrence(id: String, day: Long) =
        UUID.nameUUIDFromBytes("recurring:$id:$day".toByteArray(Charsets.UTF_8)).toString()
}

class RecurringRepository(val db: TraxosDatabase) {
    // This private ledger is only entered inside the database transaction: no UI gate/DB inversion.
    private val ledger = LedgerRepository(db)
    val rules = db.recurring().observe()

    suspend fun save(
        rule: RecurringRule,
        expected: Long,
        today: Long = LocalDate.now().toEpochDay(),
    ) =
        withContext(Dispatchers.IO) {
            UUID.fromString(rule.id)
            UUID.fromString(rule.operationId)
            require(rule.frequency in RecurringDates.frequencies && rule.anchorDay in 1..31) {
                "Choose a valid schedule"
            }
            require(
                rule.nextDay >= today &&
                    rule.nextDay <= LocalDate.ofEpochDay(today).plusYears(10).toEpochDay()
            ) {
                "Next date must be today or within the next 10 years"
            }
            require(rule.note.trim().length in 1..120) { "Enter a description (1–120 characters)" }
            require(rule.amountMinor in 1..Money.MAX_MINOR) {
                "Enter a positive amount within the supported limit"
            }
            require(rule.type in setOf("EXPENSE", "INCOME")) { "Choose expense or income" }
            db.withTransaction {
                val old = db.recurring().get(rule.id)
                if (old?.operationId == rule.operationId) return@withTransaction
                require((old?.revision ?: 0) == expected && old?.deleted != true) {
                    "Schedule changed; reopen it before saving"
                }
                val account =
                    requireNotNull(db.ledger().account(rule.accountId)) { "Choose an account" }
                require(!account.archived) { "Choose an active account" }
                require(account.type != "CREDIT_CARD" || rule.type == "EXPENSE") {
                    "Credit cards support recurring purchases, not income or repayments"
                }
                val category =
                    requireNotNull(db.ledger().category(rule.categoryId)) { "Choose a category" }
                require(!category.archived && category.type == rule.type) {
                    "Choose an active matching category"
                }
                db.recurring()
                    .put(
                        rule.copy(note = rule.note.trim(), revision = expected + 1, lastError = "")
                    )
            }
        }

    suspend fun state(id: String, revision: Long, enabled: Boolean, delete: Boolean = false) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val old = requireNotNull(db.recurring().get(id))
                require(old.revision == revision && !old.deleted) {
                    "Schedule changed; refresh before trying again"
                }
                db.recurring()
                    .put(
                        old.copy(
                            enabled = enabled,
                            deleted = delete,
                            revision = revision + 1,
                            lastError = "",
                        )
                    )
            }
        }

    /** Atomic occurrence + schedule advance. Failed entries keep their due date for correction. */
    suspend fun runDue(today: Long = LocalDate.now().toEpochDay(), limit: Int = 100): Boolean =
        withContext(Dispatchers.IO) {
            require(limit in 1..100)
            var posted = 0
            val blocked = mutableSetOf<String>()
            while (posted < limit) {
                val candidates = db.recurring().due(today).filter { it.id !in blocked }
                if (candidates.isEmpty()) break
                for (candidate in candidates) {
                    if (posted >= limit) break
                    try {
                        db.withTransaction {
                            val rule = db.recurring().get(candidate.id) ?: return@withTransaction
                            if (!rule.enabled || rule.deleted || rule.nextDay > today)
                                return@withTransaction
                            val occurrence = RecurringDates.occurrence(rule.id, rule.nextDay)
                            ledger.saveRecord(
                                occurrence,
                                occurrence,
                                rule.accountId,
                                rule.categoryId,
                                rule.type,
                                rule.amountMinor,
                                rule.nextDay,
                                rule.note,
                            )
                            db.recurring()
                                .put(
                                    rule.copy(
                                        nextDay =
                                            RecurringDates.next(
                                                rule.nextDay,
                                                rule.frequency,
                                                rule.anchorDay,
                                            ),
                                        revision = rule.revision + 1,
                                        lastError = "",
                                    )
                                )
                        }
                        posted++
                    } catch (e: IllegalArgumentException) {
                        blocked += candidate.id
                        db.recurring()
                            .error(
                                candidate.id,
                                "Not posted: check active account/category, amount and opening date. Edit this schedule to retry.",
                            )
                    }
                }
            }
            db.recurring().due(today).any { it.id !in blocked }
        }
}
