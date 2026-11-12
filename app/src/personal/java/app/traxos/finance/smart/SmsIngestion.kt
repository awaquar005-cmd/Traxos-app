package app.traxos.finance.smart

import app.traxos.finance.data.LedgerRepository
import app.traxos.finance.gifts.ImportTextSafety
import kotlinx.coroutines.flow.first

/** Gate before classifying/persisting a message; called only by the personal system receiver. */
class SmsIngestion(
    private val ledger: LedgerRepository,
    private val smart: SmartRepository,
    private val hasPermission: () -> Boolean,
) {
    suspend fun receive(
        sender: String,
        body: String,
        receivedDay: String? = null,
    ): Pair<SmartSuggestion, Boolean>? {
        val p = ledger.dao.preferences().first() ?: return null
        if (
            !SmsAccess.allowed(
                app.traxos.finance.BuildConfig.SMS_ENABLED,
                p.smsOptIn,
                hasPermission(),
                p.smartEnabled,
            )
        )
            return null
        if (
            sender.startsWith("+") ||
                (sender.all(Char::isDigit) && sender.length >= 10) ||
                sender.length !in 3..20
        )
            return null
        if (body.length > 16000 || ImportTextSafety.isOtp(body)) return null
        // Do not retain personal/unrelated text; only the conservative financial parser can offer
        // it.
        return smart.offer(body, receivedDay)
    }
}
