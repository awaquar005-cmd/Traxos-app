package app.traxos.finance

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.traxos.finance.data.*
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PlansTest {
    private lateinit var db: TraxosDatabase
    private lateinit var repo: PlansRepository

    private fun id() = UUID.randomUUID().toString()

    @Before
    fun setup() {
        db = TraxosDatabase.open(ApplicationProvider.getApplicationContext(), "plans-${id()}.db")
        repo = PlansRepository(db)
    }

    @After
    fun cleanup() {
        val name = db.openHelper.databaseName!!
        db.close()
        ApplicationProvider.getApplicationContext<Application>().deleteDatabase(name)
    }

    @Test
    fun budgetsAreUniqueRevisionCheckedAndNeverCreateFinancialRecords() =
        runBlocking<Unit> {
            val operation = id()
            val month = YearMonth.now().toString()
            repo.budget(month, "INR", null, 300000, 0, operation)
            repo.budget(month, "INR", null, 300000, 0, operation)
            assertEquals(1, repo.budgets.first().size)
            assertEquals(0L, db.ledger().recordCount())
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repo.budget(month, "INR", null, 500000, 0, id()) }
            }
            val first = repo.budgets.first().single()
            repo.budget(month, "INR", null, 500000, first.revision, id())
            repo.budget(month, "USD", "food", 10000, 0, id())
            assertEquals(2, repo.budgets.first().size)
            repo.deleteBudget(first.id, 2)
            assertEquals("USD", repo.budgets.first().single().currency)
        }

    @Test
    fun invalidBudgetsKeepDataAndRejectIncomeCategories() =
        runBlocking<Unit> {
            for (category in listOf("salary", "missing")) assertThrows(
                IllegalArgumentException::class.java
            ) {
                runBlocking { repo.budget("2026-11", "INR", category, 10000, 0, id()) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repo.budget("bad", "INR", null, 10000, 0, id()) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repo.budget("2026-11", "INR", null, 0, 0, id()) }
            }
            assertTrue(repo.budgets.first().isEmpty())
        }

    @Test
    fun contributionsUndoAndArchiveNeverChangeBankBalances() =
        runBlocking<Unit> {
            LedgerRepository(db)
                .saveAccount(
                    id(),
                    "bank",
                    "Bank",
                    "BANK",
                    "INR",
                    100000,
                    openingDate = LocalDate.now().toEpochDay(),
                )
            val goal = id()
            repo.goal(goal, "Laptop", "INR", 500000, 0, id())
            val operation = id()
            repo.contribute(goal, 1, 25000, operation)
            repo.contribute(goal, 1, 25000, operation)
            assertEquals(25000L, db.plans().goal(goal)!!.savedMinor)
            repo.contribute(goal, 2, -5000, id())
            assertEquals(20000L, db.plans().goal(goal)!!.savedMinor)
            val undo = id()
            repo.undo(goal, 3, undo)
            repo.undo(goal, 3, undo)
            assertEquals(25000L, db.plans().goal(goal)!!.savedMinor)
            assertEquals(100000L, db.ledger().accounts().first().single().balanceMinor)
            assertEquals(0L, db.ledger().recordCount())
            repo.archive(goal, 4, true)
            assertEquals(3, db.plans().movementCount(goal))
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repo.contribute(goal, 5, 1000, id()) }
            }
        }

    @Test
    fun goalCurrencyAndStaleWritesAreProtectedAcrossRestart() =
        runBlocking<Unit> {
            val goal = id()
            repo.goal(goal, "Emergency", "INR", 100000, 0, id())
            repo.contribute(goal, 1, 10000, id())
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repo.goal(goal, "Emergency", "USD", 100000, 2, id()) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repo.contribute(goal, 1, 1000, id()) }
            }
            val name = db.openHelper.databaseName!!
            db.close()
            db = TraxosDatabase.open(ApplicationProvider.getApplicationContext(), name)
            repo = PlansRepository(db)
            assertEquals(10000L, repo.goals.first().single().savedMinor)
            assertEquals(1, db.plans().movementCount(goal))
        }

    @Test
    fun goalBoundsAndConcurrentDuplicateOperationsAreExact() =
        runBlocking<Unit> {
            val goal = id()
            repo.goal(goal, "Goal", "INR", 100000, 0, id())
            val operation = id()
            coroutineScope { repeat(10) { launch { repo.contribute(goal, 1, 10000, operation) } } }
            assertEquals(10000L, db.plans().goal(goal)!!.savedMinor)
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repo.contribute(goal, 2, 20000, operation) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repo.contribute(goal, 2, -20000, id()) }
            }
            assertEquals(10000L, db.plans().goal(goal)!!.savedMinor)
        }
}
