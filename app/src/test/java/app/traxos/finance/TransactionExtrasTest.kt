package app.traxos.finance

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.*
import app.traxos.finance.entry.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TransactionExtrasTest {
    private lateinit var db: TraxosDatabase
    private val context
        get() = ApplicationProvider.getApplicationContext<Application>()

    private fun id() = UUID.randomUUID().toString()

    private val day = LocalDate.of(2026, 11, 14).toEpochDay()

    @Before
    fun setup() {
        db = TraxosDatabase.open(context, "extras-${id()}.db")
    }

    @After
    fun cleanup() {
        val name = db.openHelper.databaseName!!
        db.close()
        context.deleteDatabase(name)
    }

    private suspend fun seed(): LedgerRepository {
        val r = LedgerRepository(db)
        r.saveAccount(id(), "bank", "Bank", "BANK", "INR", 10000, openingDate = day - 10)
        db.ledger().insertCategory(Category("extra-food", "Food", "EXPENSE"))
        db.ledger().insertCategory(Category("extra-trip", "Trip", "EXPENSE"))
        return r
    }

    @Test
    fun splitWritesOneMovementAndAllocatesReportsExactly() = runBlocking {
        val r = seed()
        val record = id()
        val op = id()
        val parts = listOf(SplitPart("extra-food", 301), SplitPart("extra-trip", 700))
        r.saveRecord(
            op,
            record,
            "bank",
            "extra-food",
            "EXPENSE",
            1001,
            day,
            "Split",
            splits = parts,
        )
        r.saveRecord(
            op,
            record,
            "bank",
            "extra-food",
            "EXPENSE",
            1001,
            day,
            "Split",
            splits = parts,
        )
        assertEquals(2, db.extras().splits(record).size)
        val points = db.trends().points(day, day).first().single()
        assertEquals(1001L, points.expenseMinor)
        assertEquals(1L, points.entries)
        val cats =
            db.trends().categories(day, day).first().associate { it.categoryId to it.expenseMinor }
        assertEquals(mapOf("extra-food" to 301L, "extra-trip" to 700L), cats)
        val cursor =
            db.openHelper.readableDatabase.query(
                "SELECT COUNT(*),SUM(amountMinor) FROM transactions WHERE id='$record'"
            )
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
            assertEquals(1001, it.getLong(1))
        }
        r.saveRecord(
            id(),
            record,
            "bank",
            "extra-food",
            "EXPENSE",
            1001,
            day,
            "Edit",
            expectedRevision = 1,
        )
        assertEquals(2, db.extras().splits(record).size)
        r.setDeleted(id(), record, 2, true)
        assertTrue(db.trends().points(day, day).first().isEmpty())
        r.setDeleted(id(), record, 3, false)
        assertEquals(
            1001L,
            db.trends().points(day, day, split = "SPLIT").first().single().expenseMinor,
        )
        assertTrue(db.trends().points(day, day, split = "SINGLE").first().isEmpty())
    }

    @Test
    fun badSplitsRollbackEntireWrite() = runBlocking {
        val r = seed()
        val record = id()
        try {
            r.saveRecord(
                id(),
                record,
                "bank",
                "extra-food",
                "EXPENSE",
                1000,
                day,
                "",
                splits = listOf(SplitPart("extra-food", 300), SplitPart("extra-trip", 600)),
            )
            fail("must reject")
        } catch (_: IllegalArgumentException) {}
        assertNull(db.ledger().record(record))
        assertTrue(db.extras().splits(record).isEmpty())
    }

    @Test
    fun rulesUseWholeWordsScopeAndSpecificity() {
        val cats = listOf(Category("a", "Food", "EXPENSE"), Category("b", "Other", "EXPENSE"))
        val broad = MerchantRule("b", "mart", "CONTAINS", "EXPENSE", "b")
        val exact = MerchantRule("a", "fresh mart", "EXACT", "EXPENSE", "a")
        assertEquals(
            "a",
            EntryRules.match("ＦＲＥＳＨ—Mart", "EXPENSE", "bank", listOf(broad, exact), cats)!!
                .categoryId,
        )
        assertNull(EntryRules.match("smart", "EXPENSE", "bank", listOf(broad), cats))
        assertNull(EntryRules.match("mart", "INCOME", "bank", listOf(broad), cats))
        assertNull(
            EntryRules.match(
                "mart",
                "EXPENSE",
                "other",
                listOf(broad.copy(accountId = "bank")),
                cats,
            )
        )
        assertNull(
            EntryRules.match(
                "mart",
                "EXPENSE",
                "bank",
                listOf(broad),
                cats.map { it.copy(archived = true) },
            )
        )
    }

    @Test
    fun splitValidationRejectsDuplicatesAndArchivedNewReferences() {
        val cats =
            listOf(Category("a", "A", "EXPENSE"), Category("b", "B", "EXPENSE", archived = true))
        val parts = listOf(SplitPart("a", 50), SplitPart("b", 50))
        assertThrows(IllegalArgumentException::class.java) {
            EntryRules.validateSplits("EXPENSE", 100, parts, cats)
        }
        EntryRules.validateSplits("REFUND", 100, parts, cats, setOf("b"))
        assertThrows(IllegalArgumentException::class.java) {
            EntryRules.validateSplits("INCOME", 100, parts, cats, setOf("b"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EntryRules.validateSplits(
                "EXPENSE",
                100,
                listOf(SplitPart("a", 50), SplitPart("a", 50)),
                cats,
            )
        }
    }

    @Test
    fun chartZeroFillsAndPreservesRefundsAndCurrencyBoundaries() {
        val rows = listOf(TrendPoint(day, "INR", 100, 300, 50, 1))
        val buckets = TrendMath.buckets(rows, day - 1, day + 1)
        assertEquals(3, buckets.size)
        assertEquals(0L, buckets.first().spending)
        assertEquals(-200L, buckets[1].spending)
        assertEquals(50L, buckets[1].income)
        assertThrows(IllegalArgumentException::class.java) {
            TrendMath.buckets(rows + rows.single().copy(currency = "USD"), day, day)
        }
    }

    @Test
    fun comparisonsAndAmountFiltersAreExplicit() {
        val f = TrendFilters("2024-02-29", "2024-02-29", comparison = "YEAR").validate()
        assertEquals(LocalDate.of(2023, 2, 28).toEpochDay(), f.previous().first)
        assertEquals(f, TrendFilters.read(f.saved()))
        assertNull(TrendFilters.change(100, 0))
        assertEquals("50.0%", TrendFilters.change(150, 100))
        assertThrows(IllegalArgumentException::class.java) {
            TrendFilters(minimum = "1").validate()
        }
    }

    @Test
    fun csvAllocationsHaveOneParentRowAndExactDetails() = runBlocking {
        val r = seed()
        val record = id()
        r.saveRecord(
            id(),
            record,
            "bank",
            "extra-food",
            "EXPENSE",
            1001,
            day,
            "Split",
            splits = listOf(SplitPart("extra-food", 301), SplitPart("extra-trip", 700)),
        )
        val out = java.io.ByteArrayOutputStream()
        app.traxos.finance.backup
            .ReadableExports(db, TestGiftCipher())
            .csv(out, app.traxos.finance.backup.ExportScope())
        val text = out.toString("UTF-8")
        assertTrue(text.contains("category_allocations"))
        assertTrue(text.contains("Split categories"))
        assertTrue(text.contains("Food: 3.01 INR"))
        assertTrue(text.contains("Trip: 7.00 INR"))
        assertEquals(2, text.lineSequence().filter { it.isNotBlank() }.count())
    }

    @Test
    fun parentAmountBoundsAndCurrencyAndAllocationFiltersDoNotMultiplyTotals() = runBlocking {
        val r = seed()
        val record = id()
        r.saveRecord(
            id(),
            record,
            "bank",
            "extra-food",
            "EXPENSE",
            1001,
            day,
            "",
            splits = listOf(SplitPart("extra-food", 301), SplitPart("extra-trip", 700)),
        )
        assertTrue(db.trends().points(day, day, maximum = 1000).first().isEmpty())
        assertTrue(db.trends().points(day, day, currency = "USD").first().isEmpty())
        assertEquals(
            301L,
            db.trends()
                .points(day, day, category = "extra-food", minimum = 1001)
                .first()
                .single()
                .expenseMinor,
        )
        assertTrue(db.trends().points(day, day, receipt = "HAS").first().isEmpty())
        assertEquals(
            1001L,
            db.trends().points(day, day, receipt = "NONE").first().single().expenseMinor,
        )
    }

    @Test
    fun groupSelfShareReportsMatchSummaryWithoutCountingReimbursementsAsIncome() = runBlocking {
        val r = seed()
        val groups = GroupsRepository(db)
        val groupId = id()
        groups.create(groupId, "Trip", "INR", "Me", id())
        val group = db.groups().group(groupId)!!
        val member = GroupMember(id(), groupId, "Other", operationId = id())
        groups.member(member, 0)
        val source = id()
        r.saveRecord(id(), source, "bank", "food", "EXPENSE", 1000, day, "")
        groups.expense(
            groupId,
            group.selfId,
            1000,
            "EQUAL",
            mapOf(group.selfId to "1", member.id to "1"),
            day,
            "Dinner",
            source,
            id(),
            "food",
        )
        val point = db.trends().points(day, day).first().single()
        assertEquals(500L, point.expenseMinor)
        assertEquals(1L, point.entries)
        assertEquals(
            500L,
            db.trends()
                .points(day, day, account = "bank", origin = "GROUP")
                .first()
                .single()
                .expenseMinor,
        )
        val payment = id()
        r.saveRecord(id(), payment, "bank", "salary", "INCOME", 500, day, "")
        groups.settle(groupId, member.id, group.selfId, 500, day, "Paid", payment, id())
        val settled = db.trends().points(day, day).first().single()
        assertEquals(0L, settled.incomeMinor)
        assertEquals(1L, settled.entries)
        assertEquals(
            db.ledger().summary(day, day + 1).first().single().expenseMinor,
            settled.expenseMinor,
        )
    }
}
