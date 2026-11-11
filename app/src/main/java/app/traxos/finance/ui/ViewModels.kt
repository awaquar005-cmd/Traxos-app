package app.traxos.finance.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import app.traxos.finance.data.*
import app.traxos.finance.domain.Money
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

sealed interface ReadState {
    data object Loading : ReadState

    data class Ready(
        val accounts: List<AccountBalance>,
        val categories: List<Category>,
        val recent: List<RecordRow>,
        val summary: List<Summary>,
    ) : ReadState

    data object Failed : ReadState
}

data class Feedback(
    val text: String,
    val close: Boolean = false,
    val undoId: String? = null,
    val undoRevision: Long? = null,
    val undoDeleted: Boolean = true,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MainViewModel(val repository: LedgerRepository, private val saved: SavedStateHandle) :
    ViewModel() {
    private val retry = MutableStateFlow(0)
    private val month = MutableStateFlow(LocalDate.now().withDayOfMonth(1))
    val reads =
        retry
            .flatMapLatest {
                month
                    .flatMapLatest { date ->
                        combine(
                            repository.dao.accounts(),
                            repository.dao.categories(),
                            repository.dao.recent(),
                            repository.dao.summary(
                                date.toEpochDay(),
                                date.plusMonths(1).toEpochDay(),
                            ),
                        ) { a, c, r, s ->
                            ReadState.Ready(a, c, r, s) as ReadState
                        }
                    }
                    .onStart { emit(ReadState.Loading) }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(ReadState.Failed)
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReadState.Loading)
    val theme =
        retry
            .flatMapLatest {
                repository.dao
                    .preferences()
                    .map { it?.theme ?: "SYSTEM" }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        events.send(Feedback("Could not load appearance. Choose a theme to retry."))
                        emit("SYSTEM")
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")
    val search = saved.getStateFlow("search", "")
    val pages =
        search
            .debounce(300)
            .distinctUntilChanged()
            .flatMapLatest { q ->
                val escaped = q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                Pager(PagingConfig(pageSize = 40, enablePlaceholders = false)) {
                        repository.dao.page(escaped)
                    }
                    .flow
            }
            .cachedIn(viewModelScope)
    val events = Channel<Feedback>(Channel.BUFFERED)
    var busy = MutableStateFlow(false)
        private set

    fun search(value: String) {
        saved["search"] = value.take(200)
    }

    fun refresh() {
        month.value = LocalDate.now().withDayOfMonth(1)
    }

    fun retry() {
        retry.value += 1
    }

    fun theme(value: String) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                repository.setTheme(value)
                retry.value += 1
                events.send(Feedback("Appearance saved"))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                events.send(Feedback("Could not save appearance. Please retry."))
            } finally {
                busy.value = false
            }
        }
    }

    fun undo(feedback: Feedback) {
        if (busy.value) {
            viewModelScope.launch {
                events.send(
                    feedback.copy(text = "Another change is saving. Try Undo again.", close = false)
                )
            }
            return
        }
        busy.value = true
        val op = UUID.randomUUID().toString()
        viewModelScope.launch {
            try {
                repository.setDeleted(
                    op,
                    requireNotNull(feedback.undoId),
                    requireNotNull(feedback.undoRevision),
                    feedback.undoDeleted,
                )
                events.send(Feedback("Change undone on this phone"))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                events.send(
                    Feedback(
                        if (e is IllegalArgumentException) e.message ?: "Unable to undo"
                        else "Could not undo. Your records are unchanged."
                    )
                )
            } finally {
                busy.value = false
            }
        }
    }
}

