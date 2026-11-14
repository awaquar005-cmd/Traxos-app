package app.traxos.finance.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import app.traxos.finance.data.TraxosDatabase
import app.traxos.finance.domain.Money
import app.traxos.finance.gifts.GiftCipher
import app.traxos.finance.gifts.GiftSecret
import java.io.*
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Versioned, authenticated streaming envelope. Passwords never leave this operation. */
object BackupEnvelope {
    private val magic = "TRAXOSB1".toByteArray(Charsets.US_ASCII)
    private const val rounds = 310_000

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        require(password.size in 12..128) { "Use a password of 12–128 characters" }
        val spec = PBEKeySpec(password, salt, rounds, 256)
        return try {
            SecretKeySpec(
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,
                "AES",
            )
        } finally {
            spec.clearPassword()
        }
    }

    fun write(output: OutputStream, password: CharArray, write: (DataOutputStream) -> Unit) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val header = magic + salt + iv
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(header)
        output.write(header)
        DataOutputStream(GZIPOutputStream(CipherOutputStream(output, cipher))).use(write)
    }

    fun read(input: InputStream, password: CharArray, read: (DataInputStream) -> Unit) {
        val header = ByteArray(36)
        DataInputStream(input).readFully(header)
        require(header.copyOfRange(0, 8).contentEquals(magic)) { "Not a Traxos backup" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(password, header.copyOfRange(8, 24)),
            GCMParameterSpec(128, header.copyOfRange(24, 36)),
        )
        cipher.updateAAD(header)
        // Drain through AEAD EOF even if the compressed stream has already ended.
        CipherInputStream(LimitedInput(input, 64L * 1024 * 1024), cipher).use { decrypted ->
            DataInputStream(LimitedInput(GZIPInputStream(decrypted), 512L * 1024 * 1024)).use { data
                ->
                read(data)
                require(data.read() == -1) { "Unexpected backup content" }
                require(decrypted.read() == -1) { "Unexpected encrypted content" }
            }
        }
    }

    internal class LimitedInput(input: InputStream, private val limit: Long) :
        FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int = super.read().also { if (it >= 0) checked(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            `in`.read(b, off, len).also { if (it > 0) checked(it) }

        private fun checked(n: Int) {
            count += n
            require(count <= limit) { "Backup exceeds the supported size" }
        }
    }
}

internal val backupTables =
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
        "shared_groups",
        "group_members",
        "group_events",
        "group_shares",
        "group_ledger_links",
        "card_cycles",
        "card_statements",
        "statement_payments",
        "statement_revisions",
        "payment_plans",
        "plan_payments",
    )

private fun DataOutputStream.text(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    require(bytes.size <= 4_000_000)
    writeInt(bytes.size)
    write(bytes)
}

private fun DataInputStream.text(): String {
    val n = readInt()
    require(n in 0..4_000_000) { "Invalid backup record" }
    return ByteArray(n).also { readFully(it) }.toString(Charsets.UTF_8)
}

private fun row(cursor: Cursor): JSONObject =
    JSONObject().apply {
        cursor.columnNames.forEachIndexed { i, column ->
            put(
                column,
                when (cursor.getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                    Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                    Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                    Cursor.FIELD_TYPE_BLOB ->
                        JSONObject()
                            .put("blob", Base64.encodeToString(cursor.getBlob(i), Base64.NO_WRAP))
                    else -> error("Unsupported financial field")
                },
            )
        }
    }

private fun equivalent(a: JSONObject, b: JSONObject): Boolean {
    if (a.keys().asSequence().toSet() != b.keys().asSequence().toSet()) return false
    return a.keys().asSequence().all { key ->
        val x = a.get(key)
        val y = b.get(key)
        when {
            x is Number && y is Number -> x.toLong() == y.toLong()
            x is JSONObject && y is JSONObject -> equivalent(x, y)
            else -> x == y
        }
    }
}

private val booleanColumns =
    setOf("archived", "deleted", "enabled", "redeemed", "partialAllowed", "manuallyExpired")

