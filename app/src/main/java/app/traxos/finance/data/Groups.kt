package app.traxos.finance.data

import androidx.room.*
import app.traxos.finance.domain.Money
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Entity(tableName = "shared_groups")
data class ExpenseGroup(
    @PrimaryKey val id: String,
    val name: String,
    val currency: String,
    val selfId: String,
    val archived: Boolean = false,
    val revision: Long = 1,
    val operationId: String,
)

@Entity(
    tableName = "group_members",
    foreignKeys =
        [
            ForeignKey(
                entity = ExpenseGroup::class,
                parentColumns = ["id"],
                childColumns = ["groupId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices = [Index("groupId")],
)
data class GroupMember(
    @PrimaryKey val id: String,
    val groupId: String,
    val name: String,
    val archived: Boolean = false,
    val revision: Long = 1,
    val operationId: String,
)

@Entity(
    tableName = "group_events",
    foreignKeys =
        [
            ForeignKey(
                entity = ExpenseGroup::class,
                parentColumns = ["id"],
                childColumns = ["groupId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices =
        [
            Index(value = ["groupId", "createdAt"]),
            Index("day"),
            Index(value = ["reversalOf"], unique = true),
        ],
)
data class GroupEvent(
    @PrimaryKey val id: String,
    val groupId: String,
    val kind: String,
    val payerId: String,
    val receiverId: String?,
    val amountMinor: Long,
    val day: Long,
    val title: String,
    val splitMethod: String,
    val createdAt: Long,
    val reversalOf: String? = null,
    val categoryId: String? = null,
)

@Entity(
    tableName = "group_shares",
    primaryKeys = ["eventId", "memberId"],
    foreignKeys =
        [
            ForeignKey(
                entity = GroupEvent::class,
                parentColumns = ["id"],
                childColumns = ["eventId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = GroupMember::class,
                parentColumns = ["id"],
                childColumns = ["memberId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices = [Index("memberId")],
)
data class GroupShare(val eventId: String, val memberId: String, val amountMinor: Long)

@Entity(
    tableName = "group_ledger_links",
    foreignKeys =
        [
            ForeignKey(
                entity = LedgerRecord::class,
                parentColumns = ["id"],
                childColumns = ["recordId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = GroupEvent::class,
                parentColumns = ["id"],
                childColumns = ["eventId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices = [Index(value = ["eventId"], unique = true)],
)
data class GroupLedgerLink(
    @PrimaryKey val recordId: String,
    val eventId: String,
    val reportMinor: Long,
)

data class LocalGroupEvent(
    @Embedded val event: GroupEvent,
    @Relation(parentColumn = "id", entityColumn = "eventId") val shares: List<GroupShare>,
)

data class LocalGroup(
    @Embedded val group: ExpenseGroup,
    @Relation(parentColumn = "id", entityColumn = "groupId") val members: List<GroupMember>,
    @Relation(parentColumn = "id", entityColumn = "groupId", entity = GroupEvent::class)
    val eventRows: List<LocalGroupEvent>,
)

@Dao
interface GroupsDao {
    @Transaction
    @Query("SELECT * FROM shared_groups WHERE id=:id")
    fun snapshot(id: String): kotlinx.coroutines.flow.Flow<LocalGroup?>

    @Query("SELECT * FROM shared_groups ORDER BY archived,name,id")
    fun groups(): kotlinx.coroutines.flow.Flow<List<ExpenseGroup>>

    @Query("SELECT * FROM shared_groups WHERE id=:id") suspend fun group(id: String): ExpenseGroup?

    @Upsert suspend fun put(row: ExpenseGroup)

    @Query("SELECT * FROM group_members WHERE groupId=:id ORDER BY archived,name,id")
    fun members(id: String): kotlinx.coroutines.flow.Flow<List<GroupMember>>

    @Query("SELECT * FROM group_members WHERE id=:id") suspend fun member(id: String): GroupMember?

    @Upsert suspend fun put(row: GroupMember)

    @Query("SELECT * FROM group_events WHERE groupId=:id ORDER BY createdAt DESC,id DESC")
    fun events(id: String): kotlinx.coroutines.flow.Flow<List<GroupEvent>>

    @Query("SELECT * FROM group_events WHERE id=:id") suspend fun event(id: String): GroupEvent?

    @Query("SELECT * FROM group_events WHERE reversalOf=:id")
    suspend fun reversal(id: String): GroupEvent?

    @Query(
        "SELECT * FROM group_shares WHERE eventId IN (SELECT id FROM group_events WHERE groupId=:id)"
    )
    fun shares(id: String): kotlinx.coroutines.flow.Flow<List<GroupShare>>

    @Insert suspend fun insert(row: GroupEvent)

    @Insert suspend fun insert(rows: List<GroupShare>)

    @Insert suspend fun insert(row: GroupLedgerLink)

    @Query("SELECT * FROM group_ledger_links WHERE eventId=:id")
    suspend fun eventLink(id: String): GroupLedgerLink?

    @Query("SELECT COALESCE(MAX(createdAt),0) FROM group_events WHERE groupId=:id")
    suspend fun latestTime(id: String): Long

    @Query("SELECT COUNT(*) FROM group_events WHERE groupId=:id AND reversalOf IS NULL")
    suspend fun eventCount(id: String): Int

    @Query("SELECT * FROM group_ledger_links WHERE recordId=:id")
    suspend fun link(id: String): GroupLedgerLink?

    @Query("DELETE FROM group_ledger_links WHERE eventId=:id") suspend fun unlink(id: String)

    @Query(
        "SELECT * FROM transactions WHERE deleted=0 AND linkId IS NULL AND type IN ('EXPENSE','INCOME') ORDER BY day DESC,id DESC LIMIT 500"
    )
    fun linkCandidates(): kotlinx.coroutines.flow.Flow<List<LedgerRecord>>
}

object GroupMath {
    /**
     * Percentages use exact basis points; shares use positive integer weights. Largest remainder,
     * stable member-ID tie order guarantees total conservation, independent of input map order.
     */
    fun split(
        total: Long,
        method: String,
        values: Map<String, String>,
        currency: String,
    ): Map<String, Long> {
        require(total in 1..Money.MAX_MINOR && values.isNotEmpty() && values.size <= 100)
        val sorted = values.toSortedMap()
        if (method == "EXACT")
            return sorted
                .mapValues { Money.parse(it.value, currency, true) }
                .also {
                    require(it.values.fold(0L, Math::addExact) == total) {
                        "Exact amounts must add up to the total"
                    }
                }
        val weights =
            sorted.mapValues { (_, v) ->
                when (method) {
                    "EQUAL" -> 1L
                    "PERCENT" ->
                        try {
                            BigDecimal(v).movePointRight(2).longValueExact()
                        } catch (e: ArithmeticException) {
                            throw IllegalArgumentException(
                                "Use percentages with up to two decimal places"
                            )
                        }
                    "SHARES" ->
                        v.toLongOrNull()
                            ?: throw IllegalArgumentException("Use whole-number shares")
                    else -> throw IllegalArgumentException("Choose a split method")
                }
            }
        require(weights.values.all { it in 1..1000000 }) {
            "Every selected member needs a positive share"
        }
        val sum = weights.values.sum()
        if (method == "PERCENT") require(sum == 10000L) { "Percentages must total 100" }
        val amounts =
            weights.mapValues { (_, w) -> Math.multiplyExact(total, w) / sum }.toMutableMap()
        val remainder = total - amounts.values.sum()
        weights.keys
            .sortedWith(
                compareByDescending<String> {
                        Math.multiplyExact(total, weights.getValue(it)) % sum
                    }
                    .thenBy { it }
            )
            .take(remainder.toInt())
            .forEach { amounts[it] = amounts.getValue(it) + 1 }
        return amounts
    }

    fun balances(
        members: List<GroupMember>,
        events: List<GroupEvent>,
        shares: List<GroupShare>,
    ): Map<String, Long> {
        val reversed = events.mapNotNull { it.reversalOf }.toSet()
        val totals = members.associate { it.id to 0L }.toMutableMap()
        fun add(id: String, amount: Long) {
            require(id in totals) { "Unknown member" }
            totals[id] = Math.addExact(totals.getValue(id), amount)
        }
        events
            .filter { it.reversalOf == null && it.id !in reversed }
            .forEach { e ->
                if (e.kind == "EXPENSE") {
                    add(e.payerId, e.amountMinor)
                    val allocations = shares.filter { it.eventId == e.id }
                    require(allocations.sumOf { it.amountMinor } == e.amountMinor)
                    allocations.forEach { add(it.memberId, -it.amountMinor) }
                } else if (e.kind == "SETTLEMENT") {
                    add(e.payerId, e.amountMinor)
                    add(requireNotNull(e.receiverId), -e.amountMinor)
                }
            }
        require(totals.values.sum() == 0L)
        return totals
    }
}

class GroupsRepository(val db: TraxosDatabase) {
    val groups = db.groups().groups()

    private fun uuid(id: String) {
        UUID.fromString(id)
    }

    private fun date(day: Long) {
        require(LocalDate.ofEpochDay(day).year in 1980..2200)
    }

    suspend fun create(
        id: String,
        name: String,
        currency: String,
        selfName: String,
        operation: String,
    ) =
        withContext(Dispatchers.IO) {
            uuid(id)
            uuid(operation)
            require(name.trim().length in 1..80 && selfName.trim().length in 1..80)
            require(currency in Money.currencies)
            db.withTransaction {
                val old = db.groups().group(id)
                if (old != null) {
                    require(
                        old.operationId == operation &&
                            old.name == name.trim() &&
                            old.currency == currency
                    )
                    return@withTransaction
                }
                require(db.groups().groups().first().size < 1000)
                val self = UUID.nameUUIDFromBytes("group-self:$id".toByteArray()).toString()
                db.groups()
                    .put(ExpenseGroup(id, name.trim(), currency, self, operationId = operation))
                db.groups().put(GroupMember(self, id, selfName.trim(), operationId = operation))
            }
        }

    suspend fun member(row: GroupMember, expected: Long) =
        withContext(Dispatchers.IO) {
            uuid(row.id)
            uuid(row.operationId)
            require(row.name.trim().length in 1..80)
            db.withTransaction {
                val group = requireNotNull(db.groups().group(row.groupId))
                require(!group.archived)
                val old = db.groups().member(row.id)
                if (old?.operationId == row.operationId) {
                    require(old.name == row.name.trim() && old.archived == row.archived)
                    return@withTransaction
                }
                require(
                    (old?.revision ?: 0) == expected && (old == null || old.groupId == row.groupId)
                ) {
                    "Member changed; reopen"
                }
                val members = db.groups().members(row.groupId).first()
                require(old != null || members.size < 100)
                require(members.none { it.id != row.id && it.name.equals(row.name.trim(), true) }) {
                    "Member name already exists"
                }
                require(!row.archived || row.id != group.selfId) {
                    "Your own member cannot be archived"
                }
                if (row.archived)
                    require(
                        GroupMath.balances(
                                members,
                                db.groups().events(group.id).first(),
                                db.groups().shares(group.id).first(),
                            )[row.id] == 0L
                    ) {
                        "Settle the member before archiving"
                    }
                db.groups().put(row.copy(name = row.name.trim(), revision = expected + 1))
            }
        }

    suspend fun expense(
        groupId: String,
        payer: String,
        total: Long,
        method: String,
        values: Map<String, String>,
        day: Long,
        title: String,
        recordId: String?,
        operation: String,
        categoryId: String = "other-expense",
    ) =
        withContext(Dispatchers.IO) {
            uuid(operation)
            date(day)
            require(title.trim().length in 1..120)
            db.withTransaction {
                val g = requireNotNull(db.groups().group(groupId))
                require(!g.archived)
                val category =
                    requireNotNull(db.ledger().category(categoryId)) {
                        "Choose an expense category"
                    }
                require(category.type == "EXPENSE" && !category.archived)
                val allocation = GroupMath.split(total, method, values, g.currency)
                db.groups().event(operation)?.let { old ->
                    require(
                        old.groupId == groupId &&
                            old.payerId == payer &&
                            old.amountMinor == total &&
                            old.kind == "EXPENSE" &&
                            db.groups().eventLink(operation)?.recordId ==
                                recordId?.takeIf { it.isNotBlank() } &&
                            old.categoryId == categoryId &&
                            old.title == title.trim() &&
                            old.day == day &&
                            db.groups()
                                .shares(groupId)
                                .first()
                                .filter { it.eventId == operation }
                                .associate { it.memberId to it.amountMinor } == allocation
                    ) {
                        "Operation already used"
                    }
                    return@withTransaction
                }
                require(db.groups().eventCount(groupId) < 5000) {
                    "Group supports 5000 original events; archive a settled group and start another"
                }
                val active =
                    db.groups().members(groupId).first().filter { !it.archived }.map { it.id }
                require(payer in active && allocation.keys.all { it in active }) {
                    "Choose active group members"
                }
                var link: GroupLedgerLink? = null
                if (!recordId.isNullOrBlank()) {
                    require(payer == g.selfId) { "Only your own payment can link to your ledger" }
                    val record = requireNotNull(db.ledger().record(recordId))
                    require(
                        !record.deleted &&
                            record.linkId == null &&
                            record.type == "EXPENSE" &&
                            record.amountMinor == total &&
                            record.day == day &&
                            db.ledger().account(record.accountId)?.currency == g.currency
                    ) {
                        "Choose a matching expense, currency, date and amount"
                    }
                    require(
                        db.groups().link(recordId) == null &&
                            db.planning().debtRecord(recordId) == null
                    ) {
                        "This transaction is already linked"
                    }
                    require(db.obligations().paymentForRecord(recordId) == null) {
                        "This expense is already a subscription payment"
                    }
                    link = GroupLedgerLink(recordId, operation, 0)
                }
                db.groups()
                    .insert(
                        GroupEvent(
                            operation,
                            groupId,
                            "EXPENSE",
                            payer,
                            null,
                            total,
                            day,
                            title.trim(),
                            method,
                            nextTime(groupId),
                            categoryId = categoryId,
                        )
                    )
                db.groups().insert(allocation.map { GroupShare(operation, it.key, it.value) })
                link?.let { db.groups().insert(it) }
            }
        }

    suspend fun settle(
        groupId: String,
        from: String,
        to: String,
        amount: Long,
        day: Long,
        note: String,
        recordId: String?,
        operation: String,
    ) =
        withContext(Dispatchers.IO) {
            uuid(operation)
            date(day)
            require(amount in 1..Money.MAX_MINOR && from != to && note.length <= 120)
            db.withTransaction {
                val g = requireNotNull(db.groups().group(groupId))
                require(!g.archived)
                db.groups().event(operation)?.let {
                    require(
                        it.groupId == groupId &&
                            it.kind == "SETTLEMENT" &&
                            db.groups().eventLink(operation)?.recordId ==
                                recordId?.takeIf { it.isNotBlank() } &&
                            it.payerId == from &&
                            it.receiverId == to &&
                            it.amountMinor == amount &&
                            it.day == day &&
                            it.title == note
                    )
                    return@withTransaction
                }
                require(db.groups().eventCount(groupId) < 5000) {
                    "Group supports 5000 original events"
                }
                val balance =
                    GroupMath.balances(
                        db.groups().members(groupId).first(),
                        db.groups().events(groupId).first(),
                        db.groups().shares(groupId).first(),
                    )
                val atDate =
                    GroupMath.balances(
                        db.groups().members(groupId).first(),
                        db.groups().events(groupId).first().filter { it.day <= day },
                        db.groups().shares(groupId).first(),
                    )
                require(amount <= -(atDate[from] ?: 0) && amount <= (atDate[to] ?: 0)) {
                    "Settlement date precedes the amount owed"
                }
                require(amount <= -(balance[from] ?: 0) && amount <= (balance[to] ?: 0)) {
                    "Settlement exceeds the current net amount owed"
                }
                var link: GroupLedgerLink? = null
                if (!recordId.isNullOrBlank()) {
                    require(from == g.selfId || to == g.selfId)
                    val row = requireNotNull(db.ledger().record(recordId))
                    val incoming = to == g.selfId
                    require(
                        !row.deleted &&
                            row.linkId == null &&
                            row.type == if (incoming) "INCOME" else "EXPENSE"
                    )
                    require(
                        row.amountMinor == amount &&
                            row.day == day &&
                            db.ledger().account(row.accountId)?.currency == g.currency
                    )
                    require(
                        db.groups().link(recordId) == null &&
                            db.planning().debtRecord(recordId) == null &&
                            db.obligations().paymentForRecord(recordId) == null
                    )
                    link = GroupLedgerLink(recordId, operation, 0)
                }
                db.groups()
                    .insert(
                        GroupEvent(
                            operation,
                            groupId,
                            "SETTLEMENT",
                            from,
                            to,
                            amount,
                            day,
                            note,
                            "",
                            nextTime(groupId),
                        )
                    )
                link?.let { db.groups().insert(it) }
            }
        }

    suspend fun reverse(id: String, operation: String) =
        withContext(Dispatchers.IO) {
            uuid(operation)
            db.withTransaction {
                db.groups().event(operation)?.let {
                    require(it.reversalOf == id)
                    return@withTransaction
                }
                val original = requireNotNull(db.groups().event(id))
                require(original.reversalOf == null && db.groups().reversal(id) == null) {
                    "This event is already reversed"
                }
                val g = requireNotNull(db.groups().group(original.groupId))
                require(!g.archived)
                db.groups()
                    .insert(
                        GroupEvent(
                            operation,
                            g.id,
                            "REVERSAL",
                            original.payerId,
                            original.receiverId,
                            original.amountMinor,
                            LocalDate.now().toEpochDay(),
                            "Reversed: ${original.title}",
                            "",
                            nextTime(g.id),
                            id,
                        )
                    )
                db.groups().unlink(id)
            }
        }

    suspend fun rename(id: String, expected: Long, name: String, operation: String) =
        withContext(Dispatchers.IO) {
            uuid(operation)
            require(name.trim().length in 1..80)
            db.withTransaction {
                val g = requireNotNull(db.groups().group(id))
                if (g.operationId == operation) {
                    require(g.name == name.trim())
                    return@withTransaction
                }
                require(g.revision == expected && !g.archived)
                db.groups()
                    .put(
                        g.copy(
                            name = name.trim(),
                            revision = g.revision + 1,
                            operationId = operation,
                        )
                    )
            }
        }

    suspend fun archive(id: String, expected: Long, value: Boolean) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val g = requireNotNull(db.groups().group(id))
                require(g.revision == expected)
                if (value)
                    require(
                        GroupMath.balances(
                                db.groups().members(id).first(),
                                db.groups().events(id).first(),
                                db.groups().shares(id).first(),
                            )
                            .values
                            .all { it == 0L }
                    ) {
                        "Settle all balances before archiving"
                    }
                db.groups().put(g.copy(archived = value, revision = expected + 1))
            }
        }

    private suspend fun nextTime(id: String) =
        maxOf(System.currentTimeMillis(), db.groups().latestTime(id) + 1)
}