class FormViewModel(
    private val repository: LedgerRepository,
    private val saved: SavedStateHandle,
    private val accountForm: Boolean,
) : ViewModel() {
    val fields = saved.getStateFlow("fields", hashMapOf<String, String>())
    val loading = MutableStateFlow(true)
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val events = Channel<Feedback>(Channel.BUFFERED)
    private val id: String = saved.get<String>("id") ?: "new"
    private val entityId: String =
        saved.get<String>("entityId")
            ?: (if (id == "new") UUID.randomUUID().toString() else id).also {
                saved["entityId"] = it
            }
    private val op: String =
        saved.get<String>("operationId")
            ?: UUID.randomUUID().toString().also { saved["operationId"] = it }
    val isNew
        get() = id == "new"

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                if (fields.value.isEmpty()) {
                    val map =
                        if (accountForm) {
                            if (isNew)
                                hashMapOf(
                                    "name" to "",
                                    "type" to "CASH",
                                    "currency" to "INR",
                                    "opening" to "0",
                                    "archived" to "false",
                                    "day" to LocalDate.now().toString(),
                                )
                            else
                                requireNotNull(repository.dao.account(entityId)) {
                                        "Account not found"
                                    }
                                    .let {
                                        hashMapOf(
                                            "name" to it.name,
                                            "type" to it.type,
                                            "currency" to it.currency,
                                            "opening" to
                                                Money.editable(it.openingMinor, it.currency),
                                            "archived" to it.archived.toString(),
                                            "revision" to it.revision.toString(),
                                            "day" to LocalDate.ofEpochDay(it.openingDate).toString(),
                                        )
                                    }
                        } else {
                            if (isNew)
                                hashMapOf(
                                    "amount" to "",
                                    "type" to "EXPENSE",
                                    "day" to LocalDate.now().toString(),
                                    "note" to "",
                                    "accountId" to "",
                                    "categoryId" to "",
                                )
                            else
                                requireNotNull(repository.dao.record(entityId)) {
                                        "Transaction not found"
                                    }
                                    .let { r ->
                                        require(!r.deleted) { "Transaction was deleted" }
                                        val currency =
                                            requireNotNull(repository.dao.account(r.accountId))
                                                .currency
                                        hashMapOf(
                                            "amount" to Money.editable(r.amountMinor, currency),
                                            "type" to r.type,
                                            "day" to LocalDate.ofEpochDay(r.day).toString(),
                                            "note" to r.note,
                                            "accountId" to r.accountId,
                                            "categoryId" to r.categoryId,
                                            "revision" to r.revision.toString(),
                                        )
                                    }
                        }
                    saved["fields"] = map
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error.value =
                    if (e is IllegalArgumentException) e.message
                    else "Could not open this record. Retry."
            } finally {
                loading.value = false
            }
        }
    }

    fun set(key: String, value: String) {
        saved["fields"] = HashMap(fields.value).apply { put(key, value) }
        saved["dirty"] = true
        error.value = null
    }

    fun default(key: String, value: String) {
        if (fields.value[key].isNullOrEmpty())
            saved["fields"] = HashMap(fields.value).apply { put(key, value) }
    }

    fun dirty(): Boolean = saved.get<Boolean>("dirty") == true

    fun save() {
        if (busy.value || loading.value || fields.value.isEmpty()) return
        busy.value = true
        error.value = null
        val f = fields.value.toMap()
        viewModelScope.launch {
            try {
                val revision = f["revision"]?.toLong()
                if (accountForm)
                    repository.saveAccount(
                        op,
                        entityId,
                        f["name"].orEmpty(),
                        f["type"].orEmpty(),
                        f["currency"].orEmpty(),
                        Money.parse(f["opening"].orEmpty(), f["currency"].orEmpty(), true),
                        revision,
                        f["archived"].toBoolean(),
                        LocalDate.parse(f["day"]).toEpochDay(),
                    )
                else {
                    val account =
                        requireNotNull(repository.dao.account(f["accountId"].orEmpty())) {
                            "Choose an account first"
                        }
                    repository.saveRecord(
                        op,
                        entityId,
                        account.id,
                        f["categoryId"].orEmpty(),
                        f["type"].orEmpty(),
                        Money.parse(f["amount"].orEmpty(), account.currency),
                        LocalDate.parse(f["day"]).toEpochDay(),
                        f["note"].orEmpty(),
                        revision,
                    )
                }
                saved["dirty"] = false
                events.send(
                    Feedback(
                        "Saved on this phone",
                        true,
                        if (!accountForm && isNew) entityId else null,
                        if (!accountForm && isNew) 1L else null,
                    )
                )
                // Keep gate closed until destination is popped; a distinct new form gets a fresh
                // identity.
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error.value =
                    when (e) {
                        is java.time.DateTimeException -> "Enter a valid date as YYYY-MM-DD"
                        is IllegalArgumentException -> e.message
                        else -> "Could not save. Your entries are kept; please retry."
                    }
                busy.value = false
            }
        }
    }

    fun delete() {
        if (busy.value || isNew || accountForm) return
        busy.value = true
        viewModelScope.launch {
            try {
                val revision = requireNotNull(fields.value["revision"]).toLong()
                repository.setDeleted(op, entityId, revision, true)
                events.send(
                    Feedback(
                        "Transaction deleted on this phone",
                        true,
                        entityId,
                        revision + 1,
                        false,
                    )
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error.value =
                    if (e is IllegalArgumentException) e.message
                    else "Could not delete. Please retry."
                busy.value = false
            }
        }
    }
}