private fun values(row: JSONObject) =
    ContentValues().apply {
        row.keys().forEach { key ->
            when (val value = row.get(key)) {
                JSONObject.NULL -> putNull(key)
                is JSONObject -> {
                    require(value.length() == 1 && value.has("blob"))
                    put(key, Base64.decode(value.getString("blob"), Base64.NO_WRAP))
                }
                is Number -> {
                    require(value !is Double && value !is Float)
                    put(key, value.toLong())
                }
                is String -> put(key, value)
                else -> error("Unsupported backup field")
            }
        }
    }

/** Additive restore: any same-ID disagreement rolls back; nothing is silently replaced. */
class PortableBackup(
    private val context: Context,
    private val db: TraxosDatabase,
    private val secrets: GiftCipher,
) {
    class Preview
    internal constructor(
        internal val stage: TraxosDatabase,
        internal val path: File,
        val counts: Map<String, Long>,
    ) : Closeable {
        override fun close() {
            stage.close()
            SQLiteDatabase.deleteDatabase(path)
        }
    }

    suspend fun write(output: OutputStream, password: CharArray) =
        withContext(Dispatchers.IO) {
            val operationContext = currentCoroutineContext()
            db.withTransaction {
                val sql = db.openHelper.writableDatabase
                BackupEnvelope.write(output, password) { data ->
                    data.writeInt(1)
                    data.writeInt(13)
                    data.writeInt(backupTables.size)
                    backupTables.forEach { table ->
                        data.text(table)
                        sql.query("SELECT COUNT(*) FROM `$table`").use {
                            it.moveToFirst()
                            val n = it.getLong(0)
                            require(n <= 1_000_000)
                            data.writeLong(n)
                        }
                        sql.query("SELECT * FROM `$table`").use { cursor ->
                            while (cursor.moveToNext()) {
                                operationContext.ensureActive()
                                val item = row(cursor)
                                if (table == "gift_cards") {
                                    val blob = values(item).getAsByteArray("secret")
                                    item.put(
                                        "secret",
                                        JSONObject()
                                            .put(
                                                "blob",
                                                Base64.encodeToString(
                                                    secrets.open(item.getString("id"), blob),
                                                    Base64.NO_WRAP,
                                                ),
                                            ),
                                    )
                                    item.put("fingerprint", "portable")
                                }
                                data.text(item.toString())
                            }
                        }
                    }
                    data.text("END")
                }
            }
        }

    suspend fun inspect(input: InputStream, password: CharArray): Preview {
        var owned: Preview? = null
        try {
            return withContext(Dispatchers.IO) {
                val operationContext = currentCoroutineContext()
                if (db.gifts().count() > 0) secrets.verifyExisting()
                val path = File(context.cacheDir, "traxos-restore-${UUID.randomUUID()}.db")
                val stage = TraxosDatabase.open(context, path.absolutePath)
                try {
                    stage.clearAllTables()
                    val sql = stage.openHelper.writableDatabase
                    val counts = linkedMapOf<String, Long>()
                    stage.withTransaction {
                        BackupEnvelope.read(input, password) { data ->
                            require(data.readInt() == 1)
                            val sourceSchema = data.readInt()
                            require(sourceSchema in 11..13) {
                                "This backup needs a compatible Traxos version"
                            }
                            val added13 =
                                setOf(
                                    "shared_groups",
                                    "group_members",
                                    "group_events",
                                    "group_shares",
                                    "group_ledger_links",
                                    "card_cycles",
                                    "card_statements",
                                    "statement_payments",
                                    "statement_revisions",
                                    "payment_plans",
                                    "plan_payments",
                                )
                            val sourceTables =
                                backupTables.filterNot {
                                    (sourceSchema < 13 && it in added13) ||
                                        (sourceSchema == 11 &&
                                            it in listOf("debt_threads", "debt_movements"))
                                }
                            require(data.readInt() == sourceTables.size)
                            sourceTables.forEach { expected ->
                                require(data.text() == expected) { "Invalid backup table" }
                                val columns =
                                    sql.query("SELECT * FROM `$expected` LIMIT 0").use {
                                        it.columnNames.toSet()
                                    }
                                val schema =
                                    sql.query("PRAGMA table_info(`$expected`)").use { info ->
                                        buildList {
                                            while (info.moveToNext()) add(
                                                Triple(
                                                    info.getString(1),
                                                    info.getString(2),
                                                    info.getInt(3) != 0 || info.getInt(5) != 0,
                                                )
                                            )
                                        }
                                    }
                                val n = data.readLong()
                                require(n in 0..1_000_000) { "Too many backup records" }
                                counts[expected] = n
                                repeat(n.toInt()) {
                                    operationContext.ensureActive()
                                    val item = JSONObject(data.text())
                                    require(item.keys().asSequence().toSet() == columns) {
                                        "Incompatible backup columns"
                                    }
                                    schema.forEach { (name, type, required) ->
                                        val value = item.get(name)
                                        if (value == JSONObject.NULL)
                                            require(!required) { "Missing backup field" }
                                        else
                                            when (type) {
                                                "INTEGER" ->
                                                    require(
                                                        value is Number &&
                                                            value !is Double &&
                                                            value !is Float
                                                    ) {
                                                        "Invalid integer field"
                                                    }
                                                "TEXT" ->
                                                    require(value is String) {
                                                        "Invalid text field"
                                                    }
                                                "BLOB" ->
                                                    require(
                                                        value is JSONObject && value.has("blob")
                                                    ) {
                                                        "Invalid binary field"
                                                    }
                                                else -> error("Unsupported schema field")
                                            }
                                        if (name in booleanColumns)
                                            require(value is Number && value.toLong() in 0..1)
                                        if (name == "revision")
                                            require(value is Number && value.toLong() >= 1)
                                    }
                                    if (expected == "gift_cards") {
                                        val portable = values(item).getAsByteArray("secret")
                                        val secret = GiftSecret.read(portable)
                                        require(
                                            secret.code.isNotBlank() &&
                                                secret.code.length <= 256 &&
                                                secret.pin.length <= 128 &&
                                                secret.notes.length <= 4000 &&
                                                (secret.image?.size ?: 0) <= 700_000
                                        ) {
                                            "Invalid gift payload"
                                        }
                                        val code = secret.code
                                        val fingerprint =
                                            secrets.fingerprint(
                                                if (item.getString("kind") == "DISCOUNT")
                                                    "discount:${item.getString("merchant").trim().lowercase(Locale.ROOT)}:$code"
                                                else "value:$code"
                                            )
                                        item.put(
                                            "secret",
                                            JSONObject()
                                                .put(
                                                    "blob",
                                                    Base64.encodeToString(
                                                        secrets.seal(
                                                            item.getString("id"),
                                                            portable,
                                                        ),
                                                        Base64.NO_WRAP,
                                                    ),
                                                ),
                                        )
                                        item.put(
                                            "fingerprint",
                                            if (item.getInt("deleted") == 1)
                                                "deleted:${item.getString("id")}"
                                            else fingerprint,
                                        )
                                    }
                                    sql.insert(
                                        expected,
                                        SQLiteDatabase.CONFLICT_ABORT,
                                        values(item),
                                    )
                                }
                            }
                            require(data.text() == "END")
                        }
                        validate(sql)
                    }
                    Preview(stage, path, counts).also { owned = it }
                } catch (e: Exception) {
                    stage.close()
                    SQLiteDatabase.deleteDatabase(path)
                    throw e
                }
            }
        } catch (e: Exception) {
            owned?.close()
            throw e
        }
    }

    suspend fun restore(preview: Preview): Long =
        withContext(Dispatchers.IO) {
            var added = 0L
            db.withTransaction {
                val target = db.openHelper.writableDatabase
                val source = preview.stage.openHelper.writableDatabase
                val pristine =
                    backupTables
                        .filter { it != "categories" }
                        .all { table ->
                            target.query("SELECT COUNT(*) FROM `$table`").use {
                                it.moveToFirst()
                                it.getLong(0) == 0L
                            }
                        }
                val seeded =
                    listOf(
                        "food" to "Food & drink",
                        "groceries" to "Groceries",
                        "travel" to "Transport",
                        "bills" to "Bills",
                        "other-expense" to "Other expense",
                        "salary" to "Salary",
                        "other-income" to "Other income",
                    )
                val seedOnly =
                    target.query("SELECT id,name,revision,archived FROM categories").use { c ->
                        var count = 0
                        var valid = true
                        while (c.moveToNext()) {
                            count++
                            valid =
                                valid &&
                                    seeded.contains(c.getString(0) to c.getString(1)) &&
                                    c.getLong(2) == 1L &&
                                    c.getInt(3) == 0
                        }
                        valid && count == seeded.size
                    }
                if (pristine && seedOnly) target.execSQL("DELETE FROM categories")
                backupTables.forEach { table ->
                    val primary =
                        source.query("PRAGMA table_info(`$table`)").use { info ->
                            buildList<Pair<Int, String>> {
                                    while (info.moveToNext()) if (info.getInt(5) > 0)
                                        add(info.getInt(5) to info.getString(1))
                                }
                                .sortedBy { it.first }
                                .map { it.second }
                        }
                    require(primary.isNotEmpty()) { "Backup table has no stable identity" }
                    source.query("SELECT * FROM `$table`").use { cursor ->
                        while (cursor.moveToNext()) {
                            currentCoroutineContext().ensureActive()
                            val item = row(cursor)
                            val identities = primary.map { item.getString(it) }
                            val id = identities.joinToString(":")
                            val existing =
                                target
                                    .query(
                                        "SELECT * FROM `$table` WHERE " +
                                            primary.joinToString(" AND ") { "`$it`=?" },
                                        identities.toTypedArray(),
                                    )
                                    .use { if (it.moveToFirst()) row(it) else null }
                            if (existing == null) {
                                target.insert(table, SQLiteDatabase.CONFLICT_ABORT, values(item))
                                added++
                            } else {
                                if (table == "gift_cards") {
                                    val old =
                                        GiftSecret.read(
                                                secrets.open(
                                                    id,
                                                    values(existing).getAsByteArray("secret"),
                                                )
                                            )
                                            .bytes()
                                    val incoming =
                                        GiftSecret.read(
                                                secrets.open(
                                                    id,
                                                    values(item).getAsByteArray("secret"),
                                                )
                                            )
                                            .bytes()
                                    require(old.contentEquals(incoming)) {
                                        "Conflicting gift card. Nothing was restored."
                                    }
                                    existing.remove("secret")
                                    item.remove("secret")
                                }
                                require(equivalent(existing, item)) {
                                    "Conflicting $table record. Nothing was restored; keep both backups for review."
                                }
                            }
                        }
                    }
                }
                validate(target)
            }
            added
        }

    private fun validate(sql: SupportSQLiteDatabase) {
        sql.query("PRAGMA foreign_key_check").use {
            require(!it.moveToFirst()) { "Broken backup references" }
        }
        fun none(query: String) {
            sql.query(query).use { require(!it.moveToFirst()) { "Invalid financial backup" } }
        }
        none("SELECT total FROM (SELECT COUNT(*) AS total FROM transactions) WHERE total>1000000")
        none(
            "SELECT g.id FROM shared_groups g LEFT JOIN group_members m ON m.id=g.selfId WHERE m.id IS NULL OR m.groupId!=g.id OR length(trim(g.name)) NOT BETWEEN 1 AND 80 OR g.currency NOT IN ('INR','USD','EUR','JPY','KWD') OR g.revision<1"
        )
        none(
            "SELECT id FROM group_members WHERE length(trim(name)) NOT BETWEEN 1 AND 80 OR revision<1"
        )
        none(
            "SELECT e.id FROM group_events e LEFT JOIN group_members p ON p.id=e.payerId LEFT JOIN group_members r ON r.id=e.receiverId WHERE p.id IS NULL OR p.groupId!=e.groupId OR e.kind NOT IN ('EXPENSE','SETTLEMENT','REVERSAL') OR e.amountMinor NOT BETWEEN 1 AND ${Money.MAX_MINOR} OR e.day NOT BETWEEN 3652 AND 84370 OR (e.kind='SETTLEMENT' AND (r.id IS NULL OR r.groupId!=e.groupId OR e.receiverId=e.payerId))"
        )
        none(
            "SELECT e.id FROM group_events e LEFT JOIN categories c ON c.id=e.categoryId WHERE e.kind='EXPENSE' AND (c.id IS NULL OR c.type!='EXPENSE' OR e.splitMethod NOT IN ('EQUAL','EXACT','PERCENT','SHARES'))"
        )
        none(
            "SELECT e.id FROM group_events e LEFT JOIN group_events p ON p.id=e.reversalOf WHERE (e.kind='REVERSAL' AND (p.id IS NULL OR p.reversalOf IS NOT NULL OR p.groupId!=e.groupId OR p.amountMinor!=e.amountMinor)) OR (e.kind!='REVERSAL' AND e.reversalOf IS NOT NULL)"
        )
        none(
            "SELECT s.eventId FROM group_shares s JOIN group_events e ON e.id=s.eventId JOIN group_members m ON m.id=s.memberId WHERE e.kind!='EXPENSE' OR m.groupId!=e.groupId OR s.amountMinor NOT BETWEEN 0 AND ${Money.MAX_MINOR}"
        )
        none(
            "SELECT e.id FROM group_events e LEFT JOIN group_shares s ON s.eventId=e.id WHERE e.kind='EXPENSE' GROUP BY e.id HAVING COALESCE(SUM(s.amountMinor),0)!=e.amountMinor"
        )
        none(
            "SELECT l.recordId FROM group_ledger_links l JOIN transactions t ON t.id=l.recordId JOIN accounts a ON a.id=t.accountId JOIN group_events e ON e.id=l.eventId JOIN shared_groups g ON g.id=e.groupId WHERE t.deleted=1 OR t.linkId IS NOT NULL OR l.reportMinor!=0 OR a.currency!=g.currency OR t.day!=e.day OR t.amountMinor!=e.amountMinor OR EXISTS(SELECT 1 FROM group_events r WHERE r.reversalOf=e.id) OR NOT ((e.kind='EXPENSE' AND e.payerId=g.selfId AND t.type='EXPENSE') OR (e.kind='SETTLEMENT' AND ((e.payerId=g.selfId AND t.type='EXPENSE') OR (e.receiverId=g.selfId AND t.type='INCOME'))))"
        )
        none(
            "SELECT c.accountId FROM card_cycles c JOIN accounts a ON a.id=c.accountId LEFT JOIN accounts f ON f.id=c.fundingId WHERE a.type!='CREDIT_CARD' OR c.closingDay NOT BETWEEN 1 AND 31 OR c.dueDays NOT BETWEEN 1 AND 60 OR (c.limitMinor IS NOT NULL AND c.limitMinor NOT BETWEEN 1 AND ${Money.MAX_MINOR}) OR c.minimumMinor NOT BETWEEN 0 AND ${Money.MAX_MINOR} OR c.revision<1 OR (c.fundingId IS NOT NULL AND (f.id IS NULL OR f.type='CREDIT_CARD' OR f.currency!=a.currency))"
        )
        none(
            "SELECT s.id FROM card_statements s JOIN accounts a ON a.id=s.accountId WHERE a.type!='CREDIT_CARD' OR s.amountMinor NOT BETWEEN 0 AND ${Money.MAX_MINOR} OR s.minimumMinor NOT BETWEEN 0 AND s.amountMinor OR s.dueDay<s.closingDay OR s.closingDay NOT BETWEEN 3652 AND 84370 OR s.dueDay NOT BETWEEN 3652 AND 84370 OR s.revision<1"
        )
        none(
            "SELECT id FROM statement_revisions WHERE beforeAmount NOT BETWEEN 0 AND ${Money.MAX_MINOR} OR beforeMinimum NOT BETWEEN 0 AND beforeAmount OR beforeDue NOT BETWEEN 3652 AND 84370 OR length(trim(reason)) NOT BETWEEN 1 AND 200"
        )
        none(
            "SELECT accountId FROM card_statements WHERE voided=0 GROUP BY accountId,closingDay HAVING COUNT(*)>1"
        )
        none(
            "SELECT p.id FROM statement_payments p JOIN card_statements s ON s.id=p.statementId JOIN transactions t ON t.id=p.recordId WHERE p.amountMinor<=0 OR t.type!='PAYMENT_IN' OR t.accountId!=s.accountId OR t.day<=s.closingDay"
        )
        none(
            "SELECT p.recordId FROM statement_payments p JOIN transactions t ON t.id=p.recordId WHERE p.active=1 GROUP BY p.recordId HAVING SUM(p.amountMinor)>t.amountMinor"
        )
        none(
            "SELECT s.id FROM card_statements s JOIN statement_payments p ON p.statementId=s.id JOIN transactions t ON t.id=p.recordId WHERE t.deleted=0 AND p.active=1 GROUP BY s.id HAVING SUM(p.amountMinor)>s.amountMinor"
        )
        none(
            "SELECT p.id FROM payment_plans p JOIN accounts a ON a.id=p.accountId JOIN categories c ON c.id=p.categoryId WHERE p.currency!=a.currency OR c.type!='EXPENSE' OR p.kind NOT IN ('SUBSCRIPTION','BILL','EMI') OR p.status NOT IN ('ACTIVE','PAUSED','CANCELLED','ENDED') OR p.frequency NOT IN ('DAILY','WEEKLY','MONTHLY','QUARTERLY','YEARLY','CUSTOM') OR p.customDays NOT BETWEEN 1 AND 3650 OR p.anchorDay NOT BETWEEN 1 AND 31 OR length(trim(p.name)) NOT BETWEEN 1 AND 80 OR p.nextDay NOT BETWEEN 3652 AND 84370 OR (p.amountMinor IS NOT NULL AND p.amountMinor NOT BETWEEN 1 AND ${Money.MAX_MINOR}) OR (p.trialEnd IS NOT NULL AND p.trialEnd>p.nextDay) OR p.revision<1"
        )
        none(
            "SELECT p.id FROM payment_plans p JOIN accounts a ON a.id=p.accountId LEFT JOIN transactions t ON t.id=p.purchaseId WHERE (p.kind='EMI' AND (t.id IS NULL OR t.deleted=1 OR t.type!='EXPENSE' OR t.accountId!=a.id OR a.type!='CREDIT_CARD' OR p.principalMinor!=t.amountMinor OR p.installments NOT BETWEEN 2 AND 120 OR p.frequency!='MONTHLY')) OR (p.kind!='EMI' AND (p.purchaseId IS NOT NULL OR p.installments!=0 OR p.principalMinor!=0))"
        )
        none(
            "SELECT r.id FROM plan_payments r JOIN payment_plans p ON p.id=r.planId LEFT JOIN transactions t ON t.id=r.recordId WHERE r.amountMinor NOT BETWEEN 1 AND ${Money.MAX_MINOR} OR r.feeMinor NOT BETWEEN 0 AND ${Money.MAX_MINOR} OR r.paidDay NOT BETWEEN 3652 AND 84370 OR r.dueDay NOT BETWEEN 3652 AND 84370 OR (r.voided=0 AND r.occurrenceKey!=p.id||':'||r.dueDay) OR (r.voided=1 AND r.occurrenceKey!='void:'||r.id) OR (r.recordId IS NOT NULL AND (t.type!='EXPENSE' OR t.accountId!=p.accountId OR t.day!=r.paidDay OR (r.voided=0 AND t.deleted=1) OR t.amountMinor!=CASE WHEN p.kind='EMI' THEN r.feeMinor ELSE r.amountMinor END)) OR (p.kind!='EMI' AND (r.recordId IS NULL OR r.feeMinor!=0)) OR (p.kind='EMI' AND ((r.feeMinor=0 AND r.recordId IS NOT NULL) OR (r.feeMinor>0 AND r.recordId IS NULL)))"
        )
        none(
            "SELECT recordId FROM plan_payments WHERE voided=0 AND recordId IS NOT NULL GROUP BY recordId HAVING COUNT(*)>1"
        )
        none(
            "SELECT p.id FROM payment_plans p JOIN plan_payments r ON r.planId=p.id WHERE p.kind='EMI' AND r.voided=0 GROUP BY p.id HAVING SUM(r.amountMinor)>p.principalMinor OR COUNT(*)>p.installments"
        )
        none(
            "SELECT l.recordId FROM group_ledger_links l JOIN plan_payments p ON p.recordId=l.recordId WHERE p.voided=0"
        )
        sql.query("SELECT offsets FROM card_cycles UNION ALL SELECT offsets FROM payment_plans")
            .use { rows ->
                while (rows.moveToNext()) app.traxos.finance.data.PlanDates.offsets(
                    rows.getString(0)
                )
            }

        none("SELECT total FROM (SELECT COUNT(*) AS total FROM accounts) WHERE total>1000")
        none("SELECT total FROM (SELECT COUNT(*) AS total FROM debt_threads) WHERE total>10000")
        none(
            "SELECT id FROM debt_threads WHERE day NOT BETWEEN 3652 AND 84370 OR (dueDay IS NOT NULL AND dueDay NOT BETWEEN 3652 AND 84370)"
        )
        none("SELECT id FROM debt_movements WHERE day NOT BETWEEN 3652 AND 84370")
        none(
            "SELECT id FROM debt_threads WHERE direction NOT IN ('LENT','BORROWED') OR currency NOT IN ('INR','USD','EUR','JPY','KWD') OR originalMinor NOT BETWEEN 1 AND ${Money.MAX_MINOR} OR outstandingMinor NOT BETWEEN 0 AND ${Money.MAX_MINOR} OR revision<1 OR length(trim(person)) NOT BETWEEN 1 AND 80 OR length(note)>500 OR (dueDay IS NOT NULL AND dueDay<day) OR (archived=1 AND outstandingMinor!=0)"
        )
        none(
            "SELECT d.id FROM debt_threads d LEFT JOIN debt_movements m ON m.debtId=d.id GROUP BY d.id HAVING d.originalMinor+COALESCE(SUM(m.deltaMinor),0)!=d.outstandingMinor"
        )
        none(
            "SELECT m.id FROM debt_movements m JOIN debt_threads d ON d.id=m.debtId WHERE m.day<d.day OR length(m.note)>500 OR m.deltaMinor=0 OR m.deltaMinor NOT BETWEEN -${Money.MAX_MINOR} AND ${Money.MAX_MINOR} OR m.kind NOT IN ('REPAYMENT','INCREASE','DECREASE','UNDO') OR (m.kind='INCREASE' AND m.deltaMinor<0) OR (m.kind IN ('REPAYMENT','DECREASE') AND m.deltaMinor>0) OR (m.kind='UNDO' AND m.undoOf IS NULL) OR (m.kind!='UNDO' AND m.undoOf IS NOT NULL)"
        )
        none(
            "SELECT u.id FROM debt_movements u LEFT JOIN debt_movements p ON p.id=u.undoOf WHERE u.undoOf IS NOT NULL AND (p.id IS NULL OR p.debtId!=u.debtId OR p.undoOf IS NOT NULL OR p.deltaMinor!=-u.deltaMinor)"
        )
        none("SELECT id FROM accounts WHERE openingDate NOT BETWEEN -365243219162 AND 365241780471")
        none(
            "SELECT id FROM transactions WHERE day NOT BETWEEN -365243219162 AND 365241780471 OR zoneId='' "
        )
        none(
            "SELECT id FROM recurring_rules WHERE nextDay NOT BETWEEN -365243219162 AND 365241780471"
        )
        sql.query("SELECT month FROM budgets").use { months ->
            while (months.moveToNext()) java.time.YearMonth.parse(months.getString(0))
        }
        none(
            "SELECT id FROM accounts WHERE currency NOT IN ('INR','USD','EUR','JPY','KWD') OR type NOT IN ('CASH','BANK','WALLET','CREDIT_CARD') OR openingMinor < -${Money.MAX_MINOR} OR openingMinor > ${Money.MAX_MINOR}"
        )
        none(
            "SELECT id FROM transactions WHERE amountMinor<=0 OR amountMinor>${Money.MAX_MINOR} OR type NOT IN ('EXPENSE','INCOME','REFUND','TRANSFER_IN','TRANSFER_OUT','PAYMENT_IN','PAYMENT_OUT')"
        )
        none(
            "SELECT t.id FROM transactions t LEFT JOIN transactions p ON p.linkId=t.linkId AND p.id!=t.id WHERE t.linkId IS NOT NULL GROUP BY t.id HAVING COUNT(p.id)!=1 OR MIN(p.amountMinor)!=t.amountMinor OR MIN(p.deleted)!=t.deleted"
        )
        none(
            "SELECT t.id FROM transactions t JOIN accounts a ON a.id=t.accountId JOIN categories c ON c.id=t.categoryId WHERE t.day<a.openingDate OR t.revision<1 OR length(t.note)>500 OR (t.type='INCOME' AND (c.type!='INCOME' OR a.type='CREDIT_CARD')) OR (t.type IN ('EXPENSE','REFUND') AND c.type!='EXPENSE') OR (t.type='REFUND' AND a.type!='CREDIT_CARD')"
        )
        none(
            "SELECT t.id FROM transactions t JOIN transactions p ON p.linkId=t.linkId AND p.id!=t.id JOIN accounts a ON a.id=t.accountId JOIN accounts b ON b.id=p.accountId WHERE a.currency!=b.currency OR a.id=b.id OR t.day!=p.day OR NOT ((t.type='TRANSFER_IN' AND p.type='TRANSFER_OUT') OR (t.type='TRANSFER_OUT' AND p.type='TRANSFER_IN') OR (t.type='PAYMENT_IN' AND p.type='PAYMENT_OUT') OR (t.type='PAYMENT_OUT' AND p.type='PAYMENT_IN'))"
        )
        none(
            "SELECT id FROM transactions WHERE (type IN ('TRANSFER_IN','TRANSFER_OUT','PAYMENT_IN','PAYMENT_OUT')) != (linkId IS NOT NULL)"
        )
        none(
            "SELECT id FROM recurring_rules WHERE amountMinor<=0 OR amountMinor>${Money.MAX_MINOR} OR frequency NOT IN ('DAILY','WEEKLY','MONTHLY') OR anchorDay NOT BETWEEN 1 AND 31"
        )
        none(
            "SELECT id FROM budgets WHERE limitMinor<=0 OR limitMinor>${Money.MAX_MINOR} OR currency NOT IN ('INR','USD','EUR','JPY','KWD')"
        )
        none(
            "SELECT id FROM savings_goals WHERE targetMinor<=0 OR targetMinor>${Money.MAX_MINOR} OR savedMinor<0 OR savedMinor>${Money.MAX_MINOR} OR currency NOT IN ('INR','USD','EUR','JPY','KWD')"
        )
        none(
            "SELECT g.id FROM savings_goals g LEFT JOIN goal_movements m ON m.goalId=g.id GROUP BY g.id HAVING COALESCE(SUM(m.deltaMinor),0)!=g.savedMinor"
        )
        none(
            "SELECT id FROM gift_cards WHERE currency NOT IN ('INR','USD','EUR','JPY','KWD') OR kind NOT IN ('STORED_VALUE','SINGLE_USE','DISCOUNT') OR length(merchant)>80 OR length(name)>80 OR revision<1 OR originalMinor<0 OR remainingMinor<0 OR remainingMinor>originalMinor OR originalMinor>${Money.MAX_MINOR}"
        )
    }
}

