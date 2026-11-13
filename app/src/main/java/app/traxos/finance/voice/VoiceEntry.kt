package app.traxos.finance.voice

import androidx.lifecycle.ViewModel
import app.traxos.finance.domain.Money
import app.traxos.finance.gifts.ImportTextSafety
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceDraft(val amount: String, val currency: String, val type: String, val note: String)

object VoiceParser {
    fun parse(raw: String, defaultCurrency: String): VoiceDraft {
        require(raw.isNotBlank() && raw.length <= 2000) {
            "Describe one transaction, or use manual entry"
        }
        require(!ImportTextSafety.isOtp(raw) && !ImportTextSafety.isLink(raw)) {
            "Links and one-time passwords are not transaction phrases"
        }
        require(
            !Regex(
                    "(?i)\\b(?:transfer|repayment|settlement|settled|refund|reversal|cashback|loan|borrowed|lent|pay card|card bill)\\b"
                )
                .containsMatchIn(raw)
        ) {
            "Use Transfer, Pay card or the matching refund flow; this is not an ordinary expense/income"
        }
        require(
            !Regex(
                    "(?i)(?:^|\\s)-\\s*[0-9]|\\bminus\\s*[0-9]|\\b(?:failed|declined|pending|unsuccessful|not paid|not received|did not|didn't)\\b"
                )
                .containsMatchIn(raw)
        ) {
            "Use a positive amount for a completed transaction, or edit manually"
        }
        require(
            !Regex(
                    "(?i)credit\\s*card\\s*(?:bill|payment)|(?:payment|pay|paid)\\s+(?:my\\s+|to\\s+|towards\\s+)?credit\\s*card"
                )
                .containsMatchIn(raw)
        ) {
            "Use Pay card for repayments; they are not expenses"
        }
        require(!Regex("[$€£¥]").containsMatchIn(raw)) {
            "Use an explicit supported currency code, such as USD or EUR"
        }
        val currencies =
            Regex("(?i)\\b(?:INR|USD|EUR|JPY|KWD|rupees?|rupaye|Rs)\\b\\.?|₹")
                .findAll(raw)
                .map {
                    it.value.trimEnd('.').uppercase(Locale.ROOT).let { value ->
                        if (value in setOf("₹", "RS", "RUPEE", "RUPEES", "RUPAYE")) "INR" else value
                    }
                }
                .distinct()
                .toList()
        require(currencies.size <= 1) { "Choose one currency per transaction" }
        require(
            !Regex("(?i)\\b(?:dollars?|euros?|yen|pounds?)\\b").containsMatchIn(raw) ||
                currencies.size == 1
        ) {
            "Use an explicit currency code for that amount"
        }
        val currency = currencies.singleOrNull() ?: defaultCurrency
        val matches = Regex("(?<![\\w.])[0-9][0-9,]*(?:\\.[0-9]+)?(?![\\w.])").findAll(raw).toList()
        require(matches.size == 1) { "Use digits for exactly one amount, then review the draft" }
        val candidate = matches.single().value
        require(
            !candidate.contains(',') ||
                candidate.matches(
                    Regex(
                        "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,2}(?:,[0-9]{2})*,[0-9]{3})(?:\\.[0-9]+)?"
                    )
                )
        ) {
            "Check the amount grouping"
        }
        val amount = candidate.replace(",", "")
        Money.parse(amount, currency)
        val income =
            Regex("(?i)\\b(?:received|salary|income|earned|credited)\\b").containsMatchIn(raw)
        val expense = Regex("(?i)\\b(?:spent|paid|expense|debited)\\b").containsMatchIn(raw)
        require(!(income && expense)) { "Describe either income or spending, not both" }
        return VoiceDraft(
            amount,
            currency,
            if (income) "INCOME" else "EXPENSE",
            raw.trim().take(500),
        )
    }
}

interface OfflineSpeechEngine {
    val available: Boolean

    fun start(result: (String) -> Unit, failure: (String) -> Unit)

    fun stop()

    fun cancel()

    fun destroy()
}

data class VoiceState(val phase: String = "IDLE", val text: String = "", val message: String = "")

class VoiceViewModel(private val engine: OfflineSpeechEngine) : ViewModel() {
    private val _state = MutableStateFlow(VoiceState())
    val state = _state.asStateFlow()
    val available: Boolean
        get() = engine.available

    private var generation = 0
    private var pending: VoiceDraft? = null
    private var approved = false

    fun text(value: String) {
        approved = false
        if (_state.value.phase !in setOf("LISTENING", "PROCESSING"))
            _state.value = VoiceState("EDIT", value.take(2000))
    }

    fun fallback(message: String) {
        generation++
        engine.cancel()
        _state.value = VoiceState("EDIT", _state.value.text, message)
    }

    fun start(permission: Boolean) {
        if (_state.value.phase in setOf("LISTENING", "PROCESSING")) return
        if (!permission) {
            fallback("Allow microphone access, then hold Plus again, or type instead")
            return
        }
        if (!engine.available) {
            fallback(
                "On-device speech is unavailable. Install a compatible offline model in Android settings, or type below. No cloud recognition is used."
            )
            return
        }
        approved = false
        val token = ++generation
        _state.value = VoiceState("LISTENING", _state.value.text)
        try {
            engine.start(
                { text ->
                    if (token == generation) {
                        generation++
                        _state.value =
                            VoiceState(
                                "EDIT",
                                text.take(2000),
                                "Review the recognized words before using them",
                            )
                    }
                },
                { message ->
                    if (token == generation) {
                        generation++
                        _state.value = VoiceState("EDIT", _state.value.text, message)
                    }
                },
            )
        } catch (e: SecurityException) {
            fallback("Microphone permission is unavailable. Type instead.")
        } catch (e: RuntimeException) {
            fallback("On-device speech could not start. Type instead.")
        }
    }

    fun stop() {
        if (_state.value.phase == "LISTENING") {
            _state.value =
                _state.value.copy(
                    phase = "PROCESSING",
                    message = "Finishing on-device recognition…",
                )
            try {
                engine.stop()
            } catch (e: RuntimeException) {
                fallback("Recognition stopped unexpectedly. Type or try again.")
            }
        }
    }

    fun interrupt() {
        if (_state.value.phase in setOf("LISTENING", "PROCESSING"))
            fallback("Recording stopped when Traxos left the foreground. Type or try again.")
    }

    fun approve(currency: String) {
        require(!approved && _state.value.phase !in setOf("LISTENING", "PROCESSING")) {
            "This draft is already open or still recording. Edit the words to create a new one."
        }
        pending = VoiceParser.parse(_state.value.text, currency)
        approved = true
    }

    fun takeDraft(): VoiceDraft? = pending.also { pending = null }

    fun dismiss() {
        generation++
        engine.cancel()
        _state.value = VoiceState()
    }

    override fun onCleared() {
        generation++
        engine.destroy()
    }
}
