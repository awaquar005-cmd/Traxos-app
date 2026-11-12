package app.traxos.finance.smart

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import app.traxos.finance.BuildConfig
import app.traxos.finance.MainActivity
import app.traxos.finance.R
import app.traxos.finance.TraxosApplication
import app.traxos.finance.gifts.ImportTextSafety
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Compiled but not registered in the standard APK. No historical READ_SMS access exists. */
class FinancialSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            !BuildConfig.SMS_POLICY_APPROVED ||
                intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) !=
                    PackageManager.PERMISSION_GRANTED
        )
            return
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as TraxosApplication
                val prefs = app.database.ledger().preferences().first() ?: return@launch
                if (
                    !SmsAccess.allowed(
                        BuildConfig.SMS_POLICY_APPROVED,
                        prefs.smsOptIn,
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECEIVE_SMS,
                        ) == PackageManager.PERMISSION_GRANTED,
                        prefs.smartEnabled,
                    )
                )
                    return@launch
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
                // Long numeric senders/personal numbers and nonfinancial content are ignored, never
                // stored.
                if (
                    sender.startsWith("+") ||
                        (sender.all(Char::isDigit) && sender.length >= 10) ||
                        sender.length !in 3..20
                )
                    return@launch
                val body = messages.joinToString("") { it.messageBody.orEmpty() }
                if (ImportTextSafety.isOtp(body)) return@launch
                val offered = app.smart.offer(body) ?: return@launch
                if (
                    offered.second &&
                        offered.first.status == "PENDING" &&
                        prefs.suggestionNotifications
                )
                    notify(context)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                context.getSharedPreferences("smart_health", Context.MODE_PRIVATE).edit {
                    putBoolean("ingestFailure", true)
                }
            } finally {
                result.finish()
            }
        }
    }

    private fun notify(context: Context) {
        if (
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        )
            return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                "smart-add",
                "Smart Add suggestions",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        if (
            !NotificationManagerCompat.from(context).areNotificationsEnabled() ||
                manager.getNotificationChannel("smart-add")?.importance ==
                    NotificationManager.IMPORTANCE_NONE
        )
            return
        val intent =
            Intent(context, MainActivity::class.java)
                .putExtra("open_smart", true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending =
            PendingIntent.getActivity(
                context,
                28,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        manager.notify(
            "smart-add",
            28,
            NotificationCompat.Builder(context, "smart-add")
                .setSmallIcon(R.drawable.traxos_mark)
                .setContentTitle("TRAXOS · Review a suggestion")
                .setContentText("A financial message is waiting for your approval.")
                .setContentIntent(pending)
                .addAction(0, "Review", pending)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setAutoCancel(true)
                .build(),
        )
    }
}
