package app.traxos.finance.entry

import androidx.room.*
import app.traxos.finance.data.*
import app.traxos.finance.domain.Money
import java.text.Normalizer
import java.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@Entity(
    tableName = "transaction_splits",
    primaryKeys = ["recordId", "categoryId"],
    foreignKeys =
        [
            ForeignKey(
                entity = LedgerRecord::class,
                parentColumns = ["id"],
                childColumns = ["recordId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = Category::class,
                parentColumns = ["id"],
                childColumns = ["categoryId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices = [Index("categoryId")],
)
data class TransactionSplit(val recordId: String, val categoryId: String, val amountMinor: Long)

data class SplitPart(val categoryId: String, val amountMinor: Long)

@Entity(
    tableName = "receipt_attachments",
    foreignKeys =
        [
            ForeignKey(
                entity = LedgerRecord::class,
                parentColumns = ["id"],
                childColumns = ["recordId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices = [Index(value = ["recordId", "digest"], unique = true)],
)
data class ReceiptAttachment(
    @PrimaryKey val id: String,
    val recordId: String,
    val name: String,
    val mime: String,
    val digest: String,
    val secret: ByteArray,
    val createdAt: Long,
    val deleted: Boolean = false,
    val revision: Long = 1,
)

@Entity(
    tableName = "receipt_drafts",
    indices = [Index(value = ["ownerId", "digest"], unique = true)],
)
data class ReceiptDraft(
    @PrimaryKey val id: String,
    val ownerId: String,
    val name: String,
    val mime: String,
    val digest: String,
    val secret: ByteArray,
    val createdAt: Long,
)

data class ReceiptMeta(
    val id: String,
    val name: String,
    val mime: String,
    val createdAt: Long,
    val deleted: Boolean,
    val revision: Long,
)

@Entity(
    tableName = "entry_presets",
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
            ForeignKey(
                entity = EntryLabel::class,
                parentColumns = ["id"],
                childColumns = ["merchantId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices = [Index("accountId"), Index("categoryId"), Index("merchantId")],
)
data class EntryPreset(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val currency: String,
    val amountMinor: Long,
    val accountId: String,
    val categoryId: String,
    val merchantId: String? = null,
    val note: String = "",
    val favorite: Boolean = true,
    val position: Int = 0,
    val archived: Boolean = false,
    val revision: Long = 1,
)

@Entity(
    tableName = "merchant_rules",
    foreignKeys =
        [
            ForeignKey(
                entity = Category::class,
                parentColumns = ["id"],
                childColumns = ["categoryId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = Account::class,
                parentColumns = ["id"],
                childColumns = ["accountId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices =
        [
            Index("categoryId"),
            Index("accountId"),
            Index(value = ["pattern", "mode", "type", "scopeKey"], unique = true),
        ],
)
data class MerchantRule(
    @PrimaryKey val id: String,
    val pattern: String,
    val mode: String,
    val type: String,
    val categoryId: String,
    val accountId: String? = null,
    val scopeKey: String = "all",
    val priority: Int = 0,
    val enabled: Boolean = true,
    val revision: Long = 1,
)

@Entity(tableName = "fast_entry_settings")
data class FastEntrySettings(
    @PrimaryKey val id: Int = 1,
    val rules: Boolean = true,
    val widget1: String = "EXPENSE",
    val widget2: String = "INCOME",
    val widget3: String = "VOICE",
    val tile: Boolean = true,
    val shortcuts: Boolean = true,
    val publicLabels: Boolean = false,
)

@Dao
interface ExtrasDao {
    @Query(
        "SELECT t.*,a.name AS accountName,a.currency,c.name AS categoryName FROM transactions t JOIN accounts a ON a.id=t.accountId JOIN categories c ON c.id=t.categoryId WHERE t.deleted=0 AND (t.note LIKE '%'||:search||'%' OR a.name LIKE '%'||:search||'%' OR t.id=:search OR strftime('%Y-%m-%d',t.day*86400,'unixepoch') LIKE '%'||:search||'%') ORDER BY t.day DESC,t.id DESC LIMIT 50"
    )
    fun receiptRecords(search: String): Flow<List<ReceiptRecordRow>>

    @Query("SELECT * FROM transaction_splits WHERE recordId=:id ORDER BY categoryId")
    suspend fun splits(id: String): List<TransactionSplit>

    @Query("SELECT * FROM transaction_splits WHERE recordId=:id ORDER BY categoryId")
    fun watchSplits(id: String): Flow<List<TransactionSplit>>

    @Query("DELETE FROM transaction_splits WHERE recordId=:id") suspend fun clearSplits(id: String)

    @Insert suspend fun insertSplits(rows: List<TransactionSplit>)

    @Query(
        "SELECT id,name,mime,createdAt,deleted,revision FROM receipt_attachments WHERE recordId=:id ORDER BY createdAt,id"
    )
    fun receipts(id: String): Flow<List<ReceiptMeta>>

    @Query("SELECT * FROM receipt_attachments WHERE id=:id")
    suspend fun receipt(id: String): ReceiptAttachment?

    @Query(
        "SELECT id,name,mime,createdAt,0 AS deleted,1 AS revision FROM receipt_drafts WHERE ownerId=:id ORDER BY createdAt,id"
    )
    fun drafts(id: String): Flow<List<ReceiptMeta>>

    @Query("SELECT * FROM receipt_drafts WHERE ownerId=:id")
    suspend fun draftRows(id: String): List<ReceiptDraft>

    @Query("SELECT * FROM receipt_drafts WHERE id=:id") suspend fun draft(id: String): ReceiptDraft?

    @Insert suspend fun addReceipt(row: ReceiptAttachment)

    @Insert suspend fun addDraft(row: ReceiptDraft)

    @Query("DELETE FROM receipt_drafts WHERE id=:id AND ownerId=:owner")
    suspend fun deleteDraft(id: String, owner: String)

    @Query("DELETE FROM receipt_drafts WHERE ownerId=:id") suspend fun discardDrafts(id: String)

    @Query("DELETE FROM receipt_drafts") suspend fun discardAllDrafts()

    @Query(
        "UPDATE receipt_attachments SET deleted=:deleted,revision=revision+1 WHERE id=:id AND revision=:revision"
    )
    suspend fun receiptDeleted(id: String, revision: Long, deleted: Boolean): Int

    @Query("SELECT COUNT(*) FROM receipt_attachments WHERE recordId=:id")
    suspend fun receiptCount(id: String): Int

    @Query("SELECT COUNT(*) FROM receipt_attachments WHERE recordId=:id AND digest=:digest")
    suspend fun duplicateReceipt(id: String, digest: String): Int

    @Query("SELECT COUNT(*) FROM receipt_drafts WHERE ownerId=:id AND digest=:digest")
    suspend fun duplicateDraft(id: String, digest: String): Int

    @Query("SELECT COUNT(*) FROM receipt_drafts") suspend fun draftCount(): Int

    @Query("SELECT COUNT(*) FROM receipt_attachments") suspend fun allReceiptCount(): Int

    @Query("SELECT * FROM entry_presets ORDER BY archived,position,name,id")
    fun presets(): Flow<List<EntryPreset>>

    @Query("SELECT * FROM entry_presets WHERE id=:id") suspend fun preset(id: String): EntryPreset?

    @Upsert suspend fun put(row: EntryPreset)

    @Query("SELECT * FROM merchant_rules ORDER BY priority DESC,length(pattern) DESC,id")
    fun rules(): Flow<List<MerchantRule>>

    @Query("SELECT * FROM merchant_rules WHERE id=:id") suspend fun rule(id: String): MerchantRule?

    @Upsert suspend fun put(row: MerchantRule)

    @Query("DELETE FROM merchant_rules WHERE id=:id AND revision=:revision")
    suspend fun deleteRule(id: String, revision: Long): Int

    @Query("SELECT * FROM fast_entry_settings WHERE id=1") fun settings(): Flow<FastEntrySettings?>

    @Upsert suspend fun put(row: FastEntrySettings)
}

object EntryRules {
    fun normalize(text: String) =
        Normalizer.normalize(text, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    fun validateSplits(
        type: String,
        amount: Long,
        parts: List<SplitPart>,
        categories: List<Category>,
        previous: Set<String> = emptySet(),
    ) {
        if (parts.isEmpty()) return
        require(type in listOf("EXPENSE", "INCOME", "REFUND")) {
            "Only expenses, income and refunds support categories"
        }
        require(parts.size in 2..20 && parts.map { it.categoryId }.distinct().size == parts.size) {
            "Choose 2–20 different categories"
        }
        require(parts.all { it.amountMinor in 1..Money.MAX_MINOR }) {
            "Every split must have a positive exact amount"
        }
        require(parts.fold(0L) { a, p -> Math.addExact(a, p.amountMinor) } == amount) {
            "Split amounts must equal the transaction total"
        }
        val kind = if (type == "REFUND") "EXPENSE" else type
        require(
            parts.all { p ->
                categories.any {
                    it.id == p.categoryId && it.type == kind && (!it.archived || it.id in previous)
                }
            }
        ) {
            "Choose valid active categories for every split"
        }
    }

    fun match(
        merchant: String,
        type: String,
        accountId: String,
        rules: List<MerchantRule>,
        categories: List<Category>,
    ): MerchantRule? {
        val input = normalize(merchant)
        if (input.isBlank()) return null
        return rules
            .filter {
                it.enabled &&
                    it.type == (if (type == "REFUND") "EXPENSE" else type) &&
                    (it.accountId == null || it.accountId == accountId) &&
                    categories.any { c ->
                        c.id == it.categoryId && !c.archived && c.type == it.type
                    }
            }
            .sortedWith(
                compareByDescending<MerchantRule> { it.priority }
                    .thenByDescending {
                        when (it.mode) {
                            "EXACT" -> 3
                            "PREFIX" -> 2
                            else -> 1
                        }
                    }
                    .thenByDescending { it.pattern.length }
                    .thenBy { it.id }
            )
            .firstOrNull {
                when (it.mode) {
                    "EXACT" -> input == it.pattern
                    "PREFIX" -> input == it.pattern || input.startsWith(it.pattern + " ")
                    "CONTAINS" -> (" $input ").contains(" ${it.pattern} ")
                    else -> false
                }
            }
    }
}

class TransactionExtrasRepository(val db: TraxosDatabase) {
    val dao = db.extras()

    suspend fun adoptDrafts(id: String) {
        val rows = dao.draftRows(id)
        require(dao.receiptCount(id) + rows.size <= 5) {
            "Up to 5 receipts are supported per transaction"
        }
        rows.forEach { r ->
            require(dao.duplicateReceipt(id, r.digest) == 0)
            dao.addReceipt(
                ReceiptAttachment(r.id, id, r.name, r.mime, r.digest, r.secret, r.createdAt)
            )
        }
        dao.discardDrafts(id)
    }

    suspend fun preset(row: EntryPreset, expected: Long) =
        withContext(Dispatchers.IO) {
            UUID.fromString(row.id)
            require(
                row.name.trim().length in 1..60 &&
                    !row.name.any { it.isISOControl() } &&
                    row.note.length <= 500 &&
                    row.position in 0..9999
            )
            db.withTransaction {
                val old = dao.preset(row.id)
                require((old?.revision ?: 0) == expected) { "Favorite changed; reopen it" }
                require(old != null || dao.presets().first().size < 100) {
                    "Up to 100 saved presets are supported"
                }
                validatePreset(row)
                require(
                    dao.presets().first().none {
                        it.id != row.id &&
                            EntryRules.normalize(it.name) == EntryRules.normalize(row.name)
                    }
                ) {
                    "A preset with this name already exists"
                }
                dao.put(row.copy(name = row.name.trim(), revision = expected + 1))
            }
        }

    suspend fun validatePreset(row: EntryPreset) {
        require(!row.archived) { "This favorite is archived" }
        require(row.type in listOf("EXPENSE", "INCOME"))
        require(row.amountMinor in 1..Money.MAX_MINOR)
        val a =
            requireNotNull(db.ledger().account(row.accountId)) { "Favorite account is unavailable" }
        val c = requireNotNull(db.ledger().category(row.categoryId))
        require(
            !a.archived &&
                a.currency == row.currency &&
                (row.type != "INCOME" || a.type != "CREDIT_CARD")
        ) {
            "Favorite account is archived or incompatible"
        }
        require(!c.archived && c.type == row.type) {
            "Favorite category is archived or incompatible"
        }
        row.merchantId?.let {
            require(
                db.ledger().label(it)?.let { m -> !m.archived && m.kind == "MERCHANT" } == true
            ) {
                "Favorite merchant is archived"
            }
        }
    }

    suspend fun archivePreset(id: String, revision: Long, archived: Boolean) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val old = requireNotNull(dao.preset(id))
                require(old.revision == revision)
                dao.put(old.copy(archived = archived, revision = revision + 1))
            }
        }

    suspend fun move(id: String, delta: Int) =
        withContext(Dispatchers.IO) {
            require(delta in listOf(-1, 1))
            db.withTransaction {
                val rows = dao.presets().first().filter { !it.archived }
                val i = rows.indexOfFirst { it.id == id }
                require(i >= 0)
                val target = i + delta
                require(target in rows.indices)
                val ordered = rows.toMutableList()
                java.util.Collections.swap(ordered, i, target)
                ordered.forEachIndexed { index, p ->
                    dao.put(p.copy(position = index, revision = p.revision + 1))
                }
            }
        }

    suspend fun rule(row: MerchantRule, expected: Long) =
        withContext(Dispatchers.IO) {
            UUID.fromString(row.id)
            val pattern = EntryRules.normalize(row.pattern)
            require(
                pattern.length in 2..80 &&
                    row.mode in listOf("EXACT", "PREFIX", "CONTAINS") &&
                    row.priority in 0..1000
            )
            db.withTransaction {
                val old = dao.rule(row.id)
                require((old?.revision ?: 0) == expected) { "Rule changed; reopen it" }
                require(old != null || dao.rules().first().size < 500) {
                    "Up to 500 merchant rules are supported"
                }
                val cat = requireNotNull(db.ledger().category(row.categoryId))
                require(
                    !cat.archived && cat.type == row.type && row.type in listOf("INCOME", "EXPENSE")
                ) {
                    "Choose an active category of the correct type"
                }
                row.accountId?.let { require(db.ledger().account(it)?.archived == false) }
                dao.put(
                    row.copy(
                        pattern = pattern,
                        scopeKey = row.accountId ?: "all",
                        revision = expected + 1,
                    )
                )
            }
        }

    suspend fun suggested(merchant: String, type: String, account: String): MerchantRule? =
        withContext(Dispatchers.IO) {
            if (dao.settings().first()?.rules == false) null
            else
                EntryRules.match(
                    merchant,
                    type,
                    account,
                    dao.rules().first(),
                    db.ledger().categories().first(),
                )
        }

    suspend fun settings(row: FastEntrySettings) =
        withContext(Dispatchers.IO) {
            require(
                listOf(row.widget1, row.widget2, row.widget3).all {
                    it in listOf("EXPENSE", "INCOME", "VOICE", "SMART", "HOME") ||
                        it.startsWith("PRESET:") &&
                            runCatching { UUID.fromString(it.substringAfter(':')) }.isSuccess
                }
            )
            dao.put(row)
        }
}

data class ReceiptRecordRow(
    @Embedded val record: LedgerRecord,
    val accountName: String,
    val currency: String,
    val categoryName: String,
)