object LedgerCsv {
    fun cell(value: String): String {
        val safe =
            if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r', '\n'))
                "'$value"
            else value
        return "\"${safe.replace("\"","\"\"")}\""
    }

    suspend fun write(db: TraxosDatabase, output: OutputStream) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                output.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(
                        "id,date,type,account,category,amount,currency,note,link_id,deleted\r\n"
                    )
                    db.openHelper.writableDatabase
                        .query(
                            "SELECT t.*,a.name AS account_name,a.currency,c.name AS category_name FROM transactions t JOIN accounts a ON a.id=t.accountId JOIN categories c ON c.id=t.categoryId ORDER BY t.day,t.id"
                        )
                        .use { cursor ->
                            fun text(name: String) =
                                cursor.getString(cursor.getColumnIndexOrThrow(name)).orEmpty()
                            while (cursor.moveToNext()) {
                                val fields =
                                    listOf(
                                        text("id"),
                                        java.time.LocalDate.ofEpochDay(text("day").toLong())
                                            .toString(),
                                        text("type"),
                                        text("account_name"),
                                        text("category_name"),
                                        Money.editable(
                                            text("amountMinor").toLong(),
                                            text("currency"),
                                        ),
                                        text("currency"),
                                        text("note"),
                                        text("linkId"),
                                        text("deleted"),
                                    )
                                writer.write(fields.joinToString(",", transform = ::cell))
                                writer.write("\r\n")
                            }
                        }
                }
            }
        }
}
