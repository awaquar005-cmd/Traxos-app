package app.traxos.finance.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import app.traxos.finance.data.*
import app.traxos.finance.domain.ActivityScope
import app.traxos.finance.domain.Money
import java.time.LocalDate
import java.time.YearMonth
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
    val preferenceChange: Boolean = false,
    val kind: NoticeKind = NoticeKind.SUCCESS,
)

internal data class ActivityPagingQuery(
    val search: String,
    val type: String,
    val scope: ActivityScope,
    val sort: String,
)

sealed interface ReportReadState {
    data object Loading : ReportReadState

    data object Failed : ReportReadState

    data class Ready(val rows: List<CategoryReport>) : ReportReadState
}

private fun escapeSearch(value: String) =
    value.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MainViewModel(val repository: LedgerRepository, private val saved: SavedStateHandle) :
    ViewModel() {
    private val retry = MutableStateFlow(0)
    private val preferenceRetry = MutableStateFlow(0)
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
    val preferences =
        preferenceRetry
            .flatMapLatest {
                repository.dao
                    .preferences()
                    .map<Preferences?, Preferences?> { it ?: Preferences() }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        events.send(
                            Feedback(
                                "Could not read preferences. Reopen the app to retry.",
                                kind = NoticeKind.ERROR,
                            )
                        )
                        emit(Preferences(balancesHidden = true))
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val theme =
        preferences
            .map { it?.theme ?: "SYSTEM" }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")
    val search = saved.getStateFlow("search", "")
    val recordFilter = saved.getStateFlow("record_filter", "ALL")

    fun recordFilter(value: String) {
        require(value in listOf("ALL", "EXPENSES", "INCOME", "TRANSFERS", "CARD_PAYMENTS"))
        saved["record_filter"] = value
    }

    val scopeValues = saved.getStateFlow("activity_scope", arrayListOf("", "", "", "", ""))

    fun scope(value: ActivityScope) {
        saved["activity_scope"] = value.validate().saved()
    }

    fun clearActivityFilters() {
        search("")
        recordFilter("ALL")
        scope(ActivityScope())
    }

    fun activitySort(value: String) =
        preferenceChange("Activity order saved") { repository.setActivitySort(value) }

    val pages =
        combine(
                search.debounce(300).distinctUntilChanged(),
                recordFilter,
                scopeValues,
                preferences.filterNotNull().map { it.activitySort }.distinctUntilChanged(),
            ) { q, filter, scope, sort ->
                ActivityPagingQuery(q, filter, ActivityScope.read(scope), sort)
            }
            .flatMapLatest { query ->
                Pager(PagingConfig(pageSize = 40, enablePlaceholders = false)) {
                        val scope = query.scope
                        repository.dao.page(
                            escapeSearch(query.search),
                            query.type,
                            scope.startDay,
                            scope.endDay,
                            scope.accountId,
                            scope.categoryId,
                            scope.currency,
                            query.sort,
                        )
                    }
                    .flow
            }
            .cachedIn(viewModelScope)

    val deletedSearch = saved.getStateFlow("deleted_search", "")

    fun deletedSearch(value: String) {
        saved["deleted_search"] = value.take(200)
    }

    val deletedPages =
        deletedSearch
            .debounce(300)
            .distinctUntilChanged()
            .flatMapLatest { q ->
                Pager(PagingConfig(pageSize = 40, enablePlaceholders = false)) {
                        repository.dao.page(escapeSearch(q), deleted = true)
                    }
                    .flow
            }
            .cachedIn(viewModelScope)

    val reportMonth = saved.getStateFlow("report_month", YearMonth.now().toString())
    val reportType = saved.getStateFlow("report_type", "EXPENSES")

    fun reportMonth(value: String) {
        require(YearMonth.parse(value).year in 1..9999) { "Choose a year from 0001 to 9999" }
        saved["report_month"] = value
    }

    fun reportType(value: String) {
        require(value in listOf("EXPENSES", "INCOME"))
        saved["report_type"] = value
    }

    val report =
        retry
            .flatMapLatest {
                reportMonth.flatMapLatest { value ->
                    val month = YearMonth.parse(value)
                    repository.dao
                        .categoryReport(
                            month.atDay(1).toEpochDay(),
                            month.plusMonths(1).atDay(1).toEpochDay(),
                        )
                        .map<List<CategoryReport>, ReportReadState> { ReportReadState.Ready(it) }
                        .onStart { emit(ReportReadState.Loading) }
                        .catch { e ->
                            if (e is CancellationException) throw e
                            emit(ReportReadState.Failed)
                        }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportReadState.Loading)

    fun restore(row: RecordRow) {
        if (busy.value) return
        busy.value = true
        val op = UUID.randomUUID().toString()
        viewModelScope.launch {
            try {
                repository.setDeleted(op, row.record.id, row.record.revision, false)
                events.send(
                    Feedback(
                        if (row.record.linkId == null) "Transaction restored"
                        else "Both sides restored",
                        undoId = row.record.id,
                        undoRevision = row.record.revision + 1,
                        undoDeleted = true,
                    )
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                events.send(
                    Feedback(
                        if (e is IllegalArgumentException) e.message ?: "Could not restore"
                        else "Could not restore. Your records are unchanged; try again.",
                        kind = NoticeKind.ERROR,
                    )
                )
            } finally {
                busy.value = false
            }
        }
    }

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

    private fun preferenceChange(message: String, change: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                change()
                preferenceRetry.value += 1
                events.send(Feedback(message, preferenceChange = true))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                events.send(
                    Feedback("Could not save preference. Please retry.", kind = NoticeKind.ERROR)
                )
            } finally {
                busy.value = false
            }
        }
    }

    fun accent(value: String) = preferenceChange("Accent saved") { repository.setAccent(value) }

    fun theme(value: String) = preferenceChange("Appearance saved") { repository.setTheme(value) }

    fun centerAction(value: String) =
        preferenceChange("Center action updated") { repository.setCenterAction(value) }

    fun balancesHidden(hidden: Boolean) =
        preferenceChange(if (hidden) "Financial amounts hidden" else "Financial amounts visible") {
            repository.setBalancesHidden(hidden)
        }

    fun navigationSlots(slots: List<String>) =
        preferenceChange("Navigation updated") { repository.setNavigationSlots(slots) }

    fun currency(value: String) =
        preferenceChange("Default currency saved") { repository.setCurrency(value) }

    fun displayName(value: String) =
        preferenceChange("Local profile saved") { repository.setDisplayName(value) }

    fun defaults(account: String, category: String) =
        preferenceChange("Entry defaults saved") { repository.setDefaults(account, category) }

    fun defaultAccount(value: String) =
        preferenceChange("Default account saved") { repository.setDefaultAccount(value) }

    fun defaultCategory(value: String) =
        preferenceChange("Default category saved") { repository.setDefaultCategory(value) }

    fun smartEnabled(value: Boolean) =
        preferenceChange("Smart Add preference saved") { repository.setSmartEnabled(value) }

    fun smsOptIn(value: Boolean) =
        preferenceChange("SMS consent saved") { repository.setSmsOptIn(value) }

    fun suggestionNotices(value: Boolean) =
        preferenceChange("Suggestion alerts saved") { repository.setSuggestionNotices(value) }

    fun smartSettings(enabled: Boolean, sms: Boolean, notices: Boolean) =
        preferenceChange("Smart tracking saved") {
            repository.setSmartSettings(enabled, sms, notices)
        }

    fun undo(feedback: Feedback) {
        if (busy.value) {
            viewModelScope.launch {
                events.send(
                    feedback.copy(
                        text = "Another change is saving. Try Undo again.",
                        close = false,
                        kind = NoticeKind.WARNING,
                    )
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
                        else "Could not undo. Your records are unchanged.",
                        kind = NoticeKind.ERROR,
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
                                    "currency" to
                                        (repository.dao.preferences().first()?.defaultCurrency
                                            ?: "INR"),
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
                                            "icon" to it.icon,
                                            "color" to it.color,
                                            "type" to it.type,
                                            "currency" to it.currency,
                                            "opening" to
                                                Money.editable(
                                                    kotlin.math.abs(it.openingMinor),
                                                    it.currency,
                                                ),
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
                                            "merchantId" to r.merchantId.orEmpty(),
                                            "linkId" to r.linkId.orEmpty(),
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
                        f["icon"] ?: "wallet",
                        f["color"] ?: "BLUE",
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
                        f["merchantId"]?.takeIf { it.isNotBlank() },
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
