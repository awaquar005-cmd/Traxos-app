package app.traxos.finance.backup

import android.database.Cursor
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.util.Base64
import android.util.JsonWriter
import androidx.room.withTransaction
import app.traxos.finance.data.TraxosDatabase
import app.traxos.finance.domain.Money
import app.traxos.finance.gifts.GiftCipher
import app.traxos.finance.regional.*
import java.io.*
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Android PDF is device-native; the page surface is injectable for host layout tests. */
interface PdfPages : Closeable {
    fun startPage(number: Int): Canvas

    fun finishPage()

    fun writeTo(output: OutputStream)
}

private class AndroidPdfPages : PdfPages {
    private val document = PdfDocument()
    private var page: PdfDocument.Page? = null

    override fun startPage(number: Int): Canvas {
        finishPage()
        val next = document.startPage(PdfDocument.PageInfo.Builder(595, 842, number).create())
        page = next
        return next.canvas
    }

    override fun finishPage() {
        val current = page ?: return
        document.finishPage(current)
        page = null
    }

    override fun writeTo(output: OutputStream) {
        finishPage()
        document.writeTo(output)
    }

    override fun close() {
        try {
            finishPage()
        } finally {
            document.close()
        }
    }
}

data class ExportScope(
    val start: Long = LocalDate.of(1900, 1, 1).toEpochDay(),
    val end: Long = LocalDate.of(2200, 12, 31).toEpochDay(),
    val account: String = "",
    val currency: String = "",
    val deleted: Boolean = false,
    val notes: Boolean = true,
) {
    fun validate() {
        require(
            start <= end &&
                start >= LocalDate.of(1900, 1, 1).toEpochDay() &&
                end <= LocalDate.of(2200, 12, 31).toEpochDay()
        ) {
            "Choose a valid1900–2200 date range"
        }
        require(currency.isBlank() || currency in Money.currencies)
    }
}

