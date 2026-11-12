package app.traxos.finance.gifts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import androidx.work.*
import app.traxos.finance.MainActivity
import app.traxos.finance.R
import app.traxos.finance.TraxosApplication
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object GiftReminderPlan {
    data class Due(val offset: Int, val day: Long, val token: String)

    fun entries(card: GiftCard, settings: GiftSettings, today: Long): List<Due> {
        val expiry = card.expiryDay ?: return emptyList()
        if (
            !settings.reminders ||
                card.archived ||
                card.deleted ||
                card.manuallyExpired ||
                card.redeemed ||
                card.remainingMinor == 0L
        )
            return emptyList()
        return GiftRules.intervals(settings.intervals).mapNotNull { offset ->
            val day = expiry - offset
            if (day < today && !(offset == 0 && expiry < today)) null
            else Due(offset, maxOf(day, today), "${card.id}:$expiry:$offset")
        }
    }

    fun eligible(
        card: GiftCard,
        settings: GiftSettings,
        expiry: Long,
        offset: Int,
        today: Long,
    ): Boolean =
        card.expiryDay == expiry &&
            entries(card, settings, today).any { it.offset == offset && it.day <= today }
}

class AndroidGiftScheduler(private val context: Context) : GiftScheduler {
    override fun reconcile() {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "gift-reconcile",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<GiftReconcileWorker>().build(),
            )
    }

    override fun cancel(cardId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag("gift:$cardId")
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications
            .filter { it.tag?.startsWith("$cardId:") == true }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    fun start() {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            "gift-daily-reconcile",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<GiftReconcileWorker>(24, TimeUnit.HOURS).build(),
        )
        reconcile()
    }
}

private val giftPlanMutex = Mutex()

class GiftReconcileWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        giftPlanMutex.withLock {
            try {
                val app = applicationContext as TraxosApplication
                val dao = app.database.gifts()
                val settings = dao.settings().first() ?: GiftSettings()
                val now = java.time.ZonedDateTime.now()
                val wm = WorkManager.getInstance(applicationContext)
                // Expiry work carries a revision and rechecks settings/card state before posting.
                // New requests replace existing names; obsolete requests are cancelled first.
                wm.cancelAllWorkByTag("gift-expiry").result.get()
                dao.all().forEach { card ->
                    GiftReminderPlan.entries(card, settings, now.toLocalDate().toEpochDay())
                        .forEach { due ->
                            if (dao.notified(due.token) == 0) {
                                val at =
                                    LocalDate.ofEpochDay(due.day)
                                        .atTime(LocalTime.of(9, 0))
                                        .atZone(ZoneId.systemDefault())
                                val delay = maxOf(0, java.time.Duration.between(now, at).toMillis())
                                val data =
                                    workDataOf(
                                        "card" to card.id,
                                        "revision" to card.revision,
                                        "expiry" to card.expiryDay!!,
                                        "offset" to due.offset,
                                        "token" to due.token,
                                    )
                                wm.enqueueUniqueWork(
                                    "gift-expiry:${due.token}",
                                    ExistingWorkPolicy.REPLACE,
                                    OneTimeWorkRequestBuilder<GiftExpiryWorker>()
                                        .setInputData(data)
                                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                                        .addTag("gift-expiry")
                                        .addTag("gift:${card.id}")
                                        .build(),
                                )
                            }
                        }
                }
                Result.success()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
}

class GiftExpiryWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("card") ?: return Result.failure()
        val token = inputData.getString("token") ?: return Result.failure()
        val expiry = inputData.getLong("expiry", Long.MIN_VALUE)
        val offset = inputData.getInt("offset", -1)
        val notifications = NotificationManagerCompat.from(applicationContext)
        if (
            !notifications.areNotificationsEnabled() ||
                (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED)
        )
            return Result.success()
        return try {
            val app = applicationContext as TraxosApplication
            val db = app.database
            db.withTransaction {
                val dao = db.gifts()
                val card = dao.card(id) ?: return@withTransaction
                val settings = dao.settings().first() ?: GiftSettings()
                if (
                    card.revision != inputData.getLong("revision", -1) ||
                        !GiftReminderPlan.eligible(
                            card,
                            settings,
                            expiry,
                            offset,
                            LocalDate.now().toEpochDay(),
                        ) ||
                        dao.notified(token) > 0
                )
                    return@withTransaction
                val manager = applicationContext.getSystemService(NotificationManager::class.java)
                if (
                    manager.getNotificationChannel("gift-expiry")?.importance ==
                        NotificationManager.IMPORTANCE_NONE
                )
                    return@withTransaction
                manager.createNotificationChannel(
                    NotificationChannel(
                            "gift-expiry",
                            "Gift card expiry",
                            NotificationManager.IMPORTANCE_DEFAULT,
                        )
                        .apply { description = "Private reminders for your gift wallet" }
                )
                val intent =
                    Intent(applicationContext, MainActivity::class.java)
                        .putExtra("open_gifts", true)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val pending =
                    PendingIntent.getActivity(
                        applicationContext,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                val wording =
                    when {
                        expiry < LocalDate.now().toEpochDay() ->
                            "A gift card has passed its expiry date. Review it in your wallet."
                        offset == 0 ->
                            "A gift card reaches its expiry date. Check your wallet before using it."
                        offset == 1 -> "A gift card expires tomorrow. Review it in your wallet."
                        else -> "A gift card expires in $offset days. Plan a visit to your wallet."
                    }
                // Generic text deliberately excludes merchant, amount, code and PIN; stable tag
                // replaces on retry.
                val notification =
                    NotificationCompat.Builder(applicationContext, "gift-expiry")
                        .setSmallIcon(R.drawable.traxos_mark)
                        .setContentTitle("TRAXOS · Gift wallet")
                        .setContentText(wording)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(wording))
                        .setContentIntent(pending)
                        .setAutoCancel(true)
                        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                        .build()
                manager.notify(token, 0, notification)
                dao.notified(GiftNotification(token, System.currentTimeMillis()))
            }
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
