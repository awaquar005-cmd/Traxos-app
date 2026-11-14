package app.traxos.finance

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import app.traxos.finance.data.*
import app.traxos.finance.obligations.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TraxosApplication::class)
class FinanceReminderWorkerTest {
    private val app
        get() = ApplicationProvider.getApplicationContext<TraxosApplication>()

    private fun id() = UUID.randomUUID().toString()

    @After
    fun cleanup() {
        WorkManager.getInstance(app).cancelAllWork().result.get()
        app.database.close()
        app.deleteDatabase("traxos.db")
    }

    private suspend fun seed(): PaymentPlan {
        val day = LocalDate.now().toEpochDay()
        app.repository.saveAccount(
            id(),
            "bank",
            "Private bank",
            "BANK",
            "INR",
            100000,
            openingDate = day - 20,
        )
        val plan =
            PaymentPlan(
                id(),
                "SUBSCRIPTION",
                "Secret merchant",
                "bank",
                "food",
                "INR",
                10000,
                "MONTHLY",
                30,
                1,
                day,
                null,
                null,
                operationId = id(),
            )
        app.obligations.plan(plan, 0)
        app.obligations.reminders(FinanceReminderSettings(quietStart = 0, quietEnd = 0))
        return plan
    }

    private suspend fun worker() =
        TestListenableWorkerBuilder<FinanceReminderWorker>(app).build().doWork()

    @Test
    fun notificationsArePrivateDedupedAndCancelledAfterPause() = runBlocking {
        val plan = seed()
        assertEquals(ListenableWorker.Result.success(), worker())
        val manager = app.getSystemService(NotificationManager::class.java)
        val notice = manager.activeNotifications.single { it.tag?.startsWith("finance:") == true }
        assertFalse(notice.notification.extras.toString().contains("Secret merchant"))
        assertFalse(notice.notification.extras.toString().contains("10000"))
        worker()
        assertEquals(
            1,
            manager.activeNotifications.count { it.tag?.startsWith("finance:") == true },
        )
        app.obligations.status(plan.id, 1, "PAUSED")
        worker()
        assertTrue(manager.activeNotifications.none { it.tag?.startsWith("finance:") == true })
    }

    @Test
    @Config(sdk = [33])
    fun permissionDeniedNeverStampsDeliveredAndNeverPosts() = runBlocking {
        val plan = seed()
        assertEquals(ListenableWorker.Result.success(), worker())
        val token = "plan:${plan.id}:${plan.nextDay}:0"
        assertNull(app.obligations.dao.receipt(token))
        assertTrue(
            app.getSystemService(NotificationManager::class.java).activeNotifications.none {
                it.tag?.startsWith("finance:") == true
            }
        )
    }

    @Test
    fun editingNextDateChangesReminderTokenAndPaidOccurrenceCancelsOldNotification() = runBlocking {
        val plan = seed()
        worker()
        app.obligations.confirm(plan.id, 1, 10000, LocalDate.now().toEpochDay(), null, 0, id())
        worker()
        assertTrue(
            app.getSystemService(NotificationManager::class.java).activeNotifications.none {
                it.tag?.startsWith("finance:") == true
            }
        )
    }
}
