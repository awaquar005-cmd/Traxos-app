package app.traxos.finance.gifts

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.traxos.finance.domain.Money
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Transient inbox/drafts never enter saved-state bundles or logs. */
sealed interface GiftIncoming {
    data class Text(val value: String) : GiftIncoming

    data class Image(val uri: Uri) : GiftIncoming

    data object SmartOpen : GiftIncoming

    data object Open : GiftIncoming
}

object GiftInbox {
    val pending = MutableStateFlow<GiftIncoming?>(null)
}

data class GiftDraft(
    val id: String = UUID.randomUUID().toString(),
    val revision: Long = 0,
    val merchant: String = "",
    val name: String = "",
    val kind: String = "STORED_VALUE",
    val code: String = "",
    val pin: String = "",
    val notes: String = "",
    val image: ByteArray? = null,
    val original: String = "",
    val remaining: String = "",
    val currency: String = "INR",
    val received: String = LocalDate.now().toString(),
    val expiry: String = "",
    val partial: Boolean = false,
    val source: String = "RECEIVED",
    val createdAt: Long = System.currentTimeMillis(),
    val historyLocked: Boolean = false,
) {
    fun card(): GiftCard =
        GiftCard(
            id,
            merchant.trim(),
            name.trim().ifBlank {
                "${merchant.trim()} ${if(kind=="DISCOUNT") "voucher" else "gift card"}"
            },
            kind,
            byteArrayOf(),
            "",
            if (kind == "DISCOUNT") null else GiftRules.amount(original, currency),
            if (kind == "DISCOUNT") null else GiftRules.amount(remaining, currency),
            currency,
            runCatching { LocalDate.parse(received).toEpochDay() }
                .getOrElse { throw IllegalArgumentException("Use YYYY-MM-DD for received date") },
            if (expiry.isBlank()) null
            else
                runCatching { LocalDate.parse(expiry).toEpochDay() }
                    .getOrElse { throw IllegalArgumentException("Use YYYY-MM-DD for expiry date") },
            partialAllowed = partial && kind == "STORED_VALUE",
            revision = revision,
            createdAt = createdAt,
            source = source,
        )

    fun secret() = GiftSecret(code.trim(), pin, notes, image)
}

class GiftViewModel(val repository: GiftRepository) : ViewModel() {
    val cards =
        repository.cards
            .map<List<GiftCard>, List<GiftCard>?> { it }
            .catch {
                _error.value = "Could not read the gift wallet. Reopen the screen to retry."
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val localPreferences =
        repository.db
            .ledger()
            .preferences()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val settings =
        repository.settings
            .map { it ?: GiftSettings() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GiftSettings())
    var draft = MutableStateFlow<GiftDraft?>(null)
        private set

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    val events =
        kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)

    fun clearError() {
        _error.value = null
    }

    fun update(change: (GiftDraft) -> GiftDraft) {
        if (!_busy.value) draft.value = draft.value?.let(change)
        _error.value = null
    }

    fun begin(
        extracted: GiftExtraction? = null,
        image: ByteArray? = null,
        defaultCurrency: String = "INR",
    ) {
        draft.value =
            GiftDraft(
                merchant = extracted?.merchant.orEmpty(),
                kind = extracted?.giftKind ?: "STORED_VALUE",
                code = extracted?.code.orEmpty(),
                pin = extracted?.pin.orEmpty(),
                original = if (extracted?.amountIsBalance == false) extracted.amount else "",
                remaining = if (extracted?.amountIsBalance == true) extracted.amount else "",
                currency = extracted?.currency?.ifBlank { defaultCurrency } ?: defaultCurrency,
                expiry = extracted?.expiry.orEmpty(),
                image = image,
            )
        _error.value = null
    }

    fun discard() {
        draft.value = null
        _error.value = null
    }

    fun action(task: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                task()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _error.value = e.message ?: "Please check the entered details"
            } catch (_: Exception) {
                _error.value =
                    "Could not complete this action. Reopen the wallet to check saved changes. Check device storage; if encrypted details cannot open, do not clear app data."
            } finally {
                _busy.value = false
            }
        }
    }

    fun edit(id: String) {
        action {
            val c = requireNotNull(repository.dao.card(id)) { "Card is no longer available" }
            require(!c.deleted)
            val s = repository.secret(c)
            draft.value =
                GiftDraft(
                    c.id,
                    c.revision,
                    c.merchant,
                    c.name,
                    c.kind,
                    s.code,
                    s.pin,
                    s.notes,
                    s.image,
                    c.originalMinor?.let { Money.editable(it, c.currency) }.orEmpty(),
                    c.remainingMinor?.let { Money.editable(it, c.currency) }.orEmpty(),
                    c.currency,
                    LocalDate.ofEpochDay(c.receivedDay).toString(),
                    c.expiryDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty(),
                    c.partialAllowed,
                    c.source,
                    c.createdAt,
                    repository.dao.latest(id) != null,
                )
        }
    }

    fun save(done: (String) -> Unit) {
        val d = draft.value ?: return
        action {
            val saved = repository.save(d.card(), d.secret(), d.revision)
            draft.value = null
            done(saved.id)
            events.send("Gift card saved")
        }
    }

    suspend fun duplicate(code: String, id: String, kind: String, merchant: String): Boolean =
        repository.hasDuplicate(code, id, kind, merchant)

    fun applyExtraction(e: GiftExtraction, image: ByteArray?) {
        update { old ->
            old.copy(
                merchant = e.merchant.ifBlank { old.merchant },
                code = e.code.ifBlank { old.code },
                pin = e.pin.ifBlank { old.pin },
                original =
                    if (!e.amountIsBalance) e.amount.ifBlank { old.original } else old.original,
                remaining =
                    if (e.amountIsBalance) e.amount.ifBlank { old.remaining } else old.remaining,
                currency = e.currency.ifBlank { old.currency },
                expiry = e.expiry.ifBlank { old.expiry },
                image = image ?: old.image,
            )
        }
    }

    fun changeSettings(update: (GiftSettings) -> GiftSettings) = action {
        repository.changeSettings(update)
    }

    fun settings(value: GiftSettings) = action { repository.updateSettings(value) }
}
