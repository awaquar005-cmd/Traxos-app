package app.traxos.finance

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import app.traxos.finance.gifts.*
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TraxosApplication::class)
class GiftReminderWorkerTest {
    private val app
        get() = ApplicationProvider.getApplicationContext<TraxosApplication>()

    @After
    fun cleanup() {
        WorkManager.getInstance(app).cancelAllWork().result.get()
        app.database.close()
        app.deleteDatabase("traxos.db")
    }

    @Test
    fun workerPostsPrivateNotificationOnceAndRejectsStaleRevision() = runBlocking {
        val repo = GiftRepository(app.database, TestGiftCipher())
        val today = LocalDate.now().toEpochDay()
        val card =
            repo.save(
                GiftDraft(
                        merchant = "Private merchant",
                        name = "Gift",
                        code = "SECRET-1234",
                        original = "1000",
                        remaining = "1000",
                        expiry = LocalDate.now().toString(),
                    )
                    .card(),
                GiftSecret("SECRET-1234", "4444"),
                0,
            )
        val token = "${card.id}:$today:0"
        fun worker(revision: Long) =
            TestListenableWorkerBuilder<GiftExpiryWorker>(app)
                .setInputData(
                    workDataOf(
                        "card" to card.id,
                        "revision" to revision,
                        "expiry" to today,
                        "offset" to 0,
                        "token" to token,
                    )
                )
                .build()
        assertEquals(ListenableWorker.Result.success(), worker(0).doWork())
        assertEquals(0, repo.dao.notified(token))
        assertEquals(ListenableWorker.Result.success(), worker(1).doWork())
        assertEquals(1, repo.dao.notified(token))
        worker(1).doWork()
        val notifications =
            app.getSystemService(NotificationManager::class.java).activeNotifications.filter {
                it.tag == token
            }
        assertEquals(1, notifications.size)
        val extras = notifications.single().notification.extras.toString()
        assertFalse(extras.contains("SECRET-1234"))
        assertFalse(extras.contains("4444"))
        assertFalse(extras.contains("Private merchant"))
        assertEquals(1, repo.dao.notified(token))
        repo.delete(card.id, 1)
        worker(1).doWork()
        assertEquals(1, repo.dao.notified(token))
    }

    @Test
    fun reconcileReplacesEditedScheduleAndCancelsRedeemedCards() = runBlocking {
        val repo = GiftRepository(app.database, TestGiftCipher())
        val future = LocalDate.now().plusDays(40)
        var card =
            repo.save(
                GiftDraft(
                        merchant = "Store",
                        name = "Gift",
                        code = "PLAN-1234",
                        original = "1000",
                        remaining = "1000",
                        expiry = future.toString(),
                    )
                    .card(),
                GiftSecret("PLAN-1234"),
                0,
            )
        val manager = WorkManager.getInstance(app)
        suspend fun reconcile() {
            assertEquals(
                ListenableWorker.Result.success(),
                TestListenableWorkerBuilder<GiftReconcileWorker>(app).build().doWork(),
            )
        }
        reconcile()
        // A sentinel enqueued operation drains the same serial task executor before inspection.
        manager
            .enqueue(
                OneTimeWorkRequestBuilder<GiftReconcileWorker>()
                    .setInitialDelay(10, java.util.concurrent.TimeUnit.DAYS)
                    .addTag("sentinel")
                    .build()
            )
            .result
            .get()
        var scheduled =
            manager.getWorkInfosByTag("gift:${card.id}").get().filter { !it.state.isFinished }
        assertEquals(4, scheduled.size)
        card =
            repo.save(
                card.copy(expiryDay = future.plusDays(1).toEpochDay()),
                GiftSecret("PLAN-1234"),
                1,
            )
        reconcile()
        manager
            .enqueue(
                OneTimeWorkRequestBuilder<GiftReconcileWorker>()
                    .setInitialDelay(10, java.util.concurrent.TimeUnit.DAYS)
                    .build()
            )
            .result
            .get()
        scheduled =
            manager.getWorkInfosByTag("gift:${card.id}").get().filter { !it.state.isFinished }
        assertEquals(4, scheduled.size)
        repo.redeem(card.id, 2, 100000, "full")
        reconcile()
        assertTrue(manager.getWorkInfosByTag("gift:${card.id}").get().all { it.state.isFinished })
    }

    @Test
    @Config(sdk = [35])
    fun deniedNotificationPermissionDoesNotConsumeReminder() = runBlocking {
        val repo = GiftRepository(app.database, TestGiftCipher())
        val today = LocalDate.now().toEpochDay()
        val c =
            repo.save(
                GiftDraft(
                        merchant = "Store",
                        name = "Voucher",
                        code = "PERMISSION-1234",
                        expiry = LocalDate.now().toString(),
                    )
                    .card(),
                GiftSecret("PERMISSION-1234"),
                0,
            )
        val token = "${c.id}:$today:0"
        fun worker() =
            TestListenableWorkerBuilder<GiftExpiryWorker>(app)
                .setInputData(
                    workDataOf(
                        "card" to c.id,
                        "revision" to c.revision,
                        "expiry" to today,
                        "offset" to 0,
                        "token" to token,
                    )
                )
                .build()
        org.robolectric.Shadows.shadowOf(app)
            .denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        worker().doWork()
        assertEquals(0, repo.dao.notified(token))
        org.robolectric.Shadows.shadowOf(app)
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        worker().doWork()
        assertEquals(1, repo.dao.notified(token))
    }

    @Test
    fun blockedNotificationChannelDoesNotConsumeReminder() = runBlocking {
        val repo = GiftRepository(app.database, TestGiftCipher())
        val today = LocalDate.now().toEpochDay()
        val c =
            repo.save(
                GiftDraft(
                        merchant = "Store",
                        name = "Voucher",
                        code = "CHANNEL-1234",
                        expiry = LocalDate.now().toString(),
                    )
                    .card(),
                GiftSecret("CHANNEL-1234"),
                0,
            )
        val token = "${c.id}:$today:0"
        app.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                android.app.NotificationChannel(
                    "gift-expiry",
                    "Gift card expiry",
                    NotificationManager.IMPORTANCE_NONE,
                )
            )
        val worker =
            TestListenableWorkerBuilder<GiftExpiryWorker>(app)
                .setInputData(
                    workDataOf(
                        "card" to c.id,
                        "revision" to c.revision,
                        "expiry" to today,
                        "offset" to 0,
                        "token" to token,
                    )
                )
                .build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(0, repo.dao.notified(token))
    }
}
