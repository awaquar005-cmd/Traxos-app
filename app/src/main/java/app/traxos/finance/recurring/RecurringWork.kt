package app.traxos.finance.recurring

import android.content.Context
import androidx.work.*
import app.traxos.finance.TraxosApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class RecurringWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        try {
            if ((applicationContext as TraxosApplication).recurring.runDue()) Result.retry()
            else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
}

class RecurringScheduler(private val context: Context) {
    fun start() {
        val work = WorkManager.getInstance(context)
        work.enqueueUniquePeriodicWork(
            "traxos-recurring-daily",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RecurringWorker>(1, TimeUnit.DAYS).build(),
        )
        kick()
    }

    fun kick() {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "traxos-recurring-catchup",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<RecurringWorker>()
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build(),
            )
    }
}
