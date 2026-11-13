package app.traxos.finance.smart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.traxos.finance.data.*
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class SmartViewModel(val repository: SmartRepository, val ledger: LedgerRepository) : ViewModel() {
    private val status = MutableStateFlow("PENDING")
    private val limit = MutableStateFlow(200)
    private val reload = MutableStateFlow(0)
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    val items =
        combine(status, limit, reload) { s, n, _ -> s to n }
            .flatMapLatest { (s, n) ->
                repository.dao
                    .observe(s, n)
                    .map { rows -> rows.map { it to repository.read(it) } }
                    .catch {
                        _error.value =
                            "Could not decrypt the local inbox. Keep app data and try again."
                        emit(emptyList())
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val homeSuggestion =
        repository.dao
            .observe("PENDING", 1)
            .map { rows -> rows.firstOrNull()?.let { it to repository.read(it) } }
            .catch { emit(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val pending =
        repository.dao
            .pendingCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val preferences =
        ledger.dao.preferences().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val accounts =
        ledger.dao
            .accounts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories =
        ledger.dao
            .categories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val selected = MutableStateFlow<SmartSuggestion?>(null)
    val draft = MutableStateFlow<SmartDraft?>(null)
    val events = Channel<String>(Channel.BUFFERED)

    fun filter(value: String) {
        status.value = value
        limit.value = 200
    }

    fun more() {
        limit.value += 200
    }

    fun retry() {
        _error.value = null
        reload.value++
    }

    fun action(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _error.value = e.message ?: "Review the suggestion details"
            } catch (_: Exception) {
                _error.value =
                    "Could not finish this action. Reopen the inbox to check its status; do not clear local data."
            } finally {
                _busy.value = false
            }
        }
    }

    fun offer(text: String) = action {
        require(ledger.dao.preferences().first()?.smartEnabled != false) {
            "Enable Smart Add in Smart Tracking settings first"
        }
        val offered = repository.offer(text)
        if (offered == null)
            _error.value =
                "No financial transaction or gift voucher was found. Links, one-time passwords and conversations are ignored."
        else {
            if (!offered.second || offered.first.status != "PENDING")
                events.send("This message or gift code has already been reviewed/imported")
            // Stay in the inbox; only Edit opens a sheet.
        }
    }

    private suspend fun open(s: SmartSuggestion) {
        val d = repository.read(s)
        val p = ledger.dao.preferences().first()
        selected.value = s
        draft.value =
            d.copy(
                currency = d.currency.ifBlank { p?.defaultCurrency ?: "INR" },
                day = d.day.ifBlank { LocalDate.now().toString() },
            )
    }

    fun select(s: SmartSuggestion) = action { open(s) }

    fun update(change: (SmartDraft) -> SmartDraft) {
        if (!_busy.value) draft.value = draft.value?.let(change)
    }

    fun close() {
        if (!_busy.value) {
            selected.value = null
            draft.value = null
            _error.value = null
        }
    }

    fun reject(ignore: Boolean) = action {
        val s = requireNotNull(selected.value)
        repository.reject(s.id, ignore)
        selected.value = null
        draft.value = null
        events.send(if (ignore) "Suggestion ignored" else "Suggestion rejected")
    }

    fun rejectSuggestion(s: SmartSuggestion) = action {
        repository.reject(s.id, false)
        events.send("Suggestion rejected")
    }

    fun approveSuggestion(s: SmartSuggestion, account: String, category: String) = action {
        val d = repository.read(s)
        require(d.kind == "GIFT" || (account.isNotBlank() && category.isNotBlank())) {
            "Tap Edit to choose the account and category before approving"
        }
        repository.approve(s.id, d, account, category)
        events.send(if (d.kind == "GIFT") "Gift card added" else "Transaction added")
    }

    fun approve(account: String, category: String) = action {
        val s = requireNotNull(selected.value)
        val d = requireNotNull(draft.value)
        repository.approve(s.id, d, account, category)
        selected.value = null
        draft.value = null
        events.send(if (d.kind == "GIFT") "Gift card added" else "Transaction added")
    }
}
