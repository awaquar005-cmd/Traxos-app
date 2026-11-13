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
        "gift_cards",
        "gift_redemptions",
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
                    data.writeInt(11)
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
                            require(data.readInt() == 1 && data.readInt() == 11) {
                                "This backup needs a compatible Traxos version"
                            }
                            require(data.readInt() == backupTables.size)
                            backupTables.forEach { expected ->
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
                    val primary = if (table == "outbox_operations") "operationId" else "id"
                    source.query("SELECT * FROM `$table`").use { cursor ->
                        while (cursor.moveToNext()) {
                            currentCoroutineContext().ensureActive()
                            val item = row(cursor)
                            val id = item.getString(primary)
                            val existing =
                                target
                                    .query("SELECT * FROM `$table` WHERE `$primary`=?", arrayOf(id))
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
        none("SELECT total FROM (SELECT COUNT(*) AS total FROM accounts) WHERE total>1000")
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
