package app.traxos.finance.obligations

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import app.traxos.finance.MainActivity
import app.traxos.finance.R
import app.traxos.finance.TraxosApplication
import app.traxos.finance.data.*
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object FinanceReminderPlan {
    data class Due(val token: String, val target: String, val dueDay: Long, val offset: Int)

    fun quiet(settings: FinanceReminderSettings, hour: Int) =
        settings.quietStart != settings.quietEnd &&
            if (settings.quietStart < settings.quietEnd)
                hour in settings.quietStart until settings.quietEnd
            else hour >= settings.quietStart || hour < settings.quietEnd

    fun due(target: String, day: Long, offsets: String, today: Long): List<Due> =
        PlanDates.offsets(offsets)
            .filter { day - it == today || it == 0 && day < today }
            .map { Due("$target:$day:$it", target, day, it) }
}

class FinanceScheduler(private val context: Context) {
    fun start() {
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "finance-reminders-periodic",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<FinanceReminderWorker>(6, TimeUnit.HOURS).build(),
            )
        kick()
    }

    fun kick() {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "finance-reminders-reconcile",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<FinanceReminderWorker>().build(),
            )
    }
}

private val financeReminderMutex = Mutex()

class FinanceReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        financeReminderMutex.withLock {
            try {
                val app = applicationContext as TraxosApplication
                val dao = app.database.obligations()
                val settings = dao.reminderSettings().first() ?: FinanceReminderSettings()
                val manager = applicationContext.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(
                    NotificationChannel(
                        "finance_due",
                        "Bills and card reminders",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    )
                )
                val today = LocalDate.now().toEpochDay()
                val eligible = mutableListOf<FinanceReminderPlan.Due>()
                if (settings.enabled) {
                    val cycles = dao.cycles().first().associateBy { it.accountId }
                    val accounts =
                        app.database.ledger().accounts().first().associateBy { it.account.id }
                    dao.statements()
                        .first()
                        .filter {
                            !it.voided &&
                                cycles[it.accountId]?.reminders == true &&
                                accounts[it.accountId]?.account?.archived == false
                        }
                        .forEach {
                            if (PlanDates.remaining(it, dao.paid(it.id)) > 0)
                                eligible +=
                                    FinanceReminderPlan.due(
                                        "statement:${it.id}",
                                        it.dueDay,
                                        cycles.getValue(it.accountId).offsets,
                                        today,
                                    )
                        }
                    dao.plans()
                        .first()
                        .filter {
                            it.status == "ACTIVE" &&
                                it.reminders &&
                                accounts[it.accountId]?.account?.archived == false
                        }
                        .forEach {
                            eligible +=
                                FinanceReminderPlan.due(
                                    "plan:${it.id}",
                                    minOf(it.nextDay, it.trialEnd ?: it.nextDay),
                                    it.offsets,
                                    today,
                                )
                        }
                }
                val tags = eligible.map { it.token }.toSet()
                manager.activeNotifications
                    .filter {
                        it.tag?.startsWith("finance:") == true &&
                            it.tag!!.removePrefix("finance:") !in tags
                    }
                    .forEach { manager.cancel(it.tag, it.id) }
                val allowed =
                    Build.VERSION.SDK_INT < 33 ||
                        ContextCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) == PackageManager.PERMISSION_GRANTED
                if (
                    allowed &&
                        NotificationManagerCompat.from(applicationContext)
                            .areNotificationsEnabled() &&
                        manager.getNotificationChannel("finance_due")?.importance !=
                            NotificationManager.IMPORTANCE_NONE &&
                        !FinanceReminderPlan.quiet(settings, LocalTime.now().hour)
                ) {
                    val intent =
                        PendingIntent.getActivity(
                            applicationContext,
                            0,
                            Intent(applicationContext, MainActivity::class.java),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    eligible.forEach { due ->
                        if (dao.receipt(due.token) == null) {
                            val notification =
                                NotificationCompat.Builder(applicationContext, "finance_due")
                                    .setSmallIcon(R.drawable.traxos_mark)
                                    .setContentTitle("Traxos · payment reminder")
                                    .setContentText(
                                        if (due.dueDay < today)
                                            "A payment is overdue. Open Traxos to review it."
                                        else "A payment is coming up. Open Traxos to review it."
                                    )
                                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                                    .setPublicVersion(
                                        NotificationCompat.Builder(
                                                applicationContext,
                                                "finance_due",
                                            )
                                            .setSmallIcon(R.drawable.traxos_mark)
                                            .setContentTitle("Traxos reminder")
                                            .setContentText("Open the app to review.")
                                            .build()
                                    )
                                    .setContentIntent(intent)
                                    .setAutoCancel(true)
                                    .setOnlyAlertOnce(true)
                                    .build()
                            manager.notify("finance:${due.token}", 101, notification)
                            dao.insert(
                                FinanceReminderReceipt(due.token, System.currentTimeMillis())
                            )
                        }
                    }
                }
                Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.retry()
            }
        }
}
