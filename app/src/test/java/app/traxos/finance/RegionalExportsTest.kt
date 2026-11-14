package app.traxos.finance

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.backup.*
import app.traxos.finance.data.*
import app.traxos.finance.domain.Money
import app.traxos.finance.gifts.*
import app.traxos.finance.regional.*
import java.io.*
import java.time.*
import java.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RegionalExportsTest {
    private val context
        get() = ApplicationProvider.getApplicationContext<Application>()

    private lateinit var db: TraxosDatabase
    private lateinit var repo: RegionalRepository
    private val today = LocalDate.now().toEpochDay()

    private fun id() = UUID.randomUUID().toString()

    private fun rate(
        value: String = "83.125",
        source: String = "USD",
        target: String = "INR",
        day: Long = today,
    ) = ManualRate(id(), source, target, day, value)

    private fun invalid(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java, block)
    }

    @Before
    fun setup() {
        db = TraxosDatabase.open(context, "region-${id()}.db")
        repo = RegionalRepository(db)
    }

    @After
    fun cleanup() {
        val name = db.openHelper.databaseName!!
        db.close()
        context.deleteDatabase(name)
    }

    @Test
    fun directDatedRatesDoNotUseFutureDatesOrTriangulate() {
        val early = rate("80", day = today - 10)
        val current = rate("85")
        val tomorrow = current.copy(id = id(), day = today + 1, rate = "90")
        assertEquals(
            8000L,
            ExchangeRules.convert(100, "USD", "INR", today - 5, listOf(early, current, tomorrow))!!
                .minor,
        )
        assertEquals(
            8500L,
            ExchangeRules.convert(100, "USD", "INR", today, listOf(early, current, tomorrow))!!
                .minor,
        )
        assertNull(
            ExchangeRules.convert(
                100,
                "EUR",
                "INR",
                today,
                listOf(rate("2", "EUR", "USD"), current),
            )
        )
        assertNull(ExchangeRules.convert(100, "USD", "INR", today - 11, listOf(early)))
    }

    @Test
    fun inverseRatesRequireExplicitPolicyAndDirectTakesPriority() {
        val reverse = rate("4", "INR", "USD")
        assertNull(ExchangeRules.convert(100, "USD", "INR", today, listOf(reverse)))
        assertEquals(
            25L,
            ExchangeRules.convert(100, "USD", "INR", today, listOf(reverse), true)!!.minor,
        )
        val out =
            ExchangeRules.convert(100, "USD", "INR", today, listOf(reverse, rate("3")), true)!!
        assertEquals(300L, out.minor)
        assertFalse(out.inverse)
    }

    @Test
    fun roundingIsExactForZeroTwoAndThreeDecimalCurrencies() {
        assertEquals(
            100L,
            ExchangeRules.convert(
                    100,
                    "USD",
                    "INR",
                    today,
                    listOf(rate("1.005")),
                    rounding = "HALF_EVEN",
                )!!
                .minor,
        )
        assertEquals(
            101L,
            ExchangeRules.convert(
                    100,
                    "USD",
                    "INR",
                    today,
                    listOf(rate("1.005")),
                    rounding = "HALF_UP",
                )!!
                .minor,
        )
        assertEquals(
            151L,
            ExchangeRules.convert(
                    100,
                    "USD",
                    "JPY",
                    today,
                    listOf(rate("150.5", "USD", "JPY")),
                    rounding = "HALF_UP",
                )!!
                .minor,
        )
        assertEquals(
            1235L,
            ExchangeRules.convert(
                    100,
                    "USD",
                    "KWD",
                    today,
                    listOf(rate("1.2345", "USD", "KWD")),
                    rounding = "HALF_UP",
                )!!
                .minor,
        )
        assertEquals(
            -101L,
            ExchangeRules.convert(
                    -100,
                    "USD",
                    "INR",
                    today,
                    listOf(rate("1.005")),
                    rounding = "HALF_UP",
                )!!
                .minor,
        )
    }

    @Test
    fun invalidRatesAndOversizedConversionsAreRejected() {
        listOf("0", "-1", "NaN", "1e4", "1.1234567890123", "9999999999999", "https://example.com")
            .forEach { v -> invalid { ExchangeRules.validate(rate(v)) } }
        invalid { ExchangeRules.validate(rate(source = "USD", target = "USD")) }
        invalid { ExchangeRules.validate(rate(day = today + 1)) }
        invalid {
            ExchangeRules.convert(
                Money.MAX_MINOR,
                "USD",
                "INR",
                today,
                listOf(rate("999999999999")),
            )
        }
    }

    @Test
    fun zeroRateResultsAndIdentityCurrencyNeverCreateMoney() {
        assertEquals(0L, ExchangeRules.convert(0, "USD", "INR", today, listOf(rate()))!!.minor)
        assertNull(ExchangeRules.convert(100, "INR", "INR", today, emptyList()))
        invalid { ExchangeRules.convert(100, "USD", "INR", today, listOf(rate()), rounding = "UP") }
    }

    @Test
    fun rateRevisionDuplicateDateAndDeleteGuardsPreserveOtherRecords() =
        runBlocking<Unit> {
            val r = rate()
            repo.save(r, 0)
            repo.save(r, 0)
            assertEquals(1, repo.dao.rates().first().size)
            assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                runBlocking { repo.save(rate(), 0) }
            }
            repo.save(r.copy(rate = "84"), 1)
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.delete(r) } }
            val stored = repo.dao.rate(r.id)!!
            repo.delete(stored)
            assertTrue(repo.dao.rates().first().isEmpty())
        }

    @Test
    fun displayPreferencesPersistAndValidateWithoutChangingLedger() =
        runBlocking<Unit> {
            val store = DeviceOptions(context)
            val options =
                DisplayOptions(
                    locale = "hi-IN",
                    date = "dd/MM/yyyy",
                    zone = "Asia/Kolkata",
                    clock = "12",
                    currencyCode = true,
                    textScale = "1.2",
                    contrast = true,
                    haptics = false,
                    reduceMotion = true,
                    inverse = true,
                    rounding = "HALF_UP",
                )
            store.save(options)
            assertEquals(options, DeviceOptions(context).state.value)
            invalid { options.copy(zone = "bad/timezone").validate() }
            invalid { options.copy(date = "broken").validate() }
            invalid { options.copy(textScale = "0.5").validate() }
            context.getSharedPreferences("display_options", 0).edit().clear().commit()
        }

    @Test
    fun malformedStoredPreferencesFallBackSafely() {
        context
            .getSharedPreferences("display_options", 0)
            .edit()
            .putString("zone", "not/a/zone")
            .commit()
        assertEquals(DisplayOptions(), DeviceOptions(context).state.value)
        context.getSharedPreferences("display_options", 0).edit().clear().commit()
    }

    @Test
    fun dateClockAndZoneKeepEpochAccountingDatesStable() {
        val day = LocalDate.of(2024, 2, 29).toEpochDay()
        val options =
            DisplayOptions(
                locale = "en-GB",
                date = "dd/MM/yyyy",
                zone = "Asia/Kolkata",
                clock = "12",
            )
        assertEquals("29/02/2024", options.date(day))
        assertEquals(
            "1:30 am",
            options.time(Instant.parse("2024-02-28T20:00:00Z")).lowercase(Locale.ROOT),
        )
        assertEquals(day, LocalDate.of(2024, 2, 29).toEpochDay())
        assertTrue(Money.format(1234, "USD", Locale.US, true).contains("USD"))
    }

    private suspend fun seed(): String {
        val ledger = LedgerRepository(db)
        ledger.saveAccount(
            id(),
            "bank",
            "Bank, \"fixture\"",
            "BANK",
            "INR",
            100000,
            openingDate = today - 5,
        )
        ledger.saveAccount(id(), "usd", "USD", "BANK", "USD", 100000, openingDate = today - 5)
        val first = id()
        ledger.saveRecord(
            id(),
            first,
            "bank",
            "food",
            "EXPENSE",
            12345,
            today,
            "=SUM(A1)\nPrivate note",
        )
        ledger.saveRecord(id(), id(), "usd", "food", "EXPENSE", 2500, today - 1, "Other currency")
        return first
    }

    @Test
    fun typedJsonPreservesExactIntegersNullsIdsAndEscapedText() =
        runBlocking<Unit> {
            val record = seed()
            db.openHelper.writableDatabase.execSQL(
                "UPDATE outbox_operations SET createdAt=9223372036854775807 WHERE entityId=?",
                arrayOf(record),
            )
            val out = ByteArrayOutputStream()
            ReadableExports(db, TestGiftCipher()).json(out)
            val json = JSONObject(out.toString("UTF-8"))
            assertEquals("TRAXOS-TYPED-JSON", json.getString("format"))
            val tables = json.getJSONObject("tables")
            assertTrue(tables.has("manual_rates"))
            assertFalse(tables.has("card_vault"))
            val rows = tables.getJSONArray("transactions")
            val target =
                (0 until rows.length())
                    .map { rows.getJSONObject(it) }
                    .first { it.getJSONObject("id").getString("value") == record }
            assertEquals("12345", target.getJSONObject("amountMinor").getString("value"))
            assertEquals("integer", target.getJSONObject("amountMinor").getString("type"))
            assertEquals("=SUM(A1)\nPrivate note", target.getJSONObject("note").getString("value"))
            assertEquals("null", target.getJSONObject("linkId").getString("type"))
            val ops = tables.getJSONArray("outbox_operations")
            assertTrue(
                (0 until ops.length())
                    .map { ops.getJSONObject(it).getJSONObject("createdAt").getString("value") }
                    .contains(Long.MAX_VALUE.toString())
            )
        }

    @Test
    fun typedJsonExplicitSecretChoiceUsesPortablePayloadAndNeverVault() =
        runBlocking<Unit> {
            val cipher = TestGiftCipher()
            val card =
                GiftRepository(db, cipher, TestGiftScheduler())
                    .save(
                        GiftDraft(
                                merchant = "Fixture",
                                code = "SECRET-CODE-123",
                                original = "100",
                                remaining = "100",
                            )
                            .card(),
                        GiftSecret("SECRET-CODE-123", "1234", notes = "private"),
                        0,
                    )
            suspend fun export(include: Boolean): String {
                return ByteArrayOutputStream()
                    .also { ReadableExports(db, cipher).json(it, include) }
                    .toString("UTF-8")
            }
            val safe = JSONObject(export(false))
            val safeSecret =
                safe
                    .getJSONObject("tables")
                    .getJSONArray("gift_cards")
                    .getJSONObject(0)
                    .getJSONObject("secret")
            assertEquals("omitted", safeSecret.getString("type"))
            val full = JSONObject(export(true))
            val blob =
                full
                    .getJSONObject("tables")
                    .getJSONArray("gift_cards")
                    .getJSONObject(0)
                    .getJSONObject("secret")
            val payload =
                android.util.Base64.decode(blob.getString("value"), android.util.Base64.NO_WRAP)
            assertArrayEquals(cipher.open(card.id, db.gifts().card(card.id)!!.secret), payload)
            assertTrue(full.getBoolean("giftSecretsIncluded"))
            assertFalse(full.getJSONObject("tables").has("card_details"))
        }

    @Test
    fun csvScopesAreInclusiveAndTextIsFormulaSafe() =
        runBlocking<Unit> {
            seed()
            val out = ByteArrayOutputStream()
            ReadableExports(db, TestGiftCipher()).csv(out, ExportScope(today, today, "bank", "INR"))
            val csv = out.toString("UTF-8")
            assertTrue(csv.contains("123.45"))
            assertTrue(csv.contains("'=SUM(A1)"))
            assertFalse(csv.contains("Other currency"))
            assertTrue(csv.contains("\"Bank, \"\"fixture\"\"\""))
            assertTrue(csv.contains("Private note"))
        }

    @Test
    fun csvOmittedNotesAndDeletedFiltersAreExplicit() =
        runBlocking<Unit> {
            val record = seed()
            LedgerRepository(db).setDeleted(id(), record, 1, true)
            val a = ByteArrayOutputStream()
            val service = ReadableExports(db, TestGiftCipher())
            service.csv(a, ExportScope(currency = "INR"))
            assertFalse(a.toString("UTF-8").contains(record))
            val b = ByteArrayOutputStream()
            service.csv(b, ExportScope(currency = "INR", deleted = true, notes = false))
            assertTrue(b.toString("UTF-8").contains(record))
            assertFalse(b.toString("UTF-8").contains("Private note"))
            assertTrue(b.toString("UTF-8").contains("\"1\""))
        }

    @Test
    fun invalidExportRangesAndFailingStreamsDoNotMutateFinance() =
        runBlocking<Unit> {
            seed()
            invalid { ExportScope(today, today - 1).validate() }
            invalid { ExportScope(currency = "XYZ").validate() }
            val original = db.ledger().recordCount()
            assertThrows(IOException::class.java) {
                runBlocking {
                    ReadableExports(db, TestGiftCipher())
                        .json(
                            object : OutputStream() {
                                override fun write(b: Int) {
                                    throw IOException("Fixture disk full")
                                }
                            }
                        )
                }
            }
            assertEquals(original, db.ledger().recordCount())
        }

    @Test
    fun encryptedBackupPreservesManualRatesAcrossDeviceKeys() =
        runBlocking<Unit> {
            seed()
            val r = rate()
            repo.save(r, 0)
            val bytes =
                ByteArrayOutputStream()
                    .also {
                        PortableBackup(context, db, TestGiftCipher())
                            .write(it, "fixture-password".toCharArray())
                    }
                    .toByteArray()
            val name = "rate-restore-${id()}.db"
            val target = TraxosDatabase.open(context, name)
            try {
                val backup = PortableBackup(context, target, TestGiftCipher())
                backup.inspect(ByteArrayInputStream(bytes), "fixture-password".toCharArray()).use {
                    backup.restore(it)
                }
                assertEquals(r.rate, target.regional().rates().first().single().rate)
                assertEquals(2L, target.ledger().recordCount())
            } finally {
                target.close()
                context.deleteDatabase(name)
            }
        }

    @Test
    fun actualSchema14ArchiveRemainsReadableWithoutRates() =
        runBlocking<Unit> {
            seed()
            val bytes = ByteArrayOutputStream()
            BackupEnvelope.write(bytes, "fixture-password".toCharArray()) { out ->
                out.writeInt(1)
                out.writeInt(14)
                val tables = backupTables.filter { it != "manual_rates" }
                out.writeInt(tables.size)
                fun text(v: String) {
                    val b = v.toByteArray(Charsets.UTF_8)
                    out.writeInt(b.size)
                    out.write(b)
                }
                tables.forEach { table ->
                    text(table)
                    db.openHelper.readableDatabase.query("SELECT * FROM `$table`").use { c ->
                        out.writeLong(c.count.toLong())
                        while (c.moveToNext()) text(row(c).toString())
                    }
                }
                text("END")
            }
            val name = "old14-${id()}.db"
            val target = TraxosDatabase.open(context, name)
            try {
                val backup = PortableBackup(context, target, TestGiftCipher())
                backup
                    .inspect(
                        ByteArrayInputStream(bytes.toByteArray()),
                        "fixture-password".toCharArray(),
                    )
                    .use { backup.restore(it) }
                assertEquals(2L, target.ledger().recordCount())
                assertTrue(target.regional().rates().first().isEmpty())
            } finally {
                target.close()
                context.deleteDatabase(name)
            }
        }

    @Test
    fun migration14PreservesExistingDataAndCreatesEmptyRateTable() =
        runBlocking<Unit> {
            val name = "upgrade14-${id()}.db"
            val schema =
                JSONObject(
                        javaClass.classLoader!!
                            .getResourceAsStream("app.traxos.finance.data.TraxosDatabase/14.json")!!
                            .bufferedReader()
                            .use { it.readText() }
                    )
                    .getJSONObject("database")
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val e = entities.getJSONObject(i)
                    old.execSQL(
                        e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName"))
                    )
                    val indices = e.optJSONArray("indices") ?: JSONArray()
                    for (j in 0 until indices.length()) old.execSQL(
                        indices
                            .getJSONObject(j)
                            .getString("createSql")
                            .replace("\${TABLE_NAME}", e.getString("tableName"))
                    )
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
                old.execSQL(
                    "INSERT INTO accounts (id,name,type,currency,openingMinor,openingDate,archived,revision,profileId) VALUES ('keep','Keep','BANK','INR',12345,20000,0,1,'local')"
                )
                old.version = 14
            }
            val upgraded = TraxosDatabase.open(context, name)
            try {
                assertEquals(12345L, upgraded.ledger().accounts().first().single().balanceMinor)
                assertTrue(upgraded.regional().rates().first().isEmpty())
            } finally {
                upgraded.close()
                context.deleteDatabase(name)
            }
        }

    @Test
    fun exportsClassifyGeneratedAndMatchedLoanPrincipalWithoutChangingOriginalType() =
        runBlocking<Unit> {
            seed()
            val planning = PlanningExtensionsRepository(db)
            val generated =
                DebtThread(
                    id(),
                    "Person",
                    "LENT",
                    "INR",
                    10000,
                    10000,
                    today,
                    null,
                    "",
                    operationId = id(),
                )
            planning.saveDebt(generated, 0, "bank", null, null, false, "7,1,0")
            val original = id()
            LedgerRepository(db)
                .saveRecord(id(), original, "bank", "food", "EXPENSE", 8000, today, "Matched loan")
            planning.saveDebt(
                DebtThread(
                    id(),
                    "Other",
                    "LENT",
                    "INR",
                    8000,
                    8000,
                    today,
                    null,
                    "",
                    operationId = id(),
                ),
                0,
                "bank",
                original,
                null,
                false,
                "7,1,0",
            )
            val out = ByteArrayOutputStream()
            ReadableExports(db, TestGiftCipher())
                .csv(out, ExportScope(currency = "INR", notes = false))
            val csv = out.toString("UTF-8")
            assertTrue(csv.contains("classification"))
            assertEquals(2, csv.lineSequence().count { it.contains("Loan principal paid") })
            assertEquals(2, csv.lineSequence().count { it.contains("\"Loan principal\"") })
            assertEquals("EXPENSE", db.ledger().record(original)!!.type)
            assertTrue(csv.lineSequence().first { it.contains(original) }.contains("EXPENSE"))
        }
}
