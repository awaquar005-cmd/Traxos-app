package app.traxos.finance

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import app.traxos.finance.data.*
import app.traxos.finance.obligations.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TraxosApplication::class)
class PlanningAlertsWorkerTest {
    private val app
        get() = ApplicationProvider.getApplicationContext<TraxosApplication>()

    private val today = LocalDate.now().toEpochDay()

    private fun id() = UUID.randomUUID().toString()

    @After
    fun cleanup() {
        WorkManager.getInstance(app).cancelAllWork().result.get()
        app.database.close()
        app.deleteDatabase("traxos.db")
    }

    private suspend fun worker() =
        TestListenableWorkerBuilder<FinanceReminderWorker>(app).build().doWork()

    private fun notes() =
        app.getSystemService(NotificationManager::class.java).activeNotifications.filter {
            it.tag?.startsWith("finance:") == true
        }

    private suspend fun setup() {
        app.obligations.reminders(FinanceReminderSettings(quietStart = 0, quietEnd = 0))
        app.repository.saveAccount(
            id(),
            "bank",
            "Private bank",
            "BANK",
            "INR",
            500000,
            openingDate = today - 100,
        )
    }

    @Test
    fun debtAndGoalAlertsArePrivateDedupedAndCancelledWhenRescheduledOrReached() = runBlocking {
        setup()
        val p = id()
        app.planning.person(p, "Private contact", "+919999999999", 0, id())
        val d =
            DebtThread(
                id(),
                "Private contact",
                "LENT",
                "INR",
                10000,
                10000,
                today,
                today,
                "Private reason",
                operationId = id(),
            )
        app.planning.saveDebt(d, 0, null, null, p, true, "0")
        val goal = id()
        app.planning.goal(goal, "Private goal", "INR", 10000, 0, id(), today, true, "0")
        assertEquals(ListenableWorker.Result.success(), worker())
        assertEquals(2, notes().size)
        worker()
        assertEquals(2, notes().size)
        for (note in notes()) {
            val text = note.notification.extras.toString()
            assertFalse(text.contains("Private contact"))
            assertFalse(text.contains("Private goal"))
            assertFalse(text.contains("9999999999"))
            assertFalse(text.contains("10000"))
        }
        app.planning.saveDebt(
            d.copy(dueDay = today + 30, operationId = id()),
            1,
            null,
            null,
            p,
            true,
            "0",
        )
        app.planning.contribution(goal, 1, 10000, null, null, today, id())
        worker()
        assertTrue(notes().isEmpty())
    }

    @Test
    fun monthlyThresholdsNotifyOncePerPeriodAndRefundsCancelVisibleWarnings() = runBlocking {
        setup()
        app.repository.saveAccount(
            id(),
            "card",
            "Card",
            "CREDIT_CARD",
            "INR",
            0,
            openingDate = today - 100,
        )
        app.planning.monthly(
            YearMonth.now().toString(),
            "INR",
            null,
            10000,
            0,
            id(),
            true,
            "80,100",
        )
        app.repository.saveRecord(id(), id(), "card", "food", "EXPENSE", 8000, today, "Purchase")
        worker()
        assertEquals(1, notes().size)
        worker()
        assertEquals(1, notes().size)
        app.repository.saveRecord(id(), id(), "card", "food", "EXPENSE", 3000, today, "Purchase")
        worker()
        assertEquals(2, notes().size)
        app.repository.saveRecord(id(), id(), "card", "food", "REFUND", 5000, today, "Refund")
        worker()
        assertTrue(notes().isEmpty())
        app.repository.saveRecord(id(), id(), "card", "food", "EXPENSE", 6000, today, "Purchase")
        worker()
        assertTrue(notes().isEmpty())
    }

    @Test
    @Config(sdk = [33])
    fun deniedPermissionDoesNotCreateDebtGoalOrBudgetDeliveryReceipts() = runBlocking {
        setup()
        val d =
            DebtThread(
                id(),
                "Person",
                "LENT",
                "INR",
                10000,
                10000,
                today,
                today,
                "",
                operationId = id(),
            )
        app.planning.saveDebt(d, 0, null, null, null, true, "0")
        app.planning.monthly(YearMonth.now().toString(), "INR", null, 10000, 0, id(), true, "80")
        app.repository.saveRecord(id(), id(), "bank", "food", "EXPENSE", 8000, today, "Purchase")
        worker()
        assertTrue(notes().isEmpty())
        assertNull(app.obligations.dao.receipt("debt:${d.id}:$today:0"))
    }
}