class ReadableExports(
    private val db: TraxosDatabase,
    private val cipher: GiftCipher,
    private val pdfFactory: () -> PdfPages = { AndroidPdfPages() },
) {
    private fun typeLabel(type: String): String =
        when (type) {
            "EXPENSE" -> "Expense"
            "INCOME" -> "Income"
            "REFUND" -> "Refund"
            "TRANSFER_OUT" -> "Transfer sent"
            "TRANSFER_IN" -> "Transfer received"
            "PAYMENT_OUT" -> "Card payment sent"
            "PAYMENT_IN" -> "Card payment received"
            "DEBT_OUT" -> "Loan principal paid"
            "DEBT_IN" -> "Loan principal received"
            else -> type
        }

    private fun query(s: ExportScope): Cursor {
        s.validate()
        return db.openHelper.readableDatabase.query(
            "SELECT t.*,a.name AS accountName,a.currency,CASE WHEN t.type IN ('DEBT_IN','DEBT_OUT') OR EXISTS(SELECT 1 FROM debt_funding f WHERE f.recordId=t.id AND f.active=1) THEN 'Loan principal' ELSE c.name END AS categoryName,CASE WHEN t.type='EXPENSE' AND EXISTS(SELECT 1 FROM debt_funding f WHERE f.recordId=t.id AND f.active=1) THEN 'DEBT_OUT' WHEN t.type='INCOME' AND EXISTS(SELECT 1 FROM debt_funding f WHERE f.recordId=t.id AND f.active=1) THEN 'DEBT_IN' ELSE t.type END AS displayType FROM transactions t JOIN accounts a ON a.id=t.accountId JOIN categories c ON c.id=t.categoryId WHERE t.day>=? AND t.day<=? AND (?='' OR t.accountId=?) AND (?='' OR a.currency=?) AND (?=1 OR t.deleted=0) ORDER BY t.day,t.id",
            arrayOf<Any>(
                s.start,
                s.end,
                s.account,
                s.account,
                s.currency,
                s.currency,
                if (s.deleted) 1 else 0,
            ),
        )
    }

    suspend fun json(output: OutputStream, giftSecrets: Boolean = false) =
        withContext(Dispatchers.IO) {
            val job = currentCoroutineContext()
            db.withTransaction {
                JsonWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { w ->
                    w.beginObject()
                        .name("format")
                        .value("TRAXOS-TYPED-JSON")
                        .name("version")
                        .value(1L)
                        .name("roomSchema")
                        .value(15L)
                        .name("integerEncoding")
                        .value("decimal strings; no floating-point loss")
                        .name("blobEncoding")
                        .value("base64")
                        .name("giftSecretsIncluded")
                        .value(giftSecrets)
                    w.name("excluded").beginArray()
                    listOf(
                            "card vault including CVV",
                            "PIN and Keystore keys",
                            "online credentials",
                            "unapproved Smart Add",
                            "device settings and delivery receipts",
                            "local profile photo",
                        )
                        .forEach { w.value(it) }
                    if (!giftSecrets) w.value("gift secret payloads and saved images")
                    w.endArray()
                    w.name("tables").beginObject()
                    backupTables.forEach { table ->
                        w.name(table).beginArray()
                        db.openHelper.readableDatabase.query("SELECT * FROM `$table`").use { c ->
                            while (c.moveToNext()) {
                                job.ensureActive()
                                w.beginObject()
                                c.columnNames.forEachIndexed { index, name ->
                                    w.name(name).beginObject()
                                    when {
                                        table == "gift_cards" && name == "secret" && !giftSecrets ->
                                            w.name("type").value("omitted")
                                        c.isNull(index) -> w.name("type").value("null")
                                        c.getType(index) == Cursor.FIELD_TYPE_INTEGER ->
                                            w.name("type")
                                                .value("integer")
                                                .name("value")
                                                .value(c.getLong(index).toString())
                                        c.getType(index) == Cursor.FIELD_TYPE_BLOB -> {
                                            val value =
                                                if (table == "gift_cards" && name == "secret")
                                                    cipher.open(
                                                        c.getString(c.getColumnIndexOrThrow("id")),
                                                        c.getBlob(index),
                                                    )
                                                else c.getBlob(index)
                                            w.name("type")
                                                .value(
                                                    if (table == "gift_cards" && name == "secret")
                                                        "portable-gift-secret-base64"
                                                    else "blob"
                                                )
                                                .name("value")
                                                .value(Base64.encodeToString(value, Base64.NO_WRAP))
                                            if (table == "gift_cards" && name == "secret")
                                                value.fill(0)
                                        }
                                        c.getType(index) == Cursor.FIELD_TYPE_STRING ->
                                            w.name("type")
                                                .value("text")
                                                .name("value")
                                                .value(c.getString(index))
                                        else -> error("Unexpected non-exact database value")
                                    }
                                    w.endObject()
                                }
                                w.endObject()
                            }
                        }
                        w.endArray()
                    }
                    w.endObject().endObject()
                }
            }
        }

    suspend fun csv(output: OutputStream, scope: ExportScope) =
        withContext(Dispatchers.IO) {
            val job = currentCoroutineContext()
            db.withTransaction {
                output.bufferedWriter().use { w ->
                    w.write(
                        "id,date,type,account,category,classification,amount,currency,note,link_id,deleted\r\n"
                    )
                    query(scope).use { c ->
                        while (c.moveToNext()) {
                            job.ensureActive()
                            fun t(n: String): String {
                                return c.getString(c.getColumnIndexOrThrow(n)).orEmpty()
                            }
                            val fields =
                                listOf(
                                    t("id"),
                                    LocalDate.ofEpochDay(t("day").toLong()).toString(),
                                    t("type"),
                                    t("accountName"),
                                    t("categoryName"),
                                    typeLabel(t("displayType")),
                                    Money.editable(t("amountMinor").toLong(), t("currency")),
                                    t("currency"),
                                    if (scope.notes) t("note") else "",
                                    t("linkId"),
                                    t("deleted"),
                                )
                            w.write(fields.joinToString(",", transform = LedgerCsv::cell))
                            w.write("\r\n")
                        }
                    }
                }
            }
        }

    suspend fun pdf(
        output: OutputStream,
        scope: ExportScope,
        display: DisplayOptions,
        conversion: String = "",
    ) =
        withContext(Dispatchers.IO) {
            scope.validate()
            display.validate()
            require(conversion.isBlank() || conversion in Money.currencies)
            val job = currentCoroutineContext()
            db.withTransaction {
                val rates = db.regional().rates().first()
                query(scope).use { c ->
                    require(c.count <= 5000) {
                        "PDF supports up to5,000 entries. Narrow the filters or use JSON/CSV for the full history."
                    }
                    val dateRange =
                        if (c.moveToFirst()) {
                            val firstDay = c.getLong(c.getColumnIndexOrThrow("day"))
                            c.moveToLast()
                            val lastDay = c.getLong(c.getColumnIndexOrThrow("day"))
                            c.moveToPosition(-1)
                            "${display.date(firstDay)} – ${display.date(lastDay)}"
                        } else "${display.date(scope.start)} – ${display.date(scope.end)}"
                    pdfFactory().use { doc ->
                        var canvas: Canvas? = null
                        var y = 0f
                        var number = 0
                        val paint =
                            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.rgb(21, 33, 56)
                                textSize = 10f
                            }
                        fun next() {
                            number++
                            canvas = doc.startPage(number)
                            y = 40f
                            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                            paint.textSize = 16f
                            canvas!!.drawText("TRAXOS · Activity", 32f, y, paint)
                            y += 22
                            paint.textSize = 9f
                            paint.typeface = Typeface.DEFAULT
                            canvas!!.drawText("$dateRange · Page $number", 32f, y, paint)
                            y += 18
                        }
                        fun line(text: String) {
                            text.lines().forEach { paragraph ->
                                var remaining =
                                    paragraph.replace(Regex("[\\p{Cc}]"), " ").ifEmpty { " " }
                                while (remaining.isNotEmpty()) {
                                    if (canvas == null || y > 790) next()
                                    paint.textSize = 10f
                                    val n =
                                        paint
                                            .breakText(remaining, true, 525f, null)
                                            .coerceAtLeast(1)
                                    canvas!!.drawText(remaining.take(n), 32f, y, paint)
                                    y += 15
                                    remaining = remaining.drop(n)
                                }
                            }
                        }
                        run {
                            next()
                            line(
                                "Local ledger movements. Transfers, card payments and loan principal are not spending."
                            )
                            line(
                                "${c.count} entries · ${if(scope.deleted)"includes deleted records"else"active records only"}"
                            )
                            if (conversion.isNotBlank())
                                line(
                                    "Manual estimates in $conversion; original currency amounts remain authoritative. Rate chosen as of transaction date. Missing rates stay unconverted."
                                )
                            while (c.moveToNext()) {
                                job.ensureActive()
                                fun t(n: String): String {
                                    return c.getString(c.getColumnIndexOrThrow(n)).orEmpty()
                                }
                                val type = typeLabel(t("displayType"))
                                val currency = t("currency")
                                val amount = t("amountMinor").toLong()
                                val day = t("day").toLong()
                                y += 8
                                line(
                                    "${display.date(day)} · $type${if(t("deleted")=="1")" · DELETED"else""}"
                                )
                                line(
                                    "${t("accountName")} · ${t("categoryName")} · ${Money.format(amount,currency,display.localeValue(),display.currencyCode)}"
                                )
                                if (conversion.isNotBlank() && currency != conversion) {
                                    val result =
                                        ExchangeRules.convert(
                                            amount,
                                            currency,
                                            conversion,
                                            day,
                                            rates,
                                            display.inverse,
                                            display.rounding,
                                        )
                                    line(
                                        if (result == null)
                                            "No applicable manual rate for $currency → $conversion"
                                        else
                                            "Estimate ${Money.format(result.minor,conversion,display.localeValue(),true)} · ${if(result.inverse)"inverse "else""}rate ${result.rate.rate} dated ${display.date(result.rate.day)}${if(day-result.rate.day>30)" · older than30 days"else""}"
                                    )
                                }
                                if (scope.notes && t("note").isNotBlank()) line(t("note"))
                                line(
                                    "ID ${t("id")}${t("linkId").takeIf{it.isNotBlank()}?.let{" · Pair $it"}.orEmpty()}"
                                )
                            }
                            doc.finishPage()
                            canvas = null
                            doc.writeTo(output)
                        }
                    }
                }
            }
        }
}
